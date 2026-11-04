package org.localpilot.android;
/** Pure JVM regressions for safe screenshot callback-loss recovery. */
public final class CaptureRetryTest {
 private static int checks;
 private static void check(boolean condition,String name) {
  checks++;if(!condition)throw new AssertionError(name);
 }
 public static void main(String[] args) {
  CaptureRetry retry=new CaptureRetry();
  check(!retry.allow(3,"ANDROID_SCREENSHOT_ERROR"),"framework error is terminal");
  check(retry.allow(3,"SCREENSHOT_TIMEOUT"),"one lost callback may trigger a read-only retry");
  check(!retry.allow(3,"SCREENSHOT_TIMEOUT"),"a second timeout must STOP");
  check(!retry.allow(2,"SCREENSHOT_TIMEOUT"),"older session may not retry");
  check(retry.allow(3,"STALE_FRAME"),"independent stale-frame budget still works");
  check(retry.allow(3,"FRAME_TTL_EXPIRED"),"independent TTL budget still works");
  check(retry.allow(3,"TICKET_EXPIRED"),"independent ticket budget still works");
  check(!retry.allow(3,"SCREENSHOT_INTERVAL"),"existing stale-frame retries remain bounded");
  check(!retry.allow(3,"SCREENSHOT_TIMEOUT"),"stale retries must not restore timeout budget");
  retry.fresh(2);
  check(!retry.allow(3,"SCREENSHOT_TIMEOUT"),"old session fresh marker may not reset safety");
  retry.fresh(3);
  check(retry.allow(3,"SCREENSHOT_TIMEOUT"),"fresh capture can recover the timeout budget");
  check(!retry.allow(3,"SCREENSHOT_TIMEOUT"),"bounded again after success");
  check(retry.allow(4,"SCREENSHOT_TIMEOUT"),"new epoch has new independent budget");
  check(!retry.allow(4,"SCREENSHOT_TIMEOUT"),"same epoch remains bounded");
  check(!retry.allow(-1,"SCREENSHOT_TIMEOUT"),"negative epoch rejected");
  check(!retry.allow(4,"SCREENSHOT_FAILED"),"unexpected failures cannot retry");
  System.out.println("CaptureRetry: "+checks+" checks PASS (synthetic callback-loss only)");
 }
}
