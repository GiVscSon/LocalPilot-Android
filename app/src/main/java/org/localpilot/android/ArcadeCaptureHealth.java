package org.localpilot.android;

/**
 * Session-scoped, metadata-only MediaProjection heartbeat.
 *
 * An ImageReader callback is not a decoded/consumed frame, and Android's
 * gesture completion is not evidence of game movement. All timestamps must
 * use elapsedRealtime milliseconds; no pixels or consent tokens are retained.
 * All methods synchronize because callbacks run on the capture worker thread
 * while frame consumption and the watchdog run on the main thread.
 */
final class ArcadeCaptureHealth {
 static final long STALL_MS=1800;
 enum State {
  FRESH, WAIT_FIRST_FRAME, PROJECTION_STOPPED, IMAGE_CALLBACKS_IDLE,
  FRAME_DELIVERY_PENDING, FRAME_NOT_WANTED, FRAME_THROTTLED, CALLBACK_NO_DELIVERY
 }
 static final class Snapshot {
  final State state;
  final long session, callbackAgeMs, deliveredAgeMs, callbacks, accepted, skipped, throttled, notWanted, invalidClock;
  Snapshot(State state,long session,long callbackAgeMs,long deliveredAgeMs,
      long callbacks,long accepted,long skipped,long throttled,long notWanted,long invalidClock){
   this.state=state;this.session=session;this.callbackAgeMs=callbackAgeMs;this.deliveredAgeMs=deliveredAgeMs;
   this.callbacks=callbacks;this.accepted=accepted;this.skipped=skipped;
   this.throttled=throttled;this.notWanted=notWanted;this.invalidClock=invalidClock;
  }
  String report(){
   return "CAPTURE_HEALTH session="+session+" state="+state
    +" callback_age_ms="+callbackAgeMs+" delivered_age_ms="+deliveredAgeMs
    +" callbacks="+callbacks+" accepted="+accepted+" skipped="+skipped
    +" throttled="+throttled+" not_wanted="+notWanted+" invalid_clock="+invalidClock;
  }
 }
 private long session,startedAt=-1,callbackAt=-1,acceptedAt=-1,deliveredAt=-1;
 private long callbacks,accepted,skipped,throttled,notWanted,invalidClock;
 private long wantedWhenDelivered,throttledWhenDelivered;
 private boolean stopped;

 synchronized void begin(long id,long now){
  if(id<=0||now<0)throw new IllegalArgumentException("invalid capture session");
  session=id;startedAt=now;callbackAt=acceptedAt=deliveredAt=-1;
  callbacks=accepted=skipped=throttled=notWanted=invalidClock=0;
  wantedWhenDelivered=throttledWhenDelivered=0;stopped=false;
 }
 synchronized void callback(long id,long now,long nextAccepted,long nextSkipped,
      long nextThrottled,long nextNotWanted,long nextInvalidClock){
  if(id!=session||stopped||now<startedAt||now<callbackAt)return;
  if(nextAccepted<accepted||nextSkipped<skipped||nextThrottled<throttled||
     nextNotWanted<notWanted||nextInvalidClock<invalidClock)return;
  callbacks++;
  callbackAt=now;
  if(nextAccepted>accepted)acceptedAt=now;
  accepted=nextAccepted;skipped=nextSkipped;throttled=nextThrottled;
  notWanted=nextNotWanted;invalidClock=nextInvalidClock;
 }
 synchronized void delivered(long id,long now){
  if(id!=session||stopped||now<startedAt||now<deliveredAt)return;
  deliveredAt=now;
  wantedWhenDelivered=notWanted;
  throttledWhenDelivered=throttled;
 }
 synchronized void projectionStopped(long id){
  if(id==session&&id>0)stopped=true;
 }
 synchronized boolean isProjectionStopped(long id){return id==session&&id>0&&stopped;}
 synchronized Snapshot snapshot(long id,long now){
  if(id<=0||id!=session||startedAt<0)return new Snapshot(State.WAIT_FIRST_FRAME,id,-1,-1,0,0,0,0,0,0);
  long callbackAge=age(now,callbackAt);
  long deliveredAge=now>=startedAt?Math.max(0,now-(deliveredAt>=0?deliveredAt:startedAt)):-1;
  State state;
  if(stopped)state=State.PROJECTION_STOPPED;
  else if(deliveredAge>=0&&deliveredAge<STALL_MS)state=State.FRESH;
  else if(callbackAt<0)state=State.WAIT_FIRST_FRAME;
  else if(callbackAge>=STALL_MS)state=State.IMAGE_CALLBACKS_IDLE;
  else if(acceptedAt>deliveredAt)state=State.FRAME_DELIVERY_PENDING;
  else if(notWanted>wantedWhenDelivered)state=State.FRAME_NOT_WANTED;
  else if(throttled>throttledWhenDelivered)state=State.FRAME_THROTTLED;
  else state=State.CALLBACK_NO_DELIVERY;
  return new Snapshot(state,id,callbackAge,deliveredAge,callbacks,accepted,skipped,throttled,notWanted,invalidClock);
 }
 private static long age(long now,long at){return at<0||now<at?-1:now-at;}
}
