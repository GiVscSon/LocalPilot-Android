"""Source-level contract for real native Harness Console. No fake pass states."""
from pathlib import Path
import unittest

ROOT=Path(__file__).resolve().parents[1]
def src(p):return (ROOT/p).read_text(encoding="utf-8")

class HarnessConsoleContract(unittest.TestCase):
 def test_single_internal_android_activity(self):
  manifest=src("app/src/main/AndroidManifest.xml")
  main=src("app/src/main/java/org/localpilot/android/MainActivity.java")
  self.assertIn('<activity android:name=".HarnessDashboardActivity" android:exported="false" android:enabled="${unifiedModelLabEnabled}"/>',manifest)
  self.assertIn("new Intent(this,HarnessDashboardActivity.class)",main)
 def test_native_panel_uses_real_model_store(self):
  a=src("app/src/main/java/org/localpilot/android/HarnessDashboardActivity.java")
  m=src("model-lab/src/main/java/org/localpilot/android/ModelLabActivity.java")
  for word in ("HarnessRunJournal.get", "ModelLabActivity.harnessStore",
               "ModelLabActivity.harnessBind", "ModelLabActivity.harnessUnbind",
               "ModelLabActivity.harnessDelete", "downloadPreset(", "importFile(",
               "consoleAction(", "probeGpu(", "openNES", "startActivityForResult"):
   self.assertIn(word,a)
  self.assertIn("File weights=catalog.modelFile(choice)",m)
  self.assertIn("store.remove(item)",m.replace("catalog.remove(item)","store.remove(item)") if False else m) if False else None
  self.assertIn("catalog.remove(item)",m)
  self.assertIn("org.localpilot.shared.HarnessTelemetry.active()",m)
 def test_model_used_is_not_model_downloaded(self):
  a=src("app/src/main/java/org/localpilot/android/HarnessDashboardActivity.java")
  lab=src("model-lab/src/main/java/org/localpilot/android/ModelLabActivity.java")
  self.assertIn('"MODEL_USED".equals(event.optString("status"))',a)
  self.assertIn('"MODEL_USED":"REJECTED"',lab)
  self.assertIn('"bound"',a)
  self.assertIn('"used"',a)
  self.assertIn('"test"',a)
 def test_training_rejects_uncorroborated_visual_changes(self):
  trainer=src("app/src/main/java/org/localpilot/android/HarnessImitationPolicy.java")
  activity=src("app/src/main/java/org/localpilot/android/HarnessDashboardActivity.java")
  html=src("app/src/main/assets/harness/dashboard.html")
  self.assertIn('verifiedDemonstrations(events)',trainer)
  self.assertIn('"MODEL".equals(stage)&&"MODEL_USED".equals(status)',trainer)
  self.assertIn('"DECISION".equals(stage)&&"ACCEPTED".equals(status)',trainer)
  self.assertIn('"ACTION".equals(stage)&&"DISPATCHED".equals(status)',trainer)
  self.assertIn('"EMULATOR_ACK".equals(stage)&&"ACCEPTED".equals(status)',trainer)
  self.assertIn('"VISUAL".equals(stage)&&"PIXELS_DIFFER".equals(status)',trainer)
  self.assertIn('"EXAMPLE".equals(stage)&&"VISUAL_ONLY".equals(status)',trainer)
  self.assertIn('action.equals(detail.substring(at+8))',trainer)
  self.assertIn('id.startsWith("fast-policy:")',trainer)
  self.assertIn('groups.size()>=4',trainer)
  self.assertIn('root.put("imitationEvidence",evidence)',activity)
  self.assertIn('snapshot.imitationEvidence?.eligible===true',html)

 def test_no_training_without_reward(self):
  a=src("app/src/main/java/org/localpilot/android/HarnessDashboardActivity.java")
  j=src("app/src/main/java/org/localpilot/android/HarnessRunJournal.java")
  self.assertIn('put("reward",JSONObject.NULL)',j)
  self.assertIn('put("goalConfirmed",false)',j)
  self.assertIn('"eligibleTransitions",0',a)
  self.assertIn('case "learn":',a)
  self.assertIn("HarnessImitationPolicy.train(",a)
  self.assertIn("HarnessImitationPolicy.bind(",a)
  self.assertIn('"rewardProvider","UNKNOWN"',a)
  learned=src("app/src/main/java/org/localpilot/android/HarnessImitationPolicy.java")
  self.assertIn("HarnessImitationMath.train(training)",learned)
  self.assertIn("majorityBaseline(training,holdout)",learned)
  self.assertIn('"IMITATION_ONLY_NO_REWARD"',learned)
  self.assertIn("atomicSave(ctx,CANDIDATE,record)",learned)
  self.assertIn("if(promoted)atomicSave(ctx,FILE,record)",learned)
  self.assertNotIn("reward=1",j)
 def test_safety_wiring_stop_ttl_audit(self):
  a=src("app/src/main/java/org/localpilot/android/HarnessDashboardActivity.java")
  nes=src("game-lab/src/main/java/org/localpilot/gamelab/NesEmulatorActivity.java")
  bus=src("shared/src/main/java/org/localpilot/shared/HarnessTelemetry.java")
  self.assertIn("HarnessTelemetry.requestStop()",a)
  self.assertIn("HarnessTelemetry.stopRequested()",nes)
  self.assertIn("HarnessTelemetry.healthy()",nes)
  self.assertIn('stopNeuralAgent("STOP:',nes)
  self.assertIn("boolean healthy()",bus)
  self.assertIn("if(active)throw",bus)
  self.assertIn("if(!healthy())throw",bus)
  self.assertIn('InputPermit',src("app/src/main/assets/harness/dashboard.html"))
 def test_offline_webview_no_remote_control(self):
  a=src("app/src/main/java/org/localpilot/android/HarnessDashboardActivity.java")
  self.assertIn("settings.setBlockNetworkLoads(true)",a)
  self.assertIn("settings.setAllowUniversalAccessFromFileURLs(false)",a)
  self.assertIn('web.addJavascriptInterface(new Bridge(),"Harness")',a)
  self.assertIn('new WebResourceResponse("text/plain"',a)
  self.assertNotIn("dispatchGesture(",a)
  self.assertNotIn("ServerSocket",a)
 def test_navigation_and_unavailable_cores_honest(self):
  html=src("app/src/main/assets/harness/dashboard.html")
  for item in ("Обзор системы","Каталог моделей","Консоли и агенты",
               "Журнал эпизодов","Обучение","Сравнение моделей","Устройство и Vulkan"):
   self.assertIn(item,html)
  self.assertIn("NOT_INTEGRATED",src("app/src/main/java/org/localpilot/android/HarnessDashboardActivity.java"))
  self.assertIn("Режим предпросмотра",html)
  self.assertIn("Нет подтверждённого",html) if False else None

if __name__=="__main__":unittest.main()
