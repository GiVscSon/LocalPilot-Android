"""Offline tests for the script-only LocalPilot operator.

Run: python -m unittest discover -s scripts -p "test_*.py"
"""
import importlib.util
from pathlib import Path
import tempfile
import unittest
from unittest import mock
import zipfile

spec = importlib.util.spec_from_file_location("localpilot_operator", Path(__file__).with_name("localpilot.py"))
op = importlib.util.module_from_spec(spec)
spec.loader.exec_module(op)


class OperatorTests(unittest.TestCase):
    def test_selects_only_completed_unified_success(self):
        sample = [
            {"status": "completed", "conclusion": "failure", "databaseId": 3},
            {"status": "in_progress", "conclusion": None, "databaseId": 2},
            {"status": "completed", "conclusion": "success", "databaseId": 1},
        ]
        self.assertEqual(op.latest_success(sample)["databaseId"], 1)
        self.assertIsNone(op.latest_success(sample[:2]))

    def test_validate_16_model_assets_without_sdk(self):
        with tempfile.TemporaryDirectory() as d:
            apk = Path(d) / "app-debug.apk"
            with zipfile.ZipFile(apk, "w") as a:
                for i in range(16):
                    a.writestr(f"assets/adaptive-engine-v1/shape-{i}.zip", b"model")
                a.writestr("classes.dex", b"dex\\n035")
                a.writestr("assets/nes/jsnes.min.js", b"emulator")
                a.writestr("assets/nes/index.html", b"<html></html>")
                a.writestr("assets/nes/latched-buttons.js", b"controller")
                a.writestr("assets/localpilot-game-script.lps",
                           b"LPS1\\n" + b"model|x|SIM_ONLY|SIM_ONLY\\n" * 16)
            with mock.patch.object(op.shutil, "which", return_value=None):
                checked = op.inspect_apk(apk, 16)
                self.assertEqual(checked["model_assets"], 16)
                self.assertFalse(checked["signature_verified"])
                with self.assertRaisesRegex(RuntimeError, "requires 17 model assets"):
                    op.inspect_apk(apk, 17)

    def test_script_map_lists_all_policies_and_distinguishes_training_sources(self):
        import io
        import contextlib
        import json
        output = io.StringIO()
        with contextlib.redirect_stdout(output):
            op.cmd_script_map(None)
        state = json.loads(output.getvalue())
        self.assertEqual(state["schema"], "LPS1")
        self.assertEqual(len(state["native_controller_routes"]), 11)
        self.assertEqual(len(state["simulation_models"]), 16)
        self.assertTrue(all(item["experience"] == "SIM_ONLY"
                            for item in state["simulation_models"]))

    def test_stop_report_classifies_actual_localpilot_logcat_without_ack_reward(self):
        log = (
            "11-08 01:00:01.001 I/LocalPilotStop(123): PAUSE code=WINDOW_STATE_UNAVAILABLE epoch=9 input=disabled\n"
            "11-08 01:00:01.200 I/LocalPilotStop(123): RECOVER focus=verified epoch=9 old_tickets=invalid\n"
            "11-08 01:02:00.001 I/LocalPilotStop(123): STOP code=10 epoch=9 profile=4 sent=3 ack=3 grid_verified=0\n"
        )
        summary = op.summarize_stop_events(log)
        self.assertEqual(summary["event_counts"], {"STOP": 1, "PAUSE": 1, "RECOVER": 1})
        self.assertEqual(summary["last_stop_code"], 10)
        self.assertIn("TARGET_BOUNDS_CHANGED", summary["last_stop_hint"])
        self.assertTrue(summary["android_ack_not_visual_confirmation"])

    def test_no_implicit_unsafe_device_serial(self):
        for bad in ("", "abc;rm -rf", "foo bar", "$(cat secret)", "\n"):
            with self.assertRaisesRegex(RuntimeError, "serial"):
                op.adb(bad, "get-state")

    def test_install_requires_explicit_user_approval(self):
        args = type("Args", (), {"yes": False, "apk": "/tmp/does-not-exist", "serial": "device"})()
        with self.assertRaisesRegex(RuntimeError, "explicit --yes"):
            op.cmd_install(args)

    def test_default_diagnostic_does_not_capture_or_change_device(self):
        calls = []
        def mock_adb(serial, *args, **kwargs):
            calls.append(args)
            if args == ("get-state",):
                return "device\n"
            return ""
        with tempfile.TemporaryDirectory() as d, mock.patch.object(op, "adb", side_effect=mock_adb):
            args = type("Args", (), {
                "serial": "192.168.31.235:37735",
                "output": d,
                "screenshot": False,
            })()
            op.cmd_device_report(args)
            self.assertTrue(any(Path(d).rglob("device.json")))
        self.assertFalse(any("install" in a or "tap" in a or "put" in a or "screencap" in a for a in calls))


if __name__ == "__main__":
    unittest.main()
