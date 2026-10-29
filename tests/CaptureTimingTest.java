package org.localpilot.android;

/** Synthetic cases for screenshot callback timing only. No Android SDK. */
public final class CaptureTimingTest {
 private static int n;
 private static void check(boolean ok,String label){n++;if(!ok)throw new AssertionError(label);}
 public static void main(String[] args){
  CaptureTimingTrace trace=new CaptureTimingTrace();
  trace.request(11,1000);
  trace.apiReturned(11,1000,1004);
  trace.callback(11,1000,1130,true,true);
  trace.timeout(11,1000,1004,5005,4000,false);
  String healthy=trace.report();
  check(healthy.contains("SUCCESS_ENTER id=11 age_ms=130"),"callback entry latency is monotonic");
  check(healthy.contains("WATCHDOG_RESOLVED id=11"),"a resolved request cannot become a timeout");

  trace.request(12,6000);
  trace.apiReturned(12,6000,6000);
  trace.timeout(12,6000,6000,10027,4000,true);
  String missing=trace.report();
  check(missing.contains("WATCHDOG_NO_MAIN_CALLBACK id=12 age_ms=4027 watchdog_lag_ms=27"),
   "on-time main watchdog identifies absence of main callback, not Binder cause");
  trace.callback(12,6000,10500,true,false);
  check(trace.report().contains("STALE_SUCCESS_ENTER id=12 age_ms=4500"),
   "late platform callback is recorded as ignored, never as proof");

  trace.request(13,11000);
  trace.apiReturned(13,11000,11200);
  trace.timeout(13,11000,11200,15801,4000,true);
  check(trace.report().contains("WATCHDOG_MAIN_QUEUE_LATE id=13 age_ms=4801 watchdog_lag_ms=601"),
   "delayed main queue watchdog is differentiated from on-time callback absence");

  check(trace.report().contains("API_RETURN id=13 age_ms=200"),
   "synchronous takeScreenshot API time is separately measured");
  trace.request(14,20000);
  trace.callback(14,20000,20110,false,true);
  check(trace.report().contains("FAILURE_ENTER id=14 age_ms=110"),
   "platform onFailure callback is separately recorded");
  trace.request(15,21000);
  trace.unavailable(15,21000,21002);
  check(trace.report().contains("SYNCHRONOUS_FAILURE id=15 age_ms=2"),
   "synchronous API exception is separate from missing callback");

  for(int i=0;i<50;i++)trace.request(100+i,50000+i);
  String bounded=trace.report();
  check(bounded.contains("events=32"),"only latest 32 events retained");
  check(!bounded.contains("id=100 "), "oldest request evicted");
  check(bounded.contains("id=149 "),"newest request retained");
  check(!bounded.contains("Bitmap")&&!bounded.contains("pixels"),
   "trace has no image or screenshot content");

  try{trace.callback(15,21000,20999,true,true);throw new AssertionError("backward clock accepted");}
  catch(IllegalArgumentException expected){n++;}
  System.out.println("Capture callback timing: "+n+" assertions PASS (synthetic only)");
 }
}
