package org.localpilot.android.learning;

import java.io.*;
import java.util.Arrays;
import org.localpilot.android.rl.ArcadeEnv;

/**
 * Compact pure-Java neural policy & value network for Android on-device inference and adaptation.
 * Architecture:
 * - Shared object trunk: h1 = ReLU(W1 * x + b1)                   [64 -> 64]
 * - Genre representation + LoRA (rank r=4):
 *     h2 = ReLU((W2 + loraScale * A * B) * h1 + b2)               [64 -> 32]
 * - Game policy head: logits = Wp * h2 + bp                       [32 -> actionCount]
 * - Game value head:  value  = Wv * h2 + bv                       [32 -> 1]
 */
public final class StudentPolicy {
 public static final int IN = TaskSchema.OBS_DIM;
 public static final int H1 = 64;
 public static final int H2 = 32;
 public static final int LORA_RANK = 4;

 public static final class InferenceResult {
  public final int selectedAction;
  public final float confidence;
  public final float valueEstimate;
  public final float[] maskedProbs;
  public final float[] rawLogits;
  public final boolean constructedWeights;
  public final long gradientSteps;

  InferenceResult(int selectedAction, float confidence, float valueEstimate,
    float[] maskedProbs, float[] rawLogits, boolean constructedWeights, long gradientSteps) {
   this.selectedAction = selectedAction;
   this.confidence = confidence;
   this.valueEstimate = valueEstimate;
   this.maskedProbs = maskedProbs;
   this.rawLogits = rawLogits;
   this.constructedWeights = constructedWeights;
   this.gradientSteps = gradientSteps;
  }
 }

 public final TaskSchema.GameId gameId;
 private final int actions;
 private final float[] w1 = new float[IN * H1];
 private final float[] b1 = new float[H1];
 private final float[] w2 = new float[H1 * H2];
 private final float[] b2 = new float[H2];
 // Low-rank adapter A [H1 x LORA_RANK], B [LORA_RANK x H2]
 private final float[] loraA = new float[H1 * LORA_RANK];
 private final float[] loraB = new float[LORA_RANK * H2];
 private float loraAlpha = 0.5f;
 private final float[] wp;
 private final float[] bp;
 private final float[] wv = new float[H2];
 private float bv;
 private boolean constructed;
 private long gradientSteps;

 public StudentPolicy(TaskSchema.GameId gameId, long seed, boolean randomInit) {
  if (gameId == null) throw new IllegalArgumentException("gameId required");
  this.gameId = gameId;
  this.actions = gameId.actionCount;
  this.wp = new float[H2 * actions];
  this.bp = new float[actions];
  if (randomInit) {
   ArcadeEnv.Rng rng = new ArcadeEnv.Rng(seed == 0 ? 0x5eed1234L : seed);
   float s1 = (float) Math.sqrt(2.0 / IN);
   float s2 = (float) Math.sqrt(2.0 / H1);
   float sp = (float) Math.sqrt(1.0 / H2);
   for (int i = 0; i < w1.length; i++) w1[i] = (rng.unit() * 2f - 1f) * s1;
   for (int i = 0; i < w2.length; i++) w2[i] = (rng.unit() * 2f - 1f) * s2;
   for (int i = 0; i < loraA.length; i++) loraA[i] = (rng.unit() * 2f - 1f) * 0.05f;
   // loraB starts at zero as in standard LoRA initialization
   for (int i = 0; i < wp.length; i++) wp[i] = (rng.unit() * 2f - 1f) * sp;
   for (int i = 0; i < wv.length; i++) wv[i] = (rng.unit() * 2f - 1f) * sp;
  } else {
   ArcadeEnv.Rng rng = new ArcadeEnv.Rng(0x20261104L + gameId.ordinal());
   for (int i = 0; i < loraA.length; i++) loraA[i] = (rng.unit() * 2f - 1f) * 0.08f;
  }
 }

 public int actionCount() { return actions; }
 public boolean isConstructed() { return constructed; }
 public long gradientSteps() { return gradientSteps; }
 void markConstructed() { this.constructed = true; }

