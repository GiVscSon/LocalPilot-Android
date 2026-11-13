package org.localpilot.gamelab;

/**
 * One-shot correlation of an explicit NES screenshot request with its eventual
 * Android JavascriptInterface response. All methods run on the UI thread.
 * Never infer success from the JavaScript ACK or from an old response.
 */
public final class NesSnapshotGate {
 private long serial;
 private String active;
 public String begin(){
  if(active!=null)throw new IllegalStateException("NES screenshot already requested");
  serial=serial==Long.MAX_VALUE?1:serial+1;
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
