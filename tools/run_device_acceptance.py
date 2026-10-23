"""Run the isolated Android test APK and expose bounded failures in CI annotations."""
import json
from pathlib import Path
import subprocess
import sys

OUT = Path('device-evidence')


def command(arguments, timeout=45):
    result = subprocess.run(arguments, stdin=subprocess.DEVNULL, stdout=subprocess.PIPE,
                            stderr=subprocess.STDOUT, text=True, timeout=timeout)
    if result.returncode:
        raise RuntimeError('Command failed: ' + ' '.join(arguments[:5]) + '\n' + result.stdout[-3500:])
    return result.stdout


def annotation(level, message):
    safe = str(message)[:4000].replace('%', '%25').replace('\r', '%0D').replace('\n', '%0A')
    print('::' + level + ' title=Android device acceptance::' + safe, flush=True)


def main():
    OUT.mkdir(exist_ok=True)
    try:
        command(['adb', 'install', '-r', 'app/build/outputs/apk/debug/app-debug.apk'])
        command(['adb', 'install', '-r', 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'])
        command(['adb', 'shell', 'settings', 'put', 'secure', 'enabled_accessibility_services',
                 'org.localpilot.android.dev/org.localpilot.android.PilotService'])
        command(['adb', 'shell', 'settings', 'put', 'secure', 'accessibility_enabled', '1'])
        command(['adb', 'shell', 'am', 'start', '-n', 'org.localpilot.android.dev/org.localpilot.android.MainActivity'])
        output = command(['adb', 'shell', 'am', 'instrument', '-w',
                          'org.localpilot.android.dev.test/org.localpilot.android.PilotUiInstrumentation'], 300)
        (OUT / 'instrumentation.txt').write_text(output, encoding='utf-8')
        command(['adb', 'pull', '/sdcard/Android/data/org.localpilot.android.dev/files/instrumentation/pilot-ui-evidence.json',
                 str(OUT / 'pilot-ui-evidence.json')])
        report = json.loads((OUT / 'pilot-ui-evidence.json').read_text(encoding='utf-8'))
        print(json.dumps(report, indent=2), flush=True)
        if report.get('result') != 'PASS':
            raise RuntimeError(str(report.get('failure', report.get('evidence_error', 'No PASS result')))
                               + '\n' + str(report.get('stack', ''))[:2500])
        annotation('notice', 'PASS: ' + str(len(report.get('checks', []))) + ' actual Android acceptance scenarios')
        return 0
    except (OSError, RuntimeError, ValueError, subprocess.TimeoutExpired) as error:
        text = str(error)
        (OUT / 'runner-error.txt').write_text(text[:8000], encoding='utf-8')
        annotation('error', text)
        return 1


if __name__ == '__main__':
    sys.exit(main())
