#!/usr/bin/env python3
"""Host-only regression tests of non-invasive device acceptance (no adb needed)."""
import importlib.util
import struct
from pathlib import Path
import unittest
from unittest.mock import patch

path = Path(__file__).resolve().parents[1] / "tools" / "device_triad_acceptance.py"
spec = importlib.util.spec_from_file_location("device_triad_acceptance", path)
device = importlib.util.module_from_spec(spec)
spec.loader.exec_module(device)


class TriadSmokeContract(unittest.TestCase):
    def test_real_screenshot_header(self):
        png = b"\x89PNG\r\n\x1a\n" + struct.pack(">I", 13) + b"IHDR" + struct.pack(">II", 1080, 2400)
        self.assertEqual(device.compositor_png_dimensions(png), (1080, 2400))
        with self.assertRaises(ValueError):
            device.compositor_png_dimensions(b"this is not a PNG")
        with self.assertRaises(ValueError):
            device.compositor_png_dimensions(png[:16] + struct.pack(">II", 100000, 1))

    def test_no_launch_collects_packages_without_claiming_release(self):
        called = []
        def fake(serial, *args, **kwargs):
            called.append(args)
            if args == ("get-state",):
                return "device"
            if args[:3] == ("shell", "getprop", "ro.product.model"):
                return "POCO X7 Pro"
            if args[:3] == ("shell", "getprop", "ro.build.version.sdk"):
                return "36"
            if args[:3] == ("shell", "getprop", "ro.product.cpu.abi"):
                return "arm64-v8a"
            if args[:3] == ("shell", "pm", "path"):
                return "package:/data/app/base.apk"
            raise AssertionError("Unexpected command: " + repr(args))
        with patch.object(device, "run_adb", side_effect=fake):
            report = device.probe_device("synthetic-serial", perform_launch=False)
        self.assertTrue(report["automated_pass"])
        self.assertFalse(report["release_ready"])
        self.assertEqual(len(report["manual_pending"]), 6)
        self.assertFalse(any("am" in args for args in called))

    def test_missing_model_lab_fails(self):
        def fake(serial, *args, **kwargs):
            if args == ("get-state",): return "device"
            if args[:3] == ("shell", "getprop", "ro.product.model"): return "Phone"
            if args[:3] == ("shell", "getprop", "ro.build.version.sdk"): return "35"
            if args[:3] == ("shell", "getprop", "ro.product.cpu.abi"): return "arm64-v8a"
            if args[:3] == ("shell", "pm", "path"):
                if args[-1] == "org.localpilot.modellab": return ""
                return "package:/data/app/base.apk"
            raise AssertionError(repr(args))
        with patch.object(device, "run_adb", side_effect=fake):
            report = device.probe_device("synthetic", perform_launch=False)
        self.assertFalse(report["automated_pass"])
        self.assertFalse(report["release_ready"])

if __name__ == "__main__":
    unittest.main()
