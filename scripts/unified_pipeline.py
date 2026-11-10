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
import shlex
import shutil
import subprocess
import sys
import time
import zipfile

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_OUT = Path("build/reports/unified-script-engine")
SEEDS = (20261108, 20261201, 177777)
GAMES = 10
MODELS = 16
LAB = "build/reports/sim-autoplay"
BUILT_APK = Path("app/build/outputs/apk/debug/app-debug.apk")


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


def source_revision() -> tuple[str, str]:
    """Bind local and CI evidence to the actual checkout and tracked contents."""
    head = subprocess.check_output(
        ["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip()
    ci_sha = os.environ.get("GITHUB_SHA")
    if ci_sha and ci_sha != head:
        raise RuntimeError("GITHUB_SHA differs from checked-out HEAD")
    paths = subprocess.check_output(
        ["git", "ls-files", "-z"], cwd=ROOT).split(b"\0")
    digest = hashlib.sha256()
    for raw in sorted(filter(None, paths)):
        path = ROOT / os.fsdecode(raw)
        digest.update(raw + b"\0")
        digest.update(hashlib.sha256(path.read_bytes()).digest()
                      if path.is_file() else b"MISSING")
    return head, digest.hexdigest()


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
        "test_sim_seed_split.py",
    ):
        execute(sys.executable, "-m", "unittest", "discover",
                "-s", "tests", "-p", test, "-v")
    for test in (
        "GameScriptVerify.java", "StopRecoveryVerify.java",
        "Board2048StallVerify.java", "SimExperienceVerify.java",
        "GameLabCoachVerify.java", "ModelLibraryVerify.java",
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


def browser_shadow(out: Path) -> dict:
    """Optional public-site evaluation; never substitutes for Android acceptance."""
    out.mkdir(parents=True, exist_ok=True)
    classes = out / "classes"
    classes.mkdir(exist_ok=True)
    execute("javac", "--release", "17", "-sourcepath", "app/src/main/java",
            "-d", str(classes), "tools/ShadowFrameEvaluate.java")
    execute("node", "tools/browser_shadow_2048.cjs", str(out), str(classes), timeout=240)
    report = json.loads(strict_file_bytes(out / "report.json", 100_000))
    if (report.get("android_verified") is not False
            or report.get("model_transfer") is not False
            or len(report.get("results", [])) != 2):
        raise RuntimeError("Invalid browser shadow provenance")
    for site in report["results"]:
        steps = site.get("steps", [])
        if (site.get("result") != "PASS" or len(steps) != 13
                or any(step.get("recognition_mismatches") != 0
                       or step.get("may_dispatch") is not False
                       or step.get("may_promote") is not False for step in steps)
                or any(step.get("transition_verified") is not True for step in steps[1:])):
            raise RuntimeError("Browser shadow evaluation incomplete or rejected")
    return {"source": "BROWSER_GAME", "sites": report["results"],
            "android_verified": False, "model_transfer": False}


def jvm_regression_commands() -> list[list[str]]:
    """Reuse the checked-in Android regression list, rather than invent a second suite."""
    workflow = strict_file_bytes(ROOT / ".github/workflows/android.yml", 80_000).decode("utf-8")
    start = workflow.index("   - name: JVM regressions")
    end = workflow.index("   - name: Explicit unit tests and Android lint", start)
    commands = [shlex.split(line.strip()) for line in workflow[start:end].splitlines()
                if line.strip().startswith("java ")]
    if not commands:
        raise RuntimeError("Android workflow declares no JVM regression commands")
    # These are the existing unified-model integration suites, not generated tests.
    commands.extend(["java", f"tests/{name}Verify.java"] for name in (
        "TenGames", "RetroConsole", "AdaptiveEngine", "Unified081Integration"))
    return [list(command) for command in dict.fromkeys(tuple(c) for c in commands)]


def evidenced_command(command: list[str], log: Path, timeout: int = 900) -> dict:
    """Retain full build/test output and the exit status, including failures."""
    started = time.monotonic()
    result = {"command": command, "status": "FAIL", "exit_code": None,
              "log": str(log)}
    try:
        process = subprocess.run(command, cwd=ROOT, stdin=subprocess.DEVNULL,
                                 capture_output=True, text=True, encoding="utf-8",
                                 errors="replace", timeout=timeout, check=False)
        output = (process.stdout or "") + (process.stderr or "")
        result["exit_code"] = process.returncode
        result["status"] = "PASS" if process.returncode == 0 else "FAIL"
    except (OSError, subprocess.TimeoutExpired) as error:
        def decoded(value: str | bytes | None) -> str:
            return value.decode("utf-8", errors="replace") if isinstance(value, bytes) else value or ""
        output = (decoded(getattr(error, "stdout", None)) +
                  decoded(getattr(error, "stderr", None)) + "\n" + str(error))
    log.parent.mkdir(parents=True, exist_ok=True)
    log.write_text(output, encoding="utf-8")
    result["log_sha256"] = hashlib.sha256(log.read_bytes()).hexdigest()
    result["seconds"] = round(time.monotonic() - started, 3)
    print(f"{result['status']} {' '.join(command)} log={log}")
    return result


def build_test(out: Path) -> dict:
    """Actually build and test one checkout. Device/physical acceptance is separate."""
    out.mkdir(parents=True, exist_ok=True)
    revision = source_revision()
    evidence = {"github_sha": revision[0], "source_sha256": revision[1],
                "status": "FAIL", "commands": [], "real_game_verified": False,
                "device_acceptance": "NOT_RUN", "gradle_unit_tests": "UNDETERMINED"}
    manifest = out / "build-test.json"
    try:
        commands = [[sys.executable, "-m", "unittest", "discover", "-s", "tests", "-p", "test_*.py", "-v"]]
        commands.extend(jvm_regression_commands())
        commands.append(["bash", "./gradlew", "--no-daemon", ":app:testDebugUnitTest",
                         ":app:lintDebug", ":app:assembleDebug",
                         ":app:assembleDebugAndroidTest", ":game-lab:assembleDebug"])
        classes = out / "nes-classes"
        classes.mkdir(exist_ok=True)
        rom = out / "homebrew.nes"
        core = ROOT / "game-lab/build/generated/jsnes/assets/nes/jsnes.min.js"
        commands.extend([
            ["node", "tests/nes_controller_framehold.cjs"],
            ["javac", "--release", "17", "-d", str(classes),
             "game-lab/src/main/java/org/localpilot/gamelab/HomebrewNes.java",
             "tests/ExportHomebrewNes.java"],
            ["java", "-cp", str(classes), "ExportHomebrewNes", str(rom)],
            ["node", "tests/jsnes_nes_smoke.cjs", str(core), str(rom)],
            ["node", "tests/nes_observation_smoke.cjs", str(core), str(rom)],
            ["node", "tests/nes_record_contract.cjs", str(core), str(rom)],
            ["node", "tests/nes_training_contract.cjs", str(core), str(rom)],
            ["node", "tests/nes_lab_cli_contract.cjs", str(core), str(rom)],
            ["node", "tools/nes-agent/train.cjs", "--core", str(core),
             "--rom", str(rom), "--output", str(out / "nes-trained"),
             "--episodes", "160"],
            ["node", "tools/nes-agent/record.cjs", "--core", str(core),
             "--rom", str(rom), "--output", str(out / "nes-ppu-dataset.jsonl"),
             "--steps", "24"],
        ])
        for index, command in enumerate(commands, 1):
            entry = evidenced_command(command, out / f"{index:03d}.log", timeout=1800)
            evidence["commands"].append(entry)
            if entry["status"] != "PASS":
                raise RuntimeError(f"Build/test failed: {' '.join(command)}; see {entry['log']}")
            if "./gradlew" in command:
                log = Path(entry["log"]).read_text(encoding="utf-8")
                receipt = next((line for line in log.splitlines()
                                if ":app:testDebugUnitTest" in line), "")
                if not receipt:
                    raise RuntimeError("Gradle did not report the requested unit-test task")
                evidence["gradle_unit_tests"] = next(
                    (label.replace("-", "_") for label in
                     ("NO-SOURCE", "UP-TO-DATE", "FROM-CACHE", "SKIPPED") if label in receipt),
                    "EXECUTED")
            if source_revision() != revision:
                raise RuntimeError("Build/test modified source; evidence rejected")
        apk = ROOT / BUILT_APK
        raw = strict_file_bytes(apk, 64 * 1024 * 1024)
        evidence["built_apk"] = {"path": str(apk), "sha256": hashlib.sha256(raw).hexdigest(),
                                 "size_bytes": len(raw)}
        evidence["status"] = "PASS"
        return {"build_test_manifest": str(manifest), "commands_passed": len(commands),
                "built_apk": evidence["built_apk"],
                "gradle_unit_tests": evidence["gradle_unit_tests"],
                "device_acceptance": "NOT_RUN", "real_game_verified": False}
    except (RuntimeError, OSError, ValueError, KeyError) as error:
        evidence["reason"] = str(error)
        raise
    finally:
        manifest.write_text(json.dumps(evidence, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


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
            "assets/nes/latched-buttons.js", "assets/nes/nes-observation.js",
            "assets/localpilot-game-script.lps",
            "assets/localpilot-build.json",
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
        if archive.getinfo("assets/localpilot-build.json").file_size > 4096:
            raise RuntimeError("APK provenance asset is oversized")
        metadata = json.loads(archive.read("assets/localpilot-build.json"))
        head, fingerprint = source_revision()
        if (not isinstance(metadata, dict)
                or metadata.get("schema") != "localpilot-build-provenance-v1"
                or metadata.get("git_sha") != head
                or metadata.get("source_sha256") != fingerprint):
            raise RuntimeError("APK embedded provenance differs from the checked-out source")
    result = {"apk": str(apk), "size_bytes": apk.stat().st_size,
              "sha256": hashlib.sha256(apk.read_bytes()).hexdigest(),
              "simulation_model_assets": len(models),
              "embedded_nes": True, "embedded_gamelab_manifest": False,
              "signature_verified": False, "real_game_verified": False,
              "embedded_source_sha": metadata["git_sha"],
              "embedded_source_sha256": metadata["source_sha256"]}
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
        choices=("verify", "selfplay", "shadow", "build-test", "apk", "all"))
    parser.add_argument("--out", default=str(DEFAULT_OUT))
    parser.add_argument("--apk", default="app/build/outputs/apk/debug/app-debug.apk")
    parser.add_argument("--require-sdk", action="store_true")
    opts = parser.parse_args()
    out = Path(opts.out)
    if not out.is_absolute():
        out = ROOT / out
    out.mkdir(parents=True, exist_ok=True)
    state_file = out / "unified-pipeline.json"
    github_sha, source_sha256 = source_revision()
    prior_stages = {}
    if state_file.exists():
        try:
            prior = json.loads(state_file.read_text(encoding="utf-8"))
            if (prior.get("schema") != "localpilot-script-orchestrator-v1"
                    or prior.get("github_sha") != github_sha
                    or prior.get("source_sha256") != source_sha256):
                raise RuntimeError("Old report belongs to a different source revision")
            prior_stages = prior.get("stages", {})
            if not isinstance(prior_stages, dict):
                raise RuntimeError("Bad previous pipeline report")
        except (OSError, ValueError, TypeError) as exc:
            raise RuntimeError(f"Previous integration report invalid: {exc}") from exc
    report = {
        "schema": "localpilot-script-orchestrator-v1",
        "github_sha": github_sha,
        "source_sha256": source_sha256,
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
        if opts.stage in ("selfplay", "all"):
            current_stage = "selfplay"
            report["selfplay"] = play_train(out / "selfplay")
            report["stages"]["selfplay"] = {"status": "PASS", **report["selfplay"]}
        if opts.stage == "shadow":
            current_stage = "shadow"
            report["shadow"] = browser_shadow(out / "browser-shadow")
            report["stages"]["shadow"] = {"status": "PASS", **report["shadow"]}
        if opts.stage in ("build-test", "all"):
            current_stage = "build-test"
            # A fresh build invalidates an older APK receipt, even when the
            # source revision is unchanged (signing/configuration may differ).
            report["stages"].pop("apk", None)
            report["build_test"] = build_test(out / "build-test")
            report["stages"]["build-test"] = {"status": "PASS", **report["build_test"]}
        if opts.stage in ("apk", "all"):
            current_stage = "apk"
            report["apk"] = verify_apk(
                ROOT / opts.apk if not Path(opts.apk).is_absolute()
                else Path(opts.apk), opts.require_sdk)
            built = report["stages"].get("build-test", {})
            if (built.get("status") == "PASS" and
                    report["apk"]["sha256"] != built["built_apk"]["sha256"]):
                raise RuntimeError("APK differs from the artifact produced by build-test")
            report["stages"]["apk"] = {"status": "PASS", **report["apk"]}
        if source_revision() != (github_sha, source_sha256):
            raise RuntimeError("Source changed during pipeline; evidence rejected")
        report["result"] = "PASS"
        return_code = 0
    except (RuntimeError, OSError, ValueError, zipfile.BadZipFile,
            subprocess.TimeoutExpired, KeyError) as exc:
        report["result"] = "FAIL"
        report["stages"][current_stage] = {"status": "FAIL", "reason": str(exc)[:400]}
        report["reason"] = str(exc)[:400]
        print(f"UNIFIED_SCRIPT_ENGINE_FAIL: {exc}", file=sys.stderr)
        return_code = 1
    report["release_ready"] = False  # Device acceptance is a separate mandatory gate.
    report["complete"] = all(
        report["stages"].get(name, {}).get("status") == "PASS"
        for name in ("verify", "selfplay", "build-test", "apk")
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
