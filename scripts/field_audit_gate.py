#!/usr/bin/env python3
"""Validate an explicitly exported LocalPilot audit ZIP without inflating claims.

SafeJournal v1 is a checksum-chained metadata journal, not a signed device
attestation or a per-game Market acceptance receipt. No APK input is authorized.
"""
from __future__ import annotations

import argparse
from collections import Counter, defaultdict
import hashlib
import json
from pathlib import Path
import re
import sys
from zipfile import BadZipFile, ZipFile

MAX_ARCHIVE_BYTES = 6 * 1024 * 1024
MAX_LOG_BYTES = 524288
MAX_NOTE_BYTES = 8192
LOG_NAME = re.compile(r"segment-(0|[1-9][0-9]{0,17})\.log")
SOURCE_NAME = re.compile(r"(?:segment-[0-9]{1,18}\.log|legacy-v1\.log|legacy-root-v1\.log)")
META_ENTRY = re.compile(r"(?P<name>[^ ]+) bytes=(?P<size>[0-9]+) sha256=(?P<hash>[0-9a-f]{64})")
EVENTS = frozenset(("PROPOSED", "ANDROID_COMPLETED", "ANDROID_CANCELLED",
                    "REJECTED", "STOP", "VERIFIED_2048", "VERIFIED_GRID",
                    "VERIFIED_BUBBLE", "VERIFIED_ARCADE"))
VERIFIED = frozenset(event for event in EVENTS if event.startswith("VERIFIED_"))


class InvalidAudit(ValueError):
    """The source is not a valid, bounded LocalPilot audit-v2 export."""


def _read_source(raw: bytes, name: str) -> list[tuple[str, int]]:
    if len(raw) > MAX_LOG_BYTES:
        raise InvalidAudit(name + ": log is oversized")
    try:
        content = raw.decode("utf-8", errors="strict")
    except UnicodeDecodeError as exc:
        raise InvalidAudit(name + ": log is not UTF-8") from exc
    if content and not content.endswith("\n"):
        raise InvalidAudit(name + ": partial final journal line")
    previous, sequence, rows = "0", 0, []
    for line in content.splitlines():
        if not line:
            continue
        fields = line.split("|")
        if len(fields) != 8 or fields[1] not in EVENTS:
            raise InvalidAudit(name + ": unexpected journal event or fields")
        if not all(re.fullmatch(r"[0-9]+", value) for value in
                   (fields[0], *fields[2:6])):
            raise InvalidAudit(name + ": nonnumeric journal context")
        sequence += 1
        if int(fields[0]) != sequence or fields[6] != previous:
            raise InvalidAudit(name + ": broken sequence or predecessor")
        body = "|".join(fields[:7]).encode("utf-8")
        actual = hashlib.sha256(body).hexdigest()
        if fields[7] != actual:
            raise InvalidAudit(name + ": invalid journal checksum")
        previous = actual
        rows.append((fields[1], int(fields[2])))
    return rows


