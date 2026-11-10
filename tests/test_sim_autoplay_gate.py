"""Regression tests for scripted-reference and random-baseline lab gating."""
import pathlib
import json
import shutil
import subprocess
import sys
import tempfile
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))
from gate_sim_autoplay_candidates import SEEDS, classify, produce


class GateRules(unittest.TestCase):
    def test_lab_candidate_requires_consistent_improvement(self):
        self.assertEqual(
            classify([10, 9, 8], [2, 3, 1], [-4, -5, -3], [-2, -1, -3]),
            "LAB_CANDIDATE_ONLY",
        )

    def test_any_material_regression_vetoes(self):
        self.assertEqual(
            classify([20, -4, 12], [2, 1, 3], [-10, -10, -10], [-1, -1, -1]),
            "LAB_QUARANTINED_REGRESSION",
        )

    def test_scripted_reference_blocks_weak_candidate(self):
        self.assertEqual(
            classify([10, 12, 15], [2, 3, 4], [6, 10, 1], [1, 2, 0]),
            "LAB_BELOW_SCRIPTED_REFERENCE",
        )

    def test_insufficient_gain_is_not_success(self):
        self.assertEqual(
            classify([0, 2, 1], [0, 1, 0], [0, 0, 0], [0, 0, 0]),
            "LAB_INCONCLUSIVE",
        )

    def test_invalid_or_missing_multiseed_data_rejected(self):
        with self.assertRaises(ValueError):
            classify([10, 10], [1, 1], [0, 0], [0, 0])
        with self.assertRaises(ValueError):
            classify([float("nan")] * 3, [1] * 3, [0] * 3, [0] * 3)


class MeasuredGateSources(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        if not shutil.which("javac"):
            raise unittest.SkipTest("JDK compiler unavailable; measured gate needs javac")
        cls.workspace = tempfile.TemporaryDirectory()
        cls.original = pathlib.Path(cls.workspace.name) / "original"
        for seed in SEEDS:
            run = subprocess.run(
                ["bash", "tools/run_sim_autoplay.sh", "--out",
                 str(cls.original / f"seed-{seed}"), "--seed", str(seed),
                 "--train-episodes", "1", "--eval-episodes", "2", "--steps", "4"],
                cwd=ROOT, text=True, capture_output=True, timeout=90)
            if run.returncode:
                raise RuntimeError("actual simulator gate fixture failed: " + run.stdout + run.stderr)

    @classmethod
    def tearDownClass(cls):
        cls.workspace.cleanup()

    def setUp(self):
        self.base = pathlib.Path(self.workspace.name) / self._testMethodName
        shutil.copytree(self.original, self.base)

    def test_actual_multiseed_csv_evidence_is_accepted_for_lab_only(self):
        gate = produce(self.base)
        self.assertEqual(10, len(gate["decisions"]))
        self.assertEqual(list(SEEDS), gate["evaluated_seeds"])
        self.assertFalse(gate["may_dispatch_real_gestures"])
        self.assertTrue(all(row["may_change_real_model"] is False for row in gate["decisions"]))

    def test_old_physics_middle_seed_requires_rerun(self):
        path = self.base / f"seed-{SEEDS[1]}" / "report.json"
        report = json.loads(path.read_text())
        del report["physics_rules"]
        path.write_text(json.dumps(report))
        with self.assertRaisesRegex(ValueError, "physics measurement version"):
            produce(self.base)

    def test_json_only_gain_in_last_seed_is_rejected(self):
        path = self.base / f"seed-{SEEDS[-1]}" / "report.json"
        report = json.loads(path.read_text())
        report["games"][0]["lab_mean_score"] += 100
        path.write_text(json.dumps(report))
        with self.assertRaisesRegex(ValueError, "score not supported"):
            produce(self.base)


if __name__ == "__main__":
    unittest.main()
