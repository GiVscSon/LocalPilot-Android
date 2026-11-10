import copy
import pathlib
import sys
import unittest

sys.path.insert(0, str(pathlib.Path('tools').resolve()))
from verify_sim_autoplay_report import validate_seed_split


class SeedIsolation(unittest.TestCase):
    def fixture(self):
        games = ('BUBBLE', 'ARI', 'MATCH', 'PADDLE', 'TRAFFIC', 'FLAPPY',
                 'BOARD2048', 'HEXON', 'PROTECTED_SWAP', 'CAT_TOWER')
        report = dict(seed=20261108, train_episodes_per_game=8, evaluation_episodes_per_game=4)
        for key, offset, count in [('training_seeds', 0, 8), ('evaluation_seeds', 987654321, 4)]:
            report[key] = {game: [20261108 + offset + i * 1000003 + e * 9127 for e in range(count)]
                           for i, game in enumerate(games)}
        return report

    def test_actual_schedules_are_disjoint(self):
        validate_seed_split(self.fixture())

    def test_reused_training_seed_is_rejected(self):
        report = self.fixture()
        report['evaluation_seeds']['BUBBLE'][0] = report['training_seeds']['BUBBLE'][0]
        with self.assertRaises(ValueError):
            validate_seed_split(report)

    def test_missing_scene_and_changed_schedule_rejected(self):
        for key in ('training_seeds', 'evaluation_seeds'):
            report = self.fixture()
            del report[key]['HEXON']
            with self.assertRaises(ValueError):
                validate_seed_split(report)
