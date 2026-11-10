package org.localpilot.android.learning;

import java.util.ArrayList;
import java.util.List;
import org.localpilot.android.rl.ArcadeEnv;

/**
 * Segregated experience replay store for SIM (synthetic curriculum), SHADOW (passive real observation),
 * and REAL (post-ACK visually verified real-game transitions).
 * Critical invariant: Android ACK alone can NEVER insert a REAL transition with positive/zero reward;
 * unverified outcomes are marked RewardStatus.UNKNOWN and excluded from reward learning.
 */
public final class ReplayStore {
 public static final class Transition {
  public final TaskSchema schema;
  public final float[] state;
  public final boolean[] legalMask;
  public final int action;
  public final float reward;
  public final TaskSchema.RewardStatus rewardStatus;
  public final float[] nextState;
  public final boolean done;
  public final TaskSchema.Provenance provenance;
  public final float confidence;
  public final boolean androidAck;
  public final boolean visualVerified;
  public final long timestampMs;

  public Transition(TaskSchema schema, float[] state, boolean[] legalMask, int action,
    float reward, TaskSchema.RewardStatus rewardStatus, float[] nextState, boolean done,
    TaskSchema.Provenance provenance, float confidence,
    boolean androidAck, boolean visualVerified, long timestampMs) {
   if (schema == null || state == null || state.length != TaskSchema.OBS_DIM
     || legalMask == null || legalMask.length != schema.gameId.actionCount
     || nextState == null || nextState.length != TaskSchema.OBS_DIM
     || rewardStatus == null || provenance == null)
    throw new IllegalArgumentException("invalid transition fields");
   // Critical guardrail: REAL measured rewards require BOTH Android ACK and post-ACK visual proof!
   if (provenance == TaskSchema.Provenance.REAL && rewardStatus == TaskSchema.RewardStatus.MEASURED
     && (!androidAck || !visualVerified)) {
    throw new IllegalArgumentException("REAL measured reward requires both Android ACK and post-ACK visual verification");
   }
   this.schema = schema;
   this.state = state.clone();
   this.legalMask = legalMask.clone();
   this.action = action;
   this.reward = rewardStatus == TaskSchema.RewardStatus.MEASURED ? reward : Float.NaN;
   this.rewardStatus = rewardStatus;
   this.nextState = nextState.clone();
   this.done = done;
   this.provenance = provenance;
   this.confidence = confidence;
   this.androidAck = androidAck;
   this.visualVerified = visualVerified;
   this.timestampMs = timestampMs;
  }

  public boolean usableForTraining() {
   return rewardStatus == TaskSchema.RewardStatus.MEASURED
     && Float.isFinite(reward)
     && action >= 0 && action < legalMask.length && legalMask[action]
     && (provenance == TaskSchema.Provenance.SIM || (provenance == TaskSchema.Provenance.REAL && visualVerified));
  }

  /** Versioned single-line export without raw frame pixels. */
  public String serializeLine() {
   return "LP-TRANSITION-v2|" + schema.scopeKey() + "|" + provenance + "|" + action
     + "|" + rewardStatus + "|" + (Float.isFinite(reward) ? reward : "UNKNOWN")
     + "|done=" + done + "|conf=" + confidence + "|ack=" + androidAck + "|verified=" + visualVerified
     + "|ts=" + timestampMs;
  }
 }

 private final TaskSchema schema;
 private final int capacity;
 private final List<Transition> simBuffer = new ArrayList<>();
 private final List<Transition> shadowBuffer = new ArrayList<>();
 private final List<Transition> realBuffer = new ArrayList<>();
 private int simCursor, shadowCursor, realCursor;
 private long rejectedUnverifiedCount;

 public ReplayStore(TaskSchema schema, int capacityPerStream) {
  if (schema == null || capacityPerStream < 8 || capacityPerStream > 50_000)
   throw new IllegalArgumentException("invalid replay store config");
  this.schema = schema;
  this.capacity = capacityPerStream;
 }

 public TaskSchema schema() { return schema; }
 public synchronized int simSize() { return simBuffer.size(); }
 public synchronized int shadowSize() { return shadowBuffer.size(); }
 public synchronized int realSize() { return realBuffer.size(); }
 public synchronized long rejectedUnverifiedCount() { return rejectedUnverifiedCount; }

 public synchronized boolean add(Transition t) {
  if (t == null || !schema.compatibleWith(t.schema)) {
   throw new IllegalArgumentException("schema mismatch in ReplayStore: cannot mix incompatible games/mechanics");
  }
  if (t.provenance == TaskSchema.Provenance.REAL && (!t.visualVerified || t.rewardStatus == TaskSchema.RewardStatus.UNKNOWN)) {
   rejectedUnverifiedCount++;
   return false;
  }
  switch (t.provenance) {
   case SIM ->simInsert(t);
   case SHADOW -> shadowInsert(t);
   case REAL -> realInsert(t);
  }
  return true;
 }

 private void simInsert(Transition t) {
  if (simBuffer.size() < capacity) simBuffer.add(t);
  else { simBuffer.set(simCursor, t); simCursor = (simCursor + 1) % capacity; }
 }

 private void shadowInsert(Transition t) {
  if (shadowBuffer.size() < capacity) shadowBuffer.add(t);
  else { shadowBuffer.set(shadowCursor, t); shadowCursor = (shadowCursor + 1) % capacity; }
 }

 private void realInsert(Transition t) {
  if (realBuffer.size() < capacity) realBuffer.add(t);
  else { realBuffer.set(realCursor, t); realCursor = (realCursor + 1) % capacity; }
 }

 /**
  * Controlled mixing sampler: prioritizes verified REAL transitions (up to `realRatio`, e.g. 0.70)
  * and fills remaining batch slots from compatible SIM transitions. Never samples SHADOW or UNKNOWN.
  */
 public synchronized List<Transition> sampleMixedBatch(int batchSize, float realRatio, ArcadeEnv.Rng rng) {
  List<Transition> batch = new ArrayList<>(batchSize);
  int targetReal = Math.round(batchSize * Math.max(0f, Math.min(1f, realRatio)));
  for (int i = 0; i < batchSize; i++) {
   boolean pickReal = !realBuffer.isEmpty() && (simBuffer.isEmpty() || i < targetReal);
   if (pickReal) {
    Transition t = realBuffer.get(rng.integer(realBuffer.size()));
    if (t.usableForTraining()) batch.add(t);
   } else if (!simBuffer.isEmpty()) {
    Transition t = simBuffer.get(rng.integer(simBuffer.size()));
    if (t.usableForTraining()) batch.add(t);
   }
  }
  return batch;
 }
}
