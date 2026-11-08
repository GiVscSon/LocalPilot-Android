package org.localpilot.gamelab;

import java.util.Arrays;
import java.util.Random;

/**
 * Minimal exact 2048 tile rules used ONLY for the native simulated test board.
 * Does not claim visual recognition or reward from the actual Market game.
 */
public final class GameLab2048Rules {
 private GameLab2048Rules(){}
 public record Move(int[] tiles,int mergeScore,boolean changed) {
  public Move { tiles=tiles.clone(); }
  public int[] tiles(){return tiles.clone();}
 }
 /** Directions: 0=LEFT,1=RIGHT,2=UP,3=DOWN. */
 public static Move move(int[] board,int direction){
  if(board==null||board.length!=16||direction<0||direction>3)
   throw new IllegalArgumentException("2048 board or direction");
  int[] out=board.clone();int gained=0;
  for(int line=0;line<4;line++){
   int[] packed=new int[4];int length=0;
   for(int i=0;i<4;i++){
    int row=direction<2?line:(direction==2?i:3-i);
    int col=direction<2?(direction==0?i:3-i):line;
    int value=board[row*4+col];
    if(value<0||(value!=0&&Integer.bitCount(value)!=1))
     throw new IllegalArgumentException("2048 tile value");
    if(value!=0)packed[length++]=value;
   }
   int[] merged=new int[4];int n=0;
   for(int i=0;i<length;i++){
    int value=packed[i];
    if(i+1<length&&packed[i+1]==value){
     if(value>32768)throw new IllegalArgumentException("2048 tile overflow");
     value*=2;gained+=value;i++;
    }
    merged[n++]=value;
   }
   for(int i=0;i<4;i++){
    int row=direction<2?line:(direction==2?i:3-i);
    int col=direction<2?(direction==0?i:3-i):line;
    out[row*4+col]=merged[i];
   }
  }
  return new Move(out,gained,!Arrays.equals(board,out));
 }
 public static boolean spawn(int[] board,Random random){
  if(board==null||board.length!=16||random==null)
   throw new IllegalArgumentException("2048 spawn args");
  int free=0;for(int value:board)if(value==0)free++;
  if(free==0)return false;
  int slot=random.nextInt(free);
  for(int i=0;i<16;i++)if(board[i]==0&&slot--==0){
   board[i]=random.nextInt(10)==0?4:2;return true;
  }
  throw new AssertionError("2048 free slot search");
 }
 public static boolean canMove(int[] board){
  for(int d=0;d<4;d++)if(move(board,d).changed())return true;
  return false;
 }
 public static int empty(int[] board){
  int free=0;for(int t:board)if(t==0)free++;return free;
 }
 /** Deterministic, non-learning baseline solely for the 2048 UI demo. */
 public static int baselineDirection(int[] board){
  int best=-1;double bestValue=Double.NEGATIVE_INFINITY;
  for(int d=0;d<4;d++){
   Move trial=move(board,d);
   if(!trial.changed())continue;
   int[] after=trial.tiles();
   // Prefer legal merges with many remaining spaces; no false win credit.
   double value=trial.mergeScore()+GameLab2048Rules.empty(after)*7+
     (after[0]==max(after)||after[3]==max(after)||after[12]==max(after)||after[15]==max(after)?8:0);
   if(value>bestValue){bestValue=value;best=d;}
  }
  return best;
 }
 private static int max(int[] board){
  int m=0;for(int v:board)m=Math.max(m,v);return m;
 }
}
