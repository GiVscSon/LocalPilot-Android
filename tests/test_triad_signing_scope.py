"""Static audit preventing accidental release-key exposure to ordinary PR jobs."""
from pathlib import Path
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / 'tools' / 'setup_triad_signing.ps1'
WORKFLOW = Path(__file__).resolve().parents[1] / '.github' / 'workflows' / 'triad-signed-candidate.yml'


class SigningEnvironmentScope(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.script = SCRIPT.read_text(encoding='utf-8')
        cls.workflow = WORKFLOW.read_text(encoding='utf-8')

    def test_secrets_must_be_environment_scoped(self):
        self.assertIn("'--env',$Environment", self.script)
        self.assertIn('environment: android-signing', self.workflow)

    def test_environment_must_exist_before_key_read(self):
        environment = self.script.index('environments/$Environment')
        keystore = self.script.index('[System.IO.File]::ReadAllBytes($absolute)')
        self.assertLess(environment, keystore)

    def test_certificate_pin_is_environment_variable(self):
        self.assertIn('variable set "${prefix}_RELEASE_CERT_SHA256" --repo $Repo --env $Environment', self.script)

    def test_no_debug_fallback_for_release(self):
        self.assertIn('Missing/invalid pinned certificate', self.workflow)
        self.assertIn('no debug fallback', self.workflow)

    def test_three_independent_keys_and_packages_are_mandatory(self):
        self.assertIn("ValidateSet('LocalPilot', 'GameLab', 'ModelLab')", self.script)
        for prefix in ('LOCALPILOT', 'GAMELAB', 'MODELLAB'):
            self.assertIn(prefix + '_RELEASE_CERT_SHA256', self.workflow)
            self.assertIn(prefix + '_RELEASE_KEYSTORE_B64', self.workflow)
            self.assertIn(prefix + '_RELEASE_STORE_PASS', self.workflow)
            self.assertIn(prefix + '_RELEASE_KEY_PASS', self.workflow)
            self.assertIn(prefix + '_RELEASE_KEY_ALIAS', self.workflow)
        self.assertIn('Three independent release certificate identities are required', self.workflow)
        self.assertIn('Release signing certificates must be distinct', (Path(__file__).resolve().parents[1] / 'tools' / 'triad_release_gate.py').read_text(encoding='utf-8'))

    def test_gh_cli_secrets_use_standard_input(self):
        self.assertIn('RedirectStandardInput = $true', self.script)
        self.assertNotIn("--body',$SecretValue",self.script)

    def test_job_only_runs_on_approved_dispatch(self):
        self.assertIn("if: github.event_name == 'workflow_dispatch'",self.workflow)
        self.assertIn('environment: android-signing',self.workflow)


if __name__ == '__main__':
    unittest.main()