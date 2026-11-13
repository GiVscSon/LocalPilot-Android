package org.localpilot.shared;

public final class HarnessCoordinatorTest {
 private static int checks;
 private static void check(boolean good,String why){
  checks++;if(!good)throw new AssertionError(why);
 }
 public static void main(String[] args){
  HarnessCoordinator c=new HarnessCoordinator();
  String epoch="1042", next="1043";
  c.reset(epoch,HarnessCoordinator.Mode.REALTIME_MODE);
  HarnessCoordinator.Ticket t=c.begin(epoch,30,123,1000);
  check(t.nonce.length()==22&&c.hasPending(),"one-use ticket");
  check(!c.changeMode(epoch,HarnessCoordinator.Mode.STEP_MODE),
    "mode cannot change under a pending agent ticket");
  check(c.start(HarnessCoordinator.Actor.MANUAL,epoch,"A",30,123,0,1001)==null,
    "manual actor cannot steal observation");
  check(!c.consume(t.nonce,next,30,123,1002),"wrong ROM epoch rejected");
  check(!c.hasPending(),"wrong epoch consumed ticket");
  check(c.start(HarnessCoordinator.Actor.GGUF,epoch,"A",30,123,16,1003)==null,
    "cannot run model without its approval");
  t=c.begin(epoch,30,123,2000);
  check(!c.consume(t.nonce,epoch,31,123,2001),"new frame invalidates proposal");
  t=c.begin(epoch,30,123,3000);
  check(!c.consume(t.nonce,epoch,30,123,
    3000+ConsoleAgentContract.TTL_MS+1),"late proposal rejected");
  t=c.begin(epoch,30,123,4000);
  check(c.consume(t.nonce,epoch,30,123,4001),"valid prepared proposal");
  check(c.hasPending(),"prepared permit still owns input");
  check(c.start(HarnessCoordinator.Actor.HEURISTIC,epoch,"A",30,123,16,4002)==null,
    "scripted actor cannot overtake model");
  HarnessCoordinator.ActionPermit action=c.start(
    HarnessCoordinator.Actor.GGUF,epoch,"RIGHT",30,123,16,4003);
  check(action!=null&&c.actionInFlight(),"GGUF now owns one input");
  check(c.start(HarnessCoordinator.Actor.MANUAL,epoch,"B",30,123,0,4004)==null,
    "manual input cannot interleave with in-flight model");
  check(!c.complete(action,epoch,44,456,true,true),
    "ACK without fifteen new emulated frames is not verified");
  check(!c.actionInFlight(),"bad proof releases permit");
  t=c.begin(epoch,60,345,6000);
  check(c.consume(t.nonce,epoch,60,345,6001),"valid repeat observation");
  action=c.start(HarnessCoordinator.Actor.GGUF,epoch,"A",60,345,16,6002);
  check(c.complete(action,epoch,76,345,true,true),
    "unchanged pixels do not invalidate verified frame transport");
  check(!c.actionInFlight(),"verified transport releases action");
  check(c.changeMode(epoch,HarnessCoordinator.Mode.STEP_MODE),
    "empty harness can enter frozen-time mode");
  check(c.changeMode(epoch,HarnessCoordinator.Mode.REALTIME_MODE),
    "empty harness can resume realtime mode");
  HarnessCoordinator.ActionPermit manual=c.start(
    HarnessCoordinator.Actor.MANUAL,epoch,"LEFT",80,345,0,6200);
  check(manual!=null&&manual.actor==HarnessCoordinator.Actor.MANUAL,
    "manual input through same owner");
  c.stop("USER_STOP");
  check(c.stopRevision()>0&&c.lastStopReason().equals("USER_STOP"),
    "STOP creates revision and reason");
  check(!c.complete(manual,epoch,96,789,true,true),
    "STOP invalidates old action even with later ACK");
  t=c.begin(epoch,90,345,7000);
  c.reset(next,HarnessCoordinator.Mode.STEP_MODE);
  check(!c.consume(t.nonce,epoch,90,345,7001),"ROM switch fences model reply");
  check(c.start(HarnessCoordinator.Actor.HEURISTIC,epoch,"A",90,345,16,7002)==null,
    "old ROM input rejected");
  HarnessCoordinator.ActionPermit scripted=c.start(
    HarnessCoordinator.Actor.HEURISTIC,next,"A",90,345,16,7003);
  check(scripted!=null&&scripted.mode==HarnessCoordinator.Mode.STEP_MODE,
    "explicit execution mode is bound to permit");
  check(c.complete(scripted,next,106,500,true,true),
    "valid distinct transport ACK and frame advancement");
  System.out.println("HARNESS_COORDINATOR_PASS checks="+checks);
 }
}
