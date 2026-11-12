#!/usr/bin/env python3
"""Fail-closed, offline audit of three RELEASE-signed Android APK candidates.

No signing keys, passwords, game screenshots or model weights are handled here.
The manifest explicitly records that physical acceptance is NOT done.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import zipfile

PACKAGES = {
    "LocalPilot": "org.localpilot.android",
    "GameLab": "org.localpilot.gamelab",
    "ModelLab": "org.localpilot.modellab",
}
APK_CERT = re.compile(r"^Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]{64})$", re.M)
APK_PACKAGE = re.compile(r"^package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", re.M)
SDK_MIN = re.compile(r"^sdkVersion:'(\d+)'", re.M)
SDK_TARGET = re.compile(r"^targetSdkVersion:'(\d+)'", re.M)


def command(args):
    completed = subprocess.run(args, capture_output=True, text=True, check=False)
    if completed.returncode:
        raise ValueError(f"Android SDK tool returned {completed.returncode}: {Path(args[0]).name}")
    return completed.stdout


def digest(path):
    h = hashlib.sha256()
    with path.open("rb") as f:
        for b in iter(lambda: f.read(1024 * 1024), b""):
            h.update(b)
    return h.hexdigest()


def check_one(label, path, aapt, apksigner, expected):
    if label not in PACKAGES or not path.is_file():
        raise ValueError(f"Missing {label} APK")
    with zipfile.ZipFile(path) as archive:
        names = set(archive.namelist())
        if archive.testzip() is not None or not {"AndroidManifest.xml", "classes.dex"} <= names:
            raise ValueError(f"Invalid APK archive: {label}")
        if label in ("GameLab", "LocalPilot") and not {
            "assets/nes/jsnes.min.js", "assets/nes/latched-buttons.js"
        } <= names:
            raise ValueError(f"NES assets missing: {label}")
        if label == "ModelLab":
            if "lib/arm64-v8a/liblocalpilot_embedded.so" not in names:
                raise ValueError("ModelLab native inference runtime missing")
            if any(n.startswith("assets/models/") and n.endswith(".gguf") for n in names):
                raise ValueError("Install-only ModelLab must not bundle GGUF weights")
            if path.stat().st_size >= 40 * 1024 * 1024:
                raise ValueError("ModelLab import-only APK unexpectedly large")
    badging = command([aapt, "dump", "badging", str(path)])
    match = APK_PACKAGE.search(badging)
    if not match or match.group(1) != PACKAGES[label]:
        raise ValueError(f"Unexpected APK package for {label}")
    version_code = int(match.group(2))
    version_name = match.group(3)
    if version_code <= 0 or "dev" in version_name.lower() :
        # Build variants must be genuine release identities; artifacts are separately
        # labelled candidates because physical release acceptance has not happened.
        raise ValueError(f"Unexpected release version of {label}")
    minimum, target = SDK_MIN.search(badging), SDK_TARGET.search(badging)
    if not minimum or not target or int(minimum.group(1)) != 30 or int(target.group(1)) != 35:
        raise ValueError(f"Unexpected Android SDK target for {label}")
    verification = command([apksigner, "verify", "--verbose", "--print-certs", str(path)])
    certs = APK_CERT.findall(verification)
    if len(certs) != 1 or certs[0].lower() != expected:
        raise ValueError(f"Signing key continuity failed for {label}")
    if not any(f"Verified using v{scheme} scheme" in verification and
               re.search(fr"Verified using v{scheme} scheme.*: true", verification)
               for scheme in (2, 3)):
        raise ValueError(f"No verified modern APK signature for {label}")
    if label == "ModelLab":
        permissions = command([aapt, "dump", "permissions", str(path)])
        if re.search(r"BIND_ACCESSIBILITY_SERVICE|SYSTEM_ALERT_WINDOW|MANAGE_EXTERNAL_STORAGE|FOREGROUND_SERVICE_MEDIA_PROJECTION", permissions):
            raise ValueError("ModelLab must not request game-control permissions")
    return {"package": PACKAGES[label], "versionCode": version_code,
            "versionName": version_name, "signerSha256": certs[0].lower(),
            "sha256": digest(path), "bytes": path.stat().st_size, "filename": path.name}


def audit(apks, aapt, apksigner, expected, sha, previous=None):
    if not re.fullmatch(r"[0-9a-fA-F]{40}", sha):
        raise ValueError("Source commit must be the full 40-digit Git SHA")
    if not isinstance(expected, dict) or set(expected) != set(PACKAGES):
        raise ValueError("A pinned signing certificate is required for each package")
    pins = {}
    for label, cert in expected.items():
        if not isinstance(cert, str) or not re.fullmatch(r"[0-9a-fA-F]{64}", cert):
            raise ValueError(f"Pinned signing certificate must have 64 hex digits for {label}")
        pins[label] = cert.lower()
    if len(set(pins.values())) != len(PACKAGES):
        raise ValueError("Release signing certificates must be distinct for all three packages")
    if set(apks) != set(PACKAGES):
        raise ValueError("All three Android packages are mandatory")
    results = {key: check_one(key, Path(apks[key]), aapt, apksigner, pins[key])
               for key in PACKAGES}
    if previous is not None:
        if previous.get("schema") != "localpilot-triad-candidate-v2":
            raise ValueError("Unknown previous release metadata schema")
        old = previous.get("packages", {})
        for name in PACKAGES:
            item = old.get(name, {})
            if item.get("package") != results[name]["package"]:
                raise ValueError(f"Previous {name} package changed")
            if item.get("signerSha256") != results[name]["signerSha256"]:
                raise ValueError(f"Previous {name} signing certificate changed")
            if not isinstance(item.get("versionCode"), int) or \
               results[name]["versionCode"] <= item["versionCode"]:
                raise ValueError(f"{name} versionCode must increase for upgrade")
    return {"schema": "localpilot-triad-candidate-v2", "sourceCommit": sha.lower(),
            "releaseReady": False, "physicalAndroidTest": "NOT_VERIFIED",
            "updateCompatibility": "NOT_VERIFIED_ON_DEVICE",
            "signingCertificateSha256ByPackage": pins, "packages": results}


def main():
    p = argparse.ArgumentParser(description=__doc__)
    for label in PACKAGES:
        p.add_argument("--" + label.lower(), type=Path, required=True)
    p.add_argument("--aapt", required=True)
    p.add_argument("--apksigner", required=True)
    for label in PACKAGES:
        p.add_argument("--" + label.lower() + "-certificate", required=True)
    p.add_argument("--sha", required=True)
    p.add_argument("--previous", type=Path)
    p.add_argument("--output", type=Path, required=True)
    opts = p.parse_args()
    old = json.loads(opts.previous.read_text(encoding="utf-8")) if opts.previous else None
    manifest = audit({name: getattr(opts, name.lower()) for name in PACKAGES},
                     opts.aapt, opts.apksigner,
                     {name: getattr(opts, name.lower() + "_certificate") for name in PACKAGES},
                     opts.sha, old)
    opts.output.parent.mkdir(parents=True, exist_ok=True)
    opts.output.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print("TRIAD_SIGNED_CANDIDATE_PASS commit=" + manifest["sourceCommit"] +
          "; NOT a physical-device-tested stable release")


if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError, zipfile.BadZipFile, json.JSONDecodeError) as failure:
        raise SystemExit("TRIAD_RELEASE_GATE_FAIL: " + str(failure))