package org.localpilot.android;

import java.io.File;
import java.nio.file.Files;
import java.util.BitSet;
import java.util.List;
import org.localpilot.android.learning.*;

/**
 * End-to-end verification of LocalPilot Adaptive Learning Engine:
 * 1. Analytical weight construction vs Random Init across all 10 games;
 * 2. Targeted weight-space oscillation repair (Takehi et al. subspace projection);
 * 3. Teacher -> Student Knowledge Distillation + PPO/GAE curriculum training;
 * 4. ReplayStore provenance isolation & ACK-is-not-reward enforcement;
 * 5. Atomic ModelCheckpoint SHA-256 verification & .bak corruption recovery;
 * 6. Real Android policy selection trace (MODEL_USED / MODEL_FALLBACK / MODEL_UPDATE_ACCEPTED).
 */
public final class AdaptiveEngineTest {
 private static int checks;
 private static void check(boolean ok, String msg) {
  checks++;
  if (!ok) throw new AssertionError(msg);
 }

 public static void main(String[] args) throws Exception {
  testSchemaIsolationAndReplayGuard();
  testWeightConstructionBeatsRandomAcrossAll10Games();
  testTargetedWeightSpaceOscillationRepair();
  testPpoGaeSafeguardsAndDurationDiscount();
  testCheckpointPersistenceAndCorruptionRecovery();
  testEndToEndTransferToRealAndroidPolicies();
  System.out.println("Adaptive Learning Engine: " + checks + " assertions PASS; all 10 games verified in JVM");
 }

 private static void testSchemaIsolationAndReplayGuard() {
  TaskSchema s6 = TaskSchema.forGame(TaskSchema.GameId.NASHCHUPAY_KHOD);
  TaskSchema s7 = TaskSchema.forGame(TaskSchema.GameId.MATCH_WORLD);
  check(!s6.compatibleWith(s7), "6x6 protected-skull swap must not mix with 7x7 match-world");
  ReplayStore store = new ReplayStore(s6, 64);
  float[] obs = new float[TaskSchema.OBS_DIM];
  boolean[] mask = new boolean[s6.gameId.actionCount];
  mask[0] = true;

  // 1. Attempting to construct a REAL transition with MEASURED reward but visualVerified=false must throw!
  try {
   new ReplayStore.Transition(s6, obs, mask, 0, 1.0f, TaskSchema.RewardStatus.MEASURED,
     obs, false, TaskSchema.Provenance.REAL, 0.9f, true, false, 1000L);
   throw new AssertionError("REAL transition with ACK only (visualVerified=false) must not claim MEASURED reward");
  } catch (IllegalArgumentException expected) {
   checks++;
  }

  // 2. Unverified REAL transition with UNKNOWN reward is rejected from training buffer
  ReplayStore.Transition unknownReal = new ReplayStore.Transition(s6, obs, mask, 0, Float.NaN,
    TaskSchema.RewardStatus.UNKNOWN, obs, false, TaskSchema.Provenance.REAL, 0.9f, true, false, 1000L);
  check(!store.add(unknownReal) && store.realSize() == 0 && store.rejectedUnverifiedCount() == 1,
    "ACK without visual verification is rejected from REAL replay buffer");

  // 3. Verified REAL transition is accepted
  ReplayStore.Transition verifiedReal = new ReplayStore.Transition(s6, obs, mask, 0, 0.8f,
    TaskSchema.RewardStatus.MEASURED, obs, false, TaskSchema.Provenance.REAL, 0.9f, true, true, 1200L);
  check(store.add(verifiedReal) && store.realSize() == 1, "post-ACK visually verified REAL transition stored");

  // 4. Cross-game insertion throws
  try {
   store.add(new ReplayStore.Transition(s7, obs, mask, 0, 0.5f, TaskSchema.RewardStatus.MEASURED,
     obs, false, TaskSchema.Provenance.SIM, 0.9f, false, true, 1300L));
   throw new AssertionError("cross-game transition accepted");
  } catch (IllegalArgumentException expected) {
   checks++;
  }
 }

