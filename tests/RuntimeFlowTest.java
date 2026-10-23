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
   check(f.failedWithAck()==(i!=1),"missing ACK never trains negative physical response");
   check(!f.acknowledge(token),"expired token rejects late callback");
  }
  f.reset();token=f.dispatched(JumperPolicy.Action.TAP_RIGHT,400,6000);f.cancel(token);
  check(!f.acknowledge(token)&&f.observe(state(6200,430),6200)==JumperFeedback.State.READY,"dropped dispatch never verifies");
  token=f.dispatched(JumperPolicy.Action.TAP_LEFT,400,7000);f.acknowledge(token);f.reset();
  long replacement=f.dispatched(JumperPolicy.Action.TAP_LEFT,400,7100);
  check(replacement!=token&&!f.acknowledge(token),"reset rejects previous session ACK");
  f.acknowledge(replacement);
  check(f.observe(state(7800,380),7300)==JumperFeedback.State.WAITING,"future observation rejected");
  check(f.observe(state(7100,380),7800)==JumperFeedback.State.RETRY,"same-time/pre-dispatch pixels never prove success");
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
}
