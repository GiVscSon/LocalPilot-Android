"""Exercise actual simulator CSV output and reject mixed/fabricated score evidence."""
from pathlib import Path
import copy
import csv
import importlib.util
import json
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('sim_report_physics', ROOT / 'tools/verify_sim_autoplay_report.py')
validator = importlib.util.module_from_spec(spec)
spec.loader.exec_module(validator)


class PhysicsScoreEvidence(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        if not shutil.which('javac'):
            raise unittest.SkipTest('JDK compiler unavailable; actual simulator contract needs javac')
        cls.workspace = tempfile.TemporaryDirectory()
        cls.base = Path(cls.workspace.name) / 'original'
        run = subprocess.run(['bash', 'tools/run_sim_autoplay.sh', '--out', str(cls.base),
                              '--seed', '20261108', '--train-episodes', '1',
                              '--eval-episodes', '2', '--steps', '4'], cwd=ROOT,
                             text=True, capture_output=True, timeout=90)
        if run.returncode:
            raise RuntimeError('actual simulator contract failed: ' + run.stdout + run.stderr)

    @classmethod
    def tearDownClass(cls):
        cls.workspace.cleanup()

    def setUp(self):
        self.folder = Path(self.workspace.name) / self._testMethodName
        shutil.copytree(self.base, self.folder)

    def report(self):
        return json.loads((self.folder / 'report.json').read_text())

    def write_report(self, report):
        (self.folder / 'report.json').write_text(json.dumps(report))

    def csv_rows(self):
        with (self.folder / 'evaluation-random.csv').open(newline='') as file:
            return list(csv.DictReader(file))

    def write_csv(self, rows):
        with (self.folder / 'evaluation-random.csv').open('w', newline='') as file:
            writer = csv.DictWriter(file, fieldnames=validator.COLUMNS)
            writer.writeheader()
            writer.writerows(rows)

    def test_actual_physics_and_gesture_scores_are_separate(self):
        report = validator.validate(self.folder)
        paddle = next(row for row in report['games'] if row['game'] == 'PADDLE')
        self.assertGreater(paddle['random_physics_score'], 0)
        self.assertEqual(0, paddle['random_gesture_score'])
        self.assertEqual(paddle['random_physics_score'], paddle['random_mean_score'])

    def test_invented_json_mean_score_rejected(self):
        report = self.report()
        report['games'][0]['random_mean_score'] += 1
        self.write_report(report)
        with self.assertRaisesRegex(ValueError, 'score not supported'):
            validator.validate(self.folder)

    def test_invented_physics_attribution_rejected(self):
        report = self.report()
        report['games'][0]['random_physics_score'] += 1
        self.write_report(report)
        with self.assertRaisesRegex(ValueError, 'score not supported'):
            validator.validate(self.folder)

    def test_nonfinite_physics_attribution_rejected(self):
        report = self.report()
        report['games'][0]['random_physics_score'] = float('nan')
        self.write_report(report)
        with self.assertRaisesRegex(ValueError, 'score not supported'):
            validator.validate(self.folder)

    def test_duplicate_transition_cannot_substitute_missing_step(self):
        rows = self.csv_rows()
        rows[1] = copy.copy(rows[0])
        self.write_csv(rows)
        with self.assertRaisesRegex(ValueError, 'duplicate evaluation transition'):
            validator.validate(self.folder)

    def test_no_change_gesture_cannot_receive_physics_reward(self):
        rows = self.csv_rows()
        row = next(row for row in rows if row['result'] != 'CHANGED')
        row['score_after'] = str(int(row['score_before']) + 1)
        self.write_csv(rows)
        with self.assertRaisesRegex(ValueError, 'no-change input falsely earns score'):
            validator.validate(self.folder)

    def test_old_physics_report_is_not_new_rule_evidence(self):
        report = self.report()
        del report['physics_rules']
        self.write_report(report)
        with self.assertRaisesRegex(ValueError, 'physics measurement version'):
            validator.validate(self.folder)
