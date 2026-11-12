#!/usr/bin/env python3
"""Read-only-on-device UI acceptance for the ONE LocalPilot test APK.

Runs only on an isolated Android emulator, never on a physical phone.
Does not enable Accessibility, use model weights, claim model output correctness,
perform game gestures, access private files or certify a stable release.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET

PACKAGE = "org.localpilot.android.dev.unified"
MAIN = "org.localpilot.android.MainActivity"
MODEL = "org.localpilot.android.ModelLabActivity"
GAMELAB = "org.localpilot.gamelab.TestDeviceActivity"
NES = "org.localpilot.gamelab.NesEmulatorActivity"
UI_PATH = "/data/local/tmp/localpilot-unified-smoke.xml"
BOUNDS = re.compile(r"^\[(\d+),(\d+)\]\[(\d+),(\d+)\]$")


def adb(*argv: str, timeout: int = 45) -> str:
    command = ["adb", *argv]
    result = subprocess.run(command, capture_output=True, text=True,
                            timeout=timeout, check=False)
    if result.returncode != 0:
        # This is an isolated CI emulator. Preserve bounded actual ADB errors:
        # the former exception type alone hid whether UiAutomator or the app failed.
        detail = (result.stderr.strip() or result.stdout.strip())[-800:]
        raise RuntimeError("ADB failed: " + " ".join(argv[:4]) +
                           " (exit=" + str(result.returncode) + ") " + detail)
    return result.stdout


def is_emulator() -> bool:
    return (adb("shell", "getprop", "ro.kernel.qemu").strip() == "1" or
            adb("shell", "getprop", "ro.boot.qemu").strip() == "1")


def screen_size() -> tuple[int, int]:
    match = re.search(r"Physical size:\s*(\d+)x(\d+)",
                      adb("shell", "wm", "size"))
    if not match:
        raise RuntimeError("Unable to determine emulator display size")
    width, height = int(match.group(1)), int(match.group(2))
    if width < 320 or height < 480:
        raise RuntimeError("Unexpected Android display size")
    return width, height


def target_bounds(xml_text: str, label: str) -> tuple[int, int] | None:
    """Locate a visible UI node using a bounded text substring, no OCR."""
    root = ET.fromstring(xml_text)
    for node in root.iter("node"):
        visible = (node.attrib.get("text", "") + " " +
                   node.attrib.get("content-desc", ""))
        if label.casefold() not in visible.casefold():
            continue
        box = BOUNDS.fullmatch(node.attrib.get("bounds", ""))
        if box is None:
            continue
        x1, y1, x2, y2 = (int(n) for n in box.groups())
        if x1 < x2 and y1 < y2:
            return (x1 + x2) // 2, (y1 + y2) // 2
    return None


def recover_system_ui_anr(xml_text: str) -> bool:
    """Acknowledge only Android SYSTEM UI's transient Wait dialog.

    Never dismiss an ANR of LocalPilot or any other application, and never
    return a passing activity result merely because the dialog disappeared.
    """
    root = ET.fromstring(xml_text)
    texts = [node.attrib.get("text", "").strip().casefold()
             for node in root.iter("node")]
    if not any(label in ("system ui isn't responding",
                         "system ui is not responding") for label in texts):
        return False
    wait = None
    for node in root.iter("node"):
        if node.attrib.get("text", "").strip().casefold() != "wait":
            continue
        box = BOUNDS.fullmatch(node.attrib.get("bounds", ""))
        if box is None:
            continue
        x1, y1, x2, y2 = (int(n) for n in box.groups())
        if x1 < x2 and y1 < y2:
            wait = ((x1 + x2) // 2, (y1 + y2) // 2)
            break
    if wait is None:
        raise RuntimeError("System UI ANR detected; no safe Wait action")
    adb("shell", "input", "tap", str(wait[0]), str(wait[1]), timeout=15)
    return True


def dump_ui() -> str:
    """Require a FRESH, real UiAutomator hierarchy; never accept cached XML."""
    failures = []
    for compressed in (False, True, False, True, False):
        try:
            # Removing only our own scratch dump prevents a stale false PASS.
            adb("shell", "rm", "-f", UI_PATH, timeout=12)
            command = ("shell", "uiautomator", "dump")
            if compressed:
                command += ("--compressed",)
            adb(*command, UI_PATH, timeout=45)
            value = adb("shell", "cat", UI_PATH, timeout=15)
            root = ET.fromstring(value)
            if root.tag != "hierarchy" or not list(root.iter("node")):
                raise RuntimeError("UiAutomator returned no accessible UI nodes")
            if recover_system_ui_anr(value):
                failures.append("System UI ANR dialog: selected Wait, must reverify app")
                time.sleep(2)
                continue
            return value
        except (ET.ParseError, RuntimeError, subprocess.TimeoutExpired) as error:
            failures.append(type(error).__name__ + ": " + str(error)[-550:])
            time.sleep(2)
    raise RuntimeError("Unable to read on-device UI hierarchy; attempts: " +
                       " | ".join(failures)[-1700:])


def write_failure_diagnostics(output: Path, error: Exception) -> None:
    """Capture bounded emulator evidence without modifying system settings."""
    directory = output.parent
    directory.mkdir(parents=True, exist_ok=True)
    evidence = {"result": "FAIL", "exception": str(error)[:1800],
                "source": "isolated Android CI emulator", "releaseReady": False}
    for name, args in (
        ("focus", ("shell", "dumpsys", "window")),
        ("activity", ("shell", "dumpsys", "activity", "activities")),
        ("crashes", ("shell", "logcat", "-d", "-t", "250", "-s",
                     "AndroidRuntime:E", "UiAutomator:E", "ActivityTaskManager:E")),
    ):
        try:
            value = adb(*args, timeout=20)
            evidence[name] = value[-3500:]
        except (RuntimeError, subprocess.TimeoutExpired, OSError) as diagnostic:
            evidence[name + "_error"] = str(diagnostic)[-350:]
    (directory / "failure-diagnostics.json").write_text(
        json.dumps(evidence, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    # Screenshots come only from the ephemeral test emulator, not a user phone.
    try:
        image = subprocess.run(["adb", "exec-out", "screencap", "-p"],
                               capture_output=True, timeout=20, check=False)
        if image.returncode == 0 and image.stdout.startswith(b"\x89PNG\r\n\x1a\n"):
            (directory / "failure-screen.png").write_bytes(image.stdout)
    except (OSError, subprocess.TimeoutExpired):
        pass


def scroll_up() -> None:
    width, height = screen_size()
    x = width // 2
    adb("shell", "input", "swipe", str(x), str(int(height * .78)),
        str(x), str(int(height * .26)), "280")


def tap_text(label: str, swipes: int = 6) -> None:
    for index in range(swipes + 1):
        bounds = target_bounds(dump_ui(), label)
        if bounds is not None:
            adb("shell", "input", "tap", str(bounds[0]), str(bounds[1]))
            return
        if index < swipes:
            scroll_up()
            time.sleep(.35)
    raise RuntimeError("Required in-app button not visible: " + label)


def resumed_activity() -> str:
    text = adb("shell", "dumpsys", "activity", "activities", timeout=15)
    lines = [line.strip() for line in text.splitlines()
             if ("topResumedActivity" in line or
                 "mResumedActivity" in line or
                 "ResumedActivity:" in line)]
    matches = [line for line in lines if PACKAGE + "/" in line]
    if not matches:
        raise RuntimeError("No resumed activity in the installed LocalPilot test package")
    return matches[-1]


def wait_activity(class_name: str, seconds: int = 12) -> None:
    deadline = time.monotonic() + seconds
    last = ""
    while time.monotonic() < deadline:
        try:
            last = resumed_activity()
        except RuntimeError:
            pass
        if PACKAGE + "/" + class_name in last:
            return
        time.sleep(.4)
    raise RuntimeError("Activity did not resume: " + class_name +
                       "; last=" + last[:200])


def one_process() -> str:
    pids = adb("shell", "pidof", PACKAGE).strip().split()
    if len(pids) != 1 or not pids[0].isdigit():
        raise RuntimeError("Unified APK is not running as one Android process")
    return pids[0]


def companion_absent(package: str) -> bool:
    # Android may return exit code 1 with no output for 'pm path <absent>'.
    # 'pm list packages <filter>' succeeds on absence. Require exact match,
    # never treat a failed ADB invocation as proof of non-installation.
    listing = adb("shell", "pm", "list", "packages", package)
    return all(line.strip() != "package:" + package
               for line in listing.splitlines())


def verify(apk: Path, output: Path) -> dict:
    if not is_emulator():
        raise RuntimeError("EMULATOR_ONLY: will not install/test on a physical phone")
    if not apk.is_file():
        raise RuntimeError("Missing single unified APK; do not substitute three test APKs")
    if not companion_absent("org.localpilot.modellab"):
        raise RuntimeError("Separate ModelLab APK exists: test requires only one installation")
    if not companion_absent("org.localpilot.gamelab"):
        raise RuntimeError("Separate GameLab APK exists: test requires only one installation")
    accessibility_before = adb("shell", "settings", "get", "secure",
                               "enabled_accessibility_services").strip()
    adb("install", str(apk), timeout=100)
    adb("shell", "am", "start", "-W", "-n", PACKAGE + "/" + MAIN)
    wait_activity(MAIN)
    process = one_process()
    checks = [{"name": "single_installed_application", "status": "PASS"}]

    tap_text("ModelLab · GGUF")
    wait_activity(MODEL)
    if one_process() != process:
        raise RuntimeError("ModelLab launched in a different process")
    if target_bounds(dump_ui(), "ModelLab · локальные нейросети") is None:
        raise RuntimeError("Embedded ModelLab screen title not visible")
    checks.append({"name": "in_app_model_lab_screen", "status": "PASS"})

    tap_text("Вернуться в GameLab")
    wait_activity(GAMELAB)
    if one_process() != process:
        raise RuntimeError("GameLab launched in a different process")
    checks.append({"name": "in_app_game_lab_navigation", "status": "PASS"})

    tap_text("Настоящая NES")
    wait_activity(NES)
    if one_process() != process:
        raise RuntimeError("NES launched in a different process")
    checks.append({"name": "in_app_nes_navigation", "status": "PASS"})

    adb("shell", "input", "keyevent", "KEYCODE_BACK")
    wait_activity(GAMELAB)
    adb("shell", "input", "keyevent", "KEYCODE_BACK")
    wait_activity(MODEL)
    adb("shell", "input", "keyevent", "KEYCODE_BACK")
    wait_activity(MAIN)
    checks.append({"name": "back_stack_single_package", "status": "PASS"})

    accessibility_after = adb("shell", "settings", "get", "secure",
                              "enabled_accessibility_services").strip()
    if accessibility_after != accessibility_before:
        raise RuntimeError("Opening ModelLab/GameLab changed Android Accessibility settings")
    checks.append({"name": "no_accessibility_settings_mutation", "status": "PASS"})

    report = {
        "schema": "localpilot-unified-device-ui-test-v1",
        "appId": PACKAGE,
        "emulatorOnly": True,
        "singleProcess": True,
        "separateGameLabInstalled": False,
        "separateModelLabInstalled": False,
        "checks": checks,
        "realGameMotion": "NOT_TESTED",
        "modelGeneration": "NOT_TESTED",
        "physicalAndroidTest": "NOT_VERIFIED",
        "permanentReleaseSigner": "NOT_VERIFIED",
        "releaseReady": False
    }
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n",
                      encoding="utf-8")
    return report


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    try:
        result = verify(args.apk, args.output)
    except (OSError, RuntimeError, ET.ParseError, subprocess.TimeoutExpired) as error:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        try:
            if is_emulator():
                write_failure_diagnostics(args.output, error)
        except (OSError, RuntimeError, subprocess.TimeoutExpired):
            pass
        args.output.write_text(json.dumps({
            "schema": "localpilot-unified-device-ui-test-v1",
            "result": "FAIL", "reason": str(error)[:500],
            "releaseReady": False}, ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8")
        raise SystemExit("UNIFIED_DEVICE_UI_SMOKE_FAIL: " + str(error))
    print("UNIFIED_DEVICE_UI_SMOKE_PASS checks=" + str(len(result["checks"])) +
          "; PHYSICAL DEVICE NOT VERIFIED; NOT A STABLE RELEASE")


if __name__ == "__main__":
    main()
