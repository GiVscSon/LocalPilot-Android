#!/usr/bin/env python3
"""Owner-local, fail-closed preparation of ONE release-signed LocalPilot candidate.

Use ONLY an EXISTING permanent PKCS12 key on a trusted computer. This does
not create/change signing identities, publish APKs, install anything, or
declare a stable release. In particular, it works before workflow_dispatch
can be registered on the repository's default branch.
"""
from __future__ import annotations

import argparse
import getpass
import hashlib
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile

REPO = Path(__file__).resolve().parents[1]
LLAMA_SHA = "2b66bfbf7107e43f5cd2fe99802ff6e872e4c4d0"
CERT = re.compile(r"[0-9a-fA-F]{64}\Z")
SHA = re.compile(r"[0-9a-fA-F]{40}\Z")


def run(args: list[str], *, cwd: Path = REPO, env: dict | None = None) -> str:
    proc = subprocess.run(args, cwd=cwd, env=env, text=True,
                          capture_output=True, check=False)
    if proc.returncode:
        # No secret values are passed as argv; passwords reach apksigner via env.
        label = Path(args[0]).name
        raise RuntimeError(f"{label} exited {proc.returncode}: "
                           + (proc.stderr or proc.stdout)[-900:])
    return proc.stdout.strip()


def source_check(expected: str) -> None:
    if not SHA.fullmatch(expected):
        raise ValueError("Full 40-hex source SHA required")
    actual = run(["git", "rev-parse", "HEAD"]).lower()
    if actual != expected.lower():
        raise ValueError("Working checkout does not match pinned source SHA")
    if run(["git", "status", "--porcelain", "--untracked-files=no"]):
        raise ValueError("Tracked source modifications: commit/review first")


def tool(sdk: Path, name: str) -> str:
    base = sdk / "build-tools" / "35.0.0"
    suffix = (".bat" if name == "apksigner" else ".exe") if os.name == "nt" else ""
    path = base / (name + suffix)
    if not path.is_file():
        raise ValueError(f"Missing pinned Android build tool: {path}")
    return str(path)


def ensure_llama() -> None:
    path = REPO / "third_party" / "llama.cpp"
    if not (path / ".git").exists():
        if path.exists() and any(path.iterdir()):
            raise ValueError("Unreviewed third_party/llama.cpp content")
        path.mkdir(parents=True, exist_ok=True)
        run(["git", "init"], cwd=path)
        run(["git", "remote", "add", "origin",
             "https://github.com/ggml-org/llama.cpp.git"], cwd=path)
        run(["git", "fetch", "--depth", "1", "origin", LLAMA_SHA], cwd=path)
        run(["git", "checkout", "--detach", "FETCH_HEAD"], cwd=path)
    if run(["git", "rev-parse", "HEAD"], cwd=path) != LLAMA_SHA:
        raise ValueError("llama.cpp checkout differs from reviewed revision")


