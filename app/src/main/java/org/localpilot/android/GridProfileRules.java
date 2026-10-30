package org.localpilot.android;

/** Explicit, fail-closed mapping between the game picker and grid action type. */
public final class GridProfileRules {
 private GridProfileRules(){}
 public static GridPuzzlePolicy.Mode mode(int profile){
  return switch(profile){
   case 6->GridPuzzlePolicy.Mode.SWAP_MATCH3; // Match World
   case 7->GridPuzzlePolicy.Mode.TAP_GROUP;
   case 10,11->GridPuzzlePolicy.Mode.TRACE_LINE; // Straight line, incl. diagonals.
   default->throw new IllegalArgumentException("unsupported grid profile");
  };
 }
 public static int minimum(int profile){return mode(profile)==GridPuzzlePolicy.Mode.TRACE_LINE?3:2;}
 public static boolean protectSkulls(int profile){mode(profile);return profile==11;}
}
