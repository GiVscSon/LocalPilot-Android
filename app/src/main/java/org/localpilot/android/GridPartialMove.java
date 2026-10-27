package org.localpilot.android;

import java.util.BitSet;

/**
 * Read-only proposal for partially decoded grid scenes.
 *
 * A -1 cell stays UNKNOWN. For planning only, each unknown is replaced with a
 * different sentinel that cannot match a genuine token. The policy then sees
 * only provably recognized ordinary tokens as playable endpoints/clears.
 *
 * This is NOT a dispatch permit or a visual-result verifier. A proposal must
 * not be sent to Android until the live session, ROI, special-item semantics,
 * fresh-frame gate and post-ACK verifier explicitly support partial boards.
 */
public final class GridPartialMove {
 private GridPartialMove(){}

 public static final class Proposal {
  public final GridPuzzlePolicy.Decision decision;
  public final int unknownCount;
  public final int knownCount;
  private Proposal(GridPuzzlePolicy.Decision decision,int unknownCount,int knownCount){
   this.decision=decision;this.unknownCount=unknownCount;this.knownCount=knownCount;
  }
 }

 /**
  * Produces a shadow-mode suggestion, or null if no locally justified action
  * exists. No input occurs and no unknown cell is relabeled in the observation.
  */
 public static Proposal propose(GridPuzzle observed,GridPuzzlePolicy.Mode mode,
                                int minGroup,int targetType,int targetRemaining,
                                BitSet specialCells){
  if(observed==null||mode==null||specialCells==null)
   throw new IllegalArgumentException("partial grid context");
  int[] original=observed.cells();
  if(specialCells.length()>original.length)
   throw new IllegalArgumentException("special mask outside board");
  int unknown=0,max=0,known=0;
  for(int value:original){
   if(value==-1)unknown++;
   else if(value>0){known++;max=Math.max(max,value);}
  }
  // Keep this API distinct from normal fully recognized-board planning.
  if(unknown==0||known<minGroup)return null;
  if(max>Integer.MAX_VALUE-original.length)return null;

  int[] surrogate=original.clone();
  BitSet excluded=(BitSet)specialCells.clone();
  int synthetic=max;
  for(int i=0;i<surrogate.length;i++){
   if(surrogate[i]==-1){
    surrogate[i]=++synthetic; // one unique never-matching sentinel per unknown
    excluded.set(i);         // never touch, clear or traverse it
   }
  }
  GridPuzzlePolicy.Decision chosen=GridPuzzlePolicy.choose(
    new GridPuzzle(observed.rows,observed.cols,surrogate),
    mode,minGroup,targetType,targetRemaining,excluded);
  if(chosen==null)return null;
  int from=chosen.fromRow*observed.cols+chosen.fromCol;
  int to=chosen.toRow*observed.cols+chosen.toCol;
  if(original[from]<=0||original[to]<=0||excluded.get(from)||excluded.get(to))
   return null;
  for(int index:chosen.clearedCells())
   if(index<0||index>=original.length||original[index]<=0||excluded.get(index))
    return null;
  return new Proposal(chosen,unknown,known);
 }
}
