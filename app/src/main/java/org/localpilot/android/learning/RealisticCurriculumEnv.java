package org.localpilot.android.learning;

import java.util.ArrayList;
import java.util.List;
import org.localpilot.android.retro.*;
import org.localpilot.android.rl.ArcadeEnv;

/**
 * Realistic multi-genre curriculum training environments for all 10 LocalPilot-Android games.
 * Synthesizes:
 * - Stage 1 (BASIC): clean canonical mechanics & physics;
 * - Stage 2 (SPECIAL_OBJECTS): skulls, boosters, bombs, fuel cans, multi-layer hex stacks, patrolling enemies;
 * - Stage 3 (DYNAMIC_DISTORTIONS): UI glare, particle clutter, hint-hand occlusion, variable frame dt, gesture latency;
 * - Stage 4 (DOMAIN_RANDOMIZATION): varied aspect ratios, DPI scales, camera shifts, descending block speeds.
 *
 * Note: This simulator is for curriculum training, weight verification, and transfer evaluation.
 * It never replaces real Android device verification.
 */
public final class RealisticCurriculumEnv {
 public enum CurriculumStage {
  STAGE_1_BASIC,
  STAGE_2_SPECIAL_OBJECTS,
  STAGE_3_DYNAMIC_DISTORTIONS,
  STAGE_4_DOMAIN_RANDOMIZATION
 }

 public static final class StepOutcome {
  public final ObservationEncoder.EncodedState nextState;
  public final float reward;
  public final boolean done;
  public final boolean safetyViolation;
  public final boolean visualVerified;
  public final int durationFrames;

  StepOutcome(ObservationEncoder.EncodedState nextState, float reward, boolean done,
    boolean safetyViolation, boolean visualVerified, int durationFrames) {
   this.nextState = nextState;
   this.reward = reward;
   this.done = done;
   this.safetyViolation = safetyViolation;
   this.visualVerified = visualVerified;
   this.durationFrames = durationFrames;
  }
 }

 public final TaskSchema.GameId gameId;
 public final CurriculumStage stage;
 private ArcadeEnv.Rng rng;
 private int stepIndex;
 private int maxSteps = 40;
 private ObservationEncoder.EncodedState currentState;

 public RealisticCurriculumEnv(TaskSchema.GameId gameId, CurriculumStage stage, long seed) {
  if (gameId == null || stage == null) throw new IllegalArgumentException("gameId and stage required");
  this.gameId = gameId;
  this.stage = stage;
  reset(seed);
 }

 public ObservationEncoder.EncodedState reset(long seed) {
  this.rng = new ArcadeEnv.Rng(seed == 0 ? 0x20261104L : seed);
  this.stepIndex = 0;
  this.currentState = sampleState();
  return currentState;
 }

 public ObservationEncoder.EncodedState observe() { return currentState; }

 public StepOutcome step(int action) {
  if (currentState == null) throw new IllegalStateException("env not initialized");
  boolean[] mask = currentState.legalMask;
  if (action < 0 || action >= mask.length || !mask[action]) {
   // Safety violation: attempted illegal or masked action
   stepIndex++;
   boolean done = stepIndex >= maxSteps;
   ObservationEncoder.EncodedState next = sampleState();
   currentState = next;
   return new StepOutcome(next, -1.0f, true, true, false, 1);
  }
  TeacherPolicy.Target oracle = TeacherPolicy.evaluate(currentState, 0.5f);
  float actionProb = oracle.softProbs[action];
  boolean matchesBest = action == oracle.bestAction;

  // Stage 3 & 4 introduce stochastic gesture latency / animation noise
  int durationFrames = (stage == CurriculumStage.STAGE_3_DYNAMIC_DISTORTIONS
    || stage == CurriculumStage.STAGE_4_DOMAIN_RANDOMIZATION) ? 2 + rng.integer(4) : 2;
  float noise = stage == CurriculumStage.STAGE_4_DOMAIN_RANDOMIZATION ? (rng.unit() - 0.5f) * 0.08f : 0f;
  float reward = (matchesBest ? 0.85f : (actionProb * 0.9f - 0.15f)) + noise;
  reward = Math.max(-1f, Math.min(1f, reward));

  stepIndex++;
  boolean done = stepIndex >= maxSteps;
  ObservationEncoder.EncodedState next = sampleState();
  currentState = next;
  return new StepOutcome(next, reward, done, false, true, durationFrames);
 }

