"""Guardrails for the trusted Control Tower GitHub status dispatcher."""
import importlib.util
from pathlib import Path
import unittest

PATH = Path(__file__).resolve().parents[1] / "tools/control_tower_dispatcher.py"
spec = importlib.util.spec_from_file_location("control_tower_dispatcher", PATH)
tower = importlib.util.module_from_spec(spec)
spec.loader.exec_module(tower)
SHA = "a" * 40
RUNS = [
    {"id": 110, "name": tower.TRACKED[0], "status": "completed", "conclusion": "success", "head_sha": "b" * 40},
    {"id": 109, "name": tower.TRACKED[1], "status": "completed", "conclusion": "failure", "head_sha": "c" * 40},
    {"id": 111, "name": "untrusted workflow", "status": "completed", "conclusion": "success", "head_sha": "d" * 40},
]


class FakeAPI:
    def __init__(self, previous=None):
        self.previous = previous
        self.calls = []

    def call(self, path, method="GET", data=None):
        self.calls.append((path, method, data))
        if path == "/actions/runs?per_page=100":
            return {"workflow_runs": [dict(x) for x in RUNS]}
        if path == "/branches/main":
            return {"commit": {"sha": SHA}}
        if path == "/actions/runs/110/jobs?per_page=100":
            return {"jobs": [{"id": 8, "name": "NES Android", "conclusion": "success"}]}
        if path == "/actions/runs/109/jobs?per_page=100":
            return {"jobs": [{"id": 9, "name": "Model test", "conclusion": "failure"}]}
        if path.startswith("/issues/159/comments?"):
            return [self.previous] if self.previous else []
        if path == "/issues/159/comments" and method == "POST":
            self.previous = {"id": 44, "user": {"login": "github-actions[bot]"}, "body": data["body"]}
            return {"id": 44}
        if path == "/issues/comments/44" and method == "PATCH":
            self.previous["body"] = data["body"]
            return {"id": 44}
        raise AssertionError(f"Unexpected GitHub API request: {method} {path}")


class TowerDispatcherTests(unittest.TestCase):
    def test_render_no_device_release_claim(self):
        text = tower.render(SHA, tower.latest_runs(RUNS), 110, [])
        self.assertIn("UNKNOWN", text)
        self.assertIn("physical POCO", text)
        self.assertIn("run_id=110", text)
        self.assertIn("FAILURE", text)
        self.assertNotIn("DEVICE_PASS", text)

    def test_dry_run_does_not_post(self):
        api = FakeAPI()
        state = tower.dispatch(api)
        self.assertEqual("DRY_RUN", state["result"])
        self.assertFalse(any(method in ("POST", "PATCH") for _, method, _ in api.calls))

    def test_one_bot_comment_and_idempotent_update(self):
        api = FakeAPI()
        self.assertEqual("CREATED", tower.dispatch(api, 110, True)["result"])
        self.assertEqual("UNCHANGED", tower.dispatch(api, 110, True)["result"])
        self.assertEqual(1, sum(method == "POST" for _, method, _ in api.calls))
        self.assertEqual(0, sum(method == "PATCH" for _, method, _ in api.calls))

    def test_stale_snapshot_not_overwritten(self):
        api = FakeAPI({"id": 44, "user": {"login": "github-actions[bot]"},
                       "body": tower.MARKER + " run_id=999 -->\nnewer"})
        self.assertEqual("STALE_SKIPPED", tower.dispatch(api, 110, True)["result"])
        self.assertFalse(any(method == "PATCH" for _, method, _ in api.calls))

    def test_other_workflow_does_not_update(self):
        api = FakeAPI()
        self.assertEqual("SKIPPED_UNTRACKED_OR_INCOMPLETE",
                         tower.dispatch(api, 111, True)["result"])
        self.assertFalse(any(method == "POST" for _, method, _ in api.calls))

    def test_only_bot_marker_can_be_edited(self):
        api = FakeAPI({"id": 44, "user": {"login": "human"},
                       "body": tower.MARKER + " run_id=109 -->"})
        self.assertEqual("CREATED", tower.dispatch(api, 110, True)["result"])
        self.assertFalse(any(method == "PATCH" for _, method, _ in api.calls))

    def test_reject_invalid_sha(self):
        with self.assertRaises(ValueError):
            tower.render("not-a-sha", tower.latest_runs(RUNS), 110, [])


if __name__ == "__main__":
    unittest.main()
