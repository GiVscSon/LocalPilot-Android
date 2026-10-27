package org.localpilot.android;

import java.util.BitSet;

/** Read-only four-cell proposals; no gesture or game-rule validation is implied. */
public final class GridChainShadow {
 private GridChainShadow(){}
 public static int[] findFour(GridPuzzle board,BitSet special){
  if(board==null||special==null||special.length()>board.rows*board.cols)throw new IllegalArgumentException("invalid grid context");
  int[] cells=board.cells(),path=new int[4];boolean[] used=new boolean[cells.length];
  for(int i=0;i<cells.length;i++)if(cells[i]>0&&!special.get(i)){
   path[0]=i;used[i]=true;
   if(search(board,cells,special,used,path,1))return path.clone();
   used[i]=false;
  }
  return null;
 }
 private static boolean search(GridPuzzle board,int[] cells,BitSet special,boolean[] used,int[] path,int depth){
  if(depth==path.length)return true;
  int previous=path[depth-1],row=previous/board.cols,col=previous%board.cols,type=cells[path[0]];
  for(int dr=-1;dr<=1;dr++)for(int dc=-1;dc<=1;dc++){
   if(dr==0&&dc==0)continue;int r=row+dr,c=col+dc;
   if(r<0||r>=board.rows||c<0||c>=board.cols)continue;
   int next=r*board.cols+c;if(used[next]||special.get(next)||cells[next]!=type)continue;
   path[depth]=next;used[next]=true;if(search(board,cells,special,used,path,depth+1))return true;used[next]=false;
  }
  return false;
 }
}
