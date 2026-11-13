package org.localpilot.android;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.HardwareBuffer;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONArray;
import org.json.JSONObject;

/** Focused real-Android capture acceptance. Never grants an InputPermit. */
public final class CaptureAcceptanceInstrumentation extends Instrumentation {
 private final JSONObject evidence=new JSONObject();
 private final JSONArray checks=new JSONArray();
 private UiAutomation automation;private PilotService service;
 private long started;private long previousRequest;
 private final AtomicInteger delivered=new AtomicInteger();
 private boolean stopWhenDelivered;
 private String fixturePackage;
 private int lastFixtureWindow=-1;
 @Override public void onCreate(Bundle args){super.onCreate(args);start();}
 @Override public void onStart(){
  started=SystemClock.uptimeMillis();Bundle result=new Bundle();boolean passed=false;
  try{
   fixturePackage=getContext().getPackageName();
   evidence.put("schema","localpilot-android-window-capture-v1")
    .put("scope","actual Android Accessibility screenshot API, fullscreen test-only owner, secure content, stale window and STOP; no Market gameplay or physical-device acceptance")
    .put("api",Build.VERSION.SDK_INT).put("physical_device",false).put("checks",checks)
    .put("input_setup","production arm() on actual fixture window; automatic=false; InputPermit revoked; no gesture injected");
   automation=getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
   AccessibilityServiceInfo info=automation.getServiceInfo();
   info.flags|=AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
   automation.setServiceInfo(info);
   Bundle ready=new Bundle();ready.putString("capture_ready","1");sendStatus(1,ready);
   await(()->main(()->PilotService.instance!=null),10_000,"PilotService did not connect");
   service=main(()->PilotService.instance);
   main(()->{service.setAutomatic(false);service.hidePanel();return null;});
   if(Build.VERSION.SDK_INT>=31)
    require(!service.getServiceInfo().isAccessibilityTool(),"Capture acceptance must not escalate isAccessibilityTool");
   launchFixture(false);Owner owner=owner(false);
   Capture plain=capture(owner,route(owner));
   require(plain.error==-1,"Actual screenshot failed: "+plain.error);
   require(plain.width==owner.screen.x&&plain.height==owner.screen.y,"Actual screenshot dimensions differ from verified fullscreen owner");
   require(plain.fresh,"Actual screenshot timestamp failed CaptureFreshness");
   int[] expected={0xffdf3b36,0xff38b76b,0xff335dde,0xffe8ba34};
   for(int i=0;i<4;i++)require(closeColor(plain.colors[i],expected[i]),"Screenshot fixture landmark mismatch at quadrant "+i);
   check("actual_bridge_fullscreen_pixels_and_geometry",new JSONObject()
    .put("route",route(owner).name()).put("window_id",owner.id).put("bounds",owner.bounds.toShortString())
    .put("frame_width",plain.width).put("frame_height",plain.height).put("timestamp_uptime_ms",plain.timestamp)
    .put("landmark_count",4));
   checkStopCallback();
   checkOldOwner();
   checkConsumerStop();
   checkSecure();
   assertNoInput();
   passed=true;
  }catch(Throwable error){
   Throwable cause=error;while(cause.getCause()!=null&&cause.getCause()!=cause)cause=cause.getCause();
   try{evidence.put("failure",cause.toString()).put("failure_wrapper",error.toString())
    .put("failed_at_ms",SystemClock.uptimeMillis()-started);
    if(service!=null)evidence.put("capture_timing",service.captureTimingReport()).put("last_stop",String.valueOf(field("lastStopReason")));}
   catch(Throwable ignored){/* Preserve primary failure. */}
  }finally{
   try{if(service!=null)main(()->{service.hidePanel();return null;});}catch(Exception ignored){}
   try{evidence.put("result",passed?"PASS":"FAIL").put("duration_ms",SystemClock.uptimeMillis()-started);
    result.putString("evidence_json",evidence.toString());result.putString("result",passed?"PASS":"FAIL");}
   catch(Exception error){result.putString("result","FAIL");passed=false;}
   finish(passed?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
  }
 }
 private void checkStopCallback()throws Exception{
  throttle();Owner owner=owner(false);prepareReadOnly(owner);
  long[] request=main(()->{
   long epoch=gate().epoch();invokeCapture(epoch);long id=((Number)field("pendingCapture")).longValue();
   require((Boolean)field("captureBusy"),"Production capture did not issue an asynchronous request");
   service.hidePanel();require(gate().epoch()!=epoch&&!gate().armed(),"STOP did not revoke capture epoch");
   return new long[]{id};
  });
  await(()->service.captureTimingReport().contains("STALE_SUCCESS_ENTER id="+request[0]+" "),5_000,
   "No actual successful Android screenshot callback was observed after STOP");
  require(delivered.get()==0,"A late screenshot reached the image consumer after STOP");
  assertNoInput();
  check("actual_screenshot_late_callback_after_stop_rejected",new JSONObject()
   .put("request_id",request[0]).put("consumer_deliveries",0).put("setup","request then STOP in the same main-looper turn")
   .put("actual_callback","STALE_SUCCESS_ENTER").put("gestures_dispatched",0));
 }
 private void checkOldOwner()throws Exception{
  // Obtain an original production ticket, then really replace the foreground
  // Activity/window. No synthetic WindowInfo or invented package is used.
  Owner old=owner(false);prepareReadOnly(old);
  ActionGate.Ticket ticket=main(()->gate().offer(SystemClock.elapsedRealtime()));
  launchFixture(false);Owner current=owner(false);
  require(current.id!=old.id,"Fixture replacement did not create a new Android window");
  Method invalid=PilotService.class.getDeclaredMethod("invalidReason",ActionGate.Ticket.class);invalid.setAccessible(true);
  String reason=main(()->(String)invalid.invoke(service,ticket));
  require(reason!=null,"Production owner check accepted a ticket from the replaced window");
  main(()->{service.hidePanel();return null;});
  JSONObject detail=new JSONObject().put("old_window_id",old.id).put("new_window_id",current.id)
   .put("production_ticket_rejection",reason).put("gestures_dispatched",0);
  if(Build.VERSION.SDK_INT>=34){
   Capture stale=capture(old,WindowCaptureRoute.Source.WINDOW_FULLSCREEN);
   require(stale.error==AccessibilityService.ERROR_TAKE_SCREENSHOT_INVALID_WINDOW,
    "A stale window ID did not fail explicitly: "+stale.error);
   detail.put("actual_stale_window_error",stale.error).put("display_fallback_requests",0);
  }else detail.put("window_capture","UNSUPPORTED_API_30_33; production owner gate tested using display route");
  assertNoInput();check("actual_replaced_window_owner_rejected",detail);
 }
 private void checkSecure()throws Exception{
  launchFixture(true);Owner secure=owner(true);
  Capture result=capture(secure,route(secure));
  if(Build.VERSION.SDK_INT>=34)require(result.error==AccessibilityService.ERROR_TAKE_SCREENSHOT_SECURE_WINDOW,
   "Secure window screenshot did not fail with the explicit platform error: "+result.error);
  else require(result.error!=-1||result.nearBlack,"Secure display capture exposed fixture pixels instead of redacting or rejecting them");
  throttle();prepareReadOnly(secure);int before=delivered.get();
  main(()->{invokeCapture(gate().epoch());return null;});
  await(()->main(()->!gate().armed()),8_000,"Secure content did not reach bounded production Fail-Closed STOP");
  require(delivered.get()==before,"Secure content reached a production image consumer");
  String stop=main(()->String.valueOf(field("lastStopReason")));
  require(stop.contains("SCREENSHOT_SECURE_WINDOW")||Build.VERSION.SDK_INT<34&&
   (stop.contains("NEAR_BLACK_FRAME")||stop.contains("ANDROID_SCREENSHOT_ERROR")),
   "Secure fixture stopped for an unrelated cause: "+stop);
  assertNoInput();
  check("actual_secure_content_fail_closed_without_fallback",new JSONObject()
   .put("route",route(secure).name()).put("platform_error",result.error).put("redacted_near_black",result.nearBlack)
   .put("production_stop",stop).put("consumer_deliveries",delivered.get()-before)
   .put("secure_window_flag",true).put("is_accessibility_tool",false));
 }
 private void checkConsumerStop()throws Exception{
  throttle();Owner owner=owner(false);prepareReadOnly(owner);int before=delivered.get();
  main(()->{stopWhenDelivered=true;invokeCapture(gate().epoch());return null;});
  try{
   await(()->delivered.get()==before+1,5_000,"Actual production screenshot did not reach its read-only consumer");
   require(main(()->!gate().armed()&&field("diagnosticFrame")==null),
    "A reader's synchronous STOP was followed by recreated diagnostic pixels");
   assertNoInput();
   check("actual_reader_stop_prevents_diagnostic_frame_resurrection",new JSONObject()
    .put("consumer_deliveries",1).put("reader_stop","synchronous hidePanel within actual screenshot consumer")
    .put("diagnostic_frame_after_stop",false).put("gestures_dispatched",0));
  }finally{main(()->{stopWhenDelivered=false;service.hidePanel();return null;});}
 }
 private void prepareReadOnly(Owner owner)throws Exception{
  main(()->{
   service.hidePanel();Method arm=PilotService.class.getDeclaredMethod("arm");arm.setAccessible(true);arm.invoke(service);
   require(gate().armed()&&fixturePackage.equals(field("target")),"Production arm did not bind the actual fixture owner");
   require(((Number)field("visualWindow")).intValue()==owner.id,"Production visual owner differs from verified window");
   require(owner.bounds.equals(field("armedBounds")),"Production owner bounds differ from fullscreen geometry");
   ((InputPermit)field("permit")).revoke();set("boardRoi",new Rect(owner.screen.x/5,owner.screen.y/5,
    owner.screen.x*4/5,owner.screen.y*4/5));set("discoveringBoard",true);return null;
  });
 }
 private void invokeCapture(long epoch)throws Exception{
  Class<?> consumer=Class.forName("org.localpilot.android.PilotService$ImageResult");
  Object proxy=Proxy.newProxyInstance(consumer.getClassLoader(),new Class<?>[]{consumer},(p,m,args)->{
   if(m.getName().equals("ready")){delivered.incrementAndGet();if(stopWhenDelivered)service.hidePanel();}return null;
  });
  Method capture=PilotService.class.getDeclaredMethod("captureImage",long.class,consumer);
  capture.setAccessible(true);capture.invoke(service,epoch,proxy);
 }
 private void assertNoInput()throws Exception{
  require(main(()->((Number)field("dispatchedInputs")).longValue()==0
   &&((Number)field("captureSent")).longValue()==0&&((Number)field("captureCompleted")).longValue()==0
   &&!((InputPermit)field("permit")).allows(gate().epoch(),SystemClock.elapsedRealtime())),
   "Capture-only acceptance dispatched input or acquired a permit");
 }
 private void launchFixture(boolean secure)throws Exception{
  int oldWindow=lastFixtureWindow;
  getTargetContext().startActivity(new Intent().setClassName(fixturePackage,CaptureAcceptanceFixtureActivity.class.getName())
   .putExtra("secure",secure).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
  await(()->{try{return owner(secure).id!=oldWindow;}catch(Exception error){return false;}},4_000,"Actual fullscreen capture fixture did not become a new verified owner");
  lastFixtureWindow=owner(secure).id;
  // Wait for a compositor frame after the window and its accessibility marker.
  automation.waitForIdle(100,2_000);SystemClock.sleep(100);
 }
 private Owner owner(boolean secure)throws Exception{
  return main(()->{
   Point screen=new Point();((WindowManager)service.getSystemService(PilotService.WINDOW_SERVICE))
    .getDefaultDisplay().getRealSize(screen);
   for(AccessibilityWindowInfo window:service.getWindows()){
    if(window.getType()!=AccessibilityWindowInfo.TYPE_APPLICATION||!window.isFocused())continue;
    AccessibilityNodeInfo root=window.getRoot();if(root==null)continue;
    try{
     if(!fixturePackage.equals(String.valueOf(root.getPackageName()))||root.getWindowId()!=window.getId())continue;
     Rect bounds=new Rect();window.getBoundsInScreen(bounds);
     require(bounds.equals(new Rect(0,0,screen.x,screen.y)),"Actual owner is not origin-zero fullscreen: "+bounds);
     require(marker(root,"LOCALPILOT_CAPTURE_ACCEPTANCE secure="+secure+" touches=0",0),
      "Actual fixture marker absent, secure state differs, or an unexpected touch was delivered");
     return new Owner(window.getId(),bounds,screen);
    }finally{root.recycle();}
   }
   throw new AssertionError("No verified focused fixture owner");
  });
 }
 private boolean marker(AccessibilityNodeInfo node,String expected,int depth){
  if(depth>15)return false;if(expected.equals(String.valueOf(node.getContentDescription())))return true;
  for(int i=0;i<Math.min(30,node.getChildCount());i++){
   AccessibilityNodeInfo child=node.getChild(i);if(child==null)continue;
   try{if(marker(child,expected,depth+1))return true;}finally{child.recycle();}
  }return false;
 }
 private WindowCaptureRoute.Source route(Owner owner){
  return WindowCaptureRoute.choose(Build.VERSION.SDK_INT,owner.id,owner.bounds.left,owner.bounds.top,
   owner.bounds.right,owner.bounds.bottom,owner.screen.x,owner.screen.y,owner.screen.x/5,owner.screen.y/5,
   owner.screen.x*4/5,owner.screen.y*4/5);
 }
 private Capture capture(Owner owner,WindowCaptureRoute.Source source)throws Exception{
  throttle();Capture result=new Capture();CountDownLatch done=new CountDownLatch(1);
  long requested=SystemClock.uptimeMillis();
  main(()->{WindowCaptureBridge.request(service,source,owner.id,service.getMainExecutor(),new AccessibilityService.TakeScreenshotCallback(){
   @Override public void onFailure(int error){result.error=error;done.countDown();}
   @Override public void onSuccess(AccessibilityService.ScreenshotResult shot){
    HardwareBuffer buffer=shot.getHardwareBuffer();Bitmap hardware=null,bitmap=null;
    try{
     hardware=Bitmap.wrapHardwareBuffer(buffer,shot.getColorSpace());require(hardware!=null,"HardwareBuffer could not wrap");
     bitmap=hardware.copy(Bitmap.Config.ARGB_8888,false);require(bitmap!=null,"HardwareBuffer pixels could not copy");
     result.width=bitmap.getWidth();result.height=bitmap.getHeight();result.timestamp=shot.getTimestamp();
     result.fresh=CaptureFreshness.valid(shot.getTimestamp(),requested,SystemClock.uptimeMillis(),0);
     result.nearBlack=FrameBlackoutGate.nearBlack(result.width,result.height,result.width/5,result.height/5,
      result.width*4/5,result.height*4/5,bitmap::getPixel);
     for(int i=0;i<4;i++)result.colors[i]=bitmap.getPixel(result.width*(i%2==0?1:4)/5,result.height*(i/2==0?1:4)/5);
    }catch(Throwable error){result.failure=error;}
    finally{if(bitmap!=null)bitmap.recycle();if(hardware!=null)hardware.recycle();buffer.close();done.countDown();}
   }
  });return null;});
  require(done.await(5,TimeUnit.SECONDS),"Actual Android screenshot callback did not arrive");
  if(result.failure!=null)throw new AssertionError("Actual screenshot decode failed",result.failure);
  return result;
 }
 private void throttle(){long wait=1200-(SystemClock.uptimeMillis()-previousRequest);if(wait>0)SystemClock.sleep(wait);previousRequest=SystemClock.uptimeMillis();}
 private boolean closeColor(int actual,int expected){for(int shift=0;shift<24;shift+=8)if(Math.abs((actual>>shift&255)-(expected>>shift&255))>5)return false;return true;}
 private void check(String name,JSONObject detail)throws Exception{checks.put(detail.put("name",name).put("result","PASS"));}
 private ActionGate gate()throws Exception{return (ActionGate)field("gate");}
 private Object field(String name)throws Exception{Field f=PilotService.class.getDeclaredField(name);f.setAccessible(true);return f.get(service);}
 private void set(String name,Object value)throws Exception{Field f=PilotService.class.getDeclaredField(name);f.setAccessible(true);f.set(service,value);}
 private <T>T main(Callable<T> operation)throws Exception{
  AtomicReference<T> value=new AtomicReference<>();AtomicReference<Throwable> error=new AtomicReference<>();
  runOnMainSync(()->{try{value.set(operation.call());}catch(Throwable caught){error.set(caught);}});
  if(error.get()!=null)throw new Exception("Main-thread capture assertion/setup failed",error.get());return value.get();
 }
 private void await(Condition condition,long timeout,String failure)throws Exception{
  long until=SystemClock.uptimeMillis()+timeout;
  do{if(condition.get())return;SystemClock.sleep(60);}while(SystemClock.uptimeMillis()<until);
  throw new AssertionError(failure);
 }
 private static void require(boolean condition,String failure){if(!condition)throw new AssertionError(failure);}
 private interface Condition{boolean get()throws Exception;}
 private static final class Owner{final int id;final Rect bounds;final Point screen;Owner(int id,Rect b,Point s){this.id=id;bounds=b;screen=s;}}
 private static final class Capture{int error=-1,width,height;long timestamp;boolean fresh,nearBlack;final int[] colors=new int[4];Throwable failure;}
}
