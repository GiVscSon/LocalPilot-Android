package org.localpilot.android;

/** Vertical ARI platform/scroll evidence is handled by JumperFeedback, not obstacle motion here.
 * Bounded, visual-response feedback. Android ACK alone never confirms a game action. */
public final class ArcadeFeedback {
 public enum State { READY, WAITING, RETRY, PAUSED }
 private ArcadePolicy.Decision pending;
 private ArcadeObservation before;
 private long serial,token,dispatchedAt;
 private boolean acknowledged,paused;
 private int failures;
 private long verified,timeouts;
 public void reset(){serial++;token=0;pending=null;before=null;acknowledged=false;paused=false;failures=0;verified=timeouts=0;}
 public long dispatched(ArcadePolicy.Decision action,ArcadeObservation observation,long now){
  if(action==null||!action.sendsInput()||observation==null||!observation.usable()||paused||pending!=null)
   throw new IllegalStateException("feedback dispatch context");
  pending=action;before=observation;dispatchedAt=now;acknowledged=false;token=++serial;return token;
 }
 public void started(long id,long now){if(id==token&&pending!=null&&now>=dispatchedAt)dispatchedAt=now;}
 public boolean acknowledge(long id){if(id!=token||pending==null)return false;acknowledged=true;return true;}
 public State observe(ArcadeObservation after,long now){
  if(paused)return State.PAUSED;
  if(pending==null)return State.READY;
  // Missing ACKs or unusable frames are not success evidence, but must not
  // suspend the deadline forever. In particular a stale screen can continue
  // arriving after Android completed a gesture that the game ignored.
  long limit=before.profile==ArcadeProfile.FLAPPY?300:450;
  boolean fresh=acknowledged&&after!=null&&after.usable()&&after.profile==before.profile
    &&after.timestampMs>dispatchedAt&&after.timestampMs-dispatchedAt<=limit&&after.timestampMs<=now&&now-after.timestampMs<=300;
  if(fresh&&ArcadePolicy.response(pending,before,after)){
   verified++;failures=0;pending=null;before=null;token=0;return State.READY;
  }
  if(now<dispatchedAt||now-dispatchedAt<limit)return State.WAITING;
  timeouts++;failures++;pending=null;before=null;token=0;
  if(failures>=3){paused=true;return State.PAUSED;}
  return State.RETRY;
 }
 public void cancel(long id){if(id==token){pending=null;before=null;token=0;acknowledged=false;}}
 public boolean paused(){return paused;}
 public long verified(){return verified;}
 public String report(){return "VISUAL FEEDBACK: pending="+(pending==null?"none":pending.action)+" ack="+acknowledged+" failures="+failures+" verified="+verified+" timeouts="+timeouts+" paused="+paused;}
}
