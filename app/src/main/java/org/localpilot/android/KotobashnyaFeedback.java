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
 private long serial, token, dispatchedAt, acknowledgedAt=-1, verified, timeouts;
 private boolean acknowledged, paused;
 private int failures;

 public void reset() {
  serial++; token = 0;
  pending = null; before = null;
  acknowledged = paused = false;acknowledgedAt=-1;
  failures = 0; verified = timeouts = 0;
 }

 public long dispatched(KotobashnyaPolicy.Decision action, KotobashnyaState state, long now) {
  if (action == null || !action.sendsInput() || state == null || !state.usable() || paused || pending != null
    ||now<state.timestampMs||now-state.timestampMs>300)
   throw new IllegalStateException("invalid kotobashnya dispatch context");
  pending = action;
  before = state;
  dispatchedAt = now;
  acknowledged = false;acknowledgedAt=-1;
  token = ++serial;
  return token;
 }

 public boolean acknowledge(long id,long now) {
  if (id != token || pending == null || now<dispatchedAt || acknowledged) return false;
  acknowledged = true;acknowledgedAt=now;
  return true;
 }

 public State observe(KotobashnyaState after, long now) {
  if (paused) return State.PAUSED;
  if (pending == null) return State.READY;
  boolean fresh = acknowledged && after != null && after.usable()
    && after.subMode == before.subMode
    && after.timestampMs > acknowledgedAt && after.timestampMs - dispatchedAt <= RESPONSE_TIMEOUT_MS
    && after.timestampMs <= now && now - after.timestampMs <= 300;
  if (fresh && verifiedResponse(pending, before, after)) {
   verified++;
   failures = 0;
   pending = null; before = null; token = 0;
   return State.VERIFIED;
  }
  if (now < dispatchedAt || now - dispatchedAt < RESPONSE_TIMEOUT_MS) return State.WAITING;
  timeouts++;
  // No ACK is never permission to try another action. Missing delivery proof
  // must revoke this feedback session instead of returning a retry proposal.
  if(!acknowledged){paused=true;return State.PAUSED;}
  failures++;
  pending = null; before = null; token = 0;
  if (failures >= 3) {
   paused = true;
   return State.PAUSED;
  }
  return State.RETRY;
 }

 public static boolean verifiedResponse(KotobashnyaPolicy.Decision d, KotobashnyaState before, KotobashnyaState after) {
  if (d == null || before == null || after == null || !before.usable() || !after.usable()
    ||!after.tracked||after.timestampMs<=before.timestampMs||before.subMode != after.subMode)
   return false;
  switch (d.action) {
   case LOCK_SLAB:
    if(before.movingSlab==null||after.movingSlab==null||before.towerTop==null||after.towerTop==null)return false;
    float towerShift=after.towerTop.top-before.towerTop.top;
    float slabShift=after.movingSlab.top-before.movingSlab.top;
    float overlap=Math.max(0,Math.min(before.movingSlab.right,after.towerTop.right)
      -Math.max(before.movingSlab.left,after.towerTop.left));
    return towerShift<-.015f&&towerShift-slabShift<-.015f
      &&overlap>=after.towerTop.width()*.7f
      &&after.towerTop.width()<=before.towerTop.width()+.02f;
   case STOMP_ENEMY:
    if(after.player==null||before.player==null||after.enemies.size()>=before.enemies.size()
      ||after.playerVy>=-.05f||after.player.y()>=before.player.y()-.015f)return false;
    for(KotobashnyaState.Enemy enemy:before.enemies)
     if(KotobashnyaPolicy.safeStompWindow(before,enemy)){
      boolean stillVisible=false;
      for(KotobashnyaState.Enemy seen:after.enemies)
       if(Math.abs(enemy.box.x()-seen.box.x())<.08f&&Math.abs(enemy.box.y()-seen.box.y())<.08f)
        stillVisible=true;
      if(!stillVisible)return true;
     }
    return false;
   case JUMP_UP:
    return after.player != null && before.player != null
      && after.player.y() < before.player.y() - 0.015f && after.playerVy < -0.05f;
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
   pending = null; before = null; token = 0; acknowledged = false;acknowledgedAt=-1;
  }
 }

 public boolean paused() { return paused; }
 public long verified() { return verified; }
 public String report() {
  return "KOTOBASHNYA_FEEDBACK: pending=" + (pending == null ? "none" : pending.action)
    + " ack=" + acknowledged + " verified=" + verified + " failures=" + failures + " paused=" + paused;
 }
}
