"""Host-only regression tests for the one-package, downloadable-model variant."""
from pathlib import Path
import unittest
import re

ROOT=Path(__file__).resolve().parents[1]
def source(p):
    return (ROOT/p).read_text(encoding="utf-8")

class AllInOneContract(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.gradle=source("app/build.gradle")
        cls.manifest=source("app/src/main/AndroidManifest.xml")
        cls.hub=source("app/src/main/java/org/localpilot/android/MainActivity.java")
        cls.runtime=source("app/src/main/java/org/localpilot/android/EmbeddedGgufRuntime.java")
        cls.model=source("model-lab/src/main/java/org/localpilot/android/ModelLabActivity.java")
        cls.nes=source("game-lab/src/main/java/org/localpilot/gamelab/NesEmulatorActivity.java")
        cls.game=source("game-lab/src/main/java/org/localpilot/gamelab/TestDeviceActivity.java")
        cls.workflow=source(".github/workflows/localpilot-all-in-one.yml")
    def test_one_package_and_isolated_candidate_suffix(self):
        self.assertIn("applicationId 'org.localpilot.android'",self.gradle)
        self.assertIn("'.dev.unified'",self.gradle)
        self.assertIn("UNIFIED_MODELLAB",self.gradle)
        self.assertIn("unifiedModelLabEnabled: unifiedModelLab.toString()",self.gradle)
    def test_no_competing_jni_classes(self):
        self.assertIn("exclude '**/EmbeddedGgufRuntime.java'",self.gradle)
        self.assertIn("syncUnifiedModelLab",self.gradle)
        self.assertIn("dependsOn syncUnifiedModelLab",self.gradle)
        self.assertEqual(self.runtime.count("public final class EmbeddedGgufRuntime"),1)
    def test_model_lab_activity_is_private_and_single_launcher(self):
        self.assertIn('android:name=".ModelLabActivity" android:exported="false"',self.manifest)
        self.assertIn('android:enabled="${unifiedModelLabEnabled}"',self.manifest)
        self.assertEqual(self.manifest.count('android.intent.category.LAUNCHER'),2)
        self.assertIn('android:name=".MainActivity" android:exported="true"',self.manifest)
        self.assertIn("new Intent(this,ModelLabActivity.class)",self.hub)
    def test_models_are_imported_not_embedded(self):
        self.assertIn("new ModelLabStore(getApplicationContext())",self.model)
        self.assertIn("store.importFile",self.model)
        self.assertIn("store.download",self.model)
        self.assertIn("model-lab/src/main/java",self.gradle)
        self.assertIn("if (bundledGguf || unifiedModelLab)",self.gradle)
    def test_model_output_cannot_dispatch_game_inputs(self):
        self.assertNotIn("PilotService",self.model)
        self.assertNotIn("dispatchGesture",self.model)
        self.assertIn("requestCancel()",self.runtime)
        self.assertIn("NATIVE_JOB_ACTIVE.compareAndSet",self.runtime)
        self.assertIn("nativeInterrupt()",self.runtime)
        self.assertIn("ModelLabActivity",self.manifest)
    def test_intra_package_nes_to_model_path(self):
        self.assertIn('getPackageName().startsWith("org.localpilot.android")',self.nes)
        self.assertIn('getPackageName().startsWith("org.localpilot.android")',self.game)
        self.assertIn("NES → ModelLab",self.nes)
        self.assertIn("receiveNesIntent",self.model)
    def test_no_auto_release_or_secrets_in_candidate_workflow(self):
        self.assertIn("-PunifiedModelLab=true",self.workflow)
        self.assertIn("TEST-ONLY",self.workflow)
        self.assertIn("actions/upload-artifact@v4",self.workflow)
        self.assertNotIn("gh release create",self.workflow)
        self.assertNotIn("contents: write",self.workflow)
        self.assertNotIn("secrets.",self.workflow)
    def test_unified_debug_identity_does_not_overwrite_existing_base(self):
        self.assertIn("'.dev.install1081'",self.gradle)
        self.assertIn("'.dev.unified'",self.gradle)
        self.assertIn("'.dev.offlinecatalog'",self.gradle)
        self.assertIn("'-unified-test-only'",self.gradle)
    def test_modellab_standalone_runtime_unchanged(self):
        native=source("model-lab/src/main/java/org/localpilot/android/EmbeddedGgufRuntime.java")
        self.assertIn("public EmbeddedGgufRuntime(android.content.Context context)",native)
        self.assertIn("nativeAnalyzeImage",native)
        self.assertIn("nativeInfer",native)

if __name__=="__main__":
    unittest.main()
