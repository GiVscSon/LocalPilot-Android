"""Static integration audit must not confuse connected code with tested gameplay."""
import importlib.util
from pathlib import Path
import unittest

ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location("console_system_audit", ROOT/"tools/console_system_audit.py")
audit=importlib.util.module_from_spec(spec)
spec.loader.exec_module(audit)

class ConsoleSystemAuditTests(unittest.TestCase):
 def test_all_required_model_to_console_links_exist(self):
  result=audit.inspect()
  self.assertTrue(result["source_contracts_pass"],
    {k:v for k,v in result["source_contracts"].items() if not v})
  self.assertEqual(result["capabilities"]["one_step_gguf_nes_agent"],
    "SOURCE_AND_CI_TESTED_NOT_PHONE_TESTED")

 def test_no_false_claims_about_training_or_gpu(self):
  result=audit.inspect()
  self.assertEqual(result["capabilities"]["episode_memory"],
    "IN_CONTEXT_REUSE_NOT_WEIGHT_TRAINING")
  self.assertEqual(result["capabilities"]["nes_training_weights"],"NOT_IMPLEMENTED")
  self.assertEqual(result["capabilities"]["unified_vulkan"],"NOT_COMPILED")
  self.assertFalse(result["stable_release_ready"])
  self.assertFalse(result["physical_device_verified"])

 def test_pinned_emulator_and_build_dependencies_reported(self):
  deps=audit.inspect()["dependencies"]
  self.assertEqual(deps["jsnes"],"2.1.0")
  self.assertEqual(deps["gradle"],"8.9")
  self.assertEqual(deps["android_gradle_plugin"],"8.7.3")

if __name__=="__main__":
 unittest.main()
