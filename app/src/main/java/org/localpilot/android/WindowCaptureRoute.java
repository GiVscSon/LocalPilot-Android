package org.localpilot.android;

/**
 * Pure-Java selection contract for Accessibility window screenshots.
 * PilotService still owns tickets, STOP, window focus and every dispatched input.
 * We deliberately require fullscreen, origin-zero geometry: the current readers
 * use screen-space pixel coordinates. Partial-window crops need a separately
 * verified coordinate transform and are NOT eligible here.
 */
public final class WindowCaptureRoute {
 private WindowCaptureRoute() {}
 public enum Source { DISPLAY, WINDOW_FULLSCREEN }
 public static Source choose(
    int sdk, int windowId,
    int windowLeft, int windowTop, int windowRight, int windowBottom,
    int screenWidth, int screenHeight,
    int roiLeft, int roiTop, int roiRight, int roiBottom) {
  if(screenWidth<=0||screenHeight<=0||roiRight<=roiLeft||roiBottom<=roiTop
      ||roiLeft<0||roiTop<0||roiRight>screenWidth||roiBottom>screenHeight)
   throw new IllegalArgumentException("SCREEN_OR_ROI_GEOMETRY_INVALID");
  if(windowRight<=windowLeft||windowBottom<=windowTop)
   throw new IllegalArgumentException("WINDOW_GEOMETRY_UNKNOWN");
  if(sdk<34||windowId<0
      ||windowLeft!=0||windowTop!=0
      ||windowRight!=screenWidth||windowBottom!=screenHeight)
   return Source.DISPLAY;
  return Source.WINDOW_FULLSCREEN;
 }
 /** Hard gate shared by both capture routes; never scale images implicitly. */
 public static boolean compatibleFrame(int frameWidth,int frameHeight,
                                       int screenWidth,int screenHeight) {
  return frameWidth>0&&frameHeight>0
    &&frameWidth==screenWidth&&frameHeight==screenHeight;
 }
}
