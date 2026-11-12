package org.localpilot.gamelab;

/**
 * One-shot correlation of an explicit NES screenshot request with its eventual
 * Android JavascriptInterface response. All methods run on the UI thread.
 * Never infer success from the JavaScript ACK or from an old response.
 */
public final class NesSnapshotGate {
 private long serial;
 private final long stride;
 private String active;
 public NesSnapshotGate(){this(1,1);}
 // Distinct odd/even token spaces prevent delayed AI snapshots from being
 // accepted as manual exports, even when both request counters begin anew.
 public NesSnapshotGate(int first,int step){
  if(first<1||step<1||first>step)
   throw new IllegalArgumentException("Invalid disjoint snapshot namespace");
  serial=(long)first-step;stride=step;
 }
 public String begin(){
  if(active!=null)throw new IllegalStateException("NES screenshot already requested");
  if(serial>Long.MAX_VALUE-stride)
   throw new IllegalStateException("Snapshot token exhausted; fail closed");
  serial+=stride;
  active=Long.toString(serial);
  return active;
 }
 public boolean pending(){return active!=null;}
 public boolean current(String token){return active!=null&&active.equals(token);}
 public boolean finish(String token){
  if(!current(token))return false;
  active=null;
  return true;
 }
 public void cancel(){active=null;}
}
