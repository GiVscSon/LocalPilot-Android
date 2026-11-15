"""Negative and positive tests for six-device evidence matrix. No emulator is mocked as physical."""
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

SOURCE = Path(__file__).resolve().parents[1] / 'tools/summarize_android_matrix.py'
spec = importlib.util.spec_from_file_location('matrix', SOURCE)
matrix = importlib.util.module_from_spec(spec)
spec.loader.exec_module(matrix)
SHA = 'a' * 40
POLICY_BEFORE = 'b' * 64
POLICY_AFTER = 'c' * 64
APK = 'd' * 64


def populate(root):
    for api in matrix.APIS:
        for profile in matrix.PROFILES:
            directory = root / f'LocalPilot-One-APK-Emulator-UI-API{api}-{profile}-{SHA}'
            directory.mkdir()
            data = {
                matrix.REPORTS[0]: {'schema': 'localpilot-unified-device-ui-test-v1',
                                    'emulatorOnly': True, 'singleProcess': True,
                                    'checks': [{'status': 'PASS'} for _ in range(6)],
                                    'releaseReady': False},
                matrix.REPORTS[1]: {'sourceSha': SHA, 'api': api, 'releaseReady': False,
                                    'result': 'PASS', 'replaySamples': 16, 'singlePackage': True,
                                    'evaluation': 'NOT_RUN', 'policyBeforeSha256': POLICY_BEFORE,
                                    'policyAfterSha256': POLICY_AFTER, 'apkSha256': APK},
                matrix.REPORTS[2]: {'sourceSha': SHA, 'api': api, 'releaseReady': False,
                                    'result': 'NO_PROVEN_IMPROVEMENT', 'scenarioCount': 6,
                                    'pairedScenarios': [{'scenario': 'smoke'+str(i)} for i in range(6)],
                                    'physicalAndroidVerified': False,
                                    'promotion': 'BLOCKED_UNTIL_DEVICE_HOLDOUT_AND_ROLLBACK',
                                    'androidWeightsAndManifestUnchanged': True,
                                    'evaluationRuntime': 'HOST_REAL_JSNES_NOT_ANDROID',
                                    'activePolicySha256': POLICY_BEFORE,
                                    'candidatePolicySha256': POLICY_AFTER, 'apkSha256': APK},
                matrix.REPORTS[3]: {'sourceSha': SHA, 'api': api, 'releaseReady': False,
                                    'result': 'PASS', 'stopBlocksFurtherCompletedActions': True,
                                    'candidatePreserved': True, 'activePolicyPreserved': True,
                                    'saved': {'actions': 30}, 'resumed': {'actions': 46},
                                    'apkSha256': APK},
                matrix.REPORTS[4]: {'sourceSha': SHA, 'api': api, 'releaseReady': False,
                                    'result': 'PASS', 'originalBytesPreserved': True,
                                    'gameAndWeightsPreserved': True,
                                    'safExportReadbackVerified': True},
            }
            for name, row in data.items():
                (directory / name).write_text(json.dumps(row))


class MatrixEvidenceTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        populate(self.root)

    def test_real_emulator_evidence_not_mislabeled_release(self):
        report = matrix.inspect(self.root, SHA)
        self.assertEqual('EMULATOR_MATRIX_PASS', report['result'])
        self.assertEqual(6, len(report['matrix']))
        self.assertEqual(APK, report['apkSha256'])
        self.assertEqual('UNKNOWN', report['evidence']['physicalPoco'])
        self.assertEqual('NOT_PROVEN', report['evidence']['holdoutCandidateImprovement'])
        self.assertFalse(report['releaseReady'])

    def test_missing_one_artifact_is_fail(self):
        first = next(iter(self.root.iterdir()))
        (first / matrix.REPORTS[1]).unlink()
        report = matrix.inspect(self.root, SHA)
        self.assertEqual('FAIL', report['result'])
        self.assertTrue(any(x['status'] == 'FAIL' for x in report['matrix']))

    def test_source_sha_mismatch_is_fail(self):
        first = next(iter(self.root.iterdir()))
        file = first / matrix.REPORTS[1]
        row = json.loads(file.read_text())
        row['sourceSha'] = 'e' * 40
        file.write_text(json.dumps(row))
        self.assertEqual('FAIL', matrix.inspect(self.root, SHA)['result'])

    def test_false_accepted_holdout_is_rejected_without_proof(self):
        first = next(iter(self.root.iterdir()))
        file = first / matrix.REPORTS[2]
        row = json.loads(file.read_text())
        row['result'] = 'FAKE_ACCEPTED'
        file.write_text(json.dumps(row))
        self.assertEqual('FAIL', matrix.inspect(self.root, SHA)['result'])

    def test_holdout_missing_scenarios_is_fail(self):
        first = next(iter(self.root.iterdir()))
        file = first / matrix.REPORTS[2]
        row = json.loads(file.read_text())
        row['pairedScenarios'] = row['pairedScenarios'][:3]
        file.write_text(json.dumps(row))
        self.assertEqual('FAIL', matrix.inspect(self.root, SHA)['result'])

    def test_holdout_cannot_claim_promoted_without_evaluation(self):
        first = next(iter(self.root.iterdir()))
        file = first / matrix.REPORTS[2]
        row = json.loads(file.read_text())
        row['promotion'] = 'PROMOTED'
        file.write_text(json.dumps(row))
        self.assertEqual('FAIL', matrix.inspect(self.root, SHA)['result'])

    def test_inconsistent_apk_bytes_is_fail(self):
        first = next(iter(self.root.iterdir()))
        file = first / matrix.REPORTS[3]
        row = json.loads(file.read_text())
        row['apkSha256'] = 'e' * 64
        file.write_text(json.dumps(row))
        self.assertEqual('FAIL', matrix.inspect(self.root, SHA)['result'])

    def test_unknown_missing_report_does_not_pass(self):
        first = next(iter(self.root.iterdir()))
        file = first / matrix.REPORTS[0]
        file.write_text('{')
        self.assertEqual('FAIL', matrix.inspect(self.root, SHA)['result'])


if __name__ == '__main__':
    unittest.main()
