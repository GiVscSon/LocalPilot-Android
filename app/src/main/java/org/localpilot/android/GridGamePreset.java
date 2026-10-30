package org.localpilot.android;

/**
 * Stable game mechanic IDs shared by the selector, preference scopes and
 * gesture pipeline. Changing a game's mechanic must NOT silently reuse a
 * palette learned by its previous (wrong) mechanic.
 */
public final class GridGamePreset {
 public static final int MATCH_WORLD_SWAP=6;
 public static final int GENERIC_GROUP=7;
 public static final int GENERIC_STRAIGHT_CHAIN=10;
 public static final int NASCHUPAI_TRACE=11;
 private GridGamePreset(){}
 public static boolean selected(int id){
  return id==MATCH_WORLD_SWAP||id==GENERIC_GROUP||id==GENERIC_STRAIGHT_CHAIN||id==NASCHUPAI_TRACE;
 }
 public static GridPuzzlePolicy.Mode mode(int id){
  return switch(id){
   case MATCH_WORLD_SWAP -> GridPuzzlePolicy.Mode.SWAP_MATCH3;
   case GENERIC_GROUP -> GridPuzzlePolicy.Mode.TAP_GROUP;
   case GENERIC_STRAIGHT_CHAIN,NASCHUPAI_TRACE -> GridPuzzlePolicy.Mode.TRACE_LINE;
   default -> throw new IllegalArgumentException("unsupported grid profile "+id);
  };
 }
 public static boolean protectSkulls(int id){return id==NASCHUPAI_TRACE;}
 public static int defaultMinimum(int id){
  if(!selected(id))throw new IllegalArgumentException("unsupported grid profile "+id);
  return mode(id)==GridPuzzlePolicy.Mode.TRACE_LINE?3:2;
 }
 public static String keySuffix(int id){
  if(!selected(id))throw new IllegalArgumentException("unsupported grid profile "+id);
  // Do not restore palette and geometry persisted by the OLD skull SWAP profile.
  return id==NASCHUPAI_TRACE?".protected-skull-trace-v1":"";
 }
}
