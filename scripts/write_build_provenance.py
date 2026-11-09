#!/usr/bin/env python3
"""Embed the actual checked-out revision and reviewed source fingerprint in APK."""
import argparse
import json
import os
from pathlib import Path
from unified_pipeline import source_revision


def write(destination: Path) -> dict:
    sha, fingerprint = source_revision()
    metadata = {'schema': 'localpilot-build-provenance-v1',
                'git_sha': sha, 'source_sha256': fingerprint}
    destination.parent.mkdir(parents=True, exist_ok=True)
    temporary = destination.with_suffix('.pending')
    try:
        temporary.write_text(json.dumps(metadata, sort_keys=True) + '\n', encoding='utf-8')
        os.replace(temporary, destination)
    finally:
        temporary.unlink(missing_ok=True)
    return metadata


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args()
    print(json.dumps(write(args.out), sort_keys=True))
