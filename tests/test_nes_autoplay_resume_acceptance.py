"""Negative admission tests; these synthetic bytes are not emulator evidence."""
from pathlib import Path
import hashlib
import json
import struct
import sys
import unittest
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
import nes_autoplay_resume_acceptance as runtime


class SaveEvidence(unittest.TestCase):
    def fixture(self,**changes):
        fields=dict(core='84946f0dae4bcf3b2dfebe84ad3bfeb1dbcb413bd9b1a69e1f45910a95fd7b41',
                    frame=301,pixel=-123,actions=32,policy='a'*64)
        fields.update(changes)
        def utf(text):
            b=text.encode('ascii');return struct.pack('>H',len(b))+b
        rom=b'NES\x1a'+bytes(12)
        state=json.dumps(dict(schema='localpilot-jsnes-save-v1',frame=fields['frame'],hash=fields['pixel'],machine={})).encode()
        data=b'NSV1'+utf(fields['core'])+struct.pack('>ii?q',fields['frame'],fields['pixel'],False,fields['actions'])+utf(fields['policy'])
        data+=hashlib.sha256(rom).digest()+struct.pack('>i',len(rom))+rom+struct.pack('>i',len(state))+state
        return data+hashlib.sha256(data).digest()

    def test_reads_bounded_metadata(self):
        saved=runtime.decode(self.fixture())
        self.assertEqual((saved['frame'],saved['actions']),(301,32))

    def test_rejects_corrupt_truncated_and_wrong_identity(self):
        raw=self.fixture()
        for bad in (raw[:-1],raw[:50],raw+b'extra',raw[:100]+b'x'+raw[101:],self.fixture(core='b'*64),self.fixture(frame=1),self.fixture(pixel=0),self.fixture(actions=-1)):
            with self.subTest(length=len(bad)),self.assertRaises(Exception):runtime.decode(bad)

    def test_rejects_oversized_file_before_parsing(self):
        with self.assertRaises(RuntimeError):runtime.decode(b'x'*(7*1024*1024+4097))


if __name__=='__main__':unittest.main()
