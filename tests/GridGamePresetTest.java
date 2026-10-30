package org.localpilot.android;

import java.util.BitSet;

/** No Android SDK: exact game-mode and preference isolation contract. */
public final class GridGamePresetTest {
 private static int assertions;
 private static void check(boolean condition,String what){assertions++;if(!condition)throw new AssertionError(what);}
 public static void main(String[] args){
  check(GridGamePreset.selected(11),"Naщупай ход profile must be grid");
  check(GridGamePreset.mode(11)==GridPuzzlePolicy.Mode.TRACE_LINE,
    "Naщупай ход must trace, not swap neighbours");
  check(GridGamePreset.protectSkulls(11),"skull cells must be explicitly protected");
  check(GridGamePreset.defaultMinimum(11)==3,"trace requires a 3-cell group by default");
  check(!GridGamePreset.keySuffix(11).equals(".protected-skull"),
    "old swap-mode palette and ROI must not migrate silently into trace mode");
  check(!GridGamePreset.keySuffix(11).isEmpty(),"protected trace must use scoped palette");
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
  // Skull at row0 col3: planner may not include it in a trace even if the
  // rest of the first row would otherwise form a valid homogeneous run.
  int[] tiles=new int[36];
  for(int i=0;i<tiles.length;i++)tiles[i]=i%2+2;
  tiles[0]=5;tiles[1]=5;tiles[2]=5;tiles[3]=0;
  BitSet skull=new BitSet(36);skull.set(3);
  GridPuzzlePolicy.Decision d=GridPuzzlePolicy.choose(new GridPuzzle(6,6,tiles),
     GridGamePreset.mode(11),3,0,0,skull);
  check(d!=null,"safe straight line must be found");
  check(d.mode==GridPuzzlePolicy.Mode.TRACE_LINE,"planner must not dispatch SWAP");
  check(d.specialInvolved==0,"skull must not participate in a trace");
  for(int idx:d.clearedCells())check(idx!=3,"skull not selected by gesture");
  System.out.println("Naщупай ход TRACE_LINE / skull mode: "+assertions+" assertions PASS");
 }
}
