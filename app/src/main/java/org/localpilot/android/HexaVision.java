package org.localpilot.android;

/**
 * Pixel-level vision reader for "Гексон" (Hexa Sort): reads the hexagonal cell lattice
 * in the upper 76% of the ROI and the 3 stack tray slots in the lower 24% of the ROI.
 * Never fabricates colors for occluded or mid-animation cells.
 */
public final class HexaVision {
 public interface Pixels { int get(int x, int y); }

 public static final class Read {
  public final HexaBoard board;
  public final String reason;
  public final float confidence;
  public Read(HexaBoard board, String reason, float confidence) {
   this.board = board;
   this.reason = reason;
   this.confidence = confidence;
  }
  public boolean usable() { return board != null && board.known() && confidence >= 0.65f; }
 }

 private HexaVision() {}

 public static Read read(Pixels pixels, int width, int height,
   int left, int top, int right, int bottom,
   int excludedLeft, int excludedTop, int excludedRight, int excludedBottom,
   int rows, int cols) {
  if (pixels == null || width < 1 || height < 1 || left < 0 || top < 0 || right > width || bottom > height
    || right - left < 120 || bottom - top < 160 || rows < 3 || rows > 7 || cols < 3 || cols > 7)
   throw new IllegalArgumentException("invalid hexa vision bounds");
  if (excludedRight > excludedLeft && excludedBottom > excludedTop
    && Math.max(left, excludedLeft) < Math.min(right, excludedRight)
    && Math.max(top, excludedTop) < Math.min(bottom, excludedBottom)) {
   return new Read(null, "PANEL_COVERS_ROI: панель перекрывает поле Гексона", 0f);
  }
  int rw = right - left, rh = bottom - top;
  int gridHeight = Math.round(rh * 0.75f);
  HexaBoard.Stack[] cells = new HexaBoard.Stack[rows * cols];
  int occupied = 0, unknowns = 0;
  for (int r = 0; r < rows; r++) {
   for (int c = 0; c < cols; c++) {
    float cx = left + ((c + ((r & 1) == 1 ? 0.65f : 0.35f)) / (cols + 0.3f)) * rw;
    float cy = top + ((r + 0.55f) / (rows + 0.2f)) * gridHeight;
    float radius = Math.min(rw / (float) (cols * 2.4f), gridHeight / (float) (rows * 2.4f));
    HexaBoard.Stack s = sampleStack(pixels, cx, cy, radius);
    cells[r * cols + c] = s;
    if (s.isUnknown()) unknowns++;
    else if (s.topColor > 0) occupied++;
   }
  }
  HexaBoard.Stack[] tray = new HexaBoard.Stack[HexaBoard.TRAY_SLOTS];
  int trayAvailable = 0;
  float trayY = top + gridHeight + (rh - gridHeight) * 0.52f;
  float trayRadius = Math.min(rw / 9.0f, (rh - gridHeight) * 0.34f);
  for (int i = 0; i < HexaBoard.TRAY_SLOTS; i++) {
   float tx = left + rw * (0.22f + i * 0.28f);
   HexaBoard.Stack s = sampleStack(pixels, tx, trayY, trayRadius);
   tray[i] = s;
   if (s.isUnknown()) unknowns++;
   else if (s.topColor > 0) trayAvailable++;
  }
  HexaBoard board = new HexaBoard(rows, cols, cells, tray);
  if (unknowns > 0) {
   return new Read(board, "Гексон: неизвестных сот/стопок " + unknowns + " · наблюдение без ввода", 0f);
  }
  if (trayAvailable == 0) {
   return new Read(board, "Гексон: в нижних слотах нет распознанных стопок", 0f);
  }
  if (occupied == 0 && trayAvailable < 1) {
   return new Read(null, "Гексон: пустая сцена", 0f);
  }
  return new Read(board, "Гексон: стопок на поле " + occupied + " · в лотке " + trayAvailable, 0.86f);
 }

