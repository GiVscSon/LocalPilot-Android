package org.localpilot.android;
/** Bounded retries discard stale pixels; they never renew an input ticket. */
public final class CaptureRetry {
 public static final int MAX_RETRIES=3;
 private long epoch=Long.MIN_VALUE;private int retries;
 public boolean allow(long session,String reason){
  if(session<0||session<epoch)return false;
  if(session!=epoch){epoch=session;retries=0;}
  if(!"STALE_FRAME".equals(reason)&&!"FRAME_TTL_EXPIRED".equals(reason)&&!"TICKET_EXPIRED".equals(reason)&&!"SCREENSHOT_INTERVAL".equals(reason))return false;
  if(retries>=MAX_RETRIES)return false;
  retries++;return true;
 }
 public void fresh(long session){if(epoch==session)retries=0;}
}