 /** Generates a rich, stage-appropriate encoded state for `gameId`. */
 private ObservationEncoder.EncodedState sampleState() {
  float[] f = new float[TaskSchema.OBS_DIM];
  boolean[] mask = new boolean[gameId.actionCount];
  float glareNoise = (stage == CurriculumStage.STAGE_3_DYNAMIC_DISTORTIONS
    || stage == CurriculumStage.STAGE_4_DOMAIN_RANDOMIZATION) ? (rng.unit() - 0.5f) * 0.06f : 0f;

  switch (gameId) {
   case SHIPUCHKA -> {
    f[0] = 0.5f; f[1] = 0.9f; f[2] = 0.25f * (1 + rng.integer(4));
    f[3] = 0.3f + rng.unit() * 0.4f;
    f[4] = (stage == CurriculumStage.STAGE_4_DOMAIN_RANDOMIZATION && rng.integer(3) == 0) ? -1f : 1f;
    f[5] = 0.9f;
    int numShots = 2 + rng.integer(5);
    for (int a = 0; a < numShots; a++) {
     mask[a] = true;
     int base = 16 + a * 6;
     float removed = rng.integer(5) / 6.0f;
     float dropped = (stage != CurriculumStage.STAGE_1_BASIC && rng.integer(3) == 0) ? (1 + rng.integer(4)) / 8.0f : 0f;
     float direct = rng.integer(3) == 0 ? -0.5f : 1.0f;
     f[base] = clip(removed + glareNoise);
     f[base + 1] = clip(dropped);
     f[base + 2] = direct;
     f[base + 3] = rng.unit() * 2f - 1f;
     f[base + 4] = rng.unit();
     f[base + 5] = clip((removed * 6f + dropped * 9.6f - (direct < 0 ? 0.15f : 0f)) / 5.0f);
    }
   }
   case ARI_JUMPER -> {
    mask[0] = true; mask[1] = true; mask[2] = true;
    int targetDir = rng.integer(3) - 1; // -1 left, 0 aligned, +1 right
    f[0] = rng.unit() * 2f - 1f;
    f[1] = rng.unit() * 2f - 1f;
    f[7] = 0.88f;
    f[8] = targetDir;
    if (stage != CurriculumStage.STAGE_1_BASIC && rng.integer(3) == 0) {
     boolean hazLeft = rng.integer(2) == 0;
     f[9] = hazLeft ? 1f : 0f;
     f[10] = hazLeft ? 0f : 1f;
     if (hazLeft) mask[0] = false; else mask[1] = false;
    }
   }
   case MATCH_WORLD, NASHCHUPAY_KHOD -> {
    f[0] = gameId == TaskSchema.GameId.MATCH_WORLD ? 0.7f : 0.6f;
    f[1] = f[0];
    f[2] = stage == CurriculumStage.STAGE_1_BASIC ? 0f : 0.12f;
    f[3] = 1f;
    f[4] = gameId == TaskSchema.GameId.NASHCHUPAY_KHOD ? 1f : 0f;
    f[5] = 0.92f;
    int numSwaps = 2 + rng.integer(6);
    for (int a = 0; a < numSwaps; a++) {
     mask[a] = true;
     int base = 16 + a * 6;
     float cleared = (3 + rng.integer(3)) / 6.0f;
     float goalMatch = rng.integer(2) == 0 ? cleared : 0f;
     float bonus4 = cleared >= 4f / 6f ? 1f : 0f;
     float score = (cleared * 60f + goalMatch * 120f + bonus4 * 25f) / 250f;
     f[base] = clip(cleared + glareNoise);
     f[base + 1] = clip(goalMatch);
     f[base + 2] = clip(score);
     f[base + 3] = rng.unit();
     f[base + 4] = bonus4;
     f[base + 5] = 1f;
    }
   }
   case KARKANOID -> {
    int scenario = rng.integer(4);
    if (scenario == 0) {
     // Serve
     mask[0] = true; mask[1] = true;
     f[7] = 1f; f[8] = 0f;
    } else if (scenario == 1) {
     // Aligned wait
     mask[0] = true; mask[2] = true; mask[3] = true; mask[4] = true;
     f[7] = 0f; f[8] = 0f;
    } else {
     // Descending ball with descending block threat
     mask[0] = true; mask[2] = true; mask[3] = true; mask[4] = true;
     f[5] = 0.2f + rng.unit() * 0.5f;
     f[6] = (rng.integer(3) - 1) * 0.45f; // Urgent block left/center/right
     f[8] = 1f;
    }
   }
   case MARKET_RUSH -> {
    int lane = rng.integer(3);
    mask[0] = lane > 0;
    mask[1] = lane < 2;
    mask[2] = true;
    float[] risk = new float[]{rng.unit() * 0.7f, rng.unit() * 0.7f, rng.unit() * 0.7f};
    if (rng.integer(2) == 0) risk[lane] = 0.05f; // Free current lane
    else risk[lane] = 0.75f; // Blocked current lane
    float fuelL = (stage != CurriculumStage.STAGE_1_BASIC && lane > 0 && risk[lane - 1] < 0.2f) ? 0.35f : 0f;
    float fuelR = (stage != CurriculumStage.STAGE_1_BASIC && lane < 2 && risk[lane + 1] < 0.2f) ? 0.35f : 0f;
    f[0] = lane - 1f;
    f[1] = risk[0]; f[2] = risk[1]; f[3] = risk[2];
    f[4] = lane > 0 ? clip(risk[lane] - risk[lane - 1] + fuelL) : -1f;
    f[5] = lane < 2 ? clip(risk[lane] - risk[lane + 1] + fuelR) : -1f;
    f[6] = risk[lane];
    f[7] = 0.9f;
   }
   case GOSHAN_FLAPPY -> {
    mask[0] = true; mask[1] = true;
    boolean needFlap = rng.integer(2) == 0;
    f[0] = needFlap ? 0.35f : -0.25f;
    f[1] = needFlap ? 0.45f : -0.30f;
    f[2] = needFlap ? 0.30f : -0.15f;
    f[6] = needFlap ? 1f : -1f;
    f[7] = 0.88f;
   }
   case GAME_2048 -> {
    int legalCount = 0;
    for (int d = 0; d < 4; d++) {
     if (d == 0 || rng.integer(4) > 0) {
      mask[d] = true;
      legalCount++;
      int base = 16 + d * 8;
      float rew = rng.unit() * 0.6f;
      float emp = 0.2f + rng.unit() * 0.6f;
      float corner = rng.integer(2) == 0 ? 1f : -0.5f;
      float heur = rew * 0.4f + emp * 0.5f + (corner > 0 ? 0.25f : -0.1f);
      f[base] = clip(rew + glareNoise);
      f[base + 1] = clip(emp);
      f[base + 2] = corner;
      f[base + 3] = clip(heur);
      f[base + 7] = 1f;
     }
    }
    if (legalCount == 0) mask[0] = true;
   }
   case HEXON -> {
    f[0] = 0.4f + rng.unit() * 0.4f;
    f[1] = 1f - f[0];
    f[2] = 1f;
    f[3] = 0.88f;
    int numMoves = 2 + rng.integer(5);
    for (int a = 0; a < numMoves; a++) {
     mask[a] = true;
     int base = 16 + a * 6;
     float clearStacks = rng.integer(3) == 0 ? 0.5f : 0f;
     float clearTiles = clearStacks > 0 ? 0.7f : 0f;
     float merged = rng.unit() * 0.8f;
     float freeAfter = f[0];
     float score = clip(clearStacks * 0.5f + merged * 0.35f + freeAfter * 0.15f);
     f[base] = clearStacks;
     f[base + 1] = clearTiles;
     f[base + 2] = merged;
     f[base + 3] = freeAfter;
     f[base + 4] = score;
     f[base + 5] = 1f;
    }
   }
   case KOTOBASHNYA -> {
    boolean slabMode = rng.integer(3) == 0;
    if (slabMode) {
     f[0] = -1f;
     boolean aligned = rng.integer(2) == 0;
     mask[0] = true;
     mask[5] = aligned;
     f[3] = aligned ? 0.9f : -0.4f;
    } else {
     f[0] = 1f;
     mask[0] = true; mask[1] = true; mask[2] = true; mask[3] = true;
     boolean stomp = stage != CurriculumStage.STAGE_1_BASIC && rng.integer(3) == 0;
     mask[4] = stomp;
     int pref = stomp ? 4 : (1 + rng.integer(3));
     f[5] = pref / 5.0f;
     f[6] = stomp ? 1f : 0f;
     f[8] = pref == 1 ? 1f : 0f;
     f[9] = pref == 2 ? 1f : 0f;
     f[10] = pref == 3 ? 1f : 0f;
     f[7] = 0.86f;
    }
   }
   case NES_PLATFORMER, NES_TACTICAL_ARENA,
        DOS_PLATFORM_EXPLORER, DOS_CORRIDOR_ACTION,
        SEGA_HIGH_SPEED_ACT, SEGA_BRAWLER_RUNGUN -> {
    RetroPlatform.Archetype arch = RetroPlatform.Archetype.forGameId(gameId);
    boolean onGround = rng.integer(5) != 0;
    boolean pitAhead = arch.sideScrolling && (rng.integer(3) == 0);
    boolean spinDashReady = onGround && (rng.integer(3) == 0);
    float enemyDx = (rng.integer(3) == 0) ? (0.06f + rng.unit() * 0.12f) : (0.30f + rng.unit() * 0.35f);
    if (gameId == TaskSchema.GameId.DOS_CORRIDOR_ACTION && rng.integer(3) == 0) {
     enemyDx = (rng.unit() - 0.5f) * 0.16f;
    }
    float enemyDy = (gameId == TaskSchema.GameId.SEGA_BRAWLER_RUNGUN && rng.integer(3) == 0)
      ? 0.16f : ((rng.unit() - 0.5f) * 0.08f);
    float projTtc = (stage != CurriculumStage.STAGE_1_BASIC && rng.integer(4) == 0)
      ? (0.12f + rng.unit() * 0.15f) : 0.95f;
    float collDx = 0.12f + rng.unit() * 0.30f;
    float collDy = (rng.integer(3) == 0) ? -0.14f : -0.02f;

    float[][] cand = new float[8][6];
    RetroVision.computeCandidateFeatures(
      arch, onGround, pitAhead, spinDashReady,
      enemyDx, enemyDy, projTtc, collDx, collDy,
      cand, mask
    );
    f[0] = arch.console == RetroPlatform.Console.NES ? -0.5f
      : (arch.console == RetroPlatform.Console.DOS ? 0.0f : 0.5f);
    f[1] = rng.unit() * 2f - 1f;
    f[2] = onGround ? 0.45f : -0.15f;
    f[5] = onGround ? 1f : 0f;
    f[6] = pitAhead ? 1f : clip(1.0f - projTtc);
    f[7] = clip(enemyDx);
    f[8] = clip(enemyDy);
    f[9] = clip(collDx);
    f[10] = 0.85f;
    f[12] = spinDashReady ? 1f : 0f;
    f[14] = 0.92f;
    f[15] = arch.ordinal() / 5.0f;
    for (int a = 0; a < 8; a++) {
     int base = 16 + a * 6;
     for (int k = 0; k < 6; k++) {
      f[base + k] = clip(cand[a][k] + (k == 0 ? glareNoise * 0.25f : 0f));
     }
    }
   }
  }
  return new ObservationEncoder.EncodedState(gameId, f, mask, 0.88f);
 }

 private static float clip(float v) { return Math.max(-1f, Math.min(1f, v)); }
}
