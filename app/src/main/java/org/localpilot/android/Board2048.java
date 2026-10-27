package org.localpilot.android;
import java.util.*;
/** Standard 4x4 rules. No model, training or hidden game state required by policy. */
public final class Board2048 {
 public static final int LEFT=0,UP=1,RIGHT=2,DOWN=3;
 public static final int[] PALETTE={0xcdc1b4,0xeee4da,0xede0c8,0xf2b179,0xf59563,0xf67c5f,0xf65e3b,0xedcf72,0xedcc61,0xedc850,0xedc53f,0xedc22e};
 public final int[] cells;
 public Board2048(int[] c){if(c==null||c.length!=16)throw new IllegalArgumentException("4x4 required");cells=c.clone();for(int n:cells)if(n<0||n>65536||(n!=0&&(n<2||(n&(n-1))!=0)))throw new IllegalArgumentException("Unsupported tile");}
 public static final class Move {public final Board2048 board;public final int reward;Move(Board2048 b,int r){board=b;reward=r;}}
 private static int index(int d,int line,int pos){switch(d){case LEFT:return line*4+pos;case RIGHT:return line*4+3-pos;case UP:return pos*4+line;case DOWN:return (3-pos)*4+line;default:throw new IllegalArgumentException("direction");}}
 public Move move(int d){int[] out=new int[16];int reward=0;for(int line=0;line<4;line++){int[] a=new int[4];int size=0;for(int p=0;p<4;p++){int n=cells[index(d,line,p)];if(n!=0)a[size++]=n;}int dest=0;for(int p=0;p<size;p++){int n=a[p];if(p+1<size&&a[p+1]==n){n*=2;p++;reward+=n;}out[index(d,line,dest++)]=n;}}return new Move(new Board2048(out),reward);}
 public boolean same(Board2048 b){return b!=null&&Arrays.equals(cells,b.cells);}
 public enum Verification { VERIFIED, NO_LEGAL_MOVE, NO_BOARD, UNCHANGED, UNEXPECTED_TILES, MISSING_SPAWN, MULTIPLE_SPAWNS }
 public Verification verification(Board2048 after,int d){
  Board2048 expected=move(d).board;if(same(expected))return Verification.NO_LEGAL_MOVE;
  if(after==null)return Verification.NO_BOARD;
  int spawns=0;for(int i=0;i<16;i++){if(expected.cells[i]==after.cells[i])continue;if(expected.cells[i]!=0||(after.cells[i]!=2&&after.cells[i]!=4))return same(after)?Verification.UNCHANGED:Verification.UNEXPECTED_TILES;spawns++;}
  return spawns==1?Verification.VERIFIED:same(after)?Verification.UNCHANGED:spawns==0?Verification.MISSING_SPAWN:Verification.MULTIPLE_SPAWNS;
 }
 public boolean verifies(Board2048 after,int d){return verification(after,d)==Verification.VERIFIED;}
 public int max(){return Arrays.stream(cells).max().orElse(0);}
 private double value(){double s=0;int empty=0;for(int i=0;i<16;i++){int n=cells[i];if(n==0){empty++;continue;}double a=Math.log(n)/Math.log(2);if(i%4<3&&cells[i+1]>0)s-=Math.abs(a-Math.log(cells[i+1])/Math.log(2));if(i<12&&cells[i+4]>0)s-=Math.abs(a-Math.log(cells[i+4])/Math.log(2));}int m=max();return s+empty*12+((cells[0]==m||cells[3]==m||cells[12]==m||cells[15]==m)?8:0);}
 public int choose(){return chooseWithBonus(null);}
 /**
  * A learned preference cannot authorize an illegal action. The base board
  * heuristic always dominates: every optional bonus is bounded to +/-0.4.
  */
 public int chooseWithBonus(double[] directionBonus){
  if(directionBonus!=null){
   if(directionBonus.length!=4)throw new IllegalArgumentException("four 2048 directions required");
   for(double b:directionBonus)if(!Double.isFinite(b)||Math.abs(b)>.400001)throw new IllegalArgumentException("unbounded learned bias");
  }
  if(max()>=2048)return -1;
  int best=-1;double score=-Double.MAX_VALUE;
  for(int d=0;d<4;d++){
   Move m=move(d);if(same(m.board))continue;
   double v=m.board.value()+Math.log(1+m.reward)+(directionBonus==null?0:directionBonus[d]);
   if(v>score){score=v;best=d;}
  }
  return best;
 }
 public static int colorTile(int rgb){int nearest=-1,best=Integer.MAX_VALUE,second=Integer.MAX_VALUE;for(int i=0;i<PALETTE.length;i++){int p=PALETTE[i];int e=Math.max(Math.abs(((rgb>>16)&255)-((p>>16)&255)),Math.max(Math.abs(((rgb>>8)&255)-((p>>8)&255)),Math.abs((rgb&255)-(p&255))));if(e<best){second=best;best=e;nearest=i;}else second=Math.min(second,e);}if(best>8||second-best<5)return -1;return nearest==0?0:1<<nearest;}
}
