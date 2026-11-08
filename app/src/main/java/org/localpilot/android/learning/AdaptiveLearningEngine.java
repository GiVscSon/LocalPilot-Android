package org.localpilot.android.learning;

import java.io.File;
import java.io.IOException;
import java.util.*;
import org.localpilot.android.*;
import org.localpilot.android.retro.*;
import org.localpilot.android.rl.ArcadeEnv;

/**
 * LocalPilot Adaptive Learning Engine — Main Neural Decision & Learning Coordinator.
 *
 * Unifies:
 * 1. Programmatic Weight Construction (`WeightConstructor`) for all 10 games;
 * 2. Teacher -> Student Knowledge Distillation (`TeacherPolicy` -> `StudentPolicy`);
 * 3. PPO + GAE (`PpoGaeTrainer`) & Replay-based online LoRA adaptation (`ReplayStore`);
 * 4. Atomic SHA-256 Checkpoints (`ModelCheckpoint`) and Frozen Promotion Gate (`ModelEvaluator`);
 * 5. Direct integration into real Android game policies with hybrid safety fallback and
 *    explicit telemetry (`MODEL_USED`, `MODEL_FALLBACK`, `MODEL_UPDATE_ACCEPTED`, `MODEL_UPDATE_REJECTED`).
 */
public final class AdaptiveLearningEngine {
 public static final float CONFIDENCE_THRESHOLD = 0.52f;

 public static final class PolicyTrace {
  public final TaskSchema.GameId gameId;
  public final TaskSchema.TelemetryEvent telemetry;
  public final int selectedAction;
  public final int teacherAction;
  public final float confidence;
  public final float valueEstimate;
  public final long modelSteps;
  public final String reason;

  PolicyTrace(TaskSchema.GameId gameId, TaskSchema.TelemetryEvent telemetry,
    int selectedAction, int teacherAction, float confidence, float valueEstimate,
    long modelSteps, String reason) {
   this.gameId = gameId;
   this.telemetry = telemetry;
   this.selectedAction = selectedAction;
   this.teacherAction = teacherAction;
   this.confidence = confidence;
   this.valueEstimate = valueEstimate;
   this.modelSteps = modelSteps;
   this.reason = reason;
  }

  public String format() {
   return "TRACE[" + gameId.id + "]: " + telemetry
     + " action=" + selectedAction + " teacher=" + teacherAction
     + String.format(Locale.ROOT, " conf=%.3f val=%.3f steps=%d", confidence, valueEstimate, modelSteps)
     + " reason=" + reason;
  }
 }

 private static volatile AdaptiveLearningEngine GLOBAL = new AdaptiveLearningEngine(null);
 public static AdaptiveLearningEngine global() { return GLOBAL; }
 public static synchronized AdaptiveLearningEngine initGlobal(File storageRoot) {
  if (storageRoot != null && (GLOBAL.storageRoot == null || !storageRoot.equals(GLOBAL.storageRoot))) {
   GLOBAL = new AdaptiveLearningEngine(storageRoot);
  }
  return GLOBAL;
 }
 public File storageRoot() { return storageRoot; }

 private final File storageRoot;
 private final Map<TaskSchema.GameId, StudentPolicy> activePolicies = new EnumMap<>(TaskSchema.GameId.class);
 private final Map<TaskSchema.GameId, ReplayStore> replayStores = new EnumMap<>(TaskSchema.GameId.class);
 private final Map<TaskSchema.GameId, PpoGaeTrainer> ppoTrainers = new EnumMap<>(TaskSchema.GameId.class);
 private final Map<TaskSchema.GameId, PolicyTrace> lastTraces = new EnumMap<>(TaskSchema.GameId.class);
 private final long[] telemetryCounts = new long[TaskSchema.TelemetryEvent.values().length];
 private final Map<ArcadeProfile, RealGameNeuralCritic> neuralCritics = new EnumMap<>(ArcadeProfile.class);
 private final VisionV2Core visionV2 = new VisionV2Core();
 private final VisionNoveltyMemory noveltyMemory = new VisionNoveltyMemory();
 private final TextRepetitionGuard repetitionGuard = new TextRepetitionGuard();
 private final AutonomousSelfTrainer selfTrainer;
 private final UnifiedSimulatorHub simulatorHub;

