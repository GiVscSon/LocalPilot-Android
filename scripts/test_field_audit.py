"""Exercise the field CLI with exports in the existing Android wire format."""
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
import warnings
from zipfile import ZIP_DEFLATED, ZIP_STORED, ZipFile

OPERATOR = Path(__file__).with_name("localpilot.py")


def chain(*events):
    raw = b""
    prev = "0"
    for seq, (kind, epoch, request, metric) in enumerate(events, 1):
        row = f"{seq}|{kind}|{epoch}|{request}|{1000 + seq}|{metric}|{prev}".encode()
        prev = hashlib.sha256(row).hexdigest()
        raw += row + b"|" + prev.encode() + b"\n"
    return raw


def export(path, logs, *, update=None, extra=None, compression=ZIP_DEFLATED):
    count = sum(len(data.splitlines()) for data in logs.values())
    segments = sum(name.startswith("segment-") for name in logs)
    notes = [
        "LocalPilot audit export-v2", "Explicit local export; no upload.",
        f"status={'verified_journal_events' if count else 'no_journal_events'}",
        f"segment_count={segments}", f"event_count={count}",
    ]
    notes += [f"{name} bytes={len(data)} sha256={hashlib.sha256(data).hexdigest()}"
              for name, data in logs.items()]
    if update:
        update(notes)
    with ZipFile(path, "w", compression=compression) as z:
        for name, data in logs.items():
            z.writestr(name, data)
        z.writestr("metadata.txt", "\n".join(notes) + "\n")
        z.writestr("README.txt", "Android ACK is not proof of game motion.\n")
        if extra:
            with warnings.catch_warnings():
                warnings.simplefilter("ignore", UserWarning)
                z.writestr(*extra)


