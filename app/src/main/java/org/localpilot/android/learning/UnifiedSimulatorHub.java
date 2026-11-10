package org.localpilot.android.learning;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.localpilot.android.retro.RetroConsoleEnv;
import org.localpilot.android.retro.RetroInputBridge;
import org.localpilot.android.retro.RetroPlatform;
import org.localpilot.android.retro.RetroVision;
import org.localpilot.android.rl.ArcadeEnv;

/**
 * Unified 16-Game Simulator Hub (`UnifiedSimulatorHub`) embedded directly into the Main Menu
 * (`MainActivity`) and `AdaptiveLearningEngine`.
 *
 * Every one of the 16 supported games (6 Retro: NES, DOS, SEGA + 10 Yandex Market games) has an
 * active, stateful simulator in the core that:
 * 1. Renders live visual RGB frames (`renderSimulatorFrameRgb`) directly onto the Main Menu canvas;
 * 2. Trains itself continuously (`stepAndTrainSimulator` — «обучается сам» via curriculum physics,
 *    pixel verification for retro, oscillation repair, Teacher->Student distillation, PPO+GAE, LoRA);
 * 3. Teaches other games (`teachOtherGamesFromSimulator` — «обучает другие игры» by transferring
 *    shared trunk & representation layers from the simulator's promoted policy to sibling and
 *    cross-platform games, strictly verified by `ModelEvaluator.checkPromotionGate`).
 */
public final class UnifiedSimulatorHub {

 public static final class SimulatorTickResult {
  public final TaskSchema.GameId activeGame;
  public final int selectedAction;
  public final int teacherAction;
  public final float stepReward;
  public final double evaluatedReturn;
  public final double teacherAgreement;
  public final boolean selfPromoted;
  public final int taughtOtherGamesCount;
  public final List<TaskSchema.GameId> taughtTargets;
  public final int totalSteps;
  public final String statusLine;

  public SimulatorTickResult(TaskSchema.GameId activeGame, int selectedAction, int teacherAction,
    float stepReward, double evaluatedReturn, double teacherAgreement, boolean selfPromoted,
    int taughtOtherGamesCount, List<TaskSchema.GameId> taughtTargets, int totalSteps, String statusLine) {
   this.activeGame = activeGame;
   this.selectedAction = selectedAction;
   this.teacherAction = teacherAction;
   this.stepReward = stepReward;
   this.evaluatedReturn = evaluatedReturn;
   this.teacherAgreement = teacherAgreement;
   this.selfPromoted = selfPromoted;
   this.taughtOtherGamesCount = taughtOtherGamesCount;
   this.taughtTargets = List.copyOf(taughtTargets);
   this.totalSteps = totalSteps;
   this.statusLine = statusLine;
  }
 }

 private final AdaptiveLearningEngine engine;
 private final Map<TaskSchema.GameId, RetroConsoleEnv> retroEnvs = new EnumMap<>(TaskSchema.GameId.class);
 private final Map<TaskSchema.GameId, RetroVision.RetroScene> lastRetroScenes = new EnumMap<>(TaskSchema.GameId.class);
 private final Map<TaskSchema.GameId, RealisticCurriculumEnv> curriculumEnvs = new EnumMap<>(TaskSchema.GameId.class);
 private final Map<TaskSchema.GameId, ArcadeEnv> arcadeEnvs = new EnumMap<>(TaskSchema.GameId.class);
 private final Map<TaskSchema.GameId, Integer> simSteps = new EnumMap<>(TaskSchema.GameId.class);
 private final Map<TaskSchema.GameId, Integer> selfPromotions = new EnumMap<>(TaskSchema.GameId.class);
 private final Map<TaskSchema.GameId, Integer> taughtOthersCount = new EnumMap<>(TaskSchema.GameId.class);
 private final Map<TaskSchema.GameId, Float> lastActionNorm = new EnumMap<>(TaskSchema.GameId.class);

 private volatile TaskSchema.GameId focusedGame = TaskSchema.GameId.SEGA_HIGH_SPEED_ACT;
 private volatile boolean autoRotateAll16 = true;
 private volatile boolean runningInMainMenu = true;
 private volatile int rotationCursor = 0;
 private volatile int totalSimStepsAllGames = 0;
 private volatile int totalCrossGameTeachings = 0;
 private volatile String lastHubStatus =
   "Симуляторы в основе активны (16/16: NES · DOS · SEGA · Маркет) — обучаются сами и обучают другие игры";

