"""Negative-only regression tests: never handle a real signing key in CI."""
from pathlib import Path
import importlib.util
import os
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location(
    "unified_local_sign", ROOT / "tools/unified_local_signed_candidate.py")
sign = importlib.util.module_from_spec(spec)
spec.loader.exec_module(sign)
SOURCE = (ROOT / "tools/unified_local_signed_candidate.py").read_text("utf-8")
SHA = "a" * 40
CERT = "b" * 64


class LocalSignedCandidateContract(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.path = Path(self.temp.name)
        self.key = self.path / "existing.p12"
        self.key.write_bytes(b"synthetic not a real key")
        self.out = self.path / "never-published"
        self.sdk = self.path / "android-sdk"

    def check(self, **kwargs):
        return sign.local_candidate(
            kwargs.get("key", self.key),
            kwargs.get("alias", "localpilot-release"),
            kwargs.get("cert", CERT),
            kwargs.get("sha", SHA),
            self.sdk,
            kwargs.get("out", self.out),
        )

    def test_ci_is_rejected_without_key_access(self):
        with patch.dict(os.environ, {"GITHUB_ACTIONS": "true"}):
            with self.assertRaisesRegex(ValueError, "Owner-local signing only"):
                self.check()

    def test_noninteractive_session_rejected(self):
        with patch.dict(os.environ, {"GITHUB_ACTIONS": "false"}), \
             patch.object(sign.sys.stdin, "isatty", return_value=False):
            with self.assertRaisesRegex(ValueError, "Interactive trusted terminal"):
                self.check()

    def test_invalid_certificate_pin_rejected(self):
        with patch.dict(os.environ, {"GITHUB_ACTIONS": "false"}), \
             patch.object(sign.sys.stdin, "isatty", return_value=True):
            with self.assertRaisesRegex(ValueError, "Pinned 64-hex"):
                self.check(cert="bad")

    def test_invalid_alias_rejected(self):
        with patch.dict(os.environ, {"GITHUB_ACTIONS": "false"}), \
             patch.object(sign.sys.stdin, "isatty", return_value=True):
            with self.assertRaisesRegex(ValueError, "Invalid PKCS12"):
                self.check(alias="bad; injection")

    def test_existing_private_key_is_mandatory(self):
        with patch.dict(os.environ, {"GITHUB_ACTIONS": "false"}), \
             patch.object(sign.sys.stdin, "isatty", return_value=True), \
             patch.object(sign, "source_check"):
            with self.assertRaisesRegex(ValueError, "Existing permanent PKCS12"):
                self.check(key=self.path / "not-exists.p12")

    def test_output_inside_checkout_rejected(self):
        with patch.dict(os.environ, {"GITHUB_ACTIONS": "false"}), \
             patch.object(sign.sys.stdin, "isatty", return_value=True), \
             patch.object(sign, "source_check"):
            with self.assertRaisesRegex(ValueError, "OUTSIDE"):
                self.check(out=ROOT / "dist" / "dangerous-output")

    def test_existing_candidate_never_overwritten(self):
        self.out.mkdir()
        with patch.dict(os.environ, {"GITHUB_ACTIONS": "false"}), \
             patch.object(sign.sys.stdin, "isatty", return_value=True), \
             patch.object(sign, "source_check"):
            with self.assertRaisesRegex(ValueError, "already exists"):
                self.check()

    def test_source_commit_and_dirty_tree_are_fail_closed(self):
        with patch.object(sign, "run", side_effect=[SHA, " M app/build.gradle"]):
            with self.assertRaisesRegex(ValueError, "Tracked source modifications"):
                sign.source_check(SHA)
        with patch.object(sign, "run", return_value="0" * 40):
            with self.assertRaisesRegex(ValueError, "pinned source"):
                sign.source_check(SHA)

    def test_source_cannot_forge_stable_or_change_signing_key(self):
        self.assertIn("unified_signed_release_gate.py", SOURCE)
        self.assertIn("UNVERIFIED-DO-NOT-INSTALL.apk", SOURCE)
        self.assertIn("releaseReady=false", SOURCE)
        self.assertIn("getpass.getpass", SOURCE)
        self.assertIn("env:LOCALPILOT_RELEASE_STORE_PASS", SOURCE)
        self.assertNotIn("gh release create", SOURCE)
        self.assertNotIn("keytool -genkeypair", SOURCE)
        self.assertNotIn('["adb"', SOURCE)
        self.assertNotIn("pm clear", SOURCE)
        self.assertNotIn("uninstall", SOURCE)

    def test_unsigned_release_is_not_counted_as_signed(self):
        self.assertIn(":app:assembleRelease", SOURCE)
        self.assertIn("zipalign", SOURCE)
        self.assertIn("apksigner", SOURCE)
        self.assertIn("source_check(source_sha)", SOURCE)
        self.assertIn("output.exists()", SOURCE)


if __name__ == "__main__":
    unittest.main()
