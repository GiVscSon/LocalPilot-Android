package org.localpilot.android;

import java.util.List;

/** Immutable observations in normalized application-window coordinates. */
public final class ArcadeObservation {
 public static final class Box {
  public final float left,top,right,bottom;
  public Box(float left,float top,float right,float bottom){
   if(!Float.isFinite(left)||!Float.isFinite(top)||!Float.isFinite(right)||!Float.isFinite(bottom)
     ||left<0||top<0||right>1.001f||bottom>1.001f||right<=left||bottom<=top)
    throw new IllegalArgumentException("arcade object geometry");
   this.left=left;this.top=top;this.right=right;this.bottom=bottom;
  }
  public float x(){return (left+right)/2;}
  public float y(){return (top+bottom)/2;}
  public float width(){return right-left;}
  public float height(){return bottom-top;}
 }
 public final ArcadeProfile profile;
 public final long timestampMs;
 public final Box player,control;
 public final List<Box> obstacles;
 public final float left,right,top,bottom,gapTop,gapBottom,vx,vy,confidence;
 public final boolean tracked,terminal;
 public ArcadeObservation(ArcadeProfile profile,long timestampMs,Box player,Box control,List<Box> obstacles,
   float left,float top,float right,float bottom,float gapTop,float gapBottom,float vx,float vy,
   float confidence,boolean tracked,boolean terminal){
  this.profile=profile;this.timestampMs=timestampMs;this.player=player;this.control=control;
  this.obstacles=List.copyOf(obstacles);this.left=left;this.top=top;this.right=right;this.bottom=bottom;
  this.gapTop=gapTop;this.gapBottom=gapBottom;this.vx=vx;this.vy=vy;
  this.confidence=confidence;this.tracked=tracked;this.terminal=terminal;
 }
 public boolean usable(){return !terminal&&player!=null&&control!=null&&confidence>=.65f;}
}
