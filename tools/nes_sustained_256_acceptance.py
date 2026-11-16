#!/usr/bin/env python3
"""G1 real Android JSNES sustained play: 256 proven homebrew Student actions.

Runs only on an isolated AVD. Uses existing app, HarnessCoordinator, save vault,
and immutable on-policy replay, never a fake simulator or system touch agent.
Does not claim GGUF, arbitrary ROM autonomy, physical POCO or release.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import struct
import time
import zipfile

import unified_in_app_smoke as ui
from harness_student_device_acceptance import private_file
from nes_autoplay_resume_acceptance import ACTIVE, CANDIDATE, GOALS, checkpoint, wait_progress

GOAL_SCHEMA = "LOCALPILOT_HOMEBREW_GOAL_V1"
BATCH_SIZE = 16
EXPECTED_ACTIONS = 256
SHA64 = re.compile(r"[0-9a-f]{64}\Z")


def verify_sustained_goals(raw: bytes, policy_sha: str, rom_sha: str,
                           expected_steps: int = EXPECTED_ACTIONS) -> dict:
    if not SHA64.fullmatch(policy_sha) or not SHA64.fullmatch(rom_sha):
        raise ValueError("INVALID_POLICY_OR_ROM_SHA")
    if not raw or len(raw) > 131072:
        raise ValueError("JOURNAL_ABSENT_OR_OVERSIZE")
    rows = raw.decode("utf-8").splitlines()
    if len(rows) != expected_steps or expected_steps % BATCH_SIZE:
        raise ValueError("WRONG_SUSTAINED_ACTION_COUNT")
    previous_hash, previous_end, current_epoch = "0", -1, None
    total_reward = 0.0
    epochs = set()
    for index, row in enumerate(rows):
        fields = row.split("|")
        if (len(fields) != 15 or fields[0] != GOAL_SCHEMA or
            fields[2] != rom_sha or fields[4] != policy_sha or
            fields[5] not in ("LEFT", "RIGHT", "UP", "DOWN", "A", "B") or
            fields[11] != "builtin-homebrew-right-progress-v1" or
            fields[12] != "PPO_BLOCKED"):
            raise ValueError("UNTRUSTED_SUSTAINED_RECORD")
        digest = hashlib.sha256(row.rsplit("|", 1)[0].encode("utf-8")).hexdigest()
        if fields[14] != digest or fields[13] != previous_hash:
            raise ValueError("SUSTAINED_HASH_CHAIN_BROKEN")
        previous_hash = digest
        start, end, x0, x1 = (int(fields[i]) for i in (6, 7, 8, 9))
        if end - start != 16 or start < 15 or not (0 <= x0 <= 255 and 0 <= x1 <= 255):
            raise ValueError("BAD_SUSTAINED_FRAME_OR_RAM")
        if previous_end != -1 and start != previous_end:
            raise ValueError("NONCONTIGUOUS_SUSTAINED_ACTIONS")
        previous_end = end
        if current_epoch is not None and current_epoch != fields[1] and index % BATCH_SIZE:
            raise ValueError("EPOCH_CHANGED_MID_BATCH")
        current_epoch = fields[1]
        epochs.add(current_epoch)
        delta = x1 - x0
        if abs(delta) > 16 or (fields[5] == "RIGHT" and delta < 0) or (
            fields[5] == "LEFT" and delta > 0) or (
            fields[5] not in ("RIGHT", "LEFT") and delta != 0):
            raise ValueError("SUSTAINED_RAM_ACTION_CONTRADICTION")
        reward = struct.unpack(">f", bytes.fromhex(fields[10]))[0]
        if reward != delta / 16:
            raise ValueError("SUSTAINED_REWARD_NOT_FROM_ROM_RAM")
        total_reward += reward
    return {"steps": len(rows), "batches": len(rows) // BATCH_SIZE,
            "firstFrame": int(rows[0].split("|")[6]), "lastFrame": previous_end,
            "romSha256": rom_sha, "policySha256": policy_sha,
            "journalSha256": hashlib.sha256(raw).hexdigest(),
            "taskReturn": total_reward, "epochs": len(epochs),
            "rewardSource": "VERIFIED_ROM_RAM"}


def run(apk: Path, output: Path):
    if not ui.is_emulator():
        raise RuntimeError("EMULATOR_ONLY")
    api = int(ui.adb("shell", "getprop", "ro.build.version.sdk"))
    if api != 35:
        raise RuntimeError("G1_SELECTED_API35_REQUIRED")
    if not apk.is_file():
        raise RuntimeError("ONE_APK_MISSING")
    with zipfile.ZipFile(apk) as archive:
        source = json.loads(archive.read("assets/localpilot-source.json"))["sourceSha"]
    apk_sha = hashlib.sha256(apk.read_bytes()).hexdigest()
    ui.adb("install", str(apk), timeout=120)
    ui.adb("shell", "am", "start", "-W", "-n", ui.PACKAGE + "/" + ui.MAIN)
    ui.wait_activity(ui.MAIN)
    process = ui.one_process()
    navigation_retries = ui.tap_harness_and_verify()
    ui.tap_text("Автоигра Student · 256 ходов с сохранением")
    ui.wait_activity(ui.NES)
    checkpoints = []
    for minimum in (32, 128, EXPECTED_ACTIONS):
        saved = wait_progress(minimum, seconds=480)
        checkpoints.append({"atLeast": minimum, "savedActions": saved["actions"],
                            "frame": saved["frame"]})
    final = checkpoint()
    if final["actions"] != EXPECTED_ACTIONS or final["romSha256"] == "0" * 64:
        raise RuntimeError("AUTOPLAY_NOT_EXACTLY_256")
    active = private_file(ACTIVE)
    candidate = private_file(CANDIDATE)
    policy_sha = hashlib.sha256(active).hexdigest()
    candidate_sha = hashlib.sha256(candidate).hexdigest()
    if policy_sha == candidate_sha or final["policy"] != policy_sha:
        raise RuntimeError("ACTIVE_OR_CANDIDATE_POLICY_CHANGED")
    proof = verify_sustained_goals(
        private_file(GOALS, 131072), policy_sha, final["romSha256"])
    if proof["lastFrame"] > final["frame"]:
        raise RuntimeError("JOURNAL_ADVANCES_BEYOND_SAVED_FRAME")
    replay_dir = "files/localpilot_vault/episodes/onpolicy-v1"
    names = ui.adb("shell", "run-as", ui.PACKAGE, "ls", replay_dir).split()
    if len(names) != 16 or any(not re.fullmatch(r"[0-9a-f]{64}\.lpr", n) for n in names):
        raise RuntimeError("EXPECTED_16_SEALED_REPLAY_BATCHES")
    ui.tap_text("STOP", swipes=0)
    before = private_file(GOALS, 131072)
    time.sleep(2.0)
    if private_file(GOALS, 131072) != before:
        raise RuntimeError("POST_STOP_COMPLETED_ACTIONS")
    if ui.one_process() != process:
        raise RuntimeError("APP_PROCESS_RESTARTED_DURING_EPISODE")
    if hashlib.sha256(private_file(ACTIVE)).hexdigest() != policy_sha:
        raise RuntimeError("UNREVIEWED_CANDIDATE_WAS_PROMOTED")
    result = {
        "schema": "localpilot-nes-256-sustained-device-v1",
        "result": "EMULATOR_SUSTAINED_256_PASS",
        "sourceSha": source, "apkSha256": apk_sha, "api": api,
        "deviceProfile": "pixel_2", "singleProcess": True,
        "boundedNavigationRetries": navigation_retries,
        "harnessNavigation": ui.HARNESS_NAVIGATION[-8:],
        "checkpoints": checkpoints, "proof": proof,
        "immutableReplayBatches": len(names),
        "activePolicyPreserved": True, "candidateSha256": candidate_sha,
        "stopBlocksCompletedActions": True,
        "physicalDevice": False, "ggufInference": "NOT_TESTED",
        "policyImprovement": "NOT_PROVEN", "releaseReady": False
    }
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n")
    print("NES_256_REAL_ANDROID_EMULATOR_PASS " + json.dumps(result))


if __name__ == "__main__":
    p = argparse.ArgumentParser()
    p.add_argument("--apk", type=Path, required=True)
    p.add_argument("--output", type=Path, required=True)
    a = p.parse_args()
    try:
        run(a.apk, a.output)
    except Exception as error:
        a.output.parent.mkdir(parents=True, exist_ok=True)
        a.output.write_text(json.dumps({
            "schema": "localpilot-nes-256-sustained-device-v1",
            "result": "FAIL", "error": str(error)[:1200],
            "releaseReady": False}, ensure_ascii=False, indent=2) + "\n")
        try:
            if ui.is_emulator():
                ui.write_failure_diagnostics(a.output, error)
        except Exception:
            pass
        raise