 public AdaptiveLearningEngine(File storageRoot) {
  this.storageRoot = storageRoot;
  for (TaskSchema.GameId g : TaskSchema.GameId.values()) {
   TaskSchema schema = TaskSchema.forGame(g);
   ReplayStore store = new ReplayStore(schema, 2048);
   replayStores.put(g, store);
   ppoTrainers.put(g, new PpoGaeTrainer());
   StudentPolicy policy = null;
   if (storageRoot != null) {
    File ckpt = checkpointFile(schema);
    try {
     policy = ModelCheckpoint.loadRecovering(ckpt, schema);
    } catch (IOException ignored) {
     policy = null;
    }
   }
   if (policy == null) {
    policy = WeightConstructor.construct(g);
   }
   activePolicies.put(g, policy);
  }
  for (ArcadeProfile ap : ArcadeProfile.values()) {
   if (ap != ArcadeProfile.ARI) neuralCritics.put(ap, new RealGameNeuralCritic(ap));
  }
  this.selfTrainer = new AutonomousSelfTrainer(this);
  this.simulatorHub = new UnifiedSimulatorHub(this);
 }

 public RealGameNeuralCritic neuralCritic(ArcadeProfile profile) { return neuralCritics.get(profile); }
 public VisionV2Core visionV2() { return visionV2; }
 public VisionNoveltyMemory noveltyMemory() { return noveltyMemory; }
 public TextRepetitionGuard repetitionGuard() { return repetitionGuard; }


 public File checkpointFile(TaskSchema schema) {
  if (storageRoot == null) return null;
  File dir = new File(storageRoot, "adaptive-engine-v1");
  return new File(dir, schema.gameId.id + "-" + schema.gameId.mechanicsVersion + ".zip");
 }


 public AutonomousSelfTrainer selfTrainer() {
  return selfTrainer;
 }

 public UnifiedSimulatorHub simulatorHub() {
  return simulatorHub;
 }

 synchronized void replaceActivePolicy(TaskSchema.GameId gameId, StudentPolicy policy) {
  if (gameId != null && policy != null) {
   activePolicies.put(gameId, policy);
   telemetryCounts[TaskSchema.TelemetryEvent.MODEL_UPDATE_ACCEPTED.ordinal()]++;
  }
 }

 public synchronized StudentPolicy activePolicy(TaskSchema.GameId gameId) {
  return activePolicies.get(gameId);
 }

 public synchronized ReplayStore replayStore(TaskSchema.GameId gameId) {
  return replayStores.get(gameId);
 }

 public synchronized PolicyTrace lastTrace(TaskSchema.GameId gameId) {
  return lastTraces.get(gameId);
 }

 public synchronized long telemetryCount(TaskSchema.TelemetryEvent event) {
  return telemetryCounts[event.ordinal()];
 }

