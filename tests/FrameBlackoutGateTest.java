package org.localpilot.android;

/** Synthetic Java 17 safety regression; does not claim the compositor muted a device frame. */
public final class FrameBlackoutGateTest {
 private static int count;
 private static void check(boolean condition,String why){
  count++;if(!condition)throw new AssertionError(why);
 }
 private static void rejected(Runnable call,String why){
  boolean thrown=false;
  try{call.run();}catch(IllegalArgumentException expected){thrown=true;}
  check(thrown,why);
 }
 public static void main(String[] args){
  final int width=320,height=240,left=40,top=20,right=200,bottom=180;
  FrameBlackoutGate.Pixels black=(x,y)->0xff000000;
  check(FrameBlackoutGate.nearBlack(width,height,left,top,right,bottom,black),
   "all-black ROI must be unknown, not game-over evidence");
  check(!FrameBlackoutGate.nearBlack(width,height,left,top,right,bottom,
    (x,y)->0xff010101),"dark but non-black ROI must not be classified as muted");
  check(!FrameBlackoutGate.nearBlack(width,height,left,top,right,bottom,
    (x,y)->(x+y)%2==0?0xff000000:0xffffffff),
   "mixed scene is not near-black");
  check(FrameBlackoutGate.nearBlack(width,height,left,top,right,bottom,
    (x,y)->y==25&&(x==42||x==47)?0xffeeeeee:0xff000000),
   "2 of 512 non-black samples remain inside 99.5% bound");
  check(!FrameBlackoutGate.nearBlack(width,height,left,top,right,bottom,
    (x,y)->y==25&&(x==42||x==47||x==52)?0xffffffff:0xff000000),
   "3 of 512 non-black samples cross threshold");
  int[] sampleCount={0};
  check(FrameBlackoutGate.nearBlack(width,height,left,top,right,bottom,
   (x,y)->{
    check(x>=left&&x<right&&y>=top&&y<bottom,"sample strictly inside ROI");
    sampleCount[0]++;
    return 0xff000000;
   }),"ROI pixels are black");
  check(sampleCount[0]==512,"constant bounded sample count");
  rejected(()->FrameBlackoutGate.nearBlack(width,height,-1,top,right,bottom,black),
   "negative ROI blocked");
  rejected(()->FrameBlackoutGate.nearBlack(width,height,left,top,width+1,bottom,black),
   "out of frame blocked");
  rejected(()->FrameBlackoutGate.nearBlack(width,height,left,top,left,bottom,black),
   "empty ROI blocked");
  rejected(()->FrameBlackoutGate.nearBlack(width,height,left,top,right,bottom,null),
   "null source blocked");
  CaptureRetry retry=new CaptureRetry();
  check(retry.allow(17,"NEAR_BLACK_FRAME"),"first anomalous screenshot retry");
  check(retry.allow(17,"NEAR_BLACK_FRAME"),"second retry bounded");
  check(retry.allow(17,"NEAR_BLACK_FRAME"),"third retry bounded");
  check(!retry.allow(17,"NEAR_BLACK_FRAME"),"fourth must STOP");
  check(!retry.allow(17,"COMPOSITOR_MUTED_BLACK"),"unverified cause is not retry authority");
  retry.fresh(17);
  check(retry.allow(17,"NEAR_BLACK_FRAME"),"fresh clean frame restores finite budget");
  check(!retry.allow(16,"NEAR_BLACK_FRAME"),"old epoch forbidden");
  System.out.println("FRAME_BLACKOUT_GATE_PASS checks="+count+
    " samples=512 nearBlackThresholdPermille=995 syntheticOnly=true");
 }
}
