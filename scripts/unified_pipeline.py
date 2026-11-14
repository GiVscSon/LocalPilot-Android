#!/usr/bin/env python3
"""LocalPilot one-command scripted integration (offline + CI).

SIMULATED_GAME never becomes VERIFIED_REAL. Running a pipeline can test
code and an APK but CANNOT grant Android input or prove a Market win.
"""
from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_OUT = Path("build/reports/unified-script-engine")
SEEDS = (20261108, 20261201, 177777)
GAMES = 10
MODELS = 16
LAB = "build/reports/sim-autoplay"


def execute(*args: str, timeout: int = 900) -> str:
    p = subprocess.run(
        [str(arg) for arg in args], cwd=ROOT,
        stdin=subprocess.DEVNULL, capture_output=True, text=True,
        encoding="utf-8", errors="replace", timeout=timeout,
        check=False,
    )
    output = (p.stdout or "") + (p.stderr or "")
    if p.returncode:
        raise RuntimeError(
            f"Command {args[0]} failed (code {p.returncode}): {output[-3500:]}"
        )
    print(output[-2400:] if len(output) > 2400 else output)
    return output


def strict_file_bytes(path: Path, max_size: int = 1024 * 1024) -> bytes:
    if not path.is_file() or path.stat().st_size > max_size:
        raise RuntimeError(f"Missing or oversized evidence: {path}")
    return path.read_bytes()


def verify_contracts() -> dict:
    """All source-level contracts must pass before any automated build."""
    execute(sys.executable, "-m", "unittest", "discover",
            "-s", "scripts", "-p", "test_*.py", "-v")
    for test in (
        "test_soft_capture_recovery.py", "test_sim_autoplay_gate.py",
    ):
        execute(sys.executable, "-m", "unittest", "discover",
                "-s", "tests", "-p", test, "-v")
    for test in (
        "GameScriptVerify.java", "StopRecoveryVerify.java",
        "Board2048StallVerify.java", "SimExperienceVerify.java",
        "GameLabCoachVerify.java",
    ):
        execute("java", "tests/" + test)
    # For lab-only replay, a scripts path should NOT grant a new gesture issuer.
    controller = ROOT / "app/src/main/java/org/localpilot/android/ActionGate.java"
    freshness = ROOT / "app/src/main/java/org/localpilot/android/CaptureFreshness.java"
    if b"TTL_MS=700" not in strict_file_bytes(controller, 16_000):
        raise RuntimeError("ActionGate TTL invariant missing")
    if b"nowUptime-timestamp<=700" not in strict_file_bytes(freshness, 8_000):
        raise RuntimeError("CaptureFreshness invariant missing")
    return {"contract_checks": "PASS", "input_gate_replaced": False}


def validate_event_audit(report: dict) -> None:
    """A synthetic trace gate cannot attest to real frames or trusted rewards."""
    required = {
        "schema": "localpilot-bubble-event-audit-v1",
        "scope": "SYNTHETIC_CONTRACT_ONLY", "traces": 200,
        "real_transitions_audited": 0, "false_shadow_goals": 0,
        "actions_accepted_after_stop": 0, "stale_acks_accepted": 0,
        "trusted_training_rewards": 0, "pixel_benchmark": False,
        "physical_device": False, "release_ready": False,
    }
    for key, value in required.items():
        if type(report.get(key)) is not type(value) or report[key] != value:
            raise RuntimeError(f"Invalid event-audit evidence: {key}")
    if type(report.get("checks")) is not int or report["checks"] < 1600:
        raise RuntimeError("Incomplete adversarial event audit")
    if report.get("shadow_goals") != 19:
        raise RuntimeError("Positive contract coverage missing")


def event_audit(out: Path) -> dict:
    out.mkdir(parents=True, exist_ok=True)
    classes = out / "classes"
    classes.mkdir(exist_ok=True)
    # Never consume old success files after a failed compilation/run.
    for name in ("event-audit.json", "event-audit.csv", "event-audit.md"):
        (out / name).unlink(missing_ok=True)
    package = "app/src/main/java/org/localpilot/android/"
    execute("javac", "--release", "17", "-d", str(classes),
            *(package + name + ".java" for name in
              ("BubbleBoard", "BubblePolicy", "BubbleSession")),
            "tests/BubbleRewardAudit.java")
    execute("java", "-cp", str(classes),
            "org.localpilot.android.BubbleRewardAudit", str(out))
    report = json.loads(strict_file_bytes(out / "event-audit.json").decode("utf-8"))
    validate_event_audit(report)
    report["source_sha"] = execute("git", "rev-parse", "HEAD").strip()
    report["source_dirty"] = bool(execute("git", "status", "--porcelain").strip())
    report["real_replay"] = "NOT_RUN"
    report["csv_sha256"] = hashlib.sha256(
        strict_file_bytes(out / "event-audit.csv")).hexdigest()
    (out / "event-audit.json").write_text(
        json.dumps(report, indent=2) + "\n", encoding="utf-8")
    (out / "event-audit.md").write_text(
        "# Bubble event audit\n\n"
        f"Source: `{report['source_sha']}`; dirty: {report['source_dirty']}.\n\n"
        "200 synthetic adversarial traces, zero false shadow goals, zero accepted "
        "actions after STOP, zero accepted stale ACKs. All rewards quarantined.\n\n"
        "Real recorded transitions: 0. Real replay, labelled pixel benchmark and "
        "physical acceptance: NOT RUN. This is no release or gameplay proof.\n",
        encoding="utf-8")
    return report


