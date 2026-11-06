"""Offline provenance checks: never conflate a video, its author, and a different checkpoint."""
import hashlib
import json
from pathlib import Path
import unittest

REGISTRY=Path(__file__).resolve().parents[1]/"docs"/"VIDEO_THREE_NEW_GAME_MODELS.json"


class ThreeVideoGameModelsTests(unittest.TestCase):
    def test_explicit_source_video_identity(self):
        data=json.loads(REGISTRY.read_text(encoding="utf-8"))
        self.assertEqual(data["schema"],"localpilot-video-game-model-sources-v1")
        videos={x["id"]:x for x in data["videos"]}
        self.assertEqual(set(videos),{"HR3Qr9WLpmQ","KerqrfSUtFk","SAzDZhSSa_c"})
        self.assertEqual(sum(bool(x["original_checkpoint_confirmed"]) for x in videos.values()),1)
        self.assertFalse(data["policy"]["active_android_models_changed"])

    def test_pinned_original_nitrogen_only(self):
        source=json.loads(REGISTRY.read_text(encoding="utf-8"))["videos"][0]
        model=source["model"]
        self.assertEqual(model["id"],"nvidia/NitroGen/ng.pt")
        self.assertTrue(model["url"].startswith("https://huggingface.co/nvidia/NitroGen/blob/73a12a"))
        self.assertEqual(model["sha256"],
                         "a266f5fb9c7dbdcdf97216558d2d82075a9a994b824cda69afa9fd3280260a81")
        self.assertEqual(len(bytes.fromhex(model["sha256"])),32)
        self.assertIn("non-commercial",model["license"])
        self.assertFalse(source["downloaded"])
        self.assertFalse(source["compatible_with_android_touch"])

    def test_other_developers_not_mislabeled_as_video_authors(self):
        data=json.loads(REGISTRY.read_text(encoding="utf-8"))
        for item in data["videos"][1:]:
            self.assertFalse(item["original_checkpoint_confirmed"])
            self.assertFalse(item["downloaded"])
            self.assertTrue(item["unverified_reason"])
            self.assertIn("related_but_not_original",item)
        self.assertEqual(data["policy"]["confirmed_total_original_checkpoint_sets"],29)
        self.assertFalse(data["policy"]["all_files_downloaded"])


if __name__=="__main__":
    unittest.main()
