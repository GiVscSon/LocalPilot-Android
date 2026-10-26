package org.localpilot.android;

import java.util.List;

public final class RuntimeFlowTest {
 private static int checks;
 private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
 private static JumperState state(long time,float x){
  var player=new JumperState.ObjectBox(JumperState.Kind.PLAYER,x-10,490,x+10,530,.9f);
  var platforms=new java.util.ArrayList<JumperState.ObjectBox>();
  for(int y:new int[]{120,300,450,700,1000})platforms.add(new JumperState.ObjectBox(JumperState.Kind.PLATFORM,300,y,450,y+15,.9f));
  return new JumperState(854,1280,0,0,854,1280,time,player,platforms,List.of(),.9f,0,0,0);
 }
 public static void main(String[] args){
  JumperFeedback f=new JumperFeedback();
  long token=f.dispatched(JumperPolicy.Action.TAP_LEFT,400,1000);
  check(f.observe(state(1100,380),1100)==JumperFeedback.State.WAITING,"movement alone cannot replace Android ACK");
  check(!f.acknowledge(token+1)&&f.acknowledge(token),"only own gesture ACK accepted");
  f.started(token,1200);
  check(f.observe(state(1150,380),1250)==JumperFeedback.State.WAITING,"journal/pre-dispatch motion is not response");
  check(f.observe(state(1300,420),1300)==JumperFeedback.State.WAITING,"wrong directional motion not success");
  check(f.observe(state(1350,380),1350)==JumperFeedback.State.VERIFIED,"own ACK plus fresh directional motion verified");
  check(!f.acknowledge(token),"completed gesture callback invalidated");
  for(int i=0;i<3;i++){
   long now=2000+i*1000;token=f.dispatched(JumperPolicy.Action.TAP_RIGHT,400,now);
   if(i!=1)f.acknowledge(token);
   JumperState invalid=i==0?state(now+100,450):i==1?state(now+650,450):null;
   check(f.observe(invalid,now+650)==(i==2?JumperFeedback.State.PAUSED:JumperFeedback.State.RETRY),"stale/missing ACK/missing frames have finite deadline");
   check(!f.failedWithAck(),"missing ACK or usable response never trains negative physical response");
   check(!f.acknowledge(token),"expired token rejects late callback");
  }
  f.reset();token=f.dispatched(JumperPolicy.Action.TAP_RIGHT,400,5000);f.acknowledge(token);
  check(f.observe(state(5200,400),5200)==JumperFeedback.State.WAITING,"usable unchanged response observed before deadline");
  check(f.observe(null,5650)==JumperFeedback.State.RETRY&&f.failedWithAck(),"only a seen usable response can be a negative training signal");
  f.reset();token=f.dispatched(JumperPolicy.Action.TAP_RIGHT,400,5700);f.acknowledge(token);
  check(f.observe(state(6400,450),6400)==JumperFeedback.State.RETRY&&!f.failedWithAck(),"late movement is outside action response window");
  f.reset();token=f.dispatched(JumperPolicy.Action.TAP_RIGHT,400,6000);f.cancel(token);
  check(!f.acknowledge(token)&&f.observe(state(6200,430),6200)==JumperFeedback.State.READY,"dropped dispatch never verifies");
  token=f.dispatched(JumperPolicy.Action.TAP_LEFT,400,7000);f.acknowledge(token);f.reset();
  long replacement=f.dispatched(JumperPolicy.Action.TAP_LEFT,400,7100);
  check(replacement!=token&&!f.acknowledge(token),"reset rejects previous session ACK");
  f.acknowledge(replacement);
  check(f.observe(state(7800,380),7300)==JumperFeedback.State.WAITING,"future observation rejected");
  check(f.observe(state(7100,380),7800)==JumperFeedback.State.RETRY,"same-time/pre-dispatch pixels never prove success");
  edgeFeedback();
  for(int rows=3;rows<=10;rows++)for(int cols=3;cols<=10;cols++){
   var window=new GameViewport.Box(120,80,1200,2200);
   var initial=RegionPickerGeometry.initial(window,rows,cols);
   check(window.contains(initial),"initial visible grid inside active window");
   var local=RegionPickerGeometry.local(initial,30,45);
   check(RegionPickerGeometry.selection(window,30,45,local.right(),local.bottom(),local.left(),local.top()).equals(initial),"reversed local drag restores exact screen geometry");
  }
  var window=new GameViewport.Box(100,100,1100,1900);
  var clipped=RegionPickerGeometry.selection(window,20,30,180,270,2000,3000);
  check(clipped.equals(new GameViewport.Box(200,300,1100,1900)),"drag endpoint clamped to selected window");
  for(float bad:new float[]{Float.NaN,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY}){
   try{RegionPickerGeometry.selection(window,20,30,bad,200,700,1000);throw new AssertionError("nonfinite accepted");}catch(IllegalArgumentException expected){checks++;}
  }
  try{RegionPickerGeometry.selection(window,0,0,10,200,900,1500);throw new AssertionError("outside start accepted");}catch(IllegalArgumentException expected){checks++;}
  System.out.println("Runtime flow: "+checks+" assertions PASS (feedback and region geometry; not Android UI execution)");
 }
 private static JumperState edgeState(long time,float x){
  var a=state(time,x);var p=new java.util.ArrayList<>(a.platforms);
  p.set(2,new JumperState.ObjectBox(JumperState.Kind.PLATFORM,100,450,220,465,.9f));
  p.set(3,new JumperState.ObjectBox(JumperState.Kind.PLATFORM,650,700,770,715,.9f));
  return new JumperState(a.frameWidth,a.frameHeight,a.roiLeft,a.roiTop,a.roiRight,a.roiBottom,time,a.player,p,a.hazards,.9f,0,0,0);
 }
 private static void edgeFeedback(){
  var f=new JumperFeedback();var right=edgeState(1000,830);var left=edgeState(1200,20);
  long token=f.dispatched(JumperPolicy.Action.TAP_RIGHT,right,1000);f.acknowledge(token);
  check(f.observe(left,1200)==JumperFeedback.State.VERIFIED,"rightward wrap is measured across opposite edges");
  f.reset();token=f.dispatched(JumperPolicy.Action.TAP_LEFT,edgeState(2000,20),2000);f.acknowledge(token);
  check(f.observe(edgeState(2200,830),2200)==JumperFeedback.State.VERIFIED,"leftward wrap is measured across opposite edges");
  f.reset();token=f.dispatched(JumperPolicy.Action.TAP_LEFT,right,1000);f.acknowledge(token);
  check(f.observe(left,1200)==JumperFeedback.State.WAITING,"rightward wrap cannot confirm left input");
  f.reset();token=f.dispatched(JumperPolicy.Action.TAP_LEFT,edgeState(1000,400),1000);f.acknowledge(token);
  check(f.observe(left,1200)==JumperFeedback.State.WAITING,"interior teleport is not an edge wrap or directional proof");
  f.reset();token=f.dispatched(JumperPolicy.Action.TAP_LEFT,830,1000);f.acknowledge(token);
  check(f.observe(left,1200)==JumperFeedback.State.WAITING,"no geometry baseline cannot reinterpret a huge jump as steering");
  f.reset();token=f.dispatched(JumperPolicy.Action.TAP_RIGHT,right,1000);f.acknowledge(token);
  var changed=new JumperState(900,1280,0,0,900,1280,1200,left.player,left.platforms,left.hazards,.9f,0,0,0);
  check(f.observe(changed,1200)==JumperFeedback.State.WAITING,"changed ROI cannot verify wrap");
  f.reset();token=f.dispatched(JumperPolicy.Action.TAP_RIGHT,right,1000);f.acknowledge(token);
  var lowPlayer=new JumperState.ObjectBox(JumperState.Kind.PLAYER,10,850,30,890,.9f);
  var low=new JumperState(854,1280,0,0,854,1280,1200,lowPlayer,left.platforms,left.hazards,.9f,0,0,0);
  check(f.observe(low,1200)==JumperFeedback.State.WAITING,"different vertical object across edge is not the same wrap");
  f.reset();token=f.dispatched(JumperPolicy.Action.TAP_RIGHT,right,1000);
  check(f.observe(left,1200)==JumperFeedback.State.WAITING,"even valid wrap cannot bypass own Android completion");
  f.reset();var clippedBefore=clippedScroll(1000,20,0);token=f.dispatched(JumperPolicy.Action.TAP_LEFT,clippedBefore,1000);f.acknowledge(token);
  check(f.observe(clippedScroll(1200,8,20),1200)==JumperFeedback.State.WAITING,"one camera frame of changing edge fragment is insufficient");
  check(f.observe(clippedScroll(1300,10,35),1300)==JumperFeedback.State.SCENE_PROGRESS,"coherent world scroll may release a clipped-width wait without steering reward");
  check(f.report().contains("verified=0")&&f.report().contains("scene_progress=1"),"camera progress from clipping never becomes a steering success");
 }
 private static JumperState clippedScroll(long time,float width,float shift){
  var a=edgeState(time,10);var p=a.platforms.stream().map(b->new JumperState.ObjectBox(b.kind,b.left,b.top+shift,b.right,b.bottom+shift,b.confidence)).toList();
  var player=new JumperState.ObjectBox(JumperState.Kind.PLAYER,0,490-shift*.25f,width,530-shift*.25f,.9f);
  return new JumperState(854,1280,0,0,854,1280,time,player,p,List.of(),.9f,0,0,0);
 }
}
