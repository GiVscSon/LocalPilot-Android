package org.localpilot.android.learning;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.List;
import org.localpilot.android.*;
import org.localpilot.android.retro.*;

/**
 * Unified 64-D object & candidate encoder with game-specific adapters for all 10 target games.
 * Feature layout (float[64], clipped to [-1, 1]):
 * - [0..15]: Canonical global scene/physics state (player/board geometry, velocities,
 *            threat margin, free space ratio, special/skull density, confidence, genre tag).
 * - [16..63]: 6 slots x 8 features describing either the 6 nearest/most critical scene objects
 *            (for real-time arcade/jumper/platformer games) or up to 8 candidate legal moves
 *            (6 slots x 8 features for candidate-ranking board/bubble games).
 * Also produces the exact `boolean[] legalMask` so the neural student never selects an illegal move.
 */
public final class ObservationEncoder {
 public static final class EncodedState {
  public final TaskSchema.GameId gameId;
  public final float[] features;
  public final boolean[] legalMask;
  public final float confidence;

  public EncodedState(TaskSchema.GameId gameId, float[] features, boolean[] legalMask, float confidence) {
   if (gameId == null || features == null || features.length != TaskSchema.OBS_DIM
     || legalMask == null || legalMask.length != gameId.actionCount)
    throw new IllegalArgumentException("invalid encoded state");
   this.gameId = gameId;
   this.features = features.clone();
   this.legalMask = legalMask.clone();
   this.confidence = confidence;
  }

  public boolean hasLegalAction() {
   for (boolean b : legalMask) if (b) return true;
   return false;
  }
 }

 private ObservationEncoder() {}

 private static float clip(double v) {
  if (!Double.isFinite(v)) return 0f;
  return (float) Math.max(-1.0, Math.min(1.0, v));
 }

 /** 1. Шипучка (Bubble Shooter): encodes board + top 8 candidate shots (direct & 1-bounce). */
 public static EncodedState encodeBubble(BubbleBoard board, List<BubblePolicy.Shot> candidates) {
  float[] f = new float[TaskSchema.OBS_DIM];
  boolean[] mask = new boolean[TaskSchema.GameId.SHIPUCHKA.actionCount];
  if (board == null || !board.ceilingConnected() || board.count() == 0) {
   return new EncodedState(TaskSchema.GameId.SHIPUCHKA, f, mask, 0f);
  }
  f[0] = clip(board.launcherX / (float) Math.max(1, board.width));
  f[1] = clip(board.launcherY / (float) Math.max(1, board.height));
  f[2] = clip(board.loadedColor / 4.0);
  f[3] = clip(board.count() / (double) Math.max(1, board.size()));
  f[4] = board.shortRowParity == 1 ? 1f : -1f;
  f[5] = 0.9f;
  int n = candidates == null ? 0 : Math.min(8, candidates.size());
  for (int i = 0; i < n; i++) {
   BubblePolicy.Shot s = candidates.get(i);
   if (s == null) continue;
   mask[i] = true;
   int base = 16 + i * 6;
   f[base] = clip(s.removed / 6.0);
   f[base + 1] = clip(s.dropped / 8.0);
   f[base + 2] = s.bounces == 0 ? 1f : -0.5f;
   f[base + 3] = clip((s.aimX - board.launcherX) / Math.max(1f, board.width * 0.5f));
   f[base + 4] = clip(1.0 - (s.landing / (double) Math.max(1, board.size())));
   f[base + 5] = clip((s.removed * 1.0 + s.dropped * 1.2 - s.bounces * 0.15) / 5.0);
  }
  return new EncodedState(TaskSchema.GameId.SHIPUCHKA, f, mask, mask[0] ? 0.90f : 0f);
 }

