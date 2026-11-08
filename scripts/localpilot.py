#!/usr/bin/env python3
"""LocalPilot script-only operator: GitHub build, APK inspection and safe ADB checks.

All device diagnostics are read-only. This file never toggles Android settings,
starts games, authorizes input or treats Android ACK as verified game progress.
Requires Python 3.10+; CI commands additionally require GitHub CLI (gh).
"""
from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import json
from pathlib import Path
import re
import shutil
import subprocess
import sys
import zipfile

REPO = "GiVscSon/LocalPilot-Android"
BRANCH = "feature/unified-16-simulators-build-20261107"
WORKFLOW = "unified-16-simulators.yml"
ARTIFACT = "localpilot-unified-16-simulators-debug"
PACKAGE = "org.localpilot.android.dev.install1081"
ROOT = Path(__file__).resolve().parents[1]
SIGNATURE = b"PK\x03\x04"
SAFE_LOG = re.compile(
    r"localpilot|pilotservice|capturepacer|actiongate|inputpermit|"
    r"screen(?:shot|_timeout)|roi|bounds_changed|panel_covers_roi|"
    r"bubble|gridpuzzle|android_ack|verified_|stage.?metrics|stop",
    re.IGNORECASE,
)


def run(argv: list[str], *, timeout: int = 90, raw: bool = False) -> str | bytes:
    try:
        result = subprocess.run(
            argv, stdin=subprocess.DEVNULL, stdout=subprocess.PIPE,
            stderr=subprocess.PIPE, check=False, timeout=timeout,
        )
    except (OSError, subprocess.TimeoutExpired) as error:
        raise RuntimeError(f"Cannot execute {argv[0]}: {error}") from error
    if result.returncode:
        tail = result.stderr.decode("utf-8", errors="replace")[-1400:]
        raise RuntimeError(f"{argv[0]} exited with {result.returncode}: {tail}")
    return result.stdout if raw else result.stdout.decode("utf-8", errors="replace")


def gh(*args: str) -> str:
    if shutil.which("gh") is None:
        raise RuntimeError("GitHub CLI missing: install gh and run 'gh auth login'")
    return str(run(["gh", *args], timeout=180))


def latest_success(runs: list[dict]) -> dict | None:
    for item in runs:
        if item.get("conclusion") == "success" and item.get("status") == "completed":
            return item
    return None


def list_runs() -> list[dict]:
    raw = gh(
        "run", "list", "--repo", REPO, "--workflow", WORKFLOW,
        "--branch", BRANCH, "--limit", "20",
        "--json", "databaseId,headSha,status,conclusion,createdAt,url",
    )
    runs = json.loads(raw)
    return sorted(runs, key=lambda item: item["createdAt"], reverse=True)


def ci_start(_: argparse.Namespace) -> None:
    print(gh("workflow", "run", WORKFLOW, "--repo", REPO, "--ref", BRANCH).strip())
    print("Build requested. The workflow verifies sources, tests, APK assets and signing.")


def ci_status(_: argparse.Namespace) -> None:
    runs = list_runs()
    if not runs:
        print("No unified workflow runs found")
        return
    for item in runs[:5]:
        print(
            f"{item['databaseId']} {item['status']:12s} "
            f"{str(item.get('conclusion')):10s} {item['headSha'][:12]} "
            f"{item.get('url','')}"
        )


def ci_download(args: argparse.Namespace) -> None:
    latest = latest_success(list_runs())
    if latest is None:
        raise RuntimeError("No successful unified workflow run; refusing a base-only APK")
    target = Path(args.output).expanduser().resolve()
    target.mkdir(parents=True, exist_ok=True)
    run_id = str(latest["databaseId"])
    gh("run", "download", run_id, "--repo", REPO, "--name", ARTIFACT, "--dir", str(target))
    print(f"Unified candidate downloaded from GitHub run {run_id} ({latest['headSha'][:12]})")
    for apk in sorted(target.rglob("*.apk")):
        inspect_apk(apk, require_models=16 if apk.name == "app-debug.apk" else None)


def inspect_apk(apk: Path, require_models: int | None = None) -> dict:
    if not apk.is_file():
        raise RuntimeError(f"APK not found: {apk}")
    if apk.read_bytes()[:4] != SIGNATURE:
        raise RuntimeError("Not an Android ZIP/APK")
    with zipfile.ZipFile(apk) as archive:
        broken = archive.testzip()
        if broken is not None:
            raise RuntimeError(f"APK ZIP CRC failed: {broken}")
        assets = [name for name in archive.namelist()
                  if name.startswith("assets/adaptive-engine-v1/") and name.endswith(".zip")]
    if require_models is not None and len(assets) != require_models:
        raise RuntimeError(
            f"Unified candidate requires {require_models} model assets, found {len(assets)}"
        )
    result = {
        "path": str(apk), "size_bytes": apk.stat().st_size,
        "sha256": hashlib.sha256(apk.read_bytes()).hexdigest(),
        "model_assets": len(assets),
        "signature_verified": False,
    }
    signer = shutil.which("apksigner")
    if signer:
        output = str(run([signer, "verify", "--verbose", "--print-certs", str(apk)]))
        if "Verified using v2 scheme (APK Signature Scheme v2): true" not in output:
            raise RuntimeError("APK signature v2 not verified")
        result["signature_verified"] = True
        match = re.search(r"Signer #1 certificate SHA-256 digest: ([0-9a-f]+)", output)
        if match:
            result["certificate_sha256"] = match.group(1)
    print(json.dumps(result, ensure_ascii=False, indent=2))
    if not signer:
        print("Note: local apksigner not installed; inspect CI signature verification.", file=sys.stderr)
    return result


