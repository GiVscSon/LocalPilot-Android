#!/usr/bin/env python3
"""Twenty independent fail-closed binary/source checks for the unified APK.

PASS here means packaging/integrity only. It does NOT mean verified Market
gameplay, an Android emulator pass, or a stable/public release.
"""
from __future__ import annotations
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
from zipfile import BadZipFile, ZipFile

from verify_adaptive_apk import EXPECTED, check_model_zip

ROOT = Path(__file__).resolve().parents[1]
MODELS = "assets/adaptive-engine-v1/"
SHA = "assets/localpilot-build.json"
SCRIPT = "assets/localpilot-game-script.lps"
ROUTES = {0,1,2,3,4,5,6,7,9,10,11}
COMPONENTS = {
    "pilot_service": b"Lorg/localpilot/android/PilotService;",
    "model_library": b"Lorg/localpilot/android/ModelLibraryActivity;",
    "training": b"Lorg/localpilot/android/TrainingActivity;",
    "gamelab": b"Lorg/localpilot/gamelab/TestDeviceActivity;",
    "nes_activity": b"Lorg/localpilot/gamelab/NesEmulatorActivity;",
    "action_gate": b"Lorg/localpilot/android/ActionGate;",
    "input_permit": b"Lorg/localpilot/android/InputPermit;",
}

def digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()

def audit(apk: Path) -> dict:
    records = []
    def check(name: str, predicate: bool, detail: str = ""):
        records.append({"check":len(records)+1, "name":name,
                        "status":"PASS" if predicate else "FAIL", "detail":detail})
    try:
        sha = subprocess.check_output(["git","rev-parse","HEAD"],cwd=ROOT,text=True).strip()
    except (OSError,subprocess.CalledProcessError):
        sha = ""
    ci = os.environ.get("GITHUB_SHA",sha)
    with ZipFile(apk) as z:
        names=z.namelist()
        dex=b"\x00".join(z.read(n) for n in names if n.endswith(".dex") and n.startswith("classes"))
        model_names={n[len(MODELS):] for n in names if n.startswith(MODELS) and n.endswith(".zip")}
        script=z.read(SCRIPT) if SCRIPT in names else b""
        lines=script.decode("utf-8","replace").splitlines()
        route_lines=[line for line in lines if line.startswith("route|")]
        models=[line for line in lines if line.startswith("model|")]
        def model_valid(name,game):
            try:
                check_model_zip(z.read(MODELS+name),name,game)
                return True
            except (KeyError,ValueError,BadZipFile,TypeError):
                return False
        def same_models():
            return all((ROOT/"app/src/main/assets/adaptive-engine-v1"/name).read_bytes()==z.read(MODELS+name)
                       for name in EXPECTED)
        metadata={}
        if SHA in names:
            try:metadata=json.loads(z.read(SHA))
            except (ValueError,UnicodeDecodeError):pass
        expected_static=["nes/index.html","nes/jsnes.min.js","nes/latched-buttons.js","nes/JSNES-LICENSE.txt"]
        def asset_same(name):
            target=ROOT/"game-lab/src/main/assets"/name
            if not target.exists():
                target=ROOT/"game-lab/build/generated/jsnes/assets"/name
            return target.is_file() and ("assets/"+name in names) and target.read_bytes()==z.read("assets/"+name)
        check("01 APK ZIP CRC",z.testzip() is None)
        check("02 ZIP entries unique",len(names)==len(set(names)))
        check("03 no .bak/.tmp inside APK",not any(n.endswith((".bak",".tmp")) for n in names))
        check("04 exactly 16 bundled model archives",model_names==set(EXPECTED),f"count={len(model_names)}")
        check("05 all model manifests, hashes and scopes valid",all(model_valid(n,g) for n,g in EXPECTED.items()))
        try: model_parity=same_models()
        except (OSError,KeyError): model_parity=False
        check("06 model archives equal reviewed source",model_parity)
        check("07 LPS1 script header",bool(lines) and lines[0]=="LPS1")
        src=ROOT/"app/src/main/assets/localpilot-game-script.lps"
        check("08 APK LPS1 equals source",src.is_file() and script==src.read_bytes())
        ids=set()
        try:ids={int(line.split("|")[1]) for line in route_lines}
        except (ValueError,IndexError):pass
        check("09 all 11 external controller routes",ids==ROUTES and len(route_lines)==len(ROUTES))
        check("10 16 lab scripts remain SIM_ONLY",len(models)==16 and all(line.endswith("|SIM_ONLY") for line in models))
        check("11 HEXON cannot silently route real gestures",not any(line.startswith("route|12|") for line in route_lines))
        check("12 KOTOBASHNYA cannot silently route real gestures",not any(line.startswith("route|13|") for line in route_lines))
        check("13 NES HTML present and source-equal",asset_same(expected_static[0]))
        check("14 pinned JSNES core present",asset_same(expected_static[1]))
        check("15 NES button latch source-equal",asset_same(expected_static[2]))
        check("16 bundled JSNES license",asset_same(expected_static[3]))
        check("17 actual PilotService DEX",COMPONENTS["pilot_service"] in dex)
        check("18 model and training activities in DEX",
              all(COMPONENTS[k] in dex for k in ("model_library","training")))
        check("19 GameLab, NES, action gates in DEX",
              all(COMPONENTS[k] in dex for k in ("gamelab","nes_activity","action_gate","input_permit")))
        check("20 source commit provenance bound into binary",
              metadata.get("schema")=="localpilot-build-provenance-v1"
              and len(str(metadata.get("source_sha256","")))==64
              and sha==ci==metadata.get("git_sha"),
              "sha="+str(metadata.get("git_sha",""))[:12])
    return {"schema":"localpilot-release-20point-v1","apk":str(apk),
            "apk_sha256":digest(apk.read_bytes()),
            "source_sha":sha,"passed":sum(r["status"]=="PASS" for r in records),
            "checks":records,"packaging_ready":all(r["status"]=="PASS" for r in records),
            "real_market_gameplay_verified":False,
            "public_release_ready":False,
            "note":"This artifact checks source-to-binary integrity only; separate emulator and physical field acceptance is required."}

def main() -> int:
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk",type=Path)
    parser.add_argument("--output",type=Path)
    args=parser.parse_args()
    try: result=audit(args.apk)
    except (OSError,ValueError,KeyError,BadZipFile,UnicodeDecodeError) as exc:
        print("RELEASE_20POINT_REJECTED: "+str(exc),file=sys.stderr)
        return 1
    output=json.dumps(result,ensure_ascii=False,indent=2)+"\n"
    if args.output:
        args.output.parent.mkdir(parents=True,exist_ok=True)
        args.output.write_text(output,encoding="utf-8")
    else: print(output,end="")
    print(f"RELEASE_20POINT_CHECKS {result['passed']}/20 "+("PASS" if result["packaging_ready"] else "FAIL"))
    return 0 if result["packaging_ready"] else 1

if __name__=="__main__":
    raise SystemExit(main())
