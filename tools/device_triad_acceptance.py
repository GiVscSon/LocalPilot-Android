#!/usr/bin/env python3
"""Non-destructive physical Android smoke test for the three installed APKs.

Usage (PC, Termux or CI with a real connected Android device):
  python3 tools/device_triad_acceptance.py --serial SERIAL

This checks package identity, exported launchers and a real compositor screenshot
without persisting the screenshot. It never installs APKs, changes settings,
enables Accessibility, injects game controls, reads model weights, or declares
inference/gameplay PASS without a human observation.
"""
from __future__ import annotations
import argparse
import json
import os
from pathlib import Path
import struct
import subprocess
import sys

PACKAGES = {
    "GameLab": ("org.localpilot.gamelab", "org.localpilot.gamelab.TestDeviceActivity"),
    "ModelLab": ("org.localpilot.modellab", "org.localpilot.android.ModelLabActivity"),
}
PILOT = [
    ("org.localpilot.android.dev.install1081", "org.localpilot.android.MainActivity"),
    ("org.localpilot.android", "org.localpilot.android.MainActivity"),
]
REQUIRES_PHYSICAL_DEVICE = [
    "Observe actual NES homebrew and the author's ROM responding to START/A.",
    "Share a fresh NES action: confirm the received PNG is the current frame, not an old one.",
    "Import/download at least one text GGUF and one VL GGUF plus mmproj; confirm real inference.",
    "Check CPU/optional Vulkan speed and thermal behavior on the POCO X7 Pro.",
    "Confirm STOP, background/foreground, rotation, low-storage and failed download recovery.",
    "Check stable release signing and upgrade without losing privately downloaded weights.",
]


def run_adb(serial: str | None, *args: str, timeout: int = 30, binary: bool = False):
    cmd = ["adb"]
    if serial:
        cmd.extend(["-s", serial])
    cmd.extend(args)
    value = subprocess.run(cmd, capture_output=True, timeout=timeout)
    if value.returncode != 0:
        detail = value.stderr.decode("utf-8", "replace")[-600:]
        raise RuntimeError(f"adb {args[0]} failed: {detail}")
    if binary:
        return value.stdout
    return value.stdout.decode("utf-8", "replace").strip()


def compositor_png_dimensions(data: bytes):
    # Screenshots remain in RAM and are never uploaded or written to disk.
    if len(data) < 24 or data[:8] != b"\x89PNG\r\n\x1a\n":
        raise ValueError("adb screencap did not return PNG")
    if data[12:16] != b"IHDR":
        raise ValueError("missing PNG image header")
    width, height = struct.unpack(">II", data[16:24])
    if not (200 <= width <= 12000 and 200 <= height <= 12000):
        raise ValueError("unreasonable Android compositor dimensions")
    return width, height


def probe_device(serial: str | None, perform_launch: bool = True):
    report = {"schema": "localpilot-device-triad-v1", "checks": [],
              "manual_pending": REQUIRES_PHYSICAL_DEVICE.copy()}
    if run_adb(serial, "get-state") != "device":
        raise RuntimeError("ADB is not connected to an unlocked device")
    report["device"] = {
        "model": run_adb(serial, "shell", "getprop", "ro.product.model"),
        "sdk": run_adb(serial, "shell", "getprop", "ro.build.version.sdk"),
        "abi": run_adb(serial, "shell", "getprop", "ro.product.cpu.abi"),
    }
    existing = {}
    for label, (package, activity) in PACKAGES.items():
        paths = run_adb(serial, "shell", "pm", "path", package)
        ok = "package:" in paths
        report["checks"].append({"name": f"{label} installed", "pass": ok, "package": package})
        existing[label] = (package, activity) if ok else None
    for package, activity in PILOT:
        paths = run_adb(serial, "shell", "pm", "path", package)
        if "package:" in paths:
            existing["LocalPilot"] = (package, activity)
            break
    report["checks"].append({"name": "LocalPilot installed",
                              "pass": bool(existing.get("LocalPilot")),
                              "package": existing.get("LocalPilot", ("not installed",))[0]})
    if perform_launch:
        # Explicit exported launchers: never start private NES activity directly.
        for label in ["GameLab", "ModelLab", "LocalPilot"]:
            selected = existing.get(label)
            if selected is None:
                continue
            package, activity = selected
            output = run_adb(serial, "shell", "am", "start", "-W", "-n",
                             f"{package}/{activity}", timeout=30)
            ok = "Error:" not in output and "Error type" not in output and "Exception" not in output
            report["checks"].append({"name": f"{label} launcher", "pass": ok,
                                      "package": package, "detail": output[-220:]})
        png = run_adb(serial, "exec-out", "screencap", "-p", binary=True, timeout=30)
        width, height = compositor_png_dimensions(png)
        report["checks"].append({"name": "real Android compositor screenshot",
                                  "pass": True, "width": width, "height": height})
    report["automated_pass"] = all(item["pass"] for item in report["checks"])
    report["release_ready"] = False  # Manual inference, ROM and signing remain unverified.
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", default=os.environ.get("ANDROID_SERIAL"),
                        help="ADB device serial, e.g. from adb devices")
    parser.add_argument("--no-launch", action="store_true",
                        help="Read device package metadata only")
    parser.add_argument("--output", default="build/reports/device-triad-acceptance.json")
    args = parser.parse_args()
    try:
        report = probe_device(args.serial, not args.no_launch)
        dest = Path(args.output)
        dest.parent.mkdir(parents=True, exist_ok=True)
        dest.write_text(json.dumps(report, ensure_ascii=False, indent=2)+"\n",
                        encoding="utf-8")
        print("DEVICE_TRIAD_AUTOMATED_PASS" if report["automated_pass"]
              else "DEVICE_TRIAD_AUTOMATED_FAIL")
        print("Report:", dest)
        print("Manual physical-device checks still required:",len(report["manual_pending"]))
        return 0 if report["automated_pass"] else 1
    except (FileNotFoundError, OSError, RuntimeError, ValueError,
            subprocess.TimeoutExpired) as error:
        print("DEVICE_TRIAD_BLOCKED:", error, file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