 public UnifiedSimulatorHub(AdaptiveLearningEngine engine) {
  if (engine == null) throw new IllegalArgumentException("engine required");
  this.engine = engine;
  long baseSeed = 20261106L;
  for (TaskSchema.GameId g : TaskSchema.GameId.values()) {
   simSteps.put(g, 0);
   selfPromotions.put(g, 0);
   taughtOthersCount.put(g, 0);
   lastActionNorm.put(g, 0f);
   if (g.isRetroConsole()) {
    RetroPlatform.Archetype arch = RetroPlatform.Archetype.forGameId(g);
    retroEnvs.put(g, new RetroConsoleEnv(
      arch, RealisticCurriculumEnv.CurriculumStage.STAGE_2_SPECIAL_OBJECTS, baseSeed + g.ordinal() * 37L));
   } else {
    curriculumEnvs.put(g, new RealisticCurriculumEnv(
      g, RealisticCurriculumEnv.CurriculumStage.STAGE_2_SPECIAL_OBJECTS, baseSeed + g.ordinal() * 53L));
   }
  }
  arcadeEnvs.put(TaskSchema.GameId.ARI_JUMPER, new ArcadeEnv(ArcadeEnv.Genre.JUMPER));
  arcadeEnvs.put(TaskSchema.GameId.KOTOBASHNYA, new ArcadeEnv(ArcadeEnv.Genre.PLATFORMER));
  arcadeEnvs.put(TaskSchema.GameId.MARKET_RUSH, new ArcadeEnv(ArcadeEnv.Genre.LANES));
  arcadeEnvs.put(TaskSchema.GameId.GOSHAN_FLAPPY, new ArcadeEnv(ArcadeEnv.Genre.JETPACK));
  for (ArcadeEnv ae : arcadeEnvs.values()) ae.reset(700000);
 }

 public TaskSchema.GameId focusedGame() { return focusedGame; }
 public void setFocusedGame(TaskSchema.GameId game) {
  if (game != null) {
   this.focusedGame = game;
   this.lastHubStatus = "Симулятор в основном меню: " + game.title
     + " · обучается сам и передаёт навыки другим играм";
  }
 }

 public boolean isAutoRotateAll16() { return autoRotateAll16; }
 public void setAutoRotateAll16(boolean autoRotate) { this.autoRotateAll16 = autoRotate; }

 public boolean isRunningInMainMenu() { return runningInMainMenu; }
 public void setRunningInMainMenu(boolean running) {
  this.runningInMainMenu = running;
  this.lastHubStatus = running
    ? "Авто-симуляторы ВКЛЮЧЕНЫ в основном меню (все 16 игр обучаются и обучают друг друга)"
    : "Симуляторы основного меню на паузе (обученные модели 16 игр сохранены)";
 }

 public int totalSimStepsAllGames() { return totalSimStepsAllGames; }
 public int totalCrossGameTeachings() { return totalCrossGameTeachings; }
 public String lastHubStatus() { return lastHubStatus; }
 public int simStepsFor(TaskSchema.GameId g) { return simSteps.getOrDefault(g, 0); }
 public int selfPromotionsFor(TaskSchema.GameId g) { return selfPromotions.getOrDefault(g, 0); }
 public int taughtOthersFor(TaskSchema.GameId g) { return taughtOthersCount.getOrDefault(g, 0); }