 /**
  * Core decision selector used by real Android game loops.
  * Evaluates the active `StudentPolicy` on `encoded`, checks legality mask and confidence,
  * and falls back safely to `TeacherPolicy` if confidence < threshold or illegal.
  */
 public synchronized PolicyTrace selectAction(ObservationEncoder.EncodedState encoded) {
  if (encoded == null || !encoded.hasLegalAction()) {
   PolicyTrace trace = new PolicyTrace(encoded == null ? TaskSchema.GameId.SHIPUCHKA : encoded.gameId,
     TaskSchema.TelemetryEvent.MODEL_FALLBACK, -1, -1, 0f, 0f, 0L, "NO_LEGAL_ACTION");
   if (encoded != null) lastTraces.put(encoded.gameId, trace);
   telemetryCounts[TaskSchema.TelemetryEvent.MODEL_FALLBACK.ordinal()]++;
   return trace;
  }
  StudentPolicy policy = activePolicies.get(encoded.gameId);
  TeacherPolicy.Target teacher = TeacherPolicy.evaluate(encoded, 0.5f);
  StudentPolicy.InferenceResult inf = policy.infer(encoded);

  boolean validChoice = inf.selectedAction >= 0
    && inf.selectedAction < encoded.legalMask.length
    && encoded.legalMask[inf.selectedAction]
    && inf.confidence >= CONFIDENCE_THRESHOLD;

  if (validChoice) {
   telemetryCounts[TaskSchema.TelemetryEvent.MODEL_USED.ordinal()]++;
   PolicyTrace trace = new PolicyTrace(encoded.gameId, TaskSchema.TelemetryEvent.MODEL_USED,
     inf.selectedAction, teacher.bestAction, inf.confidence, inf.valueEstimate,
     inf.gradientSteps, "student-" + (inf.constructedWeights ? "constructed+tuned" : "learned"));
   lastTraces.put(encoded.gameId, trace);
   return trace;
  }
  telemetryCounts[TaskSchema.TelemetryEvent.MODEL_FALLBACK.ordinal()]++;
  PolicyTrace fallback = new PolicyTrace(encoded.gameId, TaskSchema.TelemetryEvent.MODEL_FALLBACK,
    teacher.bestAction, teacher.bestAction, 1.0f, teacher.valueTarget,
    inf.gradientSteps, "fallback-" + teacher.rationale);
  lastTraces.put(encoded.gameId, fallback);
  return fallback;
 }

 /**
  * Trains a candidate copy of `gameId`'s policy on the 4-stage curriculum via Teacher Distillation
  * + PPO/GAE, then runs the frozen `ModelEvaluator` promotion gate before updating the active model.
  */
 public synchronized ModelEvaluator.PromotionDecision trainCurriculumAndPromote(
   TaskSchema.GameId gameId, int distillEpisodes, int ppoRolloutSteps, long seed) {
  StudentPolicy current = activePolicies.get(gameId);
  StudentPolicy candidate = current.copy();
  ArcadeEnv.Rng rng = new ArcadeEnv.Rng(seed);
  ReplayStore store = replayStores.get(gameId);
  TaskSchema schema = store.schema();

  // Phase A: Curriculum Knowledge Distillation across all 4 stages
  RealisticCurriculumEnv.CurriculumStage[] stages = RealisticCurriculumEnv.CurriculumStage.values();
  for (int ep = 0; ep < distillEpisodes; ep++) {
   RealisticCurriculumEnv env = new RealisticCurriculumEnv(gameId, stages[ep % stages.length], rng.next());
   for (int step = 0; step < 12; step++) {
    ObservationEncoder.EncodedState s = env.observe();
    TeacherPolicy.Target target = TeacherPolicy.evaluate(s, 0.15f);
    if (target.bestAction >= 0) {
     candidate.imitationStep(s.features, s.legalMask, target.bestAction, target.valueTarget, 0.0015f);
     RealisticCurriculumEnv.StepOutcome out = env.step(target.bestAction);
     store.add(new ReplayStore.Transition(schema, s.features, s.legalMask, target.bestAction,
       out.reward, TaskSchema.RewardStatus.MEASURED, out.nextState.features, out.done,
       TaskSchema.Provenance.SIM, s.confidence, false, true, step * 100L));
     if (out.done) break;
    }
   }
  }

  // Phase B: On-Policy PPO + GAE refinement
  if (ppoRolloutSteps >= PpoGaeTrainer.MIN_ROLLOUT_STEPS) {
   PpoGaeTrainer ppo = ppoTrainers.get(gameId);
   ppo.clear();
   RealisticCurriculumEnv env = new RealisticCurriculumEnv(
     gameId, RealisticCurriculumEnv.CurriculumStage.STAGE_4_DOMAIN_RANDOMIZATION, rng.next());
   for (int t = 0; t < ppoRolloutSteps; t++) {
    ObservationEncoder.EncodedState s = env.observe();
    StudentPolicy.InferenceResult inf = candidate.infer(s);
    int act = inf.selectedAction;
    if (act < 0) break;
    float oldLogProb = (float) Math.log(Math.max(1e-6f, inf.maskedProbs[act]));
    RealisticCurriculumEnv.StepOutcome out = env.step(act);
    StudentPolicy.InferenceResult nextInf = candidate.infer(out.nextState);
    ppo.record(new PpoGaeTrainer.StepRecord(s.features, s.legalMask, act,
      oldLogProb, inf.valueEstimate, out.reward, nextInf.valueEstimate,
      out.done, false, out.durationFrames, TaskSchema.Provenance.SIM, true));
    if (out.done) env.reset(rng.next());
   }
   ppo.update(candidate, 1, 0.001f);
  }

  // Phase C: Frozen evaluation & safety promotion gate
  ModelEvaluator.EvalMetrics preCheck = ModelEvaluator.evaluatePolicy(
    "PRE_CHECK", gameId, candidate, 16, seed ^ 0x9e3779b9L);
  ModelEvaluator.EvalMetrics baseCheck = ModelEvaluator.evaluatePolicy(
    "BASE_CHECK", gameId, current, 16, seed ^ 0x9e3779b9L);
  if (preCheck.teacherAgreementRate < baseCheck.teacherAgreementRate
    || preCheck.meanReturn < baseCheck.meanReturn) {
   candidate = current.copy();
  }
  ModelEvaluator.PromotionDecision decision = ModelEvaluator.checkPromotionGate(
    gameId, current, candidate, 16, seed ^ 0x6a09e667f3bcc909L);
  telemetryCounts[decision.event.ordinal()]++;
  if (decision.accepted) {
   activePolicies.put(gameId, candidate);
   if (storageRoot != null) {
    try {
     ModelCheckpoint.save(checkpointFile(schema), schema, candidate);
    } catch (IOException ignored) {
     // Keep in-memory promoted model even if disk is unavailable
    }
   }
  }
  return decision;
 }

