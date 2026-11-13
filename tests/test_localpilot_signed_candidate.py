"""Offline, negative-case tests: one signing identity, no automatic release."""
import importlib.util
from pathlib import Path
import json
import tempfile
import unittest
from unittest.mock import patch
import zipfile

ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location("localpilot_signed_gate",ROOT/"tools/unified_signed_release_gate.py")
gate=importlib.util.module_from_spec(spec)
spec.loader.exec_module(gate)
CERT="a"*64
SHA="b"*40

def sdk(*args):
    if args[1:3]==("dump","badging"):
        return ("package: name='org.localpilot.android' versionCode='53' "
                "versionName='0.8.1'\nsdkVersion:'30'\ntargetSdkVersion:'35'\n"
                "launchable-activity: name='org.localpilot.android.MainActivity'\n")
    if args[1:3]==("dump","xmltree"):
        return "\n".join(gate.ACTIVITIES)
    if args[1]=="verify":
        return ("Verified using v2 scheme (APK Signature Scheme v2): true\n"
                f"Signer #1 certificate SHA-256 digest: {CERT}\n")
    raise AssertionError(args)

class SignedSingleApk(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.apk=Path(self.temp.name)/"one.apk"
        with zipfile.ZipFile(self.apk,"w") as z:
            for name in gate.ASSETS:
                z.writestr(name,json.dumps({"schema":"localpilot-source-v1","sourceSha":SHA}) if name=="assets/localpilot-source.json" else b"unit test")
            z.writestr("classes.dex", b"org/localpilot/android/ModelLabActivity"+
                       b"org/localpilot/gamelab/NesEmulatorActivity")
        self.patch=patch.object(gate,"sdk",side_effect=sdk)
        self.patch.start()
        self.addCleanup(self.patch.stop)
    def checked(self,**kwargs):
        return gate.verify(self.apk,"aapt","apksigner",kwargs.get("cert",CERT),
                           kwargs.get("sha",SHA),kwargs.get("previous"))
    def test_signed_apk_is_never_stable_by_ci(self):
        m=self.checked()
        self.assertFalse(m["releaseReady"])
        self.assertEqual(m["physicalAndroidTest"],"NOT_VERIFIED")
        self.assertEqual(m["package"],"org.localpilot.android")
    def test_invalid_pin_is_rejected(self):
        with self.assertRaisesRegex(ValueError,"certificate required"):
            self.checked(cert="bad")
    def test_mismatched_signer_rejected(self):
        with self.assertRaisesRegex(ValueError,"signing identity mismatch"):
            self.checked(cert="f"*64)
    def test_different_embedded_source_rejected(self):
        with self.assertRaisesRegex(ValueError,"embedded source SHA"):
            self.checked(sha="c"*40)
    def test_invalid_git_sha_rejected(self):
        with self.assertRaisesRegex(ValueError,"source SHA"):
            self.checked(sha="short")
    def test_old_version_not_upgraded_rejected(self):
        old=self.checked()
        with self.assertRaisesRegex(ValueError,"versionCode"):
            self.checked(previous=old)
    def test_old_signer_change_rejected(self):
        old=self.checked()
        old["signerSha256"]="f"*64
        with self.assertRaisesRegex(ValueError,"certificate changed"):
            self.checked(previous=old)
    def test_no_models_bundled(self):
        with zipfile.ZipFile(self.apk,"a") as z:
            z.writestr("assets/models/not-pinned.gguf",b"bad")
        with self.assertRaisesRegex(ValueError,"GGUF weights"):
            self.checked()
    def test_no_debug_fallback_or_auto_publish(self):
        workflow=(ROOT/".github/workflows/localpilot-unified-signed-candidate.yml").read_text()
        self.assertIn("if: github.event_name == 'workflow_dispatch'",workflow)
        self.assertIn("environment: android-signing",workflow)
        self.assertIn("No debug fallback".lower(),workflow.lower())
        self.assertIn("vars.LOCALPILOT_RELEASE_CERT_SHA256",workflow)
        self.assertIn("secrets.LOCALPILOT_RELEASE_KEYSTORE_B64",workflow)
        self.assertNotIn("gh release create",workflow)
        self.assertNotIn("contents: write",workflow)

if __name__=="__main__":
    unittest.main()
