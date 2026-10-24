package org.localpilot.android;
/** Software decode pacing; does not change display refresh or prevent OS process termination. */
public final class CapturePacer {
 public static final long INTERVAL_NS=33_000_000L;
 private long last=-1,accepted,skipped;
 public boolean accept(long nowNs,boolean wanted){
  if(nowNs<0||!wanted||last>=0&&(nowNs<last||nowNs-last<INTERVAL_NS)){skipped++;return false;}
  last=nowNs;accepted++;return true;
 }
 public long acceptedFrames(){return accepted;}
 public long skippedFrames(){return skipped;}
}
