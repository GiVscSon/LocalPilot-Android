import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Static contract guards against reintroducing an unguarded new dispatcher. */
class ReflexVlmReviewContractVerify {
 private static String read(String file)throws Exception {
  return Files.readString(Path.of(file),StandardCharsets.UTF_8);
 }
 private static void require(boolean ok,String message) {
  if(!ok)throw new AssertionError(message);
 }
 public static void main(String[]args)throws Exception {
  String service=read("app/src/main/java/org/localpilot/android/PilotService.java");
  String recorder=read("app/src/main/java/org/localpilot/android/reflex/StasisFrameRecorder.kt");
  String bridge=read("app/src/main/java/org/localpilot/android/reflex/ReflexCaptureBridge.kt");
  String review=read("app/src/main/java/org/localpilot/android/reflex/ReflexVlmReviewController.kt");
  String store=read("app/src/main/java/org/localpilot/android/reflex/ReflexRuleStore.kt");
  String client=read("app/src/main/java/org/localpilot/android/reflex/VlmApiClient.kt");
  require(service.contains("reflexCapture.copyStasisForReview("),"service must copy bound stasis snapshot");
  int menu=service.indexOf("private void moreOptions()");
  require(menu>=0&&service.indexOf("copyStasisForReview(",menu)<service.indexOf("stop(\"Дополнительные действия · STOP\")",menu),
   "menu must snapshot BEFORE revoking input authority");
  require(service.contains("if(actions[i]!=19)discardReflexReviewShot();"),"other menu actions must discard snapshot");
  require(service.contains(".setPositiveButton(\"Отправить снимок\""),"upload needs affirmative user click");
  require(service.contains("String packageName=reflexReviewTarget;"),"bind analysis to known package");
  require(service.contains("getBoolean(\"observe_enabled\",false)"),"passive observation must default OFF");
  require(service.contains("reflexVlmReviewer.close()"),"service teardown must revoke active review");
  require(!review.contains("dispatchGesture(")&&!review.contains("sendObserved("),
   "review is not a gesture dispatcher");
  require(review.contains("ReflexCompiler.compileCandidate("),"Qwen output must be compiled and validated");
  require(review.contains("store.saveCandidates("),"only inert compiled candidates may be saved");
  require(store.contains("enabled = false"),"persisted candidates must never be armed automatically");
  require(recorder.contains("1280.0")&&recorder.contains("30000"),"in-memory snapshots must be bounded and expire");
  require(bridge.contains("stasisFrames.record("),"stasis needs a bounded ROI snapshot");
  require(bridge.contains("stasisFrames.clear()"),"STOP/rebind must clear private frame RAM");
  require(client.contains("conn.instanceFollowRedirects = false"),"HTTP redirects cannot receive private game screenshot");
  require(!service.contains("reflexVlmReviewer.review(shot,packageName,address,modelName,key,true,result->{}"),
   "review callback must retain diagnostics");
  System.out.println("REFLEX_VLM_REVIEW_CONTRACT_PASS checks=16");
 }
}