def local_candidate(keystore: Path, alias: str, pinned_cert: str,
                    source_sha: str, sdk: Path, output: Path,
                    previous: Path | None = None) -> Path:
    if os.environ.get("GITHUB_ACTIONS", "").lower() == "true":
        raise ValueError("Owner-local signing only; CI uses protected workflow")
    if not sys.stdin.isatty():
        raise ValueError("Interactive trusted terminal required for passwords")
    if not CERT.fullmatch(pinned_cert):
        raise ValueError("Pinned 64-hex public certificate required")
    if not re.fullmatch(r"[A-Za-z0-9_.-]{1,80}", alias):
        raise ValueError("Invalid PKCS12 key alias")
    source_check(source_sha)
    if not keystore.is_file() or keystore.stat().st_size == 0:
        raise ValueError("Existing permanent PKCS12 keystore required; no new key is generated")
    if output.resolve() == REPO or REPO in output.resolve().parents:
        raise ValueError("Signed artifacts must remain OUTSIDE the Git repository")
    if output.exists():
        raise ValueError("Output folder already exists; refusing to overwrite a prior candidate")

    aapt, align, signer = (tool(sdk, part) for part in ("aapt", "zipalign", "apksigner"))
    gradle = REPO / ("gradlew.bat" if os.name == "nt" else "gradlew")
    if not gradle.is_file():
        raise ValueError("Gradle wrapper missing")
    ensure_llama()
    run([str(gradle), "--no-daemon", "--max-workers=2", "--no-parallel",
         "-PunifiedModelLab=true", ":app:assembleRelease"])
    release = REPO / "app" / "build" / "outputs" / "apk" / "release"
    unsigned = list(release.glob("*-release-unsigned.apk"))
    if len(unsigned) != 1:
        raise ValueError("Exactly ONE unsigned ARM64 release APK required")

    # Never place the keystore, its passwords or an unverified APK in Git.
    store_pass = getpass.getpass("Existing PKCS12 store password: ")
    key_pass = getpass.getpass("Existing alias password: ")
    if not store_pass or not key_pass:
        raise ValueError("Blank password is forbidden")
    secret_env = os.environ.copy()
    secret_env["LOCALPILOT_RELEASE_STORE_PASS"] = store_pass
    secret_env["LOCALPILOT_RELEASE_KEY_PASS"] = key_pass
    store_pass = key_pass = None
    output.mkdir(parents=True, exist_ok=False)
    unverified = output / "UNVERIFIED-DO-NOT-INSTALL.apk"
    manifest = output / "CANDIDATE-MANIFEST.json"
    with tempfile.TemporaryDirectory(prefix="localpilot-align-") as temp:
        aligned = Path(temp) / "aligned.apk"
        run([align, "-P", "16", "-f", "4", str(unsigned[0]), str(aligned)])
        run([signer, "sign", "--ks", str(keystore), "--ks-type", "PKCS12",
             "--ks-key-alias", alias,
             "--ks-pass", "env:LOCALPILOT_RELEASE_STORE_PASS",
             "--key-pass", "env:LOCALPILOT_RELEASE_KEY_PASS",
             "--min-sdk-version", "30", "--v2-signing-enabled", "true",
             "--v3-signing-enabled", "true", "--out", str(unverified),
             str(aligned)], env=secret_env)
    secret_env.pop("LOCALPILOT_RELEASE_STORE_PASS", None)
    secret_env.pop("LOCALPILOT_RELEASE_KEY_PASS", None)
    run([align, "-c", "-P", "16", "4", str(unverified)])
    gate = [sys.executable, str(REPO / "tools/unified_signed_release_gate.py"),
            "--apk", str(unverified), "--aapt", aapt, "--apksigner", signer,
            "--cert", pinned_cert, "--source-sha", source_sha,
            "--output", str(manifest)]
    if previous is not None:
        if not previous.is_file():
            raise ValueError("Prior signed manifest not found")
        gate += ["--previous", str(previous)]
    run(gate)
    final = output / "LocalPilot-UNIFIED-SIGNED-CANDIDATE.apk"
    unverified.rename(final)
    checksum = hashlib.sha256(final.read_bytes()).hexdigest()
    manifest_checksum = hashlib.sha256(manifest.read_bytes()).hexdigest()
    (output / "SHA256SUMS.txt").write_text(
        f"{checksum}  {final.name}\n"
        f"{manifest_checksum}  {manifest.name}\n", encoding="utf-8")
    print("SIGNED_CANDIDATE_VERIFIED; releaseReady=false; physical test NOT DONE")
    return final


def main() -> None:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--keystore", type=Path, required=True)
    p.add_argument("--alias", required=True)
    p.add_argument("--cert", required=True, help="Public 64-hex SHA-256 certificate pin")
    p.add_argument("--source-sha", required=True)
    p.add_argument("--sdk", type=Path, required=True, help="Existing Android SDK root")
    p.add_argument("--previous-manifest", type=Path,
                   help="Earlier signed candidate manifest: require same signer and higher version")
    p.add_argument("--output-dir", type=Path, required=True,
                   help="Fresh, trusted directory OUTSIDE this checkout")
    args = p.parse_args()
    try:
        local_candidate(args.keystore.resolve(), args.alias, args.cert,
                        args.source_sha, args.sdk.resolve(),
                        args.output_dir.resolve(),
                        args.previous_manifest.resolve() if args.previous_manifest else None)
    except (OSError, ValueError, RuntimeError, subprocess.SubprocessError) as failure:
        raise SystemExit("LOCAL_SIGNED_CANDIDATE_STOP: " + str(failure))


if __name__ == "__main__":
    main()
