package org.localpilot.android;

import java.util.BitSet;

/** No Android SDK: exact game-mode and preference isolation contract. */
public final class GridGamePresetTest {
 private static int assertions;
 private static void check(boolean condition,String what){assertions++;if(!condition)throw new AssertionError(what);}
 public static void main(String[] args){
  check(GridGamePreset.selected(11),"Naщупай ход profile must be grid");
  check(GridGamePreset.mode(11)==GridPuzzlePolicy.Mode.SWAP_MATCH3,
    "documented Naщупай ход mechanic is neighbour swap; screenshots do not prove a trace");
  check(GridGamePreset.protectSkulls(11),"skull cells must be explicitly protected");
  check(GridGamePreset.defaultMinimum(11)==2,"swap default retained; match length is independently three");
  check(!GridGamePreset.keySuffix(11).equals(".protected-skull-trace-v1"),
    "trace-mode palette, geometry and move history must not migrate into corrected swap");
  check(!GridGamePreset.keySuffix(11).isEmpty(),"protected swap must use scoped palette");
  check(GridGamePreset.mode(6)==GridPuzzlePolicy.Mode.SWAP_MATCH3,
    "Match World remains SWAP");
  check(!GridGamePreset.protectSkulls(6),"Match World does not infer skull mask");
  check(GridGamePreset.mode(7)==GridPuzzlePolicy.Mode.TAP_GROUP,
    "generic group mode unchanged");
  check(GridGamePreset.mode(10)==GridPuzzlePolicy.Mode.TRACE_LINE,
    "generic straight trace unchanged");
  check(!GridGamePreset.protectSkulls(10),
    "generic trace does not infer protected skulls");
  for(int n:new int[]{-1,0,4,5,8,9,12,23}){
   check(!GridGamePreset.selected(n),"non-grid profile not allowed as grid");
   try{GridGamePreset.mode(n);throw new AssertionError("unsupported profile mode silently accepted");}
   catch(IllegalArgumentException expected){assertions++;}
  }
  // No straight triple exists. Swapping the middle of the first two rows
  // creates the independently specified first-row triple, away from the skull.
  int[] tiles={1,2,1, 3,1,4, 5,6,0};
  BitSet skull=new BitSet(9);skull.set(8);
  GridPuzzle board=new GridPuzzle(3,3,tiles);
  check(GridPuzzlePolicy.choose(board,GridPuzzlePolicy.Mode.TRACE_LINE,3,0,0,skull)==null,
    "fixture distinguishes a swap from a pre-existing trace");
  GridPuzzlePolicy.Decision d=GridPuzzlePolicy.choose(board,GridGamePreset.mode(11),2,0,0,skull);
  check(d!=null,"safe adjacent swap must be found");
  check(d.mode==GridPuzzlePolicy.Mode.SWAP_MATCH3,"planner dispatches documented mechanic");
  check(Math.abs(d.fromRow-d.toRow)+Math.abs(d.fromCol-d.toCol)==1,"orthogonally adjacent endpoints");
  int from=d.fromRow*3+d.fromCol,to=d.toRow*3+d.toCol;
  check(!skull.get(from)&&!skull.get(to),"skull not a gesture endpoint");
  int value=tiles[from];tiles[from]=tiles[to];tiles[to]=value;
  check(tiles[0]==1&&tiles[1]==1&&tiles[2]==1,"independent result oracle: first row matches");
  check(java.util.Arrays.equals(d.clearedCells(),new int[]{0,1,2}),"exact ordinary predicted clears");
  check(d.specialInvolved==0,"skull not cleared");
  BitSet blocked=(BitSet)skull.clone();blocked.set(1);
  check(GridPuzzlePolicy.choose(board,GridGamePreset.mode(11),2,0,0,blocked)==null,
    "blocked necessary endpoint prevents the swap");
  int[] incomplete=board.cells();incomplete[7]=-1;
  GridPuzzle partial=new GridPuzzle(3,3,incomplete);
  check(GridPuzzlePolicy.choose(partial,GridGamePreset.mode(11),2,0,0,skull)==null,
    "unknown board never enters complete-board swap pipeline");
  var proposal=GridPuzzlePolicy.choosePartial(partial,GridGamePreset.mode(11),2,0,0,skull);
  check(!GridPartialActionGate.allows(partial,GridGamePreset.mode(11),skull,proposal,true,true),
    "partial trace authorization cannot leak into protected swap");
  System.out.println("Naщупай ход SWAP / skull mode: "+assertions+" assertions PASS");
 }
}
