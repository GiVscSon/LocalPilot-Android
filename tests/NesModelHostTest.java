package org.localpilot.shared;

/** Pure contract fixtures; neither native GGUF inference nor device evidence. */
public final class NesModelHostTest {
 private static int checks;
 private static void check(boolean good,String message){checks++;if(!good)throw new AssertionError(message);}
 private static void refuses(Runnable operation,String message){
  boolean failed=false;try{operation.run();}catch(IllegalArgumentException expected){failed=true;}check(failed,message);
 }
 public static void main(String[] args){
  HarnessCoordinator h=new HarnessCoordinator();h.reset("1",HarnessCoordinator.Mode.STEP_MODE);
  var model=new NesModelHost.ModelIdentity("gguf:test","b".repeat(64));
  var t=h.begin("1",15,123,1000);
  var o=new NesModelHost.Observation(t,"a".repeat(64),model,128,112,null);
  check(NesModelHost.current(h,o,1000),"actual waiting ticket is current");
  check(!NesModelHost.current(h,o,999),"clock reversal refused");
  check(NesModelHost.current(h,o,1000+ConsoleAgentContract.TTL_MS),"unchanged TTL inclusive boundary");
  check(!NesModelHost.current(h,o,1001+ConsoleAgentContract.TTL_MS),"late model result refused");
  refuses(()->new NesModelHost.Observation(t,"UNKNOWN",model,128,112,null),"unknown ROM identity refused");
  refuses(()->new NesModelHost.Observation(t,"a".repeat(64),model,-1,112,null),"partial RAM observation refused");
  refuses(()->new NesModelHost.Observation(t,"a".repeat(64),model,256,112,null),"invalid RAM observation refused");
  refuses(()->new NesModelHost.Observation(t,"a".repeat(64),model,128,112,new byte[100]),"fake PNG refused");
  var proposal=new NesModelHost.Reply(o,NesModelHost.Status.PROPOSAL,"gguf:test","b".repeat(64),"RIGHT",3);
  check(proposal.observation().ticket()==t,"reply keeps original one-use ticket identity");
  check(h.consume(t.nonce,"1",15,123,1002),"only controller may consume the observation");
  check(!NesModelHost.current(h,o,1003),"completed proposal cannot be reused");
  check(h.start(HarnessCoordinator.Actor.GGUF,"1","RIGHT",15,123,16,1003)!=null,"original controller remains sole input issuer");
  refuses(()->new NesModelHost.Reply(o,NesModelHost.Status.PROPOSAL,"gguf:test","b".repeat(64),"START",0),"model cannot propose manual-only START");
  refuses(()->new NesModelHost.Reply(o,NesModelHost.Status.PROPOSAL,"gguf:test","changed","RIGHT",0),"reply needs exact weights SHA");
  refuses(()->new NesModelHost.Reply(o,NesModelHost.Status.PROPOSAL,"gguf:other","b".repeat(64),"RIGHT",0),"reply cannot change requested model identity");
  refuses(()->new NesModelHost.Reply(o,NesModelHost.Status.PROPOSAL,"gguf:test","c".repeat(64),"RIGHT",0),"reply cannot change requested weights");
  h.stop("USER_STOP");
  check(!NesModelHost.current(h,o,1004),"STOP revokes delayed reply");
  var next=h.begin("1",31,456,1100);
  check(!NesModelHost.current(h,o,1101),"old reply cannot consume new pending observation");
  var current=new NesModelHost.Observation(next,"a".repeat(64),model,-1,-1,null);
  check(NesModelHost.current(h,current,1101),"explicit UNKNOWN RAM does not fabricate state");
  h.reset("2",HarnessCoordinator.Mode.STEP_MODE);
  check(!NesModelHost.current(h,current,1102),"ROM reset revokes response");
  var lease=h.reserveEpisode("2",HarnessCoordinator.PPO_TASK,"c".repeat(64),1);
  var student=h.beginEpisodeObservation(lease,15,123,1200);
  check(!NesModelHost.current(h,new NesModelHost.Observation(student,"a".repeat(64),model,128,112,null),1201),"GGUF port cannot steal Student episode");
  h.stop();h.reset("3",HarnessCoordinator.Mode.REALTIME_MODE);
  var live=h.begin("3",15,123,1300);
  check(!NesModelHost.current(h,new NesModelHost.Observation(live,"a".repeat(64),model,128,112,null),1301),"slow GGUF does not gain real-time input");
  System.out.println("NES_MODEL_HOST_CONTRACT_PASS checks="+checks+" nativeInference=false physicalDevice=false");
 }
}
