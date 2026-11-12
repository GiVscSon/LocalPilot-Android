#!/usr/bin/env python3
"""Inspect the *built* LocalPilot APK, never the source directory.

This is a packaging/provenance gate, NOT a signature verifier, CPU/GPU test,
offline-network proof, or Android device acceptance. Requires Python 3 only.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
from pathlib import Path
from zipfile import BadZipFile, ZipFile

SOURCE_ENTRY = "assets/localpilot-source.json"
REQUIRED_NES = (
    "assets/nes/index.html",
    "assets/nes/jsnes.min.js",
    "assets/nes/latched-buttons.js",
)
REQUIRED_ANDROID = ("AndroidManifest.xml", "resources.arsc", "classes.dex")
SHA_RE = re.compile(r"^[0-9a-fA-F]{40}$")
DEX_RE = re.compile(r"^classes(\d+)?\.dex$")
DEX_MAGIC_RE = re.compile(rb"^dex\n[0-9]{3}\x00")


class AuditError(ValueError):
    """A release-blocking APK packaging fault."""


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _safe_member(name: str) -> bool:
    if not name or name.startswith("/") or "\\" in name or "\x00" in name:
        return False
    parts = name.rstrip("/").split("/")
    return all(part not in ("", ".", "..") for part in parts)


def inspect_apk(
    path: Path,
    *,
    expected_source_sha: str | None = None,
    require_nes: bool = True,
    strict_one_dex: bool = False,
    baseline_apk: Path | None = None,
) -> dict:
    """Report actual ZIP contents; fail instead of inferring a release from Gradle."""
    if expected_source_sha is not None and not SHA_RE.fullmatch(expected_source_sha):
        raise AuditError("EXPECTED_SOURCE_SHA_INVALID")
    if not path.is_file() or path.stat().st_size == 0:
        raise AuditError("APK_MISSING_OR_EMPTY")
    size = path.stat().st_size
    warnings: list[str] = []
    try:
        with ZipFile(path, "r") as apk:
            infos = apk.infolist()
            names = [item.filename for item in infos]
            if not infos:
                raise AuditError("EMPTY_APK_ZIP")
            if len(names) != len(set(names)):
                raise AuditError("DUPLICATE_ZIP_MEMBER")
            for name in names:
                if not _safe_member(name):
                    raise AuditError("UNSAFE_ZIP_MEMBER:" + name)
                leaf = name.rstrip("/").split("/")[-1].lower()
                if leaf.endswith((".bak", ".tmp", ".swp")) or leaf.endswith("~"):
                    raise AuditError("BACKUP_FILE_IN_APK:" + name)

            by_name = {item.filename: item for item in infos}
            required = REQUIRED_ANDROID + (REQUIRED_NES if require_nes else ())
            for name in required:
                if name not in by_name:
                    raise AuditError("REQUIRED_ENTRY_MISSING:" + name)
                if by_name[name].file_size == 0:
                    raise AuditError("REQUIRED_ENTRY_EMPTY:" + name)
            if SOURCE_ENTRY not in by_name:
                raise AuditError("BUILD_SOURCE_PROVENANCE_MISSING")
            try:
                source = json.loads(apk.read(SOURCE_ENTRY))
            except (ValueError, UnicodeDecodeError):
                raise AuditError("BUILD_SOURCE_PROVENANCE_INVALID") from None
            if not isinstance(source, dict) or source.get("schema") != "localpilot-source-v1":
                raise AuditError("BUILD_SOURCE_SCHEMA_INVALID")
            actual_source = source.get("sourceSha")
            if not isinstance(actual_source, str) or not SHA_RE.fullmatch(actual_source):
                raise AuditError("BUILD_SOURCE_SHA_INVALID")
            if expected_source_sha and actual_source.lower() != expected_source_sha.lower():
                raise AuditError("BUILD_SOURCE_SHA_MISMATCH")

            dex_indices: list[int] = []
            for name in names:
                matched = DEX_RE.fullmatch(name)
                if not matched:
                    continue
                number = int(matched.group(1) or 1)
                if number < 1 or name == "classes1.dex":
                    raise AuditError("INVALID_DEX_FILENAME:" + name)
                if by_name[name].file_size < 8:
                    raise AuditError("TRUNCATED_DEX:" + name)
                with apk.open(name) as stream:
                    if not DEX_MAGIC_RE.fullmatch(stream.read(8)):
                        raise AuditError("INVALID_DEX_MAGIC:" + name)
                dex_indices.append(number)
            dex_indices.sort()
            if dex_indices != list(range(1, len(dex_indices) + 1)):
                raise AuditError("MISSING_DEX_SEQUENCE")
            if strict_one_dex and len(dex_indices) > 1:
                raise AuditError("UNEXPECTED_MULTIDEX")
            if len(dex_indices) > 1:
                warnings.append("MULTIDEX_PRESENT_VERIFY_DEX_METHOD_COUNTS_BEFORE_REMOVAL")

            native_libs: list[str] = []
            for name in names:
                if not name.startswith("lib/") or not name.endswith(".so"):
                    continue
                if by_name[name].file_size < 4 or apk.read(name)[:4] != b"\x7fELF":
                    raise AuditError("INVALID_NATIVE_LIBRARY:" + name)
                native_libs.append(name)
            # A ZIP index can prove packaging, not the absence of runtime
            # references to a missing native library, or a correct JNI ABI.
            gguf = [name for name in names if name.startswith("assets/models/") and name.endswith(".gguf")]
            for name in gguf:
                if by_name[name].compress_type != 0:
                    warnings.append("GGUF_COMPRESSED_SLOW_RUNTIME:" + name)
            assets = [name for name in names if name.startswith("assets/") and not name.endswith("/")]
            result = {
                "schema": "localpilot-apk-packaging-audit-v1",
                "status": "PACKAGING_PASS",
                "apk": path.name,
                "apkBytes": size,
                "apkSha256": sha256_file(path),
                "sourceSha": actual_source,
                "dexFiles": ["classes.dex" if i == 1 else f"classes{i}.dex" for i in dex_indices],
                "dexCount": len(dex_indices),
                "nativeLibraries": sorted(native_libs),
                "assetCount": len(assets),
                "nesAssetsPresent": require_nes,
                "embeddedGgufCount": len(gguf),
                "warnings": warnings,
                "limits": [
                    "ZIP_ONLY_NOT_APKSIGNER",
                    "NO_DEVICE_OR_GPU_PROOF",
                    "NO_NETWORK_EGRESS_PROOF",
                    "NO_JNI_LINKAGE_PROOF",
                ],
            }
    except (BadZipFile, EOFError, OSError) as error:
        raise AuditError("APK_ZIP_UNREADABLE:" + type(error).__name__) from error

    if baseline_apk is not None:
        if not baseline_apk.is_file():
            raise AuditError("BASELINE_APK_MISSING")
        result["apkDeltaBytes"] = size - baseline_apk.stat().st_size
        result["baselineApkSha256"] = sha256_file(baseline_apk)
    return result


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path)
    parser.add_argument("--expected-source-sha")
    parser.add_argument("--no-nes", action="store_true", help="Only for non-NES test variants")
    parser.add_argument("--strict-one-dex", action="store_true",
                        help="Only when the method count has independently been audited")
    parser.add_argument("--baseline-apk", type=Path)
    parser.add_argument("--report", type=Path)
    args = parser.parse_args(argv)
    try:
        report = inspect_apk(
            args.apk, expected_source_sha=args.expected_source_sha,
            require_nes=not args.no_nes, strict_one_dex=args.strict_one_dex,
            baseline_apk=args.baseline_apk,
        )
    except AuditError as error:
        print("APK_PACKAGING_GATE_FAIL:", error, file=sys.stderr)
        return 1
    text = json.dumps(report, indent=2, sort_keys=True, ensure_ascii=False) + "\n"
    print(text, end="")
    if args.report:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(text, encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
