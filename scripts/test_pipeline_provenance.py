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


class BuildTestEvidence(unittest.TestCase):
    def test_failed_command_retains_output_and_cannot_pass(self):
        with tempfile.TemporaryDirectory() as folder:
            log = Path(folder) / 'failure.log'
            process = subprocess.CompletedProcess(['java', 'fixture.java'], 7,
                                                  stdout='compiler failure', stderr='details')
            with patch.object(pipeline.subprocess, 'run', return_value=process):
                receipt = pipeline.evidenced_command(['java', 'fixture.java'], log)
            self.assertEqual('FAIL', receipt['status'])
            self.assertEqual(7, receipt['exit_code'])
            self.assertEqual('compiler failuredetails', log.read_text())
            self.assertEqual(64, len(receipt['log_sha256']))

    def test_timeout_keeps_partial_output(self):
        with tempfile.TemporaryDirectory() as folder:
            log = Path(folder) / 'timeout.log'
            error = subprocess.TimeoutExpired(['java'], 1, output=b'partial output', stderr=b'failed')
            with patch.object(pipeline.subprocess, 'run', side_effect=error):
                receipt = pipeline.evidenced_command(['java'], log, timeout=1)
            self.assertEqual('FAIL', receipt['status'])
            self.assertIsNone(receipt['exit_code'])
            self.assertIn('partial output', log.read_text())

    def test_build_test_runs_real_suite_entries_and_binds_built_apk(self):
        import json
        import hashlib
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            apk = root / pipeline.BUILT_APK
            apk.parent.mkdir(parents=True)
            apk.write_bytes(b'fixture APK bytes; integrity checked in the separate APK gate')
            commands = []

            def completed(command, log, timeout):
                commands.append(command)
                log.parent.mkdir(parents=True, exist_ok=True)
                log.write_text('> Task :app:testDebugUnitTest NO-SOURCE\n')
                return {'command': command, 'status': 'PASS', 'exit_code': 0, 'log': str(log)}

            with patch.object(pipeline, 'ROOT', root), \
                 patch.object(pipeline, 'source_revision', return_value=('a' * 40, 'fingerprint')), \
                 patch.object(pipeline, 'jvm_regression_commands', return_value=[['java', 'tests/CoreVerify.java']]), \
                 patch.object(pipeline, 'evidenced_command', side_effect=completed):
                report = pipeline.build_test(root / 'reports')
            self.assertEqual('NO_SOURCE', report['gradle_unit_tests'])
            self.assertEqual(hashlib.sha256(apk.read_bytes()).hexdigest(), report['built_apk']['sha256'])
            self.assertIn(['java', 'tests/CoreVerify.java'], commands)
            self.assertTrue(any(':app:assembleDebug' in command and ':app:lintDebug' in command
                                for command in commands))
            self.assertTrue(any('tests/nes_observation_smoke.cjs' in command for command in commands))
            manifest = json.loads((root / 'reports/build-test.json').read_text())
            self.assertEqual('PASS', manifest['status'])
            self.assertEqual('NOT_RUN', manifest['device_acceptance'])
            self.assertFalse(manifest['real_game_verified'])

    def test_build_failure_is_persisted_and_stops_remaining_commands(self):
        import json
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            with patch.object(pipeline, 'ROOT', root), \
                 patch.object(pipeline, 'source_revision', return_value=('a' * 40, 'fingerprint')), \
                 patch.object(pipeline, 'jvm_regression_commands', return_value=[['java', 'tests/CoreVerify.java']]), \
                 patch.object(pipeline, 'evidenced_command', return_value={
                     'status': 'FAIL', 'exit_code': 9, 'log': 'failed.log'}) as command:
                with self.assertRaisesRegex(RuntimeError, 'Build/test failed'):
                    pipeline.build_test(root / 'reports')
            self.assertEqual(1, command.call_count)
            manifest = json.loads((root / 'reports/build-test.json').read_text())
            self.assertEqual('FAIL', manifest['status'])
            self.assertEqual(9, manifest['commands'][0]['exit_code'])
            self.assertNotIn('built_apk', manifest)

    def test_rebuilding_cannot_reuse_an_older_apk_receipt(self):
        import json
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            state = {'schema': 'localpilot-script-orchestrator-v1', 'github_sha': 'a' * 40,
                     'source_sha256': 'fingerprint', 'stages': {
                         name: {'status': 'PASS'} for name in ('verify', 'selfplay', 'apk')}}
            (root / 'unified-pipeline.json').write_text(json.dumps(state))
            with patch.object(pipeline, 'source_revision', return_value=('a' * 40, 'fingerprint')), \
                 patch.object(pipeline, 'build_test', return_value={
                     'built_apk': {'sha256': 'new-bytes'}}), \
                 patch('sys.argv', ['unified_pipeline.py', 'build-test', '--out', folder]):
                self.assertEqual(0, pipeline.main())
            final = json.loads((root / 'unified-pipeline.json').read_text())
            self.assertFalse(final['complete'])
            self.assertNotIn('apk', final['stages'])
            self.assertFalse(final['release_ready'])

    def test_old_three_stage_report_is_not_complete(self):
        import json
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            state = {'schema': 'localpilot-script-orchestrator-v1', 'github_sha': 'a' * 40,
                     'source_sha256': 'fingerprint', 'stages': {
                         name: {'status': 'PASS'} for name in ('verify', 'selfplay', 'apk')}}
            (root / 'unified-pipeline.json').write_text(json.dumps(state))
            with patch.object(pipeline, 'source_revision', return_value=('a' * 40, 'fingerprint')), \
                 patch.object(pipeline, 'verify_contracts', return_value={}), \
                 patch('sys.argv', ['unified_pipeline.py', 'verify', '--out', folder]):
                self.assertEqual(0, pipeline.main())
            final = json.loads((root / 'unified-pipeline.json').read_text())
            self.assertFalse(final['complete'])
