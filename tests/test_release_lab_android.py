"""Host-only ReleaseLab fail-closed gates; no SDK, AVD, or physical device needed."""
import importlib.util
import json
from pathlib import Path
import struct
import tempfile
import unittest
from unittest.mock import patch
import zipfile

SCRIPT=Path(__file__).resolve().parents[1]/"tools"/"release_lab_android.py"
SPEC=importlib.util.spec_from_file_location("release_lab_android",SCRIPT)
lab=importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(lab)


def apk(root, source="a"*40, native="x86_64", payload=bytes(range(256))*5):
    path=Path(root)/"release.apk"
    with zipfile.ZipFile(path,"w",zipfile.ZIP_STORED) as z:
        z.writestr("assets/localpilot-source.json",json.dumps({"schema":"localpilot-source-v1","sourceSha":source}))
        if native:z.writestr("lib/"+native+"/liblocalpilot.so",payload)
        z.writestr("classes.dex",payload)
    return path


class ReleaseLabChecks(unittest.TestCase):
    def test_github_avd_runner_uses_atomic_command_line(self):
        """The emulator runner executes one script line at a time with sh -c.

        A YAML multiline command with backslash continuations discarded --apk
        on all six real Android emulators even though their systems booted.
        """
        wf=(SCRIPT.parents[1]/".github/workflows/localpilot-release-lab.yml").read_text()
        cmds=[line.strip() for line in wf.splitlines()
              if line.strip().startswith("script:")]
        self.assertEqual(len(cmds),1,"ReleaseLab must have exactly one runner invocation")
        cli=cmds[0].removeprefix("script:").strip()
        self.assertFalse(cli.startswith("|"),"No multiline script for emulator-runner")
        for fragment in ("python3 tools/release_lab_android.py check",
                         "--serial emulator-5554", "--api ${{ matrix.api }}",
                         "--apk app/build/outputs/apk/debug/app-debug.apk",
                         "--expected-source", "--output"):
            self.assertIn(fragment,cli)
        self.assertNotIn("\\",cli)

    def test_release_lab_artifact_uses_repository_root_and_preflight(self):
        """The Actions ZIP stores BOTH repository-relative files, not a flat APK.

        Downloading to app/build/outputs/apk/debug/ nests app/build inside it,
        so Android boots but ReleaseLab finds no APK. Reject that workflow.
        """
        wf=(SCRIPT.parents[1]/".github/workflows/localpilot-release-lab.yml").read_text()
        section=wf.split("  isolated-release-lab:",1)[1]
        self.assertIn("name: LocalPilot-ReleaseLab-X86-TEST-ONLY-${{ github.sha }}", section)
        self.assertIn("path: .",section)
        self.assertNotIn("path: app/build/outputs/apk/debug/",section)
        self.assertIn("Validate downloaded APK before paying for Android emulator startup",section)
        self.assertIn("test -s \"$APK\"",section)
        self.assertIn("test -s build/release-lab-elf.json",section)
        self.assertIn("unzip -t \"$APK\"",section)
        self.assertIn("lib/x86_64/liblocalpilot_embedded.so",section)
        self.assertLess(
            section.index("Validate downloaded APK before paying for Android emulator startup"),
            section.index("reactivecircus/android-emulator-runner@v2"))

    def test_verified_source_and_abi(self):
        with tempfile.TemporaryDirectory() as tmp:
            result=lab.read_source(apk(tmp))
            self.assertEqual(result["sourceSha"],"a"*40)
            self.assertEqual(result["nativeAbis"],["x86_64"])
            self.assertRegex(result["apkSha256"],r"^[0-9a-f]{64}$")

    def test_malformed_provenance_and_non_zip_fail(self):
        with tempfile.TemporaryDirectory() as tmp:
            with self.assertRaises(lab.Rejection):lab.read_source(apk(tmp,source="untrusted"))
            file=Path(tmp)/"raw.apk"
            file.write_bytes(b"not a valid APK"*150)
            with self.assertRaises(zipfile.BadZipFile):lab.read_source(file)

    def test_disallow_other_apps_and_unsafe_names(self):
        good="package: name='org.localpilot.android.dev.unified' versionCode='55' versionName='0.8.3-debug'\n"
        self.assertEqual(lab.parse_badging(good)["package"],"org.localpilot.android.dev.unified")
        for package in ["com.example.debug","org.localpilot.gamelab","org.localpilot.android.;rm -rf /", "org.localpilot.android2"]:
            with self.subTest(package=package),self.assertRaises(lab.Rejection):
                lab.parse_badging(good.replace("org.localpilot.android.dev.unified",package))
        for api, profile in [(29,"pixel_2"),(36,"phone"),(35,"pixel_7")]:
            with self.assertRaises(lab.Rejection):lab.avd_name(api,profile)
        self.assertEqual(lab.avd_name(35,"pixel_c"),"LocalPilot_ReleaseLab_API35_pixel_c")

    def test_reject_physical_even_if_user_passed_serial(self):
        with self.assertRaises(lab.Rejection):lab.adb("adb","R58M1234","shell","getprop","ro.kernel.qemu")
        with patch.object(lab,"adb",side_effect=["0","0"]):
            with self.assertRaises(lab.Rejection):lab.emulator_identity("adb","emulator-5554",30)
        with patch.object(lab,"adb",side_effect=["1","","36","x86_64","Pixel C"]):
            with self.assertRaises(lab.Rejection):lab.emulator_identity("adb","emulator-5554",30)

    def test_png_and_real_package_xml(self):
        header=b"\x89PNG\r\n\x1a\n"+struct.pack(">I",13)+b"IHDR"+struct.pack(">II",1080,2400)+b"\x08\x06\x00\x00\x00"+bytes(20)
        self.assertEqual(lab.screenshot_info(header)["width"],1080)
        with self.assertRaises(lab.Rejection):lab.screenshot_info(b"all white")
        self.assertTrue(lab.xml_has_package('<hierarchy><node package="org.localpilot.android" /></hierarchy>',"org.localpilot.android"))
        self.assertFalse(lab.xml_has_package('<hierarchy><node package="com.android.settings" /></hierarchy>',"org.localpilot.android"))
        self.assertFalse(lab.xml_has_package('<invalid>',"org.localpilot.android"))

    def test_bad_signer_source_or_abi_never_install(self):
        candidate={"package":"org.localpilot.android.dev.unified","apkSha256":"a"*64,
                   "signerSha256":"b"*64,"sourceSha":"c"*40,
                   "nativeAbis":["arm64-v8a"],"sourcePinned":True,"signerPinned":False}
        with tempfile.TemporaryDirectory() as tmp:
            with patch.object(lab,"emulator_identity",return_value={"api":35,"abi":"x86_64"}),patch.object(lab,"adb") as call:
                report=lab.run_checks("adb","emulator-5554",35,candidate,Path(tmp),None)
                self.assertEqual(report["result"],"FAIL")
                self.assertFalse(report["releaseReady"])
                self.assertIn("arm64-v8a",report["error"])
                call.assert_not_called()

    def test_refuse_incompatible_upgrade_before_install(self):
        candidate={"package":"org.localpilot.android","apkSha256":"a"*64,
                   "signerSha256":"b"*64,"sourceSha":"c"*40,"nativeAbis":[],
                   "sourcePinned":True,"signerPinned":True}
        baseline={**candidate,"signerSha256":"d"*64}
        with tempfile.TemporaryDirectory() as tmp:
            with patch.object(lab,"emulator_identity",return_value={"abi":"x86_64"}),patch.object(lab,"adb") as call:
                report=lab.run_checks("adb","emulator-5554",35,candidate,Path(tmp),baseline)
                self.assertEqual(report["result"],"FAIL")
                self.assertIn("signer",report["error"])
                call.assert_not_called()

    def test_signed_artifact_and_sha_pins_are_exact(self):
        with tempfile.TemporaryDirectory() as tmp:
            file=apk(tmp)
            with patch.object(lab,"invoke",side_effect=[
                "package: name='org.localpilot.android' versionCode='55' versionName='0.8.3'",
                "Signer #1 certificate SHA-256 digest: "+"b"*64]):
                with patch.object(lab,"tool",return_value="tool"):
                    audit=lab.inspect(file,None,"a"*40,"b"*64)
                    self.assertTrue(audit["sourcePinned"] and audit["signerPinned"])
            with patch.object(lab,"invoke",side_effect=[
                "package: name='org.localpilot.android' versionCode='55' versionName='0.8.3'",
                "Signer #1 certificate SHA-256 digest: "+"b"*64]):
                with patch.object(lab,"tool",return_value="tool"):
                    with self.assertRaises(lab.Rejection):lab.inspect(file,None,"f"*40,None)

if __name__=="__main__":unittest.main()
