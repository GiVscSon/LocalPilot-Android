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
  check(c.snapshot().mode().equals("REALTIME_MODE")&&
    c.snapshot().owner().equals("NONE")&&!c.snapshot().waiting(),
    "dashboard snapshot reads existing singleton state");
  HarnessCoordinator.Ticket t=c.begin(epoch,30,123,1000);
  check(t.nonce.length()==22&&c.hasPending(),"one-use ticket");
  check(c.snapshot().waiting()&&!c.snapshot().actionInFlight(),
    "dashboard reports waiting observation without claiming action");
  check(!c.changeMode(epoch,HarnessCoordinator.Mode.STEP_MODE),
    "mode cannot change under a pending agent ticket");
  check(c.start(HarnessCoordinator.Actor.MANUAL,epoch,"A",30,123,0,1001)==null,
    "manual actor cannot steal observation");
  check(!c.consume(t.nonce,next,30,123,1002),"wrong ROM epoch rejected");
  check(c.hasPending(),"wrong epoch cannot consume the current observation");
  c.stopForEpoch(epoch,"TEST_RESET");
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
  check(c.snapshot().owner().equals("GGUF")&&c.snapshot().actionInFlight(),
    "dashboard reports real GGUF ownership");
  check(c.start(HarnessCoordinator.Actor.MANUAL,epoch,"B",30,123,0,4004)==null,
    "manual input cannot interleave with in-flight model");
  check(!c.complete(action,epoch,44,456,true,true,10000),
    "ACK without fifteen new emulated frames is not verified");
  check(!c.actionInFlight(),"bad proof releases permit");
  t=c.begin(epoch,60,345,6000);
  check(c.consume(t.nonce,epoch,60,345,6001),"valid repeat observation");
  action=c.start(HarnessCoordinator.Actor.GGUF,epoch,"A",60,345,16,6002);
  check(c.complete(action,epoch,76,345,true,true,10000),
    "unchanged pixels do not invalidate verified frame transport");
  check(!c.actionInFlight(),"verified transport releases action");
  check(c.changeMode(epoch,HarnessCoordinator.Mode.STEP_MODE),"Student uses frozen STEP mode");
  t=c.begin(epoch,76,345,6150);
  check(c.consume(t.nonce,epoch,76,345,6151),"student consumes same nonce gate");
  HarnessCoordinator.ActionPermit student=c.start(
    HarnessCoordinator.Actor.STUDENT,epoch,"RIGHT",76,345,16,6152);
  check(student!=null&&c.snapshot().owner().equals("STUDENT"),
    "trained student has an explicit and exclusive real input owner");
  check(c.start(HarnessCoordinator.Actor.GGUF,epoch,"A",76,345,16,6153)==null,
    "GGUF cannot interleave while student executes");
  check(c.complete(student,epoch,92,400,true,true,10000),
    "student still requires the same emulated frame/ACK proof");
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
  check(c.snapshot().owner().equals("NONE")&&
    c.snapshot().lastStopReason().equals("USER_STOP"),
    "dashboard displays final STOP state");
  check(!c.complete(manual,epoch,96,789,true,true,10000),
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
  check(c.complete(scripted,next,106,500,true,true,10000),
    "valid distinct transport ACK and frame advancement");
  c.reset("1099",HarnessCoordinator.Mode.STEP_MODE);
  t=c.begin("1099",45,333,10000);
  check(!c.consume("n".repeat(22),"1099",45,333,10001)&&c.hasPending(),"old nonce cannot consume a newer ticket");
  c.consume(t.nonce,"1099",45,333,10001);
  HarnessCoordinator.ActionPermit shortStep=c.start(HarnessCoordinator.Actor.STUDENT,"1099","RIGHT",45,333,4,10002);
  check(shortStep!=null&&c.authorizesStep("1099",shortStep.nonce,4,10003),"exact short-step lease authorized");
  check(!c.authorizesStep("1099",shortStep.nonce,8,10003),"lease cannot change its duration");
  check(!c.authorizesStep("1099",shortStep.nonce,4,10002+ConsoleAgentContract.TTL_MS+1),"step lease expires");
  check(c.complete(shortStep,"1099",49,444,true,true,10010),"four-frame proof accepted for Student");
  t=c.begin("1099",49,444,10100);c.consume(t.nonce,"1099",49,444,10101);
  shortStep=c.start(HarnessCoordinator.Actor.STUDENT,"1099","RIGHT",49,444,8,10102);
  long revision=c.requestStop("1099","USER_STOP");
  check(!c.authorizesStep("1099",shortStep.nonce,8,10103),"STOP revokes JS lease immediately");
  check(c.start(HarnessCoordinator.Actor.MANUAL,"1099","A",49,444,0,10103)==null,"STOP_PENDING blocks all owners");
  c.stop("LATE_CALLBACK");
  check(c.confirmStop("1099",revision),"late callback cannot strand release ACK revision");
  t=c.begin("1099",49,444,10200);c.consume(t.nonce,"1099",49,444,10201);
  HarnessCoordinator.ActionPermit fresh=c.start(HarnessCoordinator.Actor.STUDENT,"1099","RIGHT",49,444,4,10202);
  check(!c.stopForEpoch("1042","STALE_ACTIVITY_DESTROY")&&c.actionInFlight(),"old Activity cannot stop a new epoch");
  check(!c.complete(shortStep,"1099",57,555,true,true,10203)&&c.actionInFlight(),"old callback cannot revoke a new owner");
  check(!c.complete(fresh,"1099",54,555,true,true,10204),"STEP proof must match exact authorized frame budget");
  c.reset("1100",HarnessCoordinator.Mode.STEP_MODE);
  t=c.begin("1100",45,333,10300);c.consume(t.nonce,"1100",45,333,10301);
  check(c.start(HarnessCoordinator.Actor.STUDENT,"1100","RIGHT",45,333,4,10300+ConsoleAgentContract.TTL_MS+1)==null,
    "expired prepared ticket cannot start action");
  check(!c.hasPending(),"expired prepared owner does not hold input forever");
  c.reset("1101",HarnessCoordinator.Mode.REALTIME_MODE);
  check(c.start(HarnessCoordinator.Actor.HEURISTIC,"1101","START",45,333,16,10500)==null,
    "Autonomous profiles never acquire Start or Select");
  var human=c.start(HarnessCoordinator.Actor.MANUAL,"1101","START",45,333,0,10501);
  check(human!=null,"Real NES menu remains usable by the human through the same owner");
  check(c.complete(human,"1101",60,333,true,true,10502),"Manual Start has ordinary release and frame proof");
  c.reset("1200",HarnessCoordinator.Mode.REALTIME_MODE);
  String sha="a".repeat(64);
  check(c.reserveEpisode("1200","wrong-task",sha,2)==null,"task incompatible weights rejected");
  var lease=c.reserveEpisode("1200",HarnessCoordinator.PPO_TASK,sha,2);
  check(lease!=null&&c.snapshot().owner().equals("PPO_STUDENT"),"episode reserves input before freeze");
  check(!c.changeMode("1200",HarnessCoordinator.Mode.STEP_MODE),"other owner cannot change reserved episode mode");
  check(c.changeEpisodeMode(lease),"episode freezes through same coordinator");
  check(c.start(HarnessCoordinator.Actor.PPO_STUDENT,"1200","RIGHT",30,123,16,11000)==null,"generic start cannot bypass PPO observation");
  check(c.startEpisodeStep(lease,sha,"RIGHT",30,123,11000)==null,"episode still requires prepared observation");
  t=c.beginEpisodeObservation(lease,30,123,11000);
  check(c.consume(t.nonce,"1200",30,123,11001),"episode observation uses common one-use ticket");
  check(c.startEpisodeStep(lease,"b".repeat(64),"RIGHT",30,123,11002)==null,"different weights cannot consume reserved observation");
  var ppo=c.startEpisodeStep(lease,sha,"RIGHT",30,123,11002);
  check(ppo!=null&&c.authorizesStep("1200",ppo.nonce,16,11003),"PPO uses common per-frame JS fence");
  check(c.complete(ppo,"1200",46,321,true,true,11004),"first exact PPO transition");
  check(c.hasPending()&&c.start(HarnessCoordinator.Actor.MANUAL,"1200","A",46,321,0,11005)==null,
    "between-step gap remains reserved");
  check(!c.finishEpisode(lease),"cannot silently finish a partial series");
  t=c.beginEpisodeObservation(lease,46,321,11006);
  c.consume(t.nonce,"1200",46,321,11007);
  ppo=c.startEpisodeStep(lease,sha,"LEFT",46,321,11008);
  check(c.complete(ppo,"1200",62,123,true,true,11009),"second exact PPO transition");
  check(c.startEpisodeStep(lease,sha,"LEFT",62,123,11010)==null,"episode action budget cannot overrun");
  check(c.finishEpisode(lease)&&!c.hasPending(),"fully verified series explicitly releases reservation");
  lease=c.reserveEpisode("1200",HarnessCoordinator.PPO_TASK,sha,1);
  t=c.beginEpisodeObservation(lease,62,123,11011);
  revision=c.requestStop("1200","PPO_STOP");
  check(!c.ownsEpisode(lease)&&!c.consume(t.nonce,"1200",62,123,11012),"STOP invalidates pending episode observation");
  check(c.confirmStop("1200",revision),"PPO STOP still needs release ACK");
  check(c.startEpisodeStep(lease,sha,"RIGHT",62,123,11013)==null,"old episode cannot resurrect after ACK");
  System.out.println("HARNESS_COORDINATOR_PASS checks="+checks);
 }
}
