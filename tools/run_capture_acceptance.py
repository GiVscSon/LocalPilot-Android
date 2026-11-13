"""Run focused capture acceptance on an isolated x86_64 Android emulator.

The ordinary app and its existing test companion are the only installed APKs.
No native inference, physical-device acceptance, game reward or release is claimed.
"""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import time
import zipfile

from release_common import configuration
from verify_harness_apk import inspect_apk

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = configuration(ROOT)['package']
INSTRUMENTATION = 'org.localpilot.android.CaptureAcceptanceInstrumentation'
OUT = Path('capture-evidence')
CHECKS = {
    'actual_bridge_fullscreen_pixels_and_geometry',
    'actual_screenshot_late_callback_after_stop_rejected',
    'actual_replaced_window_owner_rejected',
    'actual_reader_stop_prevents_diagnostic_frame_resurrection',
    'actual_secure_content_fail_closed_without_fallback',
}


def command(arguments, timeout=30):
    result = subprocess.run(arguments, stdin=subprocess.DEVNULL, stdout=subprocess.PIPE,
                            stderr=subprocess.STDOUT, text=True, timeout=timeout)
    if result.returncode:
        raise RuntimeError('Command failed: ' + ' '.join(arguments[:6]) + '\n' + result.stdout[-2000:])
    return result.stdout


def validate_report(report, api):
    """A completed instrumentation command alone is never an acceptance PASS."""
    if report.get('result') != 'PASS':
        raise RuntimeError(str(report.get('failure', 'No capture PASS result'))[:2000])
    if report.get('schema') != 'localpilot-android-window-capture-v1':
        raise RuntimeError('Unexpected capture evidence schema')
    if report.get('api') != api or report.get('physical_device') is not False:
        raise RuntimeError('Capture evidence API/emulator provenance mismatch')
    checks = report.get('checks')
    if not isinstance(checks, list) or any(not isinstance(item, dict) for item in checks):
        raise RuntimeError('Malformed capture checks')
    if len(checks) != len(CHECKS) or {item.get('name') for item in checks} != CHECKS:
        raise RuntimeError('Missing, duplicated or unexpected capture acceptance checks')
    if any(item.get('result') != 'PASS' for item in checks):
        raise RuntimeError('A mandatory capture check did not pass')
    route = next(item for item in checks if item['name'] == 'actual_bridge_fullscreen_pixels_and_geometry')
    if route.get('route') != ('WINDOW_FULLSCREEN' if api >= 34 else 'DISPLAY'):
        raise RuntimeError('Capture evidence used the wrong actual API route')


