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

    def test_mid_run_source_change_rejects_evidence(self):
        with tempfile.TemporaryDirectory() as folder:
            head = "a" * 40
            with patch.object(pipeline, 'source_revision', side_effect=[(head, 'old'), (head, 'changed')]), \
                 patch.object(pipeline, 'verify_contracts', return_value={'contract_checks': 'PASS'}), \
                 patch('sys.argv', ['unified_pipeline.py', 'verify', '--out', folder]):
                self.assertEqual(1, pipeline.main())
            import json
            report = json.loads((Path(folder) / 'unified-pipeline.json').read_text())
            self.assertEqual('FAIL', report['result'])
            self.assertFalse(report['complete'])
            self.assertIn('Source changed', report['reason'])