 /**
  * Record curriculum/retro simulation experience ONLY as SIM. A virtual render
  * and its simulator response are never Android ACK or a REAL observation.
  * Promoting learned weights is performed separately through the frozen
  * trainCurriculumAndPromote()/ModelEvaluator gate, not through real-game LoRA.
  */
 public synchronized boolean recordSimulatedOutcome(TaskSchema.GameId gameId,
   ObservationEncoder.EncodedState before, int action, ObservationEncoder.EncodedState after,
   float measuredReward, boolean done, long timestampMs) {
  if (gameId == null || before == null || after == null || before.gameId != gameId
    || after.gameId != gameId || !Float.isFinite(measuredReward)) return false;
  ReplayStore store = replayStores.get(gameId);
  if (store == null) return false;
  ReplayStore.Transition tr = new ReplayStore.Transition(store.schema(),
    before.features, before.legalMask, action, measuredReward,
    TaskSchema.RewardStatus.MEASURED, after.features, done,
    TaskSchema.Provenance.SIM, Math.min(before.confidence, after.confidence),
    false, false, timestampMs);
  return store.add(tr);
 }

 /**
  * Records a real-game transition after an action was dispatched.
  * If `androidAck && visualVerified` is true, records a MEASURED reward and safely performs
  * a bounded LoRA online update gated by `ModelEvaluator`. If visual verification is missing,
  * marks reward as `UNKNOWN` and refuses to update weights.
  */
 public synchronized boolean recordRealGameOutcome(TaskSchema.GameId gameId,
   ObservationEncoder.EncodedState before, int action, ObservationEncoder.EncodedState after,
   float measuredReward, boolean done, boolean androidAck, boolean visualVerified, long timestampMs) {
  if (before == null || after == null || before.gameId != gameId || after.gameId != gameId)
   throw new IllegalArgumentException("mismatched real-game transition states");
  ReplayStore store = replayStores.get(gameId);
  TaskSchema schema = store.schema();
  TaskSchema.RewardStatus status = (androidAck && visualVerified && Float.isFinite(measuredReward))
    ? TaskSchema.RewardStatus.MEASURED : TaskSchema.RewardStatus.UNKNOWN;
  ReplayStore.Transition tr = new ReplayStore.Transition(schema,
    before.features, before.legalMask, action,
    status == TaskSchema.RewardStatus.MEASURED ? measuredReward : Float.NaN,
    status, after.features, done, TaskSchema.Provenance.REAL,
    Math.min(before.confidence, after.confidence), androidAck, visualVerified, timestampMs);
  boolean stored = store.add(tr);
  if (!stored) return false;

  // Safe online adaptation on verified real transition with rollback gate:
  StudentPolicy current = activePolicies.get(gameId);
  StudentPolicy candidate = current.copy();
  TeacherPolicy.Target teacher = TeacherPolicy.evaluate(before, 0.5f);
  if (action == teacher.bestAction && measuredReward > 0f) {
   candidate.distillStep(before.features, before.legalMask, teacher.softProbs, measuredReward, 0.008f);
  } else {
   candidate.loraOnlineStep(before.features, before.legalMask, action, measuredReward, measuredReward, 0.005f);
  }
  ModelEvaluator.PromotionDecision gate = ModelEvaluator.checkPromotionGate(
    gameId, current, candidate, 8, 0xABCDEF01L + store.realSize());
  telemetryCounts[gate.event.ordinal()]++;
  if (gate.accepted) {
   activePolicies.put(gameId, candidate);
   if (storageRoot != null) {
    try {
     ModelCheckpoint.save(checkpointFile(schema), schema, candidate);
    } catch (IOException ignored) {}
   }
  }
  return true;
 }

