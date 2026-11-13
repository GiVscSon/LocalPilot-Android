"""Structural ELF fixtures are synthetic; actual APK check is a separate CI gate."""
from pathlib import Path
import struct
import sys
import unittest
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
import verify_android_elf as gate

class ElfCompatibility(unittest.TestCase):
 def elf(self,align=16384,address=0,size=120):
  data=bytearray(120);data[:7]=b'\x7fELF\x02\x01\x01'
  struct.pack_into('<HHI',data,16,3,183,1)
  struct.pack_into('<Q',data,32,64)
  struct.pack_into('<HHH',data,52,64,56,1)
  struct.pack_into('<IIQQQQQQ',data,64,1,5,0,address,0,size,size,align)
  return data
 def test_arm64_16k_load(self):
  self.assertEqual(gate.inspect_elf(self.elf(),183)['minimumLoadAlignment'],16384)
 def test_zip_alignment_cannot_fix_4k_elf(self):
  for data in [self.elf(4096),self.elf(address=4096),self.elf(size=121),self.elf(20000),b'unit test']:
   with self.subTest(data=data[:20]),self.assertRaises(ValueError):gate.inspect_elf(data,183)
 def test_machine_and_header_table_are_not_assumed(self):
  with self.assertRaises(ValueError):gate.inspect_elf(self.elf(),62)
  data=self.elf();struct.pack_into('<H',data,56,128)
  with self.assertRaises(ValueError):gate.inspect_elf(data,183)
if __name__=='__main__':unittest.main()
