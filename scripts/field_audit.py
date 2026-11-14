"""Read-only comparison of LocalPilot's existing AuditExport v2 snapshots."""
from __future__ import annotations

from collections import Counter
from dataclasses import dataclass
import hashlib
import io
from itertools import chain
from pathlib import Path
import re
import stat
from zipfile import ZIP_DEFLATED, ZIP_STORED, BadZipFile, ZipFile
from zlib import error as DecompressionError

MAX_ARCHIVE = 6 * 1024 * 1024  # AuditExport.MAX_ZIP
MAX_LOG = 524_288  # SafeJournal.MAX
MAX_META = 4096
SEGMENT = re.compile(r"segment-(0|[1-9][0-9]{0,17})\.log")
LEGACY = {"legacy-v1.log", "legacy-root-v1.log"}
META_ENTRY = re.compile(r"([^ ]+) bytes=(0|[1-9][0-9]{0,6}) sha256=([a-f0-9]{64})")
NUMBER = re.compile(r"0|[1-9][0-9]{0,18}")
TERMINALS = {"ANDROID_COMPLETED", "ANDROID_CANCELLED", "REJECTED"}
VISUAL = {"VERIFIED_2048", "VERIFIED_GRID", "VERIFIED_BUBBLE", "VERIFIED_ARCADE"}
EVENTS = {"PROPOSED", "STOP"} | TERMINALS | VISUAL


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


@dataclass(frozen=True)
class Event:
    kind: str
    epoch: int
    request: int
    metric: int


@dataclass
class Snapshot:
    digest: str
    segments: dict[int, bytes]
    events: dict[int, list[Event]]
    legacy_count: int


def parse_log(data: bytes) -> list[Event]:
    if data and not data.endswith(b"\n"):
        raise ValueError("Truncated journal log")
    rows = data.decode("ascii").split("\n")
    previous = "0"
    events = []
    for line in rows:
        if not line:
            continue
        fields = line.split("|")
        if len(fields) != 8:
            raise ValueError("Malformed journal record")
        seq, kind, epoch, request, time, metric, link, digest = fields
        if (kind not in EVENTS or link != previous
                or any(not NUMBER.fullmatch(v) or int(v) > (1 << 63) - 1
                       for v in (seq, epoch, request, time, metric))
                or int(seq) != len(events) + 1):
            raise ValueError("Invalid journal sequence, event or hash link")
        if sha256(line.rsplit("|", 1)[0].encode("ascii")) != digest:
            raise ValueError("Journal record checksum mismatch")
        previous = digest
        events.append(Event(kind, int(epoch), int(request), int(metric)))
    return events


def read_snapshot(path: Path) -> Snapshot:
    if not path.is_file():
        raise ValueError("Audit snapshot must be a regular file")
    with path.open("rb") as source:
        blob = source.read(MAX_ARCHIVE + 1)
    if not blob.startswith(b"PK\x03\x04") or len(blob) > MAX_ARCHIVE:
        raise ValueError("Expected a bounded localpilot-audit ZIP from Export Journal")
    logs: dict[str, bytes] = {}
    with ZipFile(io.BytesIO(blob)) as archive:
        entries = archive.infolist()
        if not 2 <= len(entries) <= 12:
            raise ValueError("Invalid audit ZIP entry count")
        files = {}
        for entry in entries:
            name = entry.filename
            is_text = name in {"metadata.txt", "README.txt"}
            limit = MAX_META if is_text else MAX_LOG
            if (entry.orig_filename != name or name in files
                    or not (is_text or name in LEGACY or SEGMENT.fullmatch(name))
                    or entry.is_dir() or stat.S_ISLNK(entry.external_attr >> 16)
                    or entry.flag_bits & 1
                    or entry.compress_type not in {ZIP_STORED, ZIP_DEFLATED}
                    or entry.file_size > limit):
                raise ValueError("Unexpected, duplicate or oversized audit ZIP entry")
            with archive.open(entry) as source:
                data = source.read(limit + 1)
            if len(data) != entry.file_size:
                raise ValueError("Audit ZIP entry size mismatch")
            files[name] = data
        if not {"metadata.txt", "README.txt"} <= files.keys():
            raise ValueError("Audit export v2 metadata or README missing")
        meta = files.pop("metadata.txt").decode("ascii").splitlines()
        files.pop("README.txt")
        if len(meta) < 5 or meta[:2] != [
            "LocalPilot audit export-v2", "Explicit local export; no upload."
        ]:
            raise ValueError("Unsupported audit export format")
        for line in meta[5:]:
            match = META_ENTRY.fullmatch(line)
            if match is None:
                raise ValueError("Malformed audit manifest entry")
            name, size, digest = match.groups()
            data = files.pop(name, None)
            if data is None or len(data) != int(size) or sha256(data) != digest:
                raise ValueError("Audit manifest size, checksum or membership mismatch")
            logs[name] = data
        if files:
            raise ValueError("Unlisted audit ZIP entry")
    segments = {int(m[1]): data for name, data in logs.items()
                if (m := SEGMENT.fullmatch(name)) is not None}
    ids = sorted(segments)
    if len(ids) > 8 or (ids and ids != list(range(ids[0], ids[0] + len(ids)))):
        raise ValueError("Audit segment gap or retention limit exceeded")
    events = {key: parse_log(segments[key]) for key in ids}
    legacy = [parse_log(logs[name]) for name in LEGACY & logs.keys()]
    count = sum(map(len, events.values())) + sum(map(len, legacy))
    status = "verified_journal_events" if count else "no_journal_events"
    if meta[2:5] != [f"status={status}", f"segment_count={len(ids)}", f"event_count={count}"]:
        raise ValueError("Audit manifest event or segment count mismatch")
    return Snapshot(sha256(blob), segments, events, len(legacy))