 /**
  * Steps the active simulator (or rotates across all 16 simulators), trains its own StudentPolicy
  * on the simulated trajectory, and transfers its updated trunk skills to related games.
  */
 public synchronized SimulatorTickResult stepAndTrainSimulator(TaskSchema.GameId requestedGame, long seed) {
  TaskSchema.GameId[] all = TaskSchema.GameId.values();
  TaskSchema.GameId game = requestedGame != null
    ? requestedGame
    : (autoRotateAll16 ? all[Math.floorMod(rotationCursor++, all.length)] : focusedGame);
  this.focusedGame = game;

  int selectedAction = 0;
  int teacherAction = 0;
  float stepReward = 0f;
  int stepCount = simSteps.getOrDefault(game, 0) + 1;

  if (game.isRetroConsole()) {
   RetroPlatform.Archetype arch = RetroPlatform.Archetype.forGameId(game);
   RetroConsoleEnv env = retroEnvs.get(game);
   RetroVision.RetroScene prev = lastRetroScenes.get(game);
   int[] rgbBefore = env.renderRgbFrame(320, 480, true);
   RetroVision.RetroScene sceneBefore = RetroVision.analyzeFrame(
     rgbBefore, 320, 480, env.frameGeneration(), arch, prev);
   ObservationEncoder.EncodedState encBefore = ObservationEncoder.encodeRetro(game, sceneBefore);
   TeacherPolicy.Target teacher = TeacherPolicy.evaluate(encBefore, 0.5f);
   teacherAction = teacher.bestAction;

   AdaptiveLearningEngine.PolicyTrace trace = engine.chooseRetroConsoleAction(game, sceneBefore);
   selectedAction = trace.selectedAction >= 0 ? trace.selectedAction : Math.max(0, teacherAction);
   if (selectedAction != teacherAction && teacherAction >= 0) {
    WeightConstructor.repairOscillationSubspace(
      engine.activePolicy(game), new float[][]{encBefore.features}, selectedAction, teacherAction, 5.5f);
   }
   stepReward = env.stepPhysics(selectedAction);
   int[] rgbAfter = env.renderRgbFrame(320, 480, true);
   RetroVision.RetroScene sceneAfter = RetroVision.analyzeFrame(
     rgbAfter, 320, 480, env.frameGeneration(), arch, sceneBefore);
   RetroInputBridge.PostAckVerification ver = RetroInputBridge.verifyPostAckOutcome(
     sceneBefore, sceneAfter, selectedAction, true);
   if (ver.visualVerified) {
    ObservationEncoder.EncodedState encAfter = ObservationEncoder.encodeRetro(game, sceneAfter);
    engine.recordSimulatedOutcome(
      game, encBefore, selectedAction, encAfter, ver.measuredReward, false, 1000L + stepCount * 80L);
   }
   lastRetroScenes.put(game, sceneAfter);
  } else {
   RealisticCurriculumEnv env = curriculumEnvs.get(game);
   ObservationEncoder.EncodedState encBefore = env.observe();
   TeacherPolicy.Target teacher = TeacherPolicy.evaluate(encBefore, 0.5f);
   teacherAction = teacher.bestAction;
   AdaptiveLearningEngine.PolicyTrace trace = engine.selectAction(encBefore);
   selectedAction = trace.selectedAction >= 0 ? trace.selectedAction : Math.max(0, teacherAction);
   if (selectedAction != teacherAction && teacherAction >= 0) {
    WeightConstructor.repairOscillationSubspace(
      engine.activePolicy(game), new float[][]{encBefore.features}, selectedAction, teacherAction, 5.5f);
   }
   RealisticCurriculumEnv.StepOutcome sr = env.step(selectedAction);
   stepReward = sr.reward;
   engine.recordSimulatedOutcome(
     game, encBefore, selectedAction, sr.nextState, sr.reward, sr.done, 1000L + stepCount * 80L);
   if (sr.done) env.reset(seed + stepCount * 41L);

   ArcadeEnv ae = arcadeEnvs.get(game);
   if (ae != null) {
    if (ae.terminated || ae.truncated) ae.reset(700000 + stepCount);
    ae.step(Math.max(0, Math.min(2, selectedAction)));
   }
  }

  simSteps.put(game, stepCount);
  totalSimStepsAllGames++;
  lastActionNorm.put(game, selectedAction / 7f);

  // Train the simulator's own StudentPolicy via curriculum + distillation + PPO/GAE
  ModelEvaluator.PromotionDecision selfDecision = engine.trainCurriculumAndPromote(
    game, 4, 16, seed + stepCount * 19L + game.ordinal());
  if (selfDecision.accepted) {
   selfPromotions.put(game, selfPromotions.getOrDefault(game, 0) + 1);
  }

  // Teach sibling and cross-platform games from this simulator's active policy
  List<TaskSchema.GameId> taught = teachOtherGamesFromSimulator(game, seed + stepCount * 31L);
  taughtOthersCount.put(game, taughtOthersCount.getOrDefault(game, 0) + taught.size());
  totalCrossGameTeachings += taught.size();

  String taughtNames = taught.isEmpty()
    ? "модели синхронизированы"
    : taught.stream().limit(3).map(g -> g.id).reduce((a, b) -> a + ", " + b).orElse("");
  String line = String.format(Locale.ROOT,
    "Симулятор [%s] шаг #%d · нагр=%.1f · согл=%.0f%% · обучил другие игры: +%d (%s)",
    game.title, stepCount, selfDecision.candidateMetrics.meanReturn,
    selfDecision.candidateMetrics.teacherAgreementRate * 100.0, taught.size(), taughtNames);
  this.lastHubStatus = line;

  return new SimulatorTickResult(
    game,
    selectedAction,
    teacherAction,
    stepReward,
    selfDecision.candidateMetrics.meanReturn,
    selfDecision.candidateMetrics.teacherAgreementRate,
    selfDecision.accepted,
    taught.size(),
    taught,
    stepCount,
    line
  );
 }

