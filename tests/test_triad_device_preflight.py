"""No-phone unit tests for non-destructive real-device update audit."""
import importlib.util
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

TOOL = Path(__file__).resolve().parents[1] / "tools" / "triad_device_preflight.py"
spec = importlib.util.spec_from_file_location("triad_device_preflight", TOOL)
mod = importlib.util.module_from_spec(spec)
spec.loader.exec_module(mod)
SIGNER = "c" * 64
PKGS = mod.PACKAGES


class CheckDeviceUpgrade(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.paths = {}
        for name in PKGS:
            target = Path(self.tmp.name, name + ".apk")
            target.write_bytes(b"candidate" * 300)
            self.paths[name] = target
        self.remote = set(PKGS)
        self.old_cert = SIGNER
        self.old_ver = 4
        self.new_ver = 5
        self.debug = False
        self.calls = []

    def fake_cmd(self, args, **kwargs):
        self.calls.append(args)
        if args[-1] == "get-state":
            return "device\n"
        if "pm" in args and "path" in args:
            package = args[-1]
            if package == "org.localpilot.android.dev.install1081":
                return "package:/data/app/dev/base.apk\n" if self.debug else ""
            return f"package:/data/app/{package}/base.apk\n" if package in (PKGS[x] for x in self.remote) else ""
        if "pull" in args:
            Path(args[-1]).write_bytes(b"installed" * 400)
            return ""
        if args[1:3] == ["dump", "badging"]:
            name = Path(args[-1]).name
            label = next(x for x in PKGS if x.lower() in name.lower())
            old = name.startswith("installed-")
            return (f"package: name='{PKGS[label]}' versionCode='{self.old_ver if old else self.new_ver}' versionName='1.0'\n"
                    "sdkVersion:'30'\ntargetSdkVersion:'35'\n")
        if args[1] == "verify":
            old = Path(args[-1]).name.startswith("installed-")
            return "Verified using v2 scheme (APK Signature Scheme v2): true\n" + \
                   f"Signer #1 certificate SHA-256 digest: {self.old_cert if old else SIGNER}\n"
        raise AssertionError(args)

    def run_audit(self):
        with patch.object(mod, "cmd", side_effect=self.fake_cmd):
            return mod.check(self.paths, serial="device123")

    def test_matching_upgrade_is_read_only(self):
        result = self.run_audit()
        self.assertFalse(result["releaseReady"])
        self.assertFalse(result["performedInstall"])
        self.assertFalse(result["dataPreservationProven"])
        self.assertEqual(len(result["packages"]), 3)
        self.assertTrue(all(x["status"] == "SIGNATURE_MATCH_UPGRADE_POSSIBLE" for x in result["packages"].values()))
        self.assertFalse(any("install" in a or "uninstall" in a or "clear" in a for a in self.calls))

    def test_signature_mismatch_stops_without_uninstall(self):
        self.old_cert = "f" * 64
        with self.assertRaisesRegex(ValueError, "SIGNATURE_MISMATCH"):
            self.run_audit()
        self.assertFalse(any("uninstall" in a for a in self.calls))

    def test_downgrade_rejected(self):
        self.old_ver = 6
        with self.assertRaisesRegex(ValueError, "VERSION_NOT_INCREASED"):
            self.run_audit()

    def test_equal_version_rejected(self):
        self.old_ver = 5
        with self.assertRaisesRegex(ValueError, "VERSION_NOT_INCREASED"):
            self.run_audit()

    def test_fresh_install_is_not_equated_with_migration(self):
        self.remote.clear()
        result = self.run_audit()
        self.assertTrue(all(x["status"] == "NEW_PACKAGE_NO_EXISTING_DATA" for x in result["packages"].values()))

    def test_existing_dev_package_warning(self):
        self.debug = True
        result = self.run_audit()
        self.assertTrue(result["existingDevInstall"])
        self.assertIn("will not migrate", result["warning"])

    def test_missing_one_candidate_rejected(self):
        self.paths.pop("ModelLab")
        with self.assertRaisesRegex(ValueError, "All three"):
            self.run_audit()

    def test_serial_required(self):
        with patch.object(mod, "cmd", side_effect=self.fake_cmd):
            with self.assertRaisesRegex(ValueError, "serial"):
                mod.check(self.paths, serial="")

    def test_unavailable_device_rejected(self):
        with patch.object(mod, "cmd", return_value="offline\n"):
            with self.assertRaisesRegex(ValueError, "not ready"):
                mod.check(self.paths, serial="device123")

    def test_nonexistent_package_is_not_error(self):
        with patch.object(mod, "cmd", side_effect=[""]):
            self.assertIsNone(mod.installed_base_path("adb", "device123", "nothing"))

    def test_ambiguous_installed_base_is_rejected(self):
        with patch.object(mod, "cmd", return_value="package:/one/base.apk\npackage:/two/base.apk\n"):
            with self.assertRaisesRegex(ValueError, "ambiguous"):
                mod.installed_base_path("adb", "device123", "anything")

    def test_signatures_must_be_modern(self):
        def bad_sig(a, **k):
            r = self.fake_cmd(a, **k)
            if len(a) > 1 and a[1] == "verify":
                return r.replace("v2 scheme", "v1 scheme")
            return r
        with patch.object(mod, "cmd", side_effect=bad_sig):
            with self.assertRaisesRegex(ValueError, "modern signature"):
                mod.check(self.paths, serial="device123")


if __name__ == "__main__":
    unittest.main()