package org.localpilot.android;

/**
 * Two-frame stability and post-ACK visual verification gate for "Гексон" (Hexa Sort).
 * Android ACK never counts as a verified move; verification requires two fresh post-ACK
 * frames showing that the dragged tray stack was placed onto the target hex cell.
 */
public final class HexaSession {
 public enum Status { WAIT_STABLE, READY, WAIT_RESPONSE, VERIFIED, PAUSED }
 private static final long FRESH_MS = 300, STABLE_MS = 80, SETTLE_MS = 180;
 private final long responseTimeoutMs;
 private Status status = Status.WAIT_STABLE;
 private HexaBoard candidate, stable, before;
 private HexaPolicy.Move pendingMove;
 private long candidateAt = -1, lastFrameAt = -1, dispatchedAt = -1, ackAt = -1;
 private int verifiedMoves;

 public HexaSession() { this(3500); }
 public HexaSession(long responseTimeoutMs) {
  if (responseTimeoutMs < 1000 || responseTimeoutMs > 10000)
   throw new IllegalArgumentException("invalid hexa response timeout");
  this.responseTimeoutMs = responseTimeoutMs;
 }

 public void reset() {
  status = Status.WAIT_STABLE;
  candidate = stable = before = null;
  pendingMove = null;
  candidateAt = lastFrameAt = dispatchedAt = ackAt = -1;
  verifiedMoves = 0;
 }

 public Status status() { return status; }
 public boolean pending() { return before != null; }
 public boolean acknowledged() { return pending() && ackAt >= 0; }
 public boolean paused() { return status == Status.PAUSED; }
 public int verifiedMoves() { return verifiedMoves; }

 public Status observe(HexaBoard board, long frameAt, long now) {
  if (paused()) return status;
  if (pending() && now >= dispatchedAt && now - dispatchedAt >= responseTimeoutMs) {
   candidate = null; candidateAt = -1;
   status = Status.PAUSED;
   return status;
  }
  if (board == null || !board.known() || frameAt < 0 || now < frameAt || now - frameAt > FRESH_MS || frameAt <= lastFrameAt) {
   candidate = null; candidateAt = -1;
   status = pending() ? Status.WAIT_RESPONSE : Status.WAIT_STABLE;
   return status;
  }
  lastFrameAt = frameAt;
  if (pending() && (ackAt < 0 || frameAt <= ackAt || frameAt - dispatchedAt < SETTLE_MS)) {
   candidate = null; candidateAt = -1;
   status = Status.WAIT_RESPONSE;
   return status;
  }
  if (candidate == null || !candidate.same(board)) {
   candidate = board;
   candidateAt = frameAt;
   status = pending() ? Status.WAIT_RESPONSE : Status.WAIT_STABLE;
   return status;
  }
  if (frameAt - candidateAt < STABLE_MS) {
   status = pending() ? Status.WAIT_RESPONSE : Status.WAIT_STABLE;
   return status;
  }
  if (pending()) {
   if (before.same(board)) {
    status = Status.WAIT_RESPONSE;
    return status;
   }
   // Verify that either the exact expected board appeared (or with new tray refill)
   // and the target hex cell changed from empty.
   boolean targetChanged = !board.cell(pendingMove.targetIndex).equals(before.cell(pendingMove.targetIndex))
     || pendingMove.clearedStacks > 0;
   boolean trayChanged = !board.traySlot(pendingMove.traySlot).equals(before.traySlot(pendingMove.traySlot))
     || board.occupiedCellCount() != before.occupiedCellCount();
   if (!targetChanged || !trayChanged) {
    status = Status.WAIT_RESPONSE;
    return status;
   }
   stable = board;
   before = null;
   pendingMove = null;
   dispatchedAt = ackAt = -1;
   verifiedMoves++;
   status = Status.VERIFIED;
   return status;
  }
  stable = board;
  status = Status.READY;
  return status;
 }

 public void sent(HexaBoard board, HexaPolicy.Move move, long now) {
  if (paused() || pending() || (status != Status.READY && status != Status.VERIFIED)
    || stable == null || !stable.same(board) || move == null || now < lastFrameAt || now - lastFrameAt > FRESH_MS)
   throw new IllegalStateException("fresh stable hexa board required before dispatch");
  before = board;
  pendingMove = move;
  dispatchedAt = now;
  ackAt = -1;
  candidate = null;
  candidateAt = -1;
  status = Status.WAIT_RESPONSE;
 }

 public void cancelUnsent() {
  if (!pending() || ackAt >= 0) throw new IllegalStateException("cannot cancel acknowledged hexa move");
  before = null;
  pendingMove = null;
  dispatchedAt = ackAt = -1;
  candidate = stable = null;
  candidateAt = -1;
  status = Status.WAIT_STABLE;
 }

 public void acknowledge(long now) {
  if (!pending() || paused()) return;
  if (now < dispatchedAt) throw new IllegalArgumentException("ACK precedes dispatch");
  if (ackAt < 0) {
   ackAt = now;
   candidate = null;
   candidateAt = -1;
  }
 }

 public String report() {
  return "HEXA_SESSION: status=" + status + " pending=" + pending() + " android_ack=" + acknowledged()
    + " verified_moves=" + verifiedMoves + " timeout_ms=" + responseTimeoutMs;
 }
}
