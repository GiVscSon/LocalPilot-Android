package org.localpilot.shared;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.HashSet;

/** Tiny real trainable 7 → 12 → 6 CPU Q-network (tanh hidden units).
 * Learns ONLY from integrity-checked real 6502 homebrew task transitions.
 * TD/teacher holdout is NOT a proven on-policy game win. This does not modify GGUF.
 */
public final class HomebrewStudentNetwork {
 public static final String SCHEMA="NES_GOAL_STUDENT_7_12_6_V3_TASK_DIRECTIONAL_MASS";
 public enum Objective { TD, IMITATION }
 public static final int INPUTS=7,HIDDEN=12,ACTIONS=6;
 public static final int WEIGHTS=INPUTS*HIDDEN+HIDDEN+HIDDEN*ACTIONS+ACTIONS;
 public static final String[] BUTTONS={"A","B","UP","DOWN","LEFT","RIGHT"};
 private static final int W1=0,B1=INPUTS*HIDDEN,W2=B1+HIDDEN,B2=W2+HIDDEN*ACTIONS;
 private HomebrewStudentNetwork(){}
 public record Prediction(String button,int frames,double value,double margin,String admission){}
 /** Profile-specific duration adapter; it never changes a model's chosen button. */
 private static int duration(String button,int x,int y){
  int d=(button.equals("LEFT")||button.equals("RIGHT"))?
    Math.abs(HomebrewGoalReward.TARGET_X-x):Math.abs(HomebrewGoalReward.TARGET_Y-y);
  return d<=8?4:d<=16?8:16;
 }
 public static final class Candidate {
  public final double[] weights;
  public final double validationLoss,baselineLoss;
  public final Objective objective;
  public final int trainingTransitions,holdoutTransitions,sourceSessions;
  public final String trainingEpochs,holdoutEpoch;
  Candidate(double[] weights,double err,double baseline,int train,int test,int sessions,
    String trainingEpochs,String holdoutEpoch,Objective objective){
   this.weights=weights.clone();
   this.validationLoss=err;this.baselineLoss=baseline;this.objective=objective;
   this.trainingTransitions=train;this.holdoutTransitions=test;
   this.sourceSessions=sessions;this.trainingEpochs=trainingEpochs;this.holdoutEpoch=holdoutEpoch;
  }
  public boolean eligibleForSandboxTrial(){
   return validationLoss<baselineLoss*0.98 &&
     baselineLoss>0.05 && trainingTransitions>=8&&holdoutTransitions>=2;
  }
 }
 private static int action(String button){
  for(int i=0;i<BUTTONS.length;i++)if(BUTTONS[i].equals(button))return i;
  throw new IllegalArgumentException("Unrecognized model action");
 }
 private static double[] features(int x,int y){
  HomebrewGoalReward.distance(x,y);
  double dx=(HomebrewGoalReward.TARGET_X-x)/128.0;
  double dy=(HomebrewGoalReward.TARGET_Y-y)/128.0;
  return new double[]{1.0,dx,dy,Math.abs(dx),Math.abs(dy),x/255.0,y/255.0};
 }
 private static double[] output(double[] w,double[] f,double[] hidden){
  if(w==null||w.length!=WEIGHTS)throw new IllegalArgumentException("Wrong student tensor shape");
  double[] scores=new double[ACTIONS];
  for(int h=0;h<HIDDEN;h++){
   double z=w[B1+h];
   for(int i=0;i<INPUTS;i++)z+=w[W1+h*INPUTS+i]*f[i];
   hidden[h]=Math.tanh(z);
  }
  for(int a=0;a<ACTIONS;a++){
   scores[a]=w[B2+a];
   for(int h=0;h<HIDDEN;h++)scores[a]+=w[W2+a*HIDDEN+h]*hidden[h];
  }
  return scores;
 }
 private static double[] output(double[] w,int x,int y){
  return output(w,features(x,y),new double[HIDDEN]);
 }
 private static double clamp(double d,double max){return Math.max(-max,Math.min(max,d));}
 private static double target(double[] w,ConsoleExperienceMemory.GoalEpisode e){
  double r=clamp(e.reward()/16.0,3.0);
  if(e.reached())return r;
  double next=-1e9;
  for(double v:output(w,e.afterX(),e.afterY()))next=Math.max(v,next);
  return clamp(r+Math.pow(0.8,(e.frameAfter()-e.frameBefore())/16.0)*next,4.0);
 }
 private static void learn(double[] w,double[] targetWeights,ConsoleExperienceMemory.GoalEpisode e,double eta){
  int a=action(e.button());
  double[] x=features(e.beforeX(),e.beforeY()),hidden=new double[HIDDEN];
  double[] q=output(w,x,hidden);
  double error=clamp(target(targetWeights,e)-q[a],4.0);
  double[] upstream=new double[HIDDEN];
  for(int h=0;h<HIDDEN;h++)upstream[h]=
    error*w[W2+a*HIDDEN+h]*(1.0-hidden[h]*hidden[h]);
  for(int h=0;h<HIDDEN;h++){
   w[W2+a*HIDDEN+h]=clamp(w[W2+a*HIDDEN+h]+eta*error*hidden[h],12);
  }
  w[B2+a]=clamp(w[B2+a]+eta*error,12);
  for(int h=0;h<HIDDEN;h++){
   for(int i=0;i<INPUTS;i++)
    w[W1+h*INPUTS+i]=clamp(w[W1+h*INPUTS+i]+eta*upstream[h]*x[i],12);
   w[B1+h]=clamp(w[B1+h]+eta*upstream[h],12);
  }
 }
 private static double holdoutLoss(double[] w,List<ConsoleExperienceMemory.GoalEpisode> rows){
  double sum=0;
  for(var e:rows){
   double actual=output(w,e.beforeX(),e.beforeY())[action(e.button())];
   sum+=Math.abs(target(w,e)-actual);
  }
  return sum/rows.size();
 }
 private static void validate(ConsoleExperienceMemory.GoalEpisode e){
  if(e==null||e.epoch()==null||!e.epoch().matches("[1-9][0-9]{0,18}")||
    e.modelSha()==null||!e.modelSha().matches("[0-9a-f]{64}")||
    e.modelId()==null||!e.modelId().matches("[A-Za-z0-9:_.-]{1,96}")||
    !ConsoleAgentContract.studentFramesValid((long)e.frameAfter()-e.frameBefore()))
   throw new IllegalArgumentException("Unverified task sample");
  NesTrial proof=NesTrial.create("HOMEBREW",e.button(),e.frameBefore(),e.frameAfter(),e.hashBefore(),e.hashAfter());
  if(!proof.status.equals(e.visualStatus()))throw new IllegalArgumentException("Unverified frame status");
  HomebrewGoalReward.Result r=HomebrewGoalReward.verify(e.button(),e.frameBefore(),
    e.frameAfter(),e.beforeX(),e.beforeY(),e.afterX(),e.afterY());
  if(r.reward()!=e.reward()||r.reached()!=e.reached())
   throw new IllegalArgumentException("Cannot reproduce task reward from CPU state");
 }
 private record Split(List<ConsoleExperienceMemory.GoalEpisode> train,List<ConsoleExperienceMemory.GoalEpisode> holdout,List<String> epochs){}
 private static Split split(List<ConsoleExperienceMemory.GoalEpisode> verified){
  if(verified==null||verified.size()<12||verified.size()>10000)
   throw new IllegalArgumentException("Need 12-10000 verified task transitions");
  Map<String,List<ConsoleExperienceMemory.GoalEpisode>> groups=new TreeMap<>();
  for(var e:verified){validate(e);groups.computeIfAbsent(e.epoch(),k->new ArrayList<>()).add(e);}
  if(groups.size()<3)throw new IllegalStateException("Need three independent real NES sessions");
  List<String> epochs=new ArrayList<>(groups.keySet());
  String holdoutEpoch=epochs.get(epochs.size()-1);
  List<ConsoleExperienceMemory.GoalEpisode> train=new ArrayList<>(),
    holdout=new ArrayList<>();
  for(var entry:groups.entrySet()){
   if(entry.getKey().equals(holdoutEpoch))holdout.addAll(entry.getValue());
   else train.addAll(entry.getValue());
  }
  if(train.size()<8||holdout.size()<2)
   throw new IllegalStateException("Need 8 train + 2 held-out actions by ROM session");
  return new Split(train,holdout,epochs);
 }
 public static Candidate train(List<ConsoleExperienceMemory.GoalEpisode> verified){
  Split data=split(verified);
  var train=data.train;var holdout=data.holdout;var epochs=data.epochs;
  String holdoutEpoch=epochs.get(epochs.size()-1);
  Random seed=new Random(13072026);
  double[] w=new double[WEIGHTS];
  for(int i=0;i<w.length;i++)w[i]=(seed.nextDouble()-.5)*.045;
  // Stable TD labels: this snapshot never receives gradients. Periodic hard
  // synchronization follows the DQN target-network mechanism, not online max-Q.
  double[] targetWeights=w.clone();
  List<ConsoleExperienceMemory.GoalEpisode> order=new ArrayList<>(train);
  for(int epoch=0;epoch<110;epoch++){
   Collections.shuffle(order,new Random(1517+epoch));
   double eta=0.03/(1+epoch*.014);
   for(var e:order)learn(w,targetWeights,e,eta);
   if((epoch+1)%10==0)System.arraycopy(w,0,targetWeights,0,w.length);
  }
  for(double v:w)if(!Double.isFinite(v))throw new IllegalStateException("Unstable student weights");
  double zero=0;
  for(var e:holdout)zero+=Math.abs(e.reward()/16.0);
  zero/=holdout.size();
  return new Candidate(w,holdoutLoss(w,holdout),zero,train.size(),holdout.size(),
    epochs.size(),String.join(",",epochs.subList(0,epochs.size()-1)),holdoutEpoch,Objective.TD);
 }
 /** Trusted teacher for this exact RAM-profile task; never applied to arbitrary ROMs. */
 private static int teacher(ConsoleExperienceMemory.GoalEpisode e){
  int dx=HomebrewGoalReward.TARGET_X-e.beforeX(),dy=HomebrewGoalReward.TARGET_Y-e.beforeY();
  if(Math.abs(dx)+Math.abs(dy)<=2)return -1;
  return Math.abs(dx)>=Math.abs(dy)?action(dx>0?"RIGHT":"LEFT"):action(dy>0?"DOWN":"UP");
 }
 private static void imitate(double[] w,ConsoleExperienceMemory.GoalEpisode e,double eta){
  int label=teacher(e);if(label<0)return;
  double[] f=features(e.beforeX(),e.beforeY()),hidden=new double[HIDDEN];
  double[] logits=output(w,f,hidden),error=new double[ACTIONS],upstream=new double[HIDDEN];
  double max=Double.NEGATIVE_INFINITY,sum=0;
  for(double v:logits)max=Math.max(max,v);
  for(int a=0;a<ACTIONS;a++){error[a]=Math.exp(logits[a]-max);sum+=error[a];}
  for(int a=0;a<ACTIONS;a++)error[a]=(a==label?1:0)-error[a]/sum;
  for(int h=0;h<HIDDEN;h++){
   for(int a=0;a<ACTIONS;a++)upstream[h]+=error[a]*w[W2+a*HIDDEN+h];
   upstream[h]*=1-hidden[h]*hidden[h];
  }
  for(int a=0;a<ACTIONS;a++){
   for(int h=0;h<HIDDEN;h++)w[W2+a*HIDDEN+h]=clamp(w[W2+a*HIDDEN+h]+eta*error[a]*hidden[h],12);
   w[B2+a]=clamp(w[B2+a]+eta*error[a],12);
  }
  for(int h=0;h<HIDDEN;h++){
   for(int i=0;i<INPUTS;i++)w[W1+h*INPUTS+i]=clamp(w[W1+h*INPUTS+i]+eta*upstream[h]*f[i],12);
   w[B1+h]=clamp(w[B1+h]+eta*upstream[h],12);
  }
 }
 public static Candidate trainImitation(List<ConsoleExperienceMemory.GoalEpisode> verified){
  Split data=split(verified);
  var train=new ArrayList<>(data.train);var holdout=new ArrayList<>(data.holdout);
  train.removeIf(e->teacher(e)<0);holdout.removeIf(e->teacher(e)<0);
  if(train.size()<8||holdout.size()<2)throw new IllegalArgumentException("Insufficient nonterminal teacher states");
  double[] w=new double[WEIGHTS];Random seed=new Random(13072026);
  for(int i=0;i<w.length;i++)w[i]=(seed.nextDouble()-.5)*.045;
  int[] counts=new int[ACTIONS];for(var e:train)counts[teacher(e)]++;
  int majority=0;for(int a=1;a<ACTIONS;a++)if(counts[a]>counts[majority])majority=a;
  for(int pass=0;pass<200;pass++){
   Collections.shuffle(train,new Random(1517+pass));
   for(var e:train){
    double weight=HomebrewGoalReward.distance(e.beforeX(),e.beforeY())<=16?2:1;
    imitate(w,e,weight*0.04/(1+pass*.014));
   }
  }
  for(double value:w)if(!Double.isFinite(value))throw new IllegalStateException("Unstable imitation weights");
  int errors=0,baseline=0;
  for(var e:holdout){
   double[] q=output(w,e.beforeX(),e.beforeY());int best=0;
   for(int a=1;a<ACTIONS;a++)if(q[a]>q[best])best=a;
   if(best!=teacher(e))errors++;if(majority!=teacher(e))baseline++;
  }
  var epochs=data.epochs;
  return new Candidate(w,errors/(double)holdout.size(),baseline/(double)holdout.size(),
    train.size(),holdout.size(),epochs.size(),String.join(",",epochs.subList(0,epochs.size()-1)),
    epochs.get(epochs.size()-1),Objective.IMITATION);
 }
 private static boolean toward(String button,int x,int y){
  return switch(button){
   case "RIGHT"->x<HomebrewGoalReward.TARGET_X;case "LEFT"->x>HomebrewGoalReward.TARGET_X;
   case "DOWN"->y<HomebrewGoalReward.TARGET_Y;case "UP"->y>HomebrewGoalReward.TARGET_Y;
   default->false;
  };
 }
 public static Prediction proposeImitation(double[] weights,int x,int y){
  if(HomebrewGoalReward.distance(x,y)<=2)return new Prediction("NONE",0,1,1,"TERMINAL");
  double[] q=output(weights,x,y);double max=Double.NEGATIVE_INFINITY,sum=0;
  for(double v:q)max=Math.max(max,v);
  for(int a=0;a<ACTIONS;a++){q[a]=Math.exp(q[a]-max);sum+=q[a];}
  for(int a=0;a<ACTIONS;a++)q[a]/=sum;
  int best=0;for(int a=1;a<ACTIONS;a++)if(q[a]>q[best])best=a;
  double second=0;for(int a=0;a<ACTIONS;a++)if(a!=best)second=Math.max(second,q[a]);
  boolean confident=Double.isFinite(q[best])&&q[best]>=0.55&&q[best]-second>=0.1;
  double directionalMass=0;
  for(int a=0;a<ACTIONS;a++)if(toward(BUTTONS[a],x,y))directionalMass+=q[a];
  // Ambiguity between two goal-directed axes is different from uncertainty
  // about direction. This exact known task permits either; never change argmax.
  boolean taskAdmitted=Double.isFinite(directionalMass)&&directionalMass>=0.99&&toward(BUTTONS[best],x,y);
  if(!confident&&!taskAdmitted)
   return new Prediction("NONE",0,q[best],q[best]-second,"NONE");
  return new Prediction(BUTTONS[best],duration(BUTTONS[best],x,y),q[best],q[best]-second,confident?"CONFIDENT":"TASK_DIRECTIONAL_MASS");
 }
 /** No confident action => NONE, the existing Harness controller performs no input. */
 public static Prediction propose(double[] weights,int x,int y){
  double[] scores=output(weights,x,y);
  int best=0;double second=Double.NEGATIVE_INFINITY;
  for(int i=1;i<ACTIONS;i++)if(scores[i]>scores[best])best=i;
  for(int i=0;i<ACTIONS;i++)if(i!=best)second=Math.max(second,scores[i]);
  if(!Double.isFinite(scores[best])||scores[best]<=0.06||
     scores[best]-second<0.035)return new Prediction("NONE",0,scores[best],scores[best]-second,"NONE");
  return new Prediction(BUTTONS[best],duration(BUTTONS[best],x,y),scores[best],scores[best]-second,"TD_CONFIDENT");
 }
}
