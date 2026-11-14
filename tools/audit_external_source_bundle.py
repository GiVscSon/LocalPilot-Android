#!/usr/bin/env python3
"""Read-only, fail-closed audit of externally supplied LocalPilot source ZIPs.

Never installs, deletes, copies or executes code from an archive. Compare actual
uncompressed bytes to an optional SHA256 manifest and to a Git commit's BLOBS,
not to a user-supplied claim about which commit the archive represents.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import subprocess
import sys
import zipfile
from pathlib import Path, PurePosixPath

MAX_ENTRIES = 10000
MAX_ITEM_BYTES = 32 * 1024 * 1024
MAX_TOTAL_BYTES = 256 * 1024 * 1024
ALLOWED_META = {"REPOSITORY_MANIFEST_0_8_2.json", "REPOSITORY_MANIFEST.json"}


def digest(blob: bytes) -> str:
    return hashlib.sha256(blob).hexdigest()


def safe_name(name: str) -> bool:
    parsed = PurePosixPath(name)
    return (bool(name) and not name.startswith(("/", "\\")) and ":" not in name.split("/")[0] and
            "\\" not in name and "\x00" not in name and
            not any(p in ("", ".", "..") for p in name.split("/")) and
            str(parsed) == name)


def scan_zip(path: Path) -> dict[str, bytes]:
    result = {}
    total = 0
    with zipfile.ZipFile(path) as archive:
        infos = archive.infolist()
        if len(infos) > MAX_ENTRIES:
            raise ValueError("Too many source archive entries")
        for info in infos:
            if info.is_dir():
                continue
            name = info.filename
            # Only normal files; reject symbolic links even if they point inside the ZIP.
            mode = (info.external_attr >> 16) & 0o170000
            if (not safe_name(name) or name in result or mode == 0o120000 or
                    info.flag_bits & 1 or info.file_size > MAX_ITEM_BYTES):
                raise ValueError("Unsafe, duplicate, encrypted or oversized archive member: " + name)
            total += info.file_size
            if total > MAX_TOTAL_BYTES:
                raise ValueError("Source archive exceeds uncompressed size bound")
            blob = archive.read(info)
            if len(blob) != info.file_size:
                raise ValueError("Truncated archive member: " + name)
            result[name] = blob
    return result


def check_manifest(files: dict[str, bytes], external: Path | None) -> dict:
    if external is not None:
        manifest = json.loads(external.read_text(encoding="utf-8"))
    else:
        embedded = files.get("REPOSITORY_MANIFEST.json") or files.get("REPOSITORY_MANIFEST_0_8_2.json")
        if embedded is None:
            raise ValueError("Missing embedded manifest; pass --manifest")
        manifest = json.loads(embedded)
    expected = manifest.get("files")
    if not isinstance(expected, dict) or len(expected) > MAX_ENTRIES:
        raise ValueError("Invalid manifest files map")
    broken = []
    for name, claim in expected.items():
        if not safe_name(name) or not isinstance(claim, dict):
            raise ValueError("Unsafe manifest path: " + name)
        blob = files.get(name)
        if (blob is None or claim.get("bytes") != len(blob) or
                claim.get("sha256") != digest(blob)):
            broken.append(name)
    extras = sorted(set(files) - set(expected) - ALLOWED_META)
    if broken or extras:
        raise ValueError("Manifest mismatch: " + json.dumps({"broken": broken[:12], "extras": extras[:12]}))
    return {"declaredCommit": manifest.get("commit"), "manifestFilesVerified": len(expected)}


def git(root: Path, *args: str) -> bytes:
    return subprocess.check_output(["git", "-C", str(root), *args], stderr=subprocess.PIPE)


def git_tree(root: Path, ref: str) -> tuple[str, dict[str, bytes]]:
    if not (root / ".git").exists():
        raise ValueError("--repository must have real .git history")
    commit = git(root, "rev-parse", "--verify", f"{ref}^{{commit}}").decode().strip()
    tracked: dict[str, bytes] = {}
    for record in git(root, "ls-tree", "-r", "-z", "--full-tree", commit).split(b"\0"):
        if not record:
            continue
        mode_type_sha, raw_name = record.split(b"\t", 1)
        mode, kind, sha = mode_type_sha.split(b" ")
        name = raw_name.decode("utf-8", "surrogateescape")
        if not safe_name(name) or kind != b"blob" or mode == b"120000":
            raise ValueError("Git tree has unsafe or symlink member: " + name)
        blob = git(root, "cat-file", "blob", sha.decode())
        if len(blob) > MAX_ITEM_BYTES:
            raise ValueError("Git blob exceeds size bound: " + name)
        tracked[name] = blob
    return commit, tracked


def compare(files: dict[str, bytes], reference: dict[str, bytes]) -> dict:
    left = set(files) - ALLOWED_META
    right = set(reference) - ALLOWED_META
    common = left & right
    changed = sorted(path for path in common if files[path] != reference[path])
    return {"onlyInArchive": sorted(left - right),
            "onlyInReference": sorted(right - left),
            "different": changed,
            "same": len(common) - len(changed)}


def audit(source: Path, manifest: Path | None = None,
          repository: Path | None = None, ref: str = "HEAD",
          second_zip: Path | None = None) -> dict:
    files = scan_zip(source)
    report = {"sourceArchive": source.name, "archiveEntries": len(files),
              **check_manifest(files, manifest)}
    report["sourceManifestIntegrity"] = "PASS"
    if second_zip is not None:
        report["otherArchive"] = second_zip.name
        report["secondZipDiff"] = compare(files, scan_zip(second_zip))
    if repository is not None:
        commit, tracked = git_tree(repository.resolve(), ref)
        diff = compare(files, tracked)
        report["actualGitCommit"] = commit
        report["commitClaimMatches"] = report["declaredCommit"] == commit
        report["gitDiff"] = diff
        report["gitTreeMatches"] = (report["commitClaimMatches"] and not any(
            diff[k] for k in ("onlyInArchive", "onlyInReference", "different")))
    return report


def main(argv=None) -> int:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--source", required=True, type=Path, help="external full repository ZIP")
    p.add_argument("--manifest", type=Path)
    p.add_argument("--repository", type=Path, help="real Git checkout, NEVER modified")
    p.add_argument("--ref", default="HEAD", help="Git commit/tag/ref to compare")
    p.add_argument("--other-zip", type=Path, help="additional external bundle to compare")
    p.add_argument("--report", type=Path, help="write audit JSON outside source tree")
    args = p.parse_args(argv)
    try:
        result = audit(args.source, args.manifest, args.repository, args.ref, args.other_zip)
        output = json.dumps(result, ensure_ascii=False, indent=2) + "\n"
        if args.report:
            args.report.write_text(output, encoding="utf-8")
        print(output, end="")
        return 2 if (result.get("gitTreeMatches") is False) else 0
    except (ValueError, OSError, zipfile.BadZipFile, subprocess.CalledProcessError,
            json.JSONDecodeError) as error:
        print(f"SOURCE_BUNDLE_REJECTED: {type(error).__name__}: {error}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
