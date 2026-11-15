#!/usr/bin/env python3
"""Read-only snapshot of canonical LocalPilot GitHub state (C0).

Runs only on trusted default-branch CI. Does not modify PRs, policies or Android.
"""
import argparse
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import re

from control_tower_dispatcher import API, REPO, TRACKED, latest_runs

SHA = re.compile("[0-9a-f]{40}$")
BLOCKER_ISSUES = (69, 99, 159)


def assert_sha(value):
    if not isinstance(value, str) or SHA.fullmatch(value) is None:
        raise ValueError("Untrusted or missing full SHA")
    return value


def collect(api, now):
    branch = api.call("/branches/main")
    main_sha = assert_sha((branch.get("commit") or {}).get("sha"))
    pull_data = api.call("/pulls?state=open&per_page=100")
    if not isinstance(pull_data, list):
        raise ValueError("Invalid GitHub PR inventory")
    open_prs = []
    for pr in pull_data:
        number = pr.get("number")
        if not isinstance(number, int):
            raise ValueError("PR number missing")
        base = assert_sha((pr.get("base") or {}).get("sha"))
        head = assert_sha((pr.get("head") or {}).get("sha"))
        open_prs.append({
            "number": number,
            "title": str(pr.get("title", ""))[:180],
            "draft": pr.get("draft") is True,
            "baseSha": base,
            "headSha": head,
            "state": "DRAFT" if pr.get("draft") else "OPEN_UNREVIEWED",
            "gate": "UNKNOWN",
            "url": f"https://github.com/{REPO}/pull/{number}",
        })
    if len(open_prs) >= 100:
        raise ValueError("PR inventory exceeded first-page bound: no partial PASS")
    blockers = []
    for number in BLOCKER_ISSUES:
        issue = api.call(f"/issues/{number}")
        state = issue.get("state")
        if state not in ("open", "closed"):
            raise ValueError(f"Issue #{number} has no verified state")
        blockers.append({"number": number, "state": state.upper(),
                         "title": str(issue.get("title", ""))[:160],
                         "url": f"https://github.com/{REPO}/issues/{number}"})
    runs = api.call("/actions/runs?per_page=100").get("workflow_runs")
    if not isinstance(runs, list):
        raise ValueError("Invalid workflow run inventory")
    known = latest_runs(runs)
    workflows = []
    for name in TRACKED:
        run = known.get(name)
        workflows.append({
            "name": name,
            "result": "UNKNOWN" if not run else str(run.get("conclusion") or "UNKNOWN").upper(),
            "headSha": None if not run else assert_sha(run.get("head_sha")),
            "runId": None if not run else run["id"],
            "url": None if not run else f"https://github.com/{REPO}/actions/runs/{run['id']}",
        })
    return {
        "schema": "localpilot-control-tower-snapshot-v1",
        "generatedAt": now,
        "mainSha": main_sha,
        "openPrs": sorted(open_prs, key=lambda p: p["number"]),
        "blockingIssues": blockers,
        "workflows": workflows,
        "unknowns": [
            "physicalPocoFullLoop", "vulkanBenchmark", "productionSignerUpgrade",
            "modelGgufMmprojRealDevice", "candidateHoldoutImprovement", "stableRelease",
        ],
        "evidenceScope": "PUBLIC_GITHUB_API_ONLY",
        "releaseReady": False,
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    if os.environ.get("GITHUB_REPOSITORY") != REPO:
        raise SystemExit("Unexpected repo")
    snapshot = collect(API(os.environ.get("GITHUB_TOKEN", "")),
                       datetime.now(timezone.utc).isoformat())
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(snapshot, ensure_ascii=False, indent=2) + "\n")
    print("CONTROL_TOWER_SOURCE_SNAPSHOT:", snapshot["mainSha"],
          "openPRs=", len(snapshot["openPrs"]), "releaseReady=false")


if __name__ == "__main__":
    main()