def metadata():
    api = int(command(['adb', 'shell', 'getprop', 'ro.build.version.sdk']).strip())
    if api not in (30, 34, 35, 36) or str(api) != os.environ.get('LOCALPILOT_EXPECTED_API'):
        raise RuntimeError('Observed Android API differs from the requested capture matrix')
    abi = command(['adb', 'shell', 'getprop', 'ro.product.cpu.abi']).strip()
    if abi != 'x86_64':
        raise RuntimeError('Focused capture CI requires the declared x86_64 emulator')
    source = command(['git', 'rev-parse', 'HEAD']).strip()
    if len(source) != 40 or source != os.environ.get('LOCALPILOT_BUILD_SOURCE_SHA'):
        raise RuntimeError('Capture checkout differs from its reviewed source SHA')
    artifacts = {}
    for name, path in (
        ('app', ROOT / 'app/build/outputs/apk/debug/app-debug.apk'),
        ('instrumentation', ROOT / 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'),
    ):
        with zipfile.ZipFile(path) as archive:
            if archive.testzip() is not None:
                raise RuntimeError('APK ZIP CRC failure: ' + name)
        artifacts[name + '_sha256'] = hashlib.sha256(path.read_bytes()).hexdigest()
    apk_audit = inspect_apk(ROOT / 'app/build/outputs/apk/debug/app-debug.apk', expected_source_sha=source)
    return dict(source_sha=source, api=api, emulator_abi=abi, emulator_profile='pixel_2',
                source='ISOLATED_ANDROID_EMULATOR', physical_device=False,
                scope='Java capture path; no native GGUF backend or ARM64 release claim',
                apk_packaging_audit=apk_audit, **artifacts)


def collect_failure():
    """Call only after the isolated-emulator guard, never on a physical phone."""
    queries = {
        'accessibility.txt': (['adb', 'shell', 'dumpsys', 'accessibility'], 32000),
        'pilot-service-state.txt': (['adb', 'shell', 'dumpsys', 'activity', 'services',
                                     PACKAGE + '/org.localpilot.android.PilotService'], 8000),
        'crash-logcat.txt': (['adb', 'logcat', '-d', '-b', 'crash'], 16000),
        'enabled-services.txt': (['adb', 'shell', 'settings', 'get', 'secure', 'enabled_accessibility_services'], 2048),
        'accessibility-enabled.txt': (['adb', 'shell', 'settings', 'get', 'secure', 'accessibility_enabled'], 100),
    }
    for name, (args, limit) in queries.items():
        try:
            value = command(args, timeout=10)
        except (OSError, RuntimeError, subprocess.TimeoutExpired) as error:
            value = 'Diagnostic query failed: ' + str(error)[:500]
        (OUT / name).write_text(value[:limit], encoding='utf-8')


def main():
    OUT.mkdir(exist_ok=True)
    isolated = False
    try:
        qemu = [command(['adb', 'shell', 'getprop', key]).strip()
                for key in ('ro.kernel.qemu', 'ro.boot.qemu')]
        if '1' not in qemu:
            raise RuntimeError('Isolated emulator required; refusing Accessibility setup on a physical device')
        isolated = True
        observed = metadata()
        (OUT / 'device-metadata.json').write_text(json.dumps(observed, indent=2), encoding='utf-8')
        command(['adb', 'install', '-r', 'app/build/outputs/apk/debug/app-debug.apk'], 80)
        command(['adb', 'install', '-r', 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'], 80)
        registered = command(['adb', 'shell', 'pm', 'list', 'instrumentation'])
        expected = 'instrumentation:' + PACKAGE + '.test/' + INSTRUMENTATION + ' (target=' + PACKAGE + ')'
        if expected not in registered.splitlines():
            raise RuntimeError('Focused instrumentation is not registered for the actual app package: ' + registered[:1500])
        command(['adb', 'shell', 'input', 'keyevent', 'KEYCODE_WAKEUP'])
        command(['adb', 'shell', 'wm', 'dismiss-keyguard'])
        command(['adb', 'shell', 'settings', 'put', 'system', 'screen_off_timeout', '600000'])
        # The fresh AVD's first-use immersive tutorial steals real window focus
        # after a delay. This is SystemUI navigation onboarding, not screenshot
        # consent or a security permission. Configure only this guarded emulator.
        command(['adb', 'shell', 'settings', 'put', 'secure', 'immersive_mode_confirmations', 'confirmed'])
        immersive = command(['adb', 'shell', 'settings', 'get', 'secure', 'immersive_mode_confirmations']).strip()
        if immersive != 'confirmed':
            raise RuntimeError('Isolated fullscreen fixture onboarding setup failed')
        (OUT / 'fixture-setup.json').write_text(json.dumps(dict(
            immersive_tutorial=immersive, permission_bypass=False,
            scope='isolated emulator navigation tutorial only')), encoding='utf-8')
        command(['adb', 'shell', 'settings', 'delete', 'secure', 'enabled_accessibility_services'])
        command(['adb', 'shell', 'settings', 'put', 'secure', 'accessibility_enabled', '0'])
        with (OUT / 'instrumentation.txt').open('w', encoding='utf-8') as log:
            with subprocess.Popen(['adb', 'shell', 'am', 'instrument', '-w',
                                   PACKAGE + '.test/' + INSTRUMENTATION],
                                  stdin=subprocess.DEVNULL, stdout=log, stderr=subprocess.STDOUT) as process:
                try:
                    end = time.monotonic() + 20
                    while True:
                        text = (OUT / 'instrumentation.txt').read_text(errors='replace')
                        if 'INSTRUMENTATION_STATUS: capture_ready=1' in text:
                            break
                        if process.poll() is not None or time.monotonic() >= end:
                            raise RuntimeError('Capture UiAutomation did not attach: ' + text[-2000:])
                        time.sleep(.2)
                    command(['adb', 'shell', 'settings', 'put', 'secure', 'enabled_accessibility_services',
                             PACKAGE + '/org.localpilot.android.PilotService'])
                    command(['adb', 'shell', 'settings', 'put', 'secure', 'accessibility_enabled', '1'])
                    readback = {key: command(['adb', 'shell', 'settings', 'get', 'secure', key], 2).strip()[:2048]
                                for key in ('enabled_accessibility_services', 'accessibility_enabled')}
                    (OUT / 'settings-after-enable.json').write_text(json.dumps(readback, indent=2), encoding='utf-8')
                    process.wait(timeout=180)
                    if process.returncode:
                        raise RuntimeError('Android instrumentation process failed')
                finally:
                    if process.poll() is None:
                        process.kill()
                        process.wait(timeout=5)
        output = (OUT / 'instrumentation.txt').read_text(errors='replace')
        prefix = 'INSTRUMENTATION_RESULT: evidence_json='
        payload = next((line[len(prefix):] for line in output.splitlines() if line.startswith(prefix)), None)
        if payload is None:
            raise RuntimeError('No final capture instrumentation report: ' + output[-2000:])
        report = json.loads(payload)
        report['runner_device'] = observed
        (OUT / 'capture-evidence.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
        validate_report(report, observed['api'])
        print('CAPTURE_ANDROID_PASS ' + json.dumps(dict(source_sha=observed['source_sha'],
              api=observed['api'], checks=len(report['checks']), physical_device=False)), flush=True)
        return 0
    except (OSError, RuntimeError, ValueError, subprocess.TimeoutExpired, zipfile.BadZipFile) as error:
        message = str(error)[:4000]
        (OUT / 'runner-error.txt').write_text(message, encoding='utf-8')
        print('::error title=Focused Android capture acceptance::' + message.replace('%', '%25').replace('\n', '%0A').replace('\r', '%0D'), flush=True)
        if isolated:
            collect_failure()
        return 1
    finally:
        if isolated:
            # Restore only this CI emulator's settings, after recording evidence.
            for args in (['settings', 'put', 'secure', 'accessibility_enabled', '0'],
                         ['settings', 'delete', 'secure', 'enabled_accessibility_services']):
                try:
                    command(['adb', 'shell', *args], 5)
                except (OSError, RuntimeError, subprocess.TimeoutExpired):
                    pass


if __name__ == '__main__':
    sys.exit(main())