def inspect(path: Path) -> dict:
    """Read bounded local metadata only, without assuming device/game identity."""
    if not path.is_file() or path.stat().st_size > MAX_ARCHIVE_BYTES:
        raise InvalidAudit("missing or oversized exported ZIP")
    try:
        with ZipFile(path) as archive:
            members = archive.infolist()
            names = [item.filename for item in members]
            if len(names) != len(set(names)):
                raise InvalidAudit("duplicate ZIP member")
            if len(names) > 12 or not {"metadata.txt", "README.txt"} <= set(names):
                raise InvalidAudit("missing export metadata or too many members")
            segments = [name for name in names if LOG_NAME.fullmatch(name)]
            old = [name for name in names if name in
                   ("legacy-v1.log", "legacy-root-v1.log")]
            if len(segments) > 8 or len(set(int(LOG_NAME.fullmatch(s).group(1))
                                           for s in segments)) != len(segments):
                raise InvalidAudit("ambiguous or oversized segment collection")
            if any(name not in segments + old + ["metadata.txt", "README.txt"]
                   for name in names):
                raise InvalidAudit("unexpected ZIP member")
            data = {}
            for item in members:
                limit = MAX_LOG_BYTES if item.filename in segments + old else MAX_NOTE_BYTES
                if item.is_dir() or item.flag_bits & 1 or item.file_size > limit:
                    raise InvalidAudit("invalid or oversized ZIP entry: " + item.filename)
                if (item.external_attr >> 16) & 0o170000 == 0o120000:
                    raise InvalidAudit("symbolic link in audit ZIP")
                payload = archive.read(item)
                if len(payload) != item.file_size:
                    raise InvalidAudit("truncated ZIP member")
                data[item.filename] = payload
    except (BadZipFile, OSError, RuntimeError, EOFError) as exc:
        raise InvalidAudit("invalid exported audit archive") from exc
    try:
        metadata = data["metadata.txt"].decode("utf-8", errors="strict").splitlines()
    except UnicodeDecodeError as exc:
        raise InvalidAudit("metadata encoding") from exc
    if metadata[:2] != ["LocalPilot audit export-v2",
                        "Explicit local export; no upload."]:
        raise InvalidAudit("unexpected audit-export version")
    properties = {}
    listings = {}
    for line in metadata[2:]:
        match = META_ENTRY.fullmatch(line)
        if match:
            name = match["name"]
            if name not in segments + old or name in listings:
                raise InvalidAudit("unexpected metadata source")
            listings[name] = (int(match["size"]), match["hash"])
        else:
            if "=" not in line:
                raise InvalidAudit("unrecognized audit metadata")
            key, value = line.split("=", 1)
            if key in properties or key not in ("status", "segment_count", "event_count"):
                raise InvalidAudit("unexpected metadata field")
            properties[key] = value
    if set(listings) != set(segments + old):
        raise InvalidAudit("metadata does not list all journal sources")
    if not all(properties.get(k, "").isdigit()
               for k in ("segment_count", "event_count")):
        raise InvalidAudit("missing numeric metadata fields")
    if int(properties["segment_count"]) != len(segments):
        raise InvalidAudit("segment count mismatch")
    all_events, current_events, legacy_events = [], [], []
    for name in sorted(segments, key=lambda p: int(LOG_NAME.fullmatch(p).group(1))) + old:
        source = data[name]
        if listings[name] != (len(source), hashlib.sha256(source).hexdigest()):
            raise InvalidAudit("metadata SHA-256/length mismatch: " + name)
        rows = _read_source(source, name)
        all_events.extend(rows)
        (legacy_events if name in old else current_events).extend(rows)
    if int(properties["event_count"]) != len(all_events):
        raise InvalidAudit("metadata event count mismatch")
    if properties.get("status") != ("no_journal_events" if not all_events
                                   else "verified_journal_events"):
        raise InvalidAudit("export status mismatch")
    counts = Counter(name for name, epoch in current_events)
    legacy_counts = Counter(name for name, epoch in legacy_events)
    # Only a coarse same-epoch delivery correlation is possible: verified rows
    # contain request=0 and no package/game/gesture identifier.
    acknowledgements = defaultdict(int)
    plausible = Counter()
    for event, epoch in current_events:
        if event == "ANDROID_COMPLETED":
            acknowledgements[epoch] += 1
        elif event in VERIFIED:
            if acknowledgements[epoch] > 0:
                plausible[event] += 1
                acknowledgements[epoch] -= 1
        elif event == "STOP":
            acknowledgements.pop(epoch, None)
    return {
        "schema": "localpilot-field-audit-check-v1",
        "integrity": "PASS",
        "scope": "LOCAL_CHECKSUM_CHAIN_ONLY",
        "physical_device_verified": False,
        "real_market_game_verified": False,
        "release_ready": False,
        "current_segments": len(segments),
        "current_events": len(current_events),
        "legacy_events_excluded_from_current": len(legacy_events),
        "counts_current": dict(sorted(counts.items())),
        "counts_legacy": dict(sorted(legacy_counts.items())),
        "verified_current": {name: counts.get(name, 0) for name in sorted(VERIFIED)},
        "same_epoch_ack_correlations_not_proof": dict(sorted(plausible.items())),
        "caveat": ("Journal rows have no game identity, screenshot or signed device "
                   "attestation. An Android ACK and even a checksum-valid "
                   "VERIFIED event alone cannot satisfy ten-game field acceptance.")
    }


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("audit_zip", type=Path, help="Explicit AuditExport.snapshot ZIP")
    parser.add_argument("--output", type=Path, help="Optional local JSON summary")
    args = parser.parse_args(argv)
    try:
        report = inspect(args.audit_zip)
        serialized = json.dumps(report, ensure_ascii=False, sort_keys=True, indent=2) + "\n"
        if args.output:
            args.output.write_text(serialized, encoding="utf-8")
        else:
            print(serialized, end="")
        return 0
    except (InvalidAudit, OSError) as exc:
        print("INVALID_AUDIT: " + str(exc), file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