 public float w1(int in, int h1) { return w1[in * H1 + h1]; }
 public float w2(int h1, int h2) { return w2[h1 * H2 + h2]; }
 void setW1(int in, int h1, float val) { w1[in * H1 + h1] = val; }
 void setB1(int h1, float val) { b1[h1] = val; }
 void setW2(int h1, int h2, float val) { w2[h1 * H2 + h2] = val; }
 void setB2(int h2, float val) { b2[h2] = val; }
 void setWp(int h2, int act, float val) { wp[h2 * actions + act] = val; }
 public void addWp(int h2, int act, float delta) { wp[h2 * actions + act] += delta; }
 void setBp(int act, float val) { bp[act] = val; }
 void setWv(int h2, float val) { wv[h2] = val; }

 public int parameterCount() {
  return w1.length + b1.length + w2.length + b2.length + loraA.length + loraB.length + wp.length + bp.length + wv.length + 1;
 }

 public StudentPolicy copy() {
  StudentPolicy c = new StudentPolicy(gameId, 1L, false);
  c.copyFrom(this);
  return c;
 }

 public void copyFrom(StudentPolicy other) {
  if (other == null || other.gameId != this.gameId)
   throw new IllegalArgumentException("incompatible student policy");
  System.arraycopy(other.w1, 0, w1, 0, w1.length);
  System.arraycopy(other.b1, 0, b1, 0, b1.length);
  System.arraycopy(other.w2, 0, w2, 0, w2.length);
  System.arraycopy(other.b2, 0, b2, 0, b2.length);
  System.arraycopy(other.loraA, 0, loraA, 0, loraA.length);
  System.arraycopy(other.loraB, 0, loraB, 0, loraB.length);
  this.loraAlpha = other.loraAlpha;
  System.arraycopy(other.wp, 0, wp, 0, wp.length);
  System.arraycopy(other.bp, 0, bp, 0, bp.length);
  System.arraycopy(other.wv, 0, wv, 0, wv.length);
  this.bv = other.bv;
  this.constructed = other.constructed;
  this.gradientSteps = other.gradientSteps;
 }

 private float[][] forwardInternal(float[] x) {
  if (x == null || x.length != IN) throw new IllegalArgumentException("input dimension must be 64");
  float[] h1 = new float[H1];
  for (int j = 0; j < H1; j++) {
   float z = b1[j];
   for (int i = 0; i < IN; i++) z += x[i] * w1[i * H1 + j];
   h1[j] = Math.max(0f, z);
  }
  float[] loraMid = new float[LORA_RANK];
  for (int r = 0; r < LORA_RANK; r++) {
   float s = 0f;
   for (int i = 0; i < H1; i++) s += h1[i] * loraA[i * LORA_RANK + r];
   loraMid[r] = s;
  }
  float[] h2 = new float[H2];
  for (int j = 0; j < H2; j++) {
   float z = b2[j];
   for (int i = 0; i < H1; i++) z += h1[i] * w2[i * H2 + j];
   for (int r = 0; r < LORA_RANK; r++) z += loraAlpha * loraMid[r] * loraB[r * H2 + j];
   h2[j] = Math.max(0f, z);
  }
  float[] logits = new float[actions];
  for (int a = 0; a < actions; a++) {
   float z = bp[a];
   for (int j = 0; j < H2; j++) z += h2[j] * wp[j * actions + a];
   logits[a] = z;
  }
  float val = bv;
  for (int j = 0; j < H2; j++) val += h2[j] * wv[j];
  return new float[][]{h1, h2, logits, new float[]{val}, loraMid};
 }

 public float[] hiddenLayer2(float[] x) {
  return forwardInternal(x)[1];
 }

 public InferenceResult infer(ObservationEncoder.EncodedState state) {
  if (state == null || state.gameId != gameId)
   throw new IllegalArgumentException("mismatched encoded state");
  return infer(state.features, state.legalMask);
 }

 public InferenceResult infer(float[] features, boolean[] legalMask) {
  if (legalMask == null || legalMask.length != actions)
   throw new IllegalArgumentException("invalid legalMask");
  float[][] fwd = forwardInternal(features);
  float[] logits = fwd[2];
  float value = fwd[3][0];
  float[] probs = maskedSoftmax(logits, legalMask, 1.0f);
  int best = -1;
  float bestProb = -1f;
  for (int a = 0; a < actions; a++) {
   if (legalMask[a] && probs[a] > bestProb) {
    bestProb = probs[a];
    best = a;
   }
  }
  return new InferenceResult(best, Math.max(0f, bestProb), value, probs, logits.clone(), constructed, gradientSteps);
 }

