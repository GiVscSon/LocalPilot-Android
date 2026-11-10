#!/usr/bin/env python3
"""Restore reviewed ONE-APK sources after legacy 16-model CI overlay.

The overlay is needed for adaptive model packaging. However it may contain
older app Gradle/manifest/UI files, silently losing embedded GameLab and NES.
Only these explicitly owned files are restored from the checked-out git HEAD.
Other overlay changes (adaptive models/compat) stay intact.

This is a BUILD preparation step, not runtime model promotion or a release.
"""
from __future__ import annotations

import pathlib
import subprocess
import sys

ROOT=pathlib.Path(__file__).resolve().parents[1]
PROTECTED=(
    "app/build.gradle",
    "app/src/main/AndroidManifest.xml",
    "app/src/main/java/org/localpilot/android/MainActivity.java",
    "app/src/main/java/org/localpilot/android/TrainingActivity.java",
    "game-lab/src/main/java/org/localpilot/gamelab/TestDeviceActivity.java",
)
REQUIRED={
    "app/build.gradle":(
        b"../game-lab/src/main/java", b"../game-lab/src/main/assets",
        b"../game-lab/build/generated/jsnes/assets",
        b"bundleVerifiedNesEngine",
    ),
    "app/src/main/AndroidManifest.xml":(
        b"org.localpilot.gamelab.TestDeviceActivity",
        b"org.localpilot.gamelab.RetroLibraryActivity",
        b"org.localpilot.gamelab.NesEmulatorActivity",
    ),
    "app/src/main/java/org/localpilot/android/MainActivity.java":(
        b"org.localpilot.gamelab.TestDeviceActivity.class",
        b"private void openExternalGameLab()",
    ),
    "app/src/main/java/org/localpilot/android/TrainingActivity.java":(
        b"SimLabPolicy", b"gamelab-sim-experience-v1.lpx",
    ),
    "game-lab/src/main/java/org/localpilot/gamelab/TestDeviceActivity.java":(
        b"saveEmbeddedExperience()", b"SimExperience.Step",
    ),
}

def restore() -> None:
    modifications=[]
    for name in PROTECTED:
        source=subprocess.run(
            ["git","show",f"HEAD:{name}"],cwd=ROOT,check=True,
            stdout=subprocess.PIPE,stderr=subprocess.PIPE).stdout
        if not source:
            raise RuntimeError(f"empty reviewed Git source: {name}")
        for snippet in REQUIRED[name]:
            if snippet not in source:
                raise RuntimeError(f"missing required ONE-APK contract in {name}: {snippet!r}")
        target=ROOT / name
        if target.read_bytes()!=source:
            target.write_bytes(source)
            modifications.append(name)
    # Legacy overlay service can replace a newer PilotService but keep a
    # TrainingActivity that calls its opt-in paddle-transfer API. Add back
    # ONLY the reviewed, permission-checked API method, not the whole service:
    # wholesale restoration would discard the adaptive-engine integration.
    service_name="app/src/main/java/org/localpilot/android/PilotService.java"
    reviewed=subprocess.run(
        ["git","show",f"HEAD:{service_name}"],cwd=ROOT,check=True,
        stdout=subprocess.PIPE,stderr=subprocess.PIPE).stdout.decode("utf-8")
    target=ROOT / service_name
    installed=target.read_text(encoding="utf-8")
    method=' public String installPaddleTransfer(PaddleTransfer.Candidate candidate){'
    if method not in installed:
        start=reviewed.index(method)
        end=reviewed.index(" private void installRealGameNeural(",start)
        restored=reviewed[start:end]
        # The legacy service has no 'visionTraining' state at all. The current
        # service still checks it, but the older one cannot refer to a field
        # that does not exist. Preserve the pre-existing gate/active-game/busy
        # guards and omit only this inapplicable legacy flag.
        if "visionTraining" not in installed:
            if restored.count("||visionTraining")!=1:
                raise RuntimeError("Unexpected transfer safety guard layout")
            restored=restored.replace("||visionTraining","",1)
        required=(
            "candidate.eligible()", "gate.armed()", "busy",
            "VisualGameTarget.allowed(", "arcadeLearning.savePaddleTransfer(",
        )
        if not all(flag in restored for flag in required):
            raise RuntimeError("reviewed paddle bridge lost its safety invariants")
        marker=" private final StageMetrics metrics=new StageMetrics();"
        if installed.count(marker)!=1:
            raise RuntimeError("legacy PilotService has unknown integration layout")
        installed=installed.replace(marker,restored+marker,1)
        target.write_text(installed,encoding="utf-8")
        modifications.append(service_name+"[safe-paddle-method]")
    if installed.count(method)!=1:
        raise RuntimeError("PilotService paddle bridge must be declared exactly once")
    # The verified static NES assets come from the separate GameLab source.
    for asset in ("nes/index.html","nes/latched-buttons.js"):
        source=ROOT/"game-lab/src/main/assets"/asset
        if not source.is_file() or source.stat().st_size<20:
            raise RuntimeError(f"missing NES static asset: {source}")
    print("ONE_APK_OVERLAY_RESTORE_PASS protected="+str(len(PROTECTED))+
          " restored="+(",".join(modifications) if modifications else "none"))
    print("Adaptive controller and input gates kept; only reviewed safety-checked paddle API may be restored.")

if __name__=="__main__":
    try:
        restore()
    except (OSError,RuntimeError,subprocess.CalledProcessError) as failure:
        print(f"ONE_APK_OVERLAY_RESTORE_FAIL {failure}",file=sys.stderr)
        raise SystemExit(1)
