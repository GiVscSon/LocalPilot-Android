package org.localpilot.shared;
import java.util.ArrayList;
import java.util.List;
public final class HomebrewStudentNetworkTest {
 static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
 public static void main(String[] ignored){
  List<ConsoleExperienceMemory.GoalEpisode> samples=new ArrayList<>();
  for(int session=1;session<=4;session++){
   for(int i=0;i<6;i++){
    int bx=80+16*i,ax=bx+16;
    HomebrewGoalReward.Result task=HomebrewGoalReward.verify(
      "RIGHT",30+i*16,46+i*16,bx,112,ax,112);
    samples.add(new ConsoleExperienceMemory.GoalEpisode(
      Integer.toString(2000+session),"import:teacher","a".repeat(64),"RIGHT",
      30+i*16,46+i*16,12345,45678,"PIXELS_DIFFER",
      bx,112,ax,112,task.reward(),task.reached()));
   }
  }
  var result=HomebrewStudentNetwork.train(samples);
  check(result.trainingTransitions==18&&result.holdoutTransitions==6,
    "Independent held-out ROM session split");
  check(result.sourceSessions==4&&result.holdoutEpoch.equals("2004"),
    "Train/holdout separated by actual ROM epoch");
  check(result.weights.length==HomebrewStudentNetwork.WEIGHTS,
    "Real student neural tensor shape");
  double norm=0;
  for(double weight:result.weights){norm+=Math.abs(weight);check(Double.isFinite(weight),"finite weights");}
  check(norm>1,"Training changed CPU student weights");
  check(Double.isFinite(result.heldoutTdMae)&&Double.isFinite(result.zeroBaselineMae),
    "Heldout error is computed, not synthetic win rate");
  var proposal=HomebrewStudentNetwork.propose(result.weights,128,112);
  check(proposal.button()!=null&&proposal.margin()==proposal.margin(),"Candidate produced numerical score");
  boolean blocked=false;
  try{HomebrewStudentNetwork.train(samples.subList(0,6));}
  catch(IllegalArgumentException invalid){blocked=true;}
  check(blocked,"No training on too few samples");
  var bogus=samples.get(0);
  samples.set(0,new ConsoleExperienceMemory.GoalEpisode(
    bogus.epoch(),bogus.modelId(),bogus.modelSha(),bogus.button(),
    bogus.frameBefore(),bogus.frameAfter(),bogus.hashBefore(),bogus.hashAfter(),
    bogus.visualStatus(),bogus.beforeX(),bogus.beforeY(),
    bogus.afterX(),bogus.afterY(),9999,false));
  blocked=false;
  try{HomebrewStudentNetwork.train(samples);}
  catch(IllegalArgumentException invalid){blocked=true;}
  check(blocked,"Forged game reward must never train weights");
  System.out.println("NES_REWARD_STUDENT_TRAIN_PASS shape="+
    HomebrewStudentNetwork.WEIGHTS+" train="+result.trainingTransitions+
    " holdout="+result.holdoutTransitions);
 }
}