def play_train(out: Path) -> dict:
    """Repeatable SIM-only self-play, independent baselines and regression gate."""
    out.mkdir(parents=True, exist_ok=True)
    seed_reports = []
    for seed in SEEDS:
        name = f"seed-{seed}"
        subdir = out / name
        execute("bash", "tools/run_sim_autoplay.sh",
                "--out", str(subdir),
                "--seed", str(seed),
                "--train-episodes", "8",
                "--eval-episodes", "4",
                "--steps", "10")
        report = json.loads(strict_file_bytes(
            subdir / "report.json", 80_000
        ).decode("utf-8"))
        if (report.get("source") != "SIMULATED_GAME"
                or report.get("real_game_accepted") is not False
                or report.get("weights_promoted_to_real_games") is not False
                or report.get("training_events") != 800
                or len(report.get("games", [])) != GAMES
                or report.get("seed") != seed):
            raise RuntimeError(f"Untrusted/incomplete GameLab selfplay: {seed}")
        seed_reports.append(report)
    duplicate = out / "determinism"
    execute("bash", "tools/run_sim_autoplay.sh",
            "--out", str(duplicate),
            "--seed", str(SEEDS[0]), "--train-episodes", "8",
            "--eval-episodes", "4", "--steps", "10")
    for filename in ("training-experience.lpx", "report.json"):
        ref = strict_file_bytes(out / f"seed-{SEEDS[0]}" / filename)
        replay = strict_file_bytes(duplicate / filename)
        if ref != replay:
            raise RuntimeError(f"Simulator determinism regression: {filename}")
    execute(sys.executable, "tools/gate_sim_autoplay_candidates.py", str(out))
    gate = json.loads(
        strict_file_bytes(out / "model-gate.json", 100_000).decode("utf-8")
    )
    if (gate.get("source") != "SIMULATED_GAME"
            or gate.get("may_dispatch_real_gestures") is not False
            or gate.get("may_transfer_to_market") is not False
            or len(gate.get("decisions", [])) != GAMES):
        raise RuntimeError("Model gate tries to promote simulator weights")
    decisions = {d["game"]: d["status"] for d in gate["decisions"]}
    return {"simulator_scenes": GAMES, "seeds": list(SEEDS),
            "training_actions": len(SEEDS) * 800,
            "validation": "PASS", "evaluation": "separate held-out seeds",
            "gate": decisions, "real_model_transfer": False}


def sdk_tool(name: str) -> Path | None:
    found = shutil.which(name)
    if found:
        return Path(found)
    home = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if home:
        for folder in sorted((Path(home) / "build-tools").glob("*"), reverse=True):
            target = folder / name
            if target.exists():
                return target
    return None


