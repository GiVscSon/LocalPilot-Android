package org.localpilot.android;

/**
 * Strategic placement planner for "Гексон" (Hexa Sort).
 * Evaluates all legal drag-and-drop moves (traySlot -> freeHexCell), ranking
 * immediate >=10 stack clears, neighbor top-color merges, bottom-layer unblocking,
 * and free-cell preservation.
 */
public final class HexaPolicy {
 public static final class Move {
  public final int traySlot, targetIndex, targetRow, targetCol;
  public final int mergedTiles, clearedStacks, clearedTiles, freeCellsAfter;
  public final double score;
  public final HexaBoard expectedBoard;

  public Move(int traySlot, int targetIndex, int cols,
    int mergedTiles, int clearedStacks, int clearedTiles, int freeCellsAfter,
    double score, HexaBoard expectedBoard) {
   this.traySlot = traySlot;
   this.targetIndex = targetIndex;
   this.targetRow = targetIndex / cols;
   this.targetCol = targetIndex % cols;
   this.mergedTiles = mergedTiles;
   this.clearedStacks = clearedStacks;
   this.clearedTiles = clearedTiles;
   this.freeCellsAfter = freeCellsAfter;
   this.score = score;
   this.expectedBoard = expectedBoard;
  }
 }

 private HexaPolicy() {}

 public static Move choose(HexaBoard board) {
  return choose(board, null);
 }

 /** Optional neural student bonus can reorder legal moves, never authorize illegal ones. */
 public static Move choose(HexaBoard board, double[] targetCellBonuses) {
  if (board == null || !board.known() || board.freeCellCount() == 0) return null;
  Move best = null;
  for (int slot = 0; slot < HexaBoard.TRAY_SLOTS; slot++) {
   HexaBoard.Stack hand = board.traySlot(slot);
   if (hand.topColor <= 0) continue;
   for (int cell = 0; cell < board.size(); cell++) {
    if (!board.cell(cell).isEmpty()) continue;
    HexaBoard.PlacementOutcome outcome = board.simulatePlace(slot, cell);
    if (outcome == null) continue;
    int adjPotential = 0;
    for (int nb : board.neighbors(cell)) {
     HexaBoard.Stack ns = outcome.boardAfter.cell(nb);
     if (ns.bottomColor > 0 && ns.bottomColor == hand.topColor) adjPotential += 2;
    }
    int r = cell / board.cols, c = cell % board.cols;
    double centerBias = -0.4 * (Math.abs(r - (board.rows - 1) * 0.5) + Math.abs(c - (board.cols - 1) * 0.5));
    double bonus = targetCellBonuses != null && cell < targetCellBonuses.length
      ? Math.max(-8.0, Math.min(8.0, targetCellBonuses[cell])) : 0.0;
    double score = outcome.clearedStacks * 120.0
      + outcome.clearedTiles * 14.0
      + outcome.mergedTiles * 18.0
      + outcome.chainSteps * 10.0
      + outcome.freeCellsAfter * 6.0
      + adjPotential * 4.0
      + centerBias
      + bonus;
    Move candidate = new Move(slot, cell, board.cols,
      outcome.mergedTiles, outcome.clearedStacks, outcome.clearedTiles, outcome.freeCellsAfter,
      score, outcome.boardAfter);
    if (best == null || candidate.score > best.score) best = candidate;
   }
  }
  return best;
 }
}
