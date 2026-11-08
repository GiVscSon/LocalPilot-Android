"""Regression tests for scripted-reference and random-baseline lab gating."""
import pathlib
import sys
import unittest

sys.path.insert(0, str(pathlib.Path("tools").resolve()))
from gate_sim_autoplay_candidates import classify


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


if __name__ == "__main__":
    unittest.main()
