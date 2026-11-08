package org.localpilot.android;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import org.localpilot.android.learning.*;
import org.localpilot.android.retro.*;

/**
 * End-to-end verification suite for NES, DOS, and SEGA Retro Console Training & Control
 * in LocalPilot-Android:
 * 1. Hardware, display, controller, and emulator package detection (NES, DOS, SEGA);
 * 2. Synthetic RGB frame rendering -> RetroVision CRT viewport & HUD extraction -> 64-D encoding;
 * 3. WeightConstructor vs Random Init across all 6 retro console archetypes;
 * 4. Multi-stage Curriculum Distillation + PPO/GAE training & frozen promotion gate;
 * 5. RetroInputBridge single-executor gesture planning (D-Pad, Chords, SEGA Spin Dash) & ActionGate;
 * 6. Post-ACK visual verification enforcement (`Android ACK != успешное игровое действие`).
 */
public final class RetroConsoleTest {
 private static int checks;

 private static void check(boolean ok, String msg) {
  checks++;
  if (!ok) throw new AssertionError(msg);
 }

 public static void main(String[] args) throws Exception {
  testPlatformAndEmulatorDetection();
  testPixelRenderingAndRetroVisionLoop();
  testWeightConstructionAndCurriculumAcross6RetroGames();
  testSingleExecutorGestureBridgeAndPostAckGuard();
  testExportAll16BundledCheckpoints();
  testMainMenuAutonomousSelfAndCrossGameTrainer();
  System.out.println("Retro Console Subsystem (NES / DOS / SEGA): " + checks
    + " assertions PASS; all 6 retro archetypes + 10 Market games (16 total) verified in JVM");
 }

 private static void testPlatformAndEmulatorDetection() {
  check(TaskSchema.GameId.marketGames().size() == 10, "10 Yandex Market games preserved");
  check(TaskSchema.GameId.retroGames().size() == 6, "6 Retro Console games registered (NES, DOS, SEGA)");
  check(TaskSchema.GameId.values().length == 16, "16 total games in TaskSchema.GameId");

  RetroPlatform.EmulatorTarget nesLab = RetroPlatform.detectTarget("org.localpilot.gamelab", "LocalPilot NES Mario");
  check(nesLab != null && nesLab.console() == RetroPlatform.Console.NES
    && nesLab.defaultArchetype() == RetroPlatform.Archetype.NES_PLATFORMER, "GameLab NES platformer detected");

  RetroPlatform.EmulatorTarget nesTanks = RetroPlatform.detectTarget("com.explusalpha.NesEmu", "Battle City Танки");
  check(nesTanks != null && nesTanks.console() == RetroPlatform.Console.NES
    && nesTanks.defaultArchetype() == RetroPlatform.Archetype.NES_TACTICAL_ARENA, "NES Battle City tactical arena detected");

  RetroPlatform.EmulatorTarget dosPrince = RetroPlatform.detectTarget("bruenor.magicbox", "Prince of Persia DOS");
  check(dosPrince != null && dosPrince.console() == RetroPlatform.Console.DOS
    && dosPrince.defaultArchetype() == RetroPlatform.Archetype.DOS_PLATFORM_EXPLORER, "Magic DosBox Prince of Persia detected");

  RetroPlatform.EmulatorTarget dosWolf = RetroPlatform.detectTarget("com.fishstix.dosbox", "Wolfenstein 3D VGA");
  check(dosWolf != null && dosWolf.console() == RetroPlatform.Console.DOS
    && dosWolf.defaultArchetype() == RetroPlatform.Archetype.DOS_CORRIDOR_ACTION, "DOSBox corridor shooter detected");

  RetroPlatform.EmulatorTarget segaSonic = RetroPlatform.detectTarget("com.explusalpha.MdEmu", "Sonic the Hedgehog SEGA");
  check(segaSonic != null && segaSonic.console() == RetroPlatform.Console.SEGA
    && segaSonic.defaultArchetype() == RetroPlatform.Archetype.SEGA_HIGH_SPEED_ACT, "MD.emu SEGA Sonic detected");

  RetroPlatform.EmulatorTarget segaSor = RetroPlatform.detectTarget("com.retroarch.aarch64", "Streets of Rage 2 SEGA");
  check(segaSor != null && segaSor.console() == RetroPlatform.Console.SEGA
    && segaSor.defaultArchetype() == RetroPlatform.Archetype.SEGA_BRAWLER_RUNGUN, "RetroArch SEGA Streets of Rage brawler detected");
 }

