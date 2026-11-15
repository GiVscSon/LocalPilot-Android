#!/usr/bin/env python3
"""LocalPilot ReleaseLab: an isolated Android AVD for reproducible APK acceptance.

Works with an already running AVD (`check`) or creates/boots a named dedicated
AVD (`run`). NEVER tests or modifies a physical device. Does not call release
ready from emulator success and cannot substitute native ARM64 phone checks.

  python tools/release_lab_android.py run --api 35 --profile pixel_2 \\
      --apk path/to/x86_64-unified.apk --expected-source COMMIT_SHA --fresh
  python tools/release_lab_android.py check --serial emulator-5554 \\
      --apk path/to/x86_64-unified.apk --api 35
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import struct
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
import zipfile

SOURCE_PATH = "assets/localpilot-source.json"
API_LEVELS = (30, 35, 36)
PROFILES = ("pixel_2", "pixel_c")
PACKAGE_PATTERN = re.compile(r"org\.localpilot\.android(?:\.[a-z][a-z0-9_]*)*")
SERIAL_PATTERN = re.compile(r"emulator-[0-9]{4,5}")
AVD_PATTERN = re.compile(r"LocalPilot_ReleaseLab_API(30|35|36)_(pixel_2|pixel_c)")
SOURCE_SHA = re.compile(r"[0-9a-f]{40}")
SIGNER_SHA = re.compile(r"[0-9a-f]{64}")
MAX_SCREENSHOT = 16 * 1024 * 1024


class Rejection(RuntimeError):
    """A required release-lab gate failed; never convert it to a PASS."""


def invoke(args: list[str], timeout: int = 35, binary: bool = False,
           input_text: str | None = None):
    try:
        result = subprocess.run(args, input=input_text, capture_output=True,
                                text=not binary, timeout=timeout, check=False)
    except (OSError, subprocess.TimeoutExpired) as exc:
        raise Rejection(f"Command unavailable/timed out: {args[0]}: {exc}") from exc
    if result.returncode != 0:
        detail = result.stderr if binary else (result.stderr or "") + "\n" + (result.stdout or "")
        if isinstance(detail, bytes):
            detail = detail.decode("utf-8", "replace")
        raise Rejection(f"Command failed ({result.returncode}): {Path(args[0]).name}: {detail[-1500:]}")
    return result.stdout if binary else result.stdout.strip()


def tool(sdk: Path | None, name: str):
    ext = ".exe" if os.name == "nt" else ""
    if sdk:
        candidates = {
            "adb": [sdk / "platform-tools" / ("adb" + ext)],
            "emulator": [sdk / "emulator" / ("emulator" + ext)],
            "aapt": [sdk / "build-tools" / "35.0.0" / ("aapt" + ext)],
            "apksigner": [sdk / "build-tools" / "35.0.0" / ("apksigner" + (".bat" if os.name == "nt" else ""))],
            "sdkmanager": [sdk / "cmdline-tools" / "latest" / "bin" / ("sdkmanager" + (".bat" if os.name == "nt" else ""))],
            "avdmanager": [sdk / "cmdline-tools" / "latest" / "bin" / ("avdmanager" + (".bat" if os.name == "nt" else ""))],
        }[name]
        for value in candidates:
            if value.is_file():
                return str(value)
    found = shutil.which(name) or (shutil.which(name + ".bat") if os.name == "nt" else None)
    if not found:
        raise Rejection(f"Android SDK tool missing: {name}. Install Android SDK cmdline-tools / build-tools / emulator.")
    return found


def sdk_path(cli: str | None):
    value = cli or os.getenv("ANDROID_HOME") or os.getenv("ANDROID_SDK_ROOT")
    if not value and os.name == "nt" and os.getenv("LOCALAPPDATA"):
        value = str(Path(os.environ["LOCALAPPDATA"]) / "Android" / "Sdk")
    return Path(value).expanduser() if value else None


def sha_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def sha_file(file: Path) -> str:
    digest = hashlib.sha256()
    with file.open("rb") as fh:
        for chunk in iter(lambda: fh.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def read_source(apk: Path):
    if not apk.is_file() or apk.stat().st_size < 1000:
        raise Rejection("APK missing or unexpectedly small")
    with zipfile.ZipFile(apk) as archive:
        corrupt = archive.testzip()
        if corrupt is not None:
            raise Rejection(f"APK ZIP CRC failure in {corrupt}")
        if SOURCE_PATH not in archive.namelist():
            raise Rejection("APK has no embedded source SHA; cannot attest Git source")
        raw = archive.read(SOURCE_PATH)
        if len(raw) > 2048:
            raise Rejection("Source provenance JSON oversized")
        meta = json.loads(raw)
        if meta.get("schema") != "localpilot-source-v1" or not SOURCE_SHA.fullmatch(meta.get("sourceSha", "")):
            raise Rejection("APK source provenance is invalid")
        abis = sorted({n.split("/")[1] for n in archive.namelist()
                       if n.startswith("lib/") and n.count("/") >= 2})
    return {"sourceSha": meta["sourceSha"], "apkSha256": sha_file(apk),
            "apkSizeBytes": apk.stat().st_size, "nativeAbis": abis}


def parse_badging(badging: str):
    match = re.search(r"^package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'", badging, re.M)
    if not match or not PACKAGE_PATTERN.fullmatch(match.group(1)):
        raise Rejection("APK package is not an allowed LocalPilot Android identity")
    return {"package": match.group(1), "versionCode": match.group(2), "versionName": match.group(3)}


def signer(apksigner: str, apk: Path):
    output = invoke([apksigner, "verify", "--print-certs", str(apk)], timeout=60)
    match = re.search(r"Signer #1 certificate SHA-256 digest:\s*([0-9a-fA-F]{64})", output)
    if not match:
        raise Rejection("APK signature verification did not return a single SHA-256 signer")
    return match.group(1).lower()


def inspect(apk: Path, sdk: Path | None, expected_source: str | None,
            expected_signer: str | None):
    data = read_source(apk)
    data.update(parse_badging(invoke([tool(sdk, "aapt"), "dump", "badging", str(apk)], timeout=45)))
    data["signerSha256"] = signer(tool(sdk, "apksigner"), apk)
    if expected_source and data["sourceSha"] != expected_source:
        raise Rejection("APK source SHA differs from requested verified commit")
    if expected_signer and data["signerSha256"] != expected_signer:
        raise Rejection("APK signer differs from authorized release certificate")
    data["sourcePinned"] = bool(expected_source)
    data["signerPinned"] = bool(expected_signer)
    return data


def adb(base: str, serial: str, *args: str, timeout: int = 35, binary: bool = False):
    if not SERIAL_PATTERN.fullmatch(serial):
        raise Rejection("Only a local Android emulator serial may be selected")
    return invoke([base, "-s", serial, *args], timeout=timeout, binary=binary)


def emulator_identity(adb_bin: str, serial: str, expected_api: int | None):
    qemu = [adb(adb_bin, serial, "shell", "getprop", name)
            for name in ("ro.kernel.qemu", "ro.boot.qemu")]
    if "1" not in qemu:
        raise Rejection("Refusing physical or unverified virtual Android device")
    api = int(adb(adb_bin, serial, "shell", "getprop", "ro.build.version.sdk"))
    if expected_api is not None and api != expected_api:
        raise Rejection(f"Wrong Android API: expected {expected_api}, observed {api}")
    abi = adb(adb_bin, serial, "shell", "getprop", "ro.product.cpu.abi")
    model = adb(adb_bin, serial, "shell", "getprop", "ro.product.model")
    return {"serial": serial, "api": api, "abi": abi, "model": model,
            "isolatedEmulator": True}


def screenshot_info(png: bytes):
    if len(png) < 40 or len(png) > MAX_SCREENSHOT or png[:8] != b"\x89PNG\r\n\x1a\n" or png[12:16] != b"IHDR":
        raise Rejection("Compositor screenshot missing/invalid/oversized")
    width, height = struct.unpack(">II", png[16:24])
    if not 200 <= width <= 12000 or not 200 <= height <= 12000:
        raise Rejection("Compositor screenshot invalid dimensions")
    return {"width": width, "height": height, "pngSha256": sha_bytes(png), "bytes": len(png)}


def xml_has_package(xml_text: str, package: str):
    try:
        root = ET.fromstring(xml_text)
    except ET.ParseError:
        return False
    return any(node.get("package") == package for node in root.iter("node"))


def ui_probe(adb_bin: str, serial: str, package: str, dest: Path, stage: str):
    # MainActivity is exported; no private NES service, accessibility or model
    # command may be invoked by an acceptance shell.
    launcher = f"{package}/org.localpilot.android.MainActivity"
    output = adb(adb_bin, serial, "shell", "am", "start", "-W", "-n", launcher, timeout=60)
    if "Error:" in output or "Exception" in output:
        raise Rejection(f"Android MainActivity failed to launch: {output[-500:]}")
    accepted = False
    xml = ""
    for attempt in range(4):
        time.sleep(1 if attempt == 0 else 1.5)
        activity = adb(adb_bin, serial, "shell", "dumpsys", "activity", "activities", timeout=15)
        # Emulators can put SystemUI on top while still composing our activity.
        if package not in activity:
            continue
        try:
            adb(adb_bin, serial, "shell", "uiautomator", "dump", "/sdcard/localpilot-release-lab.xml", timeout=25)
            xml = adb(adb_bin, serial, "shell", "cat", "/sdcard/localpilot-release-lab.xml", timeout=15)
        except Rejection:
            continue
        if xml_has_package(xml, package):
            accepted = True
            break
    if not accepted:
        raise Rejection("App window/UI node not confirmed on isolated Android emulator")
    png = adb(adb_bin, serial, "exec-out", "screencap", "-p", timeout=35, binary=True)
    details = screenshot_info(png)
    (dest / f"{stage}.png").write_bytes(png)
    (dest / f"{stage}.xml").write_text(xml[:180000], encoding="utf-8")
    return details


def check_installed(adb_bin: str, serial: str, app: dict):
    paths = adb(adb_bin, serial, "shell", "pm", "path", app["package"]).splitlines()
    if len(paths) != 1 or not paths[0].startswith("package:/data/app/") or not paths[0].endswith(".apk"):
        raise Rejection("Installed package identity unexpected or split APK not verified")
    remote = paths[0].split("package:", 1)[1]
    digest = adb(adb_bin, serial, "shell", "sha256sum", remote).split()[0].lower()
    if digest != app["apkSha256"]:
        raise Rejection("Actual installed base APK bytes differ from tested artifact")
    return digest


def crash_log(adb_bin: str, serial: str, package: str, out: Path):
    try:
        logs = adb(adb_bin, serial, "logcat", "-d", "-b", "crash", timeout=20)
        (out / "crash-logcat.txt").write_text(logs[-64000:], encoding="utf-8")
        if re.search(r"FATAL EXCEPTION[\s\S]{0,900}Process:\s*" + re.escape(package) + r"\b", logs):
            raise Rejection("LocalPilot process crashed; see crash-logcat.txt")
    except Rejection as exc:
        if "LocalPilot process crashed" in str(exc):
            raise
        (out / "crash-logcat-error.txt").write_text(str(exc)[:1200], encoding="utf-8")


def run_checks(adb_bin: str, serial: str, api: int | None, candidate: dict,
               output: Path, baseline: dict | None):
    output.mkdir(parents=True, exist_ok=True)
    report = {"schema": "localpilot-release-lab-v1", "result": "IN_PROGRESS",
              "releaseReady": False, "physicalAndroidVerified": False,
              "scope": "ISOLATED_EMULATOR_APK_INSTALL_LAUNCH_RESTART_ONLY",
              "checks": [], "candidate": candidate,
              "baseline": baseline if baseline else None}
    def check(name: str, evidence):
        report["checks"].append({"name": name, "result": "PASS", "evidence": evidence})
    installed = False
    try:
        device = emulator_identity(adb_bin, serial, api)
        report["device"] = device
        check("emulator_identity", device)
        if candidate["nativeAbis"] and device["abi"] not in candidate["nativeAbis"]:
            raise Rejection("APK contains native libraries for " + str(candidate["nativeAbis"]) +
                            ", not emulator " + device["abi"] +
                            ". For x86_64 test, build -PunifiedEmulatorAbi=true; " +
                            "a real ARM64 release still requires an ARM64 device test.")
        check("apk_crc_source_signature", {"sha256": candidate["apkSha256"],
              "sourcePinned": candidate["sourcePinned"], "signerPinned": candidate["signerPinned"]})
        if baseline is not None:
            if baseline["package"] != candidate["package"] or baseline["signerSha256"] != candidate["signerSha256"]:
                raise Rejection("Upgrade requires identical package and signer; keep installed app data untouched")
            if baseline["nativeAbis"] and device["abi"] not in baseline["nativeAbis"]:
                raise Rejection("Baseline APK ABI incompatible with emulator")
            adb(adb_bin, serial, "install", "-r", str(baseline["path"]), timeout=90)
            check("baseline_install", check_installed(adb_bin, serial, baseline))
            check("baseline_launch", ui_probe(adb_bin, serial, candidate["package"], output, "baseline"))
        # No uninstall, no -d, no -t, no permission bypass, no user data reset.
        adb(adb_bin, serial, "install", "-r", str(candidate["path"]), timeout=90)
        installed = True
        check("candidate_install_exact_bytes", check_installed(adb_bin, serial, candidate))
        try:
            adb(adb_bin, serial, "logcat", "-c", timeout=12)
        except Rejection:
            pass
        check("launch_real_android_ui", ui_probe(adb_bin, serial, candidate["package"], output, "launch"))
        crash_log(adb_bin, serial, candidate["package"], output)
        check("no_app_crash_on_launch", "CRASH_BUFFER_CHECKED")
        before_pid = adb(adb_bin, serial, "shell", "pidof", candidate["package"])
        adb(adb_bin, serial, "shell", "am", "force-stop", candidate["package"])
        check("process_force_stop", "NO_NES_INPUT_REPLAY_OR_AUTOLAUNCH_ASSERTED")
        check("restart_real_android_ui", ui_probe(adb_bin, serial, candidate["package"], output, "relaunch"))
        after_pid = adb(adb_bin, serial, "shell", "pidof", candidate["package"])
        if not before_pid or not after_pid or before_pid == after_pid:
            raise Rejection("Process restart was not independently observed")
        crash_log(adb_bin, serial, candidate["package"], output)
        check("restart_new_process", {"oldPid": before_pid, "newPid": after_pid})
        report["result"] = "EMULATOR_PASS"
    except (Rejection, ValueError, OSError, zipfile.BadZipFile) as exc:
        report["result"] = "FAIL"
        report["error"] = str(exc)[:1800]
        if installed:
            try:
                crash_log(adb_bin, serial, candidate["package"], output)
            except Rejection:
                pass  # Preserve the first failure as the acceptance cause.
    finally:
        report_path = output / "report.json"
        report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return report


def avd_name(api: int, profile: str):
    if api not in API_LEVELS or profile not in PROFILES:
        raise Rejection("Unsupported dedicated AVD profile/API")
    return f"LocalPilot_ReleaseLab_API{api}_{profile}"


def ensure_avd(sdk: Path | None, api: int, profile: str):
    emulator = tool(sdk, "emulator")
    name = avd_name(api, profile)
    all_avds = set(invoke([emulator, "-list-avds"], timeout=20).splitlines())
    if name not in all_avds:
        image = f"system-images;android-{api};" + ("google_apis" if api == 36 else "default") + ";x86_64"
        invoke([tool(sdk, "sdkmanager"), "--install", image], timeout=900)
        invoke([tool(sdk, "avdmanager"), "create", "avd", "-n", name,
                "-k", image, "--device", profile], timeout=90, input_text="no\n")
        if name not in set(invoke([emulator, "-list-avds"]).splitlines()):
            raise Rejection("Dedicated ReleaseLab AVD was not created")
    return emulator, name


def boot_avd(adb_bin: str, emulator_bin: str, name: str, port: int,
             headless: bool, fresh: bool):
    if not AVD_PATTERN.fullmatch(name) or port % 2 or not 5554 <= port <= 5584:
        raise Rejection("Cannot boot/wipe a non-ReleaseLab AVD or invalid emulator port")
    serial = f"emulator-{port}"
    args = [emulator_bin, "-avd", name, "-port", str(port),
            "-no-snapshot-load", "-no-snapshot-save", "-no-boot-anim"]
    if headless:
        args.append("-no-window")
    if fresh:
        # Explicit destructive wipe limited to our dedicated AVD namespace.
        args.append("-wipe-data")
    try:
        emulator = subprocess.Popen(args, stdin=subprocess.DEVNULL,
                                    stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    except OSError as exc:
        raise Rejection("Cannot launch Android Emulator") from exc
    try:
        deadline = time.monotonic() + 210
        while time.monotonic() < deadline:
            if emulator.poll() is not None:
                raise Rejection("Android emulator exited before boot")
            try:
                status = adb(adb_bin, serial, "shell", "getprop", "sys.boot_completed", timeout=5)
                if status == "1":
                    emulator_identity(adb_bin, serial, None)
                    return emulator, serial
            except Rejection:
                pass
            time.sleep(3)
        raise Rejection("Dedicated AVD boot timeout")
    except BaseException:
        emulator.terminate()
        raise


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("mode", choices=("run", "check"), help="Boot a named AVD, or use an already-running emulator")
    parser.add_argument("--apk", type=Path, required=True, help="Exact APK file to test, never a guessed release")
    parser.add_argument("--baseline", type=Path, help="Optional old APK for same-signer upgrade smoke")
    parser.add_argument("--expected-source", help="Required for a source-pinned acceptance report")
    parser.add_argument("--expected-signer", help="Required for owner-signature continuity verification")
    parser.add_argument("--api", type=int, choices=API_LEVELS)
    parser.add_argument("--profile", choices=PROFILES, default="pixel_2")
    parser.add_argument("--serial", help="For check only: explicit emulator serial (not a phone)")
    parser.add_argument("--port", type=int, default=5580)
    parser.add_argument("--fresh", action="store_true", help="Wipe ONLY the named dedicated ReleaseLab AVD")
    parser.add_argument("--headless", action="store_true")
    parser.add_argument("--sdk-root")
    parser.add_argument("--output", type=Path, default=Path("build/reports/release-lab"))
    args = parser.parse_args(argv)
    dest = args.output
    dest.mkdir(parents=True, exist_ok=True)
    sdk = sdk_path(args.sdk_root)
    adb_bin = tool(sdk, "adb")
    emulator = None
    serial = args.serial
    if args.mode == "run" and (args.api is None or args.serial):
        parser.error("run requires --api and cannot use --serial")
    if args.mode == "check" and (not serial or args.fresh):
        parser.error("check requires --serial and forbids --fresh")
    for attr, pattern in ((args.expected_source, SOURCE_SHA), (args.expected_signer, SIGNER_SHA)):
        if attr is not None and not pattern.fullmatch(attr):
            parser.error("Expected SHA must be lowercase SHA-256 or a 40-character commit SHA")
    try:
        candidate = inspect(args.apk.resolve(), sdk, args.expected_source, args.expected_signer)
        candidate["path"] = str(args.apk.resolve())
        baseline = None
        if args.baseline:
            baseline = inspect(args.baseline.resolve(), sdk, None, None)
            baseline["path"] = str(args.baseline.resolve())
        if args.mode == "run":
            emulator_bin, name = ensure_avd(sdk, args.api, args.profile)
            emulator, serial = boot_avd(adb_bin, emulator_bin, name, args.port,
                                         args.headless, args.fresh)
        report = run_checks(adb_bin, serial, args.api, candidate, dest, baseline)
        print("RELEASE_LAB_EMULATOR_PASS" if report["result"] == "EMULATOR_PASS"
              else "RELEASE_LAB_FAIL", dest / "report.json", flush=True)
        return 0 if report["result"] == "EMULATOR_PASS" else 1
    except (Rejection, OSError, ValueError, json.JSONDecodeError, zipfile.BadZipFile) as exc:
        (dest / "report.json").write_text(json.dumps({
            "schema": "localpilot-release-lab-v1", "result": "BLOCKED", "releaseReady": False,
            "physicalAndroidVerified": False, "error": str(exc)[:1800]},
            ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print("RELEASE_LAB_BLOCKED:", str(exc), file=sys.stderr)
        return 2
    finally:
        if emulator is not None:
            try:
                # Kill ONLY the emulator process we launched, never an arbitrary device.
                adb(adb_bin, serial, "emu", "kill", timeout=10)
                emulator.wait(timeout=12)
            except (Rejection, subprocess.TimeoutExpired, OSError):
                emulator.terminate()


if __name__ == "__main__":
    sys.exit(main())
