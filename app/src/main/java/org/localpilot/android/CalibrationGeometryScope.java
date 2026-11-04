package org.localpilot.android;

/**
 * Stable signature of calibration coordinates relative to the bound app window.
 * Color samples include normalized sample positions, which are NOT transferable
 * between different crop/ROI coordinates or different window dimensions.
 * Pure Java: no pixels, no Android permissions, no user identity.
 */
public final class CalibrationGeometryScope {
 private CalibrationGeometryScope(){}
 public static String id(int windowWidth,int windowHeight,int left,int top,int right,int bottom) {
  if(windowWidth<=0||windowHeight<=0||left<0||top<0||right<=left||bottom<=top
      ||right>windowWidth||bottom>windowHeight)throw new IllegalArgumentException("invalid calibration region");
  return "roi-v1:"+windowWidth+"x"+windowHeight+":"+left+","+top+","+right+","+bottom;
 }
 public static boolean matches(String stored,int windowWidth,int windowHeight,
                               int left,int top,int right,int bottom) {
  return stored!=null&&stored.equals(id(windowWidth,windowHeight,left,top,right,bottom));
 }
}
