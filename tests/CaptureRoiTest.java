package org.localpilot.android;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Independent pixel patterns exercise validity, ownership and full-reader routing. */
public final class CaptureRoiTest {
 private static int assertions;
 private static void check(boolean ok,String message){assertions++;if(!ok)throw new AssertionError(message);}
 private static void invalid(Runnable action){assertions++;try{action.run();throw new AssertionError("invalid plane/plan accepted");}catch(IllegalArgumentException expected){}}
 private static void expired(Runnable action){assertions++;try{action.run();throw new AssertionError("expired/unsafe lease accepted");}catch(IllegalStateException expected){}}
 private static void outside(Runnable action){assertions++;try{action.run();throw new AssertionError("outside pixels read");}catch(IndexOutOfBoundsException expected){}}
 private static int color(int x,int y,int seed){return 0xff000000|((x+seed)&255)<<16|((y+seed)&255)<<8|seed;}
 private static ByteBuffer plane(int w,int h,int seed,boolean direct){
  int stride=w*6+5,size=3+(h-1)*stride+(w-1)*6+4;
  ByteBuffer bytes=direct?ByteBuffer.allocateDirect(size):ByteBuffer.allocate(size);
  bytes.position(3);
  for(int y=0;y<h;y++)for(int x=0;x<w;x++){
   int at=3+y*stride+x*6,c=color(x,y,seed);
   bytes.put(at,(byte)(c>>>16));bytes.put(at+1,(byte)(c>>>8));bytes.put(at+2,(byte)c);bytes.put(at+3,(byte)255);
  }
  return bytes;
 }
 private static void strides(){
  for(boolean direct:new boolean[]{false,true}){
   int w=4,h=3,stride=w*6+5;ByteBuffer bytes=plane(w,h,7,direct);
   int[] out=new int[w*h];Arrays.fill(out,0x12345678);
   RgbaFrameDecoder.decodeRoiInPlace(bytes,stride,6,w,h,1,1,3,3,out);
   for(int y=0;y<h;y++)for(int x=0;x<w;x++)check(out[y*w+x]==(x>=1&&x<3&&y>=1?color(x,y,7):0x12345678),"global output stride and untouched exterior");
   check(bytes.position()==3,"native plane position preserved");
   Arrays.fill(out,31);int oldLimit=bytes.limit();bytes.limit(3+stride+6+3);
   invalid(()->RgbaFrameDecoder.decodeRoiInPlace(bytes,stride,6,w,h,1,1,2,2,out));
   for(int value:out)check(value==31,"truncation validated before any output write");
   bytes.limit(oldLimit);
   invalid(()->RgbaFrameDecoder.decodeRoiInPlace(bytes,stride,6,w,h,-1,0,2,2,out));
   invalid(()->RgbaFrameDecoder.decodeRoiInPlace(bytes,1,6,w,h,0,0,1,1,out));
   invalid(()->RgbaFrameDecoder.decodeRoiInPlace(bytes,Integer.MAX_VALUE,Integer.MAX_VALUE,Integer.MAX_VALUE,3,0,0,1,2,out));
   invalid(()->RgbaFrameDecoder.decodeRoiInPlace(bytes,stride,6,w,h,0,0,1,1,new int[1]));
  }
 }
 private static void leases(){
  int w=4,h=3,stride=w*6+5;ByteBuffer first=plane(w,h,7,false),second=plane(w,h,19,true);
  DecodedFrameBuffer buffer=new DecodedFrameBuffer(w,h);
  long one=buffer.decode(first,stride,6,11,FrameDecodePlan.full());
  BoundedPixels old=new BoundedPixels(buffer,11,one);
  check(buffer.isLeased()&&buffer.fullFrame(one)&&old.rgb(0,0)==color(0,0,7),"full decode publishes an owned lease");
  expired(()->buffer.decode(second,stride,6,11,FrameDecodePlan.full()));
  check(old.rgb(3,2)==color(3,2,7),"outstanding ownership prevents overwrite");
  expired(()->new BoundedPixels(buffer,12,one));
  int[] copy=new int[w*h];buffer.copyFullTo(one,copy);copy[0]=42;
  check(old.rgb(0,0)!=42,"full snapshots never expose the owned array");
  check(buffer.release(one)&&!buffer.isLeased(),"release returns the buffer to its producer");
  expired(()->old.rgb(1,1));
  FrameDecodePlan roi=FrameDecodePlan.region(11,w,h,1,1,3,3);
  long two=buffer.decode(second,stride,6,11,roi);BoundedPixels current=new BoundedPixels(buffer,11,two);
  check(two>one&&!buffer.fullFrame(two)&&buffer.contains(two,1,1,3,3),"new generation carries strict fresh bounds");
  check(current.rgb(2,2)==color(2,2,19),"ROI reads current global coordinates");
  outside(()->current.rgb(0,1));outside(()->current.rgb(3,1));outside(()->current.rgb(1,0));outside(()->current.rgb(1,3));
  expired(()->buffer.copyFullTo(two,new int[w*h]));
  expired(()->old.rgb(1,1));
  check(!buffer.release(one)&&buffer.isLeased(),"late cleanup cannot release a successor lease");
  check(buffer.release(two),"current cleanup releases its own lease");
  long three=buffer.decode(first,stride,6,22,roi);
  check(three>two&&buffer.fullFrame(three),"old-session ROI plan forces a fresh full frame");
  expired(()->buffer.require(three,11));buffer.require(three,22);
  buffer.close();expired(()->buffer.rgb(three,1,1));
  expired(()->buffer.decode(first,stride,6,22,FrameDecodePlan.full()));
 }
 private static void failedDecode(){
  DecodedFrameBuffer buffer=new DecodedFrameBuffer(4,3);ByteBuffer bytes=plane(4,3,8,false);
  int oldLimit=bytes.limit();bytes.limit(bytes.position()+3);
  invalid(()->buffer.decode(bytes,29,6,8,FrameDecodePlan.full()));
  check(!buffer.isLeased()&&buffer.decodedPixels()==0,"invalid input cannot publish pixels or a frame lease");
  bytes.limit(oldLimit);long token=buffer.decode(bytes,29,6,8,FrameDecodePlan.full());
  check(token==1&&buffer.rgb(token,3,2)==color(3,2,8),"validation failure does not poison later capture");buffer.release(token);
 }
 private static void crossThreadOwnership()throws Exception{
  DecodedFrameBuffer buffer=new DecodedFrameBuffer(4,3);ByteBuffer a=plane(4,3,5,false),b=plane(4,3,21,true);
  long token=buffer.decode(a,29,6,17,FrameDecodePlan.full());
  CountDownLatch reading=new CountDownLatch(1),reuse=new CountDownLatch(1),done=new CountDownLatch(1);
  AtomicReference<Throwable> error=new AtomicReference<>();
  Thread consumer=new Thread(()->{
   try{
    for(int i=0;i<10000;i++)if(buffer.rgb(token,2,1)!=color(2,1,5))throw new AssertionError("producer overwrote a live reader");
    reading.countDown();if(!reuse.await(3,TimeUnit.SECONDS))throw new AssertionError("producer did not finish");
    try{buffer.rgb(token,2,1);throw new AssertionError("late worker accepted pixels from a successor generation");}
    catch(IllegalStateException expected){}
   }catch(Throwable failure){error.set(failure);}finally{reading.countDown();done.countDown();}
  },"capture-roi-reader");
  consumer.start();check(reading.await(3,TimeUnit.SECONDS),"reader reaches lease handoff");
  expired(()->buffer.decode(b,29,6,17,FrameDecodePlan.full()));
  check(buffer.release(token),"owner releases only after its readers finish");
  long next=buffer.decode(b,29,6,18,FrameDecodePlan.full());reuse.countDown();
  check(done.await(3,TimeUnit.SECONDS),"late reader checks a retained token");consumer.join(3000);
  check(!consumer.isAlive()&&error.get()==null,"cross-thread reads preserve generation ownership: "+error.get());
  check(buffer.rgb(next,2,1)==color(2,1,21),"successor session owns independently decoded pixels");buffer.release(next);
 }
 private static void plansAndReaders(){
  FrameDecodePlan roi=FrameDecodePlan.region(9,160,240,20,30,140,210);
  check(roi.allowsRoi(9,160,240)&&!roi.allowsRoi(10,160,240)&&!roi.allowsRoi(9,160,241),"ROI is bound to session and exact frame geometry");
  check(roi.union(25,35,100,190)==roi,"contained consumer needs no plan allocation");
  FrameDecodePlan union=roi.union(0,8,160,192);
  check(union.left==0&&union.top==8&&union.right==160&&union.bottom==210,"reader envelope is explicitly unioned");
  for(int consumer:new int[]{FrameDecodePlan.BOARD_SEARCH,FrameDecodePlan.RESULT_SCREEN,FrameDecodePlan.SCENE_RECOVERY,
    FrameDecodePlan.DIAGNOSTICS,FrameDecodePlan.CALIBRATION,FrameDecodePlan.GEOMETRY_CHANGED,FrameDecodePlan.CONTEXT_CHANGED})
   check(!roi.requireFull(consumer).allowsRoi(9,160,240),"full-frame consumer overrides partial capture");
  invalid(()->roi.union(-1,0,40,40));invalid(()->roi.requireFull(128));
  ByteBuffer bytes=ByteBuffer.allocate(160*240*4);
  DecodedFrameBuffer frame=new DecodedFrameBuffer(160,240);long token=frame.decode(bytes,640,4,9,roi);
  BoundedPixels guarded=new BoundedPixels(frame,9,token);
  VisionV2Core temporal=new VisionV2Core();VisionV2Core.Region region=new VisionV2Core.Region(20,30,140,210);
  temporal.observe(guarded::rgb,160,240,region,null,100,9,1);
  check(temporal.observe(guarded::rgb,160,240,region,null,200,9,1).ready(),"Vision V2 samples only its declared ROI");
  check(!JumperVision.detectRecovering(guarded,160,240,20,30,140,210,0,0,0,0,
    200,null,null,SceneRecovery.Pass.BRIGHTER).usable(),"Jumper recovery reads bounded pixels without inventing a player");
  CustomVisionConfig calibration=new CustomVisionConfig(new int[]{0xff0000,0x00ff00,0x0000ff},
    new float[]{.5f,.5f,.8f},new float[]{.8f,.5f,.2f});
  for(ArcadeProfile profile:new ArcadeProfile[]{ArcadeProfile.PADDLE,ArcadeProfile.TRAFFIC,ArcadeProfile.FLAPPY}){
   check(!ArcadeVision.detectRecovering(profile,guarded,160,240,20,30,140,210,0,0,0,0,
     200,null,SceneRecovery.Pass.DIMMER).usable(),"Arcade recovery reads only its bounded ROI");
   check(!CustomArcadeVision.detect(profile,guarded::rgb,20,30,140,210,0,0,0,0,200,null,calibration).usable(),
     "Calibrated arcade reads only its bounded ROI");
  }
  outside(()->GameOverScreen.detect((x,y)->frame.rgb(token,x,y),0,0,160,240,0,0,0,0));
  frame.release(token);long full=frame.decode(bytes,640,4,9,roi.requireFull(FrameDecodePlan.RESULT_SCREEN));
  check(!GameOverScreen.detect((x,y)->frame.rgb(full,x,y),0,0,160,240,0,0,0,0),"full-frame result reader sees current pixels without bounds exceptions");
  frame.release(full);
 }
 private static void allocation(){
  var bean=java.lang.management.ManagementFactory.getThreadMXBean();
  if(!(bean instanceof com.sun.management.ThreadMXBean memory)||!memory.isThreadAllocatedMemorySupported()){
   System.out.println("Capture ROI allocation: SKIP (JVM counter unavailable)");return;
  }
  memory.setThreadAllocatedMemoryEnabled(true);
  DecodedFrameBuffer buffer=new DecodedFrameBuffer(8,6);ByteBuffer bytes=ByteBuffer.allocateDirect(8*6*4);
  FrameDecodePlan roi=FrameDecodePlan.region(3,8,6,1,1,7,5);
  for(int i=0;i<20000;i++){long token=buffer.decode(bytes,32,4,3,roi);buffer.rgb(token,1,1);buffer.release(token);}
  long thread=Thread.currentThread().getId(),allocated=Long.MAX_VALUE,sum=0;
  // JVM management/JIT bookkeeping can occur once after the concurrency test.
  // Require an entirely allocation-free 20000-frame window, not a per-frame allowance.
  for(int window=0;window<3;window++){
   long start=memory.getThreadAllocatedBytes(thread);
   for(int i=0;i<20000;i++){long token=buffer.decode(bytes,32,4,3,roi);sum+=buffer.rgb(token,1,1);buffer.release(token);}
   allocated=Math.min(allocated,memory.getThreadAllocatedBytes(thread)-start);
  }
  check(sum==0&&allocated==0,"warm ROI decode/read/release must allocate zero heap bytes; observed="+allocated);
  System.out.println("Capture ROI allocation: PASS, "+allocated+" bytes for a 20000-frame measurement window");
 }
 public static void main(String[]args)throws Exception{strides();leases();failedDecode();crossThreadOwnership();plansAndReaders();allocation();System.out.println("Capture ROI: "+assertions+" assertions PASS");}
}
