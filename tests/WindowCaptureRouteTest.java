package org.localpilot.android;

/** Synthetic geometry checks, not a device capture or accessibility permission test. */
public final class WindowCaptureRouteTest {
 private static int checks;
 private static void ok(boolean condition,String message) {
  checks++;if(!condition)throw new AssertionError(message);
 }
 private static void rejects(Runnable f,String message){
  boolean threw=false;try{f.run();}catch(IllegalArgumentException expected){threw=true;}
  ok(threw,message);
 }
 private static WindowCaptureRoute.Source route(int api,int id,
      int l,int t,int r,int b,int w,int h,int rl,int rt,int rr,int rb){
  return WindowCaptureRoute.choose(api,id,l,t,r,b,w,h,rl,rt,rr,rb);
 }
 public static void main(String[] args){
  for(int api:new int[]{30,31,32,33})
   ok(route(api,123,0,0,320,240,320,240,10,10,100,100)
     ==WindowCaptureRoute.Source.DISPLAY,"API "+api+" uses original display capture");
  for(int api:new int[]{34,35,36})
   ok(route(api,123,0,0,320,240,320,240,10,10,100,100)
     ==WindowCaptureRoute.Source.WINDOW_FULLSCREEN,"API "+api+" fullscreen window");
  ok(route(34,-1,0,0,320,240,320,240,10,10,100,100)
    ==WindowCaptureRoute.Source.DISPLAY,"unknown windowId -> display");
  ok(route(36,3,0,24,320,240,320,240,10,30,100,100)
    ==WindowCaptureRoute.Source.DISPLAY,"system bar cropped window -> display");
  ok(route(36,3,7,0,320,240,320,240,10,10,100,100)
    ==WindowCaptureRoute.Source.DISPLAY,"nonzero X origin -> display");
  ok(route(36,3,0,0,319,240,320,240,10,10,100,100)
    ==WindowCaptureRoute.Source.DISPLAY,"narrower window -> display");
  ok(route(36,3,0,0,320,241,320,240,10,10,100,100)
    ==WindowCaptureRoute.Source.DISPLAY,"larger window -> display");
  ok(route(36,0,0,0,320,240,320,240,10,10,100,100)
    ==WindowCaptureRoute.Source.WINDOW_FULLSCREEN,"window 0 can be a valid id");
  for(int api:new int[]{30,34,36}){
   rejects(()->route(api,3,0,0,0,0,320,240,10,10,100,100),
     "unknown window bounds cannot silently select display capture");
   rejects(()->route(api,3,100,100,90,120,320,240,10,10,100,100),
     "inverted window bounds cannot select a capture source");
  }
  rejects(()->route(35,3,0,0,320,240,320,240,-1,10,100,100),
    "negative ROI requires fail-closed");
  rejects(()->route(35,3,0,0,320,240,320,240,10,10,320,250),
    "out-of-display ROI requires fail-closed");
  rejects(()->route(35,3,0,0,320,240,320,240,20,20,20,100),
    "zero ROI width blocked");
  rejects(()->route(35,3,0,0,320,240,0,240,10,10,20,20),
    "zero-size display blocked");
  ok(WindowCaptureRoute.compatibleFrame(320,240,320,240),"exact bitmap geometry");
  ok(!WindowCaptureRoute.compatibleFrame(319,240,320,240),"width mismatch blocked");
  ok(!WindowCaptureRoute.compatibleFrame(320,230,320,240),"height mismatch blocked");
  ok(!WindowCaptureRoute.compatibleFrame(0,240,320,240),"zero frame blocked");
  ok(!WindowCaptureRoute.compatibleFrame(320,0,320,240),"zero height blocked");
  System.out.println("WINDOW_CAPTURE_ROUTE_PASS checks="+checks+
    " APIs=30,31,32,33,34,35,36 fullscreenOnly=true syntheticOnly=true");
 }
}
