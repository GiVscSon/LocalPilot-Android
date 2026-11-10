import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile
import unified_pipeline as pipeline
import write_build_provenance


class EmbeddedProvenance(unittest.TestCase):
    def apk(self, path, metadata):
        with zipfile.ZipFile(path, 'w') as archive:
            for index in range(16):
                archive.writestr(f'assets/adaptive-engine-v1/model{index}.zip', b'fixture')
            for name in ('jsnes.min.js', 'index.html', 'latched-buttons.js', 'nes-observation.js'):
                archive.writestr('assets/nes/' + name, b'fixture')
            archive.writestr('assets/localpilot-game-script.lps', b'LPS1\n' + b'model|fixture\n' * 16)
            if metadata is not None:
                archive.writestr('assets/localpilot-build.json', json.dumps(metadata))

    def test_build_writer_uses_actual_revision(self):
        with tempfile.TemporaryDirectory() as folder, patch.object(write_build_provenance, 'source_revision', return_value=('a'*40, 'b'*64)):
            target = Path(folder) / 'assets/localpilot-build.json'
            metadata = write_build_provenance.write(target)
            self.assertEqual('a'*40, metadata['git_sha'])
            self.assertEqual(metadata, json.loads(target.read_text()))
            self.assertFalse(target.with_suffix('.pending').exists())

    def test_wrong_or_missing_apk_revision_rejected(self):
        with tempfile.TemporaryDirectory() as folder, patch.object(pipeline, 'source_revision', return_value=('a'*40, 'b'*64)), patch.object(pipeline, 'sdk_tool', return_value=None):
            for metadata in (None, {'schema':'localpilot-build-provenance-v1', 'git_sha':'c'*40, 'source_sha256':'b'*64}, {'schema':'localpilot-build-provenance-v1', 'git_sha':'a'*40, 'source_sha256':'c'*64}):
                apk = Path(folder) / 'fixture.apk'
                self.apk(apk, metadata)
                with self.assertRaises((RuntimeError, KeyError)):
                    pipeline.verify_apk(apk, False)