 private static void testPixelRenderingAndRetroVisionLoop() {
  for (RetroPlatform.Archetype arch : RetroPlatform.Archetype.values()) {
   RetroConsoleEnv env = new RetroConsoleEnv(
     arch, RealisticCurriculumEnv.CurriculumStage.STAGE_2_SPECIAL_OBJECTS, 1000L + arch.ordinal());
   int screenW = 360, screenH = 640; // Portrait phone with bottom touch pad overlay
   int[] rgb = env.renderRgbFrame(screenW, screenH, true);
   RetroVision.RetroScene scene1 = RetroVision.analyzeFrame(rgb, screenW, screenH, env.frameGeneration(), arch, null);
   check(scene1.usable(), "Rendered pixel frame is usable for " + arch);
   check(scene1.viewport.bottom() <= Math.round(screenH * 0.74f),
     "RetroVision strips bottom virtual gamepad from active CRT viewport for " + arch);

   ObservationEncoder.EncodedState encoded = ObservationEncoder.encodeRetro(arch.gameId, scene1);
   check(encoded.hasLegalAction(), "Encoded 64-D retro state has legal actions for " + arch);

   StudentPolicy policy = WeightConstructor.construct(arch.gameId);
   StudentPolicy.InferenceResult inf = policy.infer(encoded);
   check(inf.selectedAction >= 0 && inf.selectedAction < 8 && encoded.legalMask[inf.selectedAction],
     "Constructed StudentPolicy selects valid legal action on pixel-decoded scene for " + arch);

   // Step environment with selected action and render post-action frame
   float stepRew = env.stepPhysics(inf.selectedAction);
   check(Float.isFinite(stepRew), "Physics step reward finite for " + arch);
   int[] rgb2 = env.renderRgbFrame(screenW, screenH, true);
   RetroVision.RetroScene scene2 = RetroVision.analyzeFrame(rgb2, screenW, screenH, env.frameGeneration(), arch, scene1);
   RetroInputBridge.PostAckVerification ver = RetroInputBridge.verifyPostAckOutcome(scene1, scene2, inf.selectedAction, true);
   check(ver.androidAck && ver.visualVerified && Float.isFinite(ver.measuredReward),
     "Post-ACK pixel frame change verified for " + arch + ": " + ver.verificationReason);
  }
 }

 private static void testWeightConstructionAndCurriculumAcross6RetroGames() throws Exception {
  File tmpRoot = Files.createTempDirectory("lp-retro-train-").toFile();
  AdaptiveLearningEngine engine = new AdaptiveLearningEngine(tmpRoot);
  System.out.println("=== Retro Console Weight Construction & Curriculum Promotion (NES / DOS / SEGA) ===");
  for (TaskSchema.GameId g : TaskSchema.GameId.retroGames()) {
   StudentPolicy randNet = new StudentPolicy(g, 99L + g.ordinal(), true);
   StudentPolicy constNet = WeightConstructor.construct(g);

   ModelEvaluator.EvalMetrics randMetrics = ModelEvaluator.evaluatePolicy("RANDOM_INIT", g, randNet, 16, 7000L);
   ModelEvaluator.EvalMetrics constMetrics = ModelEvaluator.evaluatePolicy("CONSTRUCTED", g, constNet, 16, 7000L);
   check(constMetrics.safetyViolations == 0, "Zero safety violations on constructed retro policy " + g);
   check(constMetrics.teacherAgreementRate > randMetrics.teacherAgreementRate + 0.35,
     "Constructed weights beat random init on retro game " + g);

   ModelEvaluator.PromotionDecision promo = engine.trainCurriculumAndPromote(g, 6, 24, 20261106L + g.ordinal());
   check(promo.accepted && promo.candidateMetrics.safetyViolations == 0,
     "Curriculum + PPO/GAE promotion accepted for retro game " + g);
   System.out.println("  [" + g.id + "] " + randMetrics.format() + " -> " + promo.candidateMetrics.format());
  }
 }

