"""Host-only QA: no real device and no screenshots needed for smoke parser tests."""
from pathlib import Path
import importlib.util
import unittest
from unittest.mock import patch

ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location("unified_ui_smoke",ROOT/"tools/unified_in_app_smoke.py")
smoke=importlib.util.module_from_spec(spec)
spec.loader.exec_module(smoke)

class UnifiedUiSmokeContract(unittest.TestCase):
    def test_clickable_harness_target_wins_over_non_clickable_label(self):
        xml=('<hierarchy><node text="Harness Console" clickable="false" '
             'bounds="[0,10][100,40]"/><node text="Harness Console" '
             'clickable="true" bounds="[200,100][400,200]"/></hierarchy>')
        self.assertEqual(smoke.target_bounds(xml,"Harness Console"),(300,150))

    def test_wrong_neighbor_navigation_retries_once_and_verifies_dashboard(self):
        with patch.object(smoke,"tap_text") as click, \
             patch.object(smoke,"wait_activity",
                          side_effect=[RuntimeError("ModelLab opened"),None,None]) as wait, \
             patch.object(smoke,"resumed_activity",
                          return_value=smoke.PACKAGE+"/"+smoke.MODEL), \
             patch.object(smoke,"adb") as adb, \
             patch.object(smoke.time,"sleep"):
            self.assertEqual(smoke.tap_harness_and_verify(),1)
            self.assertEqual(click.call_count,2)
            adb.assert_called_once_with("shell","input","keyevent","KEYCODE_BACK")
            self.assertEqual(wait.call_count,3)

    def test_parse_visible_button_text(self):
        xml='<hierarchy><node text="🧠 ModelLab · GGUF, загрузка моделей и анализ фото" bounds="[12,100][312,180]" /></hierarchy>'
        self.assertEqual(smoke.target_bounds(xml,"ModelLab · GGUF"),(162,140))
    def test_parse_description_fallback(self):
        xml='<hierarchy><node content-desc="Вернуться в GameLab" bounds="[20,30][220,90]" /></hierarchy>'
        self.assertEqual(smoke.target_bounds(xml,"Вернуться в GameLab"),(120,60))
    def test_missing_or_invalid_bounds_never_becomes_tap(self):
        xml='<hierarchy><node text="ModelLab · GGUF" bounds="[20,20][20,20]" /></hierarchy>'
        self.assertIsNone(smoke.target_bounds(xml,"ModelLab · GGUF"))
    def test_unrelated_activity_not_accepted(self):
        self.assertNotEqual(smoke.MAIN,smoke.MODEL)
        self.assertNotEqual(smoke.MODEL,smoke.GAMELAB)
        self.assertNotEqual(smoke.GAMELAB,smoke.NES)
    def test_companion_absence_is_exact_and_does_not_depend_on_pm_path(self):
        with patch.object(smoke, "adb", return_value="package:org.localpilot.modellab.debug\n") as fake:
            self.assertTrue(smoke.companion_absent("org.localpilot.modellab"))
            fake.assert_called_once_with("shell", "pm", "list", "packages", "org.localpilot.modellab")
        with patch.object(smoke, "adb", return_value="package:org.localpilot.modellab\n"):
            self.assertFalse(smoke.companion_absent("org.localpilot.modellab"))
        with patch.object(smoke, "adb", side_effect=RuntimeError("ADB disconnected")):
            with self.assertRaisesRegex(RuntimeError, "disconnected"):
                smoke.companion_absent("org.localpilot.modellab")

    def test_requires_emulator_and_never_changes_secure_settings(self):
        source=(ROOT/"tools/unified_in_app_smoke.py").read_text(encoding="utf-8")
        self.assertIn("if not is_emulator()",source)
        self.assertNotIn('"settings", "put"',source)
        self.assertNotIn('"settings", "delete"',source)
        self.assertNotIn('"uninstall"',source)
        self.assertNotIn('"pm", "clear"',source)
    def test_report_rejects_stable_and_physical_test_claims(self):
        source=(ROOT/"tools/unified_in_app_smoke.py").read_text(encoding="utf-8")
        self.assertIn('"physicalAndroidTest": "NOT_VERIFIED"',source)
        self.assertIn('"releaseReady": False',source)
        self.assertIn('"modelGeneration": "NOT_TESTED"',source)
        self.assertIn('"realGameMotion": "NOT_TESTED"',source)
    def test_ui_dump_does_not_depend_on_sdcard_mount(self):
        self.assertEqual(smoke.UI_PATH, "/data/local/tmp/localpilot-unified-smoke.xml")
        self.assertNotIn("/sdcard/", smoke.UI_PATH)

    def test_only_system_ui_anr_wait_is_recoverable(self):
        xml=('<hierarchy><node text="System UI isn\'t responding" bounds="[0,0][400,300]" />'
             '<node text="Close app" bounds="[0,30][200,80]" />'
             '<node text="Wait" bounds="[30,90][230,160]" /></hierarchy>')
        with patch.object(smoke, "adb") as fake:
            self.assertTrue(smoke.recover_system_ui_anr(xml))
            fake.assert_called_once_with("shell", "input", "tap", "130", "125", timeout=15)
        localpilot=xml.replace("System UI isn't responding", "LocalPilot isn't responding")
        with patch.object(smoke, "adb") as fake:
            self.assertFalse(smoke.recover_system_ui_anr(localpilot))
            fake.assert_not_called()

    def test_system_ui_anr_without_safe_wait_fails_closed(self):
        xml='<hierarchy><node text="System UI isn\'t responding" bounds="[0,0][200,60]"/></hierarchy>'
        with self.assertRaisesRegex(RuntimeError, "no safe Wait"):
            smoke.recover_system_ui_anr(xml)

    def test_dump_does_not_accept_dismissed_anr_as_navigation_success(self):
        anr=('<hierarchy><node text="System UI isn\'t responding" bounds="[0,0][200,60]"/>'
             '<node text="Wait" bounds="[0,60][200,120]"/></hierarchy>')
        real='<hierarchy><node text="ModelLab · локальные нейросети" bounds="[0,0][200,60]"/></hierarchy>'
        reads=iter((anr, real))
        def device(*args, **kwargs):
            if args[:2]==("shell","cat"):
                return next(reads)
            return ""
        with patch.object(smoke,"adb",side_effect=device) as fake, patch.object(smoke.time,"sleep"):
            self.assertEqual(smoke.dump_ui(),real)
            self.assertEqual(sum(x.args[:3]==("shell","input","tap") for x in fake.call_args_list),1)

    def test_dump_requires_fresh_ui_and_tries_compressed_fallback(self):
        xml='<hierarchy><node text="ModelLab" bounds="[1,1][20,20]" /></hierarchy>'
        attempts=[]
        def device(*args, **kwargs):
            attempts.append(args)
            if args[:3]==("shell","uiautomator","dump") and "--compressed" not in args:
                if sum(x[:3]==("shell","uiautomator","dump") for x in attempts)==1:
                    raise RuntimeError("UiAutomation not idle")
            if args[:2]==("shell","cat"):
                return xml
            return ""
        with patch.object(smoke,"adb",side_effect=device), patch.object(smoke.time,"sleep"):
            self.assertEqual(smoke.dump_ui(),xml)
        self.assertEqual(sum(x[:3]==("shell","rm","-f") for x in attempts),2)
        self.assertTrue(any("--compressed" in x for x in attempts))

    def test_dump_preserves_actual_device_failure_reason(self):
        with patch.object(smoke,"adb",side_effect=RuntimeError("UiAutomation connection refused")), \
             patch.object(smoke.time,"sleep"):
            with self.assertRaisesRegex(RuntimeError, "UiAutomation connection refused"):
                smoke.dump_ui()

    def test_bad_or_empty_xml_fails_closed(self):
        with patch.object(smoke,"adb",side_effect=lambda *args, **kwargs:
                          '<hierarchy></hierarchy>' if args[:2]==("shell","cat") else ""), \
             patch.object(smoke.time,"sleep"):
            with self.assertRaisesRegex(RuntimeError, "no accessible UI nodes"):
                smoke.dump_ui()

    def test_failure_diagnostics_never_mark_release_ready(self):
        import tempfile, json
        with tempfile.TemporaryDirectory() as folder:
            report=Path(folder)/"evidence.json"
            with patch.object(smoke,"adb",return_value="emulator test state"), \
                 patch.object(smoke.subprocess,"run",side_effect=OSError("no screenshot")):
                smoke.write_failure_diagnostics(report,RuntimeError("specific failure"))
            evidence=json.loads((report.parent/"failure-diagnostics.json").read_text())
            self.assertIs(evidence["releaseReady"],False)
            self.assertIn("specific failure",evidence["exception"])

    def test_ci_requires_hardware_virtualization_for_real_ui(self):
        workflow=(ROOT/".github/workflows/localpilot-all-in-one.yml").read_text(encoding="utf-8")
        self.assertIn("ANDROID_CI_INFRA_KVM_MISSING",workflow)
        self.assertIn("ANDROID_CI_INFRA_KVM_PERMISSIONS",workflow)
        self.assertIn("sudo chmod 666 /dev/kvm",workflow)
        self.assertIn("emulator-options: -no-window -gpu software",workflow)
        self.assertNotIn("-gpu swiftshader_indirect",workflow)
        self.assertLess(workflow.index("Verify KVM acceleration"),workflow.index("Exercise real Android navigation"))
    
    def test_emulator_abi_guard_blocks_indirect_release_tasks(self):
        gradle=(ROOT/"app/build.gradle").read_text(encoding="utf-8")
        self.assertIn("gradle.taskGraph.whenReady", gradle)
        self.assertIn("graph.allTasks.any", gradle)
        self.assertIn("task.project == project", gradle)
        self.assertIn("task.name.toLowerCase(java.util.Locale.ROOT).contains('release')", gradle)
        self.assertIn("Emulator-only ABI may not execute any app release task", gradle)

    def test_file_access_is_only_enabled_in_release_blocked_emulator_derivative(self):
        gradle=(ROOT/"app/build.gradle").read_text()
        self.assertIn("debuggable false",gradle)
        self.assertIn("if (unifiedEmulatorAbi) {\n   buildTypes.debug.debuggable = true\n }",gradle)
        self.assertIn("unifiedEmulatorAbi && !unifiedModelLab",gradle)
        self.assertIn("Emulator-only ABI may not execute any app release task",gradle)

    def test_ci_uses_only_compiled_single_candidate_artifact(self):
        workflow=(ROOT/".github/workflows/localpilot-all-in-one.yml").read_text(encoding="utf-8")
        self.assertIn("needs: all-in-one",workflow)
        self.assertIn("actions/download-artifact@v4",workflow)
        self.assertIn("python3 tools/unified_in_app_smoke.py --apk app/build/outputs/apk/debug/app-debug.apk --output unified-device-evidence/one-package-navigation.json",workflow)
        self.assertIn("python3 tools/harness_student_device_acceptance.py --apk app/build/outputs/apk/debug/app-debug.apk --output unified-device-evidence/nes-student-ppo.json",workflow)
        self.assertNotIn("script: |\\n            python3 tools/unified_in_app_smoke.py",workflow)
        self.assertIn("reactivecircus/android-emulator-runner@v2",workflow)

if __name__=="__main__":
    unittest.main()
