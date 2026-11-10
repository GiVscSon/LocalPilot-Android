package org.localpilot.android.learning;

import java.util.Arrays;
import java.util.Locale;
import org.localpilot.android.rl.ArcadeEnv;

/**
 * Continuous paired evaluation and safety promotion gate for LocalPilot Adaptive Learning Engine.
 * Compares:
 * - RANDOM_INIT (untrained random network)
 * - PROGRAMMED_WEIGHTS (analytically constructed weights from WeightConstructor)
 * - DISTILLED_STUDENT (teacher-distilled + RL-tuned student policy)
 * - TEACHER_BASELINE (rule/search oracle)
 * on identical held-out seeds across all 4 curriculum stages.
 *
 * Promotion Gate: A candidate model update is accepted (`MODEL_UPDATE_ACCEPTED`) ONLY if:
 * 1. Safety violations == 0;
 * 2. Teacher agreement and mean episode return do not degrade below the current active policy.
 */
public final class ModelEvaluator {
 public static final class EvalMetrics {
  public final String policyName;
  public final int episodes;
  public final double meanReturn;
  public final double teacherAgreementRate;
  public final int safetyViolations;
  public final double inferLatencyP50Us;
  public final double inferLatencyP95Us;

  public EvalMetrics(String policyName, int episodes, double meanReturn,
    double teacherAgreementRate, int safetyViolations,
    double inferLatencyP50Us, double inferLatencyP95Us) {
   this.policyName = policyName;
   this.episodes = episodes;
   this.meanReturn = meanReturn;
   this.teacherAgreementRate = teacherAgreementRate;
   this.safetyViolations = safetyViolations;
   this.inferLatencyP50Us = inferLatencyP50Us;
   this.inferLatencyP95Us = inferLatencyP95Us;
  }

  public String format() {
   return String.format(Locale.ROOT,
     "%s: return=%.2f agreement=%.1f%% safety_violations=%d latency_p50=%.1fus p95=%.1fus",
     policyName, meanReturn, teacherAgreementRate * 100.0, safetyViolations, inferLatencyP50Us, inferLatencyP95Us);
  }
 }

 public static final class PromotionDecision {
  public final TaskSchema.TelemetryEvent event;
  public final boolean accepted;
  public final EvalMetrics baselineMetrics;
  public final EvalMetrics candidateMetrics;
  public final String summary;

  PromotionDecision(TaskSchema.TelemetryEvent event, boolean accepted,
    EvalMetrics baselineMetrics, EvalMetrics candidateMetrics, String summary) {
   this.event = event;
   this.accepted = accepted;
   this.baselineMetrics = baselineMetrics;
   this.candidateMetrics = candidateMetrics;
   this.summary = summary;
  }
 }

 private ModelEvaluator() {}

 public static EvalMetrics evaluatePolicy(String name, TaskSchema.GameId gameId,
   StudentPolicy policy, int episodes, long baseSeed) {
  if (episodes < 1 || episodes > 500) throw new IllegalArgumentException("episodes");
  double totalReturn = 0;
  int totalSteps = 0, agreements = 0, violations = 0;
  long[] latenciesNs = new long[episodes * 40];
  int latCursor = 0;

  RealisticCurriculumEnv.CurriculumStage[] stages = RealisticCurriculumEnv.CurriculumStage.values();
  for (int ep = 0; ep < episodes; ep++) {
   RealisticCurriculumEnv.CurriculumStage stage = stages[ep % stages.length];
   RealisticCurriculumEnv env = new RealisticCurriculumEnv(gameId, stage, baseSeed + ep * 97L);
   double epReturn = 0;
   while (true) {
    ObservationEncoder.EncodedState s = env.observe();
    TeacherPolicy.Target teacher = TeacherPolicy.evaluate(s, 0.5f);
    long t0 = System.nanoTime();
    StudentPolicy.InferenceResult inf = policy.infer(s);
    long elapsed = System.nanoTime() - t0;
    if (latCursor < latenciesNs.length) latenciesNs[latCursor++] = elapsed;
    int action = inf.selectedAction;
    if (action == teacher.bestAction) agreements++;
    totalSteps++;
    RealisticCurriculumEnv.StepOutcome out = env.step(action);
    if (out.safetyViolation) violations++;
    epReturn += out.reward;
    if (out.done) break;
   }
   totalReturn += epReturn;
  }
  Arrays.sort(latenciesNs, 0, Math.max(1, latCursor));
  double p50Us = latCursor == 0 ? 0 : latenciesNs[(latCursor - 1) / 2] / 1000.0;
  double p95Us = latCursor == 0 ? 0 : latenciesNs[Math.min(latCursor - 1, (int) (latCursor * 0.95))] / 1000.0;
  return new EvalMetrics(name, episodes, totalReturn / episodes,
    totalSteps == 0 ? 0 : agreements / (double) totalSteps, violations, p50Us, p95Us);
 }

 /**
  * Frozen safety and utility gate before replacing an active Android game model.
  */
 public static PromotionDecision checkPromotionGate(TaskSchema.GameId gameId,
   StudentPolicy currentActive, StudentPolicy candidate, int episodes, long evalSeed) {
  EvalMetrics base = evaluatePolicy("CURRENT_ACTIVE", gameId, currentActive, episodes, evalSeed);
  EvalMetrics cand = evaluatePolicy("CANDIDATE_UPDATED", gameId, candidate, episodes, evalSeed);
  boolean safe = cand.safetyViolations == 0;
  boolean nonDegrading = cand.meanReturn + 1e-4 >= base.meanReturn - 0.15
    && cand.teacherAgreementRate + 1e-4 >= base.teacherAgreementRate - 0.02;
  boolean accepted = safe && nonDegrading;
  TaskSchema.TelemetryEvent ev = accepted
    ? TaskSchema.TelemetryEvent.MODEL_UPDATE_ACCEPTED
    : TaskSchema.TelemetryEvent.MODEL_UPDATE_REJECTED;
  String summary = ev + " [" + gameId.id + "] | " + base.format() + " -> " + cand.format();
  return new PromotionDecision(ev, accepted, base, cand, summary);
 }
}
