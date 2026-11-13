package org.localpilot.shared;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Numerical backprop tests use manufactured steps that satisfy the data
 * contract. They are NOT real JSNES experience or device training claims.
 */
public final class PpoGaeTrainerTest {
 private static int checks;
 private static void yes(boolean value,String message){
  checks++;if(!value)throw new AssertionError(message);
 }
 private static void blocked(Runnable action,String message){
  boolean threw=false;
  try{action.run();}
  catch(IllegalArgumentException|IllegalStateException expected){threw=true;}
  yes(threw,message);
 }
 private static VerifiedReplayFilter.Step record(StudentPolicyNet base,int step,
           boolean finalStep,Float reward,boolean ack,String sha){
  int x=128+step;
  float[] state=HomebrewObs64.encode(x,112);
  float[] next=HomebrewObs64.encode(x+1,112);
  StudentPolicyNet.Forward fp=base.forward(state,HomebrewObs64.LEGAL_MASK);
  return new VerifiedReplayFilter.Step(
    "synthetic-contract-fixture","555",VerifiedReplayFilter.Provenance.RETRO_EMULATED,
    "a".repeat(64),sha,HomebrewObs64.SCHEMA,
    step,15+16*step,31+16*step,16,state,next,1,
    HomebrewObs64.ACTIONS,HomebrewObs64.LEGAL_MASK,
    (float)Math.log(fp.probs()[1]),fp.value(),
    reward,VerifiedReplayFilter.RewardSource.VERIFIED_ROM_RAM,
    ack,true,true,false,finalStep,false);
 }
 private static List<VerifiedReplayFilter.Step> roll(StudentPolicyNet model,int count){
  List<VerifiedReplayFilter.Step> steps=new ArrayList<>();
  for(int i=0;i<count;i++)
   steps.add(record(model,i,i==count-1,i%3==0?.6f:1f,true,model.sha256()));
  return steps;
 }
 public static void main(String[] args)throws Exception{
  var cfg=PpoGaeTrainer.DEFAULT;
  yes(cfg.clipEps()==.2f&&cfg.lambda()==.95f&&cfg.targetKl()==.03f,
     "explicit bounded PPO hyperparameters");
  yes(Math.abs(PpoGaeTrainer.clippedLoss(1.6,1,.2f)+1.2)<1e-6,
     "positive advantage clipped at 1.2");
  yes(Math.abs(PpoGaeTrainer.clippedLoss(1.6,-1,.2f)-1.6)<1e-6,
     "negative advantage at high ratio still punished");
  yes(PpoGaeTrainer.dClippedLossByRatio(1.6,1,.2f)==0,
     "no actor gradient on upper clipped plateau");
  yes(PpoGaeTrainer.dClippedLossByRatio(1.6,-1,.2f)==1,
     "negative advantage never gets false upper clip");
  yes(PpoGaeTrainer.dClippedLossByRatio(.6,-1,.2f)==0,
     "bad-action lower clipped plateau");
  blocked(()->new PpoGaeTrainer.Config(.99f,.95f,.2f,100,
    .01f,.5f,.03f,.5f,4),"explosive LR rejected");
  StudentPolicyNet policy=new StudentPolicyNet(6,17);
  String sha=policy.sha256();
  List<VerifiedReplayFilter.Step> eligible=roll(policy,16);
  yes(VerifiedReplayFilter.inspect(eligible).eligible(),
    "fixture satisfies structural on-policy contract");
  var targets=PpoGaeTrainer.computeTargets(policy,eligible,cfg);
  yes(targets.rawAdvantages().length==16&&targets.criticTargets().length==16,
    "GAE raw and value targets maintained separately");
  float[] raw=targets.rawAdvantages(),norm=targets.normalizedAdvantages(),val=targets.criticTargets();
  double mean=0;
  for(int i=0;i<16;i++){
   mean+=norm[i];
   yes(Math.abs((raw[i]+eligible.get(i).valueEstimate())-val[i])<1e-5,
     "critic uses raw, not normalized advantage");
  }
  yes(Math.abs(mean/16)<1e-5,"advantage batch mean near zero");
  Path root=Files.createTempDirectory("student-ppo-numerical");
  Path candidate=root.resolve("models/candidates/student-checkpoint.weights.bin");
  Path active=root.resolve("models/active/student-homebrew-v1.weights.bin");
  policy.saveAtomically(active);
  var report=PpoGaeTrainer.updateCandidate(policy,eligible,candidate,cfg);
  yes(report.eligibleSteps()==16,"16 samples used");
  yes(report.adamUpdates()>0&&report.adamUpdates()<=4,"bounded actual Adam updates");
  yes(report.changedParameters()>0,"actual actor/critic parameters changed");
  yes(report.shaBefore().equals(sha)&&!report.shaAfter().equals(sha),
     "real checkpoint SHA changes after backprop");
  yes(StudentPolicyNet.load(candidate).sha256().equals(report.shaAfter()),
     "new checkpoint bytes verify");
  yes(StudentPolicyNet.load(active).sha256().equals(sha),
     "old active weights never overwritten");
  yes(policy.sha256().equals(sha),"PPO cannot mutate active in memory");
  yes(Float.isFinite(report.avgPolicyLoss())&&Float.isFinite(report.avgValueLoss()),
     "numerically finite PPO losses");
  yes(report.lastApproxKl()>=0&&Float.isFinite(report.lastApproxKl()),
     "finite approximate KL");
  var trained=StudentPolicyNet.load(candidate);
  var before=policy.forward(HomebrewObs64.encode(128,112),63);
  var after=trained.forward(HomebrewObs64.encode(128,112),63);
  yes(Math.abs(before.value()-after.value())>0||
      Math.abs(before.probs()[1]-after.probs()[1])>0,
      "true inference output changes with trained weights");
  boolean blockedActive=false;
  try{PpoGaeTrainer.updateCandidate(policy,eligible,active,cfg);}
  catch(java.io.IOException expected){blockedActive=true;}
  yes(blockedActive,"trainer cannot save into active/");
  yes(StudentPolicyNet.load(active).sha256().equals(sha),
     "active unchanged after prohibited path");
  var missing=new ArrayList<>(eligible);
  missing.set(5,record(policy,5,false,null,true,sha));
  blocked(()->{
   try{PpoGaeTrainer.updateCandidate(policy,missing,candidate,cfg);}
   catch(java.io.IOException io){throw new IllegalStateException(io);}
  },"null reward not permitted to train");
  var badEpoch=new ArrayList<>(eligible);
  badEpoch.set(4,record(policy,4,false,1f,true,"f".repeat(64)));
  blocked(()->{
   try{PpoGaeTrainer.updateCandidate(policy,badEpoch,candidate,cfg);}
   catch(java.io.IOException io){throw new IllegalStateException(io);}
  },"policy SHA drift rejected");
  var noAck=new ArrayList<>(eligible);
  noAck.set(6,record(policy,6,false,1f,false,sha));
  blocked(()->{
   try{PpoGaeTrainer.updateCandidate(policy,noAck,candidate,cfg);}
   catch(java.io.IOException io){throw new IllegalStateException(io);}
  },"transport ACK missing -> cannot train");
  yes(StudentPolicyNet.load(candidate).sha256().equals(report.shaAfter()),
     "rejected data leaves existing candidate untouched");
  System.out.println("PPO_GAE_REAL_GRADIENT_PASS checks="+checks+
    " adamSteps="+report.adamUpdates()+" changedParams="+report.changedParameters()+
    " shaChanged="+!report.shaAfter().equals(sha)+
    " provenance=SYNTHETIC_CONTRACT_ONLY realNesTraining=false");
 }
}
