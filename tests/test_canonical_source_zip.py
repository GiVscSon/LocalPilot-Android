"""Canonical source release never lies about content matching a Git SHA."""
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))
from build_canonical_source_zip import produce
from audit_external_source_bundle import audit

class CanonicalSourceTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.repo = self.root / 'repo'
        self.repo.mkdir()
        subprocess.run(['git','-C',str(self.repo),'init','-q'],check=True)
        p=self.repo/'app/build.gradle';p.parent.mkdir(parents=True)
        p.write_text("versionName '0.8.2'\nversionCode 54\n")
        (self.repo/'README.md').write_text('Original release source')
        subprocess.run(['git','-C',str(self.repo),'add','.'],check=True)
        subprocess.run(['git','-C',str(self.repo),'-c','user.name=Test',
                        '-c','user.email=test@example.invalid','commit','-qm','test'],check=True)

    def test_stable_and_verified(self):
        a=self.root/'a.zip';b=self.root/'b.zip'
        first=produce(self.repo,a)
        second=produce(self.repo,b)
        self.assertEqual(first['sha256'],second['sha256'])
        self.assertEqual(2,first['files'])
        self.assertTrue(audit(a,repository=self.repo)['gitTreeMatches'])

    def test_dirty_working_tree_does_not_modify_snapshot(self):
        (self.repo/'README.md').write_text('Uncommitted malicious alternate')
        a=self.root/'a.zip'
        result=produce(self.repo,a)
        self.assertTrue(audit(a,repository=self.repo)['gitTreeMatches'])
        self.assertEqual('0.8.2',result['version'])

    def test_existing_artifact_not_overwritten(self):
        a=self.root/'a.zip';a.write_bytes(b'keep')
        with self.assertRaises(FileExistsError): produce(self.repo,a)
        self.assertEqual(b'keep',a.read_bytes())

    def test_tracked_secret_fails_closed(self):
        secret=self.repo/'debug-signing.jks';secret.write_text('sensitive')
        subprocess.run(['git','-C',str(self.repo),'add','.'],check=True)
        subprocess.run(['git','-C',str(self.repo),'-c','user.name=Test',
                        '-c','user.email=test@example.invalid','commit','-qm','secret'],check=True)
        with self.assertRaises(ValueError): produce(self.repo,self.root/'a.zip')

    def test_source_example_is_not_a_secret(self):
        (self.repo/'README.md').write_text('Example: -----BEGIN PRIVATE KEY----- (not a key)')
        subprocess.run(['git','-C',str(self.repo),'add','.'],check=True)
        subprocess.run(['git','-C',str(self.repo),'-c','user.name=Test',
                        '-c','user.email=test@example.invalid','commit','-qm','example'],check=True)
        output=self.root/'a.zip'
        produce(self.repo,output)
        self.assertTrue(audit(output,repository=self.repo)['gitTreeMatches'])

    def test_real_private_key_block_rejected(self):
        (self.repo/'README.md').write_text(
            '-----BEGIN PRIVATE KEY-----'+'\n'+'A'*120+'\n-----END PRIVATE KEY-----')
        subprocess.run(['git','-C',str(self.repo),'add','.'],check=True)
        subprocess.run(['git','-C',str(self.repo),'-c','user.name=Test',
                        '-c','user.email=test@example.invalid','commit','-qm','key'],check=True)
        with self.assertRaises(ValueError): produce(self.repo,self.root/'a.zip')

    def test_corrupt_gradle_version_rejected(self):
        (self.repo/'app/build.gradle').write_text("versionName '0.8.2'\nversionName 'other'\nversionCode 54")
        subprocess.run(['git','-C',str(self.repo),'add','.'],check=True)
        subprocess.run(['git','-C',str(self.repo),'-c','user.name=Test',
                        '-c','user.email=test@example.invalid','commit','-qm','bad'],check=True)
        with self.assertRaises(ValueError): produce(self.repo,self.root/'a.zip')

if __name__=='__main__': unittest.main()
