"""Missing, unprotected and permission-error metadata cannot enable signing."""
import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location('environment_guard',
    Path(__file__).resolve().parents[1] / 'tools/verify_signing_environment.py')
guard = importlib.util.module_from_spec(spec)
spec.loader.exec_module(guard)


class EnvironmentProtection(unittest.TestCase):
    def test_real_required_reviewer_passes(self):
        guard.verify({'name': 'android-signing', 'protection_rules': [
            {'type': 'required_reviewers', 'reviewers': [
                {'type': 'User', 'reviewer': {'id': 33153823}}]}]})

    def test_missing_unprotected_and_api_errors_rejected(self):
        cases = [None, {'message': 'Not Found'},
                 {'message': 'Resource not accessible by integration'},
                 {'name': 'other', 'protection_rules': []},
                 {'name': 'android-signing', 'protection_rules': []},
                 {'name': 'android-signing', 'protection_rules': [{'type': 'wait_timer'}]},
                 {'name': 'android-signing', 'protection_rules': [
                     {'type': 'required_reviewers', 'reviewers': []}]}]
        for data in cases:
            with self.subTest(data=data), self.assertRaises(ValueError):
                guard.verify(data)


if __name__ == '__main__':
    unittest.main()
