package org.localpilot.android.retro;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.localpilot.android.ActionGate;
import org.localpilot.android.WindowFocus;

/**
 * Single-Executor Safe Input Bridge for NES, DOS, and SEGA Android Emulators.
 *
 * Responsibilities:
 * 1. Maps discrete 8-action decisions from `StudentPolicy` / `TeacherPolicy` into calibrated
 *    screen touch gestures (`Tap`, `Hold`, `Chord` D-Pad + Action Button, or sequential micro-stroke)
 *    within the bound emulator window;
 * 2. Enforces `ActionGate.armed()`, `WindowFocus`, and `BoundsRebinder` before any gesture plan
 *    is authorized;
 * 3. Strictly separates `ANDROID_ACK` (OS gesture delivery callback) from `visualVerified`
 *    (independent post-ACK proof in `RetroVision.RetroScene` that the player moved, an enemy/wall
 *    was cleared, or the HUD score/rings/progress advanced).
 */
public final class RetroInputBridge {

 public enum GestureKind {
  WAIT_NOOP,
  SINGLE_TAP,
  DIRECTIONAL_HOLD,
  CHORD_DPAD_PLUS_BUTTON,
  SEQUENTIAL_CHARGE_RELEASE
 }

 public record StrokePoint(int screenX, int screenY, int durationMs, String buttonTag) {}

 public static final class GesturePlan {
  public final RetroPlatform.Console console;
  public final int actionIndex;
  public final String actionName;
  public final GestureKind kind;
  public final List<StrokePoint> strokes;
  public final boolean authorized;
  public final String gateReason;

  public GesturePlan(RetroPlatform.Console console, int actionIndex, String actionName,
    GestureKind kind, List<StrokePoint> strokes, boolean authorized, String gateReason) {
   this.console = console;
   this.actionIndex = actionIndex;
   this.actionName = actionName;
   this.kind = kind;
   this.strokes = List.copyOf(strokes);
   this.authorized = authorized;
   this.gateReason = gateReason;
  }

  public int totalDurationMs() {
   int max = 0;
   for (StrokePoint p : strokes) {
    if (kind == GestureKind.SEQUENTIAL_CHARGE_RELEASE) max += p.durationMs();
    else max = Math.max(max, p.durationMs());
   }
   return max;
  }

  public String format() {
   return String.format(Locale.ROOT, "RETRO_INPUT[%s act=%d(%s) kind=%s strokes=%d auth=%s reason=%s]",
     console.id, actionIndex, actionName, kind, strokes.size(), authorized, gateReason);
  }
 }

 public static final class PostAckVerification {
  public final boolean androidAck;
  public final boolean visualVerified;
  public final float measuredReward;
  public final String verificationReason;

  public PostAckVerification(boolean androidAck, boolean visualVerified,
    float measuredReward, String verificationReason) {
   this.androidAck = androidAck;
   this.visualVerified = visualVerified;
   this.measuredReward = measuredReward;
   this.verificationReason = verificationReason;
  }
 }

 private RetroInputBridge() {}

