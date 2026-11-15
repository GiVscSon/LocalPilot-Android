import hashlib
import io
from pathlib import Path
import sys
import unittest
import zipfile

sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
from nes_experience_archive_acceptance import verify_archive


class ArchiveEvidenceTests(unittest.TestCase):
    def archive(self, payload, manifest=None):
        raw=io.BytesIO()
        with zipfile.ZipFile(raw,'w') as z:
            text='LOCALPILOT_NES_EXPERIENCE_ARCHIVE_V1\nsha256,bytes,path\n'
            for n,b in payload.items():
                z.writestr(n,b);text+=f'{hashlib.sha256(b).hexdigest()},{len(b)},{n}\n'
            z.writestr('MANIFEST.csv',text if manifest is None else manifest)
        return raw.getvalue()

    def test_exact_copy(self):
        p={'console-ai-homebrew-goal-v1.log':b'synthetic fixture'}
        raw=self.archive(p)
        self.assertEqual(verify_archive(raw,p),hashlib.sha256(raw).hexdigest())

    def test_missing_original(self):
        with self.assertRaises(RuntimeError):verify_archive(self.archive({}),{'original':b'x'})

    def test_changed_original(self):
        with self.assertRaises(RuntimeError):verify_archive(self.archive({'original':b'bad'}),{'original':b'good'})

    def test_forged_manifest(self):
        with self.assertRaises(RuntimeError):verify_archive(self.archive({'original':b'good'},'forged'),{'original':b'good'})

    def test_unexpected_entry(self):
        with self.assertRaises(RuntimeError):verify_archive(self.archive({'original':b'good','extra':b'x'}),{'original':b'good'})
