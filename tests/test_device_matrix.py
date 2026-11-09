"""Acceptance metadata must distinguish an actual tablet profile from a phone."""
from pathlib import Path
import importlib.util
import os
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path('tools').resolve()))
spec = importlib.util.spec_from_file_location('matrix_acceptance', 'tools/run_device_acceptance.py')
runner = importlib.util.module_from_spec(spec)
spec.loader.exec_module(runner)


class DeviceMatrix(unittest.TestCase):
    def metadata(self, api, factor, profile, observed=None, phone_geometry=False):
        observed = observed or api
        def command(args, **kwargs):
            key = tuple(args)
            if key == ('adb', 'shell', 'getprop', 'ro.build.version.sdk'): return str(observed)
            if key == ('adb', 'shell', 'wm', 'size'): return 'Physical size: 1080x1920' if factor == 'phone' or phone_geometry else 'Physical size: 2560x1800'
            if key == ('adb', 'shell', 'wm', 'density'): return 'Physical density: 420' if factor == 'phone' or phone_geometry else 'Physical density: 320'
            if key == ('git', 'rev-parse', 'HEAD'): return 'a' * 40
            raise AssertionError(args)
        with tempfile.TemporaryDirectory() as folder, patch.dict(os.environ, {
            'GITHUB_SHA': 'a'*40, 'LOCALPILOT_EXPECTED_API':str(api),
            'LOCALPILOT_FORM_FACTOR':factor, 'LOCALPILOT_EMULATOR_PROFILE':profile,
        }), patch.object(runner, 'command', side_effect=command), patch.object(runner.Path, 'read_bytes', return_value=b'APK fixture'):
            return runner.device_metadata()

    def test_all_six_actual_geometries(self):
        for api in (30, 35, 36):
            for factor, profile in (('phone', 'pixel_2'), ('tablet', 'pixel_c')):
                with self.subTest(api=api, factor=factor):
                    report = self.metadata(api, factor, profile)
                    self.assertEqual(api, report['api'])
                    self.assertEqual(factor, report['form_factor'])
                    self.assertFalse(report['physical_device'])
                    self.assertEqual(factor == 'tablet', report['smallest_width_dp'] >= 600)

    def test_wrong_api_profile_or_phone_as_tablet_rejected(self):
        for args, kwargs in (((35,'phone','pixel_2'), {'observed':30}),
                             ((35,'tablet','pixel_2'), {}),
                             ((35,'tablet','pixel_c'), {'phone_geometry':True})):
            with self.subTest(args=args, kwargs=kwargs), self.assertRaises(RuntimeError):
                self.metadata(*args, **kwargs)
