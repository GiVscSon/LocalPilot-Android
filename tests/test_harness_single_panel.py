"""Harness v1: ONE native panel reads actual model/console state; no demo engines."""
from pathlib import Path
import unittest

ROOT=Path(__file__).resolve().parents[1]
def source(file):return (ROOT/file).read_text(encoding="utf-8")

class SingleNativeHarnessConsole(unittest.TestCase):
 def setUp(self):
  self.dash=source("app/src/main/java/org/localpilot/android/HarnessDashboardActivity.java")
  self.nes=source("game-lab/src/main/java/org/localpilot/gamelab/NesEmulatorActivity.java")
  self.model=source("model-lab/src/main/java/org/localpilot/android/ModelLabActivity.java")
  self.coordinator=source("shared/src/main/java/org/localpilot/shared/HarnessCoordinator.java")
  self.main=source("app/src/main/java/org/localpilot/android/MainActivity.java")
 def test_one_internal_activity_and_one_launcher(self):
  manifest=source("app/src/main/AndroidManifest.xml")
  self.assertIn('android:name=".HarnessDashboardActivity" android:exported="false"',manifest)
  self.assertIn('unifiedModelLabEnabled',manifest)
  self.assertIn("new Intent(this,HarnessDashboardActivity.class)",self.main)
  self.assertNotIn("android.intent.action.MAIN",self.dash)
  self.assertNotIn("dispatchGesture",self.dash)
  self.assertNotIn("WebView",self.dash)
 def test_existing_controller_is_single_owner_and_observable(self):
  self.assertIn("HarnessCoordinator.nes().snapshot()",self.dash)
  self.assertIn("public synchronized Snapshot snapshot()",self.coordinator)
  self.assertIn("HarnessCoordinator.nes()",self.nes)
  self.assertNotIn("new HarnessCoordinator()",self.dash)
  self.assertIn("HarnessCoordinator.nes().stop(",self.dash)
  self.assertIn("ConsoleAgentContract.DASHBOARD_STOP",self.dash)
  self.assertIn("ConsoleAgentContract.DASHBOARD_STOP",self.nes)
 def test_dashboard_binds_actual_model_used_on_next_nes_request(self):
  binding=source("model-lab/src/main/java/org/localpilot/android/HarnessModelBinding.java")
  self.assertIn("HarnessModelBinding.assign(this,e)",self.dash)
  self.assertIn("HarnessModelBinding.resolve(this,store.list())",self.model)
  self.assertIn("store=harnessStore(this)",self.model)
  self.assertIn("engine.proposeNesButton(",self.model)
  self.assertIn("modelSha",binding)
  self.assertIn("commit()",binding)
 def test_real_episode_journal_never_becomes_fake_reward(self):
  self.assertIn("ConsoleExperienceMemory.summary(",self.dash)
  self.assertIn("ConsoleExperienceMemory.read(",self.dash)
  self.assertIn("Награда: НЕИЗВЕСТНА",self.dash)
  self.assertIn("Достоверных игровых наград NES: 0",self.dash)
  self.assertNotIn("reward=1",self.dash)
  self.assertIn("STOP: журнал повреждён",self.dash)
 def test_gpu_is_real_backend_status_not_fake_benchmark(self):
  self.assertIn("BuildConfig.VULKAN_COMPILED",self.dash)
  self.assertIn("ModelLabActivity.harnessGpuLayers()",self.dash)
  self.assertIn("НЕ ПОДТВЕРЖДЁН",self.dash)
  self.assertNotIn("gpuScore",self.dash)
 def test_console_sectors_and_android_navigation(self):
  for title in ("Обзор","Модели","Консоли","Эпизоды","Обучение","GPU и STOP"):
   self.assertIn('"'+title+'"',self.dash)
  script=source("tools/unified_in_app_smoke.py")
  self.assertIn('tap_text("Harness Console")',script)
  self.assertIn('wait_activity(DASH)',script)
  self.assertIn('tap_text("Открыть ModelLab")',script)
  self.assertIn('wait_activity(NES)',script)

if __name__=="__main__":unittest.main()
