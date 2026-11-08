#!/usr/bin/env python3
"""Compatibility entrypoint: validate checked-in integration without rewriting it."""
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]


def main() -> int:
    subprocess.run([sys.executable, 'training/sync_rl_lab_to_android.py'], cwd=ROOT, check=True)
    for name in ('WeightSpaceSkills', 'TenGames', 'RetroConsole', 'AdaptiveEngine', 'Unified081Integration'):
        subprocess.run(['java', f'tests/{name}Verify.java'], cwd=ROOT, check=True)
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
