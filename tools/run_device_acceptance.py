"""Run the isolated Android test APK and expose bounded failures in CI annotations."""
import json
from pathlib import Path
import subprocess
import sys
import time

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
        command(['adb', 'shell', 'settings', 'delete', 'secure', 'enabled_accessibility_services'])
        command(['adb', 'shell', 'settings', 'put', 'secure', 'accessibility_enabled', '0'])
        ready='/sdcard/Android/data/org.localpilot.android.dev/files/instrumentation/pilot-ui-ready.txt'
        command(['adb', 'shell', 'rm', '-f', ready])
        with (OUT / 'instrumentation.txt').open('w', encoding='utf-8') as log:
            with subprocess.Popen(['adb', 'shell', 'am', 'instrument', '-w',
                                   'org.localpilot.android.dev.test/org.localpilot.android.PilotUiInstrumentation'],
                                  stdin=subprocess.DEVNULL, stdout=log, stderr=subprocess.STDOUT) as process:
                try:
                    deadline=time.monotonic()+20
                    while True:
                        exists=subprocess.run(['adb','shell','test','-f',ready],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,timeout=5).returncode==0
                        if exists:break
                        if process.poll() is not None or time.monotonic()>=deadline:
                            raise RuntimeError('Instrumentation did not attach UiAutomation\n'+(OUT/'instrumentation.txt').read_text()[-2500:])
                        time.sleep(.2)
                    command(['adb', 'shell', 'settings', 'put', 'secure', 'enabled_accessibility_services',
                             'org.localpilot.android.dev/org.localpilot.android.PilotService'])
                    command(['adb', 'shell', 'settings', 'put', 'secure', 'accessibility_enabled', '1'])
                    process.wait(timeout=300)
                    if process.returncode:raise RuntimeError('Instrumentation process failed\n'+(OUT/'instrumentation.txt').read_text()[-2500:])
                finally:
                    if process.poll() is None:process.kill();process.wait(timeout=5)
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
        try:
            crash=command(['adb','logcat','-d','-b','crash'],10)
            (OUT/'crash-logcat.txt').write_text(crash[-16000:],encoding='utf-8')
            if crash.strip():annotation('error','Isolated test device crash buffer:\n'+crash[-3000:])
            state=command(['adb','shell','dumpsys','accessibility'],10)
            (OUT/'accessibility.txt').write_text(state[:32000],encoding='utf-8')
            own=[line for line in state.splitlines() if 'localpilot' in line.lower() or 'UiAutomation' in line or 'mIsAccessibilityEnabled' in line]
            annotation('notice','Accessibility test state:\n'+'\n'.join(own)[:3000])
        except (OSError,RuntimeError,subprocess.TimeoutExpired):pass
        return 1


if __name__ == '__main__':
    sys.exit(main())
