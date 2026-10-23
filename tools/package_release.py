"""Verify Dev APK and package current source without local keys or device captures."""
import argparse
import json
from pathlib import Path
import os
import re
import shutil
import subprocess
import tempfile
import zipfile
from release_common import configuration, verify_apk, digest

ROOT = Path(__file__).resolve().parents[1]
EXCLUDED = {'.git', '.gradle', '.build', 'build', 'release', 'evidence', '.idea',
            '__pycache__', '.venv', '.venv-rl', '.codex-attachments', 'node_modules'}
PRIVATE_SUFFIXES = {'.jks', '.keystore', '.pem', '.key', '.p12', '.apk', '.aab', '.pyc',
                    '.mp4', '.mov', '.webm', '.jpg', '.jpeg', '.png', '.log',
                    '.zip', '.csv', '.npz', '.npy', '.pt', '.pth', '.safetensors'}
SECRETS = re.compile(rb'-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----|ghp_[A-Za-z0-9]{36}|github_pat_[A-Za-z0-9_]{60,}|AKIA[A-Z0-9]{16}')


def source_files(root, excluded_output=None):
    output = subprocess.check_output(['git', 'ls-files', '--cached', '--others', '--exclude-standard', '-z'], cwd=root)
    tracked = set(subprocess.check_output(['git', 'ls-files', '-z'], cwd=root).split(b'\0'))
    for raw in sorted(set(output.split(b'\0')) - {b''}):
        relative = Path(os.fsdecode(raw))
        if relative.is_absolute() or '..' in relative.parts:
            raise ValueError('Unsafe source path')
        if raw not in tracked and relative.suffix.lower() not in {'.java', '.kt', '.py', '.md', '.gradle', '.yml', '.yaml', '.sh', '.xml'}:
            continue
        if any(p in EXCLUDED or p.endswith('.egg-info') for p in relative.parts):
            continue
        path = root / relative
        if excluded_output and path.resolve().is_relative_to(excluded_output):
            continue
        if path.is_symlink() or any((root / Path(*relative.parts[:i])).is_symlink() for i in range(1, len(relative.parts))):
            raise ValueError('Source symlink: ' + str(relative))
        if path.name == 'local.properties' or path.name == '.env' or path.name.startswith('.env.'):
            continue
        if path.suffix.lower() in PRIVATE_SUFFIXES or path.name.startswith('localpilot-'):
            continue
        if any(p.lower() in {'recordings', 'captures', 'checkpoints', 'datasets', 'user-data'} for p in relative.parts):
            continue
        if not path.is_file():
            continue
        if path.stat().st_size > 16 * 1024 * 1024:
            raise ValueError('Source file exceeds 16 MiB bound: ' + str(relative))
        if SECRETS.search(path.read_bytes()):
            raise ValueError('Secret pattern in source: ' + str(relative))
        yield path, relative


def package(root, apk, output, sdk=None, expected_certificate=None):
    root, apk, output = Path(root), Path(apk), Path(output)
    root, output = root.resolve(), output.resolve()
    if root.is_relative_to(output):
        raise ValueError('Output directory cannot contain the source root')
    config = configuration(root)
    metadata = verify_apk(apk, config, sdk, expected_certificate)
    metadata['sourceCommit'] = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=root, text=True).strip()
    metadata['workingTreeDirty'] = bool(subprocess.check_output(['git', 'status', '--porcelain', '--untracked-files=normal'], cwd=root))
    destination = output / config['name']
    output.mkdir(parents=True, exist_ok=True)
    if destination.exists():
        raise FileExistsError('Release already exists; choose another --output directory')
    with tempfile.TemporaryDirectory(prefix='.package-', dir=output) as temporary:
        staging = Path(temporary)
        apk_name = config['name'] + '.apk'
        shutil.copyfile(apk, staging / apk_name)
        source_name = config['name'] + '_source.zip'
        with zipfile.ZipFile(staging / source_name, 'w', zipfile.ZIP_DEFLATED) as archive:
            for path, relative in source_files(root, output):
                archive.write(path, 'LocalPilot-Android/' + relative.as_posix())
        with zipfile.ZipFile(staging / source_name) as archive:
            if archive.testzip() is not None:
                raise ValueError('Source ZIP CRC failed')
        metadata['files'] = {n: {'sha256': digest(staging / n), 'bytes': (staging / n).stat().st_size}
                             for n in (apk_name, source_name)}
        metadata['physicalAndroidTest'] = 'not verified by packaging'
        (staging / 'MANIFEST.json').write_text(json.dumps(metadata, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
        names = [apk_name, source_name, 'MANIFEST.json']
        (staging / 'SHA256SUMS').write_text(''.join(digest(staging / n) + '  ' + n + '\n' for n in names), encoding='utf-8')
        bundle = config['name'] + '_bundle.zip'
        with zipfile.ZipFile(staging / bundle, 'w', zipfile.ZIP_DEFLATED) as archive:
            for name in [*names, 'SHA256SUMS']:
                archive.write(staging / name, name)
        with zipfile.ZipFile(staging / bundle) as archive:
            if archive.testzip() is not None:
                raise ValueError('Bundle ZIP CRC failed')
        destination.mkdir()
        try:
            for path in staging.iterdir():
                path.replace(destination / path.name)
        except OSError:
            shutil.rmtree(destination)
            raise
    return destination


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apk', type=Path, default=ROOT / 'app/build/outputs/apk/debug/app-debug.apk')
    parser.add_argument('--output', type=Path, default=ROOT / 'release')
    parser.add_argument('--sdk')
    parser.add_argument('--expected-certificate', help='Expected signer SHA256; no private key required')
    args = parser.parse_args()
    print(package(ROOT, args.apk, args.output, args.sdk, args.expected_certificate))


if __name__ == '__main__':
    try:
        main()
    except (ValueError, OSError, subprocess.CalledProcessError, zipfile.BadZipFile) as error:
        raise SystemExit(str(error))
