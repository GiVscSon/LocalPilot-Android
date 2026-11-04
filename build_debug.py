"""Build and verify the Gradle Dev APK, using the current manifest and signing key."""
import argparse
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parent
sys.path.insert(0, str(ROOT / 'tools'))
from release_common import configuration, verify_apk


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--sdk', default=os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT'))
    parser.add_argument('--gradle', help='Installed Gradle executable; default: project wrapper')
    parser.add_argument('--offline', action='store_true')
    parser.add_argument('--output', type=Path, help='Optional new destination; existing files are never overwritten')
    args = parser.parse_args()
    config = configuration(ROOT)
    if args.output and args.output.exists():
        parser.error('Output already exists; choose a new path')
    env = os.environ.copy()
    if args.sdk:
        env['ANDROID_HOME'] = str(Path(args.sdk).resolve())
    command = [args.gradle or str(ROOT / ('gradlew.bat' if os.name == 'nt' else 'gradlew')), '--no-daemon']
    if args.offline:
        command.append('--offline')
    command += [':app:assembleDebug', ':app:lintDebug', ':app:compileDebugAndroidTestJavaWithJavac']
    subprocess.run(command, cwd=ROOT, env=env, check=True)
    apk = ROOT / 'app/build/outputs/apk/debug/app-debug.apk'
    result = verify_apk(apk, config, args.sdk)
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        with args.output.open('xb') as out, apk.open('rb') as source:
            shutil.copyfileobj(source, out)
        result['apk'] = str(args.output.resolve())
    print(json.dumps(result, indent=2))


if __name__ == '__main__':
    try:
        main()
    except (ValueError, OSError, subprocess.CalledProcessError) as error:
        raise SystemExit(str(error))
