package org.localpilot.android;

import java.util.Arrays;
import java.util.BitSet;

/**
 * Synthetic safety regression for imperfect 6x6 visual boards.
 * This does not claim that gestures were delivered to a real Android game.
 */
public final class GridPartialVerifyTest {
 private static int checks;
 private static void check(boolean ok,String what){checks++;if(!ok)throw new AssertionError(what);}
 private static int[] scene(){
  int[] a=new int[36];for(int i=0;i<a.length;i++)a[i]=100+i;
  a[0]=1;a[1]=2;a[2]=1;a[7]=1;
  for(int i=12;i<=18;i++)a[i]=-1;
  return a;
 }
 private static int token(int x,int y,int[] tiles){
  int col=x/80,row=y/80,index=row*6+col,value=tiles[index];
  if(value<0)return 0x505050;
  float nx=x%80/80f,ny=y%80/80f;
  if(nx<.12f||nx>.88f||ny<.12f||ny>.88f)return 0xdddddd;
  int[] colors={0xe72832,0xfbc72b,0x4aa91f,0x266ac6};
  int rgb=colors[value%colors.length];
  if(nx>.57f&&ny>.57f){
   int r=Math.max(0,(rgb>>16&255)-12),g=Math.max(0,(rgb>>8&255)-12),b=Math.max(0,(rgb&255)-12);
   return r<<16|g<<8|b;
  }
  return rgb;
 }
 public static void main(String[] args){
  int[] cells=scene();GridPuzzle partial=new GridPuzzle(6,6,cells);
  check(!partial.known(),"board is genuinely incomplete");
  check(GridPuzzlePolicy.choose(partial,GridPuzzlePolicy.Mode.SWAP_MATCH3)==null,
    "legacy strict solver must not change its contract");
  BitSet special=new BitSet(36);special.set(31);
  var swap=GridPuzzlePolicy.choosePartial(partial,GridPuzzlePolicy.Mode.SWAP_MATCH3,2,0,0,special);
  check(swap!=null&&swap.expectedCleared==3,"safe swap is found among recognized cells");
  for(int idx:swap.clearedCells())check(cells[idx]>0&&!special.get(idx),
    "all predicted clears are known ordinary cells");
  int from=swap.fromRow*6+swap.fromCol,to=swap.toRow*6+swap.toCol;
  check(cells[from]>0&&cells[to]>0&&!special.get(from)&&!special.get(to),
    "both endpoints are confidently classified");

  int[] trace=scene();trace[0]=trace[1]=trace[2]=5;
  var line=GridPuzzlePolicy.choosePartial(new GridPuzzle(6,6,trace),
    GridPuzzlePolicy.Mode.TRACE_LINE,3,0,0,new BitSet());
  check(line!=null&&line.expectedCleared>=3,"straight trace remains available");
  BitSet protectedLine=new BitSet();protectedLine.set(1);
  var split=GridPuzzlePolicy.choosePartial(new GridPuzzle(6,6,trace),
    GridPuzzlePolicy.Mode.TRACE_LINE,3,0,0,protectedLine);
  check(split==null||!Arrays.stream(split.clearedCells()).anyMatch(i->i==1),
    "protected item is not used as a bridge");

  int[] allUnknown=new int[36];Arrays.fill(allUnknown,-1);
  check(GridPuzzlePolicy.choosePartial(new GridPuzzle(6,6,allUnknown),
    GridPuzzlePolicy.Mode.SWAP_MATCH3,2,0,0,new BitSet())==null,
    "low coverage must not authorize input");

  int[] fourColors=new int[36];for(int i=0;i<36;i++)fourColors[i]=i%4;
  for(int i=12;i<=18;i++)fourColors[i]=-1;
  GridPuzzleVision.Observation vision=GridPuzzleVision.detect((x,y)->token(x,y,fourColors),
    0,0,480,480,6,6,0,0,0,0,1000,null);
  check(!vision.usable()&&vision.partialUsable(),"29/36 recognized tokens permit partial observation");
  check(vision.unknownIndices().length==7,"all seven unknowns remain unknown");
  var obstructed=GridPuzzleVision.detect((x,y)->token(x,y,fourColors),
    0,0,480,480,6,6,80,0,160,80,1100,vision.palette());
  check(!obstructed.partialUsable(),"panel overlap cannot authorize partial input");

  GridPuzzleSession session=new GridPuzzleSession();
  check(session.observePartial(6,6,cells,new BitSet(),1000,1000)==GridPuzzleSession.Status.WAIT_STABLE,
    "one partial frame not enough");
  check(session.observePartial(6,6,cells,new BitSet(),1100,1100)==GridPuzzleSession.Status.READY,
    "two stable partial frames authorize candidate consideration");
  session.sent(cells,1200,swap);session.acknowledge(1250);
  check(session.verifiedChanges()==0,"ACK is not a learned success");
  int[] onlyOne=cells.clone();onlyOne[0]=9;
  session.observePartial(6,6,onlyOne,new BitSet(),1500,1500);
  check(session.observePartial(6,6,onlyOne,new BitSet(),1600,1600)==GridPuzzleSession.Status.WAIT_RESPONSE,
    "one known cell change is insufficient for partial verification");
  int[] newKnown=cells.clone();newKnown[12]=8;
  session.observePartial(6,6,newKnown,new BitSet(),1700,1700);
  check(session.observePartial(6,6,newKnown,new BitSet(),1800,1800)==GridPuzzleSession.Status.WAIT_RESPONSE,
    "UNKNOWN becoming known is not game response");
  int[] unrelated=cells.clone();unrelated[30]=91;unrelated[31]=92;
  session.observePartial(6,6,unrelated,new BitSet(),1900,1900);
  check(session.observePartial(6,6,unrelated,new BitSet(),2000,2000)==GridPuzzleSession.Status.WAIT_RESPONSE,
    "unrelated two-cell animation must not verify the attempted move");
  int[] after=cells.clone();after[0]=9;after[1]=8;
  session.observePartial(6,6,after,new BitSet(),2100,2100);
  check(session.observePartial(6,6,after,new BitSet(),2200,2200)==GridPuzzleSession.Status.CHANGED,
    "two fresh stable changed frames including move footprint verify response");
  check(session.verifiedChanges()==1&&!session.pending(),"one change counted and no pending retry");
  check(session.observePartial(6,6,after,new BitSet(),2300,2300)==GridPuzzleSession.Status.READY
    &&session.verifiedChanges()==1,"static partial field never earns a second result");

  int[] complete=scene();for(int i=12;i<=18;i++)complete[i]=100+i;
  GridPuzzleSession full=new GridPuzzleSession();
  var fullMove=GridPuzzlePolicy.choose(new GridPuzzle(6,6,complete),GridPuzzlePolicy.Mode.SWAP_MATCH3,2,0,0,new BitSet());
  check(fullMove!=null,"complete board has a move");
  full.observe(6,6,complete,1000,1000);full.observe(6,6,complete,1100,1100);
  full.sent(complete,1200,fullMove);full.acknowledge(1250);
  int[] unrelatedFull=complete.clone();unrelatedFull[30]=91;unrelatedFull[31]=92;
  full.observe(6,6,unrelatedFull,1500,1500);
  check(full.observe(6,6,unrelatedFull,1600,1600)==GridPuzzleSession.Status.WAIT_RESPONSE,"complete board unrelated changes do not verify action");
  int[] relevantFull=complete.clone();int[] footprint=fullMove.clearedCells();relevantFull[footprint[0]]=90;relevantFull[footprint[1]]=91;
  full.observe(6,6,relevantFull,1700,1700);
  check(full.observe(6,6,relevantFull,1800,1800)==GridPuzzleSession.Status.CHANGED,"stable local changes verify response");

  GridPuzzleSession noResponse=new GridPuzzleSession();
  noResponse.observePartial(6,6,cells,new BitSet(),1000,1000);
  noResponse.observePartial(6,6,cells,new BitSet(),1100,1100);
  noResponse.sent(cells,1200);noResponse.acknowledge(1250);
  check(noResponse.observePartial(6,6,cells,new BitSet(),3700,3700)==GridPuzzleSession.Status.PAUSED,
    "no confirmed response must still fail closed at timeout");
  System.out.println("Grid partial vision/solver/session: "+checks+" assertions PASS (synthetic only)");
 }
}
