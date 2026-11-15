#!/usr/bin/env python3
"""Check unique owners of the existing LocalPilot runtime, without creating new ones.

Only tracked source files are inspected. Build caches and generated outputs are excluded.
This is a structural guard, NOT evidence of a working or reliable Android APK.
"""
import subprocess
from pathlib import PurePosixPath
import sys

CANONICAL = {
    "HarnessCoordinator.java": "shared/src/main/java/org/localpilot/shared/HarnessCoordinator.java",
    "LearningCoordinator.java": "shared/src/main/java/org/localpilot/shared/LearningCoordinator.java",
    "NesEmulatorActivity.java": "game-lab/src/main/java/org/localpilot/gamelab/NesEmulatorActivity.java",
    "EmbeddedGgufRuntime.java": "app/src/main/java/org/localpilot/android/EmbeddedGgufRuntime.java",
}

# Known standalone ModelLab and host-JVM JNI fixture variants are not shipped as
# duplicate engines in the unified APK. Reject any NEW fourth source copy.
ALLOWED_VARIANTS = {
    "EmbeddedGgufRuntime.java": {
        "app/src/main/java/org/localpilot/android/EmbeddedGgufRuntime.java",
        "model-lab/src/main/java/org/localpilot/android/EmbeddedGgufRuntime.java",
        "tests/native/org/localpilot/android/EmbeddedGgufRuntime.java",
    }
}
DASHBOARD = "app/src/main/java/org/localpilot/android/HarnessDashboardActivity.java"


def violations(tracked: list[str], dashboard: str) -> list[str]:
    failures = []
    paths = [PurePosixPath(item) for item in tracked if item]
    for name, expected in CANONICAL.items():
        matches = sorted(str(p) for p in paths if p.name == name)
        allowed = ALLOWED_VARIANTS.get(name, {expected})
        if set(matches) != allowed:
            failures.append(f"Runtime owner/known variants {name}: expected {sorted(allowed)}; found {matches}")
    if not any(str(p) == DASHBOARD for p in paths):
        failures.append("Harness dashboard source missing")
    if "new HarnessCoordinator(" in dashboard:
        failures.append("Harness dashboard allocates second HarnessCoordinator")
    if "dispatchGesture(" in dashboard:
        failures.append("Harness dashboard owns Android gesture injection")
    if "HarnessCoordinator.nes()" not in dashboard:
        failures.append("Harness dashboard is not connected to existing runtime")
    return failures


def main() -> int:
    tracked = subprocess.run(["git", "ls-files", "-z"], capture_output=True, check=True).stdout
    names = [name.decode("utf-8") for name in tracked.split(b"\x00") if name]
    with open(DASHBOARD, encoding="utf-8") as stream:
        dashboard = stream.read()
    errors = violations(names, dashboard)
    for error in errors:
        print("SINGLE_RUNTIME_FAIL: " + error, file=sys.stderr)
    if errors:
        return 1
    print("SINGLE_RUNTIME_STRUCTURE_PASS: canonical runtime owners and explicit standalone/test JNI variants verified")
    print("Reminder: source uniqueness does not prove on-device execution, learned improvement or release.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
