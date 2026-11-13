package org.localpilot.shared;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Contract fixture only: positive test demonstrates gate arithmetic, not real NES evidence. */
public final class StudentEvaluationGateTest {
 private static int checks;
 private static void check(boolean condition,String why){
  checks++;
  if(!condition)throw new AssertionError(why);
 }
 private static StudentPolicyNet forced(int action) {
  StudentPolicyNet policy=new StudentPolicyNet(HomebrewObs64.ACTIONS,11L);
  for(int i=0;i<policy.ba.length;i++)policy.ba[i]=0;
  policy.ba[action]=50;
  return policy;
 }
 private static final String ROM="a".repeat(64);
 private static Float fixtureReward(StudentEvaluationGate.Step step){
  if(step.frameAfter()-step.frameBefore()!=16||
     step.xBefore()<0||step.xBefore()>255||
     step.xAfter()<0||step.xAfter()>255)return null;
  int diff=step.xAfter()-step.xBefore();
  if(Math.abs(diff)>16||step.actionId()==0&&diff>0||
     step.actionId()==1&&diff<0||
     step.actionId()!=0&&step.actionId()!=1&&diff!=0)return null;
  return diff/16f;
 }

 private static List<StudentEvaluationGate.Step> rows(StudentPolicyNet active,
   StudentPolicyNet candidate) {
  List<StudentEvaluationGate.Step> list=new ArrayList<>();
  int[] starts={40,64,96,160,200};
  int scenario=0;
  for(int x:starts){
   for(var arm:StudentEvaluationGate.Arm.values()){
    StudentPolicyNet model=arm==StudentEvaluationGate.Arm.ACTIVE?active:candidate;
    int current=x;
    int startFrame=45+scenario*16;
    for(int i=0;i<4;i++){
     int choice=model.argmax(HomebrewObs64.encode(current,112),HomebrewObs64.LEGAL_MASK);
     int delta=choice==1?1:choice==0?-1:0;
     list.add(new StudentEvaluationGate.Step(
       "independent-"+scenario,arm,"holdout-"+scenario+"-"+arm,
       StudentEvaluationGate.Provenance.RETRO_EMULATED,ROM,model.sha256(),
       i,startFrame+i*16,startFrame+(i+1)*16,current,112,current+delta,112,
       choice,true,true,true));
     current+=delta;
    }
   }
   scenario++;
  }
  return list;
 }
 private static List<StudentEvaluationGate.Step> changed(List<StudentEvaluationGate.Step> input,
   int index,StudentEvaluationGate.Step replacement){
  var result=new ArrayList<>(input);
  result.set(index,replacement);
  return result;
 }
 public static void main(String[] args){
  StudentPolicyNet left=forced(0),right=forced(1);
  var normal=rows(left,right);
  var report=StudentEvaluationGate.evaluate(left,right,ROM,Set.of(128),normal,StudentEvaluationGateTest::fixtureReward);
  check(report.accepted()&&report.reason()==StudentEvaluationGate.Reason.ACCEPTED,
    "independent matched fixture with task progress passes");
  check(report.scenarios()==5&&report.candidateWins()==5,"paired scenarios count");
  check(report.candidateMeanReturn()>report.activeMeanReturn(),"actual arithmetic improvement");
  check(report.paired().size()==5,"individual scenario evidence preserved");
  check(left.sha256().equals(report.activeSha256()),"baseline SHA tied to report");
  check(right.sha256().equals(report.candidateSha256()),"candidate SHA tied to report");
  var first=normal.get(0);
  check(StudentEvaluationGate.evaluate(left,right,ROM,Set.of(40),normal,StudentEvaluationGateTest::fixtureReward).reason()
    ==StudentEvaluationGate.Reason.TRAINING_OVERLAP,"training initial state leakage denied");
  var missing=new ArrayList<>(normal);
  missing.remove(0);
  check(!StudentEvaluationGate.evaluate(left,right,ROM,Set.of(128),missing,StudentEvaluationGateTest::fixtureReward).accepted(),
    "missing core step cannot pass");
  var invented=new StudentEvaluationGate.Step(
   first.scenarioId(),first.arm(),first.epoch(),
   StudentEvaluationGate.Provenance.SYNTHETIC,first.romSha256(),first.policySha256(),
   first.sequence(),first.frameBefore(),first.frameAfter(),first.xBefore(),first.yBefore(),
   first.xAfter(),first.yAfter(),first.actionId(),true,true,true);
  check(StudentEvaluationGate.evaluate(left,right,ROM,Set.of(128),
    changed(normal,0,invented),StudentEvaluationGateTest::fixtureReward).reason()==StudentEvaluationGate.Reason.UNTRUSTED_PROVENANCE,
    "synthetic data rejected as holdout");
  var badAck=new StudentEvaluationGate.Step(
   first.scenarioId(),first.arm(),first.epoch(),first.provenance(),
   first.romSha256(),first.policySha256(),first.sequence(),first.frameBefore(),
   first.frameAfter(),first.xBefore(),first.yBefore(),first.xAfter(),first.yAfter(),
   first.actionId(),false,true,true);
  check(StudentEvaluationGate.evaluate(left,right,ROM,Set.of(128),
    changed(normal,0,badAck),StudentEvaluationGateTest::fixtureReward).reason()==StudentEvaluationGate.Reason.UNVERIFIED_CORE_STEP,
    "missing actual emulator input ACK rejected");
  var fakeRamJump=new StudentEvaluationGate.Step(
   first.scenarioId(),first.arm(),first.epoch(),first.provenance(),
   first.romSha256(),first.policySha256(),first.sequence(),first.frameBefore(),
   first.frameAfter(),first.xBefore(),first.yBefore(),first.xAfter()+45,first.yAfter(),
   first.actionId(),true,true,true);
  check(StudentEvaluationGate.evaluate(left,right,ROM,Set.of(128),
    changed(normal,0,fakeRamJump),StudentEvaluationGateTest::fixtureReward).reason()==StudentEvaluationGate.Reason.INVALID_ROM_REWARD,
    "impossible zero-page jump cannot mint reward");
  var fakeDecision=new StudentEvaluationGate.Step(
   first.scenarioId(),first.arm(),first.epoch(),first.provenance(),
   first.romSha256(),first.policySha256(),first.sequence(),first.frameBefore(),
   first.frameAfter(),first.xBefore(),first.yBefore(),first.xAfter(),first.yAfter(),
   5,true,true,true);
  check(StudentEvaluationGate.evaluate(left,right,ROM,Set.of(128),
    changed(normal,0,fakeDecision),StudentEvaluationGateTest::fixtureReward).reason()==StudentEvaluationGate.Reason.ACTION_MISMATCH,
    "model may not be credited for an action it did not select");
  var wrongSha=new StudentEvaluationGate.Step(
   first.scenarioId(),first.arm(),first.epoch(),first.provenance(),
   first.romSha256(),"b".repeat(64),first.sequence(),first.frameBefore(),
   first.frameAfter(),first.xBefore(),first.yBefore(),first.xAfter(),first.yAfter(),
   first.actionId(),true,true,true);
  check(StudentEvaluationGate.evaluate(left,right,ROM,Set.of(128),
    changed(normal,0,wrongSha),StudentEvaluationGateTest::fixtureReward).reason()==StudentEvaluationGate.Reason.BAD_IDENTITY,
    "weights drift cannot pass holdout");
  StudentPolicyNet noProgress1=forced(4),noProgress2=forced(5);
  var stalled=StudentEvaluationGate.evaluate(noProgress1,noProgress2,ROM,Set.of(128),
    rows(noProgress1,noProgress2),StudentEvaluationGateTest::fixtureReward);
  check(!stalled.accepted()&&stalled.reason()==StudentEvaluationGate.Reason.NO_PROVEN_IMPROVEMENT,
    "no goal improvement never activates a candidate");
  try{
   report.paired().clear();
   throw new AssertionError("Report pair list mutable");
  }catch(UnsupportedOperationException expected){checks++;}
  check(StudentEvaluationGate.MIN_SCENARIOS==5,"minimum evidence independent resets");
  System.out.println("STUDENT_EVALUATION_GATE_FIXTURE_PASS checks="+checks+
    " source=SYNTHETIC_CONTRACT_ONLY realNesHoldout=false");
 }
}
