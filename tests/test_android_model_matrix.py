import importlib
from pathlib import Path
import sys
import tempfile
import unittest

sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
from model_lab_catalog import presets
import run_real_nes_vision_acceptance as game


class AndroidModelMatrixTest(unittest.TestCase):
    def tearDown(self):
        game.select_model('qwen35')

    def test_each_android_preset_selects_its_own_pinned_weights(self):
        catalog=presets()
        self.assertGreaterEqual(len(catalog),4)
        for key,entry in catalog.items():
            game.select_model(key)
            self.assertEqual(game.MODEL_ID,key)
            self.assertEqual(game.FILES[game.MODEL],(entry['bytes'],entry['sha256']))
            self.assertEqual(game.REVISION,entry['revision'])
            self.assertEqual(game.REPO,entry['repository'])
        self.assertEqual(len({row['sha256'] for row in catalog.values()}),len(catalog))

    def test_catalog_addition_cannot_silently_escape_the_matrix(self):
        with tempfile.TemporaryDirectory() as td:
            path=Path(td)/'catalog.java'
            path.write_text('new Preset("new-syntax", unsupported())')
            with self.assertRaises(ValueError):presets(path)

    def test_unknown_model_cannot_fall_back_to_qwen(self):
        with self.assertRaises(KeyError):game.select_model('missing-model')


if __name__=='__main__':unittest.main()
