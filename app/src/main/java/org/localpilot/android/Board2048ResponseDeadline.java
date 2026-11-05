package org.localpilot.android;

/**
 * Strict bounded wait for a real visual outcome of an already-dispatched
 * 2048 swipe. Android ACK alone never marks the move as successful.
 * Pending state is cleared on STOP, window rebind, or verified transition.
 */
public final class Board2048ResponseDeadline {
 public static final long MAX_WAIT_MS=8_000;
 private long sinceMs=-1;
 public void start(long nowMs){
  if(nowMs<0)throw new IllegalArgumentException("clock");
  if(sinceMs>=0)throw new IllegalStateException("pending 2048 outcome");
  sinceMs=nowMs;
 }
 public boolean pending(){return sinceMs>=0;}
 public boolean expired(long nowMs){
  if(sinceMs<0)return false;
  return nowMs<sinceMs||nowMs-sinceMs>=MAX_WAIT_MS;
 }
 public long pendingAgeMs(long nowMs){
  return sinceMs<0?-1:Math.max(0,nowMs-sinceMs);
 }
 public void clear(){sinceMs=-1;}
}
