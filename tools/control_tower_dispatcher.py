#!/usr/bin/env python3
"""Update a single GitHub Issue #159 bot comment from trusted Actions workflow metadata.
Runs only on default-branch workflow code. Never executes pull-request artifacts.
"""
import argparse
import json
import os
import re
import urllib.request

REPO = "GiVscSon/LocalPilot-Android"
ISSUE = 159
MARKER = "<!-- LOCALPILOT-CONTROL-TOWER-v1"
TRACKED = (
    "LocalPilot All-in-One · GameLab + ModelLab (test-only)",
    "LocalPilot unified organism evidence",
    "Build Android debug APK",
    "LocalPilot One APK scripted games and training (candidate)",
    "LocalPilot · Verified Git Source Snapshot",
)
SHA = re.compile("[0-9a-f]{40}$")


def safe(value, limit=100):
    return re.sub(r"[\r\n|<>]", " ", str(value))[:limit]


class API:
    def __init__(self, token):
        if not token:
            raise ValueError("Missing Actions GITHUB_TOKEN")
        self.token = token

    def call(self, path, method="GET", data=None):
        if not path.startswith("/") or "://" in path or "\n" in path:
            raise ValueError("Unsafe API path")
        payload = None if data is None else json.dumps(data).encode("utf-8")
        request = urllib.request.Request(
            "https://api.github.com/repos/" + REPO + path, data=payload,
            headers={
                "Authorization": "Bearer " + self.token,
                "Accept": "application/vnd.github+json",
                "X-GitHub-Api-Version": "2022-11-28",
                "Content-Type": "application/json",
                "User-Agent": "localpilot-control-tower-v1",
            }, method=method)
        with urllib.request.urlopen(request, timeout=25) as response:
            raw = response.read(2_000_001)
        if len(raw) > 2_000_000:
            raise ValueError("API response too large")
        return json.loads(raw)


def latest_runs(runs):
    latest = {}
    for run in runs:
        name = run.get("name")
        if name not in TRACKED or run.get("status") != "completed" or not isinstance(run.get("id"), int):
            continue
        if name not in latest or run["id"] > latest[name]["id"]:
            latest[name] = run
    return latest


def render(main_sha, latest, watermark, jobs):
    if not isinstance(main_sha, str) or not SHA.fullmatch(main_sha):
        raise ValueError("Invalid main SHA")
    lines = [
        f"{MARKER} run_id={watermark} -->",
        "### LocalPilot Control Tower · GitHub CI",
        "",
        f"**main SHA:** {main_sha}",
        "**Scope:** recorded GitHub workflow outcomes only; an emulator is not a physical device.",
        "",
        "| Workflow | Result | Source SHA | Run |",
        "| --- | --- | --- | --- |",
    ]
    for name in TRACKED:
        run = latest.get(name)
        if not run:
            lines.append(f"| {safe(name)} | UNKNOWN | UNKNOWN | none |")
            continue
        sha = run.get("head_sha")
        digest = sha[:12] if isinstance(sha, str) and SHA.fullmatch(sha) else "UNKNOWN"
        conclusion = safe(run.get("conclusion") or "UNKNOWN", 24).upper()
        url = f"https://github.com/{REPO}/actions/runs/{run['id']}"
        lines.append(f"| {safe(name)} | {conclusion} | {digest} | [CI]({url}) |")
    lines.append(f"\n**Latest relevant run:** https://github.com/{REPO}/actions/runs/{watermark}")
    failures = [job for job in jobs if job.get("conclusion") in ("failure", "timed_out", "cancelled")]
    if failures:
        lines.append("**Failing jobs in inspected run:**")
        for job in failures[:8]:
            lines.append("- " + safe(job.get("name", "unnamed")) + ": " + safe(job.get("conclusion", "")))
    else:
        lines.append("**Inspected-run job failures:** none reported; other runs may still fail.")
    lines.append(
        "\n**Not certified:** physical POCO, Vulkan/GGUF on phone, better PPO policy, "
        "arbitrary user ROMs, signed upgrade or stable release.\n"
        "Canonical scope: docs/LOCALPILOT-SINGLE-SOURCE-OF-TRUTH.md and "
        "docs/LOCALPILOT-CONTROL-TOWER-STAGES.md.")
    return ("\n".join(lines) + "\n")[:13000]


def find_bot_comment(api):
    for page in range(1, 5):
        comments = api.call(f"/issues/{ISSUE}/comments?per_page=100&page={page}")
        if not isinstance(comments, list):
            raise ValueError("Invalid issue comments response")
        for comment in comments:
            login = (comment.get("user") or {}).get("login")
            if login in ("github-actions[bot]", "github-actions") and str(comment.get("body", "")).startswith(MARKER):
                return comment
        if len(comments) < 100:
            return None
    raise ValueError("Bounded issue comment scan exceeded: refuse duplicate")


def watermark(body):
    found = re.match(re.escape(MARKER) + r" run_id=(\d+) -->", body)
    return int(found.group(1)) if found else -1


def dispatch(api, trigger_id=None, apply=False):
    recent = api.call("/actions/runs?per_page=100")
    runs = recent.get("workflow_runs")
    if not isinstance(runs, list):
        raise ValueError("Invalid workflow listing")
    latest = latest_runs(runs)
    if not latest:
        raise ValueError("No completed tracked workflows")
    latest_id = max(run["id"] for run in latest.values())
    if trigger_id is not None:
        if trigger_id <= 0:
            raise ValueError("Invalid triggering run ID")
        selected = next((run for run in runs if run.get("id") == trigger_id), None)
        if selected is None:
            selected = api.call(f"/actions/runs/{trigger_id}")
        if selected.get("name") not in TRACKED or selected.get("status") != "completed":
            return {"result": "SKIPPED_UNTRACKED_OR_INCOMPLETE"}
    else:
        selected = max(latest.values(), key=lambda run: run["id"])
    branch = api.call("/branches/main")
    main_sha = (branch.get("commit") or {}).get("sha")
    current = max(latest.values(), key=lambda run: run["id"])
    jobs_doc = api.call(f"/actions/runs/{current['id']}/jobs?per_page=100")
    jobs = jobs_doc.get("jobs")
    if not isinstance(jobs, list):
        raise ValueError("Missing job evidence")
    rendered = render(main_sha, latest, latest_id, jobs)
    if not apply:
        return {"result": "DRY_RUN", "runId": latest_id, "markdown": rendered}
    old = find_bot_comment(api)
    if old:
        if watermark(str(old.get("body", ""))) > latest_id:
            return {"result": "STALE_SKIPPED", "runId": latest_id}
        if old.get("body") == rendered:
            return {"result": "UNCHANGED", "runId": latest_id}
        api.call(f"/issues/comments/{old['id']}", "PATCH", {"body": rendered})
        return {"result": "UPDATED", "runId": latest_id}
    api.call(f"/issues/{ISSUE}/comments", "POST", {"body": rendered})
    return {"result": "CREATED", "runId": latest_id}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--run-id", type=int)
    parser.add_argument("--apply", action="store_true")
    args = parser.parse_args()
    if os.environ.get("GITHUB_REPOSITORY") != REPO:
        raise SystemExit("Unexpected repository; refusing update")
    result = dispatch(API(os.environ.get("GITHUB_TOKEN")), args.run_id, args.apply)
    print(json.dumps({key: val for key, val in result.items() if key != "markdown"}, ensure_ascii=False))
    if "markdown" in result:
        print(result["markdown"])


if __name__ == "__main__":
    main()
