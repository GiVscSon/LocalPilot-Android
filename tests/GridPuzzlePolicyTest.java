package org.localpilot.android;

import java.util.Arrays;
import java.util.Random;

public final class GridPuzzlePolicyTest {
 private static int checks;
 private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
 private static void invalid(Runnable action,String message){try{action.run();throw new AssertionError(message);}catch(IllegalArgumentException expected){checks++;}}
 private static GridPuzzle board(int...cells){return new GridPuzzle(3,3,cells);}
 private static GridPuzzlePolicy.Decision swap(GridPuzzle board){return GridPuzzlePolicy.choose(board,GridPuzzlePolicy.Mode.SWAP_MATCH3);}
 private static GridPuzzlePolicy.Decision tap(GridPuzzle board){return GridPuzzlePolicy.choose(board,GridPuzzlePolicy.Mode.TAP_GROUP);}
 private static boolean hasLineThrough(GridPuzzle board,int row,int col){
  int type=board.at(row,col);if(type<=0)return false;
  for(int axis=0;axis<2;axis++){
   int count=1,dr=axis==0?0:1,dc=axis==0?1:0;
   for(int sign:new int[]{-1,1})for(int step=1;;step++){
    int r=row+dr*step*sign,c=col+dc*step*sign;
    if(r<0||r>=board.rows||c<0||c>=board.cols||board.at(r,c)!=type)break;count++;
   }
   if(count>=3)return true;
  }
  return false;
 }
 public static void main(String[]args){
  int[] original={1,2,1,2,1,3,3,2,3};GridPuzzle b=new GridPuzzle(3,3,original);original[0]=99;
  check(b.at(0,0)==1,"constructor clones input");int[] exported=b.cells();exported[0]=98;check(b.at(0,0)==1,"accessor clones data");
  check(b.equals(new GridPuzzle(3,3,b.cells()))&&b.hashCode()==new GridPuzzle(3,3,b.cells()).hashCode(),"immutable board value semantics");
  invalid(()->new GridPuzzle(2,3,new int[6]),"too few rows accepted");invalid(()->new GridPuzzle(3,11,new int[33]),"too many columns accepted");invalid(()->new GridPuzzle(3,3,new int[8]),"wrong cell count");invalid(()->board(-2,0,0,0,0,0,0,0,0),"invalid negative cell");
  var move=swap(b);check(move!=null&&move.expectedCleared==3,"find three-match swap");
  check(move.fromRow==0&&move.fromCol==1&&move.toRow==1&&move.toCol==1,"deterministic swap coordinates");
  check(Arrays.equals(move.clearedCells(),new int[]{0,1,2})&&Arrays.equals(move.clearedTypes(),new int[]{1}),"immediate cells and types");
  int[] mutated=move.clearedCells();mutated[0]=99;check(move.clearedCells()[0]==0,"decision arrays immutable");check(b.at(0,1)==2,"solver cannot mutate observed board");
  check(swap(board(9,9,9,1,2,3,4,5,6))==null,"unrelated preexisting match cannot authorize useless swap");
  check(swap(board(1,2,3,4,5,6,7,8,9))==null,"no swap without newly formed match");
  check(swap(board(1,0,1,2,1,3,3,2,3))==null,"empty cells cannot be swapped as pieces");
  check(swap(board(1,2,1,2,-1,3,3,2,3))==null,"unknown field abstains");
  check(tap(board(1,-1,1,1,2,3,4,5,6))==null,"unknown prevents tap too");
  check(tap(board(1,2,3,4,1,5,6,7,1))==null,"diagonal equal pieces are not tap groups");
  var group=tap(board(1,1,2,3,1,2,4,5,2));check(group!=null&&group.expectedCleared==3,"orthogonal group size");
  check(group.fromRow==0&&group.fromCol==0&&group.fromRow==group.toRow&&group.fromCol==group.toCol,"tap has one location");
  check(Arrays.equals(group.clearedCells(),new int[]{0,1,4}),"group must be connected not arbitrary equal pieces");
  java.util.BitSet markedSkull=new java.util.BitSet();markedSkull.set(4);
  check(GridPuzzlePolicy.choose(board(1,1,2,1,1,3,4,5,6),GridPuzzlePolicy.Mode.TAP_GROUP,2,0,0,markedSkull)==null,"group mode must abstain when a candidate would clear a marked skull/special cell");
  java.util.BitSet swapSkull=new java.util.BitSet();swapSkull.set(0);
  check(GridPuzzlePolicy.choose(board(1,2,1,2,1,3,4,5,6),GridPuzzlePolicy.Mode.SWAP_MATCH3,2,0,0,swapSkull)==null,"match-3 must not form a line through a marked skull/special cell");
  java.util.BitSet lineSkull=new java.util.BitSet();lineSkull.set(1);
  check(GridPuzzlePolicy.choose(board(1,1,1,2,3,4,5,6,7),GridPuzzlePolicy.Mode.TRACE_LINE,3,0,0,lineSkull)==null,"trace mode must not swipe through a marked skull/special cell");
  check(tap(board(0,0,0,0,0,0,0,0,0))==null,"empty field cannot authorize input");
  check(GridPuzzlePolicy.choose(board(1,1,2,3,4,5,6,7,8),GridPuzzlePolicy.Mode.TAP_GROUP,3,0,0)==null,"minimum group size enforced");
  var target=GridPuzzlePolicy.choose(board(1,1,1,1,2,2,3,4,5),GridPuzzlePolicy.Mode.TAP_GROUP,2,2,9);
  check(target!=null&&target.fromRow==1&&target.fromCol==1&&target.expectedCleared==2&&target.targetCleared==2,"target group outweighs larger unrelated group");
  var capped=GridPuzzlePolicy.choose(board(1,1,1,1,2,2,3,4,5),GridPuzzlePolicy.Mode.TAP_GROUP,2,1,1);check(capped!=null&&capped.targetCleared==1,"remaining goal caps target credit");
  var disabled=GridPuzzlePolicy.choose(board(1,1,1,1,2,2,3,4,5),GridPuzzlePolicy.Mode.TAP_GROUP,2,2,0);check(disabled!=null&&disabled.fromRow==0&&disabled.targetCleared==0,"completed target does not distort policy");
  // 31 Oct user-supplied 6x6 octopus frame, manually transcribed by icon.
  // Coin=1, fish=2, tomato=3, flower=4, leaf=5, shell=6,
  // ham=7, dynamite=8 (excluded), skull=0 (protected).
  int[] octopusFrame={
    1,2,0,8,7,4,
    5,6,3,2,7,1,
    5,5,1,5,0,5,
    8,3,6,7,7,1,
    7,0,8,3,5,4,
    7,2,2,7,1,7
  };
  java.util.BitSet octopusHazards=new java.util.BitSet(36);
  for(int index:new int[]{2,3,16,18,25,26})octopusHazards.set(index);
  var octopus=new GridPuzzle(6,6,octopusFrame);
  var octopusMove=GridPuzzlePolicy.choose(octopus,GridPuzzlePolicy.Mode.SWAP_MATCH3,
    3,0,0,octopusHazards);
  check(octopusMove!=null&&octopusMove.specialInvolved==0,
    "recorded 6x6 scene has a legal swap without touching skulls or dynamite");
  int[] octopusAfter=octopusFrame.clone();
  int first=2*6+2,second=2*6+3;
  int tmp=octopusAfter[first];octopusAfter[first]=octopusAfter[second];octopusAfter[second]=tmp;
  check(octopusAfter[2*6]==5&&octopusAfter[2*6+1]==5&&octopusAfter[2*6+2]==5,
    "swapping 3rd-row coin and leaf yields three leaves in a row");
  check(octopus.at(2,2)==1&&octopus.at(2,3)==5,
    "read-only policy must not mutate live 6x6 board");
  int[] fourCells=new int[16];for(int i=0;i<fourCells.length;i++)fourCells[i]=100+i;
  fourCells[2]=1;fourCells[4]=1;fourCells[5]=1;fourCells[6]=2;fourCells[7]=1;
  var four=swap(new GridPuzzle(4,4,fourCells));check(four!=null&&four.expectedCleared==4&&four.score>40,"four-line receives tactical bonus without phantom clears");
  int[] fiveCells=new int[25];for(int i=0;i<fiveCells.length;i++)fiveCells[i]=100+i;
  fiveCells[7]=1;for(int i=10;i<15;i++)fiveCells[i]=i==12?2:1;
  var five=swap(new GridPuzzle(5,5,fiveCells));check(five!=null&&five.expectedCleared==5&&five.score>five.expectedCleared*10,"five-line ranked as long match");
  int[] crossCells=new int[25];for(int i=0;i<crossCells.length;i++)crossCells[i]=100+i;
  crossCells[7]=crossCells[17]=crossCells[11]=crossCells[13]=crossCells[14]=1;crossCells[12]=2;
  var cross=swap(new GridPuzzle(5,5,crossCells));check(cross!=null&&cross.expectedCleared==5&&Arrays.equals(cross.clearedCells(),new int[]{7,12,13,14,17}),"crossed lines share one cell and are counted once");
  check(cross.fromRow==2&&cross.fromCol==1&&cross.toRow==2&&cross.toCol==2,"compound clear outranks competing ordinary four-match");
  int[] twoGoals=new int[25];for(int i=0;i<twoGoals.length;i++)twoGoals[i]=100+i;
  // Two independent row matches: move a 2 upward versus a 3 downward.
  twoGoals[0]=twoGoals[2]=twoGoals[6]=2;twoGoals[1]=5;
  twoGoals[20]=twoGoals[22]=twoGoals[16]=3;twoGoals[21]=6;
  var goalSwap=GridPuzzlePolicy.choose(new GridPuzzle(5,5,twoGoals),GridPuzzlePolicy.Mode.SWAP_MATCH3,2,3,2);
  check(goalSwap!=null&&goalSwap.targetCleared==2&&Arrays.binarySearch(goalSwap.clearedTypes(),3)>=0,"swap goal weighting uses types AFTER exchange and remaining cap");
  invalid(()->GridPuzzlePolicy.choose(b,null),"null mode");invalid(()->GridPuzzlePolicy.choose(b,GridPuzzlePolicy.Mode.TAP_GROUP,1,0,0),"invalid group minimum");
  // Independent oracle: enumerate legal swaps and validate selected movement and
  // its exact immediate clear set on reproducible rectangular boards.
  Random rng=new Random(71520);
  for(int scene=0;scene<180;scene++){
   int rows=3+rng.nextInt(8),cols=3+rng.nextInt(8);int[] cells=new int[rows*cols];for(int i=0;i<cells.length;i++)cells[i]=1+rng.nextInt(6);
   GridPuzzle sample=new GridPuzzle(rows,cols,cells);var chosen=swap(sample);boolean legal=false;
   for(int from=0;from<cells.length;from++)for(int direction=0;direction<2;direction++){
    int to=direction==0?(from%cols+1<cols?from+1:-1):(from/cols+1<rows?from+cols:-1);if(to<0||cells[to]==cells[from])continue;
    int[] after=cells.clone();int value=after[from];after[from]=after[to];after[to]=value;
    GridPuzzle result=new GridPuzzle(rows,cols,after);
    legal|=hasLineThrough(result,from/cols,from%cols)||hasLineThrough(result,to/cols,to%cols);
   }
   check((chosen!=null)==legal,"legal move presence scene="+scene);
   if(chosen!=null){
    int from=chosen.fromRow*cols+chosen.fromCol,to=chosen.toRow*cols+chosen.toCol;
    check(Math.abs(chosen.fromRow-chosen.toRow)+Math.abs(chosen.fromCol-chosen.toCol)==1,"only orthogonal swap");
    int[] after=cells.clone();int value=after[from];after[from]=after[to];after[to]=value;GridPuzzle result=new GridPuzzle(rows,cols,after);
    check(hasLineThrough(result,chosen.fromRow,chosen.fromCol)||hasLineThrough(result,chosen.toRow,chosen.toCol),"selected swap creates line");
    for(int index:chosen.clearedCells())check(hasLineThrough(result,index/cols,index%cols),"every predicted clear belongs to real line");
    check(chosen.expectedCleared==chosen.clearedCells().length,"no fictitious refill/cascade clears");
    check(sample.same(new GridPuzzle(rows,cols,cells)),"all search branches preserve input");
   }
  }
  System.out.println("Grid puzzle policy: "+checks+" assertions PASS; 180 independent rectangular-board scenes");
 }
}
