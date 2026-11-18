"""Host-only Pocket profile/sampler tests. Synthetic fixtures, NOT game evidence."""
import sys
import unittest
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))
import run_real_nes_vision_acceptance as game

class PocketProfileTest(unittest.TestCase):
    def tearDown(self):
        game.select_model("qwen35")

    def test_catalogue_identity(self):
        game.select_model("pocket")
        assert game.REPO == "taobao-mnn/MNN-Pocket-0.3B-GGUF"
        assert game.MODEL == "MNN-Pocket-0.3B-Instruct-Q4_1.gguf"
        assert game.REVISION == "791b7e92fa790a5ca5bbd7604feb1ce1d2429d14"
        assert game.FILES[game.MODEL] == (191896672, "a281c127e388b16537fd3f5d31ab32d3f87739e6dc514ac1093c80d9eac8d097")

    def test_receipts_are_real_and_mandatory(self):
        line = ("I LocalPilotNative: controller_sampler=REQUESTED temperature=0.00 "
                "top_k=20 top_p=0.80 repeat=1.18 presence=0.00 seed=0 greedy=1\n")
        assert len(game.verified_controller_settings(line * 2, "pocket", 2)) == 2
        with self.assertRaises(ValueError):
            game.verified_controller_settings(line.replace(" greedy=1", "") * 2, "pocket", 2)
        with self.assertRaises(ValueError):
            game.verified_controller_settings(line * 2, "pocket", 3)

    def test_randomized_model_seed_reuse_fails(self):
        line = ("I LocalPilotNative: controller_sampler=LFM2_TEXT temperature=0.10 "
                "top_k=50 top_p=1.00 repeat=1.05 presence=0.00 seed=42\n")
        with self.assertRaises(ValueError):
            game.verified_controller_settings(line * 2, "lfm350", 2)

if __name__ == "__main__":
    unittest.main()