 private static void testSingleExecutorGestureBridgeAndPostAckGuard() {
  RetroVision.ViewportBounds vp = new RetroVision.ViewportBounds(0, 0, 360, 440, 36, 440);
  RetroVision.VirtualPadLayout pad = RetroVision.VirtualPadLayout.standardForScreen(360, 640, vp);
  ActionGate gate = new ActionGate();

  // 1. Disarmed gate must block gesture dispatch (STOP invariant)
  RetroInputBridge.GesturePlan blocked = RetroInputBridge.planAction(
    RetroPlatform.Console.SEGA, 7, pad, gate, "com.explusalpha.MdEmu", "com.explusalpha.MdEmu");
  check(!blocked.authorized && "ACTION_GATE_DISARMED_STOP".equals(blocked.gateReason),
    "Disarmed ActionGate blocks retro gesture plan");

  // 2. Armed gate authorizes single-executor gestures for NES, DOS, and SEGA
  gate.arm("com.explusalpha.MdEmu", 1, 360, 640);
  RetroInputBridge.GesturePlan segaSpinDash = RetroInputBridge.planAction(
    RetroPlatform.Console.SEGA, 7, pad, gate, "com.explusalpha.MdEmu", "com.explusalpha.MdEmu");
  check(segaSpinDash.authorized
    && segaSpinDash.kind == RetroInputBridge.GestureKind.SEQUENTIAL_CHARGE_RELEASE
    && segaSpinDash.strokes.size() == 2,
    "SEGA Spin Dash maps to authorized Down-Charge + C-Release sequence");

  RetroInputBridge.GesturePlan nesLongJump = RetroInputBridge.planAction(
    RetroPlatform.Console.NES, 7, pad, gate, "com.explusalpha.MdEmu", "com.explusalpha.MdEmu");
  check(nesLongJump.authorized
    && nesLongJump.kind == RetroInputBridge.GestureKind.CHORD_DPAD_PLUS_BUTTON
    && nesLongJump.strokes.size() == 2,
    "NES Running Jump maps to authorized Right + A chord");

  RetroInputBridge.GesturePlan dosStrafe = RetroInputBridge.planAction(
    RetroPlatform.Console.DOS, 5, pad, gate, "com.explusalpha.MdEmu", "com.explusalpha.MdEmu");
  check(dosStrafe.authorized && dosStrafe.strokes.size() == 1,
    "DOS Alt Strafe/Use maps to authorized single tap");

  // 3. Wrong window package must fail closed
  RetroInputBridge.GesturePlan wrongPkg = RetroInputBridge.planAction(
    RetroPlatform.Console.SEGA, 1, pad, gate, "com.other.app", "com.explusalpha.MdEmu");
  check(!wrongPkg.authorized && "WINDOW_PACKAGE_MISMATCH".equals(wrongPkg.gateReason),
    "Mismatched foreground package blocks retro gesture dispatch");

  // 4. Unchanged post-ACK frame must NOT be marked visualVerified
  RetroConsoleEnv env = new RetroConsoleEnv(
    RetroPlatform.Archetype.NES_PLATFORMER, RealisticCurriculumEnv.CurriculumStage.STAGE_1_BASIC, 55L);
  RetroVision.RetroScene s0 = env.snapshotScene();
  RetroInputBridge.PostAckVerification unverified = RetroInputBridge.verifyPostAckOutcome(s0, s0, 1, true);
  check(unverified.androidAck && !unverified.visualVerified && Float.isNaN(unverified.measuredReward),
    "Android ACK with unchanged frame is rejected as unverified");
 }

 private static void testExportAll16BundledCheckpoints() throws Exception {
  File assetsDir = new File("app/src/main/assets");
  AdaptiveLearningEngine engine = new AdaptiveLearningEngine(assetsDir);
  for (TaskSchema.GameId g : TaskSchema.GameId.values()) {
   TaskSchema schema = TaskSchema.forGame(g);
   File ckpt = engine.checkpointFile(schema);
   if (!ckpt.exists()) {
    ModelEvaluator.PromotionDecision d = engine.trainCurriculumAndPromote(g, 8, 24, 20261106L + g.ordinal());
    check(d.accepted, "Promoted and saved bundled checkpoint for " + g.id);
   }
   StudentPolicy loaded = ModelCheckpoint.loadRecovering(ckpt, schema);
   check(loaded != null && ckpt.length() > 1000, "Verified SHA-256 checkpoint in assets for " + g.id);
  }
 }

 private static void testMainMenuAutonomousSelfAndCrossGameTrainer() throws Exception {
  File tmpRoot = Files.createTempDirectory("lp-self-trainer-").toFile();
  AdaptiveLearningEngine engine = new AdaptiveLearningEngine(tmpRoot);
  AutonomousSelfTrainer selfTrainer = engine.selfTrainer();
  check(selfTrainer != null && selfTrainer.isAutoSelfLearnEnabled(),
    "AutonomousSelfTrainer is enabled by default on Main Menu");

  selfTrainer.setActiveMainMenuConsole(RetroPlatform.Console.SEGA);
  check(selfTrainer.activeMainMenuConsole() == RetroPlatform.Console.SEGA,
    "Main Menu active retro console switched to SEGA");

  AutonomousSelfTrainer.SelfTrainReport report = selfTrainer.runFullSelfAndCrossGameCycle(20261106L);
  check(report.totalGamesTrained == 16, "Self-trainer trained all 16 games (6 NES/DOS/SEGA + 10 Market)");
  check(report.selfImprovedCount == 16, "All 16 games passed self-improvement promotion gate");
  check(report.crossGameTransfersAccepted >= 10,
    "Cross-game skill transfer accepted across Platform, Tactical, and Candidate families: " + report.crossGameTransfersAccepted);
  check(report.pixelVerifiedRetroSteps >= 30,
    "Pixel-rendered retro self-play verified >=30 steps across NES, DOS, SEGA: " + report.pixelVerifiedRetroSteps);
  System.out.println("  [MAIN_MENU_SELF_TRAIN] " + report.summaryText);
 }
}
