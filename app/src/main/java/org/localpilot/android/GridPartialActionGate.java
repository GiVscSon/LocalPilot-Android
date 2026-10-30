package org.localpilot.android;

import java.util.BitSet;

/**
 * Physical-game partial input remains opt-in to a single provably bounded
 * straight TRACE_LINE. No guess about UNKNOWN cells, boosters, or cascades.
 *
 * The caller separately verifies window identity, full ROI geometry, no
 * overlay, two stable fresh frames, input permission, and post-ACK proof.
 */
public final class GridPartialActionGate {
 private GridPartialActionGate(){}
 public static boolean allows(GridPuzzle board,GridPuzzlePolicy.Mode mode,
   BitSet special,GridPuzzlePolicy.Decision action,
   boolean partialUsable,boolean sessionReady){
  if(!partialUsable||!sessionReady||board==null||mode!=GridPuzzlePolicy.Mode.TRACE_LINE
    ||action==null||action.mode!=mode||special==null||board.known()
    ||action.expectedCleared<3||special.length()>board.rows*board.cols)return false;
  int[] cells=board.cells();
  int known=0;
  for(int value:cells)if(value>=0)known++;
  if(known<Math.max(6,(cells.length*72+99)/100))return false;
  int row=action.fromRow,col=action.fromCol;
  int dr=action.toRow-row,dc=action.toCol-col;
  int steps=Math.max(Math.abs(dr),Math.abs(dc));
  if(steps<2||!(dr==0||dc==0||Math.abs(dr)==Math.abs(dc)))return false;
  if(row<0||row>=board.rows||col<0||col>=board.cols
    ||action.toRow<0||action.toRow>=board.rows
    ||action.toCol<0||action.toCol>=board.cols)return false;
  int rowStep=Integer.compare(dr,0),colStep=Integer.compare(dc,0);
  int start=row*board.cols+col;
  int expectedType=cells[start];
  if(expectedType<=0||special.get(start))return false;
  int[] claimed=action.clearedCells();
  if(claimed.length!=steps+1)return false;
  // The planner must claim exactly the straight, contiguous cells the gesture
  // will traverse. A skull/unknown cannot be crossed even if not an endpoint.
  for(int k=0;k<=steps;k++){
   int nr=row+k*rowStep,nc=col+k*colStep;
   if(nr<0||nr>=board.rows||nc<0||nc>=board.cols)return false;
   int index=nr*board.cols+nc;
   if(cells[index]!=expectedType||special.get(index))return false;
   boolean included=false;
   for(int claim:claimed)if(claim==index){included=true;break;}
   if(!included)return false;
  }
  return true;
 }
}
