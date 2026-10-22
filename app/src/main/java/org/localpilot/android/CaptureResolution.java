package org.localpilot.android;
/** Bounded mirror surface: decoded/queued frames never use the phone's full resolution. */
public final class CaptureResolution {
 public final int width,height;
 public CaptureResolution(int screenWidth,int screenHeight){
  if(screenWidth<120||screenHeight<120||screenWidth>16384||screenHeight>16384)throw new IllegalArgumentException("display bounds");
  double scale=Math.min(1,Math.min(720.0/Math.max(screenWidth,screenHeight),480.0/Math.min(screenWidth,screenHeight)));
  width=Math.max(2,((int)(screenWidth*scale)/2)*2);height=Math.max(2,((int)(screenHeight*scale)/2)*2);
 }
}
