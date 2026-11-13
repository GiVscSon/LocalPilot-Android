#!/usr/bin/env python3
"""Fail-closed READ-ONLY Android upgrade preflight for ONE LocalPilot APK.

NEVER installs, uninstalls, disables services, grants permissions or clears data.
Checks a candidate's package/signature/version against the installed base APK.
This is necessary but not sufficient for a stable physical-device release.
"""
from __future__ import annotations
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import tempfile

MAIN_PACKAGE = "org.localpilot.android"
TEST_PACKAGE = "org.localpilot.android.dev.unified"
CERT_RE = re.compile(r"^Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]{64})$", re.M)
BADGING_RE = re.compile(r"^package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", re.M)
VALID_SIGNATURE_RE = re.compile(r"^Verified using v[23] scheme .*: true$", re.M)

def run(args: list[str]) -> str:
    result = subprocess.run(args, capture_output=True, text=True, check=False)
    if result.returncode != 0:
        raise ValueError("Preflight command failed: " + Path(args[0]).name +
                         " exit=" + str(result.returncode) + " " + result.stderr.strip()[:120])
    return result.stdout

def apk_info(apk: Path, aapt: str, apksigner: str) -> dict:
    if not apk.is_file() or apk.stat().st_size < 1024:
        raise ValueError("APK missing or empty")
    metadata = run([aapt, "dump", "badging", str(apk)])
    m = BADGING_RE.search(metadata)
    if not m:
        raise ValueError("APK package/version metadata unavailable")
    verification = run([apksigner, "verify", "--verbose", "--print-certs", str(apk)])
    certs = CERT_RE.findall(verification)
    if len(certs) != 1 or not VALID_SIGNATURE_RE.search(verification):
        raise ValueError("APK must have one verified v2/v3 signer")
    h = hashlib.sha256()
    with apk.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            h.update(block)
    return {"package": m.group(1), "versionCode": int(m.group(2)),
            "versionName": m.group(3), "signerSha256": certs[0].lower(),
            "apkSha256": h.hexdigest()}

def installed_packages(adb: str, serial: str) -> set[str]:
    output = run([adb, "-s", serial, "shell", "pm", "list", "packages"])
    if not output.strip() or not any(line.startswith("package:") for line in output.splitlines()):
        raise ValueError("Installed package inventory unavailable; cannot infer fresh install")
    packages = set()
    for line in output.splitlines():
        if not line.startswith("package:"):
            raise ValueError("Unexpected installed-package listing format")
        package = line[len("package:"):].strip()
        if not re.fullmatch(r"[a-zA-Z][a-zA-Z0-9_.]*", package):
            raise ValueError("Malformed Android package entry")
        packages.add(package)
    return packages

def installed_base_path(adb: str, serial: str, package: str) -> str:
    output = run([adb, "-s", serial, "shell", "pm", "path", package])
    paths = []
    for line in output.splitlines():
        if not line.startswith("package:"):
            raise ValueError("Unexpected pm path output")
        paths.append(line[len("package:"):].strip())
    base = [x for x in paths if x.endswith("/base.apk")]
    if len(base) != 1 or not base[0].startswith("/data/"):
        raise ValueError("Installed APK base is unavailable or ambiguous")
    return base[0]

def preflight(candidate: Path, *, serial: str, expected_cert: str,
              test_only: bool = False, adb: str = "adb", aapt: str = "aapt",
              apksigner: str = "apksigner") -> dict:
    if not serial or not serial.strip() or serial.startswith("-"):
        raise ValueError("Explicit ADB serial is required")
    if not re.fullmatch(r"[0-9a-fA-F]{64}", expected_cert):
        raise ValueError("Pinned full certificate SHA-256 required")
    target = TEST_PACKAGE if test_only else MAIN_PACKAGE
    fresh = apk_info(candidate, aapt, apksigner)
    if fresh["package"] != target:
        raise ValueError("Candidate package identity is not the expected single LocalPilot package")
    if fresh["signerSha256"] != expected_cert.lower():
        raise ValueError("CANDIDATE_CERT_PIN_MISMATCH: stop before device access")
    if test_only != ("-unified-test-only" in fresh["versionName"]):
        raise ValueError("Test-only candidate/release version mismatch")
    if run([adb, "-s", serial, "get-state"]).strip() != "device":
        raise ValueError("ADB device not ready")
    packages = installed_packages(adb, serial)
    state = "FRESH_INSTALL_NOT_AN_UPGRADE"
    installed = None
    if target in packages:
        base = installed_base_path(adb, serial, target)
        with tempfile.TemporaryDirectory(prefix="localpilot-one-apk-preflight-") as temp:
            local = Path(temp, "installed-base.apk")
            run([adb, "-s", serial, "pull", base, str(local)])
            installed = apk_info(local, aapt, apksigner)
        if installed["package"] != target:
            raise ValueError("Installed base APK has unexpected package identity")
        if installed["signerSha256"] != fresh["signerSha256"]:
            raise ValueError("SIGNATURE_MISMATCH: NEVER uninstall to bypass; data may be lost")
        if fresh["versionCode"] <= installed["versionCode"]:
            raise ValueError("VERSION_NOT_INCREASED: refusing equal-version and downgrade installs")
        state = "SIGNATURE_MATCH_UPGRADE_POSSIBLE"
    other = sorted(p for p in packages if p.startswith("org.localpilot.android.dev.") and p != target)
    return {"schema": "localpilot-one-apk-device-preflight-v1",
            "package": target, "candidate": fresh,
            "installedVersionCode": installed["versionCode"] if installed else None,
            "existingOtherDevPackages": other,
            "status": state, "testOnly": test_only,
            "performedInstall": False, "performedUninstall": False,
            "releaseReady": False, "physicalGameplayVerified": False,
            "upgradeDataPreservationProven": False,
            "warning": "Read-only compatibility check, NOT proof of successful upgrade or gameplay."}

def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--apk", type=Path, required=True)
    p.add_argument("--serial", required=True)
    p.add_argument("--cert", required=True, help="Trusted certificate SHA-256")
    p.add_argument("--test-only", action="store_true",
                   help="Explicitly inspect isolated .dev.unified test APK")
    p.add_argument("--adb", default="adb")
    p.add_argument("--aapt", default="aapt")
    p.add_argument("--apksigner", default="apksigner")
    p.add_argument("--output", type=Path, required=True)
    a = p.parse_args()
    result = preflight(a.apk, serial=a.serial, expected_cert=a.cert,
                       test_only=a.test_only, adb=a.adb, aapt=a.aapt,
                       apksigner=a.apksigner)
    a.output.parent.mkdir(parents=True, exist_ok=True)
    a.output.write_text(json.dumps(result, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print("LOCALPILOT_ONE_APK_PREFLIGHT_PASS: " + result["status"] + " (no install performed)")

if __name__ == "__main__":
    try: main()
    except (ValueError, OSError) as exc:
        raise SystemExit("LOCALPILOT_ONE_APK_PREFLIGHT_STOP: " + str(exc))
