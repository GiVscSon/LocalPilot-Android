"""Run the actual embedded signing policy against missing-protection mutants."""
from pathlib import Path
import subprocess
import sys
import tempfile
import textwrap
import unittest

ROOT = Path(__file__).resolve().parents[1]
WORKFLOW = Path('.github/workflows/localpilot-unified-release-dispatch.yml')


class DispatchPolicy(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.workflow = (ROOT / WORKFLOW).read_text()
        block = cls.workflow.split("          python3 - <<'PY'\n", 1)[1]
        cls.policy = textwrap.dedent(block.split('\n          PY', 1)[0])

    def check_policy(self, workflow):
        with tempfile.TemporaryDirectory() as folder:
            target = Path(folder) / WORKFLOW
            target.parent.mkdir(parents=True)
            target.write_text(workflow)
            return subprocess.run([sys.executable, '-c', self.policy], cwd=folder,
                                  text=True, capture_output=True, timeout=10)

    def test_actual_policy_accepts_protected_workflow(self):
        result = self.check_policy(self.workflow)
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn('SIGNING_DISPATCH_POLICY_PASS', result.stdout)

    def test_missing_environment_is_rejected_despite_literal_in_policy(self):
        mutant = self.workflow.replace('    environment: android-signing\n', '', 1)
        self.assertNotEqual(mutant, self.workflow)
        # The required-list literal remains: looking in the entire file would pass.
        self.assertIn('environment: android-signing', mutant)
        result = self.check_policy(mutant)
        self.assertNotEqual(0, result.returncode)
        self.assertIn('Protected signing job contract missing', result.stderr)

    def test_missing_build_source_pin_is_rejected(self):
        mutant = self.workflow.replace(
            '          LOCALPILOT_BUILD_SOURCE_SHA: ${{ inputs.source_sha }}\n', '', 1)
        self.assertNotEqual(mutant, self.workflow)
        self.assertIn('LOCALPILOT_BUILD_SOURCE_SHA:', mutant)
        result = self.check_policy(mutant)
        self.assertNotEqual(0, result.returncode)
        self.assertIn('Protected signing job contract missing', result.stderr)

    def test_missing_dispatch_trigger_is_rejected(self):
        mutant = self.workflow.replace('  workflow_dispatch:\n', '', 1)
        self.assertNotEqual(mutant, self.workflow)
        result = self.check_policy(mutant)
        self.assertNotEqual(0, result.returncode)
        self.assertIn('Manual dispatch trigger missing', result.stderr)


if __name__ == '__main__':
    unittest.main()
