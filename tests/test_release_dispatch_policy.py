"""Run the actual embedded signing policy against missing-protection mutants."""
from pathlib import Path
import re
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
        block = cls.workflow.split("          python3 - <<'POLICY'\n", 1)[1]
        cls.policy = textwrap.dedent(block.split('\n          POLICY', 1)[0])

    def check_policy(self, workflow, policy=None):
        with tempfile.TemporaryDirectory() as folder:
            target = Path(folder) / WORKFLOW
            target.parent.mkdir(parents=True)
            target.write_text(workflow)
            return subprocess.run([sys.executable, '-c', self.policy if policy is None else policy], cwd=folder,
                                  text=True, capture_output=True, timeout=10)

    def test_actual_policy_accepts_protected_workflow(self):
        result = self.check_policy(self.workflow)
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn('SIGNING_DISPATCH_POLICY_PASS', result.stdout)

    def test_actions_expression_interpolation_does_not_corrupt_policy(self):
        expanded = re.sub(r'\$\{\{.*?\}\}', 'expanded-event-value', self.policy)
        result = self.check_policy(self.workflow, expanded)
        self.assertEqual(0, result.returncode, result.stderr)

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
        self.assertIn('Build source pin missing', result.stderr)

    def test_missing_dispatch_trigger_is_rejected(self):
        mutant = self.workflow.replace('  workflow_dispatch:\n', '', 1)
        self.assertNotEqual(mutant, self.workflow)
        result = self.check_policy(mutant)
        self.assertNotEqual(0, result.returncode)
        self.assertIn('Manual dispatch trigger missing', result.stderr)

    def test_build_cannot_receive_secret(self):
        mutant = self.workflow.replace('    needs: policy\n',
            '    needs: policy\n    env:\n      KEY: ${{ secrets.LOCALPILOT_RELEASE_KEYSTORE_B64 }}\n', 1)
        result = self.check_policy(mutant)
        self.assertNotEqual(0, result.returncode)
        self.assertIn('Build must not access signing identity', result.stderr)

    def test_signer_cannot_checkout_untrusted_app(self):
        prefix, signing = self.workflow.split('\n  signed-candidate:\n', 1)
        mutant = prefix + '\n  signed-candidate:\n' + signing.replace(
            'ref: ${{ github.workflow_sha }}', 'ref: ${{ inputs.source_sha }}', 1)
        self.assertNotEqual(0, self.check_policy(mutant).returncode)

    def test_signer_cannot_execute_gradle(self):
        mutant = self.workflow.replace('      - name: Upload signed candidate (NOT stable)',
            '      - run: ./gradlew :app:assembleRelease\n      - name: Upload signed candidate (NOT stable)', 1)
        result = self.check_policy(mutant)
        self.assertNotEqual(0, result.returncode)
        self.assertIn('Signer must not execute app build', result.stderr)

    def test_signer_must_wait_for_artifact_job(self):
        prefix, signing = self.workflow.split('\n  signed-candidate:\n', 1)
        mutant = prefix + '\n  signed-candidate:\n' + signing.replace(
            'needs: [policy, build-unsigned]', 'needs: policy', 1)
        self.assertNotEqual(0, self.check_policy(mutant).returncode)

    def test_signing_requires_explicit_opt_in(self):
        prefix, signing = self.workflow.split('\n  signed-candidate:\n', 1)
        mutant = prefix + '\n  signed-candidate:\n' + signing.replace(' && inputs.sign_candidate', '', 1)
        self.assertNotEqual(0, self.check_policy(mutant).returncode)

    def test_signing_opt_in_input_cannot_be_removed(self):
        mutant = self.workflow.replace('      sign_candidate:\n', '      other_flag:\n', 1)
        result = self.check_policy(mutant)
        self.assertNotEqual(0, result.returncode)
        self.assertIn('Explicit signing opt-in missing', result.stderr)

    def test_signing_cannot_default_to_enabled(self):
        mutant = self.workflow.replace('        default: false', '        default: true', 1)
        result = self.check_policy(mutant)
        self.assertNotEqual(0, result.returncode)
        self.assertIn('Signing must default to disabled', result.stderr)

    def test_required_reviewer_guard_cannot_be_removed(self):
        marker = '\n      - name: Require existing Environment with configured owner review before any build\n'
        prefix, after = self.workflow.split(marker, 1)
        guard, rest = after.split('\n  build-unsigned:\n', 1)
        mutant = prefix + marker + guard.replace('          python3 tools/verify_signing_environment.py', '          true # omitted reviewer check', 1) + '\n  build-unsigned:\n' + rest
        self.assertNotEqual(0, self.check_policy(mutant).returncode)


if __name__ == '__main__':
    unittest.main()
