"""Run the isolated Android test APK and expose bounded failures in CI annotations."""
import json
from pathlib import Path
import subprocess
import sys
import time
from release_common import configuration

OUT = Path('device-evidence')
ROOT = Path(__file__).resolve().parents[1]


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
    isolated_emulator=False
    try:
        package = configuration(ROOT)['package']
        instrumentation = package + '.test/org.localpilot.android.PilotUiInstrumentation'
        service = package + '/org.localpilot.android.PilotService'
        qemu=command(['adb','shell','getprop','ro.kernel.qemu']).strip()
        boot_qemu=command(['adb','shell','getprop','ro.boot.qemu']).strip()
        if qemu!='1' and boot_qemu!='1':
            raise RuntimeError('Isolated emulator required; refusing to change Accessibility settings on a physical device')
        isolated_emulator=True
        command(['adb', 'install', '-r', 'app/build/outputs/apk/debug/app-debug.apk'])
        command(['adb', 'install', '-r', 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'])
        command(['adb','shell','settings','put','system','screen_off_timeout','600000'])
        command(['adb','shell','svc','power','stayon','true'])
        command(['adb','shell','input','keyevent','KEYCODE_WAKEUP'])
        command(['adb','shell','wm','dismiss-keyguard'])
        command(['adb', 'shell', 'settings', 'delete', 'secure', 'enabled_accessibility_services'])
        command(['adb', 'shell', 'settings', 'put', 'secure', 'accessibility_enabled', '0'])
        with (OUT / 'instrumentation.txt').open('w', encoding='utf-8') as log:
            with subprocess.Popen(['adb', 'shell', 'am', 'instrument', '-w',
                                   instrumentation],
                                  stdin=subprocess.DEVNULL, stdout=log, stderr=subprocess.STDOUT) as process:
                try:
                    deadline=time.monotonic()+20
                    while True:
                        status=(OUT/'instrumentation.txt').read_text(encoding='utf-8',errors='replace')
                        if 'INSTRUMENTATION_STATUS: localpilot_ready=1' in status:break
                        if process.poll() is not None or time.monotonic()>=deadline:
                            raise RuntimeError('Instrumentation did not attach UiAutomation\n'+(OUT/'instrumentation.txt').read_text()[-2500:])
                        time.sleep(.2)
                    command(['adb', 'shell', 'settings', 'put', 'secure', 'enabled_accessibility_services',
                             service])
                    command(['adb', 'shell', 'settings', 'put', 'secure', 'accessibility_enabled', '1'])
                    process.wait(timeout=300)
                    if process.returncode:raise RuntimeError('Instrumentation process failed\n'+(OUT/'instrumentation.txt').read_text()[-2500:])
                finally:
                    if process.poll() is None:process.kill();process.wait(timeout=5)
        output=(OUT/'instrumentation.txt').read_text(encoding='utf-8',errors='replace')
        prefix='INSTRUMENTATION_RESULT: evidence_json='
        payload=next((line[len(prefix):] for line in output.splitlines() if line.startswith(prefix)),None)
        if payload is None:raise RuntimeError('No final instrumentation report\n'+output[-3500:])
        report=json.loads(payload)
        (OUT/'pilot-ui-evidence.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
        print(json.dumps(report, indent=2), flush=True)
        if report.get('result') != 'PASS':
            annotation('notice', 'Failure at ms=' + str(report.get('failed_at_ms')) + '\nWindows: ' + str(report.get('windows','')))
            # This runner accepts only an isolated synthetic emulator. Keep the
            # failure evidence bounded and useful without downloading raw logs.
            trace = report.get('interaction_trace')
            if trace:
                if isinstance(trace, list):
                    trace = trace[-12:]
                annotation('notice', 'UI interaction/lifecycle trace:\n'
                           + json.dumps(trace, ensure_ascii=False)[-3500:])
            slow_main = report.get('slow_main_trace')
            if slow_main:
                annotation('notice', 'Slow main-thread dispatches:\n'
                           + json.dumps(slow_main[-12:], ensure_ascii=False)[-3500:])
            timing = report.get('capture_timing')
            if isinstance(timing, str):
                # Bounded metadata only: request ID, monotonic age and main
                # watchdog queue lag. No frame pixels, screenshots or game IDs.
                annotation('notice', 'Screenshot callback timing:\n'
                           + '\n'.join(timing.splitlines()[-14:])[-3200:])
            metrics = report.get('metrics')
            if isinstance(metrics, str):
                selected = [line[:500] for line in metrics.splitlines()
                            if line.startswith(('CAPTURE ', 'READ ', 'ANDROID_ACK '))]
                if selected:
                    annotation('notice', 'Capture/read/ACK metrics:\n' + '\n'.join(selected[:6]))
            raise RuntimeError(str(report.get('failure', report.get('evidence_error', 'No PASS result')))
                               + '\n' + str(report.get('own_state',''))
                               + '\nUI: ' + str(report.get('visible_ui',''))[:2000]
                               + '\n' + str(report.get('stack', ''))[:1000])
        annotation('notice', 'PASS: ' + str(len(report.get('checks', []))) + ' actual Android acceptance scenarios')
        return 0
    except (OSError, RuntimeError, ValueError, subprocess.TimeoutExpired) as error:
        text = str(error)
        (OUT / 'runner-error.txt').write_text(text[:8000], encoding='utf-8')
        annotation('error', text)
        if not isolated_emulator:return 1
        try:
            crash=command(['adb','logcat','-d','-b','crash'],10)
            (OUT/'crash-logcat.txt').write_text(crash[-16000:],encoding='utf-8')
            if crash.strip():annotation('error','Isolated test device crash buffer:\n'+crash[-3000:])
            activity=command(['adb','shell','dumpsys','activity','activities'],10)
            (OUT/'activities.txt').write_text(activity,encoding='utf-8')
            summary=[line for line in activity.splitlines() if any(key in line for key in ['mResumedActivity','topResumedActivity','mFocusedApp','localpilot'])]
            annotation('notice','Activity state:\n'+'\n'.join(summary)[:3000])
            state=command(['adb','shell','dumpsys','accessibility'],10)
            (OUT/'accessibility.txt').write_text(state[:32000],encoding='utf-8')
            own=[line for line in state.splitlines() if 'localpilot' in line.lower() or 'UiAutomation' in line or 'mIsAccessibilityEnabled' in line]
            annotation('notice','Accessibility test state:\n'+'\n'.join(own)[:3000])
        except (OSError,RuntimeError,subprocess.TimeoutExpired):pass
        return 1


if __name__ == '__main__':
    sys.exit(main())
