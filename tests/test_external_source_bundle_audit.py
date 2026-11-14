"""Source bundle must be checked against real Git blobs, not metadata alone."""
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))
from audit_external_source_bundle import audit, scan_zip, compare, safe_name, main


class ExternalBundleAuditTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.repo = self.root / 'project'
        self.repo.mkdir()
        subprocess.run(['git', '-C', str(self.repo), 'init', '-q'], check=True)
        for name, data in {'README.md': b'actual commit\n', 'app/build.gradle': b'versionName 0.8.2\n'}.items():
            path = self.repo / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(data)
        subprocess.run(['git', '-C', str(self.repo), 'add', '.'], check=True)
        subprocess.run(['git', '-C', str(self.repo), '-c', 'user.name=test',
                        '-c', 'user.email=test@example.invalid', 'commit', '-qm', 'fixture'], check=True)
        self.commit = subprocess.check_output(['git', '-C', str(self.repo), 'rev-parse', 'HEAD'], text=True).strip()

    def source(self, edits=None):
        edits = edits or {}
        entries = {'README.md': b'actual commit\n', 'app/build.gradle': b'versionName 0.8.2\n'}
        entries.update(edits)
        manifest = {'commit': self.commit, 'versionName': '0.8.2', 'versionCode': 54,
                    'files': {name: {'bytes': len(data), 'sha256': hashlib.sha256(data).hexdigest()}
                              for name, data in entries.items()}}
        path = self.root / 'source.zip'
        with zipfile.ZipFile(path, 'w') as z:
            for name, data in entries.items():
                z.writestr(name, data)
            z.writestr('REPOSITORY_MANIFEST_0_8_2.json', json.dumps(manifest))
        return path

    def test_exact_git_tree_passes(self):
        result = audit(self.source(), repository=self.repo)
        self.assertTrue(result['gitTreeMatches'])
        self.assertEqual(2, result['manifestFilesVerified'])

    def test_self_consistent_zip_that_lies_about_git_is_rejected(self):
        result = audit(self.source({'README.md': b'archive content differs\n'}), repository=self.repo)
        self.assertFalse(result['gitTreeMatches'])
        self.assertEqual(['README.md'], result['gitDiff']['different'])

    def test_missing_git_file_rejected(self):
        result = audit(self.source({'new-source.java': b'public class X {}'}), repository=self.repo)
        self.assertFalse(result['gitTreeMatches'])
        self.assertEqual(['new-source.java'], result['gitDiff']['onlyInArchive'])

    def test_detects_changed_working_tree_using_commit_blobs(self):
        path = self.repo / 'README.md'
        path.write_text('dirty local tree')
        result = audit(self.source(), repository=self.repo)
        self.assertTrue(result['gitTreeMatches'])

    def test_corrupt_manifest_rejected(self):
        archive = self.source()
        manifest = self.root / 'bad.json'
        manifest.write_text(json.dumps({'files': {'README.md': {'bytes': 3, 'sha256': '0'*64}}}))
        with self.assertRaises(ValueError):
            audit(archive, manifest=manifest)

    def test_archive_path_traversal_rejected(self):
        with zipfile.ZipFile(self.root / 'bad.zip', 'w') as z:
            z.writestr('../evil', 'payload')
        with self.assertRaises(ValueError):
            scan_zip(self.root / 'bad.zip')

    def test_duplicate_path_rejected(self):
        with zipfile.ZipFile(self.root / 'bad.zip', 'w') as z:
            z.writestr('dup', 'first')
            z.writestr('dup', 'other')
        with self.assertRaises(ValueError):
            scan_zip(self.root / 'bad.zip')

    def test_changed_archives_comparison(self):
        result = compare({'a': b'1', 'b': b'2'}, {'a': b'3', 'c': b'4'})
        self.assertEqual(['a'], result['different'])
        self.assertEqual(['b'], result['onlyInArchive'])
        self.assertEqual(['c'], result['onlyInReference'])

    def test_command_exit_nonzero_on_wrong_git(self):
        archive = self.source({'README.md': b'wrong'})
        self.assertEqual(2, main(['--source', str(archive), '--repository', str(self.repo)]))

    def test_unsafe_path_rejected(self):
        for name in ('../x', '/absolute', 'C:\\escape', 'bad\\path', './same', 'a/../b'):
            self.assertFalse(safe_name(name), name)


if __name__ == '__main__':
    unittest.main()