 /** 2. Прыгун Ари (Vertical Jumper with horizontal wrap-around). */
 public static EncodedState encodeJumper(JumperState s) {
  float[] f = new float[TaskSchema.OBS_DIM];
  boolean[] mask = new boolean[]{true, true, true}; // TAP_LEFT, TAP_RIGHT, WAIT
  if (s == null || !s.usable()) {
   return new EncodedState(TaskSchema.GameId.ARI_JUMPER, f, new boolean[]{false, false, true}, 0f);
  }
  float rw = Math.max(1f, s.roiRight - s.roiLeft), rh = Math.max(1f, s.roiBottom - s.roiTop);
  float scale = JumperPolicy.coordinateScale(s);
  f[0] = clip((s.player.centerX() - s.roiLeft) / rw * 2.0 - 1.0);
  f[1] = clip((s.player.centerY() - s.roiTop) / rh * 2.0 - 1.0);
  f[2] = clip(s.playerVx / (400.0 * scale));
  f[3] = clip((s.playerVy - s.scrollVy) / (600.0 * scale));
  f[4] = clip(s.scrollVy / (400.0 * scale));
  f[5] = clip(s.platforms.size() / 12.0);
  f[6] = clip(s.hazards.size() / 6.0);
  f[7] = clip(s.confidence);

  // Compute directional target delta and hazard flags in slots [8..15] for analytical & learned policy.
  JumperPolicy.Action heuristic = JumperPolicy.nextAction(s);
  f[8] = heuristic == JumperPolicy.Action.TAP_LEFT ? -1f : heuristic == JumperPolicy.Action.TAP_RIGHT ? 1f : 0f;
  boolean hazardLeft = false, hazardRight = false;
  for (JumperState.ObjectBox h : s.hazards) {
   float dy = Math.abs(h.centerY() - s.player.centerY());
   float dx = (h.centerX() - s.player.centerX());
   if (dx > rw * 0.5f) dx -= rw; else if (dx < -rw * 0.5f) dx += rw;
   if (dy <= Math.max(105f * scale, s.player.height() * 0.9f)
     && Math.abs(dx) <= Math.max(56f * scale, s.player.width() * 0.65f + h.width() * 0.35f)) {
    if (dx < 0) hazardLeft = true; else hazardRight = true;
   }
  }
  f[9] = hazardLeft ? 1f : 0f;
  f[10] = hazardRight ? 1f : 0f;
  if (hazardLeft && !hazardRight) mask[0] = false;
  if (hazardRight && !hazardLeft) mask[1] = false;
  if (hazardLeft && hazardRight) { mask[0] = false; mask[1] = false; }

  int slot = 0;
  for (JumperState.ObjectBox p : s.platforms) {
   if (slot >= 4) break;
   int k = 16 + slot * 8;
   float dx = p.centerX() - s.player.centerX();
   if (dx > rw * 0.5f) dx -= rw; else if (dx < -rw * 0.5f) dx += rw;
   f[k] = clip(dx / (rw * 0.5f));
   f[k + 1] = clip((p.centerY() - s.player.centerY()) / rh);
   f[k + 2] = clip(p.width() / rw);
   f[k + 3] = clip(p.height() / rh);
   f[k + 6] = 0.33f;
   f[k + 7] = 1f;
   slot++;
  }
  for (JumperState.ObjectBox h : s.hazards) {
   if (slot >= 6) break;
   int k = 16 + slot * 8;
   float dx = h.centerX() - s.player.centerX();
   if (dx > rw * 0.5f) dx -= rw; else if (dx < -rw * 0.5f) dx += rw;
   f[k] = clip(dx / (rw * 0.5f));
   f[k + 1] = clip((h.centerY() - s.player.centerY()) / rh);
   f[k + 2] = clip(h.width() / rw);
   f[k + 3] = clip(h.height() / rh);
   f[k + 6] = -1f;
   f[k + 7] = 1f;
   slot++;
  }
  return new EncodedState(TaskSchema.GameId.ARI_JUMPER, f, mask, s.confidence);
 }

