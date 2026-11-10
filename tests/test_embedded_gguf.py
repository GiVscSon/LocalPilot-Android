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
        self.report.write_text(json.dumps({"sha256":self.digest, "bytes":len(self.payload)}))
        self.patch = mock.patch.object(mod, "REPORT", self.report)
        self.patch.start()
        self.addCleanup(self.patch.stop)

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

if __name__ == "__main__":
    unittest.main()
