"""Bounded read-only ADB/ROM save evidence resilience; never accept partial data."""
import importlib.util
from pathlib import Path
import sys
import subprocess
from unittest import mock
import unittest

TOOLS = Path(__file__).resolve().parents[1] / "tools"
sys.path.insert(0, str(TOOLS))
spec = importlib.util.spec_from_file_location(
    "device_acceptance", TOOLS / "harness_student_device_acceptance.py")
mod = importlib.util.module_from_spec(spec)
spec.loader.exec_module(mod)


def response(code=0, stdout=b"valid", stderr=b""):
    return subprocess.CompletedProcess([], code, stdout=stdout, stderr=stderr)


class ADBReadTests(unittest.TestCase):
    def test_full_verified_bytes_return(self):
        with mock.patch.object(mod.subprocess, "run", return_value=response(stdout=b"NSV1ok")):
            self.assertEqual(b"NSV1ok", mod.private_file("files/a", 80))

    def test_partial_binary_and_failed_adb_are_not_accepted(self):
        replies = [response(1, b"NSV1PARTIAL", b"adb: device offline"),
                   response(0, b"NSV1COMPLETE")]
        with mock.patch.object(mod.subprocess, "run", side_effect=replies) as run, \
             mock.patch.object(mod.time, "sleep"):
            self.assertEqual(b"NSV1COMPLETE", mod.private_file("files/a", 80))
            self.assertEqual(2, run.call_count)

    def test_transport_stays_unavailable_then_pending_not_pass(self):
        with mock.patch.object(mod.subprocess, "run",
                               return_value=response(1, b"NSV1HALF", b"adb: device offline")) as run, \
             mock.patch.object(mod.time, "sleep"), \
             self.assertRaisesRegex(mod.EvidencePending, "ADB_READ_TRANSPORT_PENDING"):
            mod.private_file("files/a")
        self.assertEqual(3, run.call_count)

    def test_missing_current_file_is_pending_not_pass(self):
        with mock.patch.object(mod.subprocess, "run", return_value=response(
                1, b"", b"cat: files/a: No such file or directory")), \
             self.assertRaises(mod.EvidencePending):
            mod.private_file("files/a")

    def test_permission_error_fails_closed_without_raw_binary_dump(self):
        with mock.patch.object(mod.subprocess, "run", return_value=response(
                1, b"PRIVATE_ROM", b"run-as: Permission denied")), \
             self.assertRaisesRegex(RuntimeError, "adb_exit=1") as error:
            mod.private_file("files/a")
        self.assertNotIn("PRIVATE_ROM", str(error.exception))

    def test_oversize_rejected_even_when_adb_succeeds(self):
        with mock.patch.object(mod.subprocess, "run", return_value=response(
                0, b"NSV1" + b"x" * 100)), \
             self.assertRaisesRegex(RuntimeError, "size outside bound"):
            mod.private_file("files/a", limit=20)


if __name__ == "__main__":
    unittest.main()