 /** 3 & 9. Мэтч Ворлд (7x7) and Нащупай ход (6x6 protected skull swap). */
 public static EncodedState encodeGrid(TaskSchema.GameId gameId, GridPuzzle board, BitSet special,
   int targetType, List<GridPuzzlePolicy.Decision> candidates) {
  float[] f = new float[TaskSchema.OBS_DIM];
  boolean[] mask = new boolean[gameId.actionCount];
  if (board == null || !board.known()) {
   return new EncodedState(gameId, f, mask, 0f);
  }
  int totalCells = board.rows * board.cols;
  int specialCount = special == null ? 0 : special.cardinality();
  f[0] = clip(board.rows / 10.0);
  f[1] = clip(board.cols / 10.0);
  f[2] = clip(specialCount / (double) Math.max(1, totalCells));
  f[3] = targetType > 0 ? 1f : 0f;
  f[4] = gameId == TaskSchema.GameId.NASHCHUPAY_KHOD ? 1f : 0f;
  f[5] = 0.92f;
  int n = candidates == null ? 0 : Math.min(8, candidates.size());
  for (int i = 0; i < n; i++) {
   GridPuzzlePolicy.Decision d = candidates.get(i);
   if (d == null) continue;
   // Enforce skull/special safety mask: no candidate touching a special cell is legal.
   int from = d.fromRow * board.cols + d.fromCol, to = d.toRow * board.cols + d.toCol;
   if (special != null && (special.get(from) || special.get(to) || d.specialInvolved > 0)) continue;
   mask[i] = true;
   int base = 16 + i * 6;
   f[base] = clip(d.expectedCleared / 6.0);
   f[base + 1] = clip(d.targetCleared / 5.0);
   f[base + 2] = clip(d.score / 250.0);
   f[base + 3] = clip(((d.fromRow + d.toRow) * 0.5) / Math.max(1, board.rows - 1));
   f[base + 4] = clip(d.expectedCleared >= 4 ? 1.0 : 0.0);
   f[base + 5] = 1f;
  }
  return new EncodedState(gameId, f, mask, mask[0] ? 0.92f : 0f);
 }

 /** 4, 5, 6. Карканоид (PADDLE), Маркет Раш (TRAFFIC), Гошан (FLAPPY). */
 public static EncodedState encodeArcade(ArcadeObservation s, ArcadeController controller) {
  if (s == null) {
   return new EncodedState(TaskSchema.GameId.KARKANOID, new float[TaskSchema.OBS_DIM], new boolean[5], 0f);
  }
  return switch (s.profile) {
   case PADDLE -> encodePaddle(s);
   case TRAFFIC -> encodeTraffic(s);
   case FLAPPY -> encodeFlappy(s, controller);
   default -> new EncodedState(TaskSchema.GameId.ARI_JUMPER, new float[TaskSchema.OBS_DIM], new boolean[3], 0f);
  };
 }

