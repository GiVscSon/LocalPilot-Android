#!/usr/bin/env python3
"""Fail-closed verification of the ONE release-signed LocalPilot APK candidate.

Does not declare stable release readiness and cannot publish or sign anything.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import zipfile

PACKAGE="org.localpilot.android"
ACTIVITIES=(
  "org.localpilot.android.MainActivity",
  "org.localpilot.android.ModelLabActivity",
  "org.localpilot.gamelab.TestDeviceActivity",
  "org.localpilot.gamelab.NesEmulatorActivity",
)
ASSETS={
  "AndroidManifest.xml", "classes.dex",
  "assets/nes/jsnes.min.js","assets/nes/latched-buttons.js",
  "lib/arm64-v8a/liblocalpilot_embedded.so",
}

def sdk(*args):
    proc=subprocess.run(args,capture_output=True,text=True,check=False)
    if proc.returncode:
        raise ValueError(f"Verification SDK tool {Path(args[0]).name} failed")
    return proc.stdout

def verify(apk,aapt,apksigner,pinned_cert,source_sha,previous=None):
    if not re.fullmatch(r"[0-9a-fA-F]{40}",source_sha):
        raise ValueError("Full pinned 40-digit git source SHA required")
    if not re.fullmatch(r"[0-9a-fA-F]{64}",pinned_cert):
        raise ValueError("One permanent 64-hex release certificate required")
    cert=pinned_cert.lower()
    if not apk.is_file() or apk.stat().st_size>45*1024*1024:
        raise ValueError("Missing/unexpectedly large import-only unified release APK")
    with zipfile.ZipFile(apk) as z:
        names=set(z.namelist())
        if ASSETS-names or z.testzip() is not None:
            raise ValueError("Missing unified app assets or invalid ZIP CRC")
        if any(n.startswith("assets/models/") and n.endswith(".gguf") for n in names):
            raise ValueError("On-demand GGUF weights must not be bundled")
        dex=b"".join(z.read(n) for n in names
                     if re.fullmatch(r"classes(?:[2-9][0-9]*)?\.dex",n))
        for symbol in (b"org/localpilot/android/ModelLabActivity",
                       b"org/localpilot/gamelab/NesEmulatorActivity"):
            if symbol not in dex:
                raise ValueError("Required unified screen implementation is missing")
    info=sdk(aapt,"dump","badging",str(apk))
    m=re.search(r"^package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'",info,re.M)
    if not m or m.group(1)!=PACKAGE or int(m.group(2))<=0:
        raise ValueError("Wrong release package/versionCode")
    if "dev" in m.group(3).lower() or "candidate" in m.group(3).lower():
        raise ValueError("Release versionName is not clean")
    if "sdkVersion:'30'" not in info or "targetSdkVersion:'35'" not in info:
        raise ValueError("Wrong SDK metadata")
    if re.findall(r"^launchable-activity: name='([^']+)'",info,re.M)!=[ACTIVITIES[0]]:
        raise ValueError("Expected exactly one launcher, LocalPilot")
    xml=sdk(aapt,"dump","xmltree",str(apk),"AndroidManifest.xml")
    for activity in ACTIVITIES:
        if activity not in xml:
            raise ValueError(f"Missing unified screen {activity}")
    signed=sdk(apksigner,"verify","--verbose","--print-certs",str(apk))
    actual=re.findall(r"^Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]{64})$",signed,re.M)
    if len(actual)!=1 or actual[0].lower()!=cert:
        raise ValueError("Release signing identity mismatch")
    if not re.search(r"^Verified using v[23] scheme .*: true$",signed,re.M):
        raise ValueError("No valid v2/v3 signature")
    version=int(m.group(2))
    if previous is not None:
        if previous.get("schema")!="localpilot-unified-signed-candidate-v1" or previous.get("package")!=PACKAGE:
            raise ValueError("Old signed manifest schema or package mismatch")
        if previous.get("signerSha256")!=cert:
            raise ValueError("Release certificate changed since prior version")
        old_version=previous.get("versionCode")
        if not isinstance(old_version,int) or version<=old_version:
            raise ValueError("Release upgrade versionCode is not incremented")
    h=hashlib.sha256()
    with apk.open("rb") as f:
        for block in iter(lambda:f.read(1024*1024),b""):
            h.update(block)
    return {
      "schema":"localpilot-unified-signed-candidate-v1",
      "sourceCommit":source_sha.lower(),"package":PACKAGE,
      "versionCode":version,"versionName":m.group(3),
      "signerSha256":cert,"apkSha256":h.hexdigest(),
      "apkBytes":apk.stat().st_size,
      "releaseReady":False,
      "physicalAndroidTest":"NOT_VERIFIED",
      "upgradeDataPreservation":"NOT_VERIFIED",
      "models":"ON_DEMAND_NOT_BUNDLED",
    }

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument("--apk",type=Path,required=True)
    p.add_argument("--aapt",required=True)
    p.add_argument("--apksigner",required=True)
    p.add_argument("--cert",required=True)
    p.add_argument("--source-sha",required=True)
    p.add_argument("--previous",type=Path)
    p.add_argument("--output",type=Path,required=True)
    a=p.parse_args()
    prior=json.loads(a.previous.read_text(encoding="utf-8")) if a.previous else None
    result=verify(a.apk,a.aapt,a.apksigner,a.cert,a.source_sha,prior)
    a.output.parent.mkdir(parents=True,exist_ok=True)
    a.output.write_text(json.dumps(result,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
    print("SIGNED_UNIFIED_CANDIDATE_PASS; releaseReady=false; real device not tested")

if __name__=="__main__":
    try: main()
    except (ValueError,OSError,zipfile.BadZipFile,json.JSONDecodeError) as error:
        raise SystemExit("SIGNED_UNIFIED_CANDIDATE_STOP: "+str(error))
