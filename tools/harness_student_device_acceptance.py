#!/usr/bin/env python3
"""Exercise production WebView -> Harness -> PPO in the one debug APK on an isolated AVD.

Reads only the app's newly generated homebrew training files. No imported ROM,
external game, physical-device success, policy improvement or release is claimed.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET

import unified_in_app_smoke as ui
from verify_harness_apk import inspect_apk

VAULT = 'files/localpilot_vault/models/'
SHA = re.compile(r'^[0-9a-f]{64}$')


def private_file(path, limit=100000):
    result = subprocess.run(['adb', 'shell', '-T', 'run-as', ui.PACKAGE, 'cat', path],
                            capture_output=True, timeout=15)
    if result.returncode:
        raise RuntimeError('Missing generated homebrew evidence: ' + path)
    # shell v2 carries remote exit status; exec-out alone can report transport
    # success while returning cat/run-as errors as if they were file bytes.
    if not 0 < len(result.stdout) <= limit:
        raise RuntimeError('Generated homebrew evidence exceeds its bound')
    return result.stdout


def verified_manifest(manifest, active, candidate):
    before, after = (hashlib.sha256(raw).hexdigest() for raw in (active, candidate))
    if (manifest.get('schema') != 'localpilot-student-candidate-v1' or
        manifest.get('task') != 'builtin-homebrew-right-progress-v1' or
        manifest.get('observationSchema') != 'obs64-homebrew-x-v1' or
        manifest.get('policyBeforeSha256') != before or
        manifest.get('policyAfterSha256') != after or before == after or
        manifest.get('verifiedRolloutSteps') != 16 or
        type(manifest.get('changedParameters')) is not int or manifest['changedParameters'] <= 0 or
        type(manifest.get('adamSteps')) is not int or manifest['adamSteps'] <= 0 or
        not SHA.fullmatch(str(manifest.get('romSha256'))) or
        manifest.get('evaluation') != 'NOT_RUN' or
        manifest.get('promotion') != 'BLOCKED_UNTIL_INDEPENDENT_EVALUATION'):
        raise RuntimeError('Unverified Android PPO candidate or unexpected promotion')
    return before, after


def verify_journal(raw, manifest):
    lines = raw.decode('utf-8').splitlines()
    if len(lines) != 16:
        raise RuntimeError('Expected exactly 16 freshly generated RAM evidence rows')
    previous, last_frame, epoch, total = '0', None, None, 0.0
    for line in lines:
        f = line.split('|')
        if (len(f) != 15 or f[0] != 'LOCALPILOT_HOMEBREW_GOAL_V1' or
            f[2] != manifest['romSha256'] or f[3] != 'student-homebrew-v1' or
            f[4] != manifest['policyBeforeSha256'] or
            f[5] not in ('LEFT', 'RIGHT', 'UP', 'DOWN', 'A', 'B') or
            f[11] != manifest['task'] or f[12] != 'PPO_BLOCKED' or f[13] != previous or
            hashlib.sha256(line.rsplit('|', 1)[0].encode()).hexdigest() != f[14]):
            raise RuntimeError('Android RAM evidence identity or checksum mismatch')
        start, end, x0, x1 = map(int, f[6:10])
        if (end-start != 16 or start < 15 or last_frame is not None and start != last_frame or
            epoch is not None and f[1] != epoch or not all(0 <= x <= 255 for x in (x0,x1))):
            raise RuntimeError('Noncontiguous or stale Android rollout')
        delta = x1-x0
        if (abs(delta) > 16 or f[5] == 'LEFT' and delta > 0 or
            f[5] == 'RIGHT' and delta < 0 or f[5] not in ('LEFT','RIGHT') and delta != 0):
            raise RuntimeError('Android CPU RAM contradicts selected button')
        import struct
        reward = struct.unpack('>f', bytes.fromhex(f[10]))[0]
        if reward != delta/16:
            raise RuntimeError('Reward differs from actual ROM RAM')
        total += reward
        previous, last_frame, epoch = f[14], end, f[1]
    return dict(steps=len(lines), coreFrames=256, taskReturn=total,
                rewardSource='VERIFIED_ROM_RAM', provenance='RETRO_EMULATED')


def tap_scroll_control(label):
    # Swipe inside the bounded controls panel, never across the NES viewport.
    for _ in range(12):
        xml=ui.dump_ui()
        enabled=ET.fromstring(xml)
        for n in enabled.iter('node'):
            if n.get('enabled')=='false':n.set('text','');n.set('content-desc','')
        target=ui.target_bounds(ET.tostring(enabled,encoding='unicode'),label)
        if target:
            ui.adb('shell','input','tap',str(target[0]),str(target[1])); return
        root=ET.fromstring(xml)
        panel=next((n for n in root.iter('node') if n.get('class')=='android.widget.ScrollView'),None)
        if panel is None: raise RuntimeError('NES control panel not visible')
        match=ui.BOUNDS.fullmatch(panel.get('bounds',''))
        if not match: raise RuntimeError('Unverified NES control panel bounds')
        left,top,right,bottom=map(int,match.groups());x=(left+right)//2
        ui.adb('shell','input','swipe',str(x),str(bottom-12),str(x),str(top+12),'220')
        time.sleep(.2)
    raise RuntimeError('NES Student control unavailable: '+label)


def run(apk, output):
    if not ui.is_emulator(): raise RuntimeError('Isolated AVD required')
    api=int(ui.adb('shell','getprop','ro.build.version.sdk').strip())
    if os.environ.get('LOCALPILOT_EXPECTED_API',str(api))!=str(api):
        raise RuntimeError('Observed Android API differs from requested matrix')
    ui.wait_activity(ui.MAIN)
    installed=ui.adb('shell','pm','path',ui.PACKAGE).strip().removeprefix('package:')
    if '\n' in installed or not installed.startswith('/data/app/'):
        raise RuntimeError('Unexpected installed single APK path')
    apk_sha=hashlib.sha256(apk.read_bytes()).hexdigest()
    if ui.adb('shell','sha256sum',installed).split()[0] != apk_sha:
        raise RuntimeError('Installed APK differs from tested source artifact')
    source_sha=subprocess.run(['git','rev-parse','HEAD'],check=True,capture_output=True,text=True).stdout.strip()
    inspect_apk(apk,expected_source_sha=source_sha)
    process=ui.one_process()
    ui.tap_harness_and_verify(); ui.tap_text('Открыть ModelLab');ui.wait_activity(ui.MODEL)
    ui.tap_text('Вернуться к консольным эмуляторам');ui.wait_activity(ui.GAMELAB)
    ui.tap_text('Играть в NES / Денди');ui.wait_activity(ui.NES)
    tap_scroll_control('Запустить тестовую NES-игру')
    time.sleep(1)
    tap_scroll_control('StudentPolicy · 16 реальных NES-шагов')
    manifest_path=VAULT+'candidates/student-homebrew-v1.manifest.json'
    deadline=time.monotonic()+45
    while True:
        try:
            manifest=json.loads(private_file(manifest_path,4096));break
        except RuntimeError:
            if time.monotonic()>=deadline:raise RuntimeError('Production Android NES/PPO batch did not finish')
            time.sleep(.5)
    active=private_file(VAULT+'active/student-homebrew-v1.weights.bin')
    candidate=private_file(VAULT+'candidates/student-homebrew-v1.weights.bin')
    before,after=verified_manifest(manifest,active,candidate)
    rollout=verify_journal(private_file('files/console-ai-homebrew-goal-v1.log'),manifest)
    ui.tap_text('STOP',swipes=0)
    if ui.one_process()!=process:raise RuntimeError('NES Student changed Android process')
    if hashlib.sha256(private_file(VAULT+'active/student-homebrew-v1.weights.bin')).hexdigest()!=before:
        raise RuntimeError('Candidate unexpectedly replaced active policy')
    report=dict(schema='localpilot-android-nes-student-v1',result='PASS',
        api=api,deviceProfile=os.environ.get('LOCALPILOT_EXPECTED_PROFILE','UNSPECIFIED'),
        screenSize=ui.screen_size(),apkSha256=apk_sha,sourceSha=source_sha,
        policyBeforeSha256=before,policyAfterSha256=after,romSha256=manifest['romSha256'],
        rollout=rollout,changedParameters=manifest['changedParameters'],
        runtime='PRODUCTION_ANDROID_WEBVIEW_HARNESS_PPO',singlePackage=True,
        evaluation='NOT_RUN',promotion=manifest['promotion'],
        physicalDevice=False,releaseReady=False)
    output.parent.mkdir(parents=True,exist_ok=True)
    output.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
    print('ANDROID_NES_STUDENT_PPO_PASS '+json.dumps(report))


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apk',type=Path,required=True)
    parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args()
    try:run(args.apk,args.output)
    except Exception as error:
        report=dict(result='FAIL',error=str(error)[:1000],releaseReady=False)
        try:
            if ui.is_emulator():
                root=ET.fromstring(ui.dump_ui())
                report['isolated_fixture_ui']=[n.get('text','')[:500] for n in root.iter('node')
                    if n.get('text')][:35]
                report['crash_log']=ui.adb('logcat','-d','-b','crash')[-6000:]
        except Exception as diagnostic:
            report['diagnostic_error']=type(diagnostic).__name__
        args.output.parent.mkdir(parents=True,exist_ok=True)
        args.output.write_text(json.dumps(report,ensure_ascii=False,indent=2))
        raise
