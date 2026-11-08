package org.localpilot.android.learning;

/**
 * Programmatic / Analytical Weight Construction and Targeted Weight-Space Repair.
 *
 * Scientific & Engineering Grounding:
 * - Inspired by weight-space programming research (e.g. Takehi, Teoh, Zhang, Khattab,
 *   MIT CSAIL "Sparks of Weight-Space Programming", Nov 2026), which showed two distinct regimes:
 *   (1) Compiling explicit game rules/strategies directly into compact neural network layers
 *       (e.g. ~20K-parameter MinAtar Breakout/Asterix policies outperforming 100M-step random-init PPO);
 *   (2) Targeted weight-space repair of failure directions (e.g. subtracting a looping MLP direction
 *       in Qwen3.5-0.8B layers 15 & 19, 0.016% of parameters, reducing token-limit loops from 93.0% to 33.1%,
 *       whereas models without pervasive looping like Gemma 3 270M saw no gain).
 * - Accordingly, this class does NOT claim magic "6x intelligence"; instead, it deterministically
 *   compiles known game invariants into the initial weights of StudentPolicy (shared trunk 64->64,
 *   hidden 64->32, per-game policy & value heads) and provides a testable weight-space repair
 *   operator (`repairOscillationSubspace`) that projects out repetitive action-loop directions.
 */
public final class WeightConstructor {
 private WeightConstructor() {}

