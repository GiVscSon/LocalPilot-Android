"""Security contracts for protected, non-public LocalPilot release signing.

These assertions exercise the checked-in release path without any private key,
device, Android SDK or hosted GitHub environment.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]


class ProtectedSigningContract(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.workflow = (ROOT / ".github/workflows/signed-qa-candidate.yml").read_text()
        cls.gradle = (ROOT / "app/build.gradle").read_text()

    def test_manual_dispatch_only(self):
        self.assertIn("workflow_dispatch:", self.workflow)
        self.assertNotIn("\n  push:", self.workflow)
        self.assertNotIn("\n  release:", self.workflow)

    def test_only_reviewed_main_code_is_signed(self):
        self.assertIn("if: github.ref == 'refs/heads/main'", self.workflow)
        self.assertIn("sha!=expected", self.workflow)
        self.assertIn("expected_sha:", self.workflow)

    def test_protected_environment_required(self):
        self.assertIn("environment: android-release", self.workflow)
        self.assertIn("persist-credentials: false", self.workflow)

    def test_credentials_not_in_job_wide_env(self):
        before = self.workflow.split("\n    steps:", 1)[0]
        self.assertNotIn("secrets.LOCALPILOT_RELEASE_KEYSTORE_B64", before)
        self.assertNotIn("secrets.LOCALPILOT_RELEASE_STORE_PASSWORD", before)

    def test_private_key_only_decoded_in_temporary_file(self):
        self.assertIn("base64.b64decode(", self.workflow)
        self.assertIn("path.chmod(0o600)", self.workflow)
        self.assertIn("runner.temp", self.workflow)
        self.assertIn("Delete private key material", self.workflow)

    def test_public_certificate_is_explicitly_pinned(self):
        self.assertIn("vars.LOCALPILOT_RELEASE_CERT_SHA256", self.workflow)
        self.assertIn("certificate differs from pinned signer", self.workflow)

    def test_stable_app_id_and_20_point_gate(self):
        self.assertIn("org\\.localpilot\\.android", self.workflow)
        self.assertIn("release_20point_audit.py", self.workflow)
        self.assertIn(":app:assembleRelease", self.workflow)

    def test_release_not_exposed_as_stable_publication(self):
        self.assertIn("QA-only", self.workflow)
        self.assertNotIn("gh release create", self.workflow)
        self.assertNotIn("softprops/action-gh-release", self.workflow)
        self.assertNotIn("upload-release-asset", self.workflow)

    def test_gradle_release_signing_is_opt_in_only(self):
        self.assertIn("System.getenv('LOCALPILOT_RELEASE_STORE_FILE')", self.gradle)
        self.assertIn("if (releaseInputs.any", self.gradle)
        self.assertIn("Incomplete protected release signing environment", self.gradle)
        self.assertIn("localpilotProduction", self.gradle)

    def test_no_signer_certificate_bypass(self):
        self.assertIn("enableV2Signing true", self.gradle)
        self.assertIn("enableV3Signing true", self.gradle)
        self.assertNotIn("storePassword '", self.gradle)
        self.assertNotIn("keyPassword '", self.gradle)


if __name__ == "__main__":
    unittest.main()