 private static EncodedState encodePaddle(ArcadeObservation s) {
  float[] f = new float[TaskSchema.OBS_DIM];
  // Actions: 0=WAIT, 1=LAUNCH, 2=MOVE_LEFT_OFFSET, 3=MOVE_CENTER, 4=MOVE_RIGHT_OFFSET
  boolean[] mask = new boolean[]{true, false, false, false, false};
  if (!s.usable()) return new EncodedState(TaskSchema.GameId.KARKANOID, f, mask, 0f);
  boolean isServe = s.tracked && Math.abs(s.vx) < .01f && Math.abs(s.vy) < .01f
    && Math.abs(s.player.bottom - s.control.top) < .035f
    && s.player.x() > s.control.left && s.player.x() < s.control.right;
  if (isServe) {
   mask[0] = false;
   mask[1] = true;
  } else {
   float impactX = s.player.x();
   if (s.tracked && s.vy > .03f) {
    float time = Math.max(0, Math.min(.8f, (s.control.top - s.player.bottom) / s.vy));
    impactX = ArcadePolicy.reflect(s.player.x() + s.vx * time, s.left + s.player.width() / 2, s.right - s.player.width() / 2);
   }
   boolean aligned = Math.abs(impactX - s.control.x()) < Math.max(.012f, s.control.width() * .14f);
   if (!aligned) {
    mask[2] = true;
    mask[3] = true;
    mask[4] = true;
    f[8] = 1f;
   }
   f[9] = clip((impactX - s.control.x()) * 2.0);
  }
  float defeatMargin = paddleDefeatMargin(s);
  float urgentX = urgentBlockTargetX(s);
  f[0] = clip(s.player.x() * 2.0 - 1.0);
  f[1] = clip(s.player.y() * 2.0 - 1.0);
  f[2] = clip(s.vx);
  f[3] = clip(s.vy);
  f[4] = clip(s.control.x() * 2.0 - 1.0);
  f[5] = clip(defeatMargin * 2.0 - 1.0);
  f[6] = Float.isFinite(urgentX) ? clip((urgentX - s.player.x()) * 2.0) : 0f;
  f[7] = isServe ? 1f : 0f;
  f[9] = clip(s.obstacles.size() / 12.0);
  for (int i = 0; i < Math.min(6, s.obstacles.size()); i++) {
   ArcadeObservation.Box b = s.obstacles.get(i);
   int k = 16 + i * 8;
   f[k] = clip(b.x() - s.control.x());
   f[k + 1] = clip(s.control.top - b.bottom);
   f[k + 2] = clip(b.width());
   f[k + 3] = clip(b.height());
   f[k + 6] = 0.5f;
   f[k + 7] = 1f;
  }
  return new EncodedState(TaskSchema.GameId.KARKANOID, f, mask, s.confidence);
 }

 private static EncodedState encodeTraffic(ArcadeObservation s) {
  float[] f = new float[TaskSchema.OBS_DIM];
  // Actions: 0=LEFT, 1=RIGHT, 2=WAIT
  boolean[] mask = new boolean[]{true, true, true};
  if (!s.usable()) return new EncodedState(TaskSchema.GameId.MARKET_RUSH, f, new boolean[]{false, false, true}, 0f);
  float laneWidth = (s.right - s.left) / 3f;
  int current = Math.max(0, Math.min(2, (int) ((s.player.x() - s.left) / laneWidth)));
  if (current == 0) mask[0] = false;
  if (current == 2) mask[1] = false;
  float[] risk = new float[3];
  for (ArcadeObservation.Box obstacle : s.obstacles) {
   if (obstacle.top > s.player.bottom + .025f) continue;
   float distance = s.player.top - obstacle.bottom;
   if (distance > .65f) continue;
   float danger = distance < .08f ? 1f : 1f - Math.max(0f, distance) / .65f;
   for (int i = 0; i < 3; i++) {
    float center = s.left + (i + .5f) * laneWidth;
    if (obstacle.left < center + s.player.width() * .58f && obstacle.right > center - s.player.width() * .58f)
     risk[i] = Math.max(risk[i], danger);
   }
  }
  // Never allow swiping directly into a high-risk lane when a safer option exists.
  if (current > 0 && risk[current - 1] >= 0.85f && risk[current] < risk[current - 1]) mask[0] = false;
  if (current < 2 && risk[current + 1] >= 0.85f && risk[current] < risk[current + 1]) mask[1] = false;

  float[] fuelBonus = new float[3];
  for (ArcadeObservation.Box fuel : s.collectibles) {
   int fl = Math.max(0, Math.min(2, (int) ((fuel.x() - s.left) / laneWidth)));
   if (risk[fl] < 0.25f) fuelBonus[fl] = 0.5f;
  }
  f[0] = clip((current - 1.0));
  f[1] = clip(risk[0]);
  f[2] = clip(risk[1]);
  f[3] = clip(risk[2]);
  f[4] = clip(current > 0 ? risk[current] - risk[current - 1] + fuelBonus[current - 1] : -1.0);
  f[5] = clip(current < 2 ? risk[current] - risk[current + 1] + fuelBonus[current + 1] : -1.0);
  f[6] = clip(risk[current]);
  f[7] = clip(s.confidence);
  for (int i = 0; i < Math.min(6, s.obstacles.size()); i++) {
   ArcadeObservation.Box b = s.obstacles.get(i);
   int k = 16 + i * 8;
   f[k] = clip((b.x() - s.player.x()) / Math.max(.1f, s.right - s.left));
   f[k + 1] = clip(s.player.top - b.bottom);
   f[k + 2] = clip(b.width());
   f[k + 3] = clip(b.height());
   f[k + 6] = -1f;
   f[k + 7] = 1f;
  }
  return new EncodedState(TaskSchema.GameId.MARKET_RUSH, f, mask, s.confidence);
 }

 private static EncodedState encodeFlappy(ArcadeObservation s, ArcadeController controller) {
  float[] f = new float[TaskSchema.OBS_DIM];
  // Actions: 0=WAIT, 1=FLAP
  boolean[] mask = new boolean[]{true, true};
  if (!s.usable() || !s.tracked || s.gapBottom - s.gapTop < s.player.height() * 2f) {
   return new EncodedState(TaskSchema.GameId.GOSHAN_FLAPPY, f, new boolean[]{true, false}, 0f);
  }
  float targetY = (s.gapTop + s.gapBottom) * 0.5f - s.player.height() * 0.15f;
  float futureY = s.player.y() + s.vy * 0.22f + 0.5f * 1.2f * 0.22f * 0.22f;
  if (s.player.top < s.top + 0.018f || s.vy < -0.18f) mask[1] = false;
  f[0] = clip((s.player.y() - targetY) * 4.0);
  f[1] = clip((futureY - targetY) * 4.0);
  f[2] = clip(s.vy);
  f[3] = clip(s.gapTop * 2.0 - 1.0);
  f[4] = clip(s.gapBottom * 2.0 - 1.0);
  f[5] = clip((s.bottom - s.player.bottom) * 4.0);
  f[6] = controller != null && controller.decide(s, s.timestampMs).action == ArcadePolicy.Action.FLAP ? 1f : -1f;
  f[7] = clip(s.confidence);
  return new EncodedState(TaskSchema.GameId.GOSHAN_FLAPPY, f, mask, s.confidence);
 }

 /** 7. 2048: encodes 4x4 exponents, monotonicity, empty count, and 4 directional lookaheads. */
 public static EncodedState encode2048(Board2048 board) {
  float[] f = new float[TaskSchema.OBS_DIM];
  boolean[] mask = board == null ? new boolean[4] : Verified2048.mask(board);
  if (board == null || board.max() == 0) {
   return new EncodedState(TaskSchema.GameId.GAME_2048, f, mask, 0f);
  }
  int[] exp = Verified2048.features(board);
  for (int i = 0; i < 16; i++) f[i] = clip(exp[i] / 11.0);
  for (int d = 0; d < 4; d++) {
   int base = 16 + d * 8;
   if (!mask[d]) {
    f[base] = -1f;
    continue;
   }
   Board2048.Move m = board.move(d);
   int empty = 0, max = m.board.max();
   for (int v : m.board.cells) if (v == 0) empty++;
   boolean cornerMax = m.board.cells[0] == max || m.board.cells[3] == max
     || m.board.cells[12] == max || m.board.cells[15] == max;
   f[base] = clip(Math.log(1 + m.reward) / 8.0);
   f[base + 1] = clip(empty / 16.0);
   f[base + 2] = cornerMax ? 1f : -0.5f;
   f[base + 3] = clip(expectimaxHeuristicValue(m.board) / 250.0);
   f[base + 7] = 1f;
  }
  return new EncodedState(TaskSchema.GameId.GAME_2048, f, mask, 0.95f);
 }

 public static double expectimaxHeuristicValue(Board2048 b) {
  double s = 0;
  int empty = 0;
  for (int i = 0; i < 16; i++) {
   int n = b.cells[i];
   if (n == 0) { empty++; continue; }
   double a = Math.log(n) / Math.log(2);
   if (i % 4 < 3 && b.cells[i + 1] > 0) s -= Math.abs(a - Math.log(b.cells[i + 1]) / Math.log(2));
   if (i < 12 && b.cells[i + 4] > 0) s -= Math.abs(a - Math.log(b.cells[i + 4]) / Math.log(2));
  }
  int m = b.max();
  return s + empty * 12.0 + ((b.cells[0] == m || b.cells[3] == m || b.cells[12] == m || b.cells[15] == m) ? 8.0 : 0.0);
 }

 /** 8. Гексон (Hexa Sort): encodes board state + top 8 candidate (traySlot, targetCell) placements. */
 public static EncodedState encodeHexon(HexaBoard board, List<HexaPolicy.Move> candidates) {
  float[] f = new float[TaskSchema.OBS_DIM];
  boolean[] mask = new boolean[TaskSchema.GameId.HEXON.actionCount];
  if (board == null || !board.known()) {
   return new EncodedState(TaskSchema.GameId.HEXON, f, mask, 0f);
  }
  f[0] = clip(board.freeCellCount() / (double) Math.max(1, board.size()));
  f[1] = clip(board.occupiedCellCount() / (double) Math.max(1, board.size()));
  f[2] = clip(board.availableTrayCount() / 3.0);
  f[3] = 0.88f;
  int n = candidates == null ? 0 : Math.min(8, candidates.size());
  for (int i = 0; i < n; i++) {
   HexaPolicy.Move m = candidates.get(i);
   if (m == null) continue;
   mask[i] = true;
   int base = 16 + i * 6;
   f[base] = clip(m.clearedStacks / 3.0);
   f[base + 1] = clip(m.clearedTiles / 15.0);
   f[base + 2] = clip(m.mergedTiles / 10.0);
   f[base + 3] = clip(m.freeCellsAfter / (double) Math.max(1, board.size()));
   f[base + 4] = clip(m.score / 250.0);
   f[base + 5] = (8 - i) / 8.0f;
  }
  return new EncodedState(TaskSchema.GameId.HEXON, f, mask, mask[0] ? 0.88f : 0f);
 }

 /** 10. Котобашня (Cat Tower): encodes climb/stomp and stack-slab states + legal action mask. */
 public static EncodedState encodeKotobashnya(KotobashnyaState s) {
  float[] f = new float[TaskSchema.OBS_DIM];
  // Actions: 0=WAIT, 1=JUMP_UP, 2=JUMP_LEFT, 3=JUMP_RIGHT, 4=STOMP_ENEMY, 5=LOCK_SLAB
  boolean[] mask = new boolean[]{true, false, false, false, false, false};
  if (s == null || !s.usable()) {
   return new EncodedState(TaskSchema.GameId.KOTOBASHNYA, f, mask, 0f);
  }
  if (s.subMode == KotobashnyaState.SubMode.TOWER_STACK_SLAB) {
   f[0] = -1f; // Slab mode indicator
   f[1] = clip(s.movingSlab.x() - s.towerTop.x());
   f[2] = clip(s.slabVx);
   if (s.tracked) {
    float predX = s.movingSlab.x() + s.slabVx * 0.065f;
    float err = Math.abs(predX - s.towerTop.x());
    float tol = Math.max(0.018f, Math.min(s.movingSlab.width(), s.towerTop.width()) * 0.14f);
    if (err <= tol) mask[5] = true;
    f[3] = clip(1.0 - err / Math.max(0.01f, tol));
   }
   return new EncodedState(TaskSchema.GameId.KOTOBASHNYA, f, mask, s.confidence);
  }
  // TOWER_CLIMB_STOMP mode
  f[0] = 1f;
  f[1] = clip(s.player.x() * 2.0 - 1.0);
  f[2] = clip(s.player.y() * 2.0 - 1.0);
  f[3] = clip(s.playerVx);
  f[4] = clip(s.playerVy);
  mask[1] = true;
  mask[2] = true;
  mask[3] = true;
  boolean stompSafe = false;
  for (KotobashnyaState.Enemy e : s.enemies) {
   if (KotobashnyaPolicy.safeStompWindow(s, e)) {
    stompSafe = true;
    break;
   }
  }
  mask[4] = stompSafe;
  KotobashnyaPolicy.Decision d = KotobashnyaPolicy.choose(s);
  f[5] = d.action.ordinal() / 5.0f;
  f[6] = stompSafe ? 1f : 0f;
  f[8] = d.action == KotobashnyaPolicy.Action.JUMP_UP ? 1f : 0f;
  f[9] = d.action == KotobashnyaPolicy.Action.JUMP_LEFT ? 1f : 0f;
  f[10] = d.action == KotobashnyaPolicy.Action.JUMP_RIGHT ? 1f : 0f;
  f[7] = clip(s.confidence);
  int slot = 0;
  for (KotobashnyaState.Enemy e : s.enemies) {
   if (slot >= 3) break;
   int k = 16 + slot * 8;
   f[k] = clip(e.box.x() - s.player.x());
   f[k + 1] = clip(e.box.y() - s.player.y());
   f[k + 2] = clip(e.vx);
   f[k + 6] = KotobashnyaPolicy.safeStompWindow(s, e) ? 1f : -1f;
   f[k + 7] = 1f;
   slot++;
  }
  for (ArcadeObservation.Box p : s.platforms) {
   if (slot >= 6) break;
   int k = 16 + slot * 8;
   f[k] = clip(p.x() - s.player.x());
   f[k + 1] = clip(p.y() - s.player.y());
   f[k + 2] = clip(p.width());
   f[k + 6] = 0.5f;
   f[k + 7] = 1f;
   slot++;
  }
  return new EncodedState(TaskSchema.GameId.KOTOBASHNYA, f, mask, s.confidence);
 }

