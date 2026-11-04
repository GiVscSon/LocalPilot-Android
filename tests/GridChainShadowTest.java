package org.localpilot.android;
import java.util.BitSet;
public final class GridChainShadowTest {
 private static void check(boolean ok){if(!ok)throw new AssertionError();}
 public static void main(String[] args){
  GridPuzzle b=new GridPuzzle(3,3,new int[]{1,2,3,4,1,1,-1,5,1});
  int[] path=GridChainShadow.findFour(b,new BitSet());check(path!=null&&path.length==4);
  for(int i=0;i<path.length;i++){check(b.cells()[path[i]]==1);for(int j=0;j<i;j++)check(path[i]!=path[j]);if(i>0)check(Math.abs(path[i]/3-path[i-1]/3)<=1&&Math.abs(path[i]%3-path[i-1]%3)<=1);}
  BitSet special=new BitSet();special.set(4);check(GridChainShadow.findFour(b,special)==null);
  check(GridChainShadow.findFour(new GridPuzzle(3,3,new int[]{1,-1,1,0,0,0,1,0,1}),new BitSet())==null);
  System.out.println("Grid chain shadow: PASS");
 }
}