 public static float[] maskedSoftmax(float[] logits, boolean[] legalMask, float temperature) {
  float temp = Math.max(0.05f, temperature);
  float max = -Float.MAX_VALUE;
  int legalCount = 0;
  for (int i = 0; i < logits.length; i++) {
   if (legalMask[i]) {
    max = Math.max(max, logits[i] / temp);
    legalCount++;
   }
  }
  float[] probs = new float[logits.length];
  if (legalCount == 0) return probs;
  double sum = 0;
  for (int i = 0; i < logits.length; i++) {
   if (legalMask[i]) {
    double e = Math.exp(Math.max(-30.0, Math.min(30.0, (logits[i] / temp) - max)));
    probs[i] = (float) e;
    sum += e;
   }
  }
  if (sum <= 0) {
   for (int i = 0; i < logits.length; i++) if (legalMask[i]) probs[i] = 1f / legalCount;
  } else {
   for (int i = 0; i < logits.length; i++) probs[i] /= (float) sum;
  }
  return probs;
 }

 /**
  * Knowledge Distillation + Value Regression step from a TeacherPolicy distribution.
  * Minimizes KL(p_teacher || p_student) + 0.5 * (V(s) - targetValue)^2.
  */
 public float distillStep(float[] x, boolean[] legalMask, float[] teacherProbs, float targetValue, float lr) {
  if (teacherProbs == null || teacherProbs.length != actions || !Float.isFinite(lr) || lr <= 0f)
   throw new IllegalArgumentException("invalid distill parameters");
  float[][] fwd = forwardInternal(x);
  float[] h1 = fwd[0], h2 = fwd[1], logits = fwd[2], loraMid = fwd[4];
  float value = fwd[3][0];
  float[] studentProbs = maskedSoftmax(logits, legalMask, 1.0f);

  float[] dLogits = new float[actions];
  float klLoss = 0f;
  for (int a = 0; a < actions; a++) {
   if (!legalMask[a]) continue;
   float tp = Math.max(0f, teacherProbs[a]);
   float sp = Math.max(1e-6f, studentProbs[a]);
   dLogits[a] = studentProbs[a] - tp;
   if (tp > 1e-6f) klLoss += tp * (float) Math.log(tp / sp);
  }
  float valError = Math.max(-2f, Math.min(2f, value - targetValue));
  bv -= lr * 0.5f * valError;
  for (int j = 0; j < H2; j++) {
   wv[j] -= lr * 0.5f * h2[j] * valError;
  }
  // Check if current argmax already matches teacher argmax; when constructed=true,
  // only apply residual LoRA policy gradient on states where student disagrees with teacher!
  int studentBest = -1, teacherBest = -1;
  float sMax = -1f, tMax = -1f;
  for (int a = 0; a < actions; a++) {
   if (!legalMask[a]) continue;
   if (studentProbs[a] > sMax) { sMax = studentProbs[a]; studentBest = a; }
   if (teacherProbs[a] > tMax) { tMax = teacherProbs[a]; teacherBest = a; }
  }
  if (!constructed || studentBest != teacherBest) {
   float[] dh2 = new float[H2];
   for (int a = 0; a < actions; a++) {
    float dl = Math.max(-1f, Math.min(1f, dLogits[a]));
    if (!constructed) bp[a] -= lr * dl;
    for (int j = 0; j < H2; j++) {
     dh2[j] += wp[j * actions + a] * dl;
     if (!constructed) wp[j * actions + a] -= lr * h2[j] * dl;
    }
   }
   float[] dh1 = new float[H1];
   for (int j = 0; j < H2; j++) {
    if (h2[j] <= 0f) continue;
    float g = Math.max(-1f, Math.min(1f, dh2[j]));
    if (!constructed) {
     b2[j] -= lr * g;
     for (int i = 0; i < H1; i++) {
      dh1[i] += w2[i * H2 + j] * g;
      w2[i * H2 + j] -= lr * h1[i] * g;
     }
    }
    for (int r = 0; r < LORA_RANK; r++) {
     loraB[r * H2 + j] -= lr * loraAlpha * loraMid[r] * g;
    }
   }
   if (!constructed) {
    for (int i = 0; i < H1; i++) {
     if (h1[i] <= 0f) continue;
     float g = Math.max(-1f, Math.min(1f, dh1[i]));
     b1[i] -= lr * 0.5f * g;
     for (int k = 0; k < IN; k++) {
      w1[k * H1 + i] -= lr * 0.5f * x[k] * g;
     }
    }
   }
  }
  gradientSteps++;
  return klLoss + 0.5f * valError * valError;
 }

