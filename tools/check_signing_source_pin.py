#!/usr/bin/env python3
"""Exercise the real Gradle provenance task without signing or building an APK.

Requires the project's JDK/SDK and cached Gradle dependencies. No secrets are read.
"""
from pathlib import Path
import json
import os
import subprocess


def main():
    root = Path(__file__).resolve().parents[1]
    logs = root / 'build/reports/signing-source-pin'
    logs.mkdir(parents=True, exist_ok=True)
    revision = subprocess.check_output(
        ['git', 'rev-parse', 'HEAD'], cwd=root, text=True).strip()
    different = '0' * 40 if revision != '0' * 40 else '1' * 40
    cases = [
        ('dispatch_source_differs_from_event', different, revision, True, None),
        ('normal_ci_fallback', revision, None, True, None),
        ('wrong_explicit_source', revision, different, False, 'CI source SHA differs from checkout'),
        ('malformed_explicit_source', revision, 'bad', False, 'Invalid expected build source SHA'),
        ('empty_explicit_source', revision, '', False, 'Invalid expected build source SHA'),
        ('wrong_ci_fallback', different, None, False, 'CI source SHA differs from checkout'),
    ]
    for name, event, source, succeeds, error in cases:
        env = os.environ.copy()
        env['GITHUB_SHA'] = event
        env.pop('LOCALPILOT_BUILD_SOURCE_SHA', None)
        if source is not None:
            env['LOCALPILOT_BUILD_SOURCE_SHA'] = source
        result = subprocess.run(
            ['bash', './gradlew', '--offline', '--no-daemon', ':app:writeSigningProvenance'],
            cwd=root, env=env, text=True, stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT, timeout=180)
        (logs / (name + '.log')).write_text(result.stdout, encoding='utf-8')
        if (result.returncode == 0) != succeeds:
            raise RuntimeError(name + ': unexpected Gradle exit; see ' + str(logs))
        if succeeds:
            data = json.loads((root / 'app/build/generated/signing-provenance/localpilot-source.json').read_text())
            if data != {'schema': 'localpilot-source-v1', 'sourceSha': revision}:
                raise RuntimeError(name + ': APK provenance differs from actual checkout')
        elif error not in result.stdout:
            raise RuntimeError(name + ': Gradle failed for an unexpected reason')
        print(name + ': PASS', flush=True)
    print('SOURCE_PIN_GRADLE_CONTRACT_PASS cases=' + str(len(cases)))


if __name__ == '__main__':
    main()
