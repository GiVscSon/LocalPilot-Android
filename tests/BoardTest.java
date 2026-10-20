package org.localpilot.android;
import java.util.*;import java.nio.file.*;
public class BoardTest {
 static int n;static void yes(boolean b){n++;if(!b)throw new AssertionError("board check "+n);}
 static Board2048 b(int...a){return new Board2048(a);}
 public static void main(String[]args)throws Exception{
  Board2048 x=b(2,2,2,2, 4,4,8,0, 0,0,0,0, 0,0,0,0);Board2048.Move m=x.move(0);yes(Arrays.equals(m.board.cells,new int[]{4,4,0,0,8,8,0,0,0,0,0,0,0,0,0,0}));yes(m.reward==16);
  int[] after=m.board.cells.clone();after[15]=2;yes(x.verifies(new Board2048(after),0));yes(Verified2048.mergeReward(x,0,new Board2048(after))==16);yes(!x.verifies(m.board,0));after[14]=2;yes(!x.verifies(new Board2048(after),0));yes(!Verified2048.ppoEligible());yes(Verified2048.features(x)[0]==1);yes(Verified2048.mask(x)[x.choose()]);
  for(int i=0;i<Board2048.PALETTE.length;i++)yes(Board2048.colorTile(Board2048.PALETTE[i])==(i==0?0:1<<i));yes(Board2048.colorTile(0x00ff00)==-1);
  PixelBoardReader.Pixels pixels=(px,py)->{int c=px/100,r=py/100,value=x.cells[r*4+c];return Board2048.PALETTE[value==0?0:Integer.numberOfTrailingZeros(value)];};yes(PixelBoardReader.read(pixels,400,400,0,0,400,400).same(x));
  try{PixelBoardReader.read((px,py)->0x123456,400,400,0,0,400,400);throw new AssertionError();}catch(IllegalArgumentException expected){yes(true);}
  yes(CaptureFreshness.valid(100,100,100,-1));yes(CaptureFreshness.valid(100,99,800,99));yes(!CaptureFreshness.valid(100,99,801,99));yes(!CaptureFreshness.valid(100,101,102,99));yes(!CaptureFreshness.valid(100,99,99,0));yes(!CaptureFreshness.valid(100,99,101,100));yes(!CaptureFreshness.valid(0,0,1,-1));
  Random random=new Random(408);for(int trial=0;trial<500;trial++){int[] a=new int[16];for(int i=0;i<16;i++)a[i]=random.nextInt(4)==0?0:1<<(1+random.nextInt(6));Board2048 board=new Board2048(a);for(int d=0;d<4;d++){Board2048.Move move=board.move(d);yes(Arrays.stream(a).sum()==Arrays.stream(move.board.cells).sum());yes(move.board.move(d).reward>=0);}int d=board.choose();yes(d<0||Verified2048.mask(board)[d]);}
  // Cross-language parity vectors generated with the original Python LocalPilot game2048.
  if(args.length>0)for(String line:Files.readAllLines(Path.of(args[0]))){String[] parts=line.split(";");int[] a=Arrays.stream(parts[0].split(",")).mapToInt(Integer::parseInt).toArray();Board2048 board=new Board2048(a);for(int d=0;d<4;d++){String[] want=parts[d+1].split(":");Board2048.Move move=board.move(d);yes(Arrays.toString(move.board.cells).equals(Arrays.toString(Arrays.stream(want[0].split(",")).mapToInt(Integer::parseInt).toArray())));yes(move.reward==Integer.parseInt(want[1]));}}
  System.out.println("PASS "+n+" board/reader/parity assertions");
 }
}
