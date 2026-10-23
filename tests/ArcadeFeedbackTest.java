package org.localpilot.android;
import java.util.List;
public final class ArcadeFeedbackTest {
 private static int checks;
 private static void check(boolean v,String why){checks++;if(!v)throw new AssertionError(why);}
 private static ArcadeObservation state(long time,float y){
  var b=new ArcadeObservation.Box(.07f,y-.02f,.13f,y+.02f);
  return new ArcadeObservation(ArcadeProfile.FLAPPY,time,b,b,List.of(),0,.04f,1,.49f,.08f,.36f,0,.25f,.9f,true,false);
 }
 public static void main(String[]args){
  var f=new ArcadeFeedback();var before=state(1000,.3f);var action=ArcadePolicy.next(before);
  long id=f.dispatched(action,before,1000);
  check(f.observe(state(1200,.25f),1200)==ArcadeFeedback.State.WAITING,"visual movement cannot replace Android ACK");
  check(!f.acknowledge(id+1),"wrong ACK ignored");check(f.acknowledge(id),"own ACK accepted");
  check(f.observe(state(1000,.25f),1200)==ArcadeFeedback.State.WAITING,"pre-dispatch frame rejected");
  check(f.observe(state(1200,.25f),1200)==ArcadeFeedback.State.READY&&f.verified()==1,"visual lift confirms");
  for(int i=0;i<3;i++){
   long t=2000+i*1000;id=f.dispatched(action,state(t,.3f),t);check(f.acknowledge(id),"retry ACK");
   check(f.observe(state(t+200,.3f),t+200)==ArcadeFeedback.State.WAITING,"wait for fresh response");
   check(f.observe(state(t+350,.3f),t+350)==(i==2?ArcadeFeedback.State.PAUSED:ArcadeFeedback.State.RETRY),"bounded response retry");
  }
  check(f.paused()&&f.verified()==1,"failed actions never counted as successes");
  f.reset();check(!f.paused()&&!f.acknowledge(id),"reset invalidates old callback");
  id=f.dispatched(action,before,1000);f.acknowledge(id);
  check(f.observe(state(1100,.25f),1500)==ArcadeFeedback.State.RETRY&&f.verified()==0,"stale movement is not evidence and cannot suspend deadline");
  check(!f.acknowledge(id),"expired action rejects a delayed ACK");
  id=f.dispatched(action,state(2000,.3f),2000);f.acknowledge(id);
  check(f.observe(state(2600,.25f),2200)==ArcadeFeedback.State.WAITING,"future observation rejected before deadline");
  check(f.observe(state(2600,.25f),2500)==ArcadeFeedback.State.RETRY,"future observation cannot suspend deadline");
  id=f.dispatched(action,state(3000,.3f),3000);
  check(f.observe(state(3200,.25f),3200)==ArcadeFeedback.State.WAITING,"movement without ACK is never success");
  check(f.observe(state(3350,.25f),3350)==ArcadeFeedback.State.PAUSED&&f.verified()==0,"missing ACK expires and repeated failures pause");
  f.reset();id=f.dispatched(action,before,1000);f.acknowledge(id);
  check(f.observe(null,1200)==ArcadeFeedback.State.WAITING,"missing frame waits before deadline");
  check(f.observe(null,1350)==ArcadeFeedback.State.RETRY&&f.verified()==0,"missing frame expires without a visual reward");
  id=f.dispatched(action,state(2000,.3f),2000);f.acknowledge(id);
  var other=state(2350,.25f);
  other=new ArcadeObservation(ArcadeProfile.TRAFFIC,other.timestampMs,other.player,other.control,List.of(),0,.04f,1,.9f,0,1,0,0,.9f,true,false);
  check(f.observe(other,2350)==ArcadeFeedback.State.RETRY,"different profile cannot suspend deadline");
  f.reset();id=f.dispatched(action,before,1000);f.acknowledge(id);
  f.cancel(id);check(f.observe(state(1700,.3f),1700)==ArcadeFeedback.State.READY&&!f.acknowledge(id),"dropped stale dispatch invalidates ACK");
  id=f.dispatched(action,state(2000,.3f),2000);f.started(id,2100);f.acknowledge(id);
  check(f.observe(state(2050,.25f),2200)==ArcadeFeedback.State.WAITING,"frame captured during journal preparation is not a response");
  check(f.observe(state(2200,.25f),2200)==ArcadeFeedback.State.READY,"post-dispatch frame is evidence");
  ArcadeController controller=new ArcadeController();String model=controller.model();
  controller.nextRound();check(model.equals(controller.model()),"new round preserves learned physics");
  // A dropped paddle drag must allow a fresh attempt rather than stopping capture.
  var ball=new ArcadeObservation.Box(.7f,.3f,.74f,.34f);
  var basket=new ArcadeObservation.Box(.2f,.85f,.4f,.9f);
  var paddle=new ArcadeObservation(ArcadeProfile.PADDLE,3000,ball,basket,List.of(),0,.1f,1,.9f,0,1,0,1,.9f,true,false);
  var drag=ArcadePolicy.next(paddle);f.reset();id=f.dispatched(drag,paddle,3000);f.acknowledge(id);
  var unchanged=new ArcadeObservation(ArcadeProfile.PADDLE,3500,ball,basket,List.of(),0,.1f,1,.9f,0,1,0,1,.9f,true,false);
  check(f.observe(unchanged,3500)==ArcadeFeedback.State.RETRY&&!f.paused(),"first unconfirmed paddle drag retries without pause");
  check(drag.durationMs>=100,"paddle drag has a measurable hold duration");
  System.out.println("Arcade feedback: "+checks+" assertions PASS");
 }
}
