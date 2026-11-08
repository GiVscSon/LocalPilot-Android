package org.localpilot.android;

/**
 * Causal stop, pause, and geometry-change diagnostic record captured BEFORE
 * ActionGate/InputPermit revocation resets live session fields.
 * Separates historical LAST_STOP from immediate session/export stop reasons
 * and records both old and new window bounds on TARGET_BOUNDS_CHANGED.
 */
public final class StopContext {
 public static final class BoundsTransition {
  public final long epoch, timestampMs;
  public final String packageName, reason;
  public final int oldWindowId, newWindowId;
  public final int oldLeft, oldTop, oldRight, oldBottom;
  public final int newLeft, newTop, newRight, newBottom;
  public final DisplayGeometry display;
  public final boolean reboundSafely;

  public BoundsTransition(long epoch, long timestampMs, String packageName, String reason,
    int oldWindowId, int newWindowId,
    int oldLeft, int oldTop, int oldRight, int oldBottom,
    int newLeft, int newTop, int newRight, int newBottom,
    DisplayGeometry display, boolean reboundSafely) {
   this.epoch = epoch;
   this.timestampMs = timestampMs;
   this.packageName = packageName == null ? "" : packageName;
   this.reason = reason == null ? "UNKNOWN" : reason;
   this.oldWindowId = oldWindowId;
   this.newWindowId = newWindowId;
   this.oldLeft = oldLeft;
   this.oldTop = oldTop;
   this.oldRight = oldRight;
   this.oldBottom = oldBottom;
   this.newLeft = newLeft;
   this.newTop = newTop;
   this.newRight = newRight;
   this.newBottom = newBottom;
   this.display = display;
   this.reboundSafely = reboundSafely;
  }

  public String format() {
   String disp = display == null ? "unknown"
     : display.width() + "x" + display.height() + "@rot" + display.rotation() + "/" + display.densityDpi() + "dpi";
   return "BOUNDS_EVENT: epoch=" + epoch + " at_ms=" + timestampMs + " reason=" + reason
     + " pkg=" + packageName + " window=" + oldWindowId + "->" + newWindowId
     + " armed=[" + oldLeft + "," + oldTop + "][" + oldRight + "," + oldBottom + "]"
     + " observed=[" + newLeft + "," + newTop + "][" + newRight + "," + newBottom + "]"
     + " display=" + disp + " rebound=" + reboundSafely;
  }
 }

 private String immediateStopReason = "Нет остановок";
 private String historicalLastStop = "Нет остановок";
 private long stopTimestampMs = -1;
 private long epochBefore = -1, epochAfter = -1;
 private boolean boundBefore, playingBefore;
 private String profileBefore = "none", targetBefore = "";
 private int armedLeft, armedTop, armedRight, armedBottom;
 private BoundsTransition lastBoundsTransition;

 public synchronized void recordStop(String reason, long nowMs, long epochBefore, long epochAfter,
   boolean boundBefore, boolean playingBefore, String profile, String target,
   int left, int top, int right, int bottom) {
  String clean = reason == null || reason.isEmpty() ? "STOP" : reason;
  this.immediateStopReason = clean;
  boolean exportOnly = clean.startsWith("Экспорт");
  if (!exportOnly) {
   this.historicalLastStop = clean;
   this.stopTimestampMs = nowMs;
   this.epochBefore = epochBefore;
   this.epochAfter = epochAfter;
   this.boundBefore = boundBefore;
   this.playingBefore = playingBefore;
   this.profileBefore = profile == null ? "none" : profile;
   this.targetBefore = target == null ? "" : target;
   this.armedLeft = left;
   this.armedTop = top;
   this.armedRight = right;
   this.armedBottom = bottom;
  }
 }

 public synchronized void recordBoundsChange(BoundsTransition transition) {
  if (transition == null) throw new IllegalArgumentException("transition required");
  this.lastBoundsTransition = transition;
 }

 public synchronized String immediateStopReason() { return immediateStopReason; }
 public synchronized String historicalLastStop() { return historicalLastStop; }
 public synchronized BoundsTransition lastBoundsTransition() { return lastBoundsTransition; }

 public synchronized String report() {
  StringBuilder sb = new StringBuilder("STOP_CONTEXT: immediate=").append(immediateStopReason)
    .append(" historical_last_stop=").append(historicalLastStop)
    .append(" at_ms=").append(stopTimestampMs)
    .append(" epoch=").append(epochBefore).append("->").append(epochAfter)
    .append(" bound_before=").append(boundBefore)
    .append(" playing_before=").append(playingBefore)
    .append(" profile_before=").append(profileBefore)
    .append(" target_before=").append(targetBefore)
    .append(" armed_bounds=[").append(armedLeft).append(',').append(armedTop).append("][")
    .append(armedRight).append(',').append(armedBottom).append(']');
  if (lastBoundsTransition != null) {
   sb.append('\n').append(lastBoundsTransition.format());
  }
  return sb.toString();
 }
}