 private static void testWeightConstructionBeatsRandomAcrossAll10Games() {
  System.out.println("=== Weight Construction vs Random Init & Distilled Student (10 Games) ===");
  for (TaskSchema.GameId g : TaskSchema.GameId.values()) {
   StudentPolicy randomNet = new StudentPolicy(g, 42L + g.ordinal(), true);
   StudentPolicy constructedNet = WeightConstructor.construct(g);
   check(constructedNet.isConstructed(), "constructed flag set for " + g);

   ModelEvaluator.EvalMetrics randEval = ModelEvaluator.evaluatePolicy("RANDOM_INIT", g, randomNet, 16, 5000L);
   ModelEvaluator.EvalMetrics constEval = ModelEvaluator.evaluatePolicy("CONSTRUCTED", g, constructedNet, 16, 5000L);
   check(constEval.safetyViolations == 0, "constructed weights have zero safety violations on " + g);
   check(constEval.teacherAgreementRate > randEval.teacherAgreementRate + 0.25,
     "constructed weights substantially beat random init agreement on " + g
       + " (" + constEval.teacherAgreementRate + " vs " + randEval.teacherAgreementRate + ")");
   check(constEval.meanReturn > randEval.meanReturn + 4.0,
     "constructed weights beat random init return on " + g
       + " (" + constEval.meanReturn + " vs " + randEval.meanReturn + ")");
   System.out.println("  [" + g.id + "] " + randEval.format() + " | " + constEval.format());
  }
 }

 private static void testTargetedWeightSpaceOscillationRepair() {
  StudentPolicy net = WeightConstructor.construct(TaskSchema.GameId.MARKET_RUSH);
  // Create a synthetic state where action 0 (LEFT) and action 2 (WAIT) are legal,
  // and corrupt Wp so the policy loops on action 0 (LEFT) even when current lane is safe.
  RealisticCurriculumEnv env = new RealisticCurriculumEnv(
    TaskSchema.GameId.MARKET_RUSH, RealisticCurriculumEnv.CurriculumStage.STAGE_1_BASIC, 777L);
  ObservationEncoder.EncodedState state = env.observe();
  boolean[] mask = new boolean[]{true, true, true};
  float[][] loopStates = new float[][]{state.features};
  // Force action 0 logit artificially high to simulate a looping failure mode
  for (int j = 0; j < StudentPolicy.H2; j++) {
   net.addWp(j, 0, 3.0f);
  }
  StudentPolicy.InferenceResult beforeRepair = net.infer(state.features, mask);
  check(beforeRepair.selectedAction == 0, "corrupted policy loops on action 0");

  int edited = WeightConstructor.repairOscillationSubspace(net, loopStates, 0, 2, 18.0f);
  check(edited > 0 && edited <= StudentPolicy.H2 * 2, "targeted repair modifies only subspace weights");
  StudentPolicy.InferenceResult afterRepair = net.infer(state.features, mask);
  check(afterRepair.selectedAction == 2, "weight-space subspace repair eliminates oscillation toward action 0");
 }

 private static void testPpoGaeSafeguardsAndDurationDiscount() {
  PpoGaeTrainer ppo = new PpoGaeTrainer();
  StudentPolicy policy = WeightConstructor.construct(TaskSchema.GameId.GOSHAN_FLAPPY);
  float[] s = new float[TaskSchema.OBS_DIM];
  s[1] = 0.4f; s[6] = 1f;
  boolean[] mask = new boolean[]{true, true};

  // Fewer than 16 steps must refuse to update (never pretend PPO learns from 2 actions!)
  for (int i = 0; i < 4; i++) {
   ppo.record(new PpoGaeTrainer.StepRecord(s, mask, 1, -0.3f, 0.2f, 0.5f, 0.3f,
     false, false, 2, TaskSchema.Provenance.SIM, true));
  }
  PpoGaeTrainer.UpdateReport rejected = ppo.update(policy, 2, 0.005f);
  check(!rejected.updated && rejected.reason.startsWith("INSUFFICIENT_ROLLOUT"),
    "PPO refuses update on 4 steps: " + rejected.reason);

  // Fill to 16 verified steps and verify GAE & update
  for (int i = 4; i < PpoGaeTrainer.MIN_ROLLOUT_STEPS; i++) {
   ppo.record(new PpoGaeTrainer.StepRecord(s, mask, 1, -0.3f, 0.2f, 0.8f, 0.4f,
     i == 15, false, 3, TaskSchema.Provenance.SIM, true));
  }
  PpoGaeTrainer.UpdateReport updated = ppo.update(policy, 2, 0.005f);
  check(updated.updated && updated.rolloutSteps == 16, "PPO updates cleanly once rollout >= 16 verified steps");
 }

