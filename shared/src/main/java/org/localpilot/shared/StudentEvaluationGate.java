package org.localpilot.shared;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Independent, paired real-JSNES evaluation of a StudentPolicy PPO candidate.
 *
 * Input is evidence from actual pinned 6502 core execution on SEPARATE emulator
 * resets, not the training replay. Verifies every deterministic actor decision
 * and computes actual ROM-RAM reward rather than trusting a reported score.
 * It NEVER changes active weights: promotion remains disabled until physical
 * device holdout and rollback support are independently accepted.
 *
 * A synthetic Java test of this verifier is not evidence of an NES win.
 */
public final class StudentEvaluationGate {
 private StudentEvaluationGate() {}
 public static final int MIN_SCENARIOS=5, MIN_STEPS_PER_ARM=4;
 public static final String TASK="builtin-homebrew-right-progress-v1";
 /** A concrete EnvironmentAdapter must verify game-specific evidence before
  * returning a numeric reward. Shared Harness has no dependency on GameLab. */
 @FunctionalInterface public interface RewardVerifier { Float fromVerifiedCoreRam(Step step); }
 public enum Arm { ACTIVE, CANDIDATE }
 public enum Provenance { RETRO_EMULATED, SYNTHETIC, MANUAL, UNKNOWN }
 public enum Reason {
  ACCEPTED, INSUFFICIENT_SCENARIOS, BAD_IDENTITY, UNTRUSTED_PROVENANCE,
  TRAINING_OVERLAP, PAIR_MISMATCH, INVALID_SEQUENCE, ACTION_MISMATCH,
  UNVERIFIED_CORE_STEP, INVALID_ROM_REWARD, NO_PROVEN_IMPROVEMENT
 }
 public record Step(
   String scenarioId,Arm arm,String epoch,Provenance provenance,
   String romSha256,String policySha256,int sequence,
   int frameBefore,int frameAfter,int xBefore,int yBefore,int xAfter,int yAfter,
   int actionId,boolean inputAck,boolean freshFrame,boolean coreVerified
 ) {
  public Step {
   if(scenarioId==null||arm==null||epoch==null||provenance==null||
      romSha256==null||policySha256==null)
    throw new IllegalArgumentException("Missing evaluation identity");
  }
 }
 public record Scenario(
   String id,int initialX,int initialY,float activeReturn,float candidateReturn,
   int activeSteps,int candidateSteps
 ) {}
 public record Report(boolean accepted,Reason reason,int scenarios,
                      float activeMeanReturn,float candidateMeanReturn,
                      int candidateWins,String activeSha256,
                      String candidateSha256,List<Scenario> paired) {
  public Report {
   if(reason==null||paired==null)throw new IllegalArgumentException("report");
   paired=List.copyOf(paired);
  }
  @Override public List<Scenario> paired(){return paired;}
 }
 private static boolean sha(String value){
  return value!=null&&value.matches("[0-9a-f]{64}");
 }
 private static Report reject(Reason reason,int scenarios,float a,float b,
                              int wins,String active,String candidate,
                              List<Scenario> pairs){
  return new Report(false,reason,scenarios,a,b,wins,active,candidate,pairs);
 }
 /** Only SHA-verified, separately reset, reward-bearing real emulator traces
  * may establish a holdout verdict. The given training X-values were observed
  * in the earlier PPO rollout; a holdout starting at any of them is rejected.
  */
 public static Report evaluate(StudentPolicyNet active,StudentPolicyNet candidate,
                               String pinnedRomSha256,Set<Integer> trainingX,
                               List<Step> rows,RewardVerifier rewardVerifier){
  if(active==null||candidate==null||!sha(pinnedRomSha256)||trainingX==null||
     rows==null||rewardVerifier==null)
   throw new IllegalArgumentException("Missing pinned evaluation inputs");
  String activeSha=active.sha256(),candidateSha=candidate.sha256();
  if(active.actionCount!=HomebrewObs64.ACTIONS||
     candidate.actionCount!=HomebrewObs64.ACTIONS||
     activeSha.equals(candidateSha))
   return reject(Reason.BAD_IDENTITY,0,0,0,0,activeSha,candidateSha,List.of());
  if(rows.size()<2*MIN_SCENARIOS*MIN_STEPS_PER_ARM)
   return reject(Reason.INSUFFICIENT_SCENARIOS,0,0,0,0,activeSha,candidateSha,List.of());
  Map<String,Map<Arm,List<Step>>> grouped=new java.util.TreeMap<>();
  for(Step row:rows){
   if(!row.scenarioId().matches("[A-Za-z0-9_.-]{1,48}")||
      !row.epoch().matches("[A-Za-z0-9_.-]{1,64}")||
      !sha(row.romSha256())||!sha(row.policySha256())||
      !row.romSha256().equals(pinnedRomSha256)||
      !row.policySha256().equals(row.arm()==Arm.ACTIVE?activeSha:candidateSha))
    return reject(Reason.BAD_IDENTITY,0,0,0,0,activeSha,candidateSha,List.of());
   if(row.provenance()!=Provenance.RETRO_EMULATED)
    return reject(Reason.UNTRUSTED_PROVENANCE,0,0,0,0,activeSha,candidateSha,List.of());
   grouped.computeIfAbsent(row.scenarioId(),key->new HashMap<>())
      .computeIfAbsent(row.arm(),key->new ArrayList<>()).add(row);
  }
  if(grouped.size()<MIN_SCENARIOS)
   return reject(Reason.INSUFFICIENT_SCENARIOS,grouped.size(),0,0,0,
                 activeSha,candidateSha,List.of());
  List<Scenario> scenarios=new ArrayList<>();
  Set<String> epochs=new HashSet<>();
  Set<Integer> startingX=new HashSet<>();
  float activeTotal=0,candidateTotal=0;
  int wins=0;
  for(var group:grouped.entrySet()){
   List<Step> left=group.getValue().get(Arm.ACTIVE);
   List<Step> right=group.getValue().get(Arm.CANDIDATE);
   if(left==null||right==null||
      left.size()!=MIN_STEPS_PER_ARM||right.size()!=MIN_STEPS_PER_ARM)
    return reject(Reason.PAIR_MISMATCH,scenarios.size(),0,0,0,
                  activeSha,candidateSha,scenarios);
   Step a=left.get(0),b=right.get(0);
   if(a.xBefore()!=b.xBefore()||a.yBefore()!=b.yBefore()||
      a.frameBefore()!=b.frameBefore()||
      a.epoch().equals(b.epoch())||
      !epochs.add(a.epoch())||!epochs.add(b.epoch())||
      !startingX.add(a.xBefore()))
    return reject(Reason.PAIR_MISMATCH,scenarios.size(),0,0,0,
                  activeSha,candidateSha,scenarios);
   if(trainingX.contains(a.xBefore()))
    return reject(Reason.TRAINING_OVERLAP,scenarios.size(),0,0,0,
                  activeSha,candidateSha,scenarios);
   float[] results=new float[2];
   for(int j=0;j<2;j++){
    List<Step> arm=j==0?left:right;
    StudentPolicyNet net=j==0?active:candidate;
    String epoch=arm.get(0).epoch();
    Step previous=null;
    for(int k=0;k<arm.size();k++){
     Step row=arm.get(k);
     if(!epoch.equals(row.epoch())||row.sequence()!=k||
        row.frameBefore()<15||row.frameAfter()-row.frameBefore()!=16||
        row.xBefore()<0||row.xBefore()>255||row.yBefore()<0||row.yBefore()>255||
        row.xAfter()<0||row.xAfter()>255||row.yAfter()<0||row.yAfter()>255||
        (previous!=null&&(row.frameBefore()!=previous.frameAfter()||
                           row.xBefore()!=previous.xAfter()||
                           row.yBefore()!=previous.yAfter())))
      return reject(Reason.INVALID_SEQUENCE,scenarios.size(),0,0,0,
                    activeSha,candidateSha,scenarios);
     if(row.actionId()<0||row.actionId()>=HomebrewObs64.ACTIONS||
        net.argmax(HomebrewObs64.encode(row.xBefore(),row.yBefore()),
                   HomebrewObs64.LEGAL_MASK)!=row.actionId())
      return reject(Reason.ACTION_MISMATCH,scenarios.size(),0,0,0,
                    activeSha,candidateSha,scenarios);
     if(!row.inputAck()||!row.freshFrame()||!row.coreVerified())
      return reject(Reason.UNVERIFIED_CORE_STEP,scenarios.size(),0,0,0,
                    activeSha,candidateSha,scenarios);
     Float verifiedReward=rewardVerifier.fromVerifiedCoreRam(row);
     if(verifiedReward==null||!Float.isFinite(verifiedReward))
      return reject(Reason.INVALID_ROM_REWARD,scenarios.size(),0,0,0,
                    activeSha,candidateSha,scenarios);
     results[j]+=verifiedReward;
     previous=row;
    }
   }
   activeTotal+=results[0];candidateTotal+=results[1];
   if(results[1]>results[0]+0.00001f)wins++;
   scenarios.add(new Scenario(group.getKey(),a.xBefore(),a.yBefore(),
     results[0],results[1],left.size(),right.size()));
  }
  float base=activeTotal/scenarios.size(),proposed=candidateTotal/scenarios.size();
  // Preset pilot gate: positive task progress and >=60% matched scenarios
  // improve, candidate mean > active by 0.1 reward. This is not statistical
  // evidence of general performance outside the pinned homebrew task.
  boolean improved=proposed>=base+0.10f&&proposed>0&&
      wins*5>=scenarios.size()*3;
  return new Report(improved,improved?Reason.ACCEPTED:Reason.NO_PROVEN_IMPROVEMENT,
                    scenarios.size(),base,proposed,wins,activeSha,candidateSha,scenarios);
 }
}
