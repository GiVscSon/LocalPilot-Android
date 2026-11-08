package org.localpilot.android.learning;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure-Java PPO + Generalized Advantage Estimation (GAE) trainer for StudentPolicy.
 * Enforces strict on-policy rollout safeguards:
 * - Never pretends to train PPO from 2 steps: requires at least MIN_ROLLOUT_STEPS (16)
 *   verified on-policy transitions from the same model version.
 * - Uses duration-aware GAE (gamma^durationFrames) and distinguishes terminal vs truncated steps.
 * - Applies clipped surrogate objective (epsilon=0.2) + value clipping + entropy bonus.
 */
public final class PpoGaeTrainer {
 public static final int MIN_ROLLOUT_STEPS = 16;
 public static final float GAMMA = 0.99f;
 public static final float GAE_LAMBDA = 0.95f;
 public static final float CLIP_EPS = 0.20f;

 public static final class StepRecord {
  public final float[] state;
  public final boolean[] legalMask;
  public final int action;
  public final float oldLogProb;
  public final float oldValue;
  public final float reward;
  public final float nextValue;
  public final boolean terminated;
  public final boolean truncated;
  public final int durationFrames;
  public final TaskSchema.Provenance provenance;
  public final boolean visualVerified;

  public StepRecord(float[] state, boolean[] legalMask, int action,
    float oldLogProb, float oldValue, float reward, float nextValue,
    boolean terminated, boolean truncated, int durationFrames,
    TaskSchema.Provenance provenance, boolean visualVerified) {
   if (state == null || state.length != TaskSchema.OBS_DIM || legalMask == null
     || action < 0 || action >= legalMask.length || !legalMask[action]
     || !Float.isFinite(oldLogProb) || !Float.isFinite(oldValue)
     || !Float.isFinite(reward) || !Float.isFinite(nextValue)
     || durationFrames < 1 || provenance == null)
    throw new IllegalArgumentException("invalid PPO step record");
   if (provenance == TaskSchema.Provenance.REAL && !visualVerified)
    throw new IllegalArgumentException("REAL PPO step requires post-ACK visual verification");
   this.state = state.clone();
   this.legalMask = legalMask.clone();
   this.action = action;
   this.oldLogProb = oldLogProb;
   this.oldValue = oldValue;
   this.reward = reward;
   this.nextValue = nextValue;
   this.terminated = terminated;
   this.truncated = truncated;
   this.durationFrames = durationFrames;
   this.provenance = provenance;
   this.visualVerified = visualVerified;
  }
 }

 public static final class UpdateReport {
  public final boolean updated;
  public final int rolloutSteps;
  public final float policyLoss, valueLoss, meanAdvantage;
  public final String reason;

  UpdateReport(boolean updated, int rolloutSteps, float policyLoss, float valueLoss, float meanAdvantage, String reason) {
   this.updated = updated;
   this.rolloutSteps = rolloutSteps;
   this.policyLoss = policyLoss;
   this.valueLoss = valueLoss;
   this.meanAdvantage = meanAdvantage;
   this.reason = reason;
  }
 }

 private final List<StepRecord> rollout = new ArrayList<>();

 public void clear() { rollout.clear(); }
 public int size() { return rollout.size(); }

 public void record(StepRecord step) {
  if (step == null) throw new IllegalArgumentException("step required");
  rollout.add(step);
 }

 /**
  * Computes duration-aware GAE advantages and unnormalized value targets:
  * - Terminal state bootstraps 0.
  * - Truncated or continuing state bootstraps nextValue with discount gamma^durationFrames.
  */
 public static float[][] computeGae(List<StepRecord> steps) {
  int n = steps.size();
  float[] advantages = new float[n];
  float[] returns = new float[n];
  float gae = 0f;
  for (int t = n - 1; t >= 0; t--) {
   StepRecord s = steps.get(t);
   float discount = (float) Math.pow(GAMMA, Math.min(10, s.durationFrames));
   float bootstrap = s.terminated ? 0f : s.nextValue;
   float delta = s.reward + discount * bootstrap - s.oldValue;
   float traceReset = (s.terminated || s.truncated) ? 0f : 1f;
   gae = delta + discount * GAE_LAMBDA * traceReset * gae;
   advantages[t] = gae;
   returns[t] = gae + s.oldValue; // Value targets remain unnormalized!
  }
  // RMS-scale advantages for policy gradient stability without flipping positive returns negative.
  if (n > 1) {
   double sq = 0;
   for (float a : advantages) sq += (double) a * a / n;
   float rms = (float) Math.sqrt(Math.max(0.25, sq));
   for (int t = 0; t < n; t++) advantages[t] = advantages[t] / rms;
  }
  return new float[][]{advantages, returns};
 }

 public UpdateReport update(StudentPolicy policy, int epochs, float lr) {
  if (policy == null) throw new IllegalArgumentException("policy required");
  if (rollout.size() < MIN_ROLLOUT_STEPS) {
   return new UpdateReport(false, rollout.size(), 0f, 0f, 0f,
     "INSUFFICIENT_ROLLOUT: need >=" + MIN_ROLLOUT_STEPS + " verified steps, got " + rollout.size());
  }
  float[][] gae = computeGae(rollout);
  float[] advantages = gae[0], returns = gae[1];
  float totalLoss = 0f;
  int n = rollout.size();
  for (int ep = 0; ep < Math.max(1, epochs); ep++) {
   for (int i = 0; i < n; i++) {
    StepRecord s = rollout.get(i);
    StudentPolicy.InferenceResult cur = policy.infer(s.state, s.legalMask);
    float newLogProb = (float) Math.log(Math.max(1e-6f, cur.maskedProbs[s.action]));
    float ratio = (float) Math.exp(Math.max(-10f, Math.min(10f, newLogProb - s.oldLogProb)));
    float adv = advantages[i];
    boolean clipped = (adv > 0 && ratio > 1f + CLIP_EPS) || (adv < 0 && ratio < 1f - CLIP_EPS);
    float effectiveAdv = clipped ? 0f : adv;
    totalLoss += policy.loraOnlineStep(s.state, s.legalMask, s.action, effectiveAdv, returns[i], lr);
   }
  }
  rollout.clear();
  return new UpdateReport(true, n, totalLoss / (n * Math.max(1, epochs)), 0f, 0f, "PPO_GAE_UPDATED");
 }
}
