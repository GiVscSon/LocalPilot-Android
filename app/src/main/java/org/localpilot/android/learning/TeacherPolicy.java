package org.localpilot.android.learning;

import java.util.Arrays;

/**
 * Rule, Search, and Expectimax Teacher Policy for all 10 target games.
 * Produces verified target actions, soft probability distributions (for Knowledge Distillation),
 * and value targets for training compact Android StudentPolicy networks.
 */
public final class TeacherPolicy {
 public static final class Target {
  public final int bestAction;
  public final float[] softProbs;
  public final float valueTarget;
  public final String rationale;

  public Target(int bestAction, float[] softProbs, float valueTarget, String rationale) {
   this.bestAction = bestAction;
   this.softProbs = softProbs.clone();
   this.valueTarget = valueTarget;
   this.rationale = rationale;
  }
 }

 private TeacherPolicy() {}

 /** Computes expert action and soft distillation target distribution for an encoded game state. */
 public static Target evaluate(ObservationEncoder.EncodedState state, float temperature) {
  if (state == null || !state.hasLegalAction()) {
   float[] empty = new float[state == null ? 1 : state.legalMask.length];
   return new Target(-1, empty, 0f, "NO_LEGAL_ACTION");
  }
  float[] f = state.features;
  boolean[] mask = state.legalMask;
  float[] q = new float[mask.length];
  Arrays.fill(q, -10f);
  String reason = "teacher-" + state.gameId.id;

  switch (state.gameId) {
   case SHIPUCHKA -> {
    for (int a = 0; a < 8; a++) {
     if (!mask[a]) continue;
     int base = 16 + a * 6;
     q[a] = f[base] * 4.0f + f[base + 1] * 5.0f + f[base + 2] * 0.6f + f[base + 5] * 6.0f;
    }
    reason = "bubble-raycast-oracle";
   }
   case ARI_JUMPER -> {
    // 0=TAP_LEFT, 1=TAP_RIGHT, 2=WAIT
    float dir = f[8];
    float hazL = f[9], hazR = f[10];
    q[0] = (dir < -0.1f ? 3.5f : -1.0f) + hazR * 4.0f - hazL * 6.0f;
    q[1] = (dir > 0.1f ? 3.5f : -1.0f) + hazL * 4.0f - hazR * 6.0f;
    q[2] = (Math.abs(dir) <= 0.1f && hazL < 0.5f && hazR < 0.5f) ? 3.2f : 0.2f;
    reason = "jumper-wrap-hazard-oracle";
   }
   case MATCH_WORLD, NASHCHUPAY_KHOD -> {
    for (int a = 0; a < 8; a++) {
     if (!mask[a]) continue;
     int base = 16 + a * 6;
     q[a] = f[base] * 3.0f + f[base + 1] * 6.5f + f[base + 2] * 5.5f + f[base + 3] * 0.8f + f[base + 4] * 2.0f;
    }
    reason = state.gameId == TaskSchema.GameId.NASHCHUPAY_KHOD
      ? "protected-skull-swap-oracle" : "match3-cascade-goal-oracle";
   }
   case KARKANOID -> {
    // 0=WAIT, 1=LAUNCH, 2=LEFT_OFFSET, 3=CENTER, 4=RIGHT_OFFSET
    if (mask[1] && f[7] > 0.5f) {
     q[1] = 8.0f;
     reason = "karkanoid-serve";
    } else if (f[8] <= 0.5f) {
     q[0] = 5.0f;
     reason = "karkanoid-aligned-wait";
    } else {
     float urgentDelta = f[6];
     q[3] = 4.5f;
     q[2] = 4.0f + (urgentDelta > 0.08f ? 1.8f : -0.4f);
     q[4] = 4.0f + (urgentDelta < -0.08f ? 1.8f : -0.4f);
     q[0] = -2.0f;
     reason = "karkanoid-ballistic-block-threat";
    }
   }
   case MARKET_RUSH -> {
    // 0=LEFT, 1=RIGHT, 2=WAIT
    float advLeft = f[4], advRight = f[5], curRisk = f[6];
    q[0] = advLeft * 5.0f;
    q[1] = advRight * 5.0f;
    q[2] = curRisk < 0.2f ? 3.5f : (1.0f - curRisk * 4.0f);
    reason = "traffic-ttc-fuel-oracle";
   }
   case GOSHAN_FLAPPY -> {
    // 0=WAIT, 1=FLAP
    float needFlap = f[6] > 0f ? 1f : (f[1] > 0.05f ? 0.8f : -1f);
    q[0] = needFlap < 0f ? 3.8f : 0.2f;
    q[1] = needFlap > 0f ? 3.8f : -1.5f;
    reason = "flappy-rollout-beam-oracle";
   }
   case GAME_2048 -> {
    for (int d = 0; d < 4; d++) {
     if (!mask[d]) continue;
     int base = 16 + d * 8;
     q[d] = f[base] * 3.5f + f[base + 1] * 4.0f + f[base + 2] * 2.5f + f[base + 3] * 6.0f;
    }
    reason = "2048-expectimax-corner-oracle";
   }
   case HEXON -> {
    for (int a = 0; a < 8; a++) {
     if (!mask[a]) continue;
     int base = 16 + a * 6;
     q[a] = f[base] * 6.0f + f[base + 1] * 4.0f + f[base + 2] * 4.5f + f[base + 3] * 2.5f + f[base + 4] * 5.5f;
    }
    reason = "hexon-stack-merge10-oracle";
   }
   case KOTOBASHNYA -> {
    // 0=WAIT, 1=JUMP_UP, 2=JUMP_LEFT, 3=JUMP_RIGHT, 4=STOMP_ENEMY, 5=LOCK_SLAB
    if (f[0] < 0f) {
     // Stack slab sub-mode
     q[5] = mask[5] ? 6.0f : -5.0f;
     q[0] = mask[5] ? 0.5f : 4.5f;
     reason = "kotobashnya-slab-lock-oracle";
    } else {
     int preferred = Math.max(0, Math.min(5, Math.round(f[5] * 5.0f)));
     for (int a = 0; a < 6; a++) if (mask[a]) q[a] = 1.0f;
     if (mask[4] && f[6] > 0.5f) q[4] = 6.5f;
     if (mask[preferred]) q[preferred] = Math.max(q[preferred], 5.0f);
     reason = "kotobashnya-climb-stomp-oracle";
    }
   }
   case NES_PLATFORMER, NES_TACTICAL_ARENA,
        DOS_PLATFORM_EXPLORER, DOS_CORRIDOR_ACTION,
        SEGA_HIGH_SPEED_ACT, SEGA_BRAWLER_RUNGUN -> {
    float wProg = switch (state.gameId) {
     case NES_PLATFORMER, SEGA_HIGH_SPEED_ACT -> 3.6f;
     case DOS_PLATFORM_EXPLORER -> 3.0f;
     default -> 2.2f;
    };
    float wSafe = switch (state.gameId) {
     case DOS_PLATFORM_EXPLORER, NES_TACTICAL_ARENA -> 5.2f;
     default -> 4.4f;
    };
    float wCombat = switch (state.gameId) {
     case DOS_CORRIDOR_ACTION, SEGA_BRAWLER_RUNGUN, NES_TACTICAL_ARENA -> 5.0f;
     default -> 3.0f;
    };
    float wColl = switch (state.gameId) {
     case SEGA_HIGH_SPEED_ACT, DOS_PLATFORM_EXPLORER -> 3.6f;
     default -> 2.2f;
    };
    float wSyn = switch (state.gameId) {
     case SEGA_HIGH_SPEED_ACT -> 5.0f;
     default -> 3.4f;
    };
    for (int a = 0; a < 8; a++) {
     if (!mask[a]) continue;
     int base = 16 + a * 6;
     q[a] = f[base] * wProg
       + f[base + 1] * wSafe
       + f[base + 2] * wCombat
       + f[base + 3] * wColl
       + f[base + 4] * wSyn
       + f[base + 5] * 6.0f;
    }
    reason = "retro-console-oracle-" + state.gameId.id;
   }
  }

  float[] soft = StudentPolicy.maskedSoftmax(q, mask, Math.max(0.2f, temperature));
  int best = -1;
  float bestQ = -Float.MAX_VALUE;
  for (int a = 0; a < mask.length; a++) {
   if (mask[a] && q[a] > bestQ) {
    bestQ = q[a];
    best = a;
   }
  }
  float valTarget = Math.max(-1f, Math.min(1f, bestQ / 8.0f));
  return new Target(best, soft, valTarget, reason);
 }
}
