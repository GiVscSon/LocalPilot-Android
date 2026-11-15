#!/usr/bin/env python3
"""Summarize six *actual emulator* acceptance artifacts. Never certify physical device or release."""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import re
import sys

APIS = (30, 35, 36)
PROFILES = ('pixel_2', 'pixel_c')
REPORTS = (
    'one-package-navigation.json',
    'nes-student-ppo.json',
    'nes-student-candidate-independent-exam.json',
    'nes-autoplay-save-resume.json',
    'nes-experience-archive.json',
)
SHA = re.compile(r'^[0-9a-f]{40}$')
APK_SHA = re.compile(r'^[0-9a-f]{64}$')


def read_report(file: Path) -> dict:
    if not file.is_file() or file.is_symlink():
        raise ValueError('Missing or unsafe evidence: ' + file.name)
    if file.stat().st_size > 1_000_000:
        raise ValueError('Evidence too large: ' + file.name)
    data = json.loads(file.read_text(encoding='utf-8'))
    if not isinstance(data, dict):
        raise ValueError('Expected JSON object: ' + file.name)
    return data


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def inspect(root: Path, sha: str) -> dict:
    require(bool(SHA.fullmatch(sha)), 'Expected an exact 40-hex checkout SHA')
    entries = []
    apk_hashes = set()
    candidate_results = []
    for api in APIS:
        for profile in PROFILES:
            name = f'LocalPilot-One-APK-Emulator-UI-API{api}-{profile}-{sha}'
            directory = root / name
            entry = {'api': api, 'profile': profile, 'artifact': name, 'status': 'FAIL', 'errors': []}
            results = {}
            try:
                require(directory.is_dir() and not directory.is_symlink(), 'Missing emulator artifact directory')
                for report in REPORTS:
                    results[report] = read_report(directory / report)
                ui = results[REPORTS[0]]
                require(ui.get('schema') == 'localpilot-unified-device-ui-test-v1', 'Wrong navigation evidence schema')
                require(ui.get('emulatorOnly') is True and ui.get('singleProcess') is True,
                        'UI report does not prove one process on emulator')
                checks = ui.get('checks')
                require(isinstance(checks, list) and len(checks) >= 5 and
                        all(isinstance(c, dict) and c.get('status') == 'PASS' for c in checks),
                        'Missing successful native UI checks')
                require(ui.get('releaseReady') is False, 'Navigation report unexpectedly claims release ready')
                for report in REPORTS[1:]:
                    row = results[report]
                    require(row.get('sourceSha') == sha, f'{report}: source SHA mismatch')
                    require(row.get('api') == api, f'{report}: Android API mismatch')
                    require(row.get('releaseReady') is False, f'{report}: invalid release marker')
                    if 'physicalDevice' in row:
                        require(row['physicalDevice'] is False, f'{report}: claims physical device')
                student = results[REPORTS[1]]
                require(student.get('result') == 'PASS' and student.get('replaySamples') == 16,
                        'On-device Student PPO replay not verified')
                require(student.get('singlePackage') is True and
                        student.get('evaluation') == 'NOT_RUN',
                        'Student candidate lifecycle not independently verified')
                before = student.get('policyBeforeSha256')
                after = student.get('policyAfterSha256')
                require(isinstance(before, str) and bool(APK_SHA.fullmatch(before)) and
                        isinstance(after, str) and bool(APK_SHA.fullmatch(after)) and before != after,
                        'Missing verified candidate weight change')
                evaluation = results[REPORTS[2]]
                require(evaluation.get('result') in ('ACCEPTED', 'NO_PROVEN_IMPROVEMENT'),
                        'Independent holdout evaluation absent or invalid')
                require(evaluation.get('scenarioCount', 0) >= 20 and
                        evaluation.get('androidWeightsAndManifestUnchanged') is True and
                        evaluation.get('evaluationRuntime') == 'HOST_REAL_JSNES_NOT_ANDROID',
                        'Holdout provenance or separation invalid')
                require(evaluation.get('activePolicySha256') == before and
                        evaluation.get('candidatePolicySha256') == after,
                        'Holdout uses different weights than device training')
                autoplay = results[REPORTS[3]]
                require(autoplay.get('result') == 'PASS' and
                        autoplay.get('stopBlocksFurtherCompletedActions') is True and
                        autoplay.get('candidatePreserved') is True and
                        autoplay.get('activePolicyPreserved') is True,
                        'Autoplay STOP/save/resume verification missing')
                require(autoplay.get('resumed', {}).get('actions', -1) >
                        autoplay.get('saved', {}).get('actions', 1 << 30),
                        'Autoplay did not advance after restart')
                archive = results[REPORTS[4]]
                require(archive.get('result') == 'PASS' and
                        archive.get('originalBytesPreserved') is True and
                        archive.get('gameAndWeightsPreserved') is True and
                        archive.get('safExportReadbackVerified') is True,
                        'Archive readback/preservation not verified')
                for report in REPORTS[1:]:
                    digest = results[report].get('apkSha256')
                    if digest is not None:
                        require(isinstance(digest, str) and bool(APK_SHA.fullmatch(digest)),
                                f'{report}: invalid APK SHA')
                        apk_hashes.add(digest)
                candidate_results.append(evaluation['result'])
                entry['status'] = 'PASS'
                entry['evaluation'] = evaluation['result']
                entry['verifiedStudentSteps'] = student['replaySamples']
            except (OSError, ValueError, TypeError, AttributeError, KeyError) as error:
                entry['errors'].append(str(error)[:500])
            entries.append(entry)
    all_green = all(item['status'] == 'PASS' for item in entries) and len(apk_hashes) == 1
    if len(apk_hashes) != 1:
        entries.append({'status': 'FAIL', 'scope': 'cross-matrix',
                        'errors': [f'Expected exactly one identical APK SHA across matrix; observed {len(apk_hashes)}']})
    return {
        'schema': 'localpilot-android-matrix-evidence-v1',
        'sourceSha': sha,
        'result': 'EMULATOR_MATRIX_PASS' if all_green else 'FAIL',
        'matrix': entries,
        'apkSha256': next(iter(apk_hashes)) if len(apk_hashes) == 1 else None,
        'evidence': {
            'androidEmulatorMatrix': 'PASS' if all_green else 'FAIL',
            'nesStudentPpoInEmulator': 'PASS' if all_green else 'UNKNOWN',
            'longPlayStopSaveResume': 'PASS' if all_green else 'UNKNOWN',
            'holdoutCandidateImprovement': (
                'PROVEN_ON_HOST_HOLDOUT_ONLY' if all_green and all(
                    r == 'ACCEPTED' for r in candidate_results) else 'NOT_PROVEN'),
            'realGgufInferenceOnDevice': 'UNKNOWN',
            'physicalPoco': 'UNKNOWN',
            'vulkanOnPoco': 'UNKNOWN',
            'stableRelease': 'NOT_RELEASED',
        },
        'releaseReady': False,
    }


