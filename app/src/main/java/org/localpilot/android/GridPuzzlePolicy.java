package org.localpilot.android;

import java.util.Arrays;

/** Immediate-move rules for swaps, orthogonal tap groups and straight chains.
 * Marked special cells are excluded until their semantic role is known.
 * No unobserved refill, cascade, booster or animation state is inferred. */
public final class GridPuzzlePolicy {
 public enum Mode { SWAP_MATCH3,TAP_GROUP,TRACE_LINE }
 public static final class Decision {
  public final Mode mode;
  public final int fromRow,fromCol,toRow,toCol,expectedCleared,targetCleared;
  public final double score;
  public final int specialInvolved;
  private final int[] clearedCells,clearedTypes;
  private Decision(Mode mode,int from,int to,int cols,int[] cells,int[] types,int targetCleared,double score,int specialInvolved){
   this.specialInvolved=specialInvolved;
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

 /** targetType=0 or targetRemaining=0 disables goal weighting; minGroup applies to groups and chains. */
 public static Decision choose(GridPuzzle board,Mode mode,int minGroup,int targetType,int targetRemaining){return choose(board,mode,minGroup,targetType,targetRemaining,new java.util.BitSet());}
 public static Decision choose(GridPuzzle board,Mode mode,int minGroup,int targetType,int targetRemaining,java.util.BitSet special){
  return chooseInternal(board,mode,minGroup,targetType,targetRemaining,special,false);
 }
 /**
  * Opt-in planner for incomplete but independently authenticated game boards.
  * Unknown (-1) cells are obstacles, never guessed tokens. Only observed
  * ordinary actions and immediate clears are considered. Caller must verify
  * the window, ROI, scene, stable frames and the post-ACK game response.
  */
 public static Decision choosePartial(GridPuzzle board,Mode mode,int minGroup,int targetType,int targetRemaining,java.util.BitSet special){
  return chooseInternal(board,mode,minGroup,targetType,targetRemaining,special,true);
 }
 private static Decision chooseInternal(GridPuzzle board,Mode mode,int minGroup,int targetType,int targetRemaining,java.util.BitSet special,boolean partialAllowed){
  if(board==null||mode==null||minGroup<2||minGroup>100||targetType<0||targetRemaining<0)throw new IllegalArgumentException("puzzle policy context");
  if(special==null||special.length()>board.rows*board.cols)throw new IllegalArgumentException("special mask outside board");
  special=(java.util.BitSet)special.clone();
  if(!partialAllowed&&!board.known())return null;
  // Second gate, independent of scene classification by GridPuzzleVision.
  if(partialAllowed){
   int known=0;for(int cell:board.cells())if(cell>=0)known++;
   if(known<Math.max(6,(board.rows*board.cols*2+2)/3))return null;
  }
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
    boolean containsSpecial=false;for(int i=0;i<tail;i++)if(special.get(queue[i])){containsSpecial=true;break;}
    if(containsSpecial)continue;
    int[] group=Arrays.copyOf(queue,tail);Arrays.sort(group);
    Decision candidate=decision(mode,start,start,board.cols,cells,group,0,targetType,targetRemaining,special);
    if(better(candidate,best))best=candidate;
   }
  }else if(mode==Mode.TRACE_LINE){
   // A single straight swipe can safely select horizontal, vertical or
   // diagonal chains. Bent chains need a polyline gesture and are not guessed.
   int[][] directions={{0,1},{1,0},{1,1},{1,-1}};
   for(int row=0;row<board.rows;row++)for(int col=0;col<board.cols;col++){
    int start=row*board.cols+col,type=cells[start];if(type<=0)continue;
    for(int[] direction:directions){
     int dr=direction[0],dc=direction[1],pr=row-dr,pc=col-dc;
     if(pr>=0&&pr<board.rows&&pc>=0&&pc<board.cols&&cells[pr*board.cols+pc]==type)continue;
     int length=0,nr=row,nc=col;
     while(nr>=0&&nr<board.rows&&nc>=0&&nc<board.cols&&cells[nr*board.cols+nc]==type){length++;nr+=dr;nc+=dc;}
     if(length<minGroup)continue;
     int[] chain=new int[length];nr=row;nc=col;
     boolean containsSpecial=false;
     for(int i=0;i<length;i++){chain[i]=nr*board.cols+nc;if(special.get(chain[i]))containsSpecial=true;nr+=dr;nc+=dc;}
     if(containsSpecial)continue;
     Decision candidate=decision(mode,chain[0],chain[length-1],board.cols,cells,chain,0,targetType,targetRemaining,special);
     if(better(candidate,best))best=candidate;
    }
   }
  }else{
   for(int from=0;from<cells.length;from++){
    if(cells[from]<=0)continue;
    int r=from/board.cols,c=from%board.cols;
    for(int direction=0;direction<2;direction++){
     int to=direction==0?(c+1<board.cols?from+1:-1):(r+1<board.rows?from+board.cols:-1);
     if(to<0||cells[to]<=0||cells[to]==cells[from]||special.get(from)||special.get(to))continue;
     swap(cells,from,to);
     boolean[] matched=new boolean[cells.length];
     int bonus=markLines(cells,board.rows,board.cols,from,matched)+markLines(cells,board.rows,board.cols,to,matched);
     int count=0;for(boolean value:matched)if(value)count++;
     Decision candidate=null;
     if(count>=3){
      int[] cleared=new int[count];int cursor=0;for(int i=0;i<matched.length;i++)if(matched[i])cleared[cursor++]=i;
      boolean containsSpecial=false;for(int index:cleared)if(special.get(index)){containsSpecial=true;break;}
      if(!containsSpecial)candidate=decision(mode,from,to,board.cols,cells,cleared,bonus,targetType,targetRemaining,special);
     }
     swap(cells,from,to);
     if(candidate!=null&&better(candidate,best))best=candidate;
    }
   }
  }
  return best;
 }
 private static boolean better(Decision candidate,Decision previous){return previous==null||candidate.score>previous.score||candidate.score==previous.score&&candidate.specialInvolved>previous.specialInvolved;}
 private static void swap(int[] cells,int a,int b){int value=cells[a];cells[a]=cells[b];cells[b]=value;}
 private static Decision decision(Mode mode,int from,int to,int cols,int[] cells,int[] cleared,int bonus,int targetType,int targetRemaining,java.util.BitSet special){
  int target=0;for(int index:cleared)if(targetType>0&&cells[index]==targetType)target++;
  if(targetRemaining==0)target=0;else target=Math.min(target,targetRemaining);
  int[] types=Arrays.stream(cleared).map(index->cells[index]).distinct().sorted().toArray();
  // Goal progress dominates raw clears. Bonuses value long/crossed matches,
  // without pretending any particular game's special item will be generated.
  double score=cleared.length*10.0+target*100.0+bonus;
  java.util.BitSet involved=new java.util.BitSet();involved.set(from);involved.set(to);for(int i:cleared)involved.set(i);involved.and(special);
  return new Decision(mode,from,to,cols,cleared,types,target,score,involved.cardinality());
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
