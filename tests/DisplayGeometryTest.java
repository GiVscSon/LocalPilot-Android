package org.localpilot.android;
public final class DisplayGeometryTest {
 private static int checks;
 private static void check(boolean value){checks++;if(!value)throw new AssertionError("geometry "+checks);}
 public static void main(String[] args){
  DisplayGeometry g=new DisplayGeometry(1080,2400,0,420);
  check(!g.requiresRebind(new DisplayGeometry(1080,2400,0,420)));
  check(g.requiresRebind(null));
  check(g.requiresRebind(new DisplayGeometry(1081,2400,0,420)));
  check(g.requiresRebind(new DisplayGeometry(1080,2399,0,420)));
  check(g.requiresRebind(new DisplayGeometry(1080,2400,2,420)));
  check(g.requiresRebind(new DisplayGeometry(2400,1080,1,420)));
  check(g.requiresRebind(new DisplayGeometry(1080,2400,0,421)));
  for(int[] bad:new int[][]{{0,1,0,1},{1,-1,0,1},{1,1,-1,1},{1,1,4,1},{1,1,0,0}}){
   try{new DisplayGeometry(bad[0],bad[1],bad[2],bad[3]);throw new AssertionError("invalid accepted");}catch(IllegalArgumentException expected){checks++;}
  }
  CaptureRetry retry=new CaptureRetry();
  for(String reason:new String[]{"STALE_FRAME","FRAME_TTL_EXPIRED","TICKET_EXPIRED","SCREENSHOT_INTERVAL"}){
   retry=new CaptureRetry();
   for(int i=0;i<3;i++)check(retry.allow(1,reason));
   check(!retry.allow(1,reason));retry.fresh(0);check(!retry.allow(1,reason));
   retry.fresh(1);check(retry.allow(1,reason));check(retry.allow(2,reason));
  }
  check(!retry.allow(1,"STALE_FRAME"));check(!retry.allow(-1,"STALE_FRAME"));
  for(String reason:new String[]{"TARGET_APP_CHANGED","TARGET_WINDOW_CHANGED","TARGET_BOUNDS_CHANGED","FOCUS_CHANGED","SESSION_CHANGED","CLOCK_REVERSED","ANDROID_SCREENSHOT_ERROR","ROI_CHANGED",null})check(!retry.allow(2,reason));
  System.out.println("DisplayGeometry: "+checks+" assertions PASS");
 }
}
