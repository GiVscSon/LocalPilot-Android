package org.localpilot.android;

import java.util.List;

/** Deterministic synthetic pixels: no Android SDK or training-success assumptions. */
public final class VisionV2CoreTest {
 private static int checks;
 private static void ok(boolean good,String why){
  checks++;if(!good)throw new AssertionError(why);
 }
 private static final int W=240,H=320;
 private static VisionV2Core.Region roi(){return new VisionV2Core.Region(0,0,W,H);}
 private static int pixel(int x,int y,int offsetX,int gain,boolean withBall){
  // Constant smooth static background with weak spatial detail.
  int base=45+((x/24+y/35)%2)*10;
  int r=base,g=base+10,b=base+18;
  if(withBall&&x>=offsetX&&x<offsetX+28&&y>=124&&y<152){
   r=220;g=45;b=35;
  }
  return 0xff000000|(Math.min(255,r+gain)<<16)
    |(Math.min(255,g+gain)<<8)|Math.min(255,b+gain);
 }
 private static VisionV2Core.Frame input(VisionV2Core v2,long epoch,long timestamp,int x,int exposure,
                                         VisionV2Core.Region exclusion){
  return v2.observe((px,py)->pixel(px,py,x,exposure,true),W,H,roi(),exclusion,timestamp,epoch,1);
 }
 public static void main(String[] args){
  var core=new VisionV2Core();
  var first=input(core,12,1000,55,0,null);
  ok(first.status()==VisionV2Core.Status.PRIMING&&!first.ready(),"one frame never authorizes movement");
  var second=input(core,12,1030,90,0,null);
  ok(second.ready()&&second.hasLocalMotion(),"translated object creates local change candidates");
  ok(second.changedCells()>15&&!second.movingObjects().isEmpty(),"moving bounding boxes available");
  ok(second.movingObjects().stream().allMatch(b->b.left()>=0&&b.right()<=1
     &&b.top()>=0&&b.bottom()<=1),"boxes remain inside active ROI");
  var illumination=input(core,12,1060,90,12,null);
  ok(illumination.ready(),"exposure shift still produces fresh frame");
  ok(illumination.changedCells()<10,"global brightness offset is not object movement");
  var replay=input(core,12,1060,130,0,null);
  ok(replay.status()==VisionV2Core.Status.REPLAY_REJECTED&&!replay.ready(),
     "out-of-order timestamps never modify evidence");
  var afterReplay=input(core,12,1090,110,0,null);
  ok(afterReplay.ready(),"replay did not poison valid next observation");
  var blocked=new VisionV2Core.Region(0,0,205,H);
  var hidden=input(core,12,1120,120,0,blocked);
  ok(hidden.status()==VisionV2Core.Status.INSUFFICIENT_PIXELS&&!hidden.ready(),
     "masked STOP panel must not become foreground evidence");
  ok(input(core,12,1150,120,0,null).status()==VisionV2Core.Status.GEOMETRY_RESET,
    "excluded region change resets temporal identities");
  var afterReset=input(core,12,1180,145,0,null);
  ok(afterReset.ready(),"two newly aligned frames recover evidence");
  var largeGap=input(core,12,5000,110,0,null);
  ok(largeGap.status()==VisionV2Core.Status.GAP_RESET&&!largeGap.ready(),
     "old frames never bridge capture freezes");
  ok(input(core,13,5030,110,0,null).status()==VisionV2Core.Status.GEOMETRY_RESET,
     "new game epoch cannot reuse previous visual memory");
  core.reset();
  ok(core.last()==null&&input(core,14,7000,55,0,null).status()==VisionV2Core.Status.PRIMING,
     "STOP clears previous game pixels");
  var maskCore=new VisionV2Core();
  var exclusion=new VisionV2Core.Region(0,0,60,60);
  maskCore.observe((x,y)->x<60&&y<60?0xffff0000:0xff444444,
    W,H,roi(),exclusion,1,1,0);
  var motion=maskCore.observe((x,y)->x<60&&y<60?0xff00ff00:0xff444444,
    W,H,roi(),exclusion,30,1,0);
  ok(motion.ready()&&motion.changedCells()==0,"changes behind STOP mask are ignored");
  boolean rejected=false;
  try{new VisionV2Core.Region(2,2,2,5);}catch(IllegalArgumentException e){rejected=true;}
  ok(rejected,"bad ROI rejected");
  System.out.println("VISION_V2_PASS checks="+checks);
 }
}
