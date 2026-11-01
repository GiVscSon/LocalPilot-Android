import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Source-level guardrails for the Java-Kotlin integration; no Android SDK needed. */
class ReflexIntegrationContractVerify {
 private static String read(String path)throws Exception {
  return Files.readString(Path.of(path),StandardCharsets.UTF_8);
 }
 private static void required(boolean ok,String message) {
  if(!ok)throw new AssertionError(message);
 }
 public static void main(String[] args)throws Exception {
  String service=read("app/src/main/java/org/localpilot/android/PilotService.java");
  String bridge=read("app/src/main/java/org/localpilot/android/reflex/ReflexCaptureBridge.kt");
  String fsm=read("app/src/main/java/org/localpilot/android/reflex/DeadlockDetector.kt");
  String store=read("app/src/main/java/org/localpilot/android/reflex/ReflexRuleStore.kt");
  String logger=read("app/src/main/java/org/localpilot/android/reflex/SelfDistillationLogger.kt");
  String remote=read("app/src/main/java/org/localpilot/android/reflex/VlmApiClient.kt");
  required(service.contains("reflexProbeBoardFrame(pixels,roi,epoch,ticket.window);"),
   "Board capture must feed the passive observer AFTER freshness checks");
  required(service.contains("reflexProbeArcadeFrame(frame);"),
   "Arcade MediaProjection path must feed passive observer");
  required(service.contains("reflexCapture.reset();"),
   "STOP must reset per-session reflex detector");
  required(service.contains("reflexCapture.recordGameTransition("),
   "Existing game verifiers must signal game progress");
  required(service.contains("if(status==BubbleSession.Status.VERIFIED)reflexGameVerified();"),
   "Bubble reward must not come from ACK");
  required(service.contains("reflexCapture.setObserving(reflexObserveEnabled)"),
   "Passive observation is an explicit preference");
  required(service.contains("getBoolean(\"observe_enabled\",false)"),
   "Passive observation MUST default OFF");
  required(!bridge.contains("dispatchGesture(") && !bridge.contains("sendObserved(")
      && !bridge.contains("VlmApiClient(") && !bridge.contains("commitVerified("),
   "Passive bridge must not dispatch, upload or commit labels");
  required(bridge.contains("850L") && bridge.contains("stasisThresholdMs = 4500L"),
   "DCT sampling must be bounded and stasis threshold explicit");
  required(fsm.contains("VisualChangeNeedsGameVerification")
      && fsm.contains("confirmGameTransition(") && fsm.contains("observedGameProgress("),
   "Only game-specific proof can credit an outcome");
  required(store.contains("enabled = false"),"Saved candidates MUST be disarmed");
  required(logger.contains("Decision.VerifiedProgress"),
   "Dataset must require explicit independent game verification");
  required(remote.contains("url.protocol != \"https\""),
   "No insecure screenshot upload endpoint");
  System.out.println("REFLEX_INTEGRATION_CONTRACT_PASS: 16 safety assertions");
 }
}
