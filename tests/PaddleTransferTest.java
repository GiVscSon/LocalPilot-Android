package org.localpilot.android;

import java.nio.file.*;
import java.util.List;
import java.util.Random;
import org.localpilot.android.rl.PaddleWeightLab;

/** Tests the REAL arcade decision path, not only the offline simulator. */
public final class PaddleTransferTest {
 private static int assertions;
 private static void check(boolean valid,String message){
  assertions++;if(!valid)throw new AssertionError(message);
 }
 private static ArcadeObservation scene(long now,float confidence,boolean tracked){
  return new ArcadeObservation(ArcadeProfile.PADDLE,now,
    new ArcadeObservation.Box(.59f,.48f,.61f,.50f),
    new ArcadeObservation.Box(.48f,.89f,.64f,.93f),
    List.of(new ArcadeObservation.Box(.05f,.10f,.15f,.15f)),
    .04f,.04f,.96f,.96f,0,0,.10f,.60f,confidence,tracked,false);
 }
 public static void main(String[] args)throws Exception{
  var base=PaddleWeightLab.Weights.followBall();
  for(PaddleWeightLab.Scenario variant:PaddleWeightLab.Scenario.values()){
   var first=PaddleWeightLab.play(base,777,variant);
   var second=PaddleWeightLab.play(base,777,variant);
   check(first.equals(second),"scenario must be deterministic: "+variant);
   check(first.steps()>0&&first.steps()<=2400,"bounded scenario");
  }
  PaddleTransfer.Candidate trained=PaddleTransfer.trainCandidate();
  System.out.println("REAL_MODEL_TRANSFER "+trained.report());
  check(trained.eligible()&&trained.allScenariosImproved(),"held-out eight-scenario gate");
  check(!new PaddleTransfer.Candidate(trained.offset(),320,480,1,100,1,100,false).eligible(),
    "aggregate gain can never conceal a failing game scenario");
  check(trained.trainingEpisodes()==320&&trained.validationEpisodes()==480,
    "train/test episode counts");
  String encoded=PaddleTransfer.encode(trained);
  check(Math.abs(PaddleTransfer.decode(encoded)-trained.offset())<.0001,
    "round trip trained output-bias weight");
  var s=scene(1000,.97f,true);
  var original=ArcadePolicy.next(s);
  var altered=PaddleTransfer.decide(s,encoded);
  check(original.action==ArcadePolicy.Action.MOVE_PADDLE,"original PADDLE gesture");
  check(altered.action==ArcadePolicy.Action.MOVE_PADDLE,"trained PADDLE gesture");
  check(Math.abs(original.x-altered.x)>.001f,"trained parameters change live target");
  check(Math.abs(original.x-altered.x)<.045f*(s.right-s.left),"bounded adaptation");

  var controller=new ArcadeController();
  check(!controller.paddleTransferActive(),"default policy must be unchanged");
  controller.installPaddleTransfer(encoded);
  check(controller.paddleTransferActive(),"real policy consumes a trained candidate");
  check(Math.abs(controller.decide(s,1000).x-altered.x)<.00001f,
    "real ArcadeController must use the same trained policy");
  check(controller.decide(s,1100).action==ArcadePolicy.Action.WAIT,
    "replayed frame cannot authorize another gesture");
  controller.reset();
  check(!controller.paddleTransferActive(),"reset cancels live transfer");

  var lowConfidence=scene(1200,.7f,true);
  check(PaddleTransfer.decide(lowConfidence,encoded).action==ArcadePolicy.next(lowConfidence).action,
    "no simulation authority on low confidence");
  check(PaddleTransfer.decide(s,"invalid").x==original.x,"corrupt model falls back");
  check(PaddleTransfer.decide(scene(1200,.97f,false),encoded).x==
     ArcadePolicy.next(scene(1200,.97f,false)).x,"untracked ball falls back");
  try{PaddleTransfer.decode("LP-PADDLE-WEIGHTS-v1,100000");throw new AssertionError("unsafe weight accepted");}
  catch(IllegalArgumentException expected){assertions++;}

  Path temp=Files.createTempDirectory("paddle-transfer-");
  ArcadeLearningStore store=new ArcadeLearningStore(temp.resolve("learning.txt").toFile());
  store.savePaddleTransfer("ru.beru.android","paddle-a",encoded);
  check(encoded.equals(store.paddleTransfer("ru.beru.android","paddle-a")),"correct game scoped model");
  check(store.paddleTransfer("ru.beru.android","paddle-b")==null,"another game slot must stay unmodified");
  check(store.paddleTransfer("com.example.game","paddle-a")==null,"another package must stay unmodified");
  ArcadeLearningStore reopened=new ArcadeLearningStore(temp.resolve("learning.txt").toFile());
  check(encoded.equals(reopened.paddleTransfer("ru.beru.android","paddle-a")),"atomic persistence");
  reopened.disablePaddleTransfer("ru.beru.android","paddle-a");
  check("disabled".equals(reopened.paddleTransfer("ru.beru.android","paddle-a")),"fail-closed rollback survives restart");
  System.out.println("REAL_PADDLE_TRANSFER_PASS checks="+assertions);
 }
}
