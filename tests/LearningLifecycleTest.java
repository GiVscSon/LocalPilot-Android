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
  motion();response();persistence();rules2048();
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
  Files.delete(file);Files.delete(dir);
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
