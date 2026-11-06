import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'tools'))
import run_device_acceptance as runner


class FailureEvidenceTest(unittest.TestCase):
    def test_one_failed_collector_does_not_hide_other_evidence(self):
        def command(args, timeout):
            if 'crash' in args:
                raise subprocess.TimeoutExpired(args, timeout)
            return 'live emulator evidence'

        screenshot = subprocess.CompletedProcess([], 0, b'\x89PNG\r\n\x1a\nimage', b'')
        with tempfile.TemporaryDirectory() as folder, \
             mock.patch.object(runner, 'OUT', Path(folder)), \
             mock.patch.object(runner, 'command', side_effect=command), \
             mock.patch.object(runner.subprocess, 'run', return_value=screenshot):
            runner.collect_failure_evidence()
            self.assertFalse((Path(folder) / 'crash-logcat.txt').exists())
            self.assertEqual((Path(folder) / 'logcat.txt').read_text(), 'live emulator evidence')
            self.assertEqual((Path(folder) / 'accessibility.txt').read_text(), 'live emulator evidence')
            self.assertEqual((Path(folder) / 'failure-screen.png').read_bytes(), screenshot.stdout)

    def test_invalid_screenshot_is_not_saved(self):
        screenshot = subprocess.CompletedProcess([], 0, b'not a png', b'')
        with tempfile.TemporaryDirectory() as folder, \
             mock.patch.object(runner, 'OUT', Path(folder)), \
             mock.patch.object(runner, 'command', return_value=''), \
             mock.patch.object(runner.subprocess, 'run', return_value=screenshot):
            runner.collect_failure_evidence()
            self.assertFalse((Path(folder) / 'failure-screen.png').exists())


if __name__ == '__main__':
    unittest.main()
