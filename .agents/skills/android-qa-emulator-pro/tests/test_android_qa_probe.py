"""Android QA skill: reject unbooted AVD, missing SDK and fake device claims."""
import importlib.util
from pathlib import Path
import tempfile
from unittest import mock
import unittest

SOURCE = Path(__file__).resolve().parents[1] / "scripts/android_qa_probe.py"
spec = importlib.util.spec_from_file_location("qa", SOURCE)
qa = importlib.util.module_from_spec(spec)
spec.loader.exec_module(qa)


class HostTests(unittest.TestCase):
    def test_missing_sdk_not_misrepresented_as_android(self):
        with mock.patch.object(qa, "executable", return_value=None):
            out = qa.doctor({}, kvm=False)
        self.assertEqual("INSPECTED", out["status"])
        self.assertEqual("NOT_TESTED", out["androidBoot"])
        self.assertEqual("UNAVAILABLE", out["kvm"])
        self.assertFalse(out["releaseReady"])

    def test_required_sdk_rejects_missing_binaries(self):
        with mock.patch.object(qa, "executable", return_value=None):
            out = qa.doctor({}, require_sdk=True, kvm=False)
        self.assertEqual("FAIL", out["status"])
        self.assertIn("SDK_AND_ADB_REQUIRED", out["unmetRequirements"])

    def test_missing_kvm_blocks_required_acceleration(self):
        with mock.patch.object(qa, "executable", return_value="/fake/tool"):
            out = qa.doctor({}, require_kvm=True, kvm=False)
        self.assertEqual("FAIL", out["status"])
        self.assertIn("KVM_REQUIRED", out["unmetRequirements"])

    def test_sdk_revision_with_whitespace(self):
        with tempfile.TemporaryDirectory() as directory:
            folder = Path(directory)
            (folder / "source.properties").write_text("Pkg.Revision = 23.0\n")
            self.assertEqual("23.0", qa.revision(folder))

    def test_unknown_version_is_not_accepted(self):
        with tempfile.TemporaryDirectory() as directory:
            folder = Path(directory)
            (folder / "source.properties").write_text("Pkg.Revision=BAD\n")
            self.assertIsNone(qa.revision(folder))


class DeviceTests(unittest.TestCase):
    def mock_runner(self, *, state="device", boot="1", qemu="1", sdk="35"):
        def run(argv, timeout=12):
            if argv[1:] == ["devices", "-l"]:
                return True, "List of devices attached\nemulator-5554 " + state + " product:sdk\n"
            if len(argv) >= 6 and argv[-2] == "getprop":
                return True, {"sys.boot_completed": boot, "ro.kernel.qemu": qemu,
                              "ro.build.version.sdk": sdk, "ro.product.model": "Pixel 2",
                              "ro.product.cpu.abi": "x86_64"}.get(argv[-1], "")
            raise AssertionError(argv)
        return run

    def test_verified_android_emulator_but_not_app_pass(self):
        out = qa.device("emulator-5554", adb="adb", run=self.mock_runner(),
                        require_boot=True)
        self.assertEqual("BOOT_VERIFIED", out["status"])
        self.assertEqual("EMULATOR_ANDROID", out["environment"])
        self.assertEqual(35, out["api"])
        self.assertEqual("NOT_TESTED", out["appTest"])
        self.assertFalse(out["releaseReady"])

    def test_unbooted_device_must_fail(self):
        out = qa.device("emulator-5554", adb="adb",
                        run=self.mock_runner(boot="0"), require_boot=True)
        self.assertEqual("FAIL", out["status"])
        self.assertFalse(out["booted"])

    def test_device_offline_is_fail(self):
        out = qa.device("emulator-5554", adb="adb",
                        run=self.mock_runner(state="offline"), require_boot=True)
        self.assertEqual("FAIL", out["status"])

    def test_serial_and_qemu_disagreement_must_fail(self):
        out = qa.device("emulator-5554", adb="adb",
                        run=self.mock_runner(qemu="0"), require_boot=True)
        self.assertEqual("UNKNOWN", out["environment"])
        self.assertEqual("FAIL", out["status"])

    def test_invalid_api_is_fail(self):
        out = qa.device("emulator-5554", adb="adb",
                        run=self.mock_runner(sdk="invalid"), require_boot=True)
        self.assertEqual("FAIL", out["status"])
        self.assertIsNone(out["api"])

    def test_serial_injection_rejected(self):
        with self.assertRaisesRegex(ValueError, "SERIAL_INVALID_OR_AMBIGUOUS"):
            qa.device("emulator-5554;rm -rf /", adb="adb")

    def test_no_adb_but_device_requested(self):
        with mock.patch.object(qa, "executable", return_value=None):
            with self.assertRaisesRegex(ValueError, "ADB_MISSING"):
                qa.device("emulator-5554")


if __name__ == "__main__":
    unittest.main()