 /**
  * Analytically constructs the weights of a `StudentPolicy` for `gameId` so that forward passes
  * immediately implement verified domain rules before any gradient step is taken.
  */
 public static StudentPolicy construct(TaskSchema.GameId gameId) {
  StudentPolicy net = new StudentPolicy(gameId, 0L, false);
  // 1. Shared trunk W1 (64 -> 64): identity pass-through of the 64 normalized input features
  //    Using ReLU pairs: hidden unit j (0..31) captures +x[j], unit j+32 captures -x[j] for key features,
  //    and candidate slots [16..63] map directly into trunk units.
  for (int i = 0; i < 64; i++) {
   net.setW1(i, i, 1.0f);
  }
  // 2. Representation layer W2 (64 -> 32): compresses global state [0..15] into units [0..15]
  //    and candidate/object features [16..63] into units [16..31].
  for (int j = 0; j < 16; j++) {
   net.setW2(j, j, 1.0f);
  }
  switch (gameId) {
   case SHIPUCHKA -> {
    // 8 candidate shot slots, each occupying 6 features starting at 16 + a*6:
    // feature+0=removed, feature+1=dropped, feature+2=direct, feature+5=composite utility.
    for (int a = 0; a < 8; a++) {
     int base = 16 + a * 6;
     int h2Unit = 16 + a;
     net.setB2(h2Unit, 1.0f); // Keep linear regime positive for small negative bounce penalties
     net.setW2(base, h2Unit, 2.0f);
     net.setW2(base + 1, h2Unit, 2.5f);
     net.setW2(base + 2, h2Unit, 0.3f);
     net.setW2(base + 5, h2Unit, 3.0f);
     net.setWp(h2Unit, a, 4.0f);
     net.setWv(h2Unit, 0.5f);
    }
   }
   case ARI_JUMPER -> {
    // f[8] is signed horizontal direction (-1 -> TAP_LEFT, +1 -> TAP_RIGHT, 0 -> WAIT).
    // Since ReLU(x) zeros negatives, route -f[8] into h1[11] and +f[8] into h1[8],
    // plus hazard avoidance f[9] (hazardLeft -> TAP_RIGHT) and f[10] (hazardRight -> TAP_LEFT).
    net.setW1(8, 8, 1.0f);
    net.setW1(8, 11, -1.0f);
    net.setW2(8, 8, 2.5f);   // Right target signal
    net.setW2(11, 11, 2.5f); // Left target signal
    net.setW2(9, 9, 3.0f);   // Hazard on left -> push right
    net.setW2(10, 10, 3.0f); // Hazard on right -> push left
    // Action 0: TAP_LEFT, Action 1: TAP_RIGHT, Action 2: WAIT
    net.setWp(11, 0, 3.5f);
    net.setWp(10, 0, 4.0f);
    net.setWp(9, 0, -4.0f);
    net.setWp(8, 1, 3.5f);
    net.setWp(9, 1, 4.0f);
    net.setWp(10, 1, -4.0f);
    net.setBp(2, 0.8f); // Default preference for WAIT when aligned (f[8]==0 and no hazard)
    net.setWv(7, 1.0f);
   }
   case MATCH_WORLD, NASHCHUPAY_KHOD -> {
    // 8 candidate swaps, each 6 features at 16 + a*6 (removed, targetCleared, score, row, bonus4+, valid).
    for (int a = 0; a < 8; a++) {
     int base = 16 + a * 6;
     int h2Unit = 16 + a;
     net.setW2(base, h2Unit, 1.5f);
     net.setW2(base + 1, h2Unit, 3.25f);
     net.setW2(base + 2, h2Unit, 2.75f);
     net.setW2(base + 3, h2Unit, 0.4f);
     net.setW2(base + 4, h2Unit, 1.0f);
     net.setWp(h2Unit, a, 4.0f);
     net.setWv(h2Unit, 0.4f);
    }
   }
   case KARKANOID -> {
    // Actions: 0=WAIT, 1=LAUNCH, 2=LEFT_OFFSET, 3=CENTER, 4=RIGHT_OFFSET
    // f[7]=isServe, f[8]=needIntercept (>0), f[6]=urgentBlockDeltaX (+ right, - left)
    net.setW1(6, 6, 1.0f);
    net.setW1(6, 12, -1.0f);
    net.setW2(7, 7, 4.0f);   // Serve indicator
    net.setW2(8, 8, 3.5f);   // Intercept needed indicator
    net.setW2(6, 6, 3.0f);   // Block to the right -> offset 2
    net.setW2(12, 12, 3.0f); // Block to the left -> offset 4
    net.setWp(7, 1, 7.0f);   // LAUNCH when serve
    net.setWp(8, 3, 3.2f);   // CENTER interception when moving
    net.setWp(8, 2, 2.6f);
    net.setWp(8, 4, 2.6f);
    net.setWp(6, 2, 3.8f);   // Angle toward right urgent block
    net.setWp(12, 4, 3.8f);  // Angle toward left urgent block
    net.setBp(0, 1.8f);      // WAIT when aligned and not serve (f[7]==0, f[8]==0)
    net.setWv(5, 1.2f);
   }
   case MARKET_RUSH -> {
    // Actions: 0=LEFT, 1=RIGHT, 2=WAIT
    // f[4] = advantage of moving LEFT, f[5] = advantage of moving RIGHT, f[6] = current lane risk
    net.setW2(4, 4, 3.5f);
    net.setW2(5, 5, 3.5f);
    net.setW2(6, 6, 2.0f);
    net.setWp(4, 0, 4.0f);
    net.setWp(5, 1, 4.0f);
    net.setWp(6, 2, -2.5f);
    net.setBp(2, 1.1f); // Stay in current lane when risk is low
    net.setWv(7, 1.0f);
   }
   case GOSHAN_FLAPPY -> {
    // Actions: 0=WAIT, 1=FLAP
    // f[1] = (futureY - targetY)*4 (>0 means falling below target), f[6] = controller flap signal (+1/-1)
    net.setW1(1, 1, 1.0f);
    net.setW1(6, 6, 1.0f);
    net.setW1(6, 13, -1.0f);
    net.setW2(1, 1, 2.5f);
    net.setW2(6, 6, 3.5f);
    net.setW2(13, 13, 3.5f);
    net.setWp(1, 1, 2.5f);
    net.setWp(6, 1, 4.0f);
    net.setWp(13, 0, 4.0f);
    net.setBp(0, 0.4f);
   }
   case GAME_2048 -> {
    // 4 directional lookahead slots at 16 + d*8: [reward, empty, cornerMax, heuristicValue]
    for (int d = 0; d < 4; d++) {
     int base = 16 + d * 8;
     int h2Unit = 16 + d;
     // Route both positive and negative heuristic values via offset bias
     net.setB1(base + 3, 1.0f);
     net.setW2(base, h2Unit, 2.0f);
     net.setW2(base + 1, h2Unit, 2.5f);
     net.setW2(base + 2, h2Unit, 1.5f);
     net.setW2(base + 3, h2Unit, 4.0f);
     net.setWp(h2Unit, d, 4.5f);
     net.setWv(h2Unit, 0.5f);
    }
   }
   case HEXON -> {
    // 8 candidate hex placements at 16 + a*6: [clearedStacks, clearedTiles, mergedTiles, freeCellsAfter, score, valid]
    for (int a = 0; a < 8; a++) {
     int base = 16 + a * 6;
     int h2Unit = 16 + a;
     net.setW2(base, h2Unit, 3.0f);
     net.setW2(base + 1, h2Unit, 2.0f);
     net.setW2(base + 2, h2Unit, 2.2f);
     net.setW2(base + 3, h2Unit, 1.2f);
     net.setW2(base + 4, h2Unit, 3.5f);
     net.setW2(base + 5, h2Unit, 0.35f);
     net.setWp(h2Unit, a, 4.0f);
     net.setWv(h2Unit, 0.4f);
    }
   }
   case KOTOBASHNYA -> {
    // Actions: 0=WAIT, 1=JUMP_UP, 2=JUMP_LEFT, 3=JUMP_RIGHT, 4=STOMP_ENEMY, 5=LOCK_SLAB
    // f[3]=slabReady, f[6]=stompSafe, f[8]=jumpUp, f[9]=jumpLeft, f[10]=jumpRight
    net.setW2(3, 3, 3.5f);
    net.setW2(6, 6, 4.0f);
    net.setW2(8, 8, 3.5f);
    net.setW2(9, 9, 3.5f);
    net.setW2(10, 10, 3.5f);
    net.setWp(3, 5, 6.0f); // LOCK_SLAB when aligned
    net.setWp(6, 4, 6.5f); // STOMP_ENEMY when safeStompWindow is active
    net.setWp(8, 1, 4.5f); // JUMP_UP
    net.setWp(9, 2, 4.5f); // JUMP_LEFT
    net.setWp(10, 3, 4.5f); // JUMP_RIGHT
    net.setBp(0, 1.2f);    // WAIT default when no jump/slab/stomp signal
   }
   case NES_PLATFORMER, NES_TACTICAL_ARENA,
        DOS_PLATFORM_EXPLORER, DOS_CORRIDOR_ACTION,
        SEGA_HIGH_SPEED_ACT, SEGA_BRAWLER_RUNGUN -> {
    // 8 retro controller actions, each occupying 6 candidate features at 16 + a*6:
    // [0]=progressGain, [1]=survivalMargin, [2]=combatOrClearGain,
    // [3]=collectibleOrObjectiveGain, [4]=momentumOrTacticalSynergy, [5]=compositeDomainScore.
    float wProg = switch (gameId) {
     case NES_PLATFORMER, SEGA_HIGH_SPEED_ACT -> 1.8f;
     case DOS_PLATFORM_EXPLORER -> 1.5f;
     default -> 1.1f;
    };
    float wSafe = switch (gameId) {
     case DOS_PLATFORM_EXPLORER, NES_TACTICAL_ARENA -> 2.6f;
     default -> 2.2f;
    };
    float wCombat = switch (gameId) {
     case DOS_CORRIDOR_ACTION, SEGA_BRAWLER_RUNGUN, NES_TACTICAL_ARENA -> 2.5f;
     default -> 1.5f;
    };
    float wColl = switch (gameId) {
     case SEGA_HIGH_SPEED_ACT, DOS_PLATFORM_EXPLORER -> 1.8f;
     default -> 1.1f;
    };
    float wSyn = switch (gameId) {
     case SEGA_HIGH_SPEED_ACT -> 2.5f;
     default -> 1.7f;
    };
    for (int a = 0; a < 8; a++) {
     int base = 16 + a * 6;
     int h2Unit = 16 + a;
     net.setW2(base, h2Unit, wProg);
     net.setW2(base + 1, h2Unit, wSafe);
     net.setW2(base + 2, h2Unit, wCombat);
     net.setW2(base + 3, h2Unit, wColl);
     net.setW2(base + 4, h2Unit, wSyn);
     net.setW2(base + 5, h2Unit, 3.0f);
     net.setWp(h2Unit, a, 4.2f);
     net.setWv(h2Unit, 0.45f);
    }
   }
  }
  net.markConstructed();
  return net;
 }

