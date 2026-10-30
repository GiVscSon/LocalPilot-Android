package org.localpilot.android;

/**
 * A window-state event from the *same* browser process is not by itself a
 * confirmed external navigation. Quarantine inputs until the existing game's
 * reader has supplied two fresh, independently accepted stable observations.
 *
 * The caller must still verify the focused package, window ID and bounds,
 * and use its normal scene classifier/session verifier. This class cannot
 * establish gameplay scene identity by itself and NEVER authorizes a gesture.
 */
public final class BrowserSceneGate {
 private boolean quarantined;
 private long eventAt=-1,lastFrameAt=-1;
 private int consecutive;
 private long transitions;
 public void changed(long atMs) {
  if(atMs<0)throw new IllegalArgumentException("negative transition time");
  quarantined=true;eventAt=atMs;lastFrameAt=-1;consecutive=0;
  transitions=Math.min(Long.MAX_VALUE,transitions+1);
 }
 public boolean observe(long frameAtMs,boolean confirmedGame) {
  if(!quarantined)return true;
  if(frameAtMs<=eventAt||frameAtMs<=lastFrameAt)return false;
  lastFrameAt=frameAtMs;
  if(!confirmedGame){consecutive=0;return false;}
  if(++consecutive<2)return false;
  reset();
  return true;
 }
 public boolean quarantined(){return quarantined;}
 public long transitions(){return transitions;}
 public void reset(){quarantined=false;eventAt=lastFrameAt=-1;consecutive=0;}
}