def interval(before: Snapshot, after: Snapshot) -> list[Event]:
    if not before.segments:
        if min(after.segments, default=0) != 0:
            raise ValueError("No retained segment zero after the empty baseline")
        return [event for rows in after.events.values() for event in rows]
    newest = max(before.segments)
    if newest not in after.segments or not before.segments[newest]:
        raise ValueError("Newest baseline segment is missing or empty; export a new baseline")
    if min(after.segments) < min(before.segments):
        raise ValueError("After snapshot contains unexplained older segments")
    for key in before.segments.keys() & after.segments.keys():
        old, new = before.segments[key], after.segments[key]
        if (not new.startswith(old)) or (key != newest and new != old):
            raise ValueError("Audit snapshots do not share an append-only history")
    return (after.events[newest][len(before.events[newest]):]
            + [event for key, rows in after.events.items() if key > newest for event in rows])


def summarize(history: list[Event], new: list[Event]) -> dict:
    # RotatingJournal can resume after process restart. Epoch alone is not a
    # globally unique session ID; correlate terminal receipts by request too.
    proposals: dict[tuple[int, int], int] = {}
    terminal: set[tuple[int, int]] = set()
    counts: Counter[str] = Counter()
    outcomes: Counter[str] = Counter()
    visual: Counter[str] = Counter()
    stop_codes: Counter[int] = Counter()
    stop_at: dict[int, int] = {}
    user_stopped = False
    for index, ev in enumerate(chain(history, new)):
        current = index >= len(history)
        key = (ev.epoch, ev.request)
        if ev.kind == "PROPOSED":
            if not ev.request or key in proposals or key in terminal:
                raise ValueError("Invalid or repeated gesture proposal identity")
            proposals[key] = index
            if current:
                counts["proposed"] += 1
                if user_stopped:
                    counts["proposals_after_user_stop"] += 1
        elif ev.kind in TERMINALS:
            if not ev.request:
                raise ValueError("Android terminal receipt lacks a request identity")
            if key in terminal:
                raise ValueError("Repeated Android terminal receipt")
            terminal.add(key)
            if current:
                proposed_at = proposals.get(key)
                if proposed_at is None:
                    counts["unmatched_terminal_receipts"] += 1
                elif proposed_at < len(history):
                    counts["terminal_receipts_for_prior_proposals"] += 1
                else:
                    outcomes[ev.kind] += 1
                if proposed_at is not None and proposed_at < stop_at.get(ev.epoch, -1):
                    counts["terminal_receipts_after_stop_in_same_epoch"] += 1
        elif ev.kind == "STOP" and current:
            stop_codes[ev.metric] += 1
            stop_at[ev.epoch] = index
            user_stopped |= ev.metric == 1
        elif ev.kind in VISUAL and current:
            visual[ev.kind] += 1
    return {
        "events": len(new),
        "proposed": counts["proposed"],
        "android_completed": outcomes["ANDROID_COMPLETED"],
        "android_cancelled": outcomes["ANDROID_CANCELLED"],
        "android_rejected": outcomes["REJECTED"],
        "proposals_without_terminal_receipt": counts["proposed"] - sum(outcomes.values()),
        "terminal_receipts_for_prior_proposals": counts["terminal_receipts_for_prior_proposals"],
        "unmatched_terminal_receipts": counts["unmatched_terminal_receipts"],
        "terminal_receipts_after_stop_in_same_epoch": counts["terminal_receipts_after_stop_in_same_epoch"],
        "visual_receipts": dict(sorted(visual.items())),
        "stop_codes": {str(key): stop_codes[key] for key in sorted(stop_codes)},
        "user_stop_observed": user_stopped,
        "proposals_after_user_stop": counts["proposals_after_user_stop"] if user_stopped else None,
    }


def compare(before_path: Path, after_path: Path) -> dict:
    try:
        before, after = read_snapshot(before_path), read_snapshot(after_path)
    except (BadZipFile, DecompressionError, EOFError, RuntimeError) as error:
        raise ValueError("Audit ZIP cannot be read or has an invalid CRC") from error
    new = interval(before, after)
    counts = summarize([ev for rows in before.events.values() for ev in rows], new)
    return {
        "schema": "localpilot-field-audit-v1",
        "scope": "JOURNAL_EVIDENCE_ONLY",
        "before_sha256": before.digest, "after_sha256": after.digest,
        "shared_baseline_segment": max(before.segments, default=None),
        "status": "INTERVAL_READ" if new else "NO_NEW_EVENTS",
        "legacy_logs_ignored": {"before": before.legacy_count, "after": after.legacy_count},
        "interval": counts,
        "dispatch_count_proven": False,
        "visual_receipts_correlated_to_gestures": False,
        "physical_gameplay_verified": False,
        "release_ready": False,
    }
