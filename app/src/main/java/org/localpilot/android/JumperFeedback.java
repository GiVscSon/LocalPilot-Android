package org.localpilot.android;

/** Own gesture ACK plus fresh directional motion; never rewards a pre-dispatch frame. */
public final class JumperFeedback {
 public enum State { READY, WAITING, VERIFIED, RETRY, PAUSED }
 private long serial,token,dispatchedAt,verified;
 private float startX;
 private JumperPolicy.Action action;
 private boolean acknowledged,paused,failedWithAck;
 private int failures;
 public void reset(){serial++;token=0;action=null;acknowledged=paused=failedWithAck=false;failures=0;verified=0;}
 public long dispatched(JumperPolicy.Action next,float x,long now){
  if(next==null||next==JumperPolicy.Action.WAIT||!Float.isFinite(x)||now<0)throw new IllegalArgumentException("jumper dispatch");
  if(action!=null||paused)throw new IllegalStateException("jumper feedback pending or paused");
  action=next;startX=x;dispatchedAt=now;acknowledged=false;failedWithAck=false;token=++serial;return token;
 }
 public void started(long id,long now){if(id==token&&action!=null&&now>=dispatchedAt)dispatchedAt=now;}
 public boolean acknowledge(long id){if(id!=token||action==null)return false;acknowledged=true;return true;}
 public State observe(JumperState after,long now){
  if(paused)return State.PAUSED;
  if(action==null)return State.READY;
  boolean fresh=acknowledged&&after!=null&&after.usable()&&after.timestampMs>dispatchedAt
    &&after.timestampMs<=now&&now-after.timestampMs<=300;
  if(fresh){
   float dx=after.player.centerX()-startX,minimum=Math.max(2,7*JumperPolicy.coordinateScale(after));
   boolean moved=action==JumperPolicy.Action.TAP_LEFT?dx<=-minimum:dx>=minimum;
   if(moved){verified++;failures=0;clear();return State.VERIFIED;}
  }
  if(now<dispatchedAt||now-dispatchedAt<650)return State.WAITING;
  failedWithAck=acknowledged;clear();failures++;
  if(failures>=3){paused=true;return State.PAUSED;}
  return State.RETRY;
 }
 private void clear(){token=0;action=null;acknowledged=false;}
 public void cancel(long id){if(id==token){clear();failedWithAck=false;}}
 public boolean paused(){return paused;}
 public boolean failedWithAck(){return failedWithAck;}
 public String report(){return "JUMPER FEEDBACK: pending="+(action==null?"none":action)+" ack="+acknowledged+" failures="+failures+" verified="+verified+" paused="+paused;}
}
