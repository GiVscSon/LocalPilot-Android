"""Local Android CLI 12->22 activation/rollback contract tests. No network."""
import importlib.util
from pathlib import Path
import tempfile
from unittest import mock
import unittest

SOURCE = Path(__file__).resolve().parents[1] / "tools/promote_android_cli.py"
spec = importlib.util.spec_from_file_location("promote_cli", SOURCE)
cli = importlib.util.module_from_spec(spec)
spec.loader.exec_module(cli)


def make_cli(root: Path, name: str, revision: str, include_android: bool = True):
    base = root / "cmdline-tools" / name
    (base / "bin").mkdir(parents=True)
    (base / "source.properties").write_text("Pkg.Revision = " + revision + "\n")
    for exe in (("sdkmanager", "android") if include_android else ("sdkmanager",)):
        f = base / "bin" / exe
        f.write_text("#!/bin/sh\n")
        f.chmod(0o755)
    return base


class CliPromotionTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        make_cli(self.root, "latest", "12.0", include_android=False)

    def test_promote_new_cli_and_keep_old_backup(self):
        make_cli(self.root, "latest-2", "22.0")
        result = cli.promote(self.root)
        self.assertEqual("PROMOTED", result["result"])
        self.assertEqual("22.0", result["version"])
        self.assertTrue(cli.runnable_cli(self.root / "cmdline-tools/latest"))
        self.assertEqual((12, 0, 0, 0), cli.version(self.root / "cmdline-tools/previous-cli-12.0"))

    def test_reentry_does_not_duplicate_install_or_backup(self):
        make_cli(self.root, "latest-2", "22.0")
        cli.promote(self.root)
        self.assertEqual("ALREADY_CURRENT", cli.promote(self.root)["result"])
        self.assertEqual(1, len(list((self.root / "cmdline-tools").glob("previous-cli-*"))))

    def test_missing_new_cli_preserves_old(self):
        with self.assertRaisesRegex(ValueError, "No complete newer Android CLI"):
            cli.promote(self.root)
        self.assertEqual((12, 0, 0, 0), cli.version(self.root / "cmdline-tools/latest"))

    def test_old_candidate_is_rejected(self):
        make_cli(self.root, "latest-2", "20.0")
        with self.assertRaisesRegex(ValueError, "No complete newer"):
            cli.promote(self.root)

    def test_incomplete_candidate_is_rejected(self):
        make_cli(self.root, "latest-2", "22.0", include_android=False)
        with self.assertRaisesRegex(ValueError, "No complete newer"):
            cli.promote(self.root)

    def test_symlink_candidate_not_accepted(self):
        external = make_cli(self.root, "cli22", "22.0")
        base = self.root / "cmdline-tools"
        (base / "latest-2").symlink_to(external, target_is_directory=True)
        with self.assertRaisesRegex(ValueError, "No complete newer"):
            cli.promote(self.root)

    def test_failed_rename_rolls_back_old_cli(self):
        candidate = make_cli(self.root, "latest-2", "22.0")
        original = Path.rename
        def failing_move(self, target):
            if self == candidate and Path(target).name == "latest":
                raise OSError("simulated move failure")
            return original(self, target)
        with mock.patch.object(Path, "rename", failing_move):
            with self.assertRaisesRegex(OSError, "simulated move failure"):
                cli.promote(self.root)
        self.assertEqual((12, 0, 0, 0), cli.version(self.root / "cmdline-tools/latest"))
        self.assertTrue(candidate.is_dir())


if __name__ == "__main__":
    unittest.main()
