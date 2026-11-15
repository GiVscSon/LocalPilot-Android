#!/usr/bin/env python3
"""LocalPilot integration evidence runner. Does NOT claim device or policy improvement."""
import argparse
import datetime as dt
import json
import os
import re
import pathlib
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
SUITES = [
    ("source_wiring", [sys.executable, "-m", "unittest", "discover", "-s", "tests", "-p", "test_harness_live_cycle.py", "-v"]),
    ("harness_stop_owner", ["java", "tests/HarnessCoordinatorVerify.java"]),
    ("verified_replay", ["java", "tests/VerifiedPpoReplayStoreVerify.java"]),
    ("model_execution_audit", ["java", "tests/HarnessModelExecutionLogVerify.java"]),
]

def run(output: pathlib.Path) -> int:
    checks = []
    for name, command in SUITES:
        try:
            completed = subprocess.run(command, cwd=ROOT, text=True, stdout=subprocess.PIPE,
                                       stderr=subprocess.STDOUT, timeout=180, check=False)
            ok = completed.returncode == 0
            detail = completed.stdout[-12000:]
            code = completed.returncode
        except (OSError, subprocess.TimeoutExpired) as error:
            ok, detail, code = False, str(error), None
        checks.append({"name": name, "command": command, "passed": ok,
                       "exitCode": code, "logTail": detail})
    passed = all(check["passed"] for check in checks)
    report = {
        "schema": "localpilot-unified-organism-evidence-v1",
        "generatedAt": dt.datetime.now(dt.timezone.utc).isoformat(),
        "sourceSha": (os.environ.get("GITHUB_SHA") if re.fullmatch(r"[0-9a-f]{40}", os.environ.get("GITHUB_SHA", "")) else None),  # GitHub checkout SHA, never inferred
        "result": "SOURCE_AND_JVM_PASS" if passed else "FAIL",
        "scope": "source contract and local JVM tests only",
        "checks": checks,
        "evidence": {
            "sourceAndJvm": "PASS" if passed else "FAIL",
            "realNesEpisode": "UNKNOWN",
            "realGgufInference": "UNKNOWN",
            "verifiedTransitionOnDevice": "UNKNOWN",
            "candidateHoldoutImprovement": "UNKNOWN",
            "safePromotionAndRollback": "UNKNOWN",
            "physicalPoco": "UNKNOWN",
            "release": "NOT_RELEASED"
        }
    }
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Unified evidence: {report['result']} (source/JVM only); report={output}")
    return 0 if passed else 1

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=pathlib.Path, default=pathlib.Path("build/unified-organism-evidence.json"))
    args = parser.parse_args()
    raise SystemExit(run(args.output))
