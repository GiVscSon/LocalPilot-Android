"""Release regressions: signing identity, versionCode and package must survive updates.

No SDK, device, Android input or private signing keys are used in these tests.
"""
import contextlib
import io
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch
import zipfile

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))
import apk_upgrade_preflight as gate


def meta(**changes):
    base = {
        "package": "org.localpilot.android.dev.install1081",
        "version_code": 53,
        "version_name": "0.8.1-dev-install1081",
        "signer_sha256": "a" * 64,
        "min_sdk": 30,
        "apk_sha256": "0" * 64,
    }
    base.update(changes)
    return base


class UpgradePreflightTests(unittest.TestCase):
    def test_valid_samesigner_incremented_update(self):
        report = gate.compare(meta(), meta(version_code=54, apk_sha256="1" * 64))
        self.assertTrue(report["compatible_update"])
        self.assertEqual(report["reasons"], [])
        self.assertFalse(report["public_release_ready"])
        self.assertTrue(report["no_install_attempted"])

    def test_reject_random_ci_debug_signature(self):
        report = gate.compare(meta(), meta(version_code=54, signer_sha256="b" * 64,
                                            apk_sha256="1" * 64))
        self.assertFalse(report["compatible_update"])
        self.assertIn("SIGNER_CHANGED", report["reasons"])

    def test_reject_package_change_even_same_cert(self):
        report = gate.compare(meta(), meta(package="org.localpilot.android",
                                            version_code=54, apk_sha256="1" * 64))
        self.assertIn("PACKAGE_CHANGED", report["reasons"])

    def test_reject_downgrade_or_equal_version_code(self):
        for code in (52, 53):
            with self.subTest(code=code):
                report = gate.compare(meta(), meta(version_code=code,
                                                    apk_sha256="1" * 64))
                self.assertIn("VERSION_CODE_NOT_INCREMENTED", report["reasons"])

    def test_reject_identical_input(self):
        report = gate.compare(meta(), meta())
        self.assertFalse(report["compatible_update"])
        self.assertIn("IDENTICAL_APK", report["reasons"])

    def test_reject_new_android_sdk_requirement(self):
        report = gate.compare(meta(), meta(version_code=54, min_sdk=34,
                                            apk_sha256="1" * 64))
        self.assertIn("MIN_SDK_INCREASED", report["reasons"])

    def test_signed_apk_metadata_is_read_only(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "candidate.apk"
            with zipfile.ZipFile(path, "w") as archive:
                archive.writestr("AndroidManifest.xml", b"fixture")
                archive.writestr("classes.dex", b"dex")
            badge = ("package: name='org.localpilot.android.dev.install1081' "
                     "versionCode='54' versionName='0.8.2-dev-install1081'\n"
                     "sdkVersion:'30'\n")
            sign = ("Verified using v2 scheme (APK Signature Scheme v2): true\n"
                    "Signer #1 certificate SHA-256 digest: " + "A" * 64 + "\n")
            with patch.object(gate.subprocess, "check_output",
                              side_effect=[badge, sign]) as mocked:
                result = gate.inspect(path, "/nonexistent-sdk")
            self.assertEqual(result["version_code"], 54)
            self.assertEqual(result["signer_sha256"], "a" * 64)
            self.assertEqual(result["min_sdk"], 30)
            self.assertEqual(mocked.call_count, 2)

    def test_unsigned_apk_must_not_pass_even_when_zip_valid(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "unsigned.apk"
            with zipfile.ZipFile(path, "w") as archive:
                archive.writestr("AndroidManifest.xml", b"fixture")
                archive.writestr("classes.dex", b"dex")
            badge = ("package: name='org.localpilot.android.dev.install1081' "
                     "versionCode='54' versionName='candidate'\n")
            with patch.object(gate.subprocess, "check_output",
                              side_effect=[badge, "Signer #1 certificate SHA-256 digest: " +
                                           "a" * 64 + "\n"]):
                with self.assertRaisesRegex(gate.InvalidApk, "Signature Scheme"):
                    gate.inspect(path, "/nonexistent-sdk")

    def test_signature_command_failure_is_not_suppressed(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "damaged.apk"
            with zipfile.ZipFile(path, "w") as archive:
                archive.writestr("AndroidManifest.xml", b"fixture")
                archive.writestr("classes.dex", b"dex")
            badge = ("package: name='org.localpilot.android.dev.install1081' "
                     "versionCode='54' versionName='candidate'\n")
            with patch.object(gate.subprocess, "check_output",
                              side_effect=[badge,
                                           subprocess.CalledProcessError(1, "apksigner")]):
                with self.assertRaisesRegex(gate.InvalidApk, "signature verification failed"):
                    gate.inspect(path, "/nonexistent-sdk")

    def test_corrupt_archive_is_rejected_before_sdk(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "fake.apk"
            path.write_bytes(b"not-an-apk")
            with self.assertRaisesRegex(gate.InvalidApk, "Invalid APK"):
                gate.inspect(path, "/nonexistent-sdk")

    def test_cli_returns_failure_without_install_or_delete(self):
        with patch.object(gate, "inspect", side_effect=[
                meta(), meta(version_code=54, signer_sha256="b" * 64,
                             apk_sha256="1" * 64)]):
            with contextlib.redirect_stdout(io.StringIO()):
                result = gate.main(["--previous", "previous.apk",
                                    "--candidate", "candidate.apk"])
        self.assertEqual(result, 1)


if __name__ == "__main__":
    unittest.main()