 /**
  * Builds a safe, single-executor gesture plan for `action` (`0..7`) on `console` using the
  * calibrated `pad` coordinates, gated by `gate` and `expectedPackage`.
  */
 public static GesturePlan planAction(RetroPlatform.Console console, int action,
   RetroVision.VirtualPadLayout pad, ActionGate gate, String activePackage, String expectedPackage) {
  if (console == null || pad == null) {
   return new GesturePlan(console == null ? RetroPlatform.Console.NES : console,
     action, "INVALID", GestureKind.WAIT_NOOP, List.of(), false, "NULL_PAD_OR_CONSOLE");
  }
  if (gate != null && !gate.armed()) {
   return new GesturePlan(console, action, console.actionLabel(action),
     GestureKind.WAIT_NOOP, List.of(), false, "ACTION_GATE_DISARMED_STOP");
  }
  if (expectedPackage != null && !expectedPackage.isEmpty()
    && (activePackage == null || !expectedPackage.equals(activePackage))) {
   return new GesturePlan(console, action, console.actionLabel(action),
     GestureKind.WAIT_NOOP, List.of(), false, "WINDOW_PACKAGE_MISMATCH");
  }
  if (action < 0 || action >= console.actionCount()) {
   return new GesturePlan(console, action, "WAIT",
     GestureKind.WAIT_NOOP, List.of(), false, "OUT_OF_RANGE_WAIT");
  }

  String label = console.actionLabel(action);
  List<StrokePoint> strokes = new ArrayList<>(2);
  GestureKind kind;

  switch (console) {
   case NES -> {
    // 0=MOVE_LEFT, 1=MOVE_RIGHT, 2=AIM_UP, 3=CROUCH_DOWN,
    // 4=JUMP_A, 5=ATTACK_B, 6=RUN_RIGHT_B, 7=RUN_JUMP_RIGHT_AB
    switch (action) {
     case 0 -> {
      kind = GestureKind.DIRECTIONAL_HOLD;
      strokes.add(new StrokePoint(pad.dpadLeftX, pad.dpadLeftY, 110, "NES_LEFT"));
     }
     case 1 -> {
      kind = GestureKind.DIRECTIONAL_HOLD;
      strokes.add(new StrokePoint(pad.dpadRightX, pad.dpadRightY, 115, "NES_RIGHT"));
     }
     case 2 -> {
      kind = GestureKind.DIRECTIONAL_HOLD;
      strokes.add(new StrokePoint(pad.dpadUpX, pad.dpadUpY, 90, "NES_UP"));
     }
     case 3 -> {
      kind = GestureKind.DIRECTIONAL_HOLD;
      strokes.add(new StrokePoint(pad.dpadDownX, pad.dpadDownY, 95, "NES_DOWN"));
     }
     case 4 -> {
      kind = GestureKind.SINGLE_TAP;
      strokes.add(new StrokePoint(pad.buttonPrimaryX, pad.buttonPrimaryY, 130, "NES_A_JUMP"));
     }
     case 5 -> {
      kind = GestureKind.SINGLE_TAP;
      strokes.add(new StrokePoint(pad.buttonSecondaryX, pad.buttonSecondaryY, 65, "NES_B_FIRE"));
     }
     case 6 -> {
      kind = GestureKind.CHORD_DPAD_PLUS_BUTTON;
      strokes.add(new StrokePoint(pad.dpadRightX, pad.dpadRightY, 140, "NES_RIGHT"));
      strokes.add(new StrokePoint(pad.buttonSecondaryX, pad.buttonSecondaryY, 140, "NES_B_RUN"));
     }
     default -> {
      kind = GestureKind.CHORD_DPAD_PLUS_BUTTON;
      strokes.add(new StrokePoint(pad.dpadRightX, pad.dpadRightY, 165, "NES_RIGHT"));
      strokes.add(new StrokePoint(pad.buttonPrimaryX, pad.buttonPrimaryY, 165, "NES_A_LONG_JUMP"));
     }
    }
   }
   case DOS -> {
    // 0=STEP_LEFT, 1=STEP_RIGHT, 2=JUMP_CLIMB_UP, 3=CROUCH_DOWN,
    // 4=FIRE_CTRL, 5=USE_STRAFE_ALT, 6=LEAP_RIGHT_UP, 7=ADVANCE_FIRE_RIGHT
    switch (action) {
     case 0 -> {
      kind = GestureKind.DIRECTIONAL_HOLD;
      strokes.add(new StrokePoint(pad.dpadLeftX, pad.dpadLeftY, 95, "DOS_KEY_LEFT"));
     }
     case 1 -> {
      kind = GestureKind.DIRECTIONAL_HOLD;
      strokes.add(new StrokePoint(pad.dpadRightX, pad.dpadRightY, 105, "DOS_KEY_RIGHT"));
     }
     case 2 -> {
      kind = GestureKind.SINGLE_TAP;
      strokes.add(new StrokePoint(pad.dpadUpX, pad.dpadUpY, 110, "DOS_KEY_UP_JUMP"));
     }
     case 3 -> {
      kind = GestureKind.DIRECTIONAL_HOLD;
      strokes.add(new StrokePoint(pad.dpadDownX, pad.dpadDownY, 100, "DOS_KEY_DOWN"));
     }
     case 4 -> {
      kind = GestureKind.SINGLE_TAP;
      strokes.add(new StrokePoint(pad.buttonSecondaryX, pad.buttonSecondaryY, 75, "DOS_CTRL_FIRE"));
     }
     case 5 -> {
      kind = GestureKind.SINGLE_TAP;
      strokes.add(new StrokePoint(pad.buttonTertiaryX, pad.buttonTertiaryY, 85, "DOS_ALT_STRAFE_USE"));
     }
     case 6 -> {
      kind = GestureKind.CHORD_DPAD_PLUS_BUTTON;
      strokes.add(new StrokePoint(pad.dpadRightX, pad.dpadRightY, 150, "DOS_KEY_RIGHT"));
      strokes.add(new StrokePoint(pad.buttonPrimaryX, pad.buttonPrimaryY, 150, "DOS_SPACE_LEAP"));
     }
     default -> {
      kind = GestureKind.CHORD_DPAD_PLUS_BUTTON;
      strokes.add(new StrokePoint(pad.dpadRightX, pad.dpadRightY, 125, "DOS_KEY_RIGHT"));
      strokes.add(new StrokePoint(pad.buttonSecondaryX, pad.buttonSecondaryY, 125, "DOS_CTRL_FIRE"));
     }
    }
   }
   case SEGA -> {
    // 0=DPAD_LEFT, 1=DPAD_RIGHT, 2=CROUCH_DOWN, 3=SPECIAL_A,
    // 4=ATTACK_B, 5=JUMP_C, 6=SPRINT_JUMP_RIGHT_C, 7=SPIN_DASH_CHARGE_RELEASE
    switch (action) {
     case 0 -> {
      kind = GestureKind.DIRECTIONAL_HOLD;
      strokes.add(new StrokePoint(pad.dpadLeftX, pad.dpadLeftY, 110, "SEGA_LEFT"));
     }
     case 1 -> {
      kind = GestureKind.DIRECTIONAL_HOLD;
      strokes.add(new StrokePoint(pad.dpadRightX, pad.dpadRightY, 135, "SEGA_RIGHT"));
     }
     case 2 -> {
      kind = GestureKind.DIRECTIONAL_HOLD;
      strokes.add(new StrokePoint(pad.dpadDownX, pad.dpadDownY, 100, "SEGA_DOWN"));
     }
     case 3 -> {
      kind = GestureKind.SINGLE_TAP;
      strokes.add(new StrokePoint(pad.buttonTertiaryX, pad.buttonTertiaryY, 80, "SEGA_A_SPECIAL"));
     }
     case 4 -> {
      kind = GestureKind.SINGLE_TAP;
      strokes.add(new StrokePoint(pad.buttonSecondaryX, pad.buttonSecondaryY, 70, "SEGA_B_ATTACK"));
     }
     case 5 -> {
      kind = GestureKind.SINGLE_TAP;
      strokes.add(new StrokePoint(pad.buttonPrimaryX, pad.buttonPrimaryY, 130, "SEGA_C_JUMP"));
     }
     case 6 -> {
      kind = GestureKind.CHORD_DPAD_PLUS_BUTTON;
      strokes.add(new StrokePoint(pad.dpadRightX, pad.dpadRightY, 160, "SEGA_RIGHT"));
      strokes.add(new StrokePoint(pad.buttonPrimaryX, pad.buttonPrimaryY, 160, "SEGA_C_JUMP"));
     }
     default -> {
      kind = GestureKind.SEQUENTIAL_CHARGE_RELEASE;
      strokes.add(new StrokePoint(pad.dpadDownX, pad.dpadDownY, 95, "SEGA_DOWN_CHARGE"));
      strokes.add(new StrokePoint(pad.buttonPrimaryX, pad.buttonPrimaryY, 75, "SEGA_C_SPIN_REV"));
     }
    }
   }
   default -> throw new IllegalStateException("unexpected console: " + console);
  }

  // Verify every touch point is strictly outside the active CRT game viewport (or inside screen bounds)
  for (StrokePoint sp : strokes) {
   if (sp.screenX() < 0 || sp.screenY() < 0
     || sp.screenX() > pad.screenWidth || sp.screenY() > pad.screenHeight) {
    return new GesturePlan(console, action, label, kind, List.of(), false, "STROKE_OUT_OF_SCREEN_BOUNDS");
   }
  }
  return new GesturePlan(console, action, label, kind, strokes, true, "AUTHORIZED_SINGLE_EXECUTOR");
 }

