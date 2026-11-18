package org.localpilot.shared;

/**
 * Read-only admission contract for a future fast in-app NES motor.
 *
 * A PASS is NOT an input permit, an emulator ACK, a verified game transition,
 * or evidence of policy improvement. Only the existing HarnessCoordinator may
 * issue the single writer permit. No WebView, Accessibility, JNI or network
 * call is made here.
 */
public final class FastMotorContract {
 private FastMotorContract(){}
 public static final long MAX_OBSERVATION_AGE_MS=250;
 public enum Source { EXPLICIT_ROM_HEURISTIC, STUDENT_POLICY }
 public enum Verdict {
  ADMISSIBLE, MISSING_DATA, DISABLED, STOPPED, INPUT_BUSY, ROM_UNTRUSTED,
  EPOCH_MISMATCH, STALE_OBSERVATION, STOP_REVISION_MISMATCH, PHASE_MISMATCH,
  ILLEGAL_BUTTON, INVALID_DURATION, POLICY_UNAPPROVED, POLICY_IDENTITY_MISMATCH
 }
 /** Explicit origin, not inferred from a GGUF string or a display label. */
 public record Proposal(String romSha, String epoch, int frame, int pixelHash,
   long stopRevision, long observedAtElapsedMs, Source source,
   String policySha256, String button, int frames) {}
 /** Callers must derive these values from the trusted emulator/coordinator/vault. */
 public record Current(String romSha, String epoch, int frame, int pixelHash,
   long stopRevision, boolean enabled, boolean stopPending, boolean inputOwnerFree,
   boolean explicitHeuristicOptIn, String activeStudentSha256,
   boolean studentEvaluationPassed, VoidLancerGameState verifiedRam) {}

 public static Verdict inspect(Proposal p,Current c,long nowElapsedMs){
  if(p==null||c==null||p.source()==null||c.verifiedRam()==null||
     p.epoch()==null||c.epoch()==null||p.romSha()==null||c.romSha()==null||
     nowElapsedMs<0)return Verdict.MISSING_DATA;
  if(!c.enabled())return Verdict.DISABLED;
  if(c.stopPending())return Verdict.STOPPED;
  if(!c.inputOwnerFree())return Verdict.INPUT_BUSY;
  // No user ROM inherits a task-specific memory map or an action policy.
  if(!VoidLancerGameState.ROM_SHA.equals(p.romSha())||
     !VoidLancerGameState.ROM_SHA.equals(c.romSha()))return Verdict.ROM_UNTRUSTED;
  if(!p.epoch().equals(c.epoch())||!p.epoch().matches("[1-9][0-9]{0,18}"))
   return Verdict.EPOCH_MISMATCH;
  if(p.frame()<15||p.pixelHash()==0||p.frame()!=c.frame()||
     p.pixelHash()!=c.pixelHash()||p.observedAtElapsedMs()<0||
     nowElapsedMs<p.observedAtElapsedMs()||
     nowElapsedMs-p.observedAtElapsedMs()>MAX_OBSERVATION_AGE_MS)
   return Verdict.STALE_OBSERVATION;
  if(p.stopRevision()<0||p.stopRevision()!=c.stopRevision())
   return Verdict.STOP_REVISION_MISMATCH;
  if(c.verifiedRam().state()!=1)return Verdict.PHASE_MISMATCH;
  if(!ConsoleAgentContract.gamePlayable(p.button())||
     "START".equals(p.button())||"SELECT".equals(p.button())||"WAIT".equals(p.button())||
     !c.verifiedRam().phaseAllows(p.button())||
     (p.button().contains("B")&&c.verifiedRam().bombs()==0))
   return Verdict.ILLEGAL_BUTTON;
  if(!ConsoleAgentContract.studentFramesValid(p.frames()))
   return Verdict.INVALID_DURATION;
  if(p.source()==Source.EXPLICIT_ROM_HEURISTIC){
   if(!c.explicitHeuristicOptIn())return Verdict.DISABLED;
   // A heuristic must never be logged as a trained neural policy.
   if(p.policySha256()!=null&&!p.policySha256().isEmpty())
    return Verdict.POLICY_IDENTITY_MISMATCH;
  }else{
   if(!c.studentEvaluationPassed())return Verdict.POLICY_UNAPPROVED;
   if(!validSha(p.policySha256())||!validSha(c.activeStudentSha256())||
      !p.policySha256().equals(c.activeStudentSha256()))
    return Verdict.POLICY_IDENTITY_MISMATCH;
  }
  return Verdict.ADMISSIBLE;
 }
 private static boolean validSha(String value){
  return value!=null&&value.matches("[0-9a-f]{64}");
 }
}
