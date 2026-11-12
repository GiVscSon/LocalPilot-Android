"""Host-only QA: no real device and no screenshots needed for smoke parser tests."""
from pathlib import Path
import importlib.util
import unittest

ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location("unified_ui_smoke",ROOT/"tools/unified_in_app_smoke.py")
smoke=importlib.util.module_from_spec(spec)
spec.loader.exec_module(smoke)

class UnifiedUiSmokeContract(unittest.TestCase):
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
    def test_ci_uses_only_compiled_single_candidate_artifact(self):
        workflow=(ROOT/".github/workflows/localpilot-all-in-one.yml").read_text(encoding="utf-8")
        self.assertIn("needs: all-in-one",workflow)
        self.assertIn("actions/download-artifact@v4",workflow)
        self.assertIn("script: python3 tools/unified_in_app_smoke.py --apk app/build/outputs/apk/debug/app-debug.apk --output unified-device-evidence/one-package-navigation.json",workflow)
        self.assertNotIn("script: |\\n            python3 tools/unified_in_app_smoke.py",workflow)
        self.assertIn("reactivecircus/android-emulator-runner@v2",workflow)

if __name__=="__main__":
    unittest.main()