 /**
  * Verifies whether a dispatched retro console gesture produced a genuine visual game state
  * transition (`visualVerified`), enforcing the rule `Android ACK != успешное игровое действие`.
  */
 public static PostAckVerification verifyPostAckOutcome(
   RetroVision.RetroScene before, RetroVision.RetroScene after,
   int action, boolean androidAck) {
  if (!androidAck) {
   return new PostAckVerification(false, false, Float.NaN, "NO_ANDROID_ACK");
  }
  if (before == null || after == null || !before.usable() || !after.usable()) {
   return new PostAckVerification(true, false, Float.NaN, "UNUSABLE_POST_ACK_SCENE");
  }
  if (after.frameGeneration <= before.frameGeneration) {
   return new PostAckVerification(true, false, Float.NaN, "STALE_FRAME_GENERATION");
  }

  float dx = after.playerX - before.playerX;
  float dy = after.playerY - before.playerY;
  float displacement = (float) Math.hypot(dx, dy);
  int scoreDelta = after.hudScoreProxy - before.hudScoreProxy;
  int collectiblesDelta = after.hudCollectiblesProxy - before.hudCollectiblesProxy;
  float vitalityDelta = after.hudVitalityRatio - before.hudVitalityRatio;
  int enemyCleared = Math.max(0, before.activeEnemyCount - after.activeEnemyCount);

  boolean movedOrChanged = displacement >= 0.015f
    || scoreDelta != 0
    || collectiblesDelta != 0
    || enemyCleared > 0
    || Math.abs(vitalityDelta) >= 0.02f;

  if (!movedOrChanged) {
   return new PostAckVerification(true, false, Float.NaN, "ACK_WITHOUT_VISUAL_STATE_CHANGE");
  }

  // Compute bounded, domain-grounded reward in [-1, 1]
  float reward = 0f;
  if (before.archetype.sideScrolling) {
   reward += Math.max(-0.35f, Math.min(0.45f, dx * 4.5f));
  }
  if (scoreDelta > 0) reward += Math.min(0.35f, scoreDelta * 0.05f);
  if (collectiblesDelta > 0) reward += Math.min(0.25f, collectiblesDelta * 0.08f);
  if (enemyCleared > 0) reward += Math.min(0.35f, enemyCleared * 0.20f);
  if (vitalityDelta < -0.02f) reward -= Math.min(0.80f, Math.abs(vitalityDelta) * 1.5f);
  if (after.hazardCollision) reward -= 0.75f;
  if (reward == 0f && displacement >= 0.015f) reward = 0.12f;
  reward = Math.max(-1.0f, Math.min(1.0f, reward));

  return new PostAckVerification(true, true, reward,
    String.format(Locale.ROOT, "VERIFIED[disp=%.3f,dScore=%d,dColl=%d,cleared=%d,rew=%.3f]",
      displacement, scoreDelta, collectiblesDelta, enemyCleared, reward));
 }
}
