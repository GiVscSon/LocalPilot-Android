package org.localpilot.android;
/** ScreenshotResult timestamp uses uptime, NOT elapsedRealtime (which includes sleep). */
public final class CaptureFreshness {
 public static boolean valid(long timestamp,long requestedUptime,long nowUptime,long previousTimestamp){return timestamp>0&&timestamp>previousTimestamp&&timestamp>=requestedUptime&&timestamp<=nowUptime&&nowUptime-timestamp<=700;}
 /** A frame callback may synchronously stop, pause or replace the capture region. */
 public static boolean previewCurrent(long capturedEpoch,long currentEpoch,boolean captureActive,boolean focusPaused,boolean sameRegion){
  return capturedEpoch==currentEpoch&&captureActive&&!focusPaused&&sameRegion;
 }
}
