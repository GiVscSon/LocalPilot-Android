package org.localpilot.android;

import java.util.Arrays;

/** Separate immediate-move rules for swaps and orthogonally connected tap groups.
 * No unobserved refill, cascade, booster or animation state is inferred. */
public final class GridPuzzlePolicy {
 public enum Mode { SWAP_MATCH3,TAP_GROUP }
 public static final class Decision {
  public final Mode mode;
  public final int fromRow,fromCol,toRow,toCol,expectedCleared,targetCleared;
  public final double score;
  private final int[] clearedCells,clearedTypes;
  private Decision(Mode mode,int from,int to,int cols,int[] cells,int[] types,int targetCleared,double score){
   this.mode=mode;fromRow=from/cols;fromCol=from%cols;toRow=to/cols;toCol=to%cols;
   this.expectedCleared=cells.length;this.targetCleared=targetCleared;this.score=score;
   clearedCells=cells.clone();clearedTypes=types.clone();
  }
  /** Row-major cell indices, sorted; these are immediate predicted clears only. */
  public int[] clearedCells(){return clearedCells.clone();}
  /** Unique positive type IDs among the predicted clears, sorted. */
  public int[] clearedTypes(){return clearedTypes.clone();}
 }
 private GridPuzzlePolicy(){}

 public static Decision choose(GridPuzzle board,Mode mode){return choose(board,mode,2,0,0);}

 /** targetType=0 or targetRemaining=0 disables goal weighting; minGroup applies to TAP_GROUP. */
 public static Decision choose(GridPuzzle board,Mode mode,int minGroup,int targetType,int targetRemaining){
  if(board==null||mode==null||minGroup<2||minGroup>100||targetType<0||targetRemaining<0)throw new IllegalArgumentException("puzzle policy context");
  // A partially identified grid may hide an obstacle or special item: abstain.
  if(!board.known())return null;
  int[] cells=board.cells();Decision best=null;
  if(mode==Mode.TAP_GROUP){
   boolean[] visited=new boolean[cells.length];int[] queue=new int[cells.length];
   for(int start=0;start<cells.length;start++){
    if(cells[start]<=0||visited[start])continue;
    int head=0,tail=1;queue[0]=start;visited[start]=true;
    while(head<tail){
     int index=queue[head++],r=index/board.cols,c=index%board.cols;
     for(int direction=0;direction<4;direction++){
      int nr=r+(direction==0?-1:direction==1?1:0),nc=c+(direction==2?-1:direction==3?1:0);
      if(nr<0||nr>=board.rows||nc<0||nc>=board.cols)continue;
      int next=nr*board.cols+nc;
      if(!visited[next]&&cells[next]==cells[start]){visited[next]=true;queue[tail++]=next;}
     }
    }
    if(tail<minGroup)continue;
    int[] group=Arrays.copyOf(queue,tail);Arrays.sort(group);
    Decision candidate=decision(mode,start,start,board.cols,cells,group,0,targetType,targetRemaining);
    if(better(candidate,best))best=candidate;
   }
  }else{
   for(int from=0;from<cells.length;from++){
    if(cells[from]<=0)continue;
    int r=from/board.cols,c=from%board.cols;
    for(int direction=0;direction<2;direction++){
     int to=direction==0?(c+1<board.cols?from+1:-1):(r+1<board.rows?from+board.cols:-1);
     if(to<0||cells[to]<=0||cells[to]==cells[from])continue;
     swap(cells,from,to);
     boolean[] matched=new boolean[cells.length];
     int bonus=markLines(cells,board.rows,board.cols,from,matched)+markLines(cells,board.rows,board.cols,to,matched);
     int count=0;for(boolean value:matched)if(value)count++;
     Decision candidate=null;
     if(count>=3){
      int[] cleared=new int[count];int cursor=0;for(int i=0;i<matched.length;i++)if(matched[i])cleared[cursor++]=i;
      candidate=decision(mode,from,to,board.cols,cells,cleared,bonus,targetType,targetRemaining);
     }
     swap(cells,from,to);
     if(candidate!=null&&better(candidate,best))best=candidate;
    }
   }
  }
  return best;
 }
 private static boolean better(Decision candidate,Decision previous){return previous==null||candidate.score>previous.score;}
 private static void swap(int[] cells,int a,int b){int value=cells[a];cells[a]=cells[b];cells[b]=value;}
 private static Decision decision(Mode mode,int from,int to,int cols,int[] cells,int[] cleared,int bonus,int targetType,int targetRemaining){
  int target=0;for(int index:cleared)if(targetType>0&&cells[index]==targetType)target++;
  if(targetRemaining==0)target=0;else target=Math.min(target,targetRemaining);
  int[] types=Arrays.stream(cleared).map(index->cells[index]).distinct().sorted().toArray();
  // Goal progress dominates raw clears. Bonuses value long/crossed matches,
  // without pretending any particular game's special item will be generated.
  double score=cleared.length*10.0+target*100.0+bonus;
  return new Decision(mode,from,to,cols,cleared,types,target,score);
 }
 /** Only lines THROUGH a changed cell may authorize a swap. Unrelated existing
  * matches elsewhere on the board cannot turn a useless swap into a legal one. */
 private static int markLines(int[] cells,int rows,int cols,int index,boolean[] matched){
  int row=index/cols,col=index%cols,type=cells[index],bonus=0,axes=0;
  for(int axis=0;axis<2;axis++){
   int dr=axis==0?0:1,dc=axis==0?1:0,lo=0,hi=0;
   while(row-(lo+1)*dr>=0&&col-(lo+1)*dc>=0&&cells[(row-(lo+1)*dr)*cols+col-(lo+1)*dc]==type)lo++;
   while(row+(hi+1)*dr<rows&&col+(hi+1)*dc<cols&&cells[(row+(hi+1)*dr)*cols+col+(hi+1)*dc]==type)hi++;
   int length=lo+hi+1;
   if(length>=3){
    axes++;for(int offset=-lo;offset<=hi;offset++)matched[(row+offset*dr)*cols+col+offset*dc]=true;
    if(length==4)bonus+=12;else if(length>=5)bonus+=30+(length-5)*4;
   }
  }
  if(axes==2)bonus+=25;
  return bonus;
 }
}
