"""Acceptance metadata must distinguish an actual tablet profile from a phone."""
from pathlib import Path
import importlib.util
import json
import os
import subprocess
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

    def test_accessibility_settings_preserve_write_and_failure_states(self):
        component = runner.PACKAGE + '/org.localpilot.android.PilotService'
        with tempfile.TemporaryDirectory() as folder, patch.object(runner, 'OUT', Path(folder)), \
                patch.object(runner, 'command', side_effect=[component + '\n', '1\n', 'null\n', '0\n']):
            runner.record_accessibility_setup('after-enable', True)
            runner.record_accessibility_setup('failure', True)
            enabled = json.loads((Path(folder) / 'accessibility-settings-after-enable.json').read_text())
            failed = json.loads((Path(folder) / 'accessibility-settings-failure.json').read_text())
            self.assertEqual(component, enabled['enabled_accessibility_services'])
            self.assertEqual('1', enabled['accessibility_enabled'])
            self.assertTrue(enabled['pilot_component_enabled'])
            self.assertEqual('0', failed['accessibility_enabled'])
            self.assertFalse(failed['pilot_component_enabled'])

    def test_diagnostics_are_bounded_and_read_failures_do_not_abort_collection(self):
        with tempfile.TemporaryDirectory() as folder, patch.object(runner, 'OUT', Path(folder)), \
                patch.object(runner, 'command', side_effect=[
                    'x' * 5000, subprocess.TimeoutExpired('settings', 2), 'service\n' * 5000]):
            runner.record_accessibility_setup('failure', True)
            runner.record_pilot_service_failure(True)
            report = json.loads((Path(folder) / 'accessibility-settings-failure.json').read_text())
            self.assertEqual(2048, len(report['enabled_accessibility_services']))
            self.assertTrue(report['service_list_truncated'])
            self.assertIn('accessibility_enabled_read_error', report)
            self.assertEqual(8000, len((Path(folder) / 'pilot-service-state.txt').read_text()))

    def test_physical_device_diagnostics_never_query_or_write(self):
        with tempfile.TemporaryDirectory() as folder, patch.object(runner, 'OUT', Path(folder)), \
                patch.object(runner, 'command') as command:
            runner.record_accessibility_setup('failure', False)
            runner.record_pilot_service_failure(False)
            command.assert_not_called()
            self.assertEqual([], list(Path(folder).iterdir()))
