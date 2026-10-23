"""Shared stdlib release checks. Unsupported Gradle expressions fail closed."""
import hashlib
from pathlib import Path
import os
import re
import subprocess
import zipfile


def configuration(root):
    source = (Path(root) / 'app/build.gradle').read_text(encoding='utf-8')
    source = re.sub(r'/\*.*?\*/|//[^\n]*', '', source, flags=re.S)
    def one(name, pattern):
        matches = re.findall(r'\b' + name + r'\s+' + pattern, source)
        if len(matches) != 1:
            raise ValueError('Expected one literal ' + name + ' in app/build.gradle')
        return matches[0]
    config = {name: int(one(name, r'(\d+)\b')) for name in ('versionCode', 'compileSdk', 'minSdk', 'targetSdk')}
    for name in ('versionName', 'applicationId', 'applicationIdSuffix', 'versionNameSuffix'):
        config[name] = one(name, r"['\"]([^'\"]+)['\"]")
    config['package'] = config['applicationId'] + config['applicationIdSuffix']
    config['version'] = config['versionName'] + config['versionNameSuffix']
    if config['versionCode'] <= 0 or not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9._-]*', config['version']):
        raise ValueError('Invalid Dev version')
    if not re.fullmatch(r'[A-Za-z]\w*(?:\.[A-Za-z]\w*)+', config['package']):
        raise ValueError('Invalid application ID')
    config['name'] = 'LocalPilot_Android_' + config['version'].replace('-', '_')
    return config


def sdk_tools(sdk=None):
    sdk = sdk or os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
    if not sdk:
        raise ValueError('Set ANDROID_HOME or pass --sdk')
    tools = Path(sdk).resolve() / 'build-tools/35.0.0'
    return {name: str(tools / (name + ('.bat' if name == 'apksigner' else '.exe') if os.name == 'nt' else name))
            for name in ('aapt', 'apksigner')}


def digest(path):
    h = hashlib.sha256()
    with Path(path).open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            h.update(block)
    return h.hexdigest()


def verify_apk(apk, config, sdk=None, expected_certificate=None):
    apk = Path(apk)
    with zipfile.ZipFile(apk) as archive:
        if archive.testzip() is not None or not {'AndroidManifest.xml', 'classes.dex'} <= set(archive.namelist()):
            raise ValueError('APK CRC or required entries invalid')
    tools = sdk_tools(sdk)
    badging = subprocess.check_output([tools['aapt'], 'dump', 'badging', str(apk)], text=True)
    match = re.search(r"^package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", badging, re.M)
    if not match or match.groups() != (config['package'], str(config['versionCode']), config['version']):
        raise ValueError('APK package/version differs from app/build.gradle; rebuild before packaging')
    minimum = re.search(r"^sdkVersion:'(\d+)'", badging, re.M)
    target = re.search(r"^targetSdkVersion:'(\d+)'", badging, re.M)
    if not minimum or not target or (int(minimum[1]), int(target[1])) != (config['minSdk'], config['targetSdk']):
        raise ValueError('APK SDK levels differ from app/build.gradle')
    signature = subprocess.check_output([tools['apksigner'], 'verify', '--verbose', '--print-certs', str(apk)], text=True)
    certificates = re.findall(r'^Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]{64})$', signature, re.M)
    if len(certificates) != 1:
        raise ValueError('Expected exactly one APK signer')
    certificate = certificates[0].lower()
    if expected_certificate and certificate != expected_certificate.lower():
        raise ValueError('Signing certificate differs from expected certificate')
    return {'apk': str(apk.resolve()), 'package': config['package'], 'versionName': config['version'],
            'versionCode': config['versionCode'], 'certificateSha256': certificate,
            'sha256': digest(apk), 'bytes': apk.stat().st_size}
