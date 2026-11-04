package org.localpilot.android;

/** Converts a controller decision to normalized gesture coordinates. */
public final class ArcadeControls {
 public static final int TOUCH=0,SWIPE=1,PADDLE_TAP=2;
 public record Gesture(float fromX,float fromY,float toX,float toY,int durationMs){}
 private ArcadeControls(){}
 public static Gesture map(ArcadePolicy.Decision action,ArcadeObservation before,int mode){
  if(action==null||!action.sendsInput()||mode<0||mode>2)throw new IllegalArgumentException("action mapping");
  if(mode==SWIPE&&(action.action==ArcadePolicy.Action.LEFT||action.action==ArcadePolicy.Action.RIGHT))return new Gesture(.5f,action.y,action.action==ArcadePolicy.Action.LEFT?.2f:.8f,action.y,Math.max(100,action.durationMs));
  float start=action.x;
  if(action.action==ArcadePolicy.Action.MOVE_PADDLE&&mode!=PADDLE_TAP){if(before==null||before.control==null)throw new IllegalArgumentException("paddle position missing");start=before.control.x();}
  return new Gesture(start,action.y,action.x,action.y,action.durationMs);
 }
}
