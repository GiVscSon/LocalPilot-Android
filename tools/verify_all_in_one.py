#!/usr/bin/env python3
"""Audit a SINGLE LocalPilot unified test APK; never infer stable release readiness.

Only parses aapt/apksigner and ZIP data. Does not install, sign, release or
interact with an Android device. The manifest records test-only provenance.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import zipfile

EXPECTED_PACKAGE = "org.localpilot.android.dev.unified"
REQUIRED_ACTIVITIES = (
    "org.localpilot.android.MainActivity",
    "org.localpilot.android.ModelLabActivity",
    "org.localpilot.gamelab.TestDeviceActivity",
    "org.localpilot.gamelab.NesEmulatorActivity",
)
REQUIRED_ENTRIES = {
    "AndroidManifest.xml", "classes.dex",
    "assets/nes/jsnes.min.js", "assets/nes/latched-buttons.js",
    "lib/arm64-v8a/liblocalpilot_embedded.so",
}

def command(*argv):
    run = subprocess.run(argv, text=True, capture_output=True, check=False)
    if run.returncode != 0:
        raise ValueError(f"Android SDK tool failed: {Path(argv[0]).name} exit {run.returncode}")
    return run.stdout

def validate(apk: Path, aapt: str, apksigner: str):
    if not apk.is_file():
        raise ValueError("Unified APK is missing")
    if apk.stat().st_size > 45 * 1024 * 1024:
        raise ValueError("Compact import-only unified APK is unexpectedly large")
    with zipfile.ZipFile(apk) as archive:
        names = set(archive.namelist())
        missing = REQUIRED_ENTRIES - names
        if missing:
            raise ValueError("Missing runtime or NES assets: " + ", ".join(sorted(missing)))
        if archive.testzip() is not None:
            raise ValueError("APK ZIP CRC check failed")
        if any(n.startswith("assets/models/") and n.endswith(".gguf") for n in names):
            raise ValueError("Model weights must be downloaded by user, never prebundled")
        dex = b"".join(archive.read(n) for n in sorted(names)
                       if re.fullmatch(r"classes(?:[2-9][0-9]*)?\\.dex", n))
        if b"org/localpilot/android/ModelLabActivity" not in dex:
            raise ValueError("ModelLabActivity bytecode absent from APK")
        if b"org/localpilot/gamelab/TestDeviceActivity" not in dex:
            raise ValueError("GameLab bytecode absent from APK")
    badging = command(aapt, "dump", "badging", str(apk))
    match = re.search(r"^package: name='([^']+)' versionCode='(\\d+)' versionName='([^']+)'",badging,re.M)
    if not match or match.group(1) != EXPECTED_PACKAGE:
        raise ValueError("Unexpected package identity: expected isolated unified test suffix")
    if "-unified-test-only" not in match.group(3):
        raise ValueError("Debug build is not visibly marked test-only")
    if "sdkVersion:'30'" not in badging or "targetSdkVersion:'35'" not in badging:
        raise ValueError("Unexpected Android SDK compatibility")
    launchers = re.findall(r"^launchable-activity: name='([^']+)'",badging,re.M)
    if launchers != ["org.localpilot.android.MainActivity"]:
        raise ValueError("Expected exactly one LocalPilot launcher activity")
    manifest_tree = command(aapt, "dump", "xmltree", str(apk), "AndroidManifest.xml")
    for activity in REQUIRED_ACTIVITIES:
        if activity not in manifest_tree:
            raise ValueError(f"Unified manifest is missing {activity}")
    signed = command(apksigner,"verify","--verbose","--print-certs",str(apk))
    certs = re.findall(r"^Signer #\\d+ certificate SHA-256 digest: ([0-9a-fA-F]{64})$",signed,re.M)
    if len(certs)!=1 or not re.search(r"^Verified using v[23] scheme .*: true$",signed,re.M):
        raise ValueError("APK signature verification failed")
    sha = hashlib.sha256(apk.read_bytes()).hexdigest()
    return {
        "schema": "localpilot-all-in-one-test-v1",
        "source": "GitHub Actions candidate",
        "package": EXPECTED_PACKAGE,
        "versionCode": int(match.group(2)),
        "versionName": match.group(3),
        "sha256": sha,
        "bytes": apk.stat().st_size,
        "debugSignerSha256": certs[0].lower(),
        "contains": {"LocalPilot": True, "GameLabNES": True,
                     "ModelLabGGUFImport": True, "NativeCpuRuntime": True},
        "signedRelease": False,
        "releaseReady": False,
        "physicalAndroidTest": "NOT_VERIFIED",
        "upgradeDataPreservation": "NOT_VERIFIED",
    }

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument("--apk",type=Path,required=True)
    p.add_argument("--aapt",required=True)
    p.add_argument("--apksigner",required=True)
    p.add_argument("--output",type=Path,required=True)
    a=p.parse_args()
    result=validate(a.apk,a.aapt,a.apksigner)
    a.output.parent.mkdir(parents=True,exist_ok=True)
    a.output.write_text(json.dumps(result,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
    print("UNIFIED_APK_TEST_ONLY_PASS sha256="+result["sha256"]+
          "; not physical-device validated, not a stable release")

if __name__=="__main__":
    try: main()
    except (ValueError,OSError,zipfile.BadZipFile) as exc:
        raise SystemExit("UNIFIED_APK_STOP: "+str(exc))