 /** Enumerates top candidate GridPuzzle moves (sorted by immediate heuristic score) for encoding. */
 public static List<GridPuzzlePolicy.Decision> topGridCandidates(GridPuzzle board,
   GridPuzzlePolicy.Mode mode, int minGroup, int targetType, int targetRemaining, BitSet special, int limit) {
  List<GridPuzzlePolicy.Decision> all = new ArrayList<>();
  if (board == null || !board.known()) return all;
  BitSet excluded = special == null ? new BitSet() : (BitSet) special.clone();
  for (int i = 0; i < Math.max(1, limit); i++) {
   GridPuzzlePolicy.Decision d = GridPuzzlePolicy.choose(board, mode, minGroup, targetType, targetRemaining, excluded);
   if (d == null) break;
   all.add(d);
   excluded.set(d.fromRow * board.cols + d.fromCol);
  }
  return all;
 }

 /** Enumerates top candidate BubblePolicy shots (direct and 1-bounce) for encoding. */
 public static List<BubblePolicy.Shot> topBubbleCandidates(BubbleBoard board) {
  List<BubblePolicy.Shot> out = new ArrayList<>();
  if (board == null) return out;
  BubblePolicy.Shot best = BubblePolicy.choose(board);
  if (best != null) out.add(best);
  BubblePolicy.Shot direct = BubblePolicy.chooseDirect(board);
  if (direct != null && (best == null || direct.landing != best.landing || direct.bounces != best.bounces)) {
   out.add(direct);
  }
  return out;
 }