def verify_apk(apk: Path, require_sdk: bool) -> dict:
    """Fail closed on wrong APK, model count, NES assets or manifest routes."""
    apk = apk.resolve()
    if not apk.is_file():
        raise RuntimeError(f"Unified Android APK missing: {apk}")
    with zipfile.ZipFile(apk) as archive:
        if archive.testzip() is not None:
            raise RuntimeError("APK ZIP CRC mismatch")
        names = set(archive.namelist())
        prefix = "assets/adaptive-engine-v1/"
        models = [n for n in names if n.startswith(prefix) and n.endswith(".zip")]
        wanted = {
            "assets/nes/jsnes.min.js", "assets/nes/index.html",
            "assets/nes/latched-buttons.js",
            "assets/localpilot-game-script.lps",
        }
        missing = sorted(wanted - names)
        if missing or len(models) != MODELS:
            raise RuntimeError(
                f"Unified APK incomplete: models={len(models)} missing={missing}"
            )
        game_script = archive.read("assets/localpilot-game-script.lps")
        if not game_script.startswith(b"LPS1\n"):
            raise RuntimeError("Invalid LPS1 controller map in unified APK")
        if game_script.count(b"model|") != MODELS:
            raise RuntimeError("Wrong number of scripted game models")
        if not archive.read("assets/nes/jsnes.min.js"):
            raise RuntimeError("NES emulator runtime empty")
    result = {"apk": str(apk), "size_bytes": apk.stat().st_size,
              "sha256": hashlib.sha256(apk.read_bytes()).hexdigest(),
              "simulation_model_assets": len(models),
              "embedded_nes": True, "embedded_gamelab_manifest": False,
              "signature_verified": False, "real_game_verified": False}
    aapt = sdk_tool("aapt")
    signer = sdk_tool("apksigner")
    if require_sdk and (aapt is None or signer is None):
        raise RuntimeError("aapt and apksigner required for release candidate gate")
    if aapt is not None:
        text = execute(str(aapt), "dump", "xmltree", str(apk),
                       "AndroidManifest.xml", timeout=30)
        components = (
            "org.localpilot.gamelab.TestDeviceActivity",
            "org.localpilot.gamelab.RetroLibraryActivity",
            "org.localpilot.gamelab.NesEmulatorActivity",
        )
        if any(name not in text for name in components):
            raise RuntimeError("GameLab/NES not embedded in main APK manifest")
        result["embedded_gamelab_manifest"] = True
    if signer is not None:
        text = execute(str(signer), "verify", "--print-certs", "--verbose",
                       str(apk), timeout=40)
        if "Verified using v2 scheme (APK Signature Scheme v2): true" not in text:
            raise RuntimeError("Android APK v2 signing not verified")
        result["signature_verified"] = True
    return result


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("stage",
        choices=("verify", "event-audit", "selfplay", "apk", "all"))
    parser.add_argument("--out", default=str(DEFAULT_OUT))
    parser.add_argument("--apk", default="app/build/outputs/apk/debug/app-debug.apk")
    parser.add_argument("--require-sdk", action="store_true")
    opts = parser.parse_args()
    out = Path(opts.out)
    if not out.is_absolute():
        out = ROOT / out
    out.mkdir(parents=True, exist_ok=True)
    state_file = out / "unified-pipeline.json"
    source_sha = execute("git", "rev-parse", "HEAD").strip()
    source_dirty = bool(execute("git", "status", "--porcelain").strip())
    github_sha = os.environ.get("GITHUB_SHA", source_sha)
    if os.environ.get("GITHUB_ACTIONS") == "true" and (
            github_sha != source_sha or (source_dirty and opts.stage == "event-audit")):
        raise RuntimeError("CI event evidence requires a clean checkout of GITHUB_SHA")
    prior_stages = {}
    if state_file.exists():
        try:
            prior = json.loads(state_file.read_text(encoding="utf-8"))
            if (prior.get("schema") != "localpilot-script-orchestrator-v1"
                    or prior.get("github_sha") != github_sha
                    or prior.get("source_sha") != source_sha):
                raise RuntimeError("Old report belongs to a different source revision")
            prior_stages = prior.get("stages", {})
            if not isinstance(prior_stages, dict):
                raise RuntimeError("Bad previous pipeline report")
        except (OSError, ValueError, TypeError) as exc:
            raise RuntimeError(f"Previous integration report invalid: {exc}") from exc
    report = {
        "schema": "localpilot-script-orchestrator-v1",
        "github_sha": github_sha,
        "source_sha": source_sha, "source_dirty": source_dirty,
        "stages": prior_stages,
        "origin": "GITHUB_OR_LOCAL_SCRIPT",
        "stage": opts.stage,
        "branch_agnostic": True,
        "sim_is_not_market_reward": True,
        "no_android_input_dispatched": True,
        "automatic_release": False,
        "time_utc": dt.datetime.now(dt.timezone.utc).isoformat(),
    }
    current_stage = opts.stage
    try:
        if opts.stage in ("verify", "all"):
            current_stage = "verify"
            report["contracts"] = verify_contracts()
            report["stages"]["verify"] = {"status": "PASS", **report["contracts"]}
        if opts.stage in ("event-audit", "all"):
            current_stage = "event-audit"
            report["stages"]["event-audit"] = {
                "status": "PASS", **event_audit(out / "event-audit")}
        if opts.stage in ("selfplay", "all"):
            current_stage = "selfplay"
            report["selfplay"] = play_train(out / "selfplay")
            report["stages"]["selfplay"] = {"status": "PASS", **report["selfplay"]}
        if opts.stage in ("apk", "all"):
            current_stage = "apk"
            report["apk"] = verify_apk(
                ROOT / opts.apk if not Path(opts.apk).is_absolute()
                else Path(opts.apk), opts.require_sdk)
            report["stages"]["apk"] = {"status": "PASS", **report["apk"]}
        report["result"] = "PASS"
        return_code = 0
    except (RuntimeError, OSError, ValueError, zipfile.BadZipFile,
            subprocess.TimeoutExpired, KeyError) as exc:
        report["result"] = "FAIL"
        report["stages"][current_stage] = {"status": "FAIL", "reason": str(exc)[:400]}
        report["reason"] = str(exc)[:400]
        print(f"UNIFIED_SCRIPT_ENGINE_FAIL: {exc}", file=sys.stderr)
        return_code = 1
    report["complete"] = all(
        report["stages"].get(name, {}).get("status") == "PASS"
        for name in ("verify", "event-audit", "selfplay", "apk")
    )
    state_file.write_text(
        json.dumps(report, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    (out / f"unified-{opts.stage}.json").write_text(
        json.dumps(report, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print(f"UNIFIED_SCRIPT_ENGINE_{report['result']} "
          f"stage={opts.stage} report={out / 'unified-pipeline.json'}")
    return return_code


if __name__ == "__main__":
    raise SystemExit(main())
