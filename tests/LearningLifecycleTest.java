package org.localpilot.android;

import java.nio.file.*;
import java.io.IOException;
import java.util.List;

/** Measured motion and persistence regressions. No physical-game acceptance implied. */
public final class LearningLifecycleTest {
 private static int checks;
 private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
 private static ArcadeObservation bird(long time,float y,float vy,boolean tracked){
  var p=new ArcadeObservation.Box(.09f,y-.02f,.15f,y+.02f);
  return new ArcadeObservation(ArcadeProfile.FLAPPY,time,p,p,List.of(),0,.04f,1,.9f,.12f,.65f,0,vy,.9f,tracked,false);
 }
 private static float param(ArcadeController c,String name){for(String token:c.model().split(" "))if(token.startsWith(name+"="))return Float.parseFloat(token.substring(name.length()+1));throw new AssertionError(name);}
 public static void main(String[]args)throws Exception{
  motion();response();verifiedImpulse();persistence();sharedStore();rules2048();
  System.out.println("Learning lifecycle: "+checks+" assertions PASS (synthetic, no phone test)");
 }
 private static void motion(){
  ArcadeController c=new ArcadeController();c.restore("gravity=1.1 flap=-0.6 scroll=0.33");
  c.decide(bird(1200,.3f,.1f,true),1200);String saved=c.model();
  check(!c.decide(bird(1150,.35f,-.8f,true),1250).sendsInput(),"out-of-order frame cannot renew input");
  check(saved.equals(c.model()),"out-of-order frame cannot erase learned physics");
  check(!c.decide(bird(1200,.31f,-.6f,true),1250).sendsInput(),"duplicate frame cannot renew input");
  check(saved.equals(c.model()),"duplicate frame cannot change learned physics");
  check(!c.decide(bird(1300,.3f,Float.NaN,true),1300).sendsInput(),"invalid velocity rejected");
  check(saved.equals(c.model()),"invalid velocity cannot poison model");
  c.nextRound();check(saved.equals(c.model()),"new round keeps model");c.penalize();check(saved.equals(c.model()),"loss is not a gravity measurement");
  c.reset();long[] timestamps={1000,1040,1220,1270,1480,1540};float gravity=1.1f,lastY=0;long last=0;
  for(long time:timestamps){float seconds=(time-1000)/1000f,y=.3f-.2f*seconds+.5f*gravity*seconds*seconds;
   float average=last==0?-.2f:(y-lastY)/((time-last)/1000f);
   c.decide(bird(time,y,average,true),time);lastY=y;last=time;
  }
  float measured=param(c,"gravity");
  check(measured<1.5f&&measured>1.1f,"irregular intervals converge toward true gravity");
  // Four three-position estimates, each blends 15%; independent expected result.
  check(Math.abs(measured-(1.1f+.4f*(float)Math.pow(.85,4)))<.001f,"gravity uses intervals between sample midpoints");
  c.reset();c.decide(bird(1000,.3f,.1f,true),1000);c.decide(bird(1080,.32f,.2f,true),1080);
  c.actionDispatched(new ArcadePolicy.Decision(ArcadePolicy.Action.FLAP,.6f,.4f,"test"),1100);
  c.decide(bird(1160,.30f,-.25f,true),1160);
  check(param(c,"gravity")==1.5f,"trajectory spanning flap excluded from gravity fit");
  c.reset();c.decide(bird(1000,.3f,0,true),1000);c.decide(bird(1040,.302f,.05f,true),1040);
  c.decide(bird(1500,.5f,.43f,true),1500);
  check(param(c,"gravity")==1.5f,"long capture gap excluded from gravity fit");
 }
 private static void response(){
  var action=new ArcadePolicy.Decision(ArcadePolicy.Action.FLAP,.6f,.4f,"test");
  var before=bird(1000,.4f,-.4f,true);
  check(!ArcadePolicy.response(action,before,bird(1200,.35f,-.1f,true)),"passive ascent is not response to ignored flap");
  check(ArcadePolicy.response(action,before,bird(1200,.30f,-.65f,true)),"extra upward impulse confirmed");
  check(!ArcadePolicy.response(action,before,bird(1000,.3f,-.8f,true)),"same timestamp rejected");
  check(!ArcadePolicy.response(action,before,bird(1800,.1f,-.8f,true)),"late unrelated motion rejected");
  check(!ArcadePolicy.response(action,bird(1000,.4f,0,false),bird(1200,.3f,-.4f,true)),"untracked baseline cannot establish flap response");
  ArcadeFeedback f=new ArcadeFeedback();long token=f.dispatched(action,before,1000);f.acknowledge(token);
  check(f.observe(bird(1200,.35f,-.1f,true),1200)==ArcadeFeedback.State.WAITING&&f.verified()==0,"ACK plus passive ascent still unverified");
  check(f.observe(bird(1300,.34f,.05f,true),1300)==ArcadeFeedback.State.RETRY&&f.verified()==0,"ignored flap receives no success count");
 }
 private static void verifiedImpulse(){
  var action=new ArcadePolicy.Decision(ArcadePolicy.Action.FLAP,.6f,.4f,"test");
  ArcadeController ignored=new ArcadeController();ignored.restore("gravity=1.1 flap=-0.6");
  var before=bird(1000,.4f,-.4f,true);ignored.decide(before,1000);ignored.actionDispatched(action,1000);
  ignored.decide(bird(1160,.35f,-.31f,true),1160);
  var passive=bird(1240,.33f,-.25f,true);ignored.observeGesture(action,before,passive);ignored.decide(passive,1240);
  check(param(ignored,"flap")==-.6f,"ignored tap during passive ascent cannot calibrate impulse");
  var unknownBefore=new ArcadeObservation(ArcadeProfile.FLAPPY,1000,null,null,List.of(),0,.04f,1,.9f,.12f,.65f,0,-.4f,0,true,false);
  ignored.observeGesture(action,unknownBefore,bird(1280,.30f,-.65f,true));
  check(param(ignored,"flap")==-.6f,"missing baseline object cannot authorize calibration or throw");
  ignored.decide(bird(1320,.314f,-.20f,true),1320);
  check(param(ignored,"flap")==-.6f,"later upward samples do not validate ignored tap");

  ArcadeController confirmed=new ArcadeController();confirmed.restore("gravity=1.1 flap=-0.6");
  var falling=bird(1000,.4f,.2f,true);confirmed.decide(falling,1000);confirmed.actionDispatched(action,1000);
  var response=bird(1160,.36f,-.5f,true);confirmed.decide(response,1160);
  check(param(confirmed,"flap")==-.6f,"dispatch plus visible sample still requires explicit response validation");
  confirmed.observeGesture(action,falling,response);
  confirmed.decide(bird(1240,.32f,-.48f,true),1240);
  check(param(confirmed,"flap")<-.6f,"confirmed extra impulse enables later fully post-command sample");

  float learned=param(confirmed,"flap");var nextBefore=bird(1320,.29f,-.30f,true);
  confirmed.decide(nextBefore,1320);learned=param(confirmed,"flap");confirmed.actionDispatched(action,1320);
  confirmed.observeGesture(action,falling,response); // Response precedes new dispatch.
  confirmed.decide(bird(1480,.25f,-.25f,true),1480);
  confirmed.decide(bird(1560,.235f,-.18f,true),1560);
  check(param(confirmed,"flap")==learned,"earlier confirmed tap cannot authorize next ignored tap");
  confirmed.nextRound();confirmed.decide(bird(2000,.4f,-.3f,true),2000);
  confirmed.decide(bird(2240,.35f,-.2f,true),2240);
  check(param(confirmed,"flap")==learned,"new round preserves impulse without retaining old response authority");
 }
 private static void persistence()throws Exception{
  Path dir=Files.createTempDirectory("pilot-learning-lifecycle-"),file=dir.resolve("models.log");
  ArcadeLearningStore store=new ArcadeLearningStore(file.toFile());ArcadeController c=new ArcadeController();
  c.restore("gravity=1.1 flap=-0.6 scroll=0.33");String original=c.model();
  store.save("example.bird","one",ArcadeProfile.FLAPPY,original);
  try{store.recordResult("example.bird","one",ArcadeProfile.FLAPPY,"bad\nmodel");throw new AssertionError("invalid model accepted");}catch(IOException expected){}
  check(store.losses("example.bird","one",ArcadeProfile.FLAPPY)==0,"rejected model must not increment loss count in memory");
  check(original.equals(store.get("example.bird","one",ArcadeProfile.FLAPPY)),"rejected update retains committed model");
  Path backup=dir.resolve("backup");Files.move(file,backup);Files.createDirectory(file);Files.writeString(file.resolve("obstacle"),"force move failure");
  try{store.recordResult("example.bird","one",ArcadeProfile.FLAPPY,"gravity=2.5");throw new AssertionError("write failure expected");}catch(IOException expected){}
  check(store.losses("example.bird","one",ArcadeProfile.FLAPPY)==0,"failed disk commit does not increment losses");
  check(original.equals(store.get("example.bird","one",ArcadeProfile.FLAPPY)),"failed disk commit does not replace in-memory model");
  try(var paths=Files.list(dir)){check(paths.noneMatch(p->p.toString().endsWith(".pending")),"failed commit removes temporary file");}
  Files.delete(file.resolve("obstacle"));Files.delete(file);Files.move(backup,file);
  store.recordResult("example.bird","one",ArcadeProfile.FLAPPY,original);
  ArcadeLearningStore reloaded=new ArcadeLearningStore(file.toFile());ArcadeController restored=new ArcadeController();
  restored.restore(reloaded.get("example.bird","one",ArcadeProfile.FLAPPY));
  check(original.equals(restored.model()),"new service instance restores exact motion model");
  check(reloaded.losses("example.bird","one",ArcadeProfile.FLAPPY)==1,"only successfully committed loss persists");
  check(reloaded.get("example.bird","two",ArcadeProfile.FLAPPY)==null,"other game never receives this model");
  check(reloaded.get("example.bird","one",ArcadeProfile.TRAFFIC)==null,"other mechanic never receives this model");
  Files.delete(file);Files.deleteIfExists(dir.resolve("models.log.lock"));Files.delete(dir);
 }
 private static void sharedStore()throws Exception{
  Path dir=Files.createTempDirectory("pilot-learning-shared-"),file=dir.resolve("models.log");
  ArcadeLearningStore first=new ArcadeLearningStore(file.toFile()),second=new ArcadeLearningStore(file.toFile());
  first.save("example.one","default",ArcadeProfile.FLAPPY,"gravity=1");
  second.save("example.two","default",ArcadeProfile.TRAFFIC,"roadSpeed=2");
  first.recordResult("example.one","default",ArcadeProfile.FLAPPY,"gravity=1");
  second.recordResult("example.one","default",ArcadeProfile.FLAPPY,"gravity=1");
  ArcadeLearningStore reloaded=new ArcadeLearningStore(file.toFile());
  check("gravity=1".equals(reloaded.get("example.one","default",ArcadeProfile.FLAPPY)),"first scoped model survives stale writer");
  check("roadSpeed=2".equals(reloaded.get("example.two","default",ArcadeProfile.TRAFFIC)),"second scoped model survives stale writer");
  check(reloaded.losses("example.one","default",ArcadeProfile.FLAPPY)==2,"both result increments survive stale writers");
  byte[] committed=Files.readAllBytes(file);
  try{first.save("example.one","default",ArcadeProfile.FLAPPY,"");throw new AssertionError("empty model accepted");}catch(IOException expected){}
  check(java.util.Arrays.equals(committed,Files.readAllBytes(file)),"invalid model leaves committed data unchanged");
  try(var handle=new java.io.RandomAccessFile(dir.resolve("models.log.lock").toFile(),"rw");var lock=handle.getChannel().lock()){
   try{first.save("example.one","default",ArcadeProfile.FLAPPY,"gravity=4");throw new AssertionError("locked store accepted write");}catch(IOException expected){}
  }
  check(java.util.Arrays.equals(committed,Files.readAllBytes(file)),"failed lock acquisition preserves committed data");
  java.util.concurrent.atomic.AtomicReference<Throwable> failure=new java.util.concurrent.atomic.AtomicReference<>();
  Runnable a=()->{try{for(int i=0;i<10;i++)first.recordResult("example.one","default",ArcadeProfile.FLAPPY,"gravity=1");}catch(Throwable error){failure.set(error);}};
  Runnable b=()->{try{for(int i=0;i<10;i++)second.recordResult("example.one","default",ArcadeProfile.FLAPPY,"gravity=1");}catch(Throwable error){failure.set(error);}};
  Thread left=new Thread(a),right=new Thread(b);left.start();right.start();left.join();right.join();
  check(failure.get()==null,"same-process writers serialize instead of failing on overlapping locks");
  check(new ArcadeLearningStore(file.toFile()).losses("example.one","default",ArcadeProfile.FLAPPY)==22,"concurrent increments are not lost");
  Files.writeString(file,"damaged-record\n",StandardOpenOption.APPEND);byte[] damaged=Files.readAllBytes(file);
  try{first.save("example.one","default",ArcadeProfile.FLAPPY,"gravity=5");throw new AssertionError("damaged file overwritten");}catch(IOException expected){}
  check(java.util.Arrays.equals(damaged,Files.readAllBytes(file)),"malformed source is not silently rewritten");
  Files.delete(file);Files.delete(dir.resolve("models.log.lock"));Files.delete(dir);
 }
 private static void rules2048(){
  Board2048 b=new Board2048(new int[]{2,2,4,0, 0,0,0,0, 0,0,0,0, 0,0,0,0});
  Board2048.Move m=b.move(Board2048.LEFT);
  check(m.board.cells[0]==4&&m.board.cells[1]==4&&m.reward==4,"tile merges at most once in one move");
  check(!b.verifies(m.board,Board2048.LEFT),"missing spawn cannot verify move");
  int[] after=m.board.cells.clone();after[15]=4;
  check(b.verifies(new Board2048(after),Board2048.LEFT),"new four is legitimate, not a bad read");
  after[14]=2;check(!b.verifies(new Board2048(after),Board2048.LEFT),"two spawns cannot verify one move");
  b=new Board2048(new int[]{2,2,4,8, 4,8,16,32, 8,16,32,64, 16,32,64,128});
  check(b.choose()>=0,"full board with merge is not game over");
  b=new Board2048(new int[]{2,4,2,4, 4,2,4,2, 2,4,2,4, 4,2,4,2});
  check(b.choose()==-1,"full alternating board has no legal move");
 }
}