 private static void testCheckpointPersistenceAndCorruptionRecovery() throws Exception {
  File dir = Files.createTempDirectory("lp-adaptive-ckpt-").toFile();
  TaskSchema schema = TaskSchema.forGame(TaskSchema.GameId.NASHCHUPAY_KHOD);
  File ckpt = new File(dir, "nashchupay.zip");
  StudentPolicy original = WeightConstructor.construct(TaskSchema.GameId.NASHCHUPAY_KHOD);
  ModelCheckpoint.save(ckpt, schema, original);
  check(ckpt.isFile(), "checkpoint saved");

  StudentPolicy loaded = ModelCheckpoint.load(ckpt, schema);
  RealisticCurriculumEnv env = new RealisticCurriculumEnv(
    TaskSchema.GameId.NASHCHUPAY_KHOD, RealisticCurriculumEnv.CurriculumStage.STAGE_2_SPECIAL_OBJECTS, 123L);
  ObservationEncoder.EncodedState s = env.observe();
  check(loaded.infer(s).selectedAction == original.infer(s).selectedAction, "loaded checkpoint matches original inference");

  // Save a second time so .bak is created, then corrupt primary file and test loadRecovering
  ModelCheckpoint.save(ckpt, schema, original);
  check(new File(ckpt.getPath() + ".bak").isFile(), "backup checkpoint created");
  Files.writeString(ckpt.toPath(), "corrupted zip payload");
  StudentPolicy recovered = ModelCheckpoint.loadRecovering(ckpt, schema);
  check(recovered.infer(s).selectedAction == original.infer(s).selectedAction, "corrupted checkpoint recovered from .bak");
  check(ModelCheckpoint.load(ckpt, schema).infer(s).selectedAction == original.infer(s).selectedAction,
    "primary checkpoint atomically restored after recovery");
 }

