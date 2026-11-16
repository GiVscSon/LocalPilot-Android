"""Offline, negative-case tests: one signing identity, no automatic release."""
import importlib.util
from pathlib import Path
import json
import tempfile
import shutil
import subprocess
import sys
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
        if Path(args[-1]).name=="previous.apk":
            return ("package: name='org.localpilot.android' versionCode='52' versionName='0.8.0'\nsdkVersion:'30'\ntargetSdkVersion:'35'\nlaunchable-activity: name='org.localpilot.android.MainActivity'\n")
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
            for name in gate.ASSETS-{"classes.dex"}:
                z.writestr(name,json.dumps({"schema":"localpilot-source-v1","sourceSha":SHA}) if name=="assets/localpilot-source.json" else b"unit test")
            z.writestr("classes.dex", b"org/localpilot/android/ModelLabActivity"+
                       b"org/localpilot/gamelab/NesEmulatorActivity")
        self.patch=patch.object(gate,"sdk",side_effect=sdk)
        self.mock=self.patch.start()
        self.addCleanup(self.patch.stop)
    def checked(self,**kwargs):
        return gate.verify(self.apk,"aapt","apksigner",kwargs.get("cert",CERT),
                           kwargs.get("sha",SHA),kwargs.get("previous"),kwargs.get("previous_apk"))
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
            self.checked(previous=old,previous_apk=self.apk)
    def test_old_signer_change_rejected(self):
        old=self.checked()
        old["signerSha256"]="f"*64
        with self.assertRaisesRegex(ValueError,"certificate changed"):
            self.checked(previous=old,previous_apk=self.apk)
    def previous(self):
        path=Path(self.temp.name)/"previous.apk"
        shutil.copyfile(self.apk,path)
        with zipfile.ZipFile(path,"a") as z:
            z.comment=b"previous contract fixture, not a signed APK"
        manifest=gate.verify(path,"aapt","apksigner",CERT,SHA)
        return path,manifest
    def test_actual_predecessor_identity_required(self):
        old=self.checked()
        for options in ({"previous":old},{"previous_apk":self.apk}):
            self.mock.reset_mock()
            with self.subTest(options=options),self.assertRaisesRegex(ValueError,"must be supplied together"):
                self.checked(**options)
            self.mock.assert_not_called()
    def test_matching_pair_proves_only_static_identity(self):
        path,manifest=self.previous()
        result=self.checked(previous=manifest,previous_apk=path)
        evidence=result["upgradeCompatibility"]
        self.assertEqual(evidence["status"],"APK_PAIR_IDENTITY_VERSION_PASS")
        self.assertEqual(evidence["previousVersionCode"],52)
        self.assertEqual(evidence["currentVersionCode"],53)
        self.assertEqual(evidence["previousApkSha256"],manifest["apkSha256"])
        self.assertEqual(evidence["deviceInstall"],"NOT_TESTED")
        self.assertEqual(result["upgradeDataPreservation"],"NOT_VERIFIED")
        self.assertFalse(result["releaseReady"])
    def test_false_prior_version_receipt_rejected(self):
        old=self.checked()
        old["versionCode"]=52
        with self.assertRaisesRegex(ValueError,"Prior manifest versionCode differs"):
            self.checked(previous=old,previous_apk=self.apk)
    def test_prior_receipt_fields_bound_to_actual_apk(self):
        path,manifest=self.previous()
        for field,value in (("apkSha256","f"*64),("apkBytes",True),("versionCode",True),
                            ("versionName","invented")):
            altered=dict(manifest);altered[field]=value
            with self.subTest(field=field),self.assertRaisesRegex(ValueError,"Prior manifest "+field+" differs"):
                self.checked(previous=altered,previous_apk=path)
    def test_changed_predecessor_bytes_rejected(self):
        path,manifest=self.previous()
        with zipfile.ZipFile(path,"a") as z:z.comment=b"modified predecessor bytes"
        with self.assertRaisesRegex(ValueError,"Prior manifest apkSha256 differs"):
            self.checked(previous=manifest,previous_apk=path)
    def test_no_upgrade_requested_is_explicit(self):
        self.assertEqual(self.checked()["upgradeCompatibility"],{"status":"NOT_EVALUATED"})
    def test_null_cli_receipt_does_not_silently_skip_upgrade(self):
        previous=Path(self.temp.name)/"null.json";previous.write_text("null")
        output=Path(self.temp.name)/"must-not-pass.json"
        result=subprocess.run([sys.executable,str(ROOT/"tools/unified_signed_release_gate.py"),
            "--apk",str(self.apk),"--aapt","SDK_MUST_NOT_RUN","--apksigner","SDK_MUST_NOT_RUN",
            "--cert",CERT,"--source-sha",SHA,"--previous",str(previous),
            "--previous-apk",str(self.apk),"--output",str(output)],capture_output=True,text=True,timeout=10)
        self.assertNotEqual(result.returncode,0)
        self.assertIn("Previous manifest must be a JSON object",result.stderr)
        self.assertFalse(output.exists())
    def test_no_models_bundled(self):
        with zipfile.ZipFile(self.apk,"a") as z:
            z.writestr("assets/models/not-pinned.gguf",b"bad")
        with self.assertRaisesRegex(ValueError,"GGUF weights"):
            self.checked()
    def test_no_debug_fallback_or_auto_publish(self):
        workflow=(ROOT/".github/workflows/localpilot-unified-release-dispatch.yml").read_text()
        self.assertIn("if: github.event_name == 'workflow_dispatch'",workflow)
        self.assertIn("environment: android-signing",workflow)
        self.assertIn("No debug fallback".lower(),workflow.lower())
        self.assertIn("vars.LOCALPILOT_RELEASE_CERT_SHA256",workflow)
        self.assertIn("secrets.LOCALPILOT_RELEASE_KEYSTORE_B64",workflow)
        signing=workflow.split("\n  signed-candidate:\n",1)[1]
        self.assertNotIn("gh release create",signing)
        self.assertNotIn("contents: write",signing)

if __name__=="__main__":
    unittest.main()
