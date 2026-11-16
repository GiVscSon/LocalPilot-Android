#!/usr/bin/env python3
"""Auditable installed Android SDK inventory for the LocalPilot API30/35/36 CI.

A verified toolchain does not mean an emulator ran or an APK is releasable.
Checks the actual installed source.properties, not a label or SDK download list.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import re
import sys

PACKAGES = {
    "cmdline-tools/latest": "22.0",
    "platform-tools": "37.0.1",
    "platforms/android-30": None,
    "platforms/android-35": None,
    "platforms/android-36": None,
    "build-tools/35.0.0": "35.0.0",
    "cmake/3.22.1": "3.22.1",
    "ndk/27.2.12479018": "27.2.12479018",
}
EXECUTABLES = (
    "cmdline-tools/latest/bin/sdkmanager",
    "cmdline-tools/latest/bin/android",
    "platform-tools/adb",
    "build-tools/35.0.0/aapt",
    "build-tools/35.0.0/apksigner",
)
VERSION = re.compile(r"^[0-9]+(?:\.[0-9]+){0,4}$")


def version_tuple(value: str) -> tuple[int, ...]:
    if not isinstance(value, str) or not VERSION.fullmatch(value):
        raise ValueError("Invalid SDK revision")
    return tuple(int(segment) for segment in value.split("."))


def installed_revision(root: Path, package: str) -> str | None:
    properties = root / package / "source.properties"
    if not properties.is_file() or properties.is_symlink():
        return None
    if properties.stat().st_size > 100_000:
        raise ValueError("Oversize SDK source.properties")
    text = properties.read_text(encoding="utf-8", errors="replace")
    for line in text.splitlines():
        matched = re.match(r"^\s*Pkg\.Revision\s*=\s*(.*?)\s*$", line)
        if matched:
            value = matched.group(1)
            if VERSION.fullmatch(value):
                return value
            raise ValueError("Invalid package revision: " + package)
    return None


def inventory(root: Path, *, require_ci: bool = False,
              require_emulator: bool = False) -> dict:
    rows: dict[str, dict] = {}
    errors: list[str] = []
    for package, minimum in PACKAGES.items():
        revision = installed_revision(root, package)
        passed = revision is not None
        if passed and minimum:
            passed = version_tuple(revision) >= version_tuple(minimum)
        if require_ci and not passed:
            errors.append("MISSING_OR_OUTDATED: " + package)
        rows[package] = {
            "revision": revision,
            "minimum": minimum,
            "status": "PASS" if passed else ("MISSING" if revision is None else "OUTDATED"),
        }
    for path in EXECUTABLES:
        executable = root / path
        if require_ci and not (executable.is_file() and not executable.is_symlink()
                               and executable.stat().st_mode & 0o111):
            errors.append("EXECUTABLE_MISSING: " + path)
    emulator_revision = installed_revision(root, "emulator")
    emulator_ok = emulator_revision is not None
    if emulator_ok:
        emulator_ok = version_tuple(emulator_revision) >= version_tuple("37.2.12")
    image_revision = installed_revision(root, "system-images/android-35/default/x86_64")
    if require_emulator and (not emulator_ok or image_revision is None):
        errors.append("EMULATOR_OR_IMAGE_MISSING_OR_OUTDATED")
    if require_emulator and not ((root / "emulator/emulator").is_file()):
        errors.append("EMULATOR_EXECUTABLE_MISSING")

    return {
        "schema": "localpilot-android-sdk-inventory-v1",
        "channel": "stable",
        "status": "PASS" if not errors else "FAIL",
        "packages": rows,
        "emulator": {
            "revision": emulator_revision,
            "minimum": "37.2.12",
            "status": "PASS" if emulator_ok else "UNVERIFIED",
            "api35X86SystemImageRevision": image_revision,
        },
        "errors": errors,
        "checks": {
            "sdkFilesOnly": True,
            "adbDeviceConnection": "NOT_TESTED",
            "emulatorBoot": "NOT_TESTED",
            "physicalPoco": "NOT_TESTED",
            "apkBuild": "NOT_TESTED",
        },
        "releaseReady": False,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--sdk-root", required=True, type=Path)
    parser.add_argument("--require-ci", action="store_true")
    parser.add_argument("--require-emulator", action="store_true")
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    report = inventory(args.sdk_root, require_ci=args.require_ci,
                       require_emulator=args.require_emulator)
    summary = json.dumps(report, ensure_ascii=False, indent=2) + "\n"
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(summary, encoding="utf-8")
    print("ANDROID_SDK_INVENTORY_" + report["status"] +
          " channel=stable emulator_boot=NOT_TESTED")
    for error in report["errors"]:
        print("SDK_ERROR: " + error, file=sys.stderr)
    return 1 if report["status"] != "PASS" else 0


if __name__ == "__main__":
    sys.exit(main())
