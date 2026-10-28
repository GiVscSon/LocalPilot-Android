package org.localpilot.android;
public final class CaptureRetryTest {
 private static void require(boolean x,String why){if(!x)throw new AssertionError(why);}
 public static void main(String[] ignored){
  CaptureRetry r=new CaptureRetry();
  require(r.allow(42,"SCREENSHOT_TIMEOUT"),"first timeout gets ONE read-only retry");
  require(!r.allow(42,"SCREENSHOT_TIMEOUT"),"second timeout fails closed");
  require(r.allow(42,"STALE_FRAME"),"separate stale retry budget");
  r.fresh(42);
  require(r.allow(42,"SCREENSHOT_TIMEOUT"),"a fresh frame resets timeout budget");
  require(r.allow(43,"SCREENSHOT_TIMEOUT"),"new epoch resets timeout budget");
  require(!r.allow(42,"SCREENSHOT_TIMEOUT"),"old epoch cannot retry");
  require(!r.allow(43,"NOT_A_RETRY_REASON"),"unlisted code cannot trigger retry");
  for(int i=0;i<CaptureRetry.MAX_RETRIES;i++)require(r.allow(43,"STALE_FRAME"),"bounded stale allowance");
  require(!r.allow(43,"STALE_FRAME"),"stale retry budget exhausted");
  System.out.println("CaptureRetryTest PASS");
 }
}
