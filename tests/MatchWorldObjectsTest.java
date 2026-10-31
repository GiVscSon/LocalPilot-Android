package org.localpilot.android;
import java.util.BitSet;
/** Synthetic 7x7 diamond: 12 blue holes, 19 wood crates, one bonus. */
public final class MatchWorldObjectsTest {
 private static int checks;
 private static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
 private static boolean hole(int row,int col){return (row==0||row==6)&&(col<=1||col>=5)||(row==1||row==5)&&(col==0||col==6);}
 private static boolean crate(int row,int col){
  return row==0&&col>=2&&col<=4||row==1&&(col==1||col==5)
   ||row==2&&(col==1||col==5)||row==3&&(col==0||col==1||col==5||col==6)
   ||row==4&&(col==1||col==5)||row==5&&(col==1||col==4||col==5)
   ||row==6&&col>=2&&col<=4;
 }
 private static int tile(int x,int y){
  int row=y/60,col=x/60;float nx=(x%60)/60f,ny=(y%60)/60f;
  if(hole(row,col))return 0xc6d7eb;
  if(crate(row,col)){
   if(Math.abs(nx-ny)<.12||Math.abs(nx+ny-1)<.12||nx<.08||ny<.08||nx>.92||ny>.92)return 0x77502a;
   return 0xc28d51;
  }
  if(row==4&&col==3){
   if(nx>.32f&&nx<.68f&&ny>.32f&&ny<.68f)return 0x252323;
   return 0xd45f24;
  }
  if(nx<.12f||nx>.88f||ny<.12f||ny>.88f)return 0xc6d7eb;
  int type=(row*3+col*2)%4;
  if(row==2&&col==2||row==2&&col==4||row==3&&col==3)type=0;
  if(row==2&&col==3)type=1;
  int[] palette={0xe72832,0xfbc72b,0x4aa91f,0x266ac6};int c=palette[type];
  int shade=(nx>.56f&&ny>.56f)?-12:0;
  return (Math.max(0,(c>>16&255)+shade)<<16)|(Math.max(0,(c>>8&255)+shade)<<8)|Math.max(0,(c&255)+shade);
 }
 public static void main(String[] args){
  int holes=0,crates=0;for(int row=0;row<7;row++)for(int col=0;col<7;col++){
   if(hole(row,col))holes++;if(crate(row,col))crates++;
  }
  check(holes==12&&crates==19,"exact geometric fixture count");
  var image=GridPuzzleVision.detect(MatchWorldObjectsTest::tile,0,0,420,420,7,7,0,0,0,0,1000,null,false,true);
  int rejectedHole=0,rejectedCrate=0;BitSet special=image.specialCellsMask();int[] symbols=image.cells();
  for(int row=0;row<7;row++)for(int col=0;col<7;col++){
   int i=row*7+col;
   if(hole(row,col)){check(image.reason(row,col)==GridPuzzleVision.CellReason.EMPTY_HOLE,"hole is background not a move r"+row+"c"+col);
    check(symbols[i]==0&&special.get(i),"hole must be immovable");rejectedHole++;}
   if(crate(row,col)){check(image.reason(row,col)==GridPuzzleVision.CellReason.BLOCKER_CRATE,"wooden texture blocks r"+row+"c"+col+" got "+image.reason(row,col));
    check(symbols[i]==0&&special.get(i),"crate cannot be swapped");rejectedCrate++;}
  }
  check(rejectedHole==12&&rejectedCrate==19,"special mask covers entire diamond perimeter");
  check(!image.usable()&&image.partialUsable()||image.usable(),"recognized scene remains eligible for stable two-frame proof");
  var board=new GridPuzzle(7,7,symbols);
  var action=GridPuzzlePolicy.chooseAdaptive(board,GridPuzzlePolicy.Mode.SWAP_MATCH3,2,0,0,special,true,null);
  check(action!=null,"safe normal swap remains inside diamond");
  int from=action.fromRow*7+action.fromCol,to=action.toRow*7+action.toCol;
  check(symbols[from]>0&&symbols[to]>0&&!special.get(from)&&!special.get(to),"only ordinary tile endpoints");
  for(int index:action.clearedCells())check(symbols[index]>0&&!special.get(index),"no predicted clear on a hole or crate");
  // Profile-specific: identical blue corners do NOT become special in generic 7x7.
  var generic=GridPuzzleVision.detect(MatchWorldObjectsTest::tile,0,0,420,420,7,7,0,0,0,0,1100,null,false);
  check(generic.reason(0,0)!=GridPuzzleVision.CellReason.EMPTY_HOLE&&generic.reason(0,2)!=GridPuzzleVision.CellReason.BLOCKER_CRATE,
    "object vocabulary is opt-in, never applied to another grid game");
  System.out.println("Match World diamond: "+checks+" assertions PASS (synthetic)");
 }
}