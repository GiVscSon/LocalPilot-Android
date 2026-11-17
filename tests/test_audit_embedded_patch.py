import base64
import hashlib
import importlib.util
from pathlib import Path
import subprocess
import tempfile
import unittest
import zlib

spec = importlib.util.spec_from_file_location('audit', Path(__file__).parents[1] / 'tools/audit_embedded_patch.py')
mod = importlib.util.module_from_spec(spec)
spec.loader.exec_module(mod)


class PatchAuditTest(unittest.TestCase):
    def test_conflict_is_reported_without_execution_or_changes(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            subprocess.run(['git', 'init', '-q', td], check=True)
            (root / 'engine').write_text('base')
            subprocess.run(['git', '-C', td, 'add', 'engine'], check=True)
            subprocess.run(['git', '-C', td, '-c', 'user.name=Test', '-c', 'user.email=test@example.invalid',
                            'commit', '-qm', 'base'], check=True)
            raw = b'old replacement'
            meta = {'size': len(raw), 'sha256': hashlib.sha256(raw).hexdigest(),
                    'zlib_b64': base64.b64encode(zlib.compress(raw)).decode()}
            script = root / 'patch.py'
            script.write_text("raise RuntimeError('must never execute')\nEMBEDDED_FILES = " + repr({'engine': meta}))
            (root / 'engine').write_text('new fixes')
            report = mod.audit(script, root, 'HEAD')
            self.assertEqual(report['conflicts'], 1)
            self.assertEqual((root / 'engine').read_text(), 'new fixes')
            self.assertFalse(report['applied'])
            meta['sha256'] = '0' * 64
            script.write_text('EMBEDDED_FILES = ' + repr({'engine': meta}))
            with self.assertRaisesRegex(ValueError, 'integrity'):
                mod.audit(script, root, 'HEAD')
            script.write_text('EMBEDDED_FILES = ' + repr({'../escape': meta}))
            with self.assertRaisesRegex(ValueError, 'Unsafe'):
                mod.audit(script, root, 'HEAD')


if __name__ == '__main__':
    unittest.main()