def cmd_inspect(args: argparse.Namespace) -> None:
    inspect_apk(Path(args.apk).expanduser().resolve(), require_models=args.models)


def adb(serial: str, *arguments: str, timeout: int = 40, raw: bool = False) -> str | bytes:
    if not serial or re.fullmatch(r"[A-Za-z0-9.:-]{3,128}", serial) is None:
        raise RuntimeError("Supply exact, safe ADB serial using --serial")
    return run(["adb", "-s", serial, *arguments], timeout=timeout, raw=raw)


def safe_read(serial: str, *arguments: str) -> str:
    try:
        return str(adb(serial, *arguments))
    except RuntimeError as error:
        return f"UNAVAILABLE: {error}"


def cmd_device_report(args: argparse.Namespace) -> None:
    serial = args.serial
    device = str(adb(serial, "get-state")).strip()
    if device != "device":
        raise RuntimeError(f"ADB device unavailable: {device}")
    timestamp = dt.datetime.now(dt.timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    destination = Path(args.output).expanduser().resolve() / timestamp
    destination.mkdir(parents=True, exist_ok=False)
    pid = safe_read(serial, "shell", "pidof", PACKAGE).strip()
    data = {
        "schema": "localpilot-device-diagnostic-v1",
        "time_utc": timestamp, "device_serial": serial,
        "package": PACKAGE, "pid": pid,
        "wm_size": safe_read(serial, "shell", "wm", "size").strip(),
        "wm_density": safe_read(serial, "shell", "wm", "density").strip(),
        "rotation_enabled": safe_read(serial, "shell", "settings", "get", "system", "accelerometer_rotation").strip(),
        "orientation": safe_read(serial, "shell", "settings", "get", "system", "user_rotation").strip(),
        "capture_proven": False,
        "gesture_proven": False,
        "android_ack_is_gameplay_proof": False,
    }
    (destination / "device.json").write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    window = safe_read(serial, "shell", "dumpsys", "window")
    picked = [line for line in window.splitlines() if any(
        term in line for term in ("mCurrentFocus=", "mRotation=", "mUserRotationMode=", "mGlobalConfiguration=")
    )]
    (destination / "window.txt").write_text("\n".join(picked[:20]) + "\n", encoding="utf-8")
    accessibility = safe_read(serial, "shell", "dumpsys", "accessibility")
    picked = [line for line in accessibility.splitlines()
              if PACKAGE in line or "Enabled services:" in line or "Bound services:" in line]
    (destination / "accessibility.txt").write_text("\n".join(picked[:35]) + "\n", encoding="utf-8")
    if pid.isdigit():
        log = safe_read(serial, "logcat", "-d", "--pid", pid, "-v", "time")
        relevant = [line for line in log.splitlines() if SAFE_LOG.search(line)]
        (destination / "pilot-logcat.txt").write_text("\n".join(relevant[-300:]) + "\n", encoding="utf-8")
    else:
        (destination / "pilot-logcat.txt").write_text(
            "No single LocalPilot process PID. No logcat evidence collected.\n", encoding="utf-8"
        )
    if args.screenshot:
        raw = adb(serial, "exec-out", "screencap", "-p", timeout=30, raw=True)
        if not isinstance(raw, bytes) or not raw.startswith(b"\x89PNG\r\n\x1a\n"):
            raise RuntimeError("ADB screencap returned invalid PNG")
        (destination / "screen.png").write_bytes(raw)
        print("Warning: screen.png may contain personal information. Review before sharing.")
    print(f"Read-only LocalPilot report: {destination}")
    print("No gestures, settings changes, game claims or automatic upload performed.")


def cmd_install(args: argparse.Namespace) -> None:
    if not args.yes:
        raise RuntimeError("Installation requires explicit --yes; never silently replace the app")
    apk = Path(args.apk).expanduser().resolve()
    inspect_apk(apk, require_models=16)
    print("This is an explicit debug APK install; existing user data will NOT be cleared.")
    print(str(adb(args.serial, "install", "-r", str(apk), timeout=150)).strip())
    print("A signature mismatch is not automatically repaired by uninstalling the app.")


def parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(description=__doc__)
    sub = p.add_subparsers(dest="action", required=True)
    for name, fn in (("ci-start", ci_start), ("ci-status", ci_status)):
        sub.add_parser(name).set_defaults(func=fn)
    down = sub.add_parser("ci-download", help="Download ONLY last successful unified workflow APK")
    down.add_argument("--output", default="downloads/unified")
    down.set_defaults(func=ci_download)
    insp = sub.add_parser("inspect", help="Check APK CRC, SHA256, model count and optional signature")
    insp.add_argument("--apk", required=True)
    insp.add_argument("--models", type=int, default=16)
    insp.set_defaults(func=cmd_inspect)
    report = sub.add_parser("device-report", help="Read-only Android diagnostics; no controls are touched")
    report.add_argument("--serial", required=True)
    report.add_argument("--output", default="device-reports")
    report.add_argument("--screenshot", action="store_true", help="Opt in to capturing visible private screen")
    report.set_defaults(func=cmd_device_report)
    install = sub.add_parser("install", help="Explicit ADB APK install without uninstall/pm clear")
    install.add_argument("--serial", required=True)
    install.add_argument("--apk", required=True)
    install.add_argument("--yes", action="store_true")
    install.set_defaults(func=cmd_install)
    return p


if __name__ == "__main__":
    try:
        options = parser().parse_args()
        options.func(options)
    except (RuntimeError, OSError, ValueError, zipfile.BadZipFile, json.JSONDecodeError) as exc:
        raise SystemExit(f"LocalPilot script stopped safely: {exc}")
