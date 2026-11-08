package org.localpilot.android;

/**
 * Policy for "Котобашня" (Cat Tower).
 * Enforces strict physics verification for top-down enemy stomp (playerVy > 0 descending
 * from above enemy top with central hitbox alignment and zero side-contact risk)
 * and timing compensation for stack-slab locking.
 */
public final class KotobashnyaPolicy {
 public enum Action { WAIT, JUMP_UP, JUMP_LEFT, JUMP_RIGHT, STOMP_ENEMY, LOCK_SLAB }

 public static final class Decision {
  public final Action action;
  public final float x, y;
  public final int durationMs;
  public final String reason;

  public Decision(Action action, float x, float y, int durationMs, String reason) {
   if (action == null || durationMs < 40 || durationMs > 240)
    throw new IllegalArgumentException("invalid kotobashnya decision");
   this.action = action;
   this.x = x;
   this.y = y;
   this.durationMs = durationMs;
   this.reason = reason;
  }

  public boolean sendsInput() { return action != Action.WAIT; }
 }

 private KotobashnyaPolicy() {}

 public static Decision choose(KotobashnyaState s) {
  if (s == null || !s.usable()) return new Decision(Action.WAIT, 0.5f, 0.5f, 50, "Ожидаю подтверждённую сцену Котобашни");
  if (s.subMode == KotobashnyaState.SubMode.TOWER_STACK_SLAB) {
   return chooseSlabLock(s);
  }
  return chooseClimbOrStomp(s);
 }

 /**
  * Verifies whether a top-down stomp on `enemy` is physically safe:
  * - State must be tracked across at least 2 frames (`s.tracked == true`).
  * - Cat must be descending (`s.playerVy > 0.04f` in screen Y-down coordinates).
  * - Cat's bottom must be above enemy's top (`player.bottom <= enemy.box.top + 0.035f`).
  * - Predicted horizontal position at contact time must land within central 60% of enemy top.
  */
 public static boolean safeStompWindow(KotobashnyaState s, KotobashnyaState.Enemy enemy) {
  if (s == null || !s.tracked || s.player == null || enemy == null || !enemy.stompableTop) return false;
  if (s.playerVy <= 0.04f) return false; // Not descending
  float dy = enemy.box.top - s.player.bottom;
  if (dy < -0.025f || dy > 0.28f) return false;
  float ttc = Math.max(0.02f, dy / s.playerVy);
  if (ttc > 0.55f) return false;
  float predictedPlayerX = s.player.x() + s.playerVx * ttc;
  float predictedEnemyX = enemy.box.x() + enemy.vx * ttc;
  float safeHalfSpan = Math.max(0.015f, enemy.box.width() * 0.30f);
  return Math.abs(predictedPlayerX - predictedEnemyX) <= safeHalfSpan;
 }

 private static Decision chooseClimbOrStomp(KotobashnyaState s) {
  ArcadeObservation.Box p = s.player;
  // 1. Check immediate side-contact hazard from enemies at the same vertical band.
  for (KotobashnyaState.Enemy e : s.enemies) {
   boolean verticalOverlap = e.box.bottom > p.top - 0.03f && e.box.top < p.bottom + 0.02f;
   float dx = e.box.x() - p.x();
   if (verticalOverlap && Math.abs(dx) < p.width() * 0.85f + e.box.width() * 0.65f) {
    boolean evadeLeft = dx > 0;
    return new Decision(evadeLeft ? Action.JUMP_LEFT : Action.JUMP_RIGHT,
      evadeLeft ? 0.24f : 0.76f, 0.68f, 95, "Уход от бокового столкновения с врагом");
   }
  }
  // 2. Check verified top-down stomp opportunity when descending above an enemy.
  for (KotobashnyaState.Enemy e : s.enemies) {
   if (safeStompWindow(s, e)) {
    float dx = (e.box.x() + e.vx * 0.12f) - p.x();
    if (Math.abs(dx) <= 0.03f) {
     return new Decision(Action.STOMP_ENEMY, p.x(), 0.65f, 55, "Подтверждённый прыжок сверху на врага (stomp)");
    }
    return new Decision(dx < 0 ? Action.JUMP_LEFT : Action.JUMP_RIGHT,
      dx < 0 ? 0.28f : 0.72f, 0.65f, 80, "Коррекция траектории для stomp сверху");
   }
  }
  // 3. Find the best reachable higher platform that is free of side hazards.
  ArcadeObservation.Box bestPlatform = null;
  float bestScore = Float.MAX_VALUE;
  for (ArcadeObservation.Box plat : s.platforms) {
   float dy = p.bottom - plat.top;
   if (dy < 0.04f || dy > 0.36f) continue;
   float dx = plat.x() - p.x();
   if (Math.abs(dx) > 0.42f) continue;
   boolean enemyOnPlatform = false;
   for (KotobashnyaState.Enemy e : s.enemies) {
    if (Math.abs(e.box.bottom - plat.top) < 0.05f && Math.abs(e.box.x() - plat.x()) < plat.width() * 0.55f) {
     if (!safeStompWindow(s, e)) { enemyOnPlatform = true; break; }
    }
   }
   if (enemyOnPlatform) continue;
   float score = dy * 0.6f + Math.abs(dx) * 0.4f;
   if (score < bestScore) { bestScore = score; bestPlatform = plat; }
  }
  if (bestPlatform == null) {
   return new Decision(Action.WAIT, 0.5f, 0.5f, 50, "Ожидаю безопасную платформу выше");
  }
  float dx = bestPlatform.x() - p.x();
  if (Math.abs(dx) <= 0.045f) {
   return new Decision(Action.JUMP_UP, 0.5f, 0.68f, 65, "Прыжок на верхний этаж башни");
  }
  return new Decision(dx < 0 ? Action.JUMP_LEFT : Action.JUMP_RIGHT,
    dx < 0 ? 0.25f : 0.75f, 0.68f, 95, "Прыжок к платформе " + (dx < 0 ? "слева" : "справа"));
 }

 private static Decision chooseSlabLock(KotobashnyaState s) {
  if (!s.tracked) return new Decision(Action.WAIT, 0.5f, 0.5f, 50, "Измеряю скорость движущегося блока");
  float latencySec = 0.065f;
  float predictedX = s.movingSlab.x() + s.slabVx * latencySec;
  float alignmentError = Math.abs(predictedX - s.towerTop.x());
  float maxTolerance = Math.min(s.movingSlab.width(), s.towerTop.width()) * 0.14f;
  if (alignmentError <= Math.max(0.018f, maxTolerance)) {
   return new Decision(Action.LOCK_SLAB, 0.5f, 0.55f, 50, "Фиксация блока башни при максимальном перекрытии");
  }
  return new Decision(Action.WAIT, 0.5f, 0.5f, 50, "Ожидаю совмещения блока с вершиной башни");
 }
}
