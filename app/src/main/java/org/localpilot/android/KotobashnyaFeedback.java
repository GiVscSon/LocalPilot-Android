package org.localpilot.android;

/**
 * Post-ACK visual verification gate for "Котобашня" (Cat Tower).
 * Never treats Android ACK as a successful jump, stomp, or slab lock.
 */
public final class KotobashnyaFeedback {
 public enum State { READY, WAITING, VERIFIED, RETRY, PAUSED }
 private static final long RESPONSE_TIMEOUT_MS = 550;
 private KotobashnyaPolicy.Decision pending;
 private KotobashnyaState before;
 private long serial, token, dispatchedAt, verified, timeouts;
 private boolean acknowledged, paused;
 private int failures;

 public void reset() {
  serial++; token = 0;
  pending = null; before = null;
  acknowledged = paused = false;
  failures = 0; verified = timeouts = 0;
 }

 public long dispatched(KotobashnyaPolicy.Decision action, KotobashnyaState state, long now) {
  if (action == null || !action.sendsInput() || state == null || !state.usable() || paused || pending != null)
   throw new IllegalStateException("invalid kotobashnya dispatch context");
  pending = action;
  before = state;
  dispatchedAt = now;
  acknowledged = false;
  token = ++serial;
  return token;
 }

 public boolean acknowledge(long id) {
  if (id != token || pending == null) return false;
  acknowledged = true;
  return true;
 }

 public State observe(KotobashnyaState after, long now) {
  if (paused) return State.PAUSED;
  if (pending == null) return State.READY;
  boolean fresh = acknowledged && after != null && after.usable()
    && after.subMode == before.subMode
    && after.timestampMs > dispatchedAt && after.timestampMs - dispatchedAt <= RESPONSE_TIMEOUT_MS
    && after.timestampMs <= now && now - after.timestampMs <= 300;
  if (fresh && verifiedResponse(pending, before, after)) {
   verified++;
   failures = 0;
   pending = null; before = null; token = 0;
   return State.VERIFIED;
  }
  if (now < dispatchedAt || now - dispatchedAt < RESPONSE_TIMEOUT_MS) return State.WAITING;
  timeouts++;
  failures++;
  pending = null; before = null; token = 0;
  if (failures >= 3) {
   paused = true;
   return State.PAUSED;
  }
  return State.RETRY;
 }

 public static boolean verifiedResponse(KotobashnyaPolicy.Decision d, KotobashnyaState before, KotobashnyaState after) {
  if (d == null || before == null || after == null || !after.usable() || before.subMode != after.subMode)
   return false;
  switch (d.action) {
   case LOCK_SLAB:
    return before.towerTop != null && after.towerTop != null
      && (after.towerTop.top < before.towerTop.top - 0.015f
        || Math.abs(after.movingSlab.y() - before.movingSlab.y()) > 0.02f);
   case STOMP_ENEMY:
    return after.enemies.size() < before.enemies.size()
      || (after.player != null && before.player != null && after.playerVy < -0.05f);
   case JUMP_UP:
    return after.player != null && before.player != null
      && (after.player.y() < before.player.y() - 0.015f || after.playerVy < -0.05f);
   case JUMP_LEFT:
    return after.player != null && before.player != null
      && after.player.x() < before.player.x() - 0.015f;
   case JUMP_RIGHT:
    return after.player != null && before.player != null
      && after.player.x() > before.player.x() + 0.015f;
   default:
    return false;
  }
 }

 public void cancel(long id) {
  if (id == token) {
   pending = null; before = null; token = 0; acknowledged = false;
  }
 }

 public boolean paused() { return paused; }
 public long verified() { return verified; }
 public String report() {
  return "KOTOBASHNYA_FEEDBACK: pending=" + (pending == null ? "none" : pending.action)
    + " ack=" + acknowledged + " verified=" + verified + " failures=" + failures + " paused=" + paused;
 }
}
