"""Fail-closed build and runtime contract for experimental single-APK Vulkan."""
from pathlib import Path
import unittest

ROOT=Path(__file__).resolve().parents[1]
def source(path):return (ROOT/path).read_text(encoding="utf-8")

class OneApkVulkanContract(unittest.TestCase):
    def test_gpu_opt_in_is_not_a_second_package(self):
        app=source("app/build.gradle")
        self.assertIn("def unifiedVulkan = providers.gradleProperty('unifiedVulkan')",app)
        self.assertIn("unifiedVulkan && (!unifiedModelLab || unifiedEmulatorAbi || bundledGguf)",app)
        self.assertIn("buildConfigField 'boolean', 'VULKAN_COMPILED', unifiedVulkan.toString()",app)
        self.assertIn("gradle.taskGraph.whenReady",app)
        self.assertIn("Experimental ABI/backend may not execute any app release task",app)
        self.assertIn("'.dev.unified'",app)
    def test_native_probes_use_real_backend(self):
        runtime=source("app/src/main/java/org/localpilot/android/EmbeddedGgufRuntime.java")
        for phrase in ("nativeGpuDescription()", "nativeConfigureGpuLayers(int layers)",
                       "nativeLoadedGpuLayers()", "gpuLayersLoaded=nativeLoadedGpuLayers()",
                       "chooseGpuLayers(requestedGpuLayers)", "BuildConfig.VULKAN_COMPILED"):
            self.assertIn(phrase,runtime)
        self.assertNotIn("public int lastGpuLayers(){return 0;}",runtime)
        native=source("app/src/main/cpp/localpilot_embedded.cpp")
        self.assertIn("gpuLayersLoaded=mp.n_gpu_layers",native)
        self.assertIn("llama_model_load_from_file",native)
    def test_agent_uses_selected_backend_after_explicit_binding(self):
        source_code=source("model-lab/src/main/java/org/localpilot/android/ModelLabActivity.java")
        self.assertIn("selectedGpuLayers(choice)",source_code)
        self.assertIn("registerConsoleBrain(choice,gpuLayers)",source_code)
        self.assertIn("runtime.text(weights,observation,offload",source_code)
        self.assertIn("runtime.image(weights,projector,observation,rgb,w,h,offload",source_code)
    def test_ci_uses_exact_pins_and_never_publishes(self):
        workflow=source(".github/workflows/localpilot-console-vulkan.yml")
        for key in ("09913f088a1197aba4aefd300a876b2ebbaa3391",
                    "952f776f6573aafbb62ea717d871cd1d6816c387",
                    "2b66bfbf7107e43f5cd2fe99802ff6e872e4c4d0",
                    "readelf -d", "libvulkan.so", "verify_all_in_one.py",
                    "releaseReady", "physical Mali device"):
            self.assertIn(key,workflow)
        self.assertNotIn("gh release create",workflow)
        self.assertNotIn("contents: write",workflow)
if __name__=="__main__":unittest.main()
