"""Negative regressions for strictly local, non-authoritative field audit parsing."""
import hashlib
import tempfile
import unittest
import warnings
from pathlib import Path
from zipfile import ZIP_DEFLATED, ZipFile

import field_audit_gate as gate


def rows(events):
    previous = "0"
    result = []
    for sequence, (event, epoch) in enumerate(events, 1):
        body = f"{sequence}|{event}|{epoch}|{sequence}|{1000+sequence}|0|{previous}"
        previous = hashlib.sha256(body.encode()).hexdigest()
        result.append(body + "|" + previous + "\n")
    return "".join(result).encode()


def make_export(path, sources, extra=None, event_override=None):
    extra = extra or {}
    segment_count = sum(bool(gate.LOG_NAME.fullmatch(name)) for name in sources)
    event_count = sum(source.count(b"\n") for source in sources.values())
    metadata = ("LocalPilot audit export-v2\nExplicit local export; no upload.\n"
                + "status=" + ("verified_journal_events" if event_count else "no_journal_events")
                + "\nsegment_count=" + str(segment_count) + "\nevent_count="
                + str(event_count if event_override is None else event_override) + "\n")
    for name, source in sources.items():
        metadata += (f"{name} bytes={len(source)} sha256="
                     + hashlib.sha256(source).hexdigest() + "\n")
    with ZipFile(path, "w", compression=ZIP_DEFLATED) as archive:
        archive.writestr("metadata.txt", metadata.encode())
        archive.writestr("README.txt", b"Local metadata only")
        for name, source in sources.items():
            archive.writestr(name, source)
        for name, source in extra.items():
            archive.writestr(name, source)


class FieldAuditGateTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.path = Path(self.directory.name) / "audit.zip"

    def test_real_journal_format_and_same_epoch_correlation(self):
        raw = rows([("PROPOSED", 12), ("ANDROID_COMPLETED", 12),
                    ("VERIFIED_BUBBLE", 12), ("STOP", 12)])
        legacy = rows([("VERIFIED_2048", 3)])
        make_export(self.path, {"segment-0.log": raw, "legacy-v1.log": legacy})
        report = gate.inspect(self.path)
        self.assertEqual(report["integrity"], "PASS")
        self.assertEqual(report["current_events"], 4)
        self.assertEqual(report["verified_current"]["VERIFIED_BUBBLE"], 1)
        self.assertEqual(report["verified_current"]["VERIFIED_2048"], 0)
        self.assertEqual(report["legacy_events_excluded_from_current"], 1)
        self.assertEqual(report["same_epoch_ack_correlations_not_proof"]["VERIFIED_BUBBLE"], 1)
        self.assertFalse(report["real_market_game_verified"])
        self.assertFalse(report["release_ready"])

    def test_android_ack_alone_never_counts_as_win(self):
        make_export(self.path, {"segment-0.log": rows([
            ("PROPOSED", 9), ("ANDROID_COMPLETED", 9)])})
        report = gate.inspect(self.path)
        self.assertTrue(all(n == 0 for n in report["verified_current"].values()))
        self.assertFalse(report["physical_device_verified"])

    def test_verified_event_without_ack_does_not_gain_correlation(self):
        make_export(self.path, {"segment-0.log": rows([
            ("VERIFIED_GRID", 5), ("STOP", 5), ("ANDROID_COMPLETED", 5),
            ("VERIFIED_GRID", 5), ("VERIFIED_ARCADE", 6)])})
        report = gate.inspect(self.path)
        self.assertEqual(report["verified_current"]["VERIFIED_GRID"], 2)
        self.assertEqual(report["same_epoch_ack_correlations_not_proof"]["VERIFIED_GRID"], 1)
        self.assertNotIn("VERIFIED_ARCADE", report["same_epoch_ack_correlations_not_proof"])

    def test_no_journal_events_is_not_success(self):
        make_export(self.path, {"segment-0.log": b""})
        report = gate.inspect(self.path)
        self.assertEqual(report["current_events"], 0)
        self.assertFalse(report["release_ready"])

    def test_tampered_line_even_with_updated_metadata_is_rejected(self):
        raw = rows([("ANDROID_COMPLETED", 4)]).replace(
            b"ANDROID_COMPLETED", b"VERIFIED_2048")
        make_export(self.path, {"segment-0.log": raw})
        with self.assertRaisesRegex(gate.InvalidAudit, "checksum"):
            gate.inspect(self.path)

    def test_segment_duplicate_numeric_identity_rejected(self):
        make_export(self.path, {"segment-0.log": b"", "segment-00.log": b""})
        with self.assertRaisesRegex(gate.InvalidAudit, "unexpected ZIP member"):
            gate.inspect(self.path)

    def test_disallowed_member_and_traversal_rejected(self):
        make_export(self.path, {"segment-0.log": b""},
                    extra={"../secret.txt": b"secret"})
        with self.assertRaisesRegex(gate.InvalidAudit, "unexpected ZIP member"):
            gate.inspect(self.path)

    def test_metadata_count_mismatch_rejected(self):
        make_export(self.path, {"segment-0.log": rows([("STOP", 7)])},
                    event_override=99)
        with self.assertRaisesRegex(gate.InvalidAudit, "event count mismatch"):
            gate.inspect(self.path)

    def test_duplicate_zip_name_rejected(self):
        make_export(self.path, {"segment-0.log": b""})
        with warnings.catch_warnings():
            warnings.simplefilter("ignore", UserWarning)
            with ZipFile(self.path, "a") as archive:
                archive.writestr("metadata.txt", b"duplicate")
        with self.assertRaisesRegex(gate.InvalidAudit, "duplicate ZIP"):
            gate.inspect(self.path)


if __name__ == "__main__":
    unittest.main()