 /** Behavior Cloning / DAgger step for a verified expert action. */
 public float imitationStep(float[] x, boolean[] legalMask, int expertAction, float targetValue, float lr) {
  if (expertAction < 0 || expertAction >= actions || !legalMask[expertAction])
   throw new IllegalArgumentException("illegal expert action");
  float[] oneHot = new float[actions];
  oneHot[expertAction] = 1.0f;
  return distillStep(x, legalMask, oneHot, targetValue, lr);
 }

 /**
  * Parameter-efficient safe online adaptation on Android: updates ONLY the LoRA adapter (loraB)
  * and policy/value output heads, keeping the feature trunk W1/W2 frozen.
  */
 public float loraOnlineStep(float[] x, boolean[] legalMask, int action, float advantage, float targetValue, float lr) {
  if (action < 0 || action >= actions || !legalMask[action] || !Float.isFinite(advantage) || !Float.isFinite(lr) || lr <= 0f)
   throw new IllegalArgumentException("invalid online step");
  float[][] fwd = forwardInternal(x);
  float[] h2 = fwd[1], logits = fwd[2], loraMid = fwd[4];
  float value = fwd[3][0];
  float[] probs = maskedSoftmax(logits, legalMask, 1.0f);
  float adv = Math.max(-2f, Math.min(2f, advantage));
  float[] dh2 = new float[H2];
  for (int a = 0; a < actions; a++) {
   if (!legalMask[a]) continue;
   float gradLogPi = (a == action ? 1f : 0f) - probs[a];
   float step = lr * adv * gradLogPi;
   if (!constructed) bp[a] += step;
   for (int j = 0; j < H2; j++) {
    dh2[j] += wp[j * actions + a] * (-adv * gradLogPi);
    if (!constructed) wp[j * actions + a] += step * h2[j];
   }
  }
  float valErr = Math.max(-2f, Math.min(2f, value - targetValue));
  bv -= lr * 0.5f * valErr;
  for (int j = 0; j < H2; j++) {
   wv[j] -= lr * 0.5f * h2[j] * valErr;
   if (!constructed && h2[j] > 0f) {
    float g = Math.max(-1f, Math.min(1f, dh2[j]));
    for (int r = 0; r < LORA_RANK; r++) {
     loraB[r * H2 + j] -= lr * loraAlpha * loraMid[r] * g;
    }
   }
  }
  gradientSteps++;
  return (float) -Math.log(Math.max(1e-6f, probs[action])) * adv + 0.5f * valErr * valErr;
 }

 public void writeWeights(DataOutput out) throws IOException {
  out.writeInt(0x4c505350); // "LPSP"
  out.writeInt(gameId.ordinal());
  out.writeInt(actions);
  out.writeBoolean(constructed);
  out.writeLong(gradientSteps);
  out.writeFloat(loraAlpha);
  out.writeFloat(bv);
  for (float[] arr : new float[][]{w1, b1, w2, b2, loraA, loraB, wp, bp, wv}) {
   out.writeInt(arr.length);
   for (float v : arr) out.writeFloat(v);
  }
 }

 public void readWeights(DataInput in) throws IOException {
  if (in.readInt() != 0x4c505350) throw new IOException("student magic mismatch");
  if (in.readInt() != gameId.ordinal()) throw new IOException("student gameId mismatch");
  if (in.readInt() != actions) throw new IOException("student action count mismatch");
  this.constructed = in.readBoolean();
  this.gradientSteps = in.readLong();
  this.loraAlpha = finite(in.readFloat());
  this.bv = finite(in.readFloat());
  for (float[] arr : new float[][]{w1, b1, w2, b2, loraA, loraB, wp, bp, wv}) {
   if (in.readInt() != arr.length) throw new IOException("student layer shape mismatch");
   for (int i = 0; i < arr.length; i++) arr[i] = finite(in.readFloat());
  }
 }

 private static float finite(float v) throws IOException {
  if (!Float.isFinite(v) || Math.abs(v) > 1e6f) throw new IOException("non-finite weight");
  return v;
 }
}
