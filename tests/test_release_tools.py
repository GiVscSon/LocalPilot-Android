"""Release failure-path tests, requiring neither Android SDK nor network."""
import json
import re
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch
import zipfile

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools'))
import release_common as common
import package_release as pack


class ReleaseToolsTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name) / 'project'
        self.root.mkdir()
        (self.root / 'app').mkdir()
        (self.root / 'app/build.gradle').write_text((ROOT / 'app/build.gradle').read_text())
        subprocess.run(['git', 'init', '-q', self.root], check=True)
        subprocess.run(['git', '-C', self.root, '-c', 'user.name=Test', '-c', 'user.email=test@example.invalid',
                        'commit', '-q', '--allow-empty', '-m', 'fixture'], check=True)

    def tearDown(self):
        self.temp.cleanup()

    def apk(self):
        path = self.root / 'fake.apk'
        with zipfile.ZipFile(path, 'w') as z:
            z.writestr('AndroidManifest.xml', 'test')
            z.writestr('classes.dex', 'test')
        return path

    def outputs(self, config, certificate='a' * 64):
        return [f"package: name='{config['package']}' versionCode='{config['versionCode']}' versionName='{config['version']}'\nsdkVersion:'{config['minSdk']}'\ntargetSdkVersion:'{config['targetSdk']}'\n",
                f'Signer #1 certificate SHA-256 digest: {certificate}\n']

    def test_version_is_read_from_gradle(self):
        path = self.root / 'app/build.gradle'
        source = re.sub(r"versionName\s+'[^']+'", "versionName '8.9.10'", path.read_text())
        path.write_text(re.sub(r'versionCode\s+\d+', 'versionCode 987', source))
        config = common.configuration(self.root)
        self.assertEqual(('8.9.10-dev-install1031', 987), (config['version'], config['versionCode']))

    def test_standalone_is_distinct_from_existing_dev_installation(self):
        config = common.configuration(self.root)
        self.assertEqual('org.localpilot.android.dev.install1031', config['package'])
        self.assertNotEqual('org.localpilot.android.dev', config['package'])
        self.assertEqual('0.7.34-dev-install1031', config['version'])

    def test_missing_or_ambiguous_version_fails(self):
        path = self.root / 'app/build.gradle'
        path.write_text(path.read_text() + "\nversionCode 44\n")
        with self.assertRaises(ValueError): common.configuration(self.root)

    def test_valid_apk_metadata_and_certificate(self):
        config = common.configuration(self.root)
        with patch.object(common.subprocess, 'check_output', side_effect=self.outputs(config)):
            result = common.verify_apk(self.apk(), config, '/fake-sdk', 'a' * 64)
        self.assertEqual(config['version'], result['versionName'])

    def test_stale_apk_fails_before_signature(self):
        config = common.configuration(self.root)
        with patch.object(common.subprocess, 'check_output', return_value="package: name='old.app' versionCode='1' versionName='old'"):
            with self.assertRaises(ValueError): common.verify_apk(self.apk(), config, '/fake-sdk')

    def test_wrong_signer_fails(self):
        config = common.configuration(self.root)
        with patch.object(common.subprocess, 'check_output', side_effect=self.outputs(config)):
            with self.assertRaises(ValueError): common.verify_apk(self.apk(), config, '/fake-sdk', 'b' * 64)

    def test_invalid_signature_is_not_ignored(self):
        config = common.configuration(self.root)
        outputs = self.outputs(config)
        with patch.object(common.subprocess, 'check_output', side_effect=[outputs[0], subprocess.CalledProcessError(1, 'apksigner')]):
            with self.assertRaises(subprocess.CalledProcessError): common.verify_apk(self.apk(), config, '/fake-sdk')

    def test_private_files_excluded_and_local_source_included(self):
        for name in ['fresh.java', 'private.jks', 'private.mp4', '.env', 'localpilot-experience-v1.log', 'weights.npz']:
            (self.root / name).write_text('fixture')
        names = [str(relative) for _, relative in pack.source_files(self.root)]
        self.assertIn('fresh.java', names)
        self.assertFalse(set(names) & {'private.jks', 'private.mp4', '.env', 'localpilot-experience-v1.log', 'weights.npz'})

    def test_symlink_and_secret_fail_closed(self):
        (self.root / 'link.java').symlink_to(self.root / 'app/build.gradle')
        with self.assertRaises(ValueError): list(pack.source_files(self.root))
        (self.root / 'link.java').unlink()
        (self.root / 'secret.py').write_text('-----BEGIN ' + 'PRIVATE KEY-----')
        with self.assertRaises(ValueError): list(pack.source_files(self.root))

    def test_package_checksums_local_edits_and_no_overwrite(self):
        (self.root / 'new.java').write_text('unsaved source')
        output = self.root / 'custom-output'
        with patch.object(pack, 'verify_apk', return_value={'versionName': 'fixture'}):
            destination = pack.package(self.root, self.apk(), output)
            metadata = json.loads((destination / 'MANIFEST.json').read_text())
            self.assertTrue(metadata['workingTreeDirty'])
            for name, info in metadata['files'].items():
                self.assertEqual(info['sha256'], common.digest(destination / name))
            with zipfile.ZipFile(next(destination.glob('*_source.zip'))) as z:
                self.assertIn('LocalPilot-Android/new.java', z.namelist())
                self.assertFalse(any('custom-output/' in n for n in z.namelist()))
            with self.assertRaises(FileExistsError): pack.package(self.root, self.apk(), output)

    def test_source_error_does_not_publish_partial_package(self):
        (self.root / 'secret.py').write_text('-----BEGIN ' + 'PRIVATE KEY-----')
        output = self.root / 'release'
        with patch.object(pack, 'verify_apk', return_value={}):
            with self.assertRaises(ValueError): pack.package(self.root, self.apk(), output)
        self.assertEqual([], list(output.iterdir()))


if __name__ == '__main__': unittest.main()
