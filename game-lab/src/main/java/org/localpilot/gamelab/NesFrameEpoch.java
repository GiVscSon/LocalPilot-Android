package org.localpilot.gamelab;

/**
 * Monotonic, per-ROM epoch for the Android-facing NES bridge.
 * WebView callbacks may be delivered after the next cartridge has loaded.
 * Only strictly increasing frames from the currently running ROM are accepted.
 * Call on the UI thread, like the lifecycle/bridge controller.
 */
public final class NesFrameEpoch {
 private static final java.util.concurrent.atomic.AtomicLong SERIAL=new java.util.concurrent.atomic.AtomicLong();
 private String active;
 private int last;
 public String begin(){
  long serial=SERIAL.incrementAndGet();
  if(serial<=0)throw new IllegalStateException("NES epoch exhausted");
  active=Long.toString(serial);
  last=0;
  return active;
 }
 public String token(){return active==null?"":active;}
 public boolean current(String session){return active!=null&&active.equals(session);}
 public boolean accept(String session,int frame){
  if(!current(session)||frame<=last||frame<1)return false;
  last=frame;
  return true;
 }
 public void cancel(){active=null;last=0;}
}