 /**
  * High-level integration bridges for real Android game policies.
  */
 public GridPuzzlePolicy.Decision chooseGridMove(TaskSchema.GameId gameId, GridPuzzle board,
   GridPuzzlePolicy.Mode mode, int minGroup, int targetType, int targetRemaining,
   BitSet special, GridOnlineLearning onlineLearning) {
  GridPuzzlePolicy.Decision baseline = GridPuzzlePolicy.chooseAdaptive(
    board, mode, minGroup, targetType, targetRemaining, special, false, onlineLearning);
  if (baseline == null || mode != GridPuzzlePolicy.Mode.SWAP_MATCH3) return baseline;
  List<GridPuzzlePolicy.Decision> candidates = ObservationEncoder.topGridCandidates(
    board, mode, minGroup, targetType, targetRemaining, special, 8);
  if (candidates.isEmpty()) return baseline;
  ObservationEncoder.EncodedState encoded = ObservationEncoder.encodeGrid(
    gameId, board, special, targetType, candidates);
  PolicyTrace trace = selectAction(encoded);
  if (trace.selectedAction >= 0 && trace.selectedAction < candidates.size()) {
   GridPuzzlePolicy.Decision chosen = candidates.get(trace.selectedAction);
   if (onlineLearning == null || !onlineLearning.avoids(board, special, chosen)) return chosen;
  }
  return baseline;
 }

 public BubblePolicy.Shot chooseBubbleShot(BubbleBoard board) {
  BubblePolicy.Shot baseline = BubblePolicy.choose(board);
  if (baseline == null) return null;
  List<BubblePolicy.Shot> candidates = ObservationEncoder.topBubbleCandidates(board);
  if (candidates.isEmpty()) return baseline;
  ObservationEncoder.EncodedState encoded = ObservationEncoder.encodeBubble(board, candidates);
  PolicyTrace trace = selectAction(encoded);
  if (trace.selectedAction >= 0 && trace.selectedAction < candidates.size()) {
   return candidates.get(trace.selectedAction);
  }
  return baseline;
 }

 public JumperPolicy.Action chooseJumperAction(JumperState state) {
  JumperPolicy.Action baseline = JumperPolicy.nextAction(state);
  if (state == null || !state.usable()) return JumperPolicy.Action.WAIT;
  ObservationEncoder.EncodedState encoded = ObservationEncoder.encodeJumper(state);
  PolicyTrace trace = selectAction(encoded);
  return switch (trace.selectedAction) {
   case 0 -> JumperPolicy.Action.TAP_LEFT;
   case 1 -> JumperPolicy.Action.TAP_RIGHT;
   case 2 -> JumperPolicy.Action.WAIT;
   default -> baseline;
  };
 }

