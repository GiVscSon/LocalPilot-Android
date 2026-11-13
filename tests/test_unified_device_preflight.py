"""Offline negative tests: one APK, one signer, no destructive device commands."""
import importlib.util
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

TOOL = Path(__file__).resolve().parents[1] / "tools" / "unified_device_preflight.py"
spec = importlib.util.spec_from_file_location("unified_device_preflight", TOOL)
mod = importlib.util.module_from_spec(spec)
spec.loader.exec_module(mod)
CERT = "a" * 64
OTHER = "b" * 64

class Preflight(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.apk = Path(self.temp.name, "one.apk")
        self.apk.write_bytes(b"candidate" * 200)
        self.old_version = 51
        self.new_version = 53
        self.old_cert = CERT
        self.new_cert = CERT
        self.pkg = mod.MAIN_PACKAGE
        self.test_only = False
        self.already_installed = True
        self.other_dev = False
        self.calls = []
        self.remote = "package:/data/app/org.localpilot.android/base.apk\n"

    def fake_run(self, argv):
        self.calls.append(argv)
        if argv[-1] == "get-state":
            return "device\n"
        if argv[-3:] == ["pm", "list", "packages"]:
            names = ["org.android.systemui"]
            if self.already_installed: names.append(self.pkg)
            if self.other_dev: names.append("org.localpilot.android.dev.install1081")
            return "".join("package:" + p + "\n" for p in names)
        if len(argv) >= 3 and argv[-3:-1] == ["pm", "path"]:
            return self.remote
        if "pull" in argv:
            Path(argv[-1]).write_bytes(b"installed" * 200)
            return ""
        if argv[1:3] == ["dump", "badging"]:
            old = Path(argv[-1]).name == "installed-base.apk"
            version = self.old_version if old else self.new_version
            name = "0.8.1-unified-test-only" if self.test_only else "0.8.1"
            return f"package: name='{self.pkg}' versionCode='{version}' versionName='{name}'\n"
        if len(argv) > 1 and argv[1] == "verify":
            old = Path(argv[-1]).name == "installed-base.apk"
            cert = self.old_cert if old else self.new_cert
            return "Verified using v2 scheme (APK Signature Scheme v2): true\n" + \
                   f"Signer #1 certificate SHA-256 digest: {cert}\n"
        raise AssertionError(argv)

    def checked(self, **options):
        with patch.object(mod, "run", side_effect=self.fake_run):
            return mod.preflight(self.apk, serial=options.get("serial", "device123"),
                                 expected_cert=options.get("cert", CERT),
                                 test_only=options.get("test_only", self.test_only))

    def test_matching_one_apk_upgrade_read_only(self):
        result = self.checked()
        self.assertEqual(result["status"], "SIGNATURE_MATCH_UPGRADE_POSSIBLE")
        self.assertFalse(result["performedInstall"])
        self.assertFalse(result["performedUninstall"])
        self.assertFalse(result["releaseReady"])
        self.assertFalse(result["upgradeDataPreservationProven"])
        self.assertFalse(any("install" in c or "uninstall" in c or "clear" in c for c in self.calls))

    def test_old_signer_mismatch_blocks(self):
        self.old_cert = OTHER
        with self.assertRaisesRegex(ValueError, "SIGNATURE_MISMATCH"): self.checked()
        self.assertFalse(any("uninstall" in c for c in self.calls))

    def test_candidate_pin_mismatch_blocks_before_adb(self):
        self.new_cert = OTHER
        with self.assertRaisesRegex(ValueError, "CANDIDATE_CERT_PIN_MISMATCH"): self.checked()
        self.assertFalse(any(c and c[0] == "adb" for c in self.calls))

    def test_equal_version_blocks(self):
        self.old_version = 53
        with self.assertRaisesRegex(ValueError, "VERSION_NOT_INCREASED"): self.checked()

    def test_downgrade_blocks(self):
        self.old_version = 54
        with self.assertRaisesRegex(ValueError, "VERSION_NOT_INCREASED"): self.checked()

    def test_fresh_install_is_not_upgrade_or_migration(self):
        self.already_installed = False
        result = self.checked()
        self.assertEqual(result["status"], "FRESH_INSTALL_NOT_AN_UPGRADE")
        self.assertIsNone(result["installedVersionCode"])
        self.assertFalse(result["upgradeDataPreservationProven"])

    def test_existing_dev_package_detected(self):
        self.other_dev = True
        result = self.checked()
        self.assertEqual(result["existingOtherDevPackages"],
                         ["org.localpilot.android.dev.install1081"])

    def test_test_apk_requires_explicit_opt_in(self):
        self.pkg = mod.TEST_PACKAGE
        self.test_only = True
        with self.assertRaisesRegex(ValueError, "Candidate package identity"):
            self.checked(test_only=False)
        result = self.checked(test_only=True)
        self.assertTrue(result["testOnly"])
        self.assertFalse(result["releaseReady"])

    def test_release_not_accepted_as_test(self):
        with self.assertRaisesRegex(ValueError, "Candidate package identity"):
            self.checked(test_only=True)

    def test_bad_serial(self):
        with self.assertRaisesRegex(ValueError, "serial"): self.checked(serial="")

    def test_cert_pin_required(self):
        with self.assertRaisesRegex(ValueError, "SHA-256"): self.checked(cert="missing")

    def test_no_base_path_fails_closed(self):
        self.remote = "package:/data/app/only-split.apk\n"
        with self.assertRaisesRegex(ValueError, "unavailable or ambiguous"): self.checked()

    def test_missing_package_inventory_not_treated_as_fresh(self):
        def broken(argv):
            if argv[-3:] == ["pm", "list", "packages"]: return ""
            return self.fake_run(argv)
        with patch.object(mod, "run", side_effect=broken):
            with self.assertRaisesRegex(ValueError, "inventory unavailable"):
                mod.preflight(self.apk, serial="device123", expected_cert=CERT)

    def test_bad_signer_refused(self):
        def bad_sig(argv):
            answer = self.fake_run(argv)
            if len(argv) > 1 and argv[1] == "verify":
                return answer.replace("v2 scheme", "v1 scheme")
            return answer
        with patch.object(mod, "run", side_effect=bad_sig):
            with self.assertRaisesRegex(ValueError, "verified v2/v3 signer"):
                mod.preflight(self.apk, serial="device123", expected_cert=CERT)

    def test_source_has_no_destructive_operations(self):
        source = TOOL.read_text()
        for verb in ('"install"', '"uninstall"', '"clear"'):
            self.assertNotIn(verb, source)

if __name__ == "__main__":
    unittest.main()
