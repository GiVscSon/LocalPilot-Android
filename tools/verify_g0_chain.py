#!/usr/bin/env python3
"""G0 cross-check of ONE built ARM64 candidate APK with three independent audits.

APK ZIP source provenance, aapt/apksigner metadata, and actual 16KiB ELF+ZIP
data offsets must describe identical bytes and source. Candidate only:
debug signer is not a permanent release key; installed upgrade is unknown.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys

SHA40 = re.compile(r"[0-9a-f]{40}\Z")
SHA64 = re.compile(r"[0-9a-f]{64}\Z")


def require(condition: bool, message: str):
    if not condition:
        raise ValueError("G0_" + message)


def read_json(path: Path) -> dict:
    require(path.is_file() and not path.is_symlink(), "REPORT_MISSING")
    require(path.stat().st_size <= 1024 * 1024, "REPORT_OVERSIZE")
    value = json.loads(path.read_text(encoding="utf-8"))
    require(isinstance(value, dict), "REPORT_NOT_OBJECT")
    return value


def verify_signature(apk: Path, apksigner: Path) -> dict:
    """Verify these bytes with the SDK; a JSON certificate claim is not proof."""
    try:
        result = subprocess.run(
            [str(apksigner), "verify", "--verbose", "--print-certs", str(apk)],
            capture_output=True, text=True, check=False, timeout=60)
    except subprocess.TimeoutExpired as error:
        raise ValueError("G0_APKSIGNER_TIMEOUT") from error
    require(result.returncode == 0, "APKSIGNER_REJECTED_APK")
    require(len(result.stdout) <= 65536 and len(result.stderr) <= 65536,
            "APKSIGNER_OUTPUT_OVERSIZE")
    require(bool(re.search(r"^Verifies\s*$", result.stdout, re.M)),
            "APKSIGNER_VERIFICATION_MISSING")
    certificates = re.findall(
        r"^Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]{64})\s*$",
        result.stdout, re.M)
    require(len(certificates) == 1, "APKSIGNER_SINGLE_CERT_REQUIRED")
    schemes = re.findall(r"^Verified using (v[0-9.]+) scheme [^\n]*: true\s*$",
                         result.stdout, re.M)
    require(bool(set(schemes) & {"v2", "v3", "v3.1"}),
            "APKSIGNER_MODERN_SCHEME_REQUIRED")
    return {"certificateSha256": certificates[0].lower(),
            "verifiedSchemes": schemes, "verifier": "ANDROID_SDK_APKSIGNER"}


def inspect(apk: Path, source_sha: str, packaging: dict,
            signed_manifest: dict, elf: dict, *, apksigner: Path) -> dict:
    require(bool(SHA40.fullmatch(source_sha)), "EXPECTED_SOURCE_SHA_INVALID")
    require(apk.is_file() and 1024 < apk.stat().st_size < 45 * 1024 * 1024,
            "APK_ABSENT_OR_OUT_OF_BOUNDS")
    with apk.open("rb") as stream:
        data_sha = hashlib.file_digest(stream, "sha256").hexdigest()
    require(bool(SHA64.fullmatch(data_sha)), "APK_HASH_INVALID")
    require(packaging.get("schema") == "localpilot-apk-packaging-audit-v1"
            and packaging.get("status") == "PACKAGING_PASS", "ZIP_AUDIT_NOT_PASSED")
    require(packaging.get("sourceSha") == source_sha
            and packaging.get("apkSha256") == data_sha
            and packaging.get("apkBytes") == apk.stat().st_size,
            "APK_ZIP_SOURCE_OR_BYTES_MISMATCH")
    require("classes.dex" in packaging.get("dexFiles", [])
            and packaging.get("nesAssetsPresent") is True
            and "lib/arm64-v8a/liblocalpilot_embedded.so" in packaging.get("nativeLibraries", []),
            "APK_RUNTIMES_NOT_PROVEN_IN_ZIP")
    require(signed_manifest.get("schema") == "localpilot-all-in-one-test-v1"
            and signed_manifest.get("package") == "org.localpilot.android.dev.unified"
            and signed_manifest.get("sha256") == data_sha
            and signed_manifest.get("bytes") == apk.stat().st_size
            and signed_manifest.get("releaseReady") is False
            and signed_manifest.get("signedRelease") is False,
            "APK_SIGNATURE_AUDIT_MISMATCH")
    require(bool(SHA64.fullmatch(str(signed_manifest.get("debugSignerSha256", "")))),
            "APK_DEBUG_CERT_INVALID")
    require(elf.get("schema") == "localpilot-android-elf16k-v1"
            and elf.get("status") == "ELF_AND_ZIP_16K_PASS"
            and elf.get("abi") == "arm64-v8a"
            and elf.get("apkSha256") == data_sha
            and elf.get("physicalDevice") is False
            and elf.get("releaseReady") is False,
            "APK_ELF16K_AUDIT_MISMATCH")
    libraries = elf.get("libraries", [])
    require(isinstance(libraries, list) and any(
        entry.get("path") == "lib/arm64-v8a/liblocalpilot_embedded.so"
        and isinstance(entry.get("minimumLoadAlignment"), int)
        and entry.get("minimumLoadAlignment") >= 16384
        for entry in libraries if isinstance(entry, dict)),
        "NATIVE_LIB_MISSING_OR_MISALIGNED")
    signature = verify_signature(apk, apksigner)
    require(signature["certificateSha256"] == signed_manifest["debugSignerSha256"],
            "APK_SIGNER_DOES_NOT_MATCH_SDK")
    with apk.open("rb") as stream:
        require(hashlib.file_digest(stream, "sha256").hexdigest() == data_sha,
                "APK_CHANGED_DURING_SIGNATURE_VERIFICATION")
    return {
        "schema": "localpilot-g0-source-apk-cert-elf-v1",
        "status": "G0_CANDIDATE_PROVENANCE_PASS",
        "sourceSha": source_sha,
        "apkSha256": data_sha,
        "apkBytes": apk.stat().st_size,
        "package": signed_manifest["package"],
        "versionName": signed_manifest.get("versionName"),
        "versionCode": signed_manifest.get("versionCode"),
        "debugSignerSha256": signed_manifest["debugSignerSha256"],
        "signatureVerification": signature,
        "abi": "arm64-v8a",
        "dexFiles": packaging["dexFiles"],
        "nativeLibraries": packaging["nativeLibraries"],
        "checks": {
            "sameApkBytes": "PASS", "sourceProvenance": "PASS",
            "zipDexManifest": "PASS", "apksignerDebugSignature": "PASS",
            "elf16k": "PASS"
        },
        "notProven": {
            "stableSigningAcrossBuilds": "UNKNOWN",
            "nonDestructiveUpgrade": "UNKNOWN",
            "realGgufOnPhysicalDevice": "UNKNOWN",
            "nesContinuousControlPhysical": "UNKNOWN",
            "vulkanPhysical": "UNKNOWN"
        },
        "releaseReady": False
    }


def main() -> int:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--apk", required=True, type=Path)
    p.add_argument("--source-sha", required=True)
    p.add_argument("--packaging", required=True, type=Path)
    p.add_argument("--signed-manifest", required=True, type=Path)
    p.add_argument("--elf", required=True, type=Path)
    p.add_argument("--apksigner", required=True, type=Path,
                   help="Trusted Android SDK apksigner executable; verify only, no key access")
    p.add_argument("--output", required=True, type=Path)
    a = p.parse_args()
    try:
        result = inspect(a.apk, a.source_sha,
                         read_json(a.packaging), read_json(a.signed_manifest),
                         read_json(a.elf), apksigner=a.apksigner)
    except (ValueError, OSError, json.JSONDecodeError) as error:
        print("G0_CANDIDATE_STOP: " + str(error), file=sys.stderr)
        return 1
    a.output.parent.mkdir(parents=True, exist_ok=True)
    a.output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n")
    print("G0_CANDIDATE_PROVENANCE_PASS source=" + result["sourceSha"]
          + " apk=" + result["apkSha256"] + " DEVICE=UNKNOWN RELEASE=NOT_READY")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
