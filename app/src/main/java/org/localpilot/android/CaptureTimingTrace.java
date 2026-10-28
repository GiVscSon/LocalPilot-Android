package org.localpilot.android;

/**
 * Bounded, metadata-only Accessibility screenshot timing trace.
 *
 * Timestamps use monotonic elapsedRealtime milliseconds (NOT the uptime-based
 * ScreenshotResult timestamp). This recorder does not read pixels, grant input
 * tickets, retry captures, or change the running state machine.
 *
 * It measures callback *entry on the main executor*, not when Android Binder
 * originally delivered a result. A missing main callback is therefore evidence,
 * not proof, of a missing platform callback.
 */
public final class CaptureTimingTrace {
 public static final int MAX_EVENTS=32;
 private final String[] events=new String[MAX_EVENTS];
 private int size,next;

 public synchronized void request(long id,long atMs){
  add("REQUEST",id,0,0,"main_executor");
 }
 public synchronized void callback(long id,long atRequestMs,long atMs,boolean success,boolean current){
  add(success?(current?"SUCCESS_ENTER":"STALE_SUCCESS_ENTER")
      :(current?"FAILURE_ENTER":"STALE_FAILURE_ENTER"),
      id,elapsed(atRequestMs,atMs),0,current?"current":"ignored");
 }
 public synchronized void unavailable(long id,long atRequestMs,long atMs){
  add("SYNCHRONOUS_FAILURE",id,elapsed(atRequestMs,atMs),0,"no_input");
 }
 public synchronized void timeout(long id,long atRequestMs,long atMs,long deadlineMs,
                                  boolean stillPending){
  long duration=elapsed(atRequestMs,atMs);
  long dispatchLag=Math.max(0,duration-deadlineMs);
  String kind;
  if(!stillPending)kind="WATCHDOG_RESOLVED";
  else if(dispatchLag>=500)kind="WATCHDOG_MAIN_QUEUE_LATE";
  else kind="WATCHDOG_NO_MAIN_CALLBACK";
  add(kind,id,duration,dispatchLag,stillPending?"pending":"inactive");
 }
 private static long elapsed(long start,long now){
  if(now<start||start<0)throw new IllegalArgumentException("nonmonotonic time");
  return now-start;
 }
 private void add(String name,long id,long ageMs,long lagMs,String note){
  events[next]=name+" id="+id+" age_ms="+ageMs+" watchdog_lag_ms="+lagMs+" "+note;
  next=(next+1)%MAX_EVENTS;size=Math.min(MAX_EVENTS,size+1);
 }
 public synchronized String report(){
  StringBuilder out=new StringBuilder("CAPTURE_CALLBACK_TRACE v1\n");
  out.append("monotonic_clock=elapsedRealtime; callback_entry=main_executor; ")
     .append("watchdog_queue_lag_is_not_binder_latency; events=").append(size).append('\n');
  for(int i=0;i<size;i++)out.append(events[(next-size+i+MAX_EVENTS)%MAX_EVENTS]).append('\n');
  return out.toString();
 }
}