def markdown(report: dict) -> str:
    lines = [f"### LocalPilot Android emulator matrix: {report['result']}",
             f"Checkout SHA: {report['sourceSha']}; stable release: **NO**.",
             '', '| API | Device | Emulator result | Holdout |',
             '| --- | --- | --- | --- |']
    for entry in report['matrix']:
        if 'api' in entry:
            reason = entry.get('evaluation') or '; '.join(entry.get('errors', []))[:120]
            lines.append(f"| {entry['api']} | {entry['profile']} | {entry['status']} | {reason} |")
    lines += ['', '**Excluded from this proof:** physical POCO X7 Pro, Vulkan GPU, '
              'user ROMs, GGUF model inference, production signing. '
              'Host JSNES holdout is not Android device evaluation.']
    return '\n'.join(lines) + '\n'


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument('--artifacts-root', type=Path, required=True)
    parser.add_argument('--source-sha', required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--summary', type=Path)
    args = parser.parse_args()
    report = inspect(args.artifacts_root, args.source_sha)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    summary = markdown(report)
    print(summary)
    if args.summary:
        args.summary.parent.mkdir(parents=True, exist_ok=True)
        args.summary.write_text(summary, encoding='utf-8')
    return 0 if report['result'] == 'EMULATOR_MATRIX_PASS' else 1


if __name__ == '__main__':
    sys.exit(main())
