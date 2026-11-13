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
  var repeated=HomebrewStudentNetwork.train(samples);
  check(java.util.Arrays.equals(result.weights,repeated.weights),
    "Same verified trajectories reproduce identical trained weights");
  // A saturated online network must not manufacture its own TD target.
  // Compare a single update against frozen zero vs terminal value sources.
  try{
   var update=HomebrewStudentNetwork.class.getDeclaredMethod("learn",double[].class,
     double[].class,ConsoleExperienceMemory.GoalEpisode.class,double.class);
   update.setAccessible(true);
   double[] online=new double[HomebrewStudentNetwork.WEIGHTS];
   double[] frozen=new double[HomebrewStudentNetwork.WEIGHTS];
   online[online.length-1]=2;
   double[] frozenCopy=frozen.clone();
   update.invoke(null,online,frozen,samples.get(0),0.03);
   check(java.util.Arrays.equals(frozen,frozenCopy),"TD update never mutates target snapshot");
   check(online[online.length-1]<2,"Frozen zero future value prevents online self-bootstrap inflation");
  }catch(ReflectiveOperationException e){throw new AssertionError(e);}
  check(result.trainingTransitions==18&&result.holdoutTransitions==6,
    "Independent held-out ROM session split");
  check(result.sourceSessions==4&&result.holdoutEpoch.equals("2004"),
    "Train/holdout separated by actual ROM epoch");
  check(result.weights.length==HomebrewStudentNetwork.WEIGHTS,
    "Real student neural tensor shape");
  double norm=0;
  for(double weight:result.weights){norm+=Math.abs(weight);check(Double.isFinite(weight),"finite weights");}
  check(norm>1,"Training changed CPU student weights");
  check(Double.isFinite(result.validationLoss)&&Double.isFinite(result.baselineLoss),
    "Heldout error is computed, not synthetic win rate");
  var proposal=HomebrewStudentNetwork.propose(result.weights,128,112);
  check(proposal.button()!=null&&proposal.margin()==proposal.margin(),"Candidate produced numerical score");
  boolean blocked=false;
  try{HomebrewStudentNetwork.train(samples.subList(0,6));}
  catch(IllegalArgumentException invalid){blocked=true;}
  check(blocked,"No training on too few samples");
  var imitation=HomebrewStudentNetwork.trainImitation(samples);
  var again=HomebrewStudentNetwork.trainImitation(samples);
  check(imitation.objective==HomebrewStudentNetwork.Objective.IMITATION,
    "Teacher classifier objective is not mislabeled as TD or PPO");
  check(java.util.Arrays.equals(imitation.weights,again.weights),"Imitation training is deterministic");
  check(!imitation.eligibleForSandboxTrial(),"Only-RIGHT heldout cannot beat majority baseline and is not promoted");
  var near=HomebrewStudentNetwork.proposeImitation(imitation.weights,189,112);
  check("RIGHT".equals(near.button())&&near.frames()==4,"Learned direction uses bounded precision adapter");
  check("NONE".equals(HomebrewStudentNetwork.proposeImitation(imitation.weights,192,112).button()),
    "Verified task terminal never sends an additional action");
  double[] ambiguous=new double[HomebrewStudentNetwork.WEIGHTS];
  java.util.Arrays.fill(ambiguous,ambiguous.length-6,ambiguous.length,-12);
  ambiguous[ambiguous.length-6+4]=0.02; // LEFT is the original argmax.
  ambiguous[ambiguous.length-6+2]=0; // UP is the other credible direction.
  var diagonal=HomebrewStudentNetwork.proposeImitation(ambiguous,194,115);
  check("LEFT".equals(diagonal.button())&&"TASK_DIRECTIONAL_MASS".equals(diagonal.admission()),
    "Known task admits model argmax when both credible directions approach the goal");
  check("NONE".equals(HomebrewStudentNetwork.proposeImitation(ambiguous,190,115).button()),
    "Task admission never substitutes a different button when model argmax points away");
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