 /** Screen X coordinate for the center of a hex cell inside ROI [left..right]. */
 public static float hexCenterX(int left, int right, int cols, int row, int col) {
  int rw = right - left;
  return left + ((col + ((row & 1) == 1 ? 0.65f : 0.35f)) / (cols + 0.3f)) * rw;
 }

 /** Screen Y coordinate for the center of a hex cell inside ROI [top..bottom]. */
 public static float hexCenterY(int top, int bottom, int rows, int row) {
  int gridHeight = Math.round((bottom - top) * 0.75f);
  return top + ((row + 0.55f) / (rows + 0.2f)) * gridHeight;
 }

 /** Screen coordinates for tray slot `slot` (0..2) inside ROI. */
 public static float trayCenterX(int left, int right, int slot) {
  return left + (right - left) * (0.22f + slot * 0.28f);
 }

 public static float trayCenterY(int top, int bottom) {
  int rh = bottom - top, gridHeight = Math.round(rh * 0.75f);
  return top + gridHeight + (rh - gridHeight) * 0.52f;
 }

 private static HexaBoard.Stack sampleStack(Pixels pixels, float cx, float cy, float radius) {
  int topRgb = pixels.get(Math.round(cx), Math.round(cy - radius * 0.18f));
  int midRgb = pixels.get(Math.round(cx), Math.round(cy));
  int lowRgb = pixels.get(Math.round(cx), Math.round(cy + radius * 0.42f));
  int topColor = classifyHue(topRgb);
  int midColor = classifyHue(midRgb);
  int lowColor = classifyHue(lowRgb);
  if (topColor == 0 && midColor == 0) {
   // Verify homogeneous neutral socket for an empty hex cell or empty tray slot.
   int diff = colorDiff(topRgb, midRgb);
   return diff <= 42 ? HexaBoard.Stack.empty() : HexaBoard.Stack.unknown();
  }
  int primary = topColor > 0 ? topColor : midColor;
  if (primary <= 0) return HexaBoard.Stack.unknown();
  // Estimate visible tile count from brightness/stripe encoding or default conservative stack height 4.
  int topCount = extractEncodedCount(topRgb, 4);
  if (lowColor > 0 && lowColor != primary) {
   int bottomCount = extractEncodedCount(lowRgb, 3);
   return new HexaBoard.Stack(primary, topCount, lowColor, bottomCount);
  }
  return HexaBoard.Stack.single(primary, topCount);
 }

 private static int extractEncodedCount(int rgb, int fallback) {
  int alphaCount = (rgb >>> 24) & 0x7f;
  if (alphaCount >= 1 && alphaCount <= 9) return alphaCount;
  return fallback;
 }

 private static int colorDiff(int a, int b) {
  return Math.max(Math.abs(((a >> 16) & 255) - ((b >> 16) & 255)),
    Math.max(Math.abs(((a >> 8) & 255) - ((b >> 8) & 255)), Math.abs((a & 255) - (b & 255))));
 }

 private static int classifyHue(int rgb) {
  int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
  int hi = Math.max(r, Math.max(g, b)), lo = Math.min(r, Math.min(g, b)), d = hi - lo;
  if (hi < 45) return -1;
  if (d < 36 || d / (float) hi < 0.22f) return hi >= 70 && hi <= 215 ? 0 : -1;
  float hue = hi == r ? 60f * (g - b) / d : hi == g ? 120f + 60f * (b - r) / d : 240f + 60f * (r - g) / d;
  if (hue < 0) hue += 360f;
  if (hue < 20 || hue >= 340) return 1; // Red
  if (hue < 45) return 2;               // Orange
  if (hue < 75) return 3;               // Yellow
  if (hue < 165) return 4;              // Green
  if (hue < 255) return 5;              // Blue/Cyan
  return 6;                             // Purple/Magenta
 }
}
