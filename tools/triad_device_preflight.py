#!/usr/bin/env python3
"""Read-only Android preflight for a signed three-app LocalPilot release.

Reads installed APKs via adb pull into a temporary local directory; never
installs/uninstalls/clears apps. Refuses certificate drift and downgrades before
an operator considers an ordinary `adb install -r`.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import tempfile

PACKAGES = {
    "LocalPilot": "org.localpilot.android",
    "GameLab": "org.localpilot.gamelab",
    "ModelLab": "org.localpilot.modellab",
}
CERT_RE = re.compile(r"^Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]{64})$", re.M)
PKG_RE = re.compile(r"^package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", re.M)


def cmd(args, *, allow_missing=False):
    completed = subprocess.run(args, capture_output=True, text=True, check=False)
    if completed.returncode != 0:
        if allow_missing and completed.returncode == 1 and not completed.stdout.strip() and not completed.stderr.strip():
            return ""
        raise ValueError(f"Command failed ({completed.returncode}): {Path(args[0]).name} "
                         f"{completed.stderr.strip()[:160]}")
    return completed.stdout


def apk_info(path: Path, aapt: str, apksigner: str):
    if not path.is_file() or path.stat().st_size < 1024:
        raise ValueError(f"APK missing or too small: {path.name}")
    text = cmd([aapt, "dump", "badging", str(path)])
    m = PKG_RE.search(text)
    if not m:
        raise ValueError(f"Cannot read Android package metadata: {path.name}")
    signed = cmd([apksigner, "verify", "--verbose", "--print-certs", str(path)])
    certs = CERT_RE.findall(signed)
    if len(certs) != 1:
        raise ValueError(f"APK must have exactly one valid signer: {path.name}")
    if not re.search(r"^Verified using v[23] scheme .*: true$", signed, re.M):
        raise ValueError(f"APK has no verified modern signature: {path.name}")
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return {"package": m.group(1), "versionCode": int(m.group(2)),
            "versionName": m.group(3), "signerSha256": certs[0].lower(),
            "fileSha256": digest.hexdigest()}


def installed_base_path(adb, serial, package):
    lines = cmd([adb, "-s", serial, "shell", "pm", "path", package], allow_missing=True)
    paths = [line[len("package:"):].strip() for line in lines.splitlines()
             if line.startswith("package:")]
    if not paths:
        if lines.strip():
            raise ValueError(f"pm path output not recognized for {package}")
        return None
    base = [path for path in paths if path.endswith("/base.apk")]
    if len(base) != 1:
        raise ValueError(f"Installed APK base path missing/ambiguous for {package}")
    return base[0]


def check(apks, *, adb="adb", serial, aapt="aapt", apksigner="apksigner"):
    if not serial or not serial.strip():
        raise ValueError("Explicit ADB serial is required")
    if set(apks) != set(PACKAGES):
        raise ValueError("All three APK paths are required")
    state = cmd([adb, "-s", serial, "get-state"]).strip()
    if state != "device":
        raise ValueError("ADB device is not ready")
    results = {}
    with tempfile.TemporaryDirectory(prefix="localpilot-installed-apk-") as tmp:
        for label, package in PACKAGES.items():
            candidate = apk_info(Path(apks[label]), aapt, apksigner)
            if candidate["package"] != package:
                raise ValueError(f"Unexpected package for {label}: {candidate['package']}")
            remote = installed_base_path(adb, serial, package)
            result = {"package": package, "candidateVersionCode": candidate["versionCode"],
                      "candidateSha256": candidate["fileSha256"],
                      "signerSha256": candidate["signerSha256"]}
            if remote is None:
                result["status"] = "NEW_PACKAGE_NO_EXISTING_DATA"
            else:
                local = Path(tmp, f"installed-{label}.apk")
                cmd([adb, "-s", serial, "pull", remote, str(local)])
                old = apk_info(local, aapt, apksigner)
                if old["package"] != package:
                    raise ValueError(f"Installed package ID changed for {label}")
                if old["signerSha256"] != candidate["signerSha256"]:
                    raise ValueError(f"SIGNATURE_MISMATCH: {label}. Stop. NEVER uninstall to bypass.")
                if candidate["versionCode"] <= old["versionCode"]:
                    raise ValueError(f"VERSION_NOT_INCREASED: {label}: "
                                     f"installed={old['versionCode']} candidate={candidate['versionCode']}")
                result["status"] = "SIGNATURE_MATCH_UPGRADE_POSSIBLE"
                result["installedVersionCode"] = old["versionCode"]
            results[label] = result
        # A different-package debug installation is not upgraded or migrated.
        development = installed_base_path(adb, serial, "org.localpilot.android.dev.install1081")
    return {"schema": "localpilot-device-preflight-v1", "serial": serial,
            "releaseReady": False, "performedInstall": False,
            "dataPreservationProven": False,
            "existingDevInstall": bool(development),
            "warning": ("Separate development LocalPilot package exists; "
                        "installing release LocalPilot will not migrate its private data.")
                       if development else None,
            "packages": results}


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--serial", required=True)
    for label in PACKAGES:
        p.add_argument("--" + label.lower(), type=Path, required=True)
    p.add_argument("--adb", default="adb")
    p.add_argument("--aapt", default="aapt")
    p.add_argument("--apksigner", default="apksigner")
    p.add_argument("--output", type=Path, required=True)
    args = p.parse_args()
    result = check({label: getattr(args, label.lower()) for label in PACKAGES},
                   adb=args.adb, serial=args.serial, aapt=args.aapt,
                   apksigner=args.apksigner)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print("TRIAD_DEVICE_PREFLIGHT_PASS (READ-ONLY; physical gameplay NOT VERIFIED)")


if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError, subprocess.SubprocessError) as e:
        raise SystemExit("TRIAD_DEVICE_PREFLIGHT_STOP: " + str(e))