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
  check(f.observe(state(1100,.25f),1500)==ArcadeFeedback.State.WAITING,"stale movement is not evidence");
  check(f.observe(state(1600,.25f),1500)==ArcadeFeedback.State.WAITING,"future observation rejected");
  f.cancel(id);check(f.observe(state(1700,.3f),1700)==ArcadeFeedback.State.READY&&!f.acknowledge(id),"dropped stale dispatch invalidates ACK");
  id=f.dispatched(action,state(2000,.3f),2000);f.started(id,2100);f.acknowledge(id);
  check(f.observe(state(2050,.25f),2200)==ArcadeFeedback.State.WAITING,"frame captured during journal preparation is not a response");
  check(f.observe(state(2200,.25f),2200)==ArcadeFeedback.State.READY,"post-dispatch frame is evidence");
  ArcadeController controller=new ArcadeController();String model=controller.model();
  controller.nextRound();check(model.equals(controller.model()),"new round preserves learned physics");
  System.out.println("Arcade feedback: "+checks+" assertions PASS");
 }
}
