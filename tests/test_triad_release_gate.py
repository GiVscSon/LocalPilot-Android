"""Host-only tests of the release safety gate. No SDK, model downloads or secrets."""
import importlib.util
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile

TARGET = Path(__file__).resolve().parents[1] / "tools" / "triad_release_gate.py"
spec = importlib.util.spec_from_file_location("triad_release_gate", TARGET)
gate = importlib.util.module_from_spec(spec)
spec.loader.exec_module(gate)
CERT = "a" * 64
SHA = "b" * 40


def fake_sdk(argv):
    name = Path(argv[-1]).name
    pkg = {"LocalPilot.apk": "org.localpilot.android", "GameLab.apk": "org.localpilot.gamelab",
           "ModelLab.apk": "org.localpilot.modellab"}[name]
    if argv[1:3] == ["dump", "badging"]:
        return f"package: name='{pkg}' versionCode='53' versionName='1.0.0'\n" \
               "sdkVersion:'30'\ntargetSdkVersion:'35'\n"
    if argv[1:3] == ["dump", "permissions"]:
        return "uses-permission: name='android.permission.INTERNET'"
    if argv[1] == "verify":
        return "Verified using v2 scheme (APK Signature Scheme v2): true\n" \
               f"Signer #1 certificate SHA-256 digest: {CERT}\n"
    raise AssertionError(argv)


class VerifyTriad(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.files = {}
        for label in gate.PACKAGES:
            path = Path(self.tmp.name, label + ".apk")
            self.files[label] = path
            self.make_apk(label)
        self.mock = patch.object(gate, "command", side_effect=fake_sdk)
        self.mock.start()
        self.addCleanup(self.mock.stop)

    def make_apk(self, label, drop=()):
        base = {"AndroidManifest.xml", "classes.dex"}
        if label != "ModelLab":
            base |= {"assets/nes/jsnes.min.js", "assets/nes/latched-buttons.js"}
        else:
            base |= {"lib/arm64-v8a/liblocalpilot_embedded.so"}
        with zipfile.ZipFile(self.files[label], "w") as z:
            for name in base - set(drop):
                z.writestr(name, b"data")

    def check(self, **kwargs):
        return gate.audit(self.files, "aapt", "apksigner", kwargs.get("certificate", CERT),
                          kwargs.get("sha", SHA), kwargs.get("previous"))

    def test_valid_manifest_has_no_release_claim(self):
        result = self.check()
        self.assertFalse(result["releaseReady"])
        self.assertEqual(result["physicalAndroidTest"], "NOT_VERIFIED")
        self.assertEqual(set(result["packages"]), set(gate.PACKAGES))

    def test_missing_native_code_is_blocker(self):
        self.make_apk("ModelLab", {"lib/arm64-v8a/liblocalpilot_embedded.so"})
        with self.assertRaisesRegex(ValueError, "native inference"):
            self.check()

    def test_missing_game_core_is_blocker(self):
        self.make_apk("GameLab", {"assets/nes/jsnes.min.js"})
        with self.assertRaisesRegex(ValueError, "NES assets"):
            self.check()

    def test_bad_certificate_blocked(self):
        with self.assertRaisesRegex(ValueError, "Signing key"):
            self.check(certificate="f" * 64)

    def test_bad_source_sha_blocked(self):
        with self.assertRaisesRegex(ValueError, "40-digit"):
            self.check(sha="short")

    def test_missing_package_blocked(self):
        self.files.pop("GameLab")
        with self.assertRaisesRegex(ValueError, "All three"):
            self.check()

    def test_old_version_must_increment(self):
        first = self.check()
        with self.assertRaisesRegex(ValueError, "versionCode must increase"):
            self.check(previous=first)

    def test_old_certificate_cannot_change(self):
        first = self.check()
        for old in first["packages"].values():
            old["versionCode"] = 52
        first["packages"]["GameLab"]["signerSha256"] = "d" * 64
        with self.assertRaisesRegex(ValueError, "certificate changed"):
            self.check(previous=first)

    def test_old_package_cannot_change(self):
        first = self.check()
        for old in first["packages"].values():
            old["versionCode"] = 52
        first["packages"]["LocalPilot"]["package"] = "org.other.app"
        with self.assertRaisesRegex(ValueError, "package changed"):
            self.check(previous=first)

    def test_v1_signature_alone_is_insufficient(self):
        def v1_only(args):
            result = fake_sdk(args)
            if args[1] == "verify":
                return result.replace("Verified using v2 scheme", "Verified using v1 scheme")
            return result
        with patch.object(gate, "command", side_effect=v1_only):
            with self.assertRaisesRegex(ValueError, "modern APK signature"):
                self.check()

    def test_model_cannot_have_unauthorized_permissions(self):
        def extra_permission(args):
            result = fake_sdk(args)
            if args[1:3] == ["dump", "permissions"]:
                return result + "\nuses-permission: name='android.permission.SYSTEM_ALERT_WINDOW'"
            return result
        with patch.object(gate, "command", side_effect=extra_permission):
            with self.assertRaisesRegex(ValueError, "game-control"):
                self.check()

    def test_model_weights_must_not_be_bundled(self):
        with zipfile.ZipFile(self.files["ModelLab"], "a") as z:
            z.writestr("assets/models/not-expected.gguf", "fake")
        with self.assertRaisesRegex(ValueError, "must not bundle"):
            self.check()


if __name__ == "__main__":
    unittest.main()