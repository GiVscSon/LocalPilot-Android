package org.localpilot.android;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Hexagonal stack-sorting board model for "Гексон" (Hexa Sort mechanic).
 * Each cell on the hex lattice is either empty (topColor=0), UNKNOWN (topColor=-1),
 * or holds a stack of up to two distinct color layers (bottomColor/bottomCount, topColor/topCount).
 * When a top layer reaches CLEAR_THRESHOLD (10), those tiles clear and expose the bottom layer.
 */
public final class HexaBoard {
 public static final int CLEAR_THRESHOLD = 10;
 public static final int TRAY_SLOTS = 3;

 public static final class Stack {
  public final int topColor, topCount, bottomColor, bottomCount;
  public Stack(int topColor, int topCount, int bottomColor, int bottomCount) {
   if (topColor < -1 || topColor > 12) throw new IllegalArgumentException("hex color");
   if (topColor <= 0 && (topCount != 0 || bottomColor != 0 || bottomCount != 0))
    throw new IllegalArgumentException("empty/unknown stack must have zero counts");
   if (topColor > 0 && (topCount < 1 || topCount > 30 || bottomColor < 0 || bottomColor > 12
     || (bottomColor == 0) != (bottomCount == 0) || bottomCount < 0 || bottomCount > 20))
    throw new IllegalArgumentException("invalid stack counts");
   if (topColor > 0 && bottomColor == topColor) {
    this.topColor = topColor;
    this.topCount = topCount + bottomCount;
    this.bottomColor = 0;
    this.bottomCount = 0;
   } else {
    this.topColor = topColor;
    this.topCount = topCount;
    this.bottomColor = bottomColor;
    this.bottomCount = bottomCount;
   }
  }
  public static Stack empty() { return new Stack(0, 0, 0, 0); }
  public static Stack unknown() { return new Stack(-1, 0, 0, 0); }
  public static Stack single(int color, int count) { return new Stack(color, count, 0, 0); }
  public boolean isEmpty() { return topColor == 0; }
  public boolean isUnknown() { return topColor < 0; }
  public int totalHeight() { return topCount + bottomCount; }
  @Override public boolean equals(Object o) {
   return o instanceof Stack s && topColor == s.topColor && topCount == s.topCount
     && bottomColor == s.bottomColor && bottomCount == s.bottomCount;
  }
  @Override public int hashCode() {
   return ((topColor * 31 + topCount) * 31 + bottomColor) * 31 + bottomCount;
  }
 }

 public static final class PlacementOutcome {
  public final HexaBoard boardAfter;
  public final int mergedTiles, clearedStacks, clearedTiles, chainSteps, freeCellsAfter;
  PlacementOutcome(HexaBoard boardAfter, int mergedTiles, int clearedStacks, int clearedTiles, int chainSteps, int freeCellsAfter) {
   this.boardAfter = boardAfter;
   this.mergedTiles = mergedTiles;
   this.clearedStacks = clearedStacks;
   this.clearedTiles = clearedTiles;
   this.chainSteps = chainSteps;
   this.freeCellsAfter = freeCellsAfter;
  }
 }

 public final int rows, cols;
 private final Stack[] cells;
 private final Stack[] tray;

 public HexaBoard(int rows, int cols, Stack[] cells, Stack[] tray) {
  if (rows < 3 || rows > 7 || cols < 3 || cols > 7 || cells == null || cells.length != rows * cols
    || tray == null || tray.length != TRAY_SLOTS)
   throw new IllegalArgumentException("hex board dimensions");
  this.rows = rows;
  this.cols = cols;
  this.cells = cells.clone();
  this.tray = tray.clone();
  for (Stack s : this.cells) if (s == null) throw new IllegalArgumentException("null hex cell");
  for (Stack s : this.tray) if (s == null) throw new IllegalArgumentException("null tray slot");
 }

 public int size() { return cells.length; }
 public Stack cell(int index) {
  if (index < 0 || index >= cells.length) throw new IllegalArgumentException("hex index");
  return cells[index];
 }
 public Stack cell(int row, int col) {
  if (row < 0 || row >= rows || col < 0 || col >= cols) throw new IllegalArgumentException("hex row/col");
  return cells[row * cols + col];
 }
 public Stack traySlot(int slot) {
  if (slot < 0 || slot >= TRAY_SLOTS) throw new IllegalArgumentException("tray slot");
  return tray[slot];
 }
 public Stack[] cells() { return cells.clone(); }
 public Stack[] tray() { return tray.clone(); }

 public boolean known() {
  for (Stack s : cells) if (s.isUnknown()) return false;
  for (Stack s : tray) if (s.isUnknown()) return false;
  return availableTrayCount() > 0;
 }

 public int freeCellCount() {
  int n = 0;
  for (Stack s : cells) if (s.isEmpty()) n++;
  return n;
 }

 public int occupiedCellCount() {
  int n = 0;
  for (Stack s : cells) if (s.topColor > 0) n++;
  return n;
 }

 public int availableTrayCount() {
  int n = 0;
  for (Stack s : tray) if (s.topColor > 0) n++;
  return n;
 }

 /** Odd-r offset hexagonal grid neighbors (up to 6 neighbors per cell). */
 public int[] neighbors(int index) {
  if (index < 0 || index >= cells.length) throw new IllegalArgumentException("hex index");
  int r = index / cols, c = index % cols;
  int[][] deltas = (r & 1) == 0
    ? new int[][]{{-1, -1}, {-1, 0}, {0, -1}, {0, 1}, {1, -1}, {1, 0}}
    : new int[][]{{-1, 0}, {-1, 1}, {0, -1}, {0, 1}, {1, 0}, {1, 1}};
  int[] temp = new int[6];
  int count = 0;
  for (int[] d : deltas) {
   int nr = r + d[0], nc = c + d[1];
   if (nr >= 0 && nr < rows && nc >= 0 && nc < cols) temp[count++] = nr * cols + nc;
  }
  return Arrays.copyOf(temp, count);
 }

 /**
  * Simulates placing tray[slot] onto an empty hex cell `targetIndex` and resolving
  * deterministic top-layer color merges and >=10 stack clears.
  */
 public PlacementOutcome simulatePlace(int slot, int targetIndex) {
  if (!known() || slot < 0 || slot >= TRAY_SLOTS || targetIndex < 0 || targetIndex >= cells.length)
   return null;
  Stack hand = tray[slot];
  if (hand.topColor <= 0 || !cells[targetIndex].isEmpty()) return null;
  Stack[] nextCells = cells.clone();
  Stack[] nextTray = tray.clone();
  nextTray[slot] = Stack.empty();
  nextCells[targetIndex] = hand;

  int mergedTiles = 0, clearedStacks = 0, clearedTiles = 0, chainSteps = 0;
  boolean progress = true;
  int focus = targetIndex;
  while (progress && chainSteps < 16) {
   progress = false;
   // Pull matching top colors from neighbors into `focus` first.
   if (focus >= 0 && nextCells[focus].topColor > 0) {
    int color = nextCells[focus].topColor;
    int absorbed = 0;
    for (int nb : neighbors(focus)) {
     Stack ns = nextCells[nb];
     if (ns.topColor == color) {
      absorbed += ns.topCount;
      nextCells[nb] = ns.bottomColor > 0
        ? new Stack(ns.bottomColor, ns.bottomCount, 0, 0)
        : Stack.empty();
     }
    }
    if (absorbed > 0) {
     Stack fs = nextCells[focus];
     nextCells[focus] = new Stack(fs.topColor, fs.topCount + absorbed, fs.bottomColor, fs.bottomCount);
     mergedTiles += absorbed;
     chainSteps++;
     progress = true;
    }
   }
   // Check all cells for any adjacent matching top colors or >= CLEAR_THRESHOLD clears.
   for (int i = 0; i < nextCells.length; i++) {
    Stack s = nextCells[i];
    if (s.topColor > 0 && s.topCount >= CLEAR_THRESHOLD) {
     clearedStacks++;
     clearedTiles += s.topCount;
     nextCells[i] = s.bottomColor > 0
       ? new Stack(s.bottomColor, s.bottomCount, 0, 0)
       : Stack.empty();
     focus = i;
     progress = true;
    }
   }
   if (!progress) {
    for (int i = 0; i < nextCells.length; i++) {
     Stack s = nextCells[i];
     if (s.topColor <= 0) continue;
     for (int nb : neighbors(i)) {
      Stack ns = nextCells[nb];
      if (ns.topColor == s.topColor) {
       nextCells[i] = new Stack(s.topColor, s.topCount + ns.topCount, s.bottomColor, s.bottomCount);
       nextCells[nb] = ns.bottomColor > 0
         ? new Stack(ns.bottomColor, ns.bottomCount, 0, 0)
         : Stack.empty();
       mergedTiles += ns.topCount;
       chainSteps++;
       focus = i;
       progress = true;
       break;
      }
     }
     if (progress) break;
    }
   }
  }
  HexaBoard after = new HexaBoard(rows, cols, nextCells, nextTray);
  return new PlacementOutcome(after, mergedTiles, clearedStacks, clearedTiles, chainSteps, after.freeCellCount());
 }

 public boolean same(HexaBoard other) {
  return other != null && rows == other.rows && cols == other.cols
    && Arrays.equals(cells, other.cells) && Arrays.equals(tray, other.tray);
 }
}
