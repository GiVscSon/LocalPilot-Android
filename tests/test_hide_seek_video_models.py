import hashlib
import sys
import tempfile
from pathlib import Path
from unittest import TestCase, main, mock

sys.path.insert(0,str(Path(__file__).resolve().parents[1]/"tools"))
import fetch_hide_seek_video_models as models


class HideSeekVideoModelTests(TestCase):
    def test_original_model_inventory(self):
        self.assertEqual(len(models.FILES),12)
        self.assertEqual(len(set(models.FILES)),12)
        self.assertEqual(sum(x.startswith("examples/hide_and_seek_policy_phases/") for x in models.FILES),5)
        self.assertEqual(len(models.LICENCE),1)
        self.assertTrue(all(len(x)==40 and all(c in "0123456789abcdef" for c in x)
                            for x in models.FILES.values()))

    def test_git_blob_digest_matches_git_object_format(self):
        for payload in (b"",b"OpenAI hide and seek",bytes(range(256))):
            expected=hashlib.sha1(b"blob "+str(len(payload)).encode()+bytes([0])+payload).hexdigest()
            self.assertEqual(models.git_blob_sha(payload),expected)

    def test_cannot_persist_modified_weights(self):
        original=b"public-example-weights"
        with tempfile.TemporaryDirectory() as folder:
            output=Path(folder)
            sha=models.git_blob_sha(original)
            with mock.patch.object(models,"get",return_value=b"tampered"):
                with self.assertRaisesRegex(ValueError,"SHA mismatch"):
                    models.fetch_checked(output,"examples/hide_and_seek_full.npz",sha)
            self.assertFalse((output/"examples/hide_and_seek_full.npz").exists())
            with mock.patch.object(models,"get",return_value=original):
                saved=models.fetch_checked(output,"examples/hide_and_seek_full.npz",sha)
            self.assertEqual(saved["sha256"],hashlib.sha256(original).hexdigest())
            with mock.patch.object(models,"get",side_effect=AssertionError("should use local copy")):
                cached=models.fetch_checked(output,"examples/hide_and_seek_full.npz",sha)
            self.assertEqual(cached["status"],"verified_existing")

    def test_rejects_unsafe_path(self):
        with tempfile.TemporaryDirectory() as folder:
            with self.assertRaisesRegex(ValueError,"unsafe asset path"):
                models.fetch_checked(Path(folder),"../outside.npz","a"*40)


if __name__=="__main__":
    main()
