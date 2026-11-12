#!/usr/bin/env python3
"""Fail-closed source integration audit; never claims device or model quality."""
from __future__ import annotations
import argparse
import json
from pathlib import Path
import re
import subprocess
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]

def source(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")

def inspect() -> dict:
    app = source("app/build.gradle")
    root = source("build.gradle")
    wrapper = source("gradle/wrapper/gradle-wrapper.properties")
    main = source("app/src/main/java/org/localpilot/android/MainActivity.java")
    manifest = source("app/src/main/AndroidManifest.xml")
    lab = source("model-lab/src/main/java/org/localpilot/android/ModelLabActivity.java")
    model_runtime = source("app/src/main/java/org/localpilot/android/EmbeddedGgufRuntime.java")
    model_store = source("model-lab/src/main/java/org/localpilot/android/ModelLabStore.java")
    nes = source("game-lab/src/main/java/org/localpilot/gamelab/NesEmulatorActivity.java")
    html = source("game-lab/src/main/assets/nes/index.html")
    emulator_build = source("game-lab/build.gradle")
    gate = source("game-lab/src/main/java/org/localpilot/gamelab/ConsoleAgentGate.java")
    contract = source("shared/src/main/java/org/localpilot/shared/ConsoleAgentContract.java")
    memory = source("model-lab/src/main/java/org/localpilot/android/ConsoleEpisodeContext.java")
    training = source("app/src/main/java/org/localpilot/android/TrainingActivity.java")
    retro = source("game-lab/src/main/java/org/localpilot/gamelab/RetroEmulatorCatalog.java")
    smoke = source("tools/unified_in_app_smoke.py")
    # Android <queries> may mention LAUNCHER but does not create a launcher.
    # Verify actual activity intent-filters instead of counting the text.
    root_xml=ET.fromstring(manifest)
    android_name="{http://schemas.android.com/apk/res/android}name"
    launcher_activities=[]
    for activity in root_xml.findall("./application/activity"):
        for intent in activity.findall("intent-filter"):
            actions={a.get(android_name) for a in intent.findall("action")}
            cats={a.get(android_name) for a in intent.findall("category")}
            if "android.intent.action.MAIN" in actions and "android.intent.category.LAUNCHER" in cats:
                launcher_activities.append(activity.get(android_name))
    checks = {
      "one_app_sources": ("../game-lab/src/main/java" in app and
          "generated/source/unified-model-lab" in app and
          "exclude '**/EmbeddedGgufRuntime.java'" in app),
      "one_launcher_internal_modules": (
          launcher_activities == [".MainActivity"] and
          '<activity android:name=".ModelLabActivity" android:exported="false"' in manifest and
          '<activity android:name="org.localpilot.gamelab.NesEmulatorActivity" android:exported="false"' in manifest),
      "console_navigation": ("new Intent(this,org.localpilot.gamelab.RetroLibraryActivity.class)" in main and
          '"org.localpilot.gamelab.RetroLibraryActivity"' in lab and
          "org.localpilot.gamelab.RetroLibraryActivity" in smoke),
      "real_nes_core_bundled": ("bundleVerifiedNesEngine" in emulator_build and
          "jsnesVersion = '2.1.0'" in emulator_build and
          "machine.loadROM(binary)" in html and "nes.frame()" in html),
      "model_catalog_validates_gguf": (
          "ModelLabStore" in lab and
          "modelSha" in model_store and "fileSha(" in model_store and
          "store.modelFile(selection)" in lab),
      "model_reaches_real_nes_controller": (
          "engine.proposeNesButton(" in lab and
          "public void proposeNesButton(" in model_runtime and
          "ConsoleAgentContract.parseButton(answer)" in lab and
          "aiGate.consume(" in nes and
          "window.retroAiTrial(" in nes and
          "window.retroAiTrial=function(" in html),
      "stop_epoch_ttl_and_whitelist": (
          "ConsoleAgentGate" in nes and
          "ConsoleAgentContract.TTL_MS" in gate and
          "pending=null" in gate and
          "frameEpoch.current(aiEpoch)" in nes and
          'homebrewRom' in html and 'HOLD_FRAMES=16' in contract and
          "ConsoleAgentContract.playable(button)" in nes),
      "visual_verification_not_ack": (
          "completeTrialOnFreshFrame(count,pixelsHash)" in nes and
          "current.downAck" in nes and "current.upAck" in nes and
          "NesTrial.create(" in nes and
          '"reward","UNKNOWN"' in nes),
      "experience_reused_for_same_model": (
          "ConsoleEpisodeContext.read(" in lab and
          'selection.id,selection.modelSha' in lab and
          "modelId.equals(row.optString(\"modelId\"))" in memory and
          "modelSha.equals(row.optString(\"modelSha256\"))" in memory and
          "instruction+\" \"+priorEvidence" in lab),
      "no_android_input_from_model": (
          'setClassName(getPackageName(),"org.localpilot.gamelab.NesEmulatorActivity")' in lab and
          "dispatchGesture(" not in model_runtime and
          "dispatchGesture(" not in lab),
    }
    old_training = 'gamelab-sim-experience-v1.lpx' in training and 'console-ai-episodes.jsonl' not in training
    agp = re.search(r"com\.android\.application' version '([^']+)'", root)
    gradle = re.search(r"/gradle-([0-9.]+)-bin\.zip", wrapper)
    jsnes = re.search(r"jsnesVersion = '([^']+)'", emulator_build)
    result = {
      "schema":"localpilot-console-integration-audit-v1",
      "source_sha": subprocess.check_output(["git","rev-parse","HEAD"],cwd=ROOT,text=True).strip(),
      "source_contracts":checks,
      "source_contracts_pass":all(checks.values()),
      "dependencies":{
        "android_gradle_plugin":agp.group(1) if agp else "UNKNOWN",
        "gradle":gradle.group(1) if gradle else "UNKNOWN",
        "jsnes":jsnes.group(1) if jsnes else "UNKNOWN",
        "llama_cpp":"pinned git revision; inspect workflows before updating"
      },
      "capabilities":{
        "nes_emulator":"SOURCE_AND_CI_TESTED",
        "local_gguf_inference":"SOURCE_AND_CI_TESTED_NOT_PHONE_TESTED",
        "one_step_gguf_nes_agent":"SOURCE_AND_CI_TESTED_NOT_PHONE_TESTED",
        "episode_memory":"IN_CONTEXT_REUSE_NOT_WEIGHT_TRAINING" if checks["experience_reused_for_same_model"] else "MISSING",
        "nes_training_weights":"NOT_IMPLEMENTED",
        "legacy_arcade_training":"PRESENT_NOT_CONNECTED_TO_NES" if old_training else "UNKNOWN",
        "unified_vulkan":"NOT_COMPILED" if "buildConfigField 'boolean', 'VULKAN_COMPILED', 'false'" in app else "REQUIRES_RUNTIME_VERIFICATION",
        "other_consoles":"DOS_FILE_PREFLIGHT_ONLY" if 'DOS("MS-DOS"' in retro else "UNKNOWN",
      },
      "physical_device_verified":False,
      "llm_gameplay_verified":False,
      "stable_release_ready":False,
    }
    return result

def main() -> int:
    parser=argparse.ArgumentParser()
    parser.add_argument("--out",type=Path)
    args=parser.parse_args()
    report=inspect()
    if args.out:
        args.out.parent.mkdir(parents=True,exist_ok=True)
        args.out.write_text(json.dumps(report,indent=2,ensure_ascii=False)+"\n",encoding="utf-8")
    print(json.dumps(report,indent=2,ensure_ascii=False))
    return 0 if report["source_contracts_pass"] else 1

if __name__=="__main__":
    raise SystemExit(main())