class FieldAuditTests(unittest.TestCase):
    def setUp(self):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.before = Path(directory.name, "before.zip")
        self.after = Path(directory.name, "after.zip")

    def call(self):
        return subprocess.run(
            [sys.executable, str(OPERATOR), "field-audit", "--before", str(self.before),
             "--after", str(self.after)], capture_output=True, text=True, timeout=10,
        )

    def report(self):
        result = self.call()
        self.assertEqual(result.returncode, 0, result.stderr)
        return json.loads(result.stdout)

    def rejected(self, message):
        result = self.call()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn(message, result.stderr)
        self.assertEqual(result.stdout, "")
        self.assertNotIn("Traceback", result.stderr)

    def test_new_events_only_and_late_ack_remains_transport_evidence(self):
        old = [("VERIFIED_GRID", 7, 0, 99), ("PROPOSED", 7, 30, 0)]
        export(self.before, {"segment-2.log": chain(*old)})
        export(self.after, {"segment-2.log": chain(*old,
            ("ANDROID_COMPLETED", 7, 30, 0),
            ("PROPOSED", 8, 31, 0), ("ANDROID_COMPLETED", 8, 31, 0),
            ("VERIFIED_2048", 8, 0, 0),
            ("PROPOSED", 8, 32, 0), ("STOP", 8, 0, 1),
            ("ANDROID_COMPLETED", 8, 32, 0),
            ("PROPOSED", 8, 33, 0), ("ANDROID_CANCELLED", 8, 33, 0),
            ("PROPOSED", 11, 34, 0), ("REJECTED", 11, 34, 0),
            ("PROPOSED", 12, 35, 0), ("ANDROID_COMPLETED", 13, 35, 0))})
        before_bytes, after_bytes = self.before.read_bytes(), self.after.read_bytes()
        result = self.report()
        self.assertEqual(result["before_sha256"], hashlib.sha256(before_bytes).hexdigest())
        self.assertEqual(result["after_sha256"], hashlib.sha256(after_bytes).hexdigest())
        rows = result["interval"]
        self.assertEqual(rows["events"], 13)
        self.assertEqual(rows["proposed"], 5)
        self.assertEqual(rows["android_completed"], 2)
        self.assertEqual(rows["android_cancelled"], 1)
        self.assertEqual(rows["android_rejected"], 1)
        self.assertEqual(rows["terminal_receipts_for_prior_proposals"], 1)
        self.assertEqual(rows["unmatched_terminal_receipts"], 1)
        self.assertEqual(rows["terminal_receipts_after_stop_in_same_epoch"], 1)
        self.assertEqual(rows["proposals_without_terminal_receipt"], 1)
        self.assertEqual(rows["visual_receipts"], {"VERIFIED_2048": 1})
        self.assertEqual(rows["stop_codes"], {"1": 1})
        self.assertEqual(rows["proposals_after_user_stop"], 3)
        self.assertFalse(result["visual_receipts_correlated_to_gestures"])
        self.assertFalse(result["dispatch_count_proven"])
        self.assertFalse(result["physical_gameplay_verified"])
        self.assertFalse(result["release_ready"])
        self.assertEqual(self.before.read_bytes(), before_bytes)
        self.assertEqual(self.after.read_bytes(), after_bytes)

    def test_android_ack_alone_has_no_visual_receipt_or_stop_observation(self):
        export(self.before, {})
        export(self.after, {"segment-0.log": chain(("PROPOSED", 4, 1, 0),
                                                   ("ANDROID_COMPLETED", 4, 1, 0))})
        data = self.report()["interval"]
        self.assertEqual(data["android_completed"], 1)
        self.assertEqual(data["visual_receipts"], {})
        self.assertIsNone(data["proposals_after_user_stop"])

    def test_rotation_keeps_anchor_and_ignores_legacy(self):
        old = chain(("PROPOSED", 4, 9, 0), ("ANDROID_COMPLETED", 4, 9, 0))
        anchor = chain(("PROPOSED", 4, 10, 0))
        legacy = chain(("VERIFIED_GRID", 7, 0, 100))
        export(self.before, {"segment-3.log": old, "segment-4.log": anchor, "legacy-v1.log": legacy})
        after = {f"segment-{n}.log": chain(("STOP", n, 0, 3)) for n in range(6, 12)}
        after.update({"segment-4.log": anchor,
                      "segment-5.log": chain(("ANDROID_COMPLETED", 4, 10, 0),
                                             ("PROPOSED", 4, 11, 0),
                                             ("ANDROID_COMPLETED", 4, 11, 0),
                                             ("VERIFIED_GRID", 4, 0, 1)),
                      "legacy-v1.log": legacy})
        export(self.after, after)
        data = self.report()
        self.assertEqual(data["shared_baseline_segment"], 4)
        self.assertEqual(data["legacy_logs_ignored"], {"before": 1, "after": 1})
        self.assertEqual(data["interval"]["terminal_receipts_for_prior_proposals"], 1)
        self.assertEqual(data["interval"]["visual_receipts"], {"VERIFIED_GRID": 1})

    def test_empty_export_is_explicit(self):
        export(self.before, {})
        export(self.after, {"legacy-v1.log": chain(("VERIFIED_GRID", 7, 0, 99))})
        data = self.report()
        self.assertEqual(data["status"], "NO_NEW_EVENTS")
        self.assertEqual(data["interval"]["visual_receipts"], {})

    def test_missing_baseline_refuses_plausible_rotated_archive(self):
        anchor = chain(("STOP", 5, 0, 3))
        export(self.before, {"segment-3.log": anchor})
        export(self.after, {"segment-4.log": anchor})
        self.rejected("Newest baseline segment is missing")
        export(self.before, {})
        self.rejected("No retained segment zero")

    def test_does_not_recount_replaced_or_reversed_history(self):
        old = chain(("STOP", 4, 0, 3))
        other = chain(("STOP", 5, 0, 3))
        export(self.before, {"segment-4.log": old})
        export(self.after, {"segment-4.log": other})
        self.rejected("append-only history")
        export(self.before, {"segment-4.log": chain(("STOP", 4, 0, 3), ("STOP", 4, 0, 1))})
        export(self.after, {"segment-4.log": old})
        self.rejected("append-only history")

    def test_discontinuous_segment_ids_rejected(self):
        anchor = chain(("STOP", 5, 0, 3))
        export(self.before, {})
        export(self.after, {"segment-0.log": anchor, "segment-2.log": anchor})
        self.rejected("segment gap")

    def test_zip_checksums_do_not_replace_log_chain_validation(self):
        archive = {"segment-0.log": chain(("STOP", 4, 0, 3)).replace(b"STOP", b"REJECTED")}
        export(self.before, {})
        export(self.after, archive)
        self.rejected("record checksum mismatch")
        export(self.after, {"segment-0.log": chain(("STOP", 4, 0, 3))[:-1]})
        self.rejected("Truncated journal log")

    def test_manifest_must_match_entries_and_counts(self):
        data = {"segment-0.log": chain(("STOP", 4, 0, 3))}
        export(self.before, {})
        export(self.after, data, update=lambda m: m.__setitem__(5, m[5][:-64] + "0" * 64))
        self.rejected("manifest size, checksum or membership mismatch")
        export(self.after, data, update=lambda m: m.__setitem__(4, "event_count=150"))
        self.rejected("manifest event or segment count mismatch")

    def test_duplicate_path_image_and_oversized_entry_rejected_without_extraction(self):
        export(self.before, {})
        for name in ("README.txt", "../parent", "game-frame.png"):
            with self.subTest(name=name):
                export(self.after, {}, extra=(name, b"payload"))
                self.rejected("Unexpected, duplicate or oversized")
        export(self.after, {"segment-0.log": b" " * 524_289})
        self.rejected("Unexpected, duplicate or oversized")

    def test_crc_failure_fails_closed(self):
        data = chain(("STOP", 4, 0, 3))
        export(self.before, {})
        export(self.after, {"segment-0.log": data}, compression=ZIP_STORED)
        self.after.write_bytes(self.after.read_bytes().replace(data, b"x" + data[1:], 1))
        self.rejected("Audit ZIP cannot be read")

    def test_duplicate_terminal_cannot_inflate_counts(self):
        export(self.before, {})
        export(self.after, {"segment-0.log": chain(("PROPOSED", 4, 1, 0),
                                                   ("ANDROID_COMPLETED", 4, 1, 0),
                                                   ("ANDROID_CANCELLED", 4, 1, 0))})
        self.rejected("Repeated Android terminal receipt")


if __name__ == "__main__":
    unittest.main()
