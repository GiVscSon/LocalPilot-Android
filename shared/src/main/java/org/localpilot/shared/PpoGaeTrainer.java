package org.localpilot.shared;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Genuine pure-Java actor/critic PPO+GAE optimizer for a StudentPolicyNet
 * candidate. All gradients are computed by backprop through both tanh layers.
 *
 * No source can invoke this with a legacy visual-only NES journal: every
 * step must pass the exact TrustedReplayFilter on-policy contract.
 *
 * This class NEVER activates weights; EvaluationGate owns promotion.
 */
public final class PpoGaeTrainer {
 private PpoGaeTrainer(){}
 public record Config(
   float gamma,float lambda,float clipEps,float learningRate,
   float entropyCoef,float valueCoef,float targetKl,float maxGradNorm,
   int epochs
 ) {
  public Config {
   if(!Float.isFinite(gamma)||gamma<=0||gamma>1||
      !Float.isFinite(lambda)||lambda<0||lambda>1||
      !Float.isFinite(clipEps)||clipEps<=0||clipEps>=.5f||
      !Float.isFinite(learningRate)||learningRate<=0||learningRate>.01f||
      !Float.isFinite(entropyCoef)||entropyCoef<0||entropyCoef>.2f||
      !Float.isFinite(valueCoef)||valueCoef<=0||valueCoef>4||
      !Float.isFinite(targetKl)||targetKl<=0||targetKl>.5f||
      !Float.isFinite(maxGradNorm)||maxGradNorm<=0||maxGradNorm>10||
      epochs<1||epochs>16)
    throw new IllegalArgumentException("Invalid PPO configuration");
  }
 }
 public static final Config DEFAULT=new Config(.99f,.95f,.2f,.0003f,
   .01f,.5f,.03f,.5f,4);
 public record Report(String shaBefore,String shaAfter,int eligibleSteps,
   int adamUpdates,int changedParameters,float avgPolicyLoss,
   float avgValueLoss,float lastApproxKl,boolean stoppedForKl) {}
 public static float clippedLoss(double ratio,double advantage,float eps){
  if(!Double.isFinite(ratio)||ratio<=0||!Double.isFinite(advantage))
   throw new IllegalArgumentException("Invalid PPO objective");
  double clipped=Math.min(1+eps,Math.max(1-eps,ratio));
  return (float)-Math.min(ratio*advantage,clipped*advantage);
 }
 public static double dClippedLossByRatio(double ratio,double advantage,float eps){
  return (advantage>0&&ratio>1+eps||advantage<0&&ratio<1-eps)?0:-advantage;
 }
 public record Targets(float[] rawAdvantages,float[] normalizedAdvantages,float[] criticTargets){
  public Targets {
   rawAdvantages=rawAdvantages.clone();
   normalizedAdvantages=normalizedAdvantages.clone();
   criticTargets=criticTargets.clone();
  }
  @Override public float[] rawAdvantages(){return rawAdvantages.clone();}
  @Override public float[] normalizedAdvantages(){return normalizedAdvantages.clone();}
  @Override public float[] criticTargets(){return criticTargets.clone();}
 }
 public static Targets computeTargets(StudentPolicyNet baseline,
                                      List<VerifiedReplayFilter.Step> steps,
                                      Config cfg) {
  if(baseline==null||cfg==null)throw new IllegalArgumentException("Baseline/config");
  VerifiedReplayFilter.Verdict v=VerifiedReplayFilter.inspect(steps);
  if(!v.eligible())throw new IllegalArgumentException("PPO_BLOCKED: "+v.reason());
  int n=steps.size();
  float[] advantages=new float[n],normalized=new float[n],targets=new float[n];
  double gae=0;
  for(int i=n-1;i>=0;i--){
   var s=steps.get(i);
   double discount=Math.pow(cfg.gamma(),s.durationFrames());
   double nextValue;
   if(s.terminal())nextValue=0;
   else if(i==n-1||s.truncated())
    nextValue=baseline.forward(s.nextState64(),s.legalMask()).value();
   else nextValue=steps.get(i+1).valueEstimate();
   double delta=s.reward()+discount*nextValue-s.valueEstimate();
   gae=delta+(s.terminal()||s.truncated()?0:discount*cfg.lambda()*gae);
   if(!Double.isFinite(gae)||Math.abs(gae)>1e9)
    throw new IllegalArgumentException("Nonfinite GAE");
   advantages[i]=(float)gae;
   targets[i]=(float)(gae+s.valueEstimate());
   if(!Float.isFinite(targets[i]))throw new IllegalArgumentException("Nonfinite value target");
  }
  double mean=0;
  for(float a:advantages)mean+=a;
  mean/=n;
  double variance=0;
  for(float a:advantages)variance+=(a-mean)*(a-mean);
  variance/=n;
  double divisor=Math.sqrt(variance+1e-8);
  for(int i=0;i<n;i++)normalized[i]=(float)((advantages[i]-mean)/divisor);
  return new Targets(advantages,normalized,targets);
 }
 private static final class Grad {
  final float[] w1=new float[StudentPolicyNet.INPUT*StudentPolicyNet.HIDDEN];
  final float[] b1=new float[StudentPolicyNet.HIDDEN];
  final float[] w2=new float[StudentPolicyNet.HIDDEN*StudentPolicyNet.HIDDEN];
  final float[] b2=new float[StudentPolicyNet.HIDDEN];
  final float[] wa,ba,wv=new float[StudentPolicyNet.HIDDEN];
  final float[] bv=new float[1];
  Grad(int count){wa=new float[StudentPolicyNet.HIDDEN*count];ba=new float[count];}
  float[][] all(){return new float[][]{w1,b1,w2,b2,wa,ba,wv,bv};}
 }
 private static final class Adam {
  final float[][] m,v;
  int t;
  Adam(float[][] grads){
   m=new float[grads.length][];v=new float[grads.length][];
   for(int j=0;j<grads.length;j++){
    m[j]=new float[grads[j].length];v[j]=new float[grads[j].length];
   }
  }
  void step(StudentPolicyNet net,Grad gradient,Config cfg,int steps){
   float[][] g=gradient.all();
   float[] criticBias={net.bv};
   float[][] weights={net.w1,net.b1,net.w2,net.b2,net.wa,net.ba,net.wv,criticBias};
   double squared=0;
   for(float[] vector:g)for(float value:vector){
    if(!Float.isFinite(value))throw new IllegalStateException("Nonfinite PPO gradient");
    squared+=(value/(double)steps)*(value/(double)steps);
   }
   double norm=Math.sqrt(squared);
   double scale=Math.min(1, cfg.maxGradNorm()/Math.max(1e-12,norm));
   ++t;
   double b1Corr=1-Math.pow(.9,t),b2Corr=1-Math.pow(.999,t);
   for(int group=0;group<g.length;group++){
    for(int i=0;i<g[group].length;i++){
     float grad=(float)(g[group][i]/steps*scale);
     m[group][i]=.9f*m[group][i]+.1f*grad;
     v[group][i]=.999f*v[group][i]+.001f*grad*grad;
     double update=cfg.learningRate()*(m[group][i]/b1Corr)/
       (Math.sqrt(v[group][i]/b2Corr)+1e-8);
     double revised=weights[group][i]-update;
     if(!Double.isFinite(revised)||Math.abs(revised)>1e6)
      throw new IllegalStateException("Nonfinite/exploding PPO parameter");
     weights[group][i]=(float)revised;
    }
   }
   net.bv=criticBias[0];
  }
 }
 private static void backprop(StudentPolicyNet net,float[] x,
                              StudentPolicyNet.Forward fp,float[] dz,
                              float dv,Grad g) {
  float[] h1=fp.h1(),h2=fp.h2(),dh2=new float[64],dh1=new float[64];
  for(int a=0;a<net.actionCount;a++){
   int row=a*64;float d=dz[a];
   g.ba[a]+=d;
   for(int i=0;i<64;i++){
    g.wa[row+i]+=d*h2[i];
    dh2[i]+=d*net.wa[row+i];
   }
  }
  g.bv[0]+=dv;
  for(int i=0;i<64;i++){
   g.wv[i]+=dv*h2[i];
   dh2[i]+=dv*net.wv[i];
  }
  for(int i=0;i<64;i++){
   float pre=dh2[i]*(1-h2[i]*h2[i]);
   g.b2[i]+=pre;
   int row=i*64;
   for(int j=0;j<64;j++){
    g.w2[row+j]+=pre*h1[j];
    dh1[j]+=pre*net.w2[row+j];
   }
  }
  for(int i=0;i<64;i++){
   float pre=dh1[i]*(1-h1[i]*h1[i]);
   g.b1[i]+=pre;
   int row=i*64;
   for(int j=0;j<64;j++)g.w1[row+j]+=pre*x[j];
  }
 }
 private static float approximateKl(StudentPolicyNet model,
                                     List<VerifiedReplayFilter.Step> steps){
  double sum=0;
  for(var s:steps){
   float p=model.forward(s.state64(),s.legalMask()).probs()[s.actionId()];
   if(p<=0||!Float.isFinite(p))throw new IllegalStateException("Invalid new action probability");
   double diff=Math.log(p)-s.oldLogProb();
   if(!Double.isFinite(diff)||diff>20||diff< -20)
    throw new IllegalStateException("KL_RISK_POLICY_DIVERGED");
   double ratio=Math.exp(diff);
   sum+=Math.expm1(diff)-diff;
   if(!Double.isFinite(sum))throw new IllegalStateException("Nonfinite KL");
  }
  return (float)(sum/steps.size());
 }
 public static Report updateCandidate(StudentPolicyNet active,
                    List<VerifiedReplayFilter.Step> onPolicy,
                    Path candidateWeights,Config config)throws IOException {
  if(active==null||config==null||candidateWeights==null)
   throw new IllegalArgumentException("Missing PPO inputs");
  var admission=VerifiedReplayFilter.inspect(onPolicy);
  if(!admission.eligible())throw new IllegalArgumentException("PPO_BLOCKED: "+admission.reason());
  String before=active.sha256();
  for(var row:onPolicy)
   if(!before.equals(row.policySha256())||row.actionCount()!=active.actionCount)
    throw new IllegalArgumentException("PPO_BLOCKED_POLICY_SHA_MISMATCH");
  StudentPolicyNet candidate=active.candidateCopy();
  Targets batch=computeTargets(active,onPolicy,config);
  float[] a=batch.normalizedAdvantages(),target=batch.criticTargets();
  Adam adam=new Adam(new Grad(active.actionCount).all());
  double policyLoss=0,valueLoss=0;
  float lastKl=0;int updates=0;
  boolean stoppedForKl=false;
  for(int epoch=0;epoch<config.epochs();epoch++){
   lastKl=approximateKl(candidate,onPolicy);
   if(lastKl>config.targetKl()){stoppedForKl=true;break;}
   Grad gradients=new Grad(candidate.actionCount);
   for(int t=0;t<onPolicy.size();t++){
    var step=onPolicy.get(t);
    StudentPolicyNet.Forward fp=candidate.forward(step.state64(),step.legalMask());
    float[] p=fp.probs();
    double newProb=p[step.actionId()];
    if(newProb<=0)throw new IllegalStateException("Invalid sampled probability");
    double logRatio=Math.log(newProb)-step.oldLogProb();
    if(logRatio>20||logRatio< -20)throw new IllegalStateException("PPO_RATIO_EXPLODED");
    double ratio=Math.exp(logRatio);
    double advantage=a[t];
    policyLoss+=clippedLoss(ratio,advantage,config.clipEps());
    double scale=dClippedLossByRatio(ratio,advantage,config.clipEps())*ratio;
    double h=0;
    for(int j=0;j<candidate.actionCount;j++){
     if((step.legalMask()&(1<<j))==0)continue;
     double pi=Math.max(1e-12,p[j]);
     h-=pi*Math.log(pi);
    }
    float[] dz=new float[candidate.actionCount];
    for(int j=0;j<candidate.actionCount;j++){
     if((step.legalMask()&(1<<j))==0)continue;
     double indicator=(j==step.actionId()?1:0);
     double ent=config.entropyCoef()*p[j]*(Math.log(Math.max(1e-12,p[j]))+h);
     dz[j]=(float)(scale*(indicator-p[j])+ent);
    }
    float diff=fp.value()-target[t];
    valueLoss+=.5*diff*diff;
    backprop(candidate,step.state64(),fp,dz,config.valueCoef()*diff,gradients);
   }
   adam.step(candidate,gradients,config,onPolicy.size());
   // StudentPolicyNet.toBytes validates every float before persisting.
   candidate.toBytes();
   updates++;
  }
  lastKl=approximateKl(candidate,onPolicy);
  String after=candidate.sha256();
  if(before.equals(after)||updates==0)
   throw new IllegalStateException("ZERO_WEIGHT_CHANGE_OR_KL_REJECT");
  int changed=0;
  byte[] original=active.toBytes(),modified=candidate.toBytes();
  for(int i=0;i<original.length;i+=4) {
   int limit=Math.min(4,original.length-i);
   boolean different=false;
   for(int j=0;j<limit;j++)if(original[i+j]!=modified[i+j])different=true;
   if(different)changed++;
  }
  // Critical: only app-private models/candidates can be supplied, never
  // models/active, rollback, an unverified arbitrary directory or a symlink.
  Path destination=candidateWeights.toAbsolutePath().normalize();
  Path parent=destination.getParent();
  if(parent==null||!"candidates".equals(parent.getFileName().toString())||
     parent.getParent()==null||
     !"models".equals(parent.getParent().getFileName().toString())||
     java.nio.file.Files.isSymbolicLink(parent)||
     java.nio.file.Files.isSymbolicLink(destination))
   throw new IOException("PPO_CANDIDATE_DESTINATION_REQUIRED");
  candidate.saveAtomically(candidateWeights);
  if(!after.equals(StudentPolicyNet.load(candidateWeights).sha256()))
   throw new IOException("PPO_CANDIDATE_CHECKSUM_MISMATCH");
  return new Report(before,after,onPolicy.size(),updates,changed,
    (float)(policyLoss/(onPolicy.size()*updates)),
    (float)(valueLoss/(onPolicy.size()*updates)),lastKl,stoppedForKl);
 }
}
