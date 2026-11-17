import sys
from pathlib import Path
import unittest
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
from verify_native_inference_optimization import verify,TARGETS

class NativeCompilerOptions(unittest.TestCase):
    def rows(self,flags):
        return [dict(file='/source/'+x,command='clang++ -g '+flags+' -c /source/'+x) for x in TARGETS]
    def test_optimization_order_is_effective(self):
        self.assertEqual(verify(self.rows('-O0 -O2'))['result'],'NATIVE_COMPILER_OPTIONS_PASS')
        with self.assertRaises(ValueError):verify(self.rows('-O2 -O0'))
    def test_absence_and_missing_kernel_fail_closed(self):
        with self.assertRaises(ValueError):verify(self.rows(''))
        with self.assertRaises(ValueError):verify(self.rows('-O2')[:1])
    def test_no_unreviewed_fast_math_or_host_instructions(self):
        for flag in ('-ffast-math','-Ofast','-march=native'):
            with self.subTest(flag=flag),self.assertRaises(ValueError):verify(self.rows('-O2 '+flag))
    def test_argument_arrays_from_cmake_are_audited(self):
        rows=[dict(file=x,arguments=['clang','-O2','-g','-c',x]) for x in TARGETS]
        self.assertEqual(verify(rows)['commands'],3)
