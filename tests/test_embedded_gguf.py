import hashlib
import json
import pathlib
import tempfile
import unittest
import zipfile
from unittest import mock

from tools import bundle_embedded_gguf as mod

class EmbeddedModelPackagingTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        root = pathlib.Path(self.temp.name)
        self.apk = root / "sample.apk"
        self.report = root / "model.json"
        self.payload = b"GGUF" + b"\x00" * 256
        self.digest = hashlib.sha256(self.payload).hexdigest()
        self.report.write_text(json.dumps({
            "sha256":self.digest, "bytes":len(self.payload), "revision":"test-pinned-revision"
        }))
        for attr, value in (
            ("REPORT",self.report),
            ("EXPECTED_SHA256",self.digest),
            ("EXPECTED_BYTES",len(self.payload)),
            ("REVISION","test-pinned-revision"),
        ):
            patch = mock.patch.object(mod,attr,value)
            patch.start()
            self.addCleanup(patch.stop)

    def pack(self, *, model=True, library=True, license=True, stored=True, contents=None):
        with zipfile.ZipFile(self.apk, "w") as out:
            if model:
                out.writestr(mod.ASSET, contents if contents is not None else self.payload,
                             compress_type=zipfile.ZIP_STORED if stored else zipfile.ZIP_DEFLATED)
            if library:
                out.writestr("lib/arm64-v8a/liblocalpilot_embedded.so", b"ELF")
            if license:
                out.writestr("assets/licenses/MNN-Pocket-LICENSE.txt",b"Apache License")
                out.writestr("assets/licenses/llama.cpp-LICENSE.txt",b"MIT License")

    def test_correct_apk(self):
        self.pack()
        mod.verify(self.apk)

    def test_missing_model_rejected(self):
        self.pack(model=False)
        with self.assertRaises(ValueError):
            mod.verify(self.apk)

    def test_missing_native_library_rejected(self):
        self.pack(library=False)
        with self.assertRaises(ValueError):
            mod.verify(self.apk)

    def test_missing_license_rejected(self):
        self.pack(license=False)
        with self.assertRaises(ValueError):
            mod.verify(self.apk)

    def test_compressed_model_rejected(self):
        self.pack(stored=False)
        with self.assertRaises(ValueError):
            mod.verify(self.apk)

    def test_corrupt_model_rejected(self):
        self.pack(contents=b"GGUF" + b"X"*256)
        with self.assertRaises(ValueError):
            mod.verify(self.apk)

    def test_forged_metadata_rejected(self):
        self.pack()
        self.report.write_text(json.dumps({
            "sha256":hashlib.sha256(b"other").hexdigest(),
            "bytes":len(self.payload),
            "revision":"test-pinned-revision"
        }))
        with self.assertRaises(ValueError):
            mod.verify(self.apk)

    def test_wrong_revision_rejected(self):
        self.pack()
        self.report.write_text(json.dumps({
            "sha256":self.digest,
            "bytes":len(self.payload),
            "revision":"wrong-revision"
        }))
        with self.assertRaises(ValueError):
            mod.verify(self.apk)

if __name__ == "__main__":
    unittest.main()
