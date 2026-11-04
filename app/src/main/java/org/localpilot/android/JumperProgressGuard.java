package org.localpilot.android;

/** Fail-closed detection of repetitive, visually confirmed horizontal steering
 * on the same vertical slice. Android ACK and lateral motion are not ascent.
 */
public final class JumperProgressGuard {
 private float highest=Float.NaN,verticalScale=1;
 private int repeatedSteers;
 private int left,top,right,bottom;
 private long lastFrame=-1;
 public void reset(){highest=Float.NaN;repeatedSteers=0;lastFrame=-1;}
 public boolean observe(JumperState s,boolean verifiedSteer){
  if(s==null||!s.usable()||s.player==null)return true;
  if(lastFrame<0||left!=s.roiLeft||top!=s.roiTop||right!=s.roiRight||bottom!=s.roiBottom){
   reset();highest=s.player.centerY();left=s.roiLeft;top=s.roiTop;
   right=s.roiRight;bottom=s.roiBottom;lastFrame=s.timestampMs;return true;
  }
  if(s.timestampMs<=lastFrame)return true;
  lastFrame=s.timestampMs;
  float scale=Math.max(.15f,JumperPolicy.coordinateScale(s));
  float rise=Math.max(25f*scale,s.player.height()*.35f);
  // A camera tracking a successful climb moves platforms downwards.
  // Tracking computes scroll from the platforms, not from the gesture ACK.
  if(s.player.centerY()<highest-rise||s.scrollVy>Math.max(30*scale,12f)){
   highest=s.player.centerY();repeatedSteers=0;return true;
  }
  if(verifiedSteer)repeatedSteers++;
  return repeatedSteers<4;
 }
 public int repeatedSteers(){return repeatedSteers;}
}
