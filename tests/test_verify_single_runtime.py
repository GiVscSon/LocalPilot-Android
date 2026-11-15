"""Regression tests for one existing LocalPilot control plane."""
import importlib.util
from pathlib import Path
import unittest

path = Path(__file__).resolve().parents[1] / "tools/verify_single_runtime.py"
spec = importlib.util.spec_from_file_location("single", path)
single = importlib.util.module_from_spec(spec)
spec.loader.exec_module(single)
FILES = [*single.CANONICAL.values(), single.DASHBOARD]
DASH = "HarnessCoordinator.nes().snapshot();"


class SingleRuntimeTests(unittest.TestCase):
    def test_existing_runtime_wiring_passes(self):
        self.assertEqual([], single.violations(FILES, DASH))

    def test_second_owner_fails(self):
        files = FILES + ["another/src/HarnessCoordinator.java"]
        self.assertTrue(single.violations(files, DASH))

    def test_second_ppo_fails(self):
        self.assertTrue(single.violations(FILES + ["mirror/LearningCoordinator.java"], DASH))

    def test_second_emulator_fails(self):
        self.assertTrue(single.violations(FILES + ["other/NesEmulatorActivity.java"], DASH))

    def test_missing_existing_runtime_fails(self):
        self.assertTrue(single.violations(FILES[1:], DASH))

    def test_forged_dashboard_gesture_fails(self):
        self.assertTrue(single.violations(FILES, DASH + " dispatchGesture();"))

    def test_second_dashboard_controller_fails(self):
        self.assertTrue(single.violations(FILES, DASH + " new HarnessCoordinator();"))


if __name__ == "__main__":
    unittest.main()
