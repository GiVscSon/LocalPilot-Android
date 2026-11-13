#!/usr/bin/env python3
"""Synthetic regressions for APK membership and provenance. Not Android tests."""
import json
import tempfile
import unittest
import warnings
from pathlib import Path
from zipfile import ZIP_DEFLATED, ZIP_STORED, ZipFile

from tools.verify_harness_apk import AuditError, inspect_apk

SHA = "a" * 40
DEX = b"dex\n035\x00" + b"\x00" * 72

BASE = {
    "AndroidManifest.xml": b"binary-manifest-placeholder",
    "resources.arsc": b"binary-resource-placeholder",
    "classes.dex": DEX,
    "assets/nes/index.html": b"<html><title>NES</title></html>",
    "assets/nes/jsnes.min.js": b"jsnes",
    "assets/nes/latched-buttons.js": b"buttons",
    "assets/localpilot-source.json": json.dumps(
        {"schema": "localpilot-source-v1", "sourceSha": SHA}
    ).encode("utf-8"),
}


class ApkAuditTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        self.apks = 0

    def make_apk(self, entries=None, *, duplicates=()):
        self.apks += 1
        path = self.root / f"candidate-{self.apks}.apk"
        values = dict(BASE) if entries is None else entries
        with warnings.catch_warnings():
            warnings.simplefilter("ignore", UserWarning)
            with ZipFile(path, "w") as apk:
                for name, data in values.items():
                    apk.writestr(name, data, compress_type=ZIP_STORED)
                for name in duplicates:
                    apk.writestr(name, values[name], compress_type=ZIP_DEFLATED)
        return path

    def test_valid_exact_provenance_and_nes(self):
        r = inspect_apk(self.make_apk(), expected_source_sha=SHA)
        self.assertEqual("PACKAGING_PASS", r["status"])
        self.assertEqual(1, r["dexCount"])
        self.assertTrue(r["nesAssetsPresent"])
        self.assertEqual(SHA, r["sourceSha"])
        self.assertIn("NO_DEVICE_OR_GPU_PROOF", r["limits"])

    def test_multidex_reported_not_incorrectly_deleted(self):
        e = dict(BASE, **{"classes2.dex": DEX})
        r = inspect_apk(self.make_apk(e), expected_source_sha=SHA)
        self.assertEqual(["classes.dex", "classes2.dex"], r["dexFiles"])
        self.assertIn("MULTIDEX_PRESENT_VERIFY_DEX_METHOD_COUNTS_BEFORE_REMOVAL", r["warnings"])
        with self.assertRaisesRegex(AuditError, "UNEXPECTED_MULTIDEX"):
            inspect_apk(self.make_apk(e), strict_one_dex=True)

    def test_reject_backup_archive(self):
        e = dict(BASE, **{"assets/adaptive-engine-v1/model.zip.bak": b"old"})
        with self.assertRaisesRegex(AuditError, "BACKUP_FILE_IN_APK"):
            inspect_apk(self.make_apk(e))

    def test_reject_missing_nes_runtime(self):
        e = dict(BASE)
        del e["assets/nes/jsnes.min.js"]
        with self.assertRaisesRegex(AuditError, "REQUIRED_ENTRY_MISSING"):
            inspect_apk(self.make_apk(e))

    def test_reject_wrong_sha(self):
        with self.assertRaisesRegex(AuditError, "BUILD_SOURCE_SHA_MISMATCH"):
            inspect_apk(self.make_apk(), expected_source_sha="b" * 40)

    def test_reject_invalid_sha_argument(self):
        with self.assertRaisesRegex(AuditError, "EXPECTED_SOURCE_SHA_INVALID"):
            inspect_apk(self.make_apk(), expected_source_sha="1234")

    def test_reject_missing_provenance(self):
        e = dict(BASE)
        del e["assets/localpilot-source.json"]
        with self.assertRaisesRegex(AuditError, "BUILD_SOURCE_PROVENANCE_MISSING"):
            inspect_apk(self.make_apk(e))

    def test_reject_source_schema_mismatch(self):
        e = dict(BASE)
        e["assets/localpilot-source.json"] = b'{"schema":"fake","sourceSha":"' + SHA.encode() + b'"}'
        with self.assertRaisesRegex(AuditError, "BUILD_SOURCE_SCHEMA_INVALID"):
            inspect_apk(self.make_apk(e))

    def test_reject_duplicate_member(self):
        with self.assertRaisesRegex(AuditError, "DUPLICATE_ZIP_MEMBER"):
            inspect_apk(self.make_apk(duplicates=["classes.dex"]))

    def test_reject_traversal_and_windows_separator(self):
        for name in ("assets/../../bad", "assets\\bad"):
            with self.subTest(name=name):
                e = dict(BASE, **{name: b"bad"})
                with self.assertRaisesRegex(AuditError, "UNSAFE_ZIP_MEMBER"):
                    inspect_apk(self.make_apk(e))

    def test_reject_dex_magic_and_order_gap(self):
        e = dict(BASE, **{"classes.dex": b"garbage data"})
        with self.assertRaisesRegex(AuditError, "INVALID_DEX_MAGIC"):
            inspect_apk(self.make_apk(e))
        e = dict(BASE, **{"classes3.dex": DEX})
        with self.assertRaisesRegex(AuditError, "MISSING_DEX_SEQUENCE"):
            inspect_apk(self.make_apk(e))

    def test_reject_invalid_native_binary(self):
        e = dict(BASE, **{"lib/arm64-v8a/liblocalpilot_embedded.so": b"not-ELF"})
        with self.assertRaisesRegex(AuditError, "INVALID_NATIVE_LIBRARY"):
            inspect_apk(self.make_apk(e))

    def test_valid_native_binary_and_delta(self):
        e = dict(BASE, **{"lib/arm64-v8a/liblocalpilot_embedded.so": b"\x7fELF" + b"abc"})
        base = self.make_apk()
        current = self.make_apk(e)
        r = inspect_apk(current, baseline_apk=base)
        self.assertEqual(r["apkDeltaBytes"], current.stat().st_size - base.stat().st_size)
        self.assertEqual(["lib/arm64-v8a/liblocalpilot_embedded.so"], r["nativeLibraries"])

    def test_reject_truncated_zip(self):
        path = self.root / "truncated.apk"
        path.write_bytes(b"not a zip archive")
        with self.assertRaisesRegex(AuditError, "APK_ZIP_UNREADABLE"):
            inspect_apk(path)

    def test_compressed_model_reports_warning(self):
        # Byte-level compression is not model compatibility or inference proof.
        path = self.root / "candidate-compressed.apk"
        with ZipFile(path, "w") as apk:
            for name, data in BASE.items():
                apk.writestr(name, data)
            apk.writestr("assets/models/test.gguf", b"GGUF" + b"x" * 1024,
                         compress_type=ZIP_DEFLATED)
        result = inspect_apk(path)
        self.assertTrue(any(s.startswith("GGUF_COMPRESSED") for s in result["warnings"]))


if __name__ == "__main__":
    unittest.main()
