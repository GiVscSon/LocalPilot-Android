"""C0 snapshot must preserve exact PR/runs/issue provenance, never assert device ready."""
import importlib.util
from pathlib import Path
import sys
import unittest

ROOT = Path(__file__).resolve().parents[1] / "tools"
sys.path.insert(0, str(ROOT))
spec = importlib.util.spec_from_file_location("tower_snapshot", ROOT / "control_tower_snapshot.py")
mod = importlib.util.module_from_spec(spec)
spec.loader.exec_module(mod)
SHA = "a" * 40


class FakeAPI:
    def __init__(self):
        self.invalid = False

    def call(self, path):
        if path == "/branches/main":
            return {"commit": {"sha": SHA}}
        if path == "/pulls?state=open&per_page=100":
            return [{"number": 201, "title": "One Coordinator", "draft": True,
                     "base": {"sha": SHA}, "head": {"sha": "b" * 40}}]
        if path.startswith("/issues/"):
            return {"state": "open", "title": "Tracked blocker"}
        if path == "/actions/runs?per_page=100":
            return {"workflow_runs": [{
                "name": mod.TRACKED[0], "status": "completed",
                "id": 124, "conclusion": "failure", "head_sha": "c" * 40,
            }]}
        raise AssertionError(path)


class SnapshotTests(unittest.TestCase):
    def test_real_github_only_and_no_release(self):
        s = mod.collect(FakeAPI(), "2026-11-15T00:00:00Z")
        self.assertEqual(mod.REPO and SHA, s["mainSha"])
        self.assertEqual("localpilot-control-tower-snapshot-v1", s["schema"])
        self.assertEqual(1, len(s["openPrs"]))
        self.assertEqual("DRAFT", s["openPrs"][0]["state"])
        self.assertEqual("UNKNOWN", s["openPrs"][0]["gate"])
        self.assertEqual(3, len(s["blockingIssues"]))
        self.assertEqual("FAILURE", s["workflows"][0]["result"])
        self.assertFalse(s["releaseReady"])
        self.assertIn("physicalPocoFullLoop", s["unknowns"])

    def test_invalid_pr_sha_fails(self):
        class Invalid(FakeAPI):
            def call(self, path):
                result = super().call(path)
                if path.startswith("/pulls"):
                    result[0]["head"]["sha"] = "not-a-sha"
                return result
        with self.assertRaises(ValueError):
            mod.collect(Invalid(), "2026-11-15T00:00:00Z")

    def test_invalid_issue_state_fails(self):
        class Invalid(FakeAPI):
            def call(self, path):
                result = super().call(path)
                if path == "/issues/99":
                    return {"state": "UNKNOWN"}
                return result
        with self.assertRaises(ValueError):
            mod.collect(Invalid(), "2026-11-15T00:00:00Z")


if __name__ == "__main__":
    unittest.main()
