"""Offline integration invariants for LocalPilot ONE user APK."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]


def source(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


class UnifiedAppContracts(unittest.TestCase):
    def test_main_apk_contains_game_lab_sources_and_nes_assets(self):
        gradle = source("app/build.gradle")
        self.assertIn("../game-lab/src/main/java", gradle)
        self.assertIn("../game-lab/src/main/assets", gradle)
        self.assertIn("../game-lab/build/generated/jsnes/assets", gradle)
        self.assertIn("game-lab:bundleVerifiedNesEngine", gradle)

    def test_game_lab_local_entry_does_not_impersonate_external_target(self):
        main = source("app/src/main/java/org/localpilot/android/MainActivity.java")
        self.assertIn("org.localpilot.gamelab.TestDeviceActivity.class", main)
        self.assertIn("private void openExternalGameLab()", main)
        self.assertNotIn("pilot.selectInstalledTarget(getPackageName()", main)
        manifest = source("app/src/main/AndroidManifest.xml")
        for activity in ("TestDeviceActivity", "RetroLibraryActivity",
                         "NesEmulatorActivity"):
            self.assertIn(f"org.localpilot.gamelab.{activity}", manifest)
        self.assertIn('android:exported="false"', manifest)
        self.assertNotIn("org.localpilot.gamelab.TestDeviceActivity\" android:exported=\"true", manifest)

    def test_lab_experience_is_shared_in_same_private_app_only(self):
        game = source(
            "game-lab/src/main/java/org/localpilot/gamelab/TestDeviceActivity.java"
        )
        training = source("app/src/main/java/org/localpilot/android/TrainingActivity.java")
        path = "gamelab-sim-experience-v1.lpx"
        self.assertIn(path, game)
        self.assertIn(path, training)
        self.assertIn("saveEmbeddedExperience();handler.removeCallbacks(clock)", game)
        self.assertIn("org.localpilot.android", game)
        self.assertIn("StandardCopyOption.ATOMIC_MOVE", game)
        self.assertIn("SimExperience.decode(", training)
        self.assertIn("SimLabPolicy.train(", training)

    def test_user_release_contains_one_apk_not_companion(self):
        workflow = source(".github/workflows/unified-16-simulators.yml")
        self.assertIn("name: localpilot-unified-script-one-apk-debug", workflow)
        self.assertIn("path: app/build/outputs/apk/debug/*.apk", workflow)
        self.assertIn("scripts/unified_pipeline.py selfplay", workflow)
        self.assertIn("scripts/unified_pipeline.py apk", workflow)
        self.assertIn("--require-sdk", workflow)

    def test_scripted_lab_cannot_be_a_real_input_authority(self):
        script = source("scripts/unified_pipeline.py")
        self.assertIn('"may_dispatch_real_gestures"', source(
            "tools/gate_sim_autoplay_candidates.py"))
        self.assertIn('"no_android_input_dispatched": True', script)
        self.assertIn('"automatic_release": False', script)
        self.assertIn('report["result"] = "FAIL"', script)
        self.assertIn("b\"TTL_MS=700\"", script)
        pilot = source("app/src/main/java/org/localpilot/android/PilotService.java")
        self.assertIn('gate.consume(ticket,', pilot)
        self.assertIn('permit.consume(', pilot)
        self.assertIn('PANEL_COVERS_ROI', pilot)
        self.assertIn('SafeJournal.Event.VERIFIED_ARCADE', pilot)


if __name__ == "__main__":
    unittest.main()
