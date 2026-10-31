package org.localpilot.android;
public final class GridCaptureCadenceTest {
 private static int checks;
 private static void check(boolean ok,String reason){checks++;if(!ok)throw new AssertionError(reason);}
 public static void main(String[] args) {
  check(GridCaptureCadence.delayMs(36,11,true,true,false,400)==240,"API36 timed pending after ACK");
  check(GridCaptureCadence.delayMs(35,11,true,true,false,400)==240,"API35 timed pending after ACK");
  check(GridCaptureCadence.delayMs(36,11,true,false,false,400)==400,"no ACK cannot accelerate");
  check(GridCaptureCadence.delayMs(36,11,false,false,false,400)==400,"no pending gesture no acceleration");
  check(GridCaptureCadence.delayMs(36,6,true,true,false,400)==400,"ordinary match profile unchanged");
  check(GridCaptureCadence.delayMs(36,10,true,true,false,400)==400,"trace profile unchanged");
  check(GridCaptureCadence.delayMs(34,11,true,true,false,400)==400,"older Android unchanged");
  check(GridCaptureCadence.delayMs(30,11,true,true,false,1100)==1100,"API30 low-capture path unchanged");
  check(GridCaptureCadence.delayMs(36,11,true,true,true,400)==1000,"paused controller polls slowly");
  check(GridCaptureCadence.delayMs(36,11,true,true,false,180)==180,"does not lengthen faster existing cadence");
  boolean refused=false;
  try{GridCaptureCadence.delayMs(36,11,true,true,false,0);}catch(IllegalArgumentException expected){refused=true;}
  check(refused,"invalid baseline rejected");
  System.out.println("GRID_CAPTURE_CADENCE_PASS checks="+checks);
 }
}