 /**
  * Targeted weight-space repair (analogous to MLP direction projection in Takehi et al., 2026):
  * Given states where a policy repeatedly selected an unverified/looping action `loopAction`
  * while a verified alternative `betterAction` was legal, computes the mean hidden activation
  * direction in layer 2 and subtracts a scaled rank-1 projection from the `loopAction` column
  * of `Wp`, suppressing the behavioral loop without destroying unrelated state mappings.
  *
  * @return number of modified weight parameters.
  */
 public static int repairOscillationSubspace(StudentPolicy policy, float[][] loopingStates,
   int loopAction, int betterAction, float strength) {
  if (policy == null || loopingStates == null || loopingStates.length == 0
    || loopAction < 0 || loopAction >= policy.actionCount()
    || betterAction < 0 || betterAction >= policy.actionCount()
    || loopAction == betterAction || !Float.isFinite(strength) || strength <= 0f)
   throw new IllegalArgumentException("invalid weight repair arguments");
  float[] meanH2 = new float[StudentPolicy.H2];
  for (float[] state : loopingStates) {
   float[] h2 = policy.hiddenLayer2(state);
   for (int j = 0; j < StudentPolicy.H2; j++) meanH2[j] += h2[j] / loopingStates.length;
  }
  double normSq = 0;
  for (float v : meanH2) normSq += (double) v * v;
  if (normSq < 1e-8) return 0;
  float invNorm = (float) (1.0 / Math.sqrt(normSq));
  int edited = 0;
  for (int j = 0; j < StudentPolicy.H2; j++) {
   float dir = meanH2[j] * invNorm;
   if (Math.abs(dir) > 1e-4f) {
    policy.addWp(j, loopAction, -strength * dir);
    policy.addWp(j, betterAction, 0.5f * strength * dir);
    edited += 2;
   }
  }
  return edited;
 }
}
