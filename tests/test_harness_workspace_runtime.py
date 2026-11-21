"""Source-linked operational workspace: no claims about untested device UI."""
import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
def source(path):return (ROOT/path).read_text(encoding="utf-8")

class WorkspaceRuntimeAcceptance(unittest.TestCase):
 def test_real_environment_snapshots_are_read_not_simulated(self):
  e=source("app/src/main/java/org/localpilot/android/HarnessWorkspaceEngine.java")
  s=source("app/src/main/java/org/localpilot/android/PilotService.java")
  self.assertIn("HarnessCoordinator.nes()",e)
  self.assertIn("PilotService.instance",e)
  self.assertIn("android.harnessEnvironmentState()",e)
  self.assertIn("nes.hasPending()||nes.actionInFlight()",e)
  self.assertIn("public HarnessAndroidState harnessEnvironmentState()",s)
  self.assertIn("captureFrames,confirmed,lastStopReason",s)
 def test_one_real_console_and_operational_engines(self):
  d=source("app/src/main/java/org/localpilot/android/HarnessDashboardActivity.java")
  for text in ('"Окружения"->environments()', 'HarnessWorkspaceEngine.inspect(this)',
               'new Intent(this,GamePickerActivity.class)',
               'this::openNES', 'this::openModelLab', 'this::stopWorkspace',
               'androidPilot.stopFromHarness()', 'HarnessCoordinator.nes().requestStop'):
   with self.subTest(text=text):self.assertIn(text,d)
  self.assertNotIn("new HarnessCoordinator()",d)
 def test_live_gate_is_checked_at_three_real_entrypoints(self):
  d=source("app/src/main/java/org/localpilot/android/HarnessDashboardActivity.java")
  p=source("app/src/main/java/org/localpilot/android/GamePickerActivity.java")
  s=source("app/src/main/java/org/localpilot/android/PilotService.java")
  self.assertIn('if(!HarnessWorkspaceEngine.inspect(this).canStartNes())',d)
  self.assertIn('if (!HarnessWorkspaceEngine.inspect(this).canStartAndroid())',p)
  self.assertIn('if(HarnessWorkspaceEngine.inspect(this).nesBusy())',s)
 def test_real_harness_console_available_in_production_unified_apk(self):
  main=source("app/src/main/java/org/localpilot/android/MainActivity.java")
  manifest=source("app/src/main/AndroidManifest.xml")
  self.assertIn('if(BuildConfig.UNIFIED_MODELLAB) {',main)
  self.assertIn('new Intent(this,HarnessDashboardActivity.class)',main)
  self.assertIn('new Intent(this,ModelLabActivity.class)',main)
  self.assertNotIn('if(BuildConfig.HARNESS_TEST_FIXTURES)button(box,"🎮 Harness Console',main)
  self.assertIn('android:name=".HarnessDashboardActivity" android:exported="false"',manifest)
  self.assertIn('pilot.showPanel()',main)
 def test_no_new_input_owner_or_fake_success_path(self):
  e=source("app/src/main/java/org/localpilot/android/HarnessWorkspaceEngine.java")
  p=source("app/src/main/java/org/localpilot/android/HarnessWorkspacePolicy.java")
  for val in ("dispatchGesture","new HarnessCoordinator()","reward=1","ScriptedNfs"):
   self.assertNotIn(val,e)
   self.assertNotIn(val,p)
  self.assertIn("Для NFS зрительный ввод и моторная политика НЕ ПОДТВЕРЖДЕНЫ",
    source("app/src/main/java/org/localpilot/android/HarnessDashboardActivity.java"))

if __name__=="__main__":unittest.main()
