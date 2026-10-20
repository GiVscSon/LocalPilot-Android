package org.localpilot.android;
/** ScreenshotResult timestamp uses uptime, NOT elapsedRealtime (which includes sleep). */
public final class CaptureFreshness {
 public static boolean valid(long timestamp,long requestedUptime,long nowUptime,long previousTimestamp){return timestamp>0&&timestamp>previousTimestamp&&timestamp>=requestedUptime&&timestamp<=nowUptime&&nowUptime-timestamp<=700;}
}
