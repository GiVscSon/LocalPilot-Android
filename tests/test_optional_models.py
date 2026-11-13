#!/usr/bin/env python3
"""Small offline tests for exact immutable HF provenance and APK integrity."""
from __future__ import annotations
import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile

MODULE_PATH=Path(__file__).resolve().parents[1]/"tools"/"bundle_optional_models.py"
spec=importlib.util.spec_from_file_location("localpilot_optional_models", MODULE_PATH)
bundler=importlib.util.module_from_spec(spec)
spec.loader.exec_module(bundler)


class OptionalModelsIntegrityTest(unittest.TestCase):
    def setUp(self):
        self.dir=tempfile.TemporaryDirectory()
        self.addCleanup(self.dir.cleanup)
        self.root=Path(self.dir.name)
        self.pinned={
            name:(len(b"GGUF"+bytes((i+1,))*8),
                  hashlib.sha256(b"GGUF"+bytes((i+1,))*8).hexdigest())
            for i,name in enumerate(bundler.PINNED_FILES)
        }
        self.payloads={
            name:b"GGUF"+bytes((i+1,))*8 for i,name in enumerate(self.pinned)
        }
        self.rows=[
            dict(id=model_id, repository=repo,
                 revision=bundler.PINNED_REVISIONS[repo], filename=name,
                 projector=projector, bytes=self.pinned[name][0],
                 sha256=self.pinned[name][1])
            for model_id,repo,name,projector in bundler.EXTRAS
        ]
        self.metadata={
            "schema":"localpilot-extra-models-v1",
            "files":self.rows,
            "total_bytes":sum(v[0] for v in self.pinned.values()),
        }
        self.report=self.root/"report.json"
        self.apk=self.root/"candidate.apk"
        self.patcher=patch.multiple(bundler, PINNED_FILES=self.pinned, REPORT=self.report)
        self.patcher.start()
        self.addCleanup(self.patcher.stop)

    def build(self, override=None, omit=None):
        self.report.write_text(json.dumps(self.metadata))
        with zipfile.ZipFile(self.apk,"w",compression=zipfile.ZIP_STORED) as out:
            out.writestr("assets/model-catalog.json",json.dumps(self.metadata))
            for name,body in self.payloads.items():
                if name!=omit:
                    out.writestr("assets/models/"+name,
                                 override if name==next(iter(self.payloads)) and override is not None else body)
            for name in ("Qwen3.5-LICENSE.txt","LFM2.5-350M-LICENSE.txt",
                         "LFM2.5-VL-450M-LICENSE.txt"):
                out.writestr("assets/licenses/"+name,"Test license placeholder")

    def test_valid_verified_assets(self):
        self.build()
        bundler.verify(self.apk)

    def test_model_tampering_rejected(self):
        self.build(override=b"GGUFtampered")
        with self.assertRaisesRegex(ValueError,"APK weight mismatch"):
            bundler.verify(self.apk)

    def test_forged_model_hash_rejected(self):
        self.metadata["files"][0]["sha256"]="0"*64
        self.build()
        with self.assertRaisesRegex(ValueError,"Forged or unpinned"):
            bundler.verify(self.apk)

    def test_missing_projector_rejected(self):
        name=list(self.pinned)[-1]
        self.build(omit=name)
        with self.assertRaises(KeyError):
            bundler.verify(self.apk)

    def test_changed_revision_rejected(self):
        self.metadata["files"][0]["revision"]="f"*40
        self.build()
        with self.assertRaisesRegex(ValueError,"Forged or unpinned"):
            bundler.verify(self.apk)

    def test_manifest_does_not_match_report(self):
        self.build()
        with zipfile.ZipFile(self.apk,"a") as apk:
            apk.writestr("assets/model-catalog.json","{}")
        with self.assertRaisesRegex(ValueError,"APK catalogue differs"):
            bundler.verify(self.apk)


if __name__=="__main__":
    unittest.main()