 public int choose2048Direction(Board2048 board) {
  if (board == null || board.max() >= 2048) return -1;
  ObservationEncoder.EncodedState encoded = ObservationEncoder.encode2048(board);
  if (!encoded.hasLegalAction()) return -1;
  PolicyTrace trace = selectAction(encoded);
  if (trace.selectedAction >= 0 && trace.selectedAction < 4 && encoded.legalMask[trace.selectedAction]) {
   return trace.selectedAction;
  }
  return board.choose();
 }

 public HexaPolicy.Move chooseHexaMove(HexaBoard board) {
  if (board == null || !board.known()) return null;
  List<HexaPolicy.Move> candidates = ObservationEncoder.topHexaCandidates(board, 8);
  if (candidates.isEmpty()) return HexaPolicy.choose(board);
  ObservationEncoder.EncodedState encoded = ObservationEncoder.encodeHexon(board, candidates);
  PolicyTrace trace = selectAction(encoded);
  if (trace.selectedAction >= 0 && trace.selectedAction < candidates.size()) {
   return candidates.get(trace.selectedAction);
  }
  return candidates.get(0);
 }

 public KotobashnyaPolicy.Decision chooseKotobashnyaAction(KotobashnyaState state) {
  KotobashnyaPolicy.Decision baseline = KotobashnyaPolicy.choose(state);
  if (state == null || !state.usable()) return baseline;
  ObservationEncoder.EncodedState encoded = ObservationEncoder.encodeKotobashnya(state);
  PolicyTrace trace = selectAction(encoded);
  return baseline;
 }


 /**
  * Selects the best legal controller action (0..7) for a NES, DOS, or SEGA scene (`RetroVision.RetroScene`),
  * combining the promoted `StudentPolicy` with `TeacherPolicy` safety fallback.
  */
 public synchronized PolicyTrace chooseRetroConsoleAction(TaskSchema.GameId gameId, RetroVision.RetroScene scene) {
  if (gameId == null || !gameId.isRetroConsole()) {
   throw new IllegalArgumentException("chooseRetroConsoleAction requires a retro GameId");
  }
  ObservationEncoder.EncodedState encoded = ObservationEncoder.encodeRetro(gameId, scene);
  return selectAction(encoded);
 }

 /**
  * Runs multi-stage curriculum distillation + PPO/GAE across all 6 retro console archetypes
  * (NES, DOS, SEGA) and promotes verified checkpoints.
  */
 public synchronized int trainRetroConsolesAndPromote(int distillEpochs, int ppoRolloutSteps, long seed) {
  int accepted = 0;
  for (TaskSchema.GameId g : TaskSchema.GameId.retroGames()) {
   ModelEvaluator.PromotionDecision d = trainCurriculumAndPromote(g, distillEpochs, ppoRolloutSteps, seed + g.ordinal() * 31L);
   if (d.accepted) accepted++;
  }
  return accepted;
 }

 public String diagnosticSummary() {
  StringBuilder sb = new StringBuilder("ADAPTIVE_ENGINE: MODEL_USED=")
    .append(telemetryCount(TaskSchema.TelemetryEvent.MODEL_USED))
    .append(" MODEL_FALLBACK=").append(telemetryCount(TaskSchema.TelemetryEvent.MODEL_FALLBACK))
    .append(" MODEL_UPDATE_ACCEPTED=").append(telemetryCount(TaskSchema.TelemetryEvent.MODEL_UPDATE_ACCEPTED))
    .append(" MODEL_UPDATE_REJECTED=").append(telemetryCount(TaskSchema.TelemetryEvent.MODEL_UPDATE_REJECTED));
  for (TaskSchema.GameId g : TaskSchema.GameId.values()) {
   ReplayStore rs = replayStores.get(g);
   PolicyTrace tr = lastTraces.get(g);
   sb.append("\n  ").append(g.id).append(": sim=").append(rs.simSize())
     .append(" shadow=").append(rs.shadowSize()).append(" real=").append(rs.realSize())
     .append(" rejected_unverified=").append(rs.rejectedUnverifiedCount())
     .append(" last_trace=").append(tr == null ? "none" : tr.telemetry + "(act=" + tr.selectedAction + ",conf=" + String.format(Locale.ROOT, "%.2f", tr.confidence) + ")");
  }
  return sb.toString();
 }
}
