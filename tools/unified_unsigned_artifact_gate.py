#!/usr/bin/env python3
"""Inspect the fixed, data-only build artifact on the fresh signing runner.

No files from the reviewed application source are executed on this runner.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import zipfile

from unified_signed_release_gate import verify_metadata, verify_payload

APK_NAME = 'LocalPilot-UNIFIED-unsigned.apk'
MANIFEST_NAME = 'UNSIGNED-MANIFEST.json'


def verify(directory, source_sha, aapt):
    if not re.fullmatch(r'[0-9a-fA-F]{40}', source_sha):
        raise ValueError('Full reviewed source SHA required')
    directory = Path(directory)
    entries = list(directory.iterdir())
    if {p.name for p in entries} != {APK_NAME, MANIFEST_NAME}:
        raise ValueError('Expected only the fixed APK and manifest in artifact')
    if any(p.is_symlink() or not p.is_file() for p in entries):
        raise ValueError('Artifact entries must be regular files')
    apk, manifest = directory / APK_NAME, directory / MANIFEST_NAME
    if manifest.stat().st_size > 4096:
        raise ValueError('Artifact manifest is oversized')
    data = json.loads(manifest.read_text(encoding='utf-8'))
    if (not isinstance(data, dict)
            or set(data) != {'schema', 'sourceSha', 'apkSha256', 'apkBytes'}
            or data.get('schema') != 'localpilot-unsigned-build-v1'
            or data.get('sourceSha') != source_sha.lower()):
        raise ValueError('Artifact source manifest differs from reviewed source')
    if apk.stat().st_size > 45 * 1024 * 1024:
        raise ValueError('Unsigned APK exceeds candidate limit')
    digest = hashlib.sha256(apk.read_bytes()).hexdigest()
    if (not isinstance(data.get('apkBytes'), int)
            or isinstance(data.get('apkBytes'), bool)
            or data['apkBytes'] != apk.stat().st_size
            or data.get('apkSha256') != digest):
        raise ValueError('Unsigned APK digest or size mismatch')
    verify_payload(apk, source_sha)
    verify_metadata(apk, aapt)
    return data


if __name__ == '__main__':
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--artifact-dir', type=Path, required=True)
    p.add_argument('--source-sha', required=True)
    p.add_argument('--aapt', required=True)
    a = p.parse_args()
    try:
        result = verify(a.artifact_dir, a.source_sha, a.aapt)
        print('UNSIGNED_ARTIFACT_PASS sha256=' + result['apkSha256'])
    except (OSError, ValueError, zipfile.BadZipFile) as error:
        raise SystemExit('UNSIGNED_ARTIFACT_STOP: ' + str(error))