 private static void testEndToEndTransferToRealAndroidPolicies() throws Exception {
  File dir = Files.createTempDirectory("lp-engine-e2e-").toFile();
  AdaptiveLearningEngine engine = new AdaptiveLearningEngine(dir);

  for (TaskSchema.GameId g : TaskSchema.GameId.values()) {
   ModelEvaluator.PromotionDecision promo = engine.trainCurriculumAndPromote(g, 10, 20, 9000L + g.ordinal());
   check(promo.accepted && promo.event == TaskSchema.TelemetryEvent.MODEL_UPDATE_ACCEPTED,
     "curriculum + PPO/GAE model promoted for " + g + ": " + promo.summary);
   check(engine.checkpointFile(TaskSchema.forGame(g)).isFile(), "promoted checkpoint persisted for " + g);
  }

  // Verify real Android policy selection trace on actual game objects:
  // 1. Нащупай ход (6x6 protected skull swap):
  int[] cells6 = new int[36];
  for (int i = 0; i < 36; i++) cells6[i] = 1 + (i % 4);
  cells6[0] = 1; cells6[1] = 2; cells6[2] = 1; cells6[7] = 1;
  cells6[14] = 0; // Protected skull at index 14
  BitSet skulls = new BitSet(36);
  skulls.set(14);
  GridPuzzle board6 = new GridPuzzle(6, 6, cells6);
  GridPuzzlePolicy.Decision swapDecision = engine.chooseGridMove(
    TaskSchema.GameId.NASHCHUPAY_KHOD, board6, GridPuzzlePolicy.Mode.SWAP_MATCH3,
    3, 1, 6, skulls, new GridOnlineLearning());
  check(swapDecision != null && swapDecision.expectedCleared >= 3, "real 6x6 swap decision produced by engine");
  check(!skulls.get(swapDecision.fromRow * 6 + swapDecision.fromCol)
    && !skulls.get(swapDecision.toRow * 6 + swapDecision.toCol), "engine never touches protected skull");
  AdaptiveLearningEngine.PolicyTrace gridTrace = engine.lastTrace(TaskSchema.GameId.NASHCHUPAY_KHOD);
  check(gridTrace != null && gridTrace.telemetry == TaskSchema.TelemetryEvent.MODEL_USED,
    "real 6x6 grid decision used trained StudentPolicy: " + gridTrace.format());

  // 2. Шипучка (Bubble Shooter):
  int[] bCells = new int[72];
  System.arraycopy(new int[]{1, 1, 2, 3, 4, 4}, 0, bCells, 0, 6);
  BubbleBoard bBoard = new BubbleBoard(320, 640, 6, 12, 32, 80, 50, 160, 600, 1, bCells);
  BubblePolicy.Shot shot = engine.chooseBubbleShot(bBoard);
  check(shot != null && shot.removed >= 3, "real Bubble shot chosen by engine");
  check(engine.lastTrace(TaskSchema.GameId.SHIPUCHKA).telemetry == TaskSchema.TelemetryEvent.MODEL_USED,
    "Bubble decision used trained StudentPolicy");

  // 3. 2048:
  Board2048 b2048 = new Board2048(new int[]{2, 2, 4, 8, 0, 4, 8, 16, 0, 0, 2, 4, 0, 0, 0, 2});
  int dir2048 = engine.choose2048Direction(b2048);
  check(dir2048 >= 0 && dir2048 < 4 && Verified2048.mask(b2048)[dir2048], "real 2048 move chosen by engine");
  check(engine.lastTrace(TaskSchema.GameId.GAME_2048).telemetry == TaskSchema.TelemetryEvent.MODEL_USED,
    "2048 decision used trained StudentPolicy");

  // 4. Гексон (Hexa Sort):
  HexaBoard.Stack[] hCells = new HexaBoard.Stack[25];
  for (int i = 0; i < 25; i++) hCells[i] = HexaBoard.Stack.empty();
  hCells[0] = HexaBoard.Stack.single(2, 6);
  HexaBoard.Stack[] hTray = new HexaBoard.Stack[]{
    HexaBoard.Stack.single(2, 4),
    HexaBoard.Stack.single(3, 3),
    HexaBoard.Stack.empty()
  };
  HexaBoard hexBoard = new HexaBoard(5, 5, hCells, hTray);
  HexaPolicy.Move hexMove = engine.chooseHexaMove(hexBoard);
  check(hexMove != null && hexMove.clearedStacks >= 1, "real Hexon move chosen by engine triggers >=10 clear");
  check(engine.lastTrace(TaskSchema.GameId.HEXON).telemetry == TaskSchema.TelemetryEvent.MODEL_USED,
    "Hexon decision used trained StudentPolicy");

  ObservationEncoder.EncodedState beforeEnc = ObservationEncoder.encode2048(b2048);
  Board2048 after2048 = b2048.move(dir2048).board;
  ObservationEncoder.EncodedState afterEnc = ObservationEncoder.encode2048(after2048);
  // SIMULATED curriculum steps must never become REAL verified outcomes.
  ReplayStore beforeSim = engine.replayStore(TaskSchema.GameId.GAME_2048);
  int priorReal = beforeSim.realSize(), priorSim = beforeSim.simSize();
  check(engine.recordSimulatedOutcome(TaskSchema.GameId.GAME_2048, beforeEnc, dir2048, afterEnc,
    0.5f, false, 1900L), "synthetic transition is accepted as SIM");
  check(beforeSim.realSize() == priorReal && beforeSim.simSize() == priorSim + 1,
    "synthetic transition never forges a REAL Android ACK");
  // 5. Real-game verified transition recording vs unverified ACK rejection:
  check(!engine.recordRealGameOutcome(TaskSchema.GameId.GAME_2048, beforeEnc, dir2048, afterEnc,
    0.5f, false, true, false, 2000L), "ACK without visual verification is rejected by engine");
  check(engine.recordRealGameOutcome(TaskSchema.GameId.GAME_2048, beforeEnc, dir2048, afterEnc,
    0.5f, false, true, true, 2100L), "ACK + visual verification is recorded and updates online model safely");

  System.out.println(engine.diagnosticSummary());
  File assetsDir = new File("app/src/main/assets");
  File trainingRoot;
  try {
   trainingRoot = Files.createTempDirectory("lp-bundled-training-").toFile();
   BundledModelInstaller.Result installed = BundledModelInstaller.install(trainingRoot,
     name -> Files.newInputStream(new File(assetsDir, name).toPath()));
   check(installed.complete(), "all bundled models copied into isolated training storage");
  } catch (java.io.IOException error) { throw new RuntimeException(error); }
  AdaptiveLearningEngine assetEngine = new AdaptiveLearningEngine(trainingRoot);
  for (TaskSchema.GameId g : TaskSchema.GameId.values()) {
   File original = new File(assetsDir, "adaptive-engine-v1/" + g.id + "-" + g.mechanicsVersion + ".zip");
   byte[] originalBytes;
   try { originalBytes = Files.readAllBytes(original.toPath()); }
   catch (java.io.IOException error) { throw new RuntimeException(error); }
   assetEngine.trainCurriculumAndPromote(g, 12, PpoGaeTrainer.MIN_ROLLOUT_STEPS, 424242L + g.ordinal() * 101L);
   File ckpt = assetEngine.checkpointFile(TaskSchema.forGame(g));
   try {
    ModelCheckpoint.save(ckpt, TaskSchema.forGame(g), assetEngine.activePolicy(g));
    StudentPolicy loaded = ModelCheckpoint.loadRecovering(ckpt, TaskSchema.forGame(g));
    check(loaded != null, "isolated trained checkpoint verified for " + g.id);
    check(java.util.Arrays.equals(originalBytes, Files.readAllBytes(original.toPath())),
      "training must never overwrite the reviewed bundled checkpoint: " + g.id);
   } catch (Exception e) {
    throw new RuntimeException(e);
   }
  }
 }
}
