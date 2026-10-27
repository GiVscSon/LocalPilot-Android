package org.localpilot.android;
/** Software frame pacing. A skipped callback is usually intentional, not proof of a slow decoder. */
public final class CapturePacer {
 public static final long INTERVAL_NS=33_000_000L;
 private long last=-1,accepted,skipped,throttled,notWanted,invalidClock;
 /**
  * Keep the original accept/reject behavior; classify rejections before any RGBA decode.
  * Owned by the capture callback thread, not safe for concurrent writers.
  */
 public boolean accept(long nowNs,boolean wanted){
  if(nowNs<0||last>=0&&nowNs<last){invalidClock++;skipped++;return false;}
  if(!wanted){notWanted++;skipped++;return false;}
  if(last>=0&&nowNs-last<INTERVAL_NS){throttled++;skipped++;return false;}
  last=nowNs;accepted++;return true;
 }
 public long acceptedFrames(){return accepted;}
 public long skippedFrames(){return skipped;}
 public long throttledFrames(){return throttled;}
 public long unwantedFrames(){return notWanted;}
 public long invalidClockFrames(){return invalidClock;}
}
