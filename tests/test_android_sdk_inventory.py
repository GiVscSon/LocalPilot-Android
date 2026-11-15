"""Regression tests for a stable and source-bound Android SDK installation."""
import importlib.util
from pathlib import Path
import tempfile
import unittest

SOURCE = Path(__file__).resolve().parents[1] / "tools/android_sdk_inventory.py"
spec = importlib.util.spec_from_file_location("sdk_inventory", SOURCE)
inventory = importlib.util.module_from_spec(spec)
spec.loader.exec_module(inventory)


class AndroidSdkInventoryTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        for package, minimum in inventory.PACKAGES.items():
            version = minimum or "1"
            p = self.root / package / "source.properties"
            p.parent.mkdir(parents=True, exist_ok=True)
            # NDK format contains spaces around the equals sign.
            p.write_text("Pkg.Desc = test\nPkg.Revision = " + version + "\n",
                         encoding="utf-8")
        for name in inventory.EXECUTABLES:
            p = self.root / name
            p.parent.mkdir(parents=True, exist_ok=True)
            p.write_text("#!/bin/sh\nexit 0\n")
            p.chmod(0o755)

    def test_matching_ci_toolchain_is_source_verified(self):
        result = inventory.inventory(self.root, require_ci=True)
        self.assertEqual("PASS", result["status"])
        self.assertFalse(result["releaseReady"])
        self.assertEqual("NOT_TESTED", result["checks"]["emulatorBoot"])
        self.assertEqual("NOT_TESTED", result["checks"]["physicalPoco"])

    def test_old_platform_tools_not_accepted(self):
        (self.root / "platform-tools/source.properties").write_text("Pkg.Revision=36.0.2\n")
        result = inventory.inventory(self.root, require_ci=True)
        self.assertEqual("FAIL", result["status"])
        self.assertTrue(any("platform-tools" in error for error in result["errors"]))

    def test_missing_api30_is_error(self):
        (self.root / "platforms/android-30/source.properties").unlink()
        result = inventory.inventory(self.root, require_ci=True)
        self.assertEqual("FAIL", result["status"])
        self.assertTrue(any("android-30" in error for error in result["errors"]))

    def test_renamed_fake_cli_does_not_pass(self):
        (self.root / "cmdline-tools/latest/bin/sdkmanager").chmod(0o644)
        result = inventory.inventory(self.root, require_ci=True)
        self.assertEqual("FAIL", result["status"])
        self.assertTrue(any("sdkmanager" in error for error in result["errors"]))

    def test_optional_emulator_not_falsely_claimed(self):
        result = inventory.inventory(self.root, require_ci=True)
        self.assertEqual("PASS", result["status"])
        self.assertEqual("UNVERIFIED", result["emulator"]["status"])
        self.assertEqual("NOT_TESTED", result["checks"]["emulatorBoot"])

    def test_emulator_requirement_fails_if_not_installed(self):
        result = inventory.inventory(self.root, require_ci=True, require_emulator=True)
        self.assertEqual("FAIL", result["status"])

    def test_emulator_verified_as_installed_but_not_booted(self):
        for pkg, version in (("emulator", "37.2.12"),
                             ("system-images/android-35/default/x86_64", "14")):
            p = self.root / pkg / "source.properties"
            p.parent.mkdir(parents=True, exist_ok=True)
            p.write_text("Pkg.Revision=" + version + "\n")
        binary = self.root / "emulator/emulator"
        binary.write_text("#!/bin/sh\n")
        binary.chmod(0o755)
        result = inventory.inventory(self.root, require_ci=True, require_emulator=True)
        self.assertEqual("PASS", result["status"])
        self.assertEqual("NOT_TESTED", result["checks"]["emulatorBoot"])

    def test_invalid_revision_is_not_accepted(self):
        (self.root / "platform-tools/source.properties").write_text(
            "Pkg.Revision = Canary\n")
        with self.assertRaisesRegex(ValueError, "Invalid package revision"):
            inventory.inventory(self.root, require_ci=True)


if __name__ == "__main__":
    unittest.main()
