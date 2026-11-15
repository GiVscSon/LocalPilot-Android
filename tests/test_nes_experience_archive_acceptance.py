import hashlib
import io
from pathlib import Path
import sys
import unittest
from unittest.mock import patch
import zipfile

sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
from nes_experience_archive_acceptance import verify_archive, cancel_documents_picker


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

    def test_cancel_navigates_only_verified_documents_windows(self):
        import nes_experience_archive_acceptance as module
        documents='<hierarchy><node package="com.android.documentsui"/></hierarchy>'
        app='<hierarchy><node package="'+module.ui.PACKAGE+'"/></hierarchy>'
        with patch.object(module.ui,'resumed_activity',side_effect=RuntimeError('No resumed activity in the installed LocalPilot test package')), \
             patch.object(module.ui,'dump_ui',side_effect=[documents,documents,app]), \
             patch.object(module.ui,'wait_activity') as resumed, \
             patch.object(module.ui,'adb') as adb,patch.object(module.time,'sleep'):
            cancel_documents_picker()
        self.assertEqual(adb.call_count,2)
        resumed.assert_called_once_with(module.ui.DASH)

    def test_cancel_refuses_unknown_foreground(self):
        import nes_experience_archive_acceptance as module
        with patch.object(module.ui,'resumed_activity',return_value=''), \
             patch.object(module.ui,'dump_ui',return_value='<hierarchy><node package="unrelated.app"/></hierarchy>'), \
             patch.object(module.ui,'adb') as adb:
            with self.assertRaises(RuntimeError):cancel_documents_picker()
        adb.assert_not_called()
