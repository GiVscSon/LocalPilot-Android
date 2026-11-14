#!/usr/bin/env python3
"""Create a deterministic, verifiable LocalPilot source ZIP from Git commit blobs.

A working-tree ZIP may include uncommitted edits. This command uses only Git's
immutable commit objects. APK and local signing/data files are NEVER packaged.
"""
from __future__ import annotations

import argparse
import json
import re
import sys
import zipfile
from pathlib import Path

from audit_external_source_bundle import audit, digest, git_tree, MAX_TOTAL_BYTES

PRIVATE_NAMES = {"local.properties", ".env", "id_rsa", "id_ed25519"}
PRIVATE_SUFFIXES = {".jks", ".keystore", ".pem", ".p12", ".apk", ".aab", ".key", ".pfx", ".log", ".mp4"}
SECRET_PATTERNS = (b"-----BEGIN PRIVATE KEY-----", b"-----BEGIN RSA PRIVATE KEY-----",
                   b"-----BEGIN EC PRIVATE KEY-----", b"-----BEGIN OPENSSH PRIVATE KEY-----")
FIXED_TIME = (1980, 1, 1, 0, 0, 0)


def version(gradle: bytes) -> tuple[str, int]:
    text = gradle.decode("utf-8")
    name = re.findall(r"\bversionName\s+['\"]([^'\"]+)['\"]", text)
    number = re.findall(r"\bversionCode\s+(\d+)\b", text)
    if len(name) != 1 or len(number) != 1 or not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._-]*", name[0]):
        raise ValueError("Build Gradle version is missing or ambiguous")
    return name[0], int(number[0])


def make_info(name: str) -> zipfile.ZipInfo:
    item = zipfile.ZipInfo(name, FIXED_TIME)
    item.create_system = 3
    item.external_attr = 0o100644 << 16
    item.compress_type = zipfile.ZIP_DEFLATED
    return item


def produce(root: Path, output: Path, ref: str = "HEAD") -> dict:
    root = root.resolve()
    commit, files = git_tree(root, ref)
    if not files or "app/build.gradle" not in files:
        raise ValueError("Not a checked-out LocalPilot Android tree")
    total = 0
    for name, blob in files.items():
        p = Path(name)
        if (p.name in PRIVATE_NAMES or p.name.startswith(".env.") or
                p.suffix.lower() in PRIVATE_SUFFIXES or
                any(piece.lower() in {"private", "secrets", "user-data", "captures", "recordings"} for piece in p.parts) or
                any(pattern in blob for pattern in SECRET_PATTERNS)):
            raise ValueError("Potential private file must not be placed in source ZIP: " + name)
        total += len(blob)
        if total > MAX_TOTAL_BYTES:
            raise ValueError("Git tree exceeds maximum source archive size")
    name, code = version(files["app/build.gradle"])
    manifest = {"repository": "GiVscSon/LocalPilot-Android", "commit": commit,
                "versionName": name, "versionCode": code, "totalFiles": len(files),
                "source": "GIT_COMMIT_BLOBS_ONLY", "signedApkIncluded": False,
                "files": {path: {"bytes": len(blob), "sha256": digest(blob)}
                          for path, blob in sorted(files.items())}}
    manifest_bytes = (json.dumps(manifest, ensure_ascii=False, sort_keys=True,
                                 separators=(",", ":")) + "\n").encode("utf-8")
    output = output.resolve()
    if output.exists():
        raise FileExistsError("Refusing to overwrite existing source artifact: " + str(output))
    output.parent.mkdir(parents=True, exist_ok=True)
    try:
        with output.open("xb") as dest:
            with zipfile.ZipFile(dest, "w", compression=zipfile.ZIP_DEFLATED,
                                 compresslevel=9, allowZip64=False) as archive:
                for path, blob in sorted(files.items()):
                    archive.writestr(make_info(path), blob)
                archive.writestr(make_info("REPOSITORY_MANIFEST.json"), manifest_bytes)
        verified = audit(output, repository=root, ref=commit)
        if not verified["gitTreeMatches"]:
            raise AssertionError("Post-archive Git tree comparison failed")
    except BaseException:
        output.unlink(missing_ok=True)
        raise
    return {"commit": commit, "version": name, "versionCode": code,
            "files": len(files), "bytes": output.stat().st_size, "sha256": digest(output.read_bytes())}


def main(argv=None) -> int:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--repository", type=Path, default=Path(__file__).resolve().parents[1])
    p.add_argument("--ref", default="HEAD")
    p.add_argument("--output", type=Path, required=True)
    args = p.parse_args(argv)
    try:
        print(json.dumps(produce(args.repository, args.output, args.ref), indent=2))
        return 0
    except (ValueError, OSError, FileExistsError, RuntimeError, zipfile.BadZipFile) as e:
        print(f"CANONICAL_SOURCE_REJECTED: {type(e).__name__}: {e}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
