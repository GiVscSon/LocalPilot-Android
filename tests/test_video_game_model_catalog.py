import hashlib
from pathlib import Path
import sys
import tempfile
import unittest
from unittest import mock

sys.path.insert(0,str(Path(__file__).resolve().parents[1]/"tools"))
import fetch_video_game_models as v


class OriginalVideoModelsTests(unittest.TestCase):
    def test_catalog_covers_all_sixteen_released_game_models(self):
        self.assertEqual(len(v.GRID_PPO),4)
        self.assertEqual(len(v.KINETIX),2)
        self.assertEqual(len(v.MINATAR),3)
        self.assertEqual(len(v.GO_DIRS["go_astra"]),4)
        self.assertEqual(len(v.GO_DIRS["go_ppo"]),3)
        self.assertEqual(len(v.GO_FILES),15)
        self.assertEqual(len(v.NPZ_SHA),9)
        self.assertEqual(len(set(v.NPZ_SHA)),9)

    def test_git_blob_header_matches_reference(self):
        for payload in (b"",b"hello world",bytes(range(256))):
            expected=hashlib.sha1(b"blob "+str(len(payload)).encode()+bytes([0])+payload).hexdigest()
            self.assertEqual(v.git_blob(payload),expected)

    def test_pinned_npz_filenames_are_verified(self):
        tree={p:{"sha":sha,"size":100} for p,sha in v.NPZ_SHA.items()}
        for group in ("grid_ppo","kinetix","minatar_astra"):
            self.assertEqual(
                len(v.list_assets(tree,[group])),
                {"grid_ppo":4,"kinetix":2,"minatar_astra":3}[group])
        tampered=dict(tree)
        tampered[v.GRID_PPO[0]]={"sha":"f"*40,"size":100}
        with self.assertRaisesRegex(ValueError,"Pinned original model differs"):
            v.list_assets(tampered,["grid_ppo"])

    def test_go_weights_require_14_tensors_and_arch(self):
        tree={}
        for group,prefixes in v.GO_DIRS.items():
            for prefix in prefixes:
                for name in v.GO_FILES:
                    tree[prefix+name]={"sha":"a"*40,"size":123}
        items=v.list_assets(tree,["go_astra","go_ppo"])
        self.assertEqual(len(items),7*15)
        tree.pop(v.GO_DIRS["go_astra"][0]+"out.b.npy")
        with self.assertRaisesRegex(ValueError,"Published model missing"):
            v.list_assets(tree,["go_astra"])

    def test_only_verified_downloads_are_written(self):
        contents=b"synthetic-model-bits"
        sha=v.git_blob(contents)
        with tempfile.TemporaryDirectory() as folder:
            out=Path(folder)
            with mock.patch.object(v,"request_bytes",return_value=contents) as downloaded:
                result=v.save_one(out,"game/policy.npz",sha,len(contents))
                self.assertEqual(result["status"],"downloaded_verified")
                self.assertEqual((out/"game/policy.npz").read_bytes(),contents)
                again=v.save_one(out,"game/policy.npz",sha,len(contents))
                self.assertEqual(again["status"],"already_verified")
                self.assertEqual(downloaded.call_count,1)
            with mock.patch.object(v,"request_bytes",return_value=b"not right"):
                with self.assertRaisesRegex(ValueError,"MODEL INTEGRITY ERROR"):
                    v.save_one(out,"bad/policy.npz",sha,len(contents))
            self.assertFalse((out/"bad/policy.npz").exists())


if __name__=="__main__":
    unittest.main()