 /**
  * Runs a full round across all 16 simulators so every simulator steps, trains itself,
  * and teaches its sibling/cross-platform games.
  */
 public synchronized List<SimulatorTickResult> runAllSimulatorsCycle(long seed) {
  List<SimulatorTickResult> results = new ArrayList<>(TaskSchema.GameId.values().length);
  for (TaskSchema.GameId g : TaskSchema.GameId.values()) {
   results.add(stepAndTrainSimulator(g, seed + g.ordinal() * 97L));
  }
  this.lastHubStatus = String.format(Locale.ROOT,
    "Все 16 симуляторов в основе прошли цикл: шагов=%d, передач навыков другим играм=%d",
    totalSimStepsAllGames, totalCrossGameTeachings);
  return results;
 }

 /**
  * Transfers learned representations from `donorSimGame` to all sibling games in its `SkillFamily`
  * PLUS cross-platform bridges (Retro <-> Market), strictly gated by `ModelEvaluator.checkPromotionGate`.
  */
 public synchronized List<TaskSchema.GameId> teachOtherGamesFromSimulator(TaskSchema.GameId donorSimGame, long seed) {
  List<TaskSchema.GameId> acceptedTargets = new ArrayList<>();
  if (donorSimGame == null) return acceptedTargets;
  StudentPolicy donorPolicy = engine.activePolicy(donorSimGame);
  AutonomousSelfTrainer trainer = engine.selfTrainer();

  // Find all games in the same SkillFamily + cross-platform counterpart
  for (AutonomousSelfTrainer.SkillFamily family : AutonomousSelfTrainer.SkillFamily.values()) {
   if (family.members.contains(donorSimGame)) {
    for (TaskSchema.GameId target : family.members) {
     if (target == donorSimGame) continue;
     if (trainer.transferSharedTrunkSkill(donorPolicy, target, 0.20f, seed + target.ordinal())) {
      acceptedTargets.add(target);
     }
    }
   }
  }
  return acceptedTargets;
 }

