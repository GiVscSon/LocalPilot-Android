package org.localpilot.shared;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Synthetic contract fixtures; not a claim about actual NES training. */
public final class VerifiedPpoReplayStoreTest {
 private static int checks;
 private static void check(boolean ok,String what){checks++;if(!ok)throw new AssertionError(what);}
 private static List<VerifiedReplayFilter.Step> sample(boolean valid){
  ArrayList<VerifiedReplayFilter.Step> rows=new ArrayList<>();
  for(int i=0;i<16;i++){
   float[] state=new float[64],next=new float[64];
   state[0]=i;next[0]=i+1;
   rows.add(new VerifiedReplayFilter.Step("sample-1","123",
     VerifiedReplayFilter.Provenance.RETRO_EMULATED,"a".repeat(64),"b".repeat(64),
     "obs64-homebrew-x-v1",i,15+i*16,31+i*16,16,state,next,i%2,2,3,
     (float)Math.log(.5),0.25f,valid?1f:null,
     valid?VerifiedReplayFilter.RewardSource.VERIFIED_ROM_RAM:
           VerifiedReplayFilter.RewardSource.UNKNOWN,
     true,true,true,false,i==15,false));
  }
  return rows;
 }
 public static void main(String[] args)throws Exception{
  Path vault=Files.createTempDirectory("nes-replay-storage");
  var rows=sample(true);
  check(VerifiedReplayFilter.inspect(rows).eligible(),"valid contract fixture");
  var first=VerifiedPpoReplayStore.append(vault,rows);
  check(first.steps()==16&&first.totalReward()==16f,"write exact reward-bearing batch");
  check(first.sha256().matches("[0-9a-f]{64}"),"SHA recorded");
  Path record=vault.resolve("episodes/onpolicy-v1").resolve(first.filename());
  check(Files.isRegularFile(record),"actual file persisted");
  var restored=VerifiedPpoReplayStore.read(record);
  check(VerifiedReplayFilter.inspect(restored).eligible()&&restored.size()==16,
    "readback and gate");
  check(restored.get(7).state64()[0]==7f&&
    restored.get(7).nextState64()[0]==8f,"observations persisted, not hashes only");
  check(restored.get(7).oldLogProb()==(float)Math.log(.5)&&
    restored.get(7).reward()==1f,"sample likelihood and reward persisted");
  var again=VerifiedPpoReplayStore.append(vault,rows);
  check(first.equals(again),"duplicate batch is idempotent");
  var status=VerifiedPpoReplayStore.summary(vault);
  check(status.batches()==1&&status.steps()==16&&status.rewardSum()==16f,
    "live summary from durable verified records");
  boolean refused=false;
  try{VerifiedPpoReplayStore.append(vault,sample(false));}catch(java.io.IOException expected){refused=true;}
  check(refused,"unknown reward never persisted as PPO training");
  byte[] corrupt=Files.readAllBytes(record);corrupt[corrupt.length/2]^=1;
  Files.write(record,corrupt);
  refused=false;
  try{VerifiedPpoReplayStore.read(record);}catch(java.io.IOException expected){refused=true;}
  check(refused,"tampering fails closed");
  refused=false;
  try{VerifiedPpoReplayStore.summary(vault);}catch(java.io.IOException expected){refused=true;}
  check(refused,"tampered log not counted");
  System.out.println("VERIFIED_PPO_REPLAY_STORE_PASS checks="+checks+
    " realDeviceEpisodes=NOT_TESTED syntheticFixturesOnly=true");
 }
}
