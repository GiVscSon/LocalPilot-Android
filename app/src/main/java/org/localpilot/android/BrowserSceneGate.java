package org.localpilot.android;

/**
 * A window-state event from the *same* browser process is not by itself a
 * confirmed external navigation. Quarantine input until the existing reader
 * has supplied two independent recognized frames spanning a settling window.
 *
 * The caller MUST separately verify focused package, window id/bounds and
 * its game-specific scene classifier. This class never grants input authority.
 */
public final class BrowserSceneGate {
 public static final long MIN_STABLE_MS=80;
 private boolean quarantined;
 private long eventAt=-1,lastFrameAt=-1,firstConfirmedAt=-1;
 private long transitions;

 public void changed(long atMs) {
  if(atMs<0)throw new IllegalArgumentException("negative transition time");
  quarantined=true;eventAt=atMs;lastFrameAt=firstConfirmedAt=-1;
  if(transitions<Long.MAX_VALUE)transitions++;
 }
 public boolean observe(long frameAtMs,boolean confirmedGame) {
  if(!quarantined)return true;
  // A late callback from before a transition, or the same frame twice,
  // cannot provide independent evidence.
  if(frameAtMs<=eventAt||frameAtMs<=lastFrameAt)return false;
  lastFrameAt=frameAtMs;
  if(!confirmedGame){firstConfirmedAt=-1;return false;}
  if(firstConfirmedAt<0){firstConfirmedAt=frameAtMs;return false;}
  // Two frames 1ms apart might both be from an in-progress browser animation.
  // At least 80ms between accepted observations is required before policy.
  if(frameAtMs-firstConfirmedAt<MIN_STABLE_MS)return false;
  reset();
  return true;
 }
 public boolean quarantined(){return quarantined;}
 public long transitions(){return transitions;}
 public void reset(){quarantined=false;eventAt=lastFrameAt=firstConfirmedAt=-1;}
}
