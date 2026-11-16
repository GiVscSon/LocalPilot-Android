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


class EvidencePending(RuntimeError):
    pass


def private_file(path, limit=100000):
    # Android AVD can temporarily drop shell v2 during a large run-as read.
    # A nonzero exit is NEVER evidence, even when stdout begins with NSV1.
    # Bound the retry to 3 read-only probes; do not retry gameplay/actions.
    for attempt in range(3):
        try:
            result = subprocess.run(
                ['adb', 'shell', '-T', 'run-as', ui.PACKAGE, 'cat', path],
                capture_output=True, timeout=15)
        except subprocess.TimeoutExpired:
            if attempt < 2:
                time.sleep(1)
                continue
            raise EvidencePending('ADB_READ_TIMEOUT: device evidence not verified')
        if result.returncode == 0:
            # Do not accept an empty, truncated or oversized binary as saved proof.
            if not 0 < len(result.stdout) <= limit:
                raise RuntimeError('Generated homebrew evidence size outside bound')
            return result.stdout
        # Some ADB versions send remote cat/run-as errors to stdout with no
        # stderr. Inspect only small printable text, never dump a binary ROM.
        stderr = result.stderr.decode('utf-8', errors='replace')[-500:]
        printable = (len(result.stdout) <= 500 and
                     all(ch in b'\\t\\r\\n' or 32 <= ch <= 126
                         for ch in result.stdout))
        stdout_error = (result.stdout.decode('ascii', errors='replace')
                        if not stderr and printable else '')
        diagnostic = stderr or stdout_error
        if ('No such file or directory' in diagnostic and
            ('cat:' in diagnostic or path in diagnostic)):
            raise EvidencePending('Generated homebrew evidence not written yet: ' + path)
        interrupted = (any(marker in diagnostic.lower() for marker in (
            'device offline', 'device not found', 'no devices/emulators',
            'device disconnected', 'transport', 'closed', 'device unauthorized'))
            or result.stdout.startswith(b'NSV1'))  # partial binary + failed ADB
        if interrupted:
            if attempt < 2:
                time.sleep(1)
                continue
            raise EvidencePending('ADB_READ_TRANSPORT_PENDING: no verified bytes; '
                                  'stdout_bytes=' + str(len(result.stdout)))
        # Show stderr and size only; raw save blobs may contain private ROM/state.
        raise RuntimeError('Unverified run-as evidence: adb_exit=' +
                           str(result.returncode) + ' stderr=' + diagnostic[:250] +
                           ' stdout_bytes=' + str(len(result.stdout)))
    raise EvidencePending('ADB_READ_NOT_VERIFIED')


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
    ui.adb('shell','run-as',ui.PACKAGE,'true') # Fail immediately if this is not the test derivative.
    source_sha=subprocess.run(['git','rev-parse','HEAD'],check=True,capture_output=True,text=True).stdout.strip()
    inspect_apk(apk,expected_source_sha=source_sha)
    process=ui.one_process()
    # Navigate through the actual ONE-package Harness control, not an unexported
    # activity launched directly by adb. The button must start its own ROM.
    ui.tap_harness_and_verify()
    ui.tap_text('Играть и учиться: 16 NES-ходов Student')
    ui.wait_activity(ui.NES)
    time.sleep(1)
    manifest_path=VAULT+'candidates/student-homebrew-v1.manifest.json'
    deadline=time.monotonic()+45
    while True:
        try:
            manifest=json.loads(private_file(manifest_path,4096));break
        except EvidencePending:
            if time.monotonic()>=deadline:raise RuntimeError('Production Android NES/PPO batch did not finish')
            time.sleep(.5)
    active=private_file(VAULT+'active/student-homebrew-v1.weights.bin')
    candidate=private_file(VAULT+'candidates/student-homebrew-v1.weights.bin')
    before,after=verified_manifest(manifest,active,candidate)
    rollout=verify_journal(private_file('files/console-ai-homebrew-goal-v1.log'),manifest)
    # The on-policy samples must be durable after the JNI/JSNES episode and
    # contain complete obs64 and old-log probabilities, not only screen hashes.
    replay_dir='files/localpilot_vault/episodes/onpolicy-v1'
    listing=ui.adb('shell','run-as',ui.PACKAGE,'ls',replay_dir).splitlines()
    entries=[name.strip() for name in listing if name.strip()]
    if len(entries)!=1 or not re.fullmatch(r'[0-9a-f]{64}\.lpr',entries[0]):
        raise RuntimeError('Expected one immutable on-policy NES replay, got '+str(entries[:5]))
    replay=private_file(replay_dir+'/'+entries[0],65536)
    filename_hash=hashlib.sha256(replay).hexdigest()
    if filename_hash+'.lpr'!=entries[0] or len(replay)<100 or len(replay)>65536 or replay[:4]!=b'LPR1':
        raise RuntimeError('PPO replay binary magic/sha/bounds mismatch')
    if int.from_bytes(replay[4:8],'big')!=16 or hashlib.sha256(replay[:-32]).digest()!=replay[-32:]:
        raise RuntimeError('PPO replay samples or payload hash mismatch')
    ui.tap_text('STOP',swipes=0)
    if ui.one_process()!=process:raise RuntimeError('NES Student changed Android process')
    if hashlib.sha256(private_file(VAULT+'active/student-homebrew-v1.weights.bin')).hexdigest()!=before:
        raise RuntimeError('Candidate unexpectedly replaced active policy')
    report=dict(schema='localpilot-android-nes-student-v1',result='PASS',
        api=api,deviceProfile=os.environ.get('LOCALPILOT_EXPECTED_PROFILE','UNSPECIFIED'),
        screenSize=ui.screen_size(),apkSha256=apk_sha,sourceSha=source_sha,
        policyBeforeSha256=before,policyAfterSha256=after,romSha256=manifest['romSha256'],
        rollout=rollout,replaySha256=filename_hash,replayBytes=len(replay),
        replaySamples=16,changedParameters=manifest['changedParameters'],
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
