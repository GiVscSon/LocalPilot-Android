#!/usr/bin/env python3
"""Make a newly installed Android CLI v22+ the active 'cmdline-tools/latest'.

Some preinstalled GitHub images contain Command-line Tools 12 in 'latest'.
sdkmanager 'cmdline-tools;latest' then installs 22 as 'latest-2' without
overwriting the old folder. This promotes only a validated, local official
SDK package, with the previous tree preserved for rollback.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import re
import sys

REVISION = re.compile(r"^[0-9]+(?:\.[0-9]+){0,4}$")


def version(folder: Path) -> tuple[int, ...] | None:
    prop = folder / "source.properties"
    if folder.is_symlink() or not folder.is_dir() or not prop.is_file() or prop.is_symlink():
        return None
    if prop.stat().st_size > 100_000:
        return None
    for line in prop.read_text(encoding="utf-8", errors="replace").splitlines():
        found = re.match(r"^\s*Pkg\.Revision\s*=\s*(.*?)\s*$", line)
        if found:
            raw = found.group(1)
            if REVISION.fullmatch(raw):
                parts = tuple(int(s) for s in raw.split("."))
                return (parts + (0, 0, 0, 0))[:4]
            return None
    return None


def runnable_cli(path: Path, minimum: tuple[int, ...] = (22, 0, 0, 0)) -> bool:
    rev = version(path)
    return (rev is not None and rev >= minimum and
            all((path / name).is_file() and not (path / name).is_symlink()
                and (path / name).stat().st_mode & 0o111
                for name in ("bin/sdkmanager", "bin/android")))


def promote(root: Path) -> dict:
    base = root / "cmdline-tools"
    active = base / "latest"
    if active.is_symlink():
        raise ValueError("Active CLI folder must not be a symlink")
    if runnable_cli(active):
        return {"result": "ALREADY_CURRENT", "version": ".".join(
            str(x) for x in version(active)[:2])}
    if not active.is_dir():
        raise ValueError("Original SDK manager is missing; bootstrap first")
    all_candidates = [
        p for p in base.iterdir()
        if p.name != "latest" and not p.name.startswith("previous-")
        and runnable_cli(p)
    ]
    if not all_candidates:
        raise ValueError("No complete newer Android CLI found; current CLI retained")
    candidate = max(all_candidates, key=lambda path: version(path))
    oldrev = version(active)
    oldtext = "unknown" if oldrev is None else ".".join(str(x) for x in oldrev[:2])
    backup = base / ("previous-cli-" + oldtext)
    for suffix in range(2, 100):
        if not backup.exists():
            break
        backup = base / ("previous-cli-" + oldtext + "-" + str(suffix))
    if backup.exists():
        raise ValueError("Too many old CLI backups")
    moved_old = False
    try:
        active.rename(backup)
        moved_old = True
        candidate.rename(active)
        if not runnable_cli(active):
            raise ValueError("Promoted CLI failed post-move validation")
    except Exception:
        # Keep the new download if possible; always restore the old active
        # manager when the second rename or verification fails.
        if moved_old:
            if active.exists():
                active.rename(candidate)
            backup.rename(active)
        raise
    return {
        "result": "PROMOTED",
        "version": ".".join(str(x) for x in version(active)[:2]),
        "previousVersion": oldtext,
        "backupFolder": backup.name,
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--sdk-root", type=Path, required=True)
    args = parser.parse_args()
    try:
        result = promote(args.sdk_root)
    except (ValueError, OSError) as error:
        print("ANDROID_CLI_UPGRADE_FAIL: " + str(error), file=sys.stderr)
        return 1
    print("ANDROID_CLI_UPGRADE: " + json.dumps(result, sort_keys=True))
    return 0


if __name__ == "__main__":
    sys.exit(main())
