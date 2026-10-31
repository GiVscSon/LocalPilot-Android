package org.localpilot.android;
import java.util.*;
/** Synthetic evidence for the recorded 1220x2712 phone geometry; not live gameplay. */
public final class GridStage3Test {
 private static int checks;
 private static void check(boolean yes,String why){checks++;if(!yes)throw new AssertionError(why);}
 private static final int LEFT=103,TOP=1241,RIGHT=1099,BOTTOM=2262;
 private static final int[] COLORS={0xe72832,0xfbc72b,0x4aa91f,0x266ac6};
 private static final int[] RINGS={23,29,30},BLANKS={31,32};
 private static int[] types(){int[] a=new int[36];for(int i=0;i<a.length;i++)a[i]=i%4;
  a[0]=0;a[1]=1;a[2]=0;a[7]=0;return a;}
 private static boolean includes(int[] set,int value){for(int v:set)if(v==value)return true;return false;}
 private static int rgb(int x,int y){
  if(x<LEFT||y<TOP||x>=RIGHT||y>=BOTTOM)return 0x1b1b1b;
  int col=Math.min(5,(x-LEFT)*6/(RIGHT-LEFT)),row=Math.min(5,(y-TOP)*6/(BOTTOM-TOP)),i=row*6+col;
  float l=LEFT+col*(RIGHT-LEFT)/6f,t=TOP+row*(BOTTOM-TOP)/6f;
  float w=(RIGHT-LEFT)/6f,h=(BOTTOM-TOP)/6f;
  float nx=(x-l)/w,ny=(y-t)/h;
  if(includes(BLANKS,i))return 0x505050;
  if(includes(RINGS,i)){
   if(nx>=.35f&&nx<=.65f&&ny>=.35f&&ny<=.65f)return 0x242424;
   return 0x8c18af;
  }
  if(i==35){
   int tile=0x9a4045;
   if(nx<.12f||nx>.88f||ny<.12f||ny>.88f)return 0x707070;
   if(nx>=.25f&&nx<=.70f&&ny>=.20f&&ny<=.65f)tile=0xbbbaad;
   if(ny>=.33f&&ny<=.48f&&(nx>=.29f&&nx<=.38f||nx>=.49f&&nx<=.59f))tile=0x323232;
   if(nx>=.15f&&nx<=.32f&&ny>=.52f&&ny<=.75f||nx>=.62f&&nx<=.79f&&ny>=.64f&&ny<=.84f)tile=0xbbbaad;
   return tile;
  }
  if(nx<.12f||nx>.88f||ny<.12f||ny>.88f)return 0xdddddd;
  int color=COLORS[types()[i]],shade=nx>.57f&&ny>.57f?-12:0;
  return (Math.max(0,(color>>16&255)+shade)<<16)
     |(Math.max(0,(color>>8&255)+shade)<<8)
     |Math.max(0,(color&255)+shade);
 }
 public static void main(String[] args){
  check(RIGHT-LEFT==996&&BOTTOM-TOP==1021,"recorded ROI exactly reproduced");
  check((RIGHT-LEFT)/6==166&&(BOTTOM-TOP)/6==170,"cell geometry");
  var read=GridPuzzleVision.detect(GridStage3Test::rgb,LEFT,TOP,RIGHT,BOTTOM,6,6,0,0,0,0,1000,null,true);
  check(read.unknownIndices().length==5,"exactly 5 unknown observations; got="+read.unknownIndices().length+" reasons="+read.unknownReasonCounts()+" ringReasons="+read.reason(3,5)+","+read.reason(4,5)+","+read.reason(5,0)+" cells="+read.unknownSummary(36));
  check(read.unknownReasonCounts().contains("RING_NO_MATCH=3"),"3 ring rejections");
  check(read.unknownReasonCounts().contains("NO_SAMPLE=2"),"2 absent samples");
  check(read.partialUsable()&&!read.usable(),"31/36 known supports partial classification only");
  check(read.reason(5,5)==GridPuzzleVision.CellReason.PROTECTED_SKULL
      &&read.specialCellsMask().get(35)&&read.type(5,5)==0,"protected skull never normal token");
  check(read.cellReasonDetails().contains("R4C6:RING_NO_MATCH"),"ring failure coordinates retained");
  check(GridProfileRules.mode(11)==GridPuzzlePolicy.Mode.SWAP_MATCH3,"protected game stays a swap");
  var board=new GridPuzzle(6,6,read.cells());
  var action=GridPuzzlePolicy.choosePartial(board,GridProfileRules.mode(11),2,0,0,read.specialCellsMask());
  check(action!=null,"legal move exists despite ring/no-sample unknowns");
  for(int index:action.clearedCells())check(read.cells()[index]>0&&!read.specialCellsMask().get(index),"clear excludes unknown/skull/booster");
  int from=action.fromRow*6+action.fromCol,to=action.toRow*6+action.toCol;
  check(read.cells()[from]>0&&read.cells()[to]>0
    &&!read.specialCellsMask().get(from)&&!read.specialCellsMask().get(to),"both swap endpoints ordinary known");
  int[] a=read.cells(),b=a.clone();b[23]=1;
  GridPuzzleSession partial=new GridPuzzleSession();
  check(partial.observePartial(6,6,a,read.specialCellsMask(),1000,1000)==GridPuzzleSession.Status.WAIT_STABLE,"first frame waits");
  check(partial.observePartial(6,6,b,read.specialCellsMask(),1100,1100)==GridPuzzleSession.Status.READY,"2 frames permit conservative intersection");
  check(partial.stableCells()[23]==-1&&partial.stableCells()[31]==-1,"intermittent unknown remains unknown");
  check(partial.stableReads()>=2,"stableReads threshold visible");
  var safe=new GridPuzzle(6,6,partial.stableCells());
  var safeChoice=GridPuzzlePolicy.choosePartial(safe,GridPuzzlePolicy.Mode.SWAP_MATCH3,2,0,0,partial.stableSpecialCells());
  check(safeChoice!=null,"intersected board retains a legal swap");
  check(safe.at(safeChoice.fromRow,safeChoice.fromCol)>0&&safe.at(safeChoice.toRow,safeChoice.toCol)>0,"safe partial endpoints");
  // For Match World 7x7, two changed frames during a cascade must not
  // validate the move if the observations disagree.
  int[] old=new int[49];for(int i=0;i<old.length;i++)old[i]=100+i;
  old[0]=1;old[1]=2;old[2]=1;old[8]=1;
  var swap=GridPuzzlePolicy.choose(new GridPuzzle(7,7,old),GridPuzzlePolicy.Mode.SWAP_MATCH3);
  check(swap!=null&&swap.expectedCleared>=3,"synthetic 7x7 legal swap");
  GridPuzzleSession s=new GridPuzzleSession();
  s.observe(7,7,old,1000,1000);
  check(s.observe(7,7,old,1100,1100)==GridPuzzleSession.Status.READY,"2 stable pre-action frames");
  s.sent(old,1200,swap);s.acknowledge(1250);
  int[] moving=old.clone();moving[0]=90;moving[2]=91;
  int[] movingOther=moving.clone();movingOther[2]=92;
  check(s.observe(7,7,moving,1500,1500)==GridPuzzleSession.Status.WAIT_RESPONSE,"first cascade frame not verified");
  check(s.observe(7,7,movingOther,1600,1600)==GridPuzzleSession.Status.WAIT_RESPONSE,"different second cascade frame not verified");
  check(s.stableReads()==1&&s.verifiedChanges()==0,"unstable animation resets reads");
  check(s.observe(7,7,moving,1700,1700)==GridPuzzleSession.Status.WAIT_RESPONSE,"first settled candidate");
  check(s.observe(7,7,moving,1800,1800)==GridPuzzleSession.Status.CHANGED,"second stable post ACK response confirms");
  check(s.verifiedChanges()==1&&s.stableReads()==2,"exactly one verified transition after ACK");
  System.out.println("Grid Stage 3: "+checks+" assertions PASS; synthetic 6x6/7x7 only");
 }
}
