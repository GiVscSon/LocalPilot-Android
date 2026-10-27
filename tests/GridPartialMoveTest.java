package org.localpilot.android;

import java.util.BitSet;
import java.util.Arrays;

/** Offline-only properties. Never authorizes Android input. */
public final class GridPartialMoveTest {
 private static int assertions;
 private static void ok(boolean value,String message){assertions++;if(!value)throw new AssertionError(message);}
 private static GridPuzzle grid(int rows,int cols,int...cells){return new GridPuzzle(rows,cols,cells);}
 private static GridPartialMove.Proposal shadow(GridPuzzle b,GridPuzzlePolicy.Mode m,BitSet special){
  return GridPartialMove.propose(b,m,2,0,0,special);
 }
 private static void onlyKnown(GridPuzzle b,GridPartialMove.Proposal p,BitSet special){
  ok(p!=null,"expected read-only proposal");
  int cols=b.cols;int a=p.decision.fromRow*cols+p.decision.fromCol;
  int z=p.decision.toRow*cols+p.decision.toCol;
  ok(b.cells()[a]>0&&b.cells()[z]>0&&!special.get(a)&&!special.get(z),"never act on unknown or special endpoints");
  for(int i:p.decision.clearedCells())
   ok(b.cells()[i]>0&&!special.get(i),"never predict clearing an unknown or protected token");
 }
 public static void main(String[] args){
  GridPuzzle swap=grid(4,4,
   1,2,1,8,
   2,1,3,7,
   3,2,3,6,
   4,5,-1,9);
  GridPartialMove.Proposal p=shadow(swap,GridPuzzlePolicy.Mode.SWAP_MATCH3,new BitSet());
  onlyKnown(swap,p,new BitSet());
  ok(p.unknownCount==1&&p.knownCount==15,"report unknown count without inventing classes");
  ok(p.decision.expectedCleared>=3,"only genuine known triples can be proposed");
  ok(swap.at(3,2)==-1,"immutable input preserves UNKNOWN");

  GridPuzzle tap=grid(4,4,
   1,1,2,3,
   4,1,5,6,
   7,8,9,10,
   11,12,-1,13);
  onlyKnown(tap,shadow(tap,GridPuzzlePolicy.Mode.TAP_GROUP,new BitSet()),new BitSet());
  GridPuzzle trace=grid(4,4,
   1,1,1,3,
   4,5,6,7,
   8,9,10,11,
   12,13,-1,14);
  onlyKnown(trace,shadow(trace,GridPuzzlePolicy.Mode.TRACE_LINE,new BitSet()),new BitSet());

  BitSet allProtected=new BitSet();allProtected.set(0,swap.rows*swap.cols);
  ok(shadow(swap,GridPuzzlePolicy.Mode.SWAP_MATCH3,allProtected)==null,
    "fully protected field abstains");
  ok(shadow(grid(3,3,1,-1,1,2,4,3,3,2,3),
     GridPuzzlePolicy.Mode.SWAP_MATCH3,new BitSet())==null,
    "unknown cannot be used as the middle of a match");
  ok(shadow(grid(3,3,-1,-1,-1,-1,-1,-1,-1,-1,-1),
     GridPuzzlePolicy.Mode.SWAP_MATCH3,new BitSet())==null,
    "no evidence means no speculative gesture");
  ok(shadow(grid(3,3,1,2,1,2,1,3,3,2,3),
     GridPuzzlePolicy.Mode.SWAP_MATCH3,new BitSet())==null,
    "full board uses existing validated policy, not the fallback");
  ok(shadow(grid(3,3,1,2,1,2,1,3,3,2,Integer.MAX_VALUE),
     GridPuzzlePolicy.Mode.SWAP_MATCH3,new BitSet())==null,
    "surrogate arithmetic must not overflow");
  BitSet invalid=new BitSet();invalid.set(100);
  try{shadow(swap,GridPuzzlePolicy.Mode.SWAP_MATCH3,invalid);
   throw new AssertionError("invalid protected mask accepted");
  }catch(IllegalArgumentException expected){assertions++;}
  System.out.println("Grid partial shadow proposals: "+assertions+" assertions PASS; no Android dispatch");
 }
}
