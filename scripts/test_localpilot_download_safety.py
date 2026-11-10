"""Release operator must not silently fetch an older candidate APK."""
import argparse
import tempfile
import unittest
from unittest import mock

import localpilot


def run(sha, status, conclusion, stamp):
    return {"headSha": sha, "status": status, "conclusion": conclusion,
            "createdAt": stamp, "databaseId": stamp}


class LatestCandidateTest(unittest.TestCase):
    def test_operator_targets_current_unified_pr(self):
        self.assertEqual(localpilot.BRANCH,
                         "fix/unified-engine-validation-20261108")
        self.assertEqual(localpilot.WORKFLOW, "unified-16-simulators.yml")

    def test_latest_failure_cannot_fall_back_to_old_success(self):
        runs = [run("new", "completed", "failure", 2),
                run("old", "completed", "success", 1)]
        self.assertIsNone(localpilot.latest_success(runs))

    def test_running_new_candidate_cannot_fall_back(self):
        runs = [run("new", "in_progress", None, 2),
                run("old", "completed", "success", 1)]
        self.assertIsNone(localpilot.latest_success(runs))

    def test_explicit_current_success_can_be_selected(self):
        runs = [run("new", "completed", "success", 2),
                run("old", "completed", "success", 1)]
        self.assertEqual(localpilot.latest_success(runs)["headSha"], "new")

    def test_no_runs_is_not_a_release(self):
        self.assertIsNone(localpilot.latest_success([]))

    def test_ci_download_failed_new_run_never_calls_gh_or_makes_directory(self):
        with tempfile.TemporaryDirectory() as tmp:
            dest = tmp + "/not-created"
            runs = [run("new", "completed", "failure", 2),
                    run("old", "completed", "success", 1)]
            with mock.patch.object(localpilot, "list_runs", return_value=runs), \
                 mock.patch.object(localpilot, "gh") as github:
                with self.assertRaisesRegex(RuntimeError, "older APK"):
                    localpilot.ci_download(argparse.Namespace(output=dest))
                github.assert_not_called()

    def test_field_audit_is_read_only_cli_route(self):
        args = localpilot.parser().parse_args(
            ["field-audit", "--zip", "field/localpilot-audit.zip"])
        self.assertIs(args.func, localpilot.cmd_field_audit)
        self.assertIsNone(args.output)


if __name__ == "__main__":
    unittest.main()
