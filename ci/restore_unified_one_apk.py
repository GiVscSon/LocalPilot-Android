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
    "app/src/main/java/org/localpilot/android/PilotService.java",
    "app/build.gradle",
    "app/src/main/AndroidManifest.xml",
    "app/src/main/java/org/localpilot/android/MainActivity.java",
    "app/src/main/java/org/localpilot/android/TrainingActivity.java",
    "game-lab/src/main/java/org/localpilot/gamelab/TestDeviceActivity.java",
)
REQUIRED={
    "app/src/main/java/org/localpilot/android/PilotService.java":(
        b"public HarnessAndroidState harnessEnvironmentState()",
        b"public String installedGameReadiness(", b"public void stopFromHarness()",
        b"public String installPaddleTransfer(", b"HarnessWorkspaceEngine.inspect(this).nesBusy()",
    ),
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
        b"org.localpilot.gamelab.RetroLibraryActivity.class",
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
    # PilotService owns the real input gate, capture, STOP and installed-game
    # binding. An archived service is never a valid substitute for this HEAD.
    # Adaptive model classes/assets remain available from the overlay; they do
    # not require replacing this reviewed executor with a historical one.
    # The verified static NES assets come from the separate GameLab source.
    for asset in ("nes/index.html","nes/latched-buttons.js"):
        source=ROOT/"game-lab/src/main/assets"/asset
        if not source.is_file() or source.stat().st_size<20:
            raise RuntimeError(f"missing NES static asset: {source}")
    print("ONE_APK_OVERLAY_RESTORE_PASS protected="+str(len(PROTECTED))+
          " restored="+(",".join(modifications) if modifications else "none"))
    print("Reviewed PilotService, input gates and workspace APIs restored; adaptive assets/classes retained.")

if __name__=="__main__":
    try:
        restore()
    except (OSError,RuntimeError,subprocess.CalledProcessError) as failure:
        print(f"ONE_APK_OVERLAY_RESTORE_FAIL {failure}",file=sys.stderr)
        raise SystemExit(1)
