#!/usr/bin/env python3
"""Read-only Android SDK/KVM and actual connected Android OS probe.

Never installs software, launches AVDs, sends input, or asserts that an APK
or a learned policy works. All commands are argv arrays, no shell.
"""
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import platform
import re
import shutil
import stat
import subprocess
import sys

SCHEMA = "android-qa-host-device-probe-v1"
SERIAL = re.compile(r"^[A-Za-z0-9._:-]{2,100}$")
PACKAGES = ("cmdline-tools/latest", "platform-tools", "emulator",
            "platforms/android-30", "platforms/android-35", "platforms/android-36",
            "build-tools/35.0.0", "ndk/27.2.12479018", "cmake/3.22.1")
BIN = {"adb": "platform-tools/adb", "emulator": "emulator/emulator",
       "sdkmanager": "cmdline-tools/latest/bin/sdkmanager",
       "avdmanager": "cmdline-tools/latest/bin/avdmanager",
       "apksigner": "build-tools/35.0.0/apksigner",
       "aapt": "build-tools/35.0.0/aapt"}


def sdk_root(env=None):
    env = os.environ if env is None else env
    value = env.get("ANDROID_SDK_ROOT") or env.get("ANDROID_HOME")
    return Path(value).expanduser() if value else None


def executable(name, root=None):
    if root is not None and name in BIN:
        candidate = root / BIN[name]
        if candidate.is_file() and os.access(candidate, os.X_OK):
            return str(candidate)
    return shutil.which(name)


def revision(path):
    properties = path / "source.properties"
    if not properties.is_file() or properties.is_symlink() or properties.stat().st_size > 100000:
        return None
    for line in properties.read_text(encoding="utf-8", errors="replace").splitlines():
        match = re.fullmatch(r"\s*Pkg\.Revision\s*=\s*([0-9]+(?:\.[0-9]+){0,4})\s*", line)
        if match:
            return match.group(1)
    return None


def kvm_accessible():
    path = Path("/dev/kvm")
    try:
        return stat.S_ISCHR(path.stat().st_mode) and os.access(path, os.R_OK | os.W_OK)
    except OSError:
        return False


def doctor(env=None, *, require_sdk=False, require_kvm=False, kvm=None):
    root = sdk_root(env)
    tools = {name: executable(name, root) for name in (
        "java", "javac", "adb", "emulator", "sdkmanager", "avdmanager",
        "apksigner", "aapt", "gradle")}
    access = kvm_accessible() if kvm is None else kvm
    missing = []
    if require_sdk and not (tools["sdkmanager"] and tools["adb"]):
        missing.append("SDK_AND_ADB_REQUIRED")
    if require_kvm and not access:
        missing.append("KVM_REQUIRED")
    return {
        "schema": SCHEMA, "type": "HOST_DOCTOR",
        "status": "FAIL" if missing else "INSPECTED",
        "host": {"os": platform.system(), "architecture": platform.machine(),
                 "sdkRoot": str(root) if root else None},
        "tools": tools,
        "packageRevisions": {item: revision(root / item) if root else None
                             for item in PACKAGES},
        "kvm": "ACCESSIBLE" if access else "UNAVAILABLE",
        "androidBoot": "NOT_TESTED",
        "physicalDevice": "NOT_TESTED",
        "unmetRequirements": missing,
        "releaseReady": False,
    }


def execute(argv, timeout=12):
    try:
        result = subprocess.run(argv, capture_output=True, text=True, timeout=timeout)
    except (OSError, subprocess.TimeoutExpired):
        return False, ""
    return result.returncode == 0, result.stdout.strip()[:2400]


def device(serial, *, adb=None, run=execute, require_boot=False):
    if not SERIAL.fullmatch(serial) or serial.startswith("-"):
        raise ValueError("SERIAL_INVALID_OR_AMBIGUOUS")
    adb = adb or executable("adb", sdk_root())
    if not adb:
        raise ValueError("ADB_MISSING")
    success, output = run([adb, "devices", "-l"], 12)
    if not success:
        raise ValueError("ADB_LIST_FAILED")
    devices = {}
    for row in output.splitlines()[1:]:
        fields = row.split()
        if len(fields) >= 2:
            devices[fields[0]] = fields[1]
    state = devices.get(serial, "ABSENT")
    report = {
        "schema": SCHEMA, "type": "DEVICE", "serial": serial, "adbState": state,
        "status": "NOT_CONNECTED", "booted": False, "environment": "UNKNOWN",
        "api": None, "model": None, "abi": None,
        "physicalAcceptance": "NOT_TESTED", "appTest": "NOT_TESTED",
        "releaseReady": False,
    }
    if state != "device":
        if require_boot:
            report["status"] = "FAIL"
        report["reason"] = "ADB_DEVICE_NOT_READY"
        return report

    def prop(key):
        ok, value = run([adb, "-s", serial, "shell", "getprop", key], 12)
        return value.strip()[:240] if ok and value.strip() else None

    boot = prop("sys.boot_completed")
    qemu = prop("ro.kernel.qemu")
    sdk = prop("ro.build.version.sdk")
    model = prop("ro.product.model")
    abi = prop("ro.product.cpu.abi")
    emulated = qemu == "1" and serial.startswith("emulator-")
    physical_reported = qemu == "0" and not serial.startswith("emulator-")
    environment = ("EMULATOR_ANDROID" if emulated else
                   "PHYSICAL_ANDROID_REPORTED" if physical_reported else "UNKNOWN")
    ready = boot == "1" and bool(sdk and sdk.isdecimal()) and environment != "UNKNOWN"
    report.update(
        environment=environment, booted=ready,
        api=int(sdk) if sdk and sdk.isdecimal() else None,
        model=model, abi=abi,
        status="BOOT_VERIFIED" if ready else "FAIL" if require_boot else "NOT_VERIFIED",
        reason=None if ready else "BOOT_OR_IDENTITY_NOT_VERIFIED",
    )
    return report


def main():
    p = argparse.ArgumentParser(description=__doc__)
    modes = p.add_subparsers(dest="mode", required=True)
    a = modes.add_parser("doctor")
    a.add_argument("--require-sdk", action="store_true")
    a.add_argument("--require-kvm", action="store_true")
    a.add_argument("--output", type=Path)
    b = modes.add_parser("device")
    b.add_argument("--serial", required=True)
    b.add_argument("--require-boot", action="store_true")
    b.add_argument("--output", type=Path)
    args = p.parse_args()
    try:
        out = (doctor(require_sdk=args.require_sdk, require_kvm=args.require_kvm)
               if args.mode == "doctor" else
               device(args.serial, require_boot=args.require_boot))
    except ValueError as err:
        out = {"schema": SCHEMA, "status": "FAIL", "reason": str(err),
               "releaseReady": False}
    payload = json.dumps(out, ensure_ascii=False, indent=2) + "\n"
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(payload, encoding="utf-8")
    print(payload, end="")
    return 1 if out["status"] == "FAIL" else 0


if __name__ == "__main__":
    sys.exit(main())
