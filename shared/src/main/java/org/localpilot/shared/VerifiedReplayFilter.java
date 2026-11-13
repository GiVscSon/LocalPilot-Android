package org.localpilot.shared;

import java.util.List;

/**
 * Fail-closed admission of CONTIGUOUS, reward-bearing, on-policy NES batches.
 *
 * This does NOT mint proof of reward or authenticate user/imported records.
 * Only a trusted runtime score/RAM verifier may create the fields consumed here.
 * Existing ConsoleExperienceMemory (reward=UNKNOWN) is never PPO input.
 */
public final class VerifiedReplayFilter {
 private VerifiedReplayFilter(){}
 public static final int OBS_DIM=64, MIN_STEPS=16, MAX_STEPS=4096;
 public enum Provenance { RETRO_EMULATED, VERIFIED_REAL, SIMULATED_GAME, SYNTHETIC, MANUAL }
 public enum RewardSource { UNKNOWN, VERIFIED_ROM_RAM, VERIFIED_GAME_SCORE, PIXEL_DIFF, INPUT_ACK, MANUAL_LABEL }
 public enum Reason {
  ACCEPTED, EMPTY_OR_SHORT, BATCH_TOO_LARGE, INVALID_STEP, INVALID_IDENTITY,
  UNTRUSTED_PROVENANCE, MIXED_PROVENANCE, EPOCH_OR_POLICY_CHANGED, NOT_CONTIGUOUS,
  INVALID_OBSERVATION, ILLEGAL_ACTION, MISSING_ON_POLICY_PROBABILITY,
  UNVERIFIED_ACTION, REWARD_UNKNOWN_OR_UNTRUSTED, INVALID_EPISODE_END
 }
 public record Verdict(boolean eligible,Reason reason,int steps){
  public Verdict {
   if(reason==null||steps<0)throw new IllegalArgumentException("invalid replay verdict");
  }
 }
 /**
  * A PPO step must be recorded at the time the action is sampled, before any
  * optimizer update. A record assembled later from screen hashes is not enough.
  *
  * Arrays are copied on entry and exit: asynchronous CV cannot mutate a replay.
  */
 public record Step(
   String episodeId,String epoch,Provenance provenance,String romSha256,
   String policySha256,String observationSchema,int sequence,
   int frameBefore,int frameAfter,int durationFrames,
   float[] state64,float[] nextState64,int actionId,int actionCount,
   int legalMask,float oldLogProb,float valueEstimate,Float reward,
   RewardSource rewardSource,boolean inputAck,boolean postAckVisuallyVerified,
   boolean coreFramesVerified,boolean terminal,boolean truncated,boolean stopOccurred
 ) {
  public Step {
   if(state64!=null)state64=state64.clone();
   if(nextState64!=null)nextState64=nextState64.clone();
  }
  @Override public float[] state64(){return state64==null?null:state64.clone();}
  @Override public float[] nextState64(){return nextState64==null?null:nextState64.clone();}
 }
 private static boolean validHexSha(String s){
  return s!=null&&s.matches("[0-9a-f]{64}");
 }
 private static boolean finite64(float[] values){
  if(values==null||values.length!=OBS_DIM)return false;
  for(float value:values)if(!Float.isFinite(value))return false;
  return true;
 }
 private static boolean sameVector(float[] a,float[] b){
  if(a==null||b==null||a.length!=OBS_DIM||b.length!=OBS_DIM)return false;
  for(int i=0;i<OBS_DIM;i++)
   if(Float.floatToIntBits(a[i])!=Float.floatToIntBits(b[i]))return false;
  return true;
 }
 private static Verdict deny(Reason why,int size){return new Verdict(false,why,size);}
 /** Single actor snapshot, one schema, one ROM and one episode. No STOP gaps. */
 public static Verdict inspect(List<Step> trajectory){
  if(trajectory==null||trajectory.size()<MIN_STEPS)
   return deny(Reason.EMPTY_OR_SHORT,trajectory==null?0:trajectory.size());
  final int n=trajectory.size();
  if(n>MAX_STEPS)return deny(Reason.BATCH_TOO_LARGE,n);
  Step first=trajectory.get(0);
  if(first==null)return deny(Reason.INVALID_STEP,n);
  if(first.provenance()!=Provenance.RETRO_EMULATED
     &&first.provenance()!=Provenance.VERIFIED_REAL)
   return deny(Reason.UNTRUSTED_PROVENANCE,n);
  if(first.episodeId()==null||!first.episodeId().matches("[A-Za-z0-9_.-]{1,96}")
     ||first.epoch()==null||!first.epoch().matches("[1-9][0-9]{0,18}")
     ||!validHexSha(first.romSha256())||!validHexSha(first.policySha256())
     ||first.observationSchema()==null
     ||!first.observationSchema().matches("obs64-[A-Za-z0-9_.-]{1,32}"))
   return deny(Reason.INVALID_IDENTITY,n);
  Step previous=null;
  for(int i=0;i<n;i++){
   Step s=trajectory.get(i);
   if(s==null)return deny(Reason.INVALID_STEP,n);
   if(s.provenance()!=first.provenance())return deny(Reason.MIXED_PROVENANCE,n);
   if(!first.episodeId().equals(s.episodeId())||
      !first.epoch().equals(s.epoch())||
      !first.romSha256().equals(s.romSha256())||
      !first.policySha256().equals(s.policySha256())||
      !first.observationSchema().equals(s.observationSchema()))
    return deny(Reason.EPOCH_OR_POLICY_CHANGED,n);
   if(!finite64(s.state64)||!finite64(s.nextState64))
    return deny(Reason.INVALID_OBSERVATION,n);
   if(s.sequence()!=i||s.frameBefore()<15||s.durationFrames()<1||
      s.durationFrames()>600||s.frameAfter()<=s.frameBefore()||
      s.frameAfter()-s.frameBefore()!=s.durationFrames()||
      (previous!=null&&(s.frameBefore()!=previous.frameAfter()
                         ||!sameVector(s.state64,previous.nextState64))))
    return deny(Reason.NOT_CONTIGUOUS,n);
   if(s.actionCount()<2||s.actionCount()>16||s.actionId()<0||
      s.actionId()>=s.actionCount()||
      (s.legalMask()&~((1<<s.actionCount())-1))!=0||
      (s.legalMask()&(1<<s.actionId()))==0)
    return deny(Reason.ILLEGAL_ACTION,n);
   if(!Float.isFinite(s.oldLogProb())||s.oldLogProb()>0.000001f||
      !Float.isFinite(s.valueEstimate()))
    return deny(Reason.MISSING_ON_POLICY_PROBABILITY,n);
   if(!s.inputAck()||!s.postAckVisuallyVerified()||!s.coreFramesVerified())
    return deny(Reason.UNVERIFIED_ACTION,n);
   if(s.reward()==null||!Float.isFinite(s.reward())||
      (s.rewardSource()!=RewardSource.VERIFIED_GAME_SCORE
       &&s.rewardSource()!=RewardSource.VERIFIED_ROM_RAM))
    return deny(Reason.REWARD_UNKNOWN_OR_UNTRUSTED,n);
   // STOP and external truncation are not equivalent to game termination.
   if(s.terminal()&&s.truncated()||s.stopOccurred()&&!s.truncated()||
      (i<n-1&&(s.terminal()||s.truncated()))||
      (i==n-1&&!s.terminal()&&!s.truncated()))
    return deny(Reason.INVALID_EPISODE_END,n);
   previous=s;
  }
  return new Verdict(true,Reason.ACCEPTED,n);
 }
 /**
  * Old NES logs contain visual hash status and explicit UNKNOWN reward.
  * Even a valid hash-chained legacy record can never qualify for PPO.
  */
 public static Verdict inspectLegacyVisualMemory(List<ConsoleExperienceMemory.Episode> legacy){
  return deny(Reason.REWARD_UNKNOWN_OR_UNTRUSTED,legacy==null?0:legacy.size());
 }
}
