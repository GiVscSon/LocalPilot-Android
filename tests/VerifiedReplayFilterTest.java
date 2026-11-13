package org.localpilot.shared;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Contract-only fixtures. NEVER count as real NES reward/training evidence. */
public final class VerifiedReplayFilterTest {
 private static int checks;
 private static final String ROM_SHA="a".repeat(64),POLICY_SHA="b".repeat(64);
 private static class Change {
  VerifiedReplayFilter.Provenance origin=VerifiedReplayFilter.Provenance.RETRO_EMULATED;
  VerifiedReplayFilter.RewardSource rewardSource=VerifiedReplayFilter.RewardSource.VERIFIED_ROM_RAM;
  Float reward=1f;
  boolean ack=true,visual=true,core=true,terminal=false,truncated=false,stop=false;
  String episode="test-episode",epoch="123",rom=ROM_SHA,policy=POLICY_SHA,schema="obs64-nes-v1";
  float oldLogProb=(float)Math.log(.5),value=0.5f;
  int action=0,actionCount=2,legalMask=3,seqBias=0,frameBias=0,duration=4;
  boolean invalidState=false,brokenLink=false;
 }
 private static VerifiedReplayFilter.Step sample(int i,Change c) {
  float[] x=new float[64],y=new float[64];
  x[0]=i+(c.brokenLink?17:0);y[0]=i+1;
  if(c.invalidState)x[4]=Float.NaN;
  return new VerifiedReplayFilter.Step(c.episode,c.epoch,c.origin,c.rom,c.policy,c.schema,
    i+c.seqBias,15+4*i+c.frameBias,19+4*i+c.frameBias,c.duration,
    x,y,c.action,c.actionCount,c.legalMask,c.oldLogProb,c.value,c.reward,
    c.rewardSource,c.ack,c.visual,c.core,c.terminal,c.truncated,c.stop);
 }
 private static List<VerifiedReplayFilter.Step> baseline(){
  List<VerifiedReplayFilter.Step> result=new ArrayList<>();
  for(int i=0;i<16;i++){
   Change c=new Change();
   if(i==15)c.truncated=true;
   result.add(sample(i,c));
  }
  return result;
 }
 private static void check(boolean pass,String label){
  checks++;if(!pass)throw new AssertionError(label);
 }
 private static void rejectAt(int index,Consumer<Change> edit,VerifiedReplayFilter.Reason reason) {
  List<VerifiedReplayFilter.Step> rows=baseline();
  Change c=new Change();
  if(index==15)c.truncated=true;
  edit.accept(c);
  rows.set(index,sample(index,c));
  VerifiedReplayFilter.Verdict got=VerifiedReplayFilter.inspect(rows);
  check(!got.eligible()&&got.reason()==reason,
   "reject "+reason+" at "+index+" actual="+got.reason());
 }
 public static void main(String[] args) {
  VerifiedReplayFilter.Verdict happy=VerifiedReplayFilter.inspect(baseline());
  check(happy.eligible()&&happy.reason()==VerifiedReplayFilter.Reason.ACCEPTED,
   "valid synthetic contiguous task contract");
  check(VerifiedReplayFilter.inspect(null).reason()==VerifiedReplayFilter.Reason.EMPTY_OR_SHORT,
   "null denied");
  check(VerifiedReplayFilter.inspect(baseline().subList(0,15)).reason()
    ==VerifiedReplayFilter.Reason.EMPTY_OR_SHORT,"short batch denied");
  rejectAt(6,c->c.origin=VerifiedReplayFilter.Provenance.SYNTHETIC,
    VerifiedReplayFilter.Reason.MIXED_PROVENANCE);
  rejectAt(0,c->c.origin=VerifiedReplayFilter.Provenance.SYNTHETIC,
    VerifiedReplayFilter.Reason.UNTRUSTED_PROVENANCE);
  rejectAt(8,c->c.origin=VerifiedReplayFilter.Provenance.VERIFIED_REAL,
    VerifiedReplayFilter.Reason.MIXED_PROVENANCE);
  rejectAt(8,c->c.epoch="124",VerifiedReplayFilter.Reason.EPOCH_OR_POLICY_CHANGED);
  rejectAt(8,c->c.rom="c".repeat(64),VerifiedReplayFilter.Reason.EPOCH_OR_POLICY_CHANGED);
  rejectAt(8,c->c.policy="c".repeat(64),VerifiedReplayFilter.Reason.EPOCH_OR_POLICY_CHANGED);
  rejectAt(8,c->c.schema="obs64-nes-v2",VerifiedReplayFilter.Reason.EPOCH_OR_POLICY_CHANGED);
  rejectAt(8,c->c.episode="forged",VerifiedReplayFilter.Reason.EPOCH_OR_POLICY_CHANGED);
  rejectAt(0,c->c.rom="invalid",VerifiedReplayFilter.Reason.INVALID_IDENTITY);
  rejectAt(4,c->c.seqBias=1,VerifiedReplayFilter.Reason.NOT_CONTIGUOUS);
  rejectAt(4,c->c.frameBias=1,VerifiedReplayFilter.Reason.NOT_CONTIGUOUS);
  rejectAt(4,c->c.duration=5,VerifiedReplayFilter.Reason.NOT_CONTIGUOUS);
  rejectAt(4,c->c.brokenLink=true,VerifiedReplayFilter.Reason.NOT_CONTIGUOUS);
  rejectAt(4,c->c.invalidState=true,VerifiedReplayFilter.Reason.INVALID_OBSERVATION);
  rejectAt(4,c->c.legalMask=2,VerifiedReplayFilter.Reason.ILLEGAL_ACTION);
  rejectAt(4,c->c.action=5,VerifiedReplayFilter.Reason.ILLEGAL_ACTION);
  rejectAt(4,c->c.legalMask=7,VerifiedReplayFilter.Reason.ILLEGAL_ACTION);
  rejectAt(4,c->c.oldLogProb=Float.NaN,
    VerifiedReplayFilter.Reason.MISSING_ON_POLICY_PROBABILITY);
  rejectAt(4,c->c.oldLogProb=.3f,
    VerifiedReplayFilter.Reason.MISSING_ON_POLICY_PROBABILITY);
  rejectAt(4,c->c.value=Float.POSITIVE_INFINITY,
    VerifiedReplayFilter.Reason.MISSING_ON_POLICY_PROBABILITY);
  rejectAt(4,c->c.ack=false,VerifiedReplayFilter.Reason.UNVERIFIED_ACTION);
  rejectAt(4,c->c.visual=false,VerifiedReplayFilter.Reason.UNVERIFIED_ACTION);
  rejectAt(4,c->c.core=false,VerifiedReplayFilter.Reason.UNVERIFIED_ACTION);
  rejectAt(4,c->c.reward=null,VerifiedReplayFilter.Reason.REWARD_UNKNOWN_OR_UNTRUSTED);
  rejectAt(4,c->c.reward=Float.NaN,VerifiedReplayFilter.Reason.REWARD_UNKNOWN_OR_UNTRUSTED);
  rejectAt(4,c->c.rewardSource=VerifiedReplayFilter.RewardSource.PIXEL_DIFF,
    VerifiedReplayFilter.Reason.REWARD_UNKNOWN_OR_UNTRUSTED);
  rejectAt(4,c->c.rewardSource=VerifiedReplayFilter.RewardSource.INPUT_ACK,
    VerifiedReplayFilter.Reason.REWARD_UNKNOWN_OR_UNTRUSTED);
  rejectAt(4,c->c.rewardSource=VerifiedReplayFilter.RewardSource.MANUAL_LABEL,
    VerifiedReplayFilter.Reason.REWARD_UNKNOWN_OR_UNTRUSTED);
  rejectAt(4,c->c.terminal=true,VerifiedReplayFilter.Reason.INVALID_EPISODE_END);
  rejectAt(4,c->c.truncated=true,VerifiedReplayFilter.Reason.INVALID_EPISODE_END);
  rejectAt(4,c->c.stop=true,VerifiedReplayFilter.Reason.INVALID_EPISODE_END);
  rejectAt(15,c->{c.stop=true;c.terminal=true;},VerifiedReplayFilter.Reason.INVALID_EPISODE_END);
  rejectAt(15,c->c.truncated=false,VerifiedReplayFilter.Reason.INVALID_EPISODE_END);
  List<VerifiedReplayFilter.Step> tooLong=baseline();
  VerifiedReplayFilter.Step first=tooLong.get(0);
  while(tooLong.size()<=VerifiedReplayFilter.MAX_STEPS)tooLong.add(first);
  check(VerifiedReplayFilter.inspect(tooLong).reason()==VerifiedReplayFilter.Reason.BATCH_TOO_LARGE,
    "memory-sized batch bound");
  List<ConsoleExperienceMemory.Episode> legacy=List.of(
    new ConsoleExperienceMemory.Episode("model","c".repeat(64),"A",30,46,9,10,"PIXELS_DIFFER"));
  check(VerifiedReplayFilter.inspectLegacyVisualMemory(legacy).reason()
     ==VerifiedReplayFilter.Reason.REWARD_UNKNOWN_OR_UNTRUSTED,
     "real legacy screen hash is never reward for PPO");
  List<VerifiedReplayFilter.Step> rows=baseline();
  VerifiedReplayFilter.Step frozen=rows.get(0);
  float[] leaked=frozen.state64();
  leaked[0]=Float.NaN;
  check(Float.isFinite(frozen.state64()[0]),"defensive arrays out");
  Change c=new Change();
  float[] state=new float[64],next=new float[64];
  VerifiedReplayFilter.Step own=new VerifiedReplayFilter.Step(
    "test-episode","123",c.origin,c.rom,c.policy,c.schema,0,15,19,4,
    state,next,0,2,3,c.oldLogProb,.5f,1f,c.rewardSource,true,true,true,false,true,false);
  state[0]=Float.NaN;
  check(Float.isFinite(own.state64()[0]),"defensive arrays in");
  // Current admission verifies the *contract*, not the origin of arbitrary
  // fabricated Java values. Production runtime needs a trusted reward producer.
  System.out.println("VERIFIED_REPLAY_FILTER_CONTRACT_PASS checks="+checks
     +" fixtures=synthetic only actualNesTraining=NOT_PROVEN");
 }
}
