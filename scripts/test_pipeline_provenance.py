import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch
import unified_pipeline as pipeline


class SourceProvenance(unittest.TestCase):
    def test_revision_and_dirty_contents_scope_evidence(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            subprocess.run(['git', 'init', '-q', folder], check=True)
            source = root / 'source.java'
            source.write_text('version one')
            subprocess.run(['git', '-C', folder, 'add', '.'], check=True)
            subprocess.run(['git', '-C', folder, '-c', 'user.name=Test', '-c',
                            'user.email=test@example.invalid', 'commit', '-qm', 'fixture'], check=True)
            with patch.object(pipeline, 'ROOT', root), patch.dict(os.environ, {}, clear=True):
                head, fingerprint = pipeline.source_revision()
                self.assertEqual(40, len(head))
                self.assertEqual((head, fingerprint), pipeline.source_revision())
                source.write_text('version two')
                self.assertNotEqual(fingerprint, pipeline.source_revision()[1])
                with patch.dict(os.environ, {'GITHUB_SHA': '0' * 40}):
                    with self.assertRaisesRegex(RuntimeError, 'HEAD'):
                        pipeline.source_revision()
