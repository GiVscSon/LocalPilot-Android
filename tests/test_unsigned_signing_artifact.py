"""The fresh signer accepts APK/JSON data only, before accessing the key."""
import hashlib
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'tools'))
import unified_signed_release_gate as signed
import unified_unsigned_artifact_gate as gate

SHA = 'a' * 40


def sdk(*args):
    if args[1:3] == ('dump', 'badging'):
        return ("package: name='org.localpilot.android' versionCode='53' versionName='0.8.1'\n"
                "sdkVersion:'30'\ntargetSdkVersion:'35'\n"
                "launchable-activity: name='org.localpilot.android.MainActivity'\n")
    if args[1:3] == ('dump', 'xmltree'):
        return '\n'.join(signed.ACTIVITIES)
    raise AssertionError('Unsigned preflight must never invoke signing: ' + str(args))


class UnsignedArtifact(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.apk = self.root / gate.APK_NAME
        self.make_apk()
        self.make_manifest()
        self.patch = patch.object(signed, 'sdk', side_effect=sdk)
        self.mock = self.patch.start()
        self.addCleanup(self.patch.stop)

    def make_apk(self, sha=SHA):
        with zipfile.ZipFile(self.apk, 'w') as z:
            for name in signed.ASSETS - {'classes.dex', 'assets/localpilot-source.json'}:
                z.writestr(name, b'fixture')
            z.writestr('assets/localpilot-source.json', json.dumps({
                'schema': 'localpilot-source-v1', 'sourceSha': sha}))
            z.writestr('classes.dex', b'org/localpilot/android/ModelLabActivity\0'
                       b'org/localpilot/gamelab/NesEmulatorActivity')

    def make_manifest(self, **changes):
        data = {'schema': 'localpilot-unsigned-build-v1', 'sourceSha': SHA,
                'apkSha256': hashlib.sha256(self.apk.read_bytes()).hexdigest(),
                'apkBytes': self.apk.stat().st_size}
        data.update(changes)
        (self.root / gate.MANIFEST_NAME).write_text(json.dumps(data))

    def test_reviewed_data_only_artifact_passes(self):
        result = gate.verify(self.root, SHA, 'aapt')
        self.assertEqual(SHA, result['sourceSha'])

    def test_changed_apk_is_rejected_before_sdk(self):
        self.apk.write_bytes(self.apk.read_bytes() + b'changed')
        with self.assertRaisesRegex(ValueError, 'digest or size mismatch'):
            gate.verify(self.root, SHA, 'aapt')
        self.mock.assert_not_called()

    def test_wrong_requested_source_is_rejected(self):
        with self.assertRaisesRegex(ValueError, 'source manifest'):
            gate.verify(self.root, 'b' * 40, 'aapt')

    def test_wrong_embedded_source_is_rejected_even_with_matching_hash(self):
        self.make_apk('b' * 40)
        self.make_manifest()
        with self.assertRaisesRegex(ValueError, 'embedded source SHA'):
            gate.verify(self.root, SHA, 'aapt')

    def test_extra_source_script_is_rejected(self):
        (self.root / 'verify.py').write_text('raise SystemExit(0)')
        with self.assertRaisesRegex(ValueError, 'only the fixed APK and manifest'):
            gate.verify(self.root, SHA, 'aapt')

    def test_symlink_is_rejected(self):
        raw = self.apk.read_bytes()
        self.apk.unlink()
        outside = self.root.parent / (self.root.name + '-other.apk')
        outside.write_bytes(raw)
        self.addCleanup(outside.unlink)
        self.apk.symlink_to(outside)
        with self.assertRaisesRegex(ValueError, 'regular files'):
            gate.verify(self.root, SHA, 'aapt')

    def test_wrong_package_is_rejected(self):
        self.mock.side_effect = lambda *a: sdk(*a).replace('org.localpilot.android', 'example.foreign')
        with self.assertRaisesRegex(ValueError, 'Wrong release package'):
            gate.verify(self.root, SHA, 'aapt')

    def test_duplicate_apk_entries_are_rejected(self):
        import warnings
        with warnings.catch_warnings():
            warnings.simplefilter('ignore', UserWarning)
            with zipfile.ZipFile(self.apk, 'a') as z:
                z.writestr('classes.dex', b'other')
        self.make_manifest()
        with self.assertRaisesRegex(ValueError, 'Duplicate APK ZIP entries'):
            gate.verify(self.root, SHA, 'aapt')


if __name__ == '__main__':
    unittest.main()
