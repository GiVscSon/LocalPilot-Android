"""Publish only the existing, verified Dev1 artifact; never build or re-sign APKs."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import urllib.parse
import urllib.request
import zipfile

REPO = 'GiVscSon/LocalPilot-Android'
SHA = '9d977d852e1ccb70a162abcf93b7dc3dd185980a'
TAG = 'v0.7.34-dev.1'
RELEASE = 428314953
APK_SHA = 'c79ce6293d1f3f89f0c3c94fe7f6749361de4c3f9ec3fec6aa63bd4ba91536af'
CERT_SHA = '010a04cb0888a4627b095def584f131b5161cff81c4d74c1513cbb932bfafcde'
ORIGINALS = ('LocalPilot_Android_0.7.34_dev.apk',
             'LocalPilot_Android_0.7.34_dev_source.zip',
             'LocalPilot_Android_0.7.34_dev_bundle.zip', 'MANIFEST.json', 'SHA256SUMS')
EXTRAS = ('ACCEPTANCE.json', 'INSTALL_AND_ACCEPTANCE.ru.md',
          'RELEASE_NOTES.ru.md', 'SHA256SUMS_ALL')


def require(condition, message):
    if not condition:
        raise ValueError(message)


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def checksum_file(directory, name, expected_names):
    actual = {}
    for line in (directory / name).read_text().splitlines():
        value, filename = line.split('  ', 1)
        require(filename in expected_names and filename not in actual, 'Unexpected checksum entry')
        require(len(value) == 64 and value == digest(directory / filename), 'Checksum mismatch: ' + filename)
        actual[filename] = value
    require(set(actual) == set(expected_names), 'Incomplete checksum list: ' + name)


def prepare(download, output, sdk):
    manifests = list(download.rglob('MANIFEST.json'))
    require(len(manifests) == 1, 'Expected exactly one CI manifest')
    source = manifests[0].parent
    metadata = json.loads(manifests[0].read_text())
    require(metadata['sourceCommit'] == SHA and metadata['workingTreeDirty'] is False, 'Wrong source revision')
    require((metadata['package'], metadata['versionName'], metadata['versionCode']) ==
            ('org.localpilot.android.dev', '0.7.34-dev', 50), 'Wrong APK metadata')
    require(metadata['sha256'] == APK_SHA and metadata['certificateSha256'] == CERT_SHA, 'Wrong artifact identity')
    require(not output.exists(), 'Choose a new staging directory')
    output.mkdir(parents=True)
    for name in ORIGINALS:
        path = source / name
        require(path.is_file() and not path.is_symlink(), 'Missing or symbolic source asset')
        shutil.copyfile(path, output / name)
    for name in EXTRAS:
        shutil.copyfile(Path(__file__).parent / 'verified-dev1' / name, output / name)
    checksum_file(output, 'SHA256SUMS', ORIGINALS[:2] + ('MANIFEST.json',))
    checksum_file(output, 'SHA256SUMS_ALL', ORIGINALS + EXTRAS[:-1])
    require(digest(output / ORIGINALS[0]) == APK_SHA, 'APK digest mismatch')
    for name in ORIGINALS[:3]:
        with zipfile.ZipFile(output / name) as archive:
            require(archive.testzip() is None, 'ZIP/APK CRC mismatch: ' + name)
    signer = Path(sdk) / 'build-tools/35.0.0/apksigner'
    signature = subprocess.check_output([str(signer), 'verify', '--verbose', '--print-certs',
                                         str(output / ORIGINALS[0])], text=True)
    certificates = [line.split(': ', 1)[1] for line in signature.splitlines()
                    if line.startswith('Signer #') and ' certificate SHA-256 digest: ' in line]
    require(certificates == [CERT_SHA], 'APK signature mismatch')
    acceptance = json.loads((output / 'ACCEPTANCE.json').read_text())
    require(acceptance['source_sha'] == SHA and acceptance['exact_sha_pass_count'] == 3, 'Wrong acceptance source')
    require({row['id'] for row in acceptance['exact_sha_matrices']} ==
            {40937714200, 40937878368, 40938378702}, 'Wrong acceptance runs')
    for row in acceptance['exact_sha_matrices']:
        require(row['head_sha'] == SHA and row['conclusion'] == 'success' and
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


def release_identity(release):
    require(release['id'] == RELEASE and release['tag_name'] == TAG and
            release['target_commitish'] == SHA and release['draft'] is True and
            release['prerelease'] is True, 'Release is not the expected draft prerelease')


def verify_assets(release, expected):
    assets = {asset['name']: asset for asset in release['assets']}
    require(len(assets) == len(release['assets']) and set(assets) == set(expected), 'Release asset set mismatch')
    for name, value in expected.items():
        require(assets[name]['state'] == 'uploaded' and assets[name]['size'] == value['size'] and
                assets[name]['digest'] == 'sha256:' + value['sha256'], 'Uploaded asset mismatch: ' + name)


def publish(output, expected):
    require(os.environ.get('GITHUB_REPOSITORY') == REPO, 'Publication is restricted to the source repository')
    url = f'https://api.github.com/repos/{REPO}/releases/{RELEASE}'
    release = request('GET', url)
    release_identity(release)
    existing = {asset['name']: asset for asset in release['assets']}
    require(set(existing) <= set(expected), 'Unexpected existing release assets')
    for name, value in expected.items():
        if name in existing:
            require(existing[name]['size'] == value['size'] and
                    existing[name]['digest'] == 'sha256:' + value['sha256'], 'Conflicting existing asset')
            continue
        endpoint = f'https://uploads.github.com/repos/{REPO}/releases/{RELEASE}/assets?' + urllib.parse.urlencode({'name': name})
        request('POST', endpoint, (output / name).read_bytes(), binary=True)
        print('Uploaded:', name)
    release = request('GET', url)
    release_identity(release)
    verify_assets(release, expected)
    published = request('PATCH', url, {'draft': False, 'prerelease': True, 'make_latest': 'false',
                                     'body': (output / 'RELEASE_NOTES.ru.md').read_text()})
    require(not published['draft'] and published['prerelease'] and
            published['tag_name'] == TAG and published['target_commitish'] == SHA,
            'Publication metadata mismatch')
    verify_assets(published, expected)
    print('Published verified Dev prerelease:', published['html_url'])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--download', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--sdk', required=True)
    parser.add_argument('--publish', action='store_true')
    args = parser.parse_args()
    expected = prepare(args.download, args.output, args.sdk)
    print('Verified asset count:', len(expected))
    if args.publish:
        publish(args.output, expected)


if __name__ == '__main__':
    main()
