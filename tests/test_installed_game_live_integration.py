"""Source-linkage assertions. HOST_UNIT only, not real NFS game acceptance."""
import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
def read(path):return (ROOT/path).read_text(encoding="utf-8")

class InstalledGameLiveIntegration(unittest.TestCase):
 def test_picker_opens_real_app_then_binds_existing_service(self):
  picker=read("app/src/main/java/org/localpilot/android/GamePickerActivity.java")
  self.assertIn("manager.getLaunchIntentForPackage(entry.packageName)",picker)
  self.assertIn("startActivity(intent)",picker)
  self.assertIn("service.selectInstalledTarget(entry.packageName, entry.label)",picker)
  self.assertIn("VisualGameTarget.allowed(entry.packageName, getPackageName())",picker)
 def test_service_calls_actual_routing_gate_before_capturing(self):
  service=read("app/src/main/java/org/localpilot/android/PilotService.java")
  self.assertIn("InstalledGameRoute.inspect(getPackageName(),expected,target",service)
  self.assertIn("permit.revoke();",service)
  self.assertIn("if(automatic())startPickedProfile();",service)
  self.assertIn("if(selectedProfile<4){startVisualArcade(ArcadeProfile.values()[selectedProfile]);return;}",service)
  self.assertIn("new Intent(this,JumperCaptureConsentActivity.class)",service)
  self.assertIn("dispatchGesture",service)
 def test_shared_dashboard_shows_real_service_state_and_stops_both(self):
  dashboard=read("app/src/main/java/org/localpilot/android/HarnessDashboardActivity.java")
  self.assertIn("new Intent(this,GamePickerActivity.class)",dashboard)
  self.assertIn("androidPilot.installedGameReadiness(gamePackage)",dashboard)
  self.assertIn("if(androidPilot!=null)androidPilot.stopFromHarness();",dashboard)
  self.assertIn("HarnessCoordinator.nes().requestStop",dashboard)
  service=read("app/src/main/java/org/localpilot/android/PilotService.java")
  self.assertIn('public void stopFromHarness(){stop("DASHBOARD_USER_STOP_ANDROID");}',service)
 def test_no_fake_nfs_vision_or_pocket_binding(self):
  dashboard=read("app/src/main/java/org/localpilot/android/HarnessDashboardActivity.java")
  self.assertIn("Для NFS зрительный ввод и моторная политика НЕ ПОДТВЕРЖДЕНЫ",dashboard)
  picker=read("app/src/main/java/org/localpilot/android/GamePickerActivity.java")
  self.assertIn("NFS и другие 3D-игры не получают профиль автоматически",picker)
  self.assertNotIn("nfsAutoSteer",dashboard)
 def test_same_existing_input_owner_not_second_dispatcher(self):
  helper=read("app/src/main/java/org/localpilot/android/InstalledGameRoute.java")
  self.assertNotIn("dispatchGesture",helper)
  self.assertNotIn("InputPermit(",helper)
  self.assertNotIn("HarnessCoordinator()",helper)

if __name__=="__main__":unittest.main()