 /**
  * Renders a live RGB preview frame (`width * height`) of any of the 16 simulators for direct display
  * on the Main Menu (`MainActivity`) or `TrainingActivity`.
  */
 public synchronized int[] renderSimulatorFrameRgb(TaskSchema.GameId game, int width, int height) {
  int w = Math.max(120, width);
  int h = Math.max(90, height);
  TaskSchema.GameId g = game != null ? game : focusedGame;
  if (g.isRetroConsole()) {
   RetroConsoleEnv env = retroEnvs.get(g);
   return env.renderRgbFrame(w, h, true);
  }

  int[] rgb = new int[w * h];
  // Background gradient
  for (int y = 0; y < h; y++) {
   int bg = (0xFF << 24) | ((14 + (y * 16) / h) << 16) | ((26 + (y * 22) / h) << 8) | (46 + (y * 30) / h);
   int row = y * w;
   for (int x = 0; x < w; x++) rgb[row + x] = bg;
  }

  ArcadeEnv ae = arcadeEnvs.get(g);
  if (ae != null) {
   boolean vertical = ae.genre == ArcadeEnv.Genre.JUMPER || ae.genre == ArcadeEnv.Genre.PLATFORMER;
   for (ArcadeEnv.Entity o : ae.objects) {
    float ox = ae.genre == ArcadeEnv.Genre.JETPACK ? 70 + o.x - ae.progress : o.x;
    float oy = ae.genre == ArcadeEnv.Genre.LANES ? 550 - (o.y - ae.progress) : 640 - (o.y - (vertical ? ae.camera : 0));
    int cx = Math.round((ox / 360f) * w);
    int cy = Math.round((oy / 640f) * h);
    int rw = Math.max(6, Math.round((o.w / 360f) * w));
    int rh = Math.max(4, Math.round((o.h / 640f) * h));
    int col = o.type == 1 ? 0xFF62D394 : (o.type == 2 ? 0xFFEE6677 : 0xFFFFD166);
    fillRect(rgb, w, h, cx - rw / 2, cy - rh / 2, cx + rw / 2, cy + rh / 2, col);
   }
   float px = ae.genre == ArcadeEnv.Genre.JETPACK ? 70 : ae.x;
   float py = ae.genre == ArcadeEnv.Genre.LANES ? 550 - ae.y : 640 - (ae.y - (vertical ? ae.camera : 0));
   int pcx = Math.round((px / 360f) * w);
   int pcy = Math.round((py / 640f) * h);
   fillRect(rgb, w, h, pcx - 7, pcy - 9, pcx + 7, pcy + 9, 0xFF5BC2FF);
  } else {
   // Grid / Board / Bubble / Hex / Paddle visualization from live curriculum state
   ObservationEncoder.EncodedState state = curriculumEnvs.get(g).observe();
   float[] f = state.features;
   if (g == TaskSchema.GameId.KARKANOID) {
    int bx = Math.round(clamp01(f[0]) * (w - 20)) + 10;
    int by = Math.round(clamp01(f[1]) * (h - 30)) + 10;
    int padX = Math.round(clamp01(f[4]) * (w - 40)) + 20;
    for (int col = 0; col < 5; col++) {
     int x0 = 12 + col * ((w - 24) / 5);
     fillRect(rgb, w, h, x0, 16, x0 + ((w - 36) / 5), 26, 0xFFFFB703);
    }
    fillRect(rgb, w, h, bx - 4, by - 4, bx + 4, by + 4, 0xFFFF4D6D);
    fillRect(rgb, w, h, padX - 18, h - 18, padX + 18, h - 11, 0xFF4CC9F0);
   } else {
    int cols = (g == TaskSchema.GameId.GAME_2048) ? 4 : 6;
    int rows = (g == TaskSchema.GameId.GAME_2048) ? 4 : 5;
    int cellW = Math.max(10, (w - 32) / cols);
    int cellH = Math.max(10, (h - 28) / rows);
    int[] palette = {0xFF4CC9F0, 0xFF62D394, 0xFFFFD166, 0xFFF77F00, 0xFFEE6677, 0xFF9B5DE5};
    for (int r = 0; r < rows; r++) {
     for (int c = 0; c < cols; c++) {
      int idx = (r * cols + c) % f.length;
      int color = palette[Math.floorMod(Math.round(f[idx] * 10f) + r + c, palette.length)];
      int x0 = 16 + c * cellW;
      int y0 = 14 + r * cellH;
      fillRect(rgb, w, h, x0 + 1, y0 + 1, x0 + cellW - 2, y0 + cellH - 2, color);
     }
    }
   }
  }
  // Top status bar indicator showing active self-learning & cross-game teaching pulse
  int barW = Math.max(8, Math.min(w - 8, (simSteps.getOrDefault(g, 1) * 12) % Math.max(16, w - 8)));
  fillRect(rgb, w, h, 4, 3, 4 + barW, 7, 0xFF06D6A0);
  return rgb;
 }

 private static float clamp01(float v) {
  return Math.max(0.05f, Math.min(0.95f, v));
 }

 private static void fillRect(int[] rgb, int w, int h, int x0, int y0, int x1, int y1, int color) {
  int l = Math.max(0, Math.min(w, x0));
  int r = Math.max(0, Math.min(w, x1));
  int t = Math.max(0, Math.min(h, y0));
  int b = Math.max(0, Math.min(h, y1));
  for (int y = t; y < b; y++) {
   int row = y * w;
   for (int x = l; x < r; x++) rgb[row + x] = color;
  }
 }
}