 /** Enumerates top candidate HexaPolicy moves for encoding. */
 public static List<HexaPolicy.Move> topHexaCandidates(HexaBoard board, int limit) {
  List<HexaPolicy.Move> list = new ArrayList<>();
  if (board == null || !board.known() || board.freeCellCount() == 0) return list;
  for (int slot = 0; slot < HexaBoard.TRAY_SLOTS; slot++) {
   HexaBoard.Stack hand = board.traySlot(slot);
   if (hand.topColor <= 0) continue;
   for (int cell = 0; cell < board.size(); cell++) {
    if (!board.cell(cell).isEmpty()) continue;
    HexaBoard.PlacementOutcome outcome = board.simulatePlace(slot, cell);
    if (outcome == null) continue;
    double score = outcome.clearedStacks * 120.0 + outcome.clearedTiles * 14.0
      + outcome.mergedTiles * 18.0 + outcome.chainSteps * 10.0 + outcome.freeCellsAfter * 6.0;
    list.add(new HexaPolicy.Move(slot, cell, board.cols,
      outcome.mergedTiles, outcome.clearedStacks, outcome.clearedTiles, outcome.freeCellsAfter,
      score, outcome.boardAfter));
   }
  }
  list.sort(Comparator.comparingDouble((HexaPolicy.Move m) -> -m.score));
  return list.size() <= limit ? list : new ArrayList<>(list.subList(0, limit));
 }

 public static float paddleDefeatMargin(ArcadeObservation s) {
  if (s == null || s.profile != ArcadeProfile.PADDLE || s.control == null) return 1f;
  float defeatY = s.control.top - 0.03f;
  if (s.obstacles.isEmpty()) return Math.max(0f, defeatY - 0.07f);
  float lowestBottom = 0f;
  for (ArcadeObservation.Box b : s.obstacles) lowestBottom = Math.max(lowestBottom, b.bottom);
  return Math.max(0f, defeatY - lowestBottom);
 }

 public static float urgentBlockTargetX(ArcadeObservation s) {
  if (s == null || s.profile != ArcadeProfile.PADDLE || s.obstacles.isEmpty()) return 0.5f;
  ArcadeObservation.Box best = null;
  for (ArcadeObservation.Box b : s.obstacles) {
   if (best == null || b.bottom > best.bottom + 1e-4f
     || (Math.abs(b.bottom - best.bottom) <= 1e-4f && Math.abs(b.x() - 0.5f) < Math.abs(best.x() - 0.5f))) {
    best = b;
   }
  }
  return best == null ? 0.5f : best.x();
 }

 /** 11–16. Retro Consoles (NES, DOS, SEGA): encodes active CRT scene & 8 controller action slots. */
 public static EncodedState encodeRetro(TaskSchema.GameId gameId, RetroVision.RetroScene s) {
  if (gameId == null || !gameId.isRetroConsole()) {
   throw new IllegalArgumentException("encodeRetro requires a retro console GameId");
  }
  float[] f = new float[TaskSchema.OBS_DIM];
  boolean[] mask = new boolean[gameId.actionCount];
  if (s == null || !s.usable()) {
   return new EncodedState(gameId, f, mask, 0f);
  }
  f[0] = s.archetype.console == RetroPlatform.Console.NES ? -0.5f
    : (s.archetype.console == RetroPlatform.Console.DOS ? 0.0f : 0.5f);
  f[1] = clip(s.playerX * 2.0 - 1.0);
  f[2] = clip(s.playerY * 2.0 - 1.0);
  f[3] = clip(s.playerVx * 5.0);
  f[4] = clip(s.playerVy * 5.0);
  f[5] = s.onGround ? 1f : 0f;
  f[6] = s.pitAhead ? 1f : clip(1.0 - s.nearestProjectileTtc);
  f[7] = clip(s.nearestEnemyDx);
  f[8] = clip(s.nearestEnemyDy);
  f[9] = clip(s.nearestCollectibleDx);
  f[10] = clip(s.hudVitalityRatio);
  f[11] = clip(s.hudScoreProxy / 50.0);
  f[12] = s.spinDashOrSpecialReady ? 1f : 0f;
  f[13] = clip(s.activeEnemyCount / 4.0);
  f[14] = clip(s.confidence);
  f[15] = clip(s.archetype.ordinal() / 5.0);

  for (int a = 0; a < 8; a++) {
   mask[a] = s.legalMask[a];
   int base = 16 + a * 6;
   for (int k = 0; k < 6; k++) {
    f[base + k] = clip(s.candidateFeatures[a][k]);
   }
  }
  return new EncodedState(gameId, f, mask, s.confidence);
 }
}
