package org.localpilot.android;

public final class ArcadeCaptureHealthTest {
 private static int checks;
 private static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message+" check="+checks);}
 public static void main(String[] args){
  ArcadeCaptureHealth health=new ArcadeCaptureHealth();
  health.begin(101,100);
  check(health.snapshot(101,1899).state==ArcadeCaptureHealth.State.FRESH,"startup grace");
  check(health.snapshot(101,1900).state==ArcadeCaptureHealth.State.WAIT_FIRST_FRAME,"no first frame is not an inferred projection death");
  health.callback(101,1901,0,1,0,1,0);
  var observation=health.snapshot(101,1902);
  check(observation.state==ArcadeCaptureHealth.State.FRAME_NOT_WANTED,"fresh unwanted callbacks distinguish backpressure");
  check(observation.callbacks==1&&observation.notWanted==1,"record callback metadata without pixels");
  health.callback(101,1950,1,1,0,1,0);
  check(health.snapshot(101,1951).state==ArcadeCaptureHealth.State.FRAME_DELIVERY_PENDING,"accepted but not delivered");
  health.delivered(101,1960);
  check(health.snapshot(101,2000).state==ArcadeCaptureHealth.State.FRESH,"new frame clears old idle classification");
  check(health.snapshot(101,4000).state==ArcadeCaptureHealth.State.IMAGE_CALLBACKS_IDLE,"no image callback is not proof onStop");
  health.callback(101,4001,1,2,1,1,0);
  check(health.snapshot(101,4002).state==ArcadeCaptureHealth.State.FRAME_THROTTLED,"pacing not a decoder failure");
  health.callback(101,4003,1,3,1,2,0);
  check(health.snapshot(101,4004).state==ArcadeCaptureHealth.State.FRAME_NOT_WANTED,"worker backpressure has distinct label");
  health.projectionStopped(100);
  check(!health.isProjectionStopped(101),"stale session cannot revoke current capture");
  health.projectionStopped(101);
  check(health.isProjectionStopped(101),"onStop revokes current capture");
  check(health.snapshot(101,4005).state==ArcadeCaptureHealth.State.PROJECTION_STOPPED,"only callback proves stopped");
  health.callback(101,4010,2,3,1,2,0);
  check(health.snapshot(101,4011).accepted==1,"terminated session ignores late callbacks");
  health.begin(102,5000);
  check(!health.isProjectionStopped(102),"new consent session does not inherit failure");
  health.callback(101,5001,30,30,30,30,30);
  health.delivered(101,5002);
  check(health.snapshot(102,5003).callbacks==0,"stale frame/session is quarantined");
  health.callback(102,5004,1,0,0,0,0);
  health.delivered(102,5005);
  check(health.snapshot(102,5006).state==ArcadeCaptureHealth.State.FRESH,"new capture progresses independently");
  check(health.snapshot(102,5006).report().contains("session=102"),"report includes session identity");
  System.out.println("ARCADE_CAPTURE_HEALTH_PASS checks="+checks);
 }
}
