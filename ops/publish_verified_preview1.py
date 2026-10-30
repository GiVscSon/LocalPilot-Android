"""Publish the plan-bound Preview artifact; never build, re-sign or replace Dev APKs."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import urllib.parse
import urllib.request
import zipfile

REPO = 'GiVscSon/LocalPilot-Android'
TAG = 'v0.7.34-preview.1'
ORIGINALS = ('LocalPilot_Android_0.7.34_preview.apk',
             'LocalPilot_Android_0.7.34_preview_source.zip',
             'LocalPilot_Android_0.7.34_preview_bundle.zip', 'MANIFEST.json', 'SHA256SUMS')
EXTRAS = ('ACCEPTANCE.json', 'INSTALL_AND_ACCEPTANCE.ru.md',
          'RELEASE_NOTES.ru.md', 'SHA256SUMS_ALL')


def require(condition, message):
    if not condition:
        raise ValueError(message)


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def load_plan(path):
    plan = json.loads(path.read_text())
    require(set(plan) == {'source_sha', 'build_run_id', 'artifact_name', 'release_id',
                         'apk_sha256', 'certificate_sha256', 'matrix_runs'}, 'Unexpected release plan fields')
    require(re.fullmatch('[0-9a-f]{40}', plan['source_sha']) is not None, 'Invalid source SHA')
    for key in ('apk_sha256', 'certificate_sha256'):
        require(re.fullmatch('[0-9a-f]{64}', plan[key]) is not None, 'Invalid digest: ' + key)
    for key in ('build_run_id', 'release_id'):
        require(type(plan[key]) is int and plan[key] > 0, 'Invalid GitHub ID: ' + key)
    require(plan['artifact_name'] == 'LocalPilot-Android-' + plan['source_sha'], 'Wrong artifact name')
    runs = plan['matrix_runs']
    require(isinstance(runs, list) and len(runs) == 3 and
            all(type(value) is int and value > 0 for value in runs) and len(set(runs)) == 3,
            'Three distinct matrix runs required')
    return plan


def checksum_file(directory, name, expected_names):
    actual = {}
    for line in (directory / name).read_text().splitlines():
        value, filename = line.split('  ', 1)
        require(filename in expected_names and filename not in actual, 'Unexpected checksum entry')
        require(len(value) == 64 and value == digest(directory / filename), 'Checksum mismatch: ' + filename)
        actual[filename] = value
    require(set(actual) == set(expected_names), 'Incomplete checksum list: ' + name)


def prepare(download, output, sdk, plan, extras):
    manifests = list(download.rglob('MANIFEST.json'))
    require(len(manifests) == 1, 'Expected exactly one CI manifest')
    source = manifests[0].parent
    metadata = json.loads(manifests[0].read_text())
    require(metadata['sourceCommit'] == plan['source_sha'] and metadata['workingTreeDirty'] is False, 'Wrong source revision')
    require((metadata['package'], metadata['versionName'], metadata['versionCode']) ==
            ('org.localpilot.android.preview', '0.7.34-preview', 50), 'Wrong APK metadata')
    require(metadata['sha256'] == plan['apk_sha256'] and metadata['certificateSha256'] == plan['certificate_sha256'], 'Wrong artifact identity')
    require(not output.exists(), 'Choose a new staging directory')
    output.mkdir(parents=True)
    for name in ORIGINALS:
        path = source / name
        require(path.is_file() and not path.is_symlink(), 'Missing or symbolic source asset')
        shutil.copyfile(path, output / name)
    for name in EXTRAS:
        shutil.copyfile(extras / name, output / name)
    checksum_file(output, 'SHA256SUMS', ORIGINALS[:2] + ('MANIFEST.json',))
    checksum_file(output, 'SHA256SUMS_ALL', ORIGINALS + EXTRAS[:-1])
    require(digest(output / ORIGINALS[0]) == plan['apk_sha256'], 'APK digest mismatch')
    for name in ORIGINALS[:3]:
        with zipfile.ZipFile(output / name) as archive:
            require(archive.testzip() is None, 'ZIP/APK CRC mismatch: ' + name)
    signer = Path(sdk) / 'build-tools/35.0.0/apksigner'
    signature = subprocess.check_output([str(signer), 'verify', '--verbose', '--print-certs',
                                         str(output / ORIGINALS[0])], text=True)
    certificates = [line.split(': ', 1)[1] for line in signature.splitlines()
                    if line.startswith('Signer #') and ' certificate SHA-256 digest: ' in line]
    require(certificates == [plan['certificate_sha256']], 'APK signature mismatch')
    acceptance = json.loads((output / 'ACCEPTANCE.json').read_text())
    require(acceptance['source_sha'] == plan['source_sha'] and acceptance['exact_sha_pass_count'] == 3, 'Wrong acceptance source')
    require({row['id'] for row in acceptance['exact_sha_matrices']} ==
            set(plan['matrix_runs']), 'Wrong acceptance runs')
    for row in acceptance['exact_sha_matrices']:
        require(row['head_sha'] == plan['source_sha'] and row['conclusion'] == 'success' and
                len(row['jobs']) == 3 and all(job['conclusion'] == 'success' for job in row['jobs']),
                'Incomplete acceptance matrix')
    return {name: {'sha256': digest(output / name), 'size': (output / name).stat().st_size}
            for name in ORIGINALS + EXTRAS}


def request(method, url, data=None, binary=False):
    token = os.environ.get('GH_TOKEN')
    require(bool(token), 'GH_TOKEN is required for publication')
    require(url.startswith('https://api.github.com/repos/' + REPO + '/') or
            url.startswith('https://uploads.github.com/repos/' + REPO + '/'), 'Unexpected GitHub destination')
    headers = {'Authorization': 'Bearer ' + token, 'Accept': 'application/vnd.github+json',
               'X-GitHub-Api-Version': '2022-11-28'}
    if data is not None:
        headers['Content-Type'] = 'application/octet-stream' if binary else 'application/json'
        data = data if binary else json.dumps(data).encode()
    with urllib.request.urlopen(urllib.request.Request(url, data=data, headers=headers, method=method), timeout=120) as response:
        return json.load(response)


def release_identity(release, plan):
    require(release['id'] == plan['release_id'] and release['tag_name'] == TAG and
            release['target_commitish'] == plan['source_sha'] and release['draft'] is True and
            release['prerelease'] is True, 'Release is not the expected draft prerelease')


def verify_assets(release, expected):
    assets = {asset['name']: asset for asset in release['assets']}
    require(len(assets) == len(release['assets']) and set(assets) == set(expected), 'Release asset set mismatch')
    for name, value in expected.items():
        require(assets[name]['state'] == 'uploaded' and assets[name]['size'] == value['size'] and
                assets[name]['digest'] == 'sha256:' + value['sha256'], 'Uploaded asset mismatch: ' + name)


def verify_live_runs(plan):
    for run_id in [plan['build_run_id'], *plan['matrix_runs']]:
        url = f'https://api.github.com/repos/{REPO}/actions/runs/{run_id}'
        run = request('GET', url)
        require(run['head_sha'] == plan['source_sha'] and run['status'] == 'completed' and
                run['conclusion'] == 'success' and run['event'] in ('push', 'workflow_dispatch'),
                'Live CI run is not a completed exact-SHA success')
        if run_id in plan['matrix_runs']:
            result = request('GET', url + '/jobs?per_page=100')
            jobs = result['jobs']
            require(result['total_count'] == 3 and len(jobs) == 3 and
                    {job['name'] for job in jobs} == {'device (30, default)', 'device (35, default)',
                                                    'device (36, google_apis)'} and
                    all(job['status'] == 'completed' and job['conclusion'] == 'success' for job in jobs),
                    'Live Android matrix is incomplete or failed')


def publish(output, expected, plan):
    require(os.environ.get('GITHUB_REPOSITORY') == REPO, 'Publication is restricted to the source repository')
    url = f'https://api.github.com/repos/{REPO}/releases/{plan["release_id"]}'
    release = request('GET', url)
    release_identity(release, plan)
    verify_live_runs(plan)
    existing = {asset['name']: asset for asset in release['assets']}
    require(set(existing) <= set(expected), 'Unexpected existing release assets')
    for name, value in expected.items():
        if name in existing:
            require(existing[name]['size'] == value['size'] and
                    existing[name]['digest'] == 'sha256:' + value['sha256'], 'Conflicting existing asset')
            continue
        endpoint = f'https://uploads.github.com/repos/{REPO}/releases/{plan["release_id"]}/assets?' + urllib.parse.urlencode({'name': name})
        request('POST', endpoint, (output / name).read_bytes(), binary=True)
        print('Uploaded:', name)
    release = request('GET', url)
    release_identity(release, plan)
    verify_assets(release, expected)
    published = request('PATCH', url, {'draft': False, 'prerelease': True, 'make_latest': 'false',
                                     'body': (output / 'RELEASE_NOTES.ru.md').read_text()})
    require(not published['draft'] and published['prerelease'] and
            published['tag_name'] == TAG and published['target_commitish'] == plan['source_sha'],
            'Publication metadata mismatch')
    verify_assets(published, expected)
    print('Published verified Preview prerelease:', published['html_url'])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--plan', type=Path, required=True)
    parser.add_argument('--download', type=Path)
    parser.add_argument('--output', type=Path)
    parser.add_argument('--sdk')
    parser.add_argument('--emit-download-outputs', type=Path)
    parser.add_argument('--publish', action='store_true')
    args = parser.parse_args()
    plan = load_plan(args.plan)
    if args.emit_download_outputs:
        require(not args.publish, 'Cannot publish while only emitting download outputs')
        with args.emit_download_outputs.open('a') as stream:
            stream.write('run=' + str(plan['build_run_id']) + '\nartifact=' + plan['artifact_name'] + '\n')
        return
    require(args.download is not None and args.output is not None and args.sdk is not None,
            'Download, output and SDK are required for verification')
    expected = prepare(args.download, args.output, args.sdk, plan, args.plan.parent)
    print('Verified asset count:', len(expected))
    if args.publish:
        publish(args.output, expected, plan)


if __name__ == '__main__':
    main()
