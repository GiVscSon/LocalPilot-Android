#!/usr/bin/env python3
"""Actual one-APK Android lifecycle/save/resume check on an isolated AVD only."""
import argparse
import hashlib
import io
import json
from pathlib import Path
import struct
import subprocess
import time
import unified_in_app_smoke as ui
from harness_student_device_acceptance import private_file, EvidencePending

SAVE='files/localpilot_vault/console/nes/latest.nss'
GOALS='files/console-ai-homebrew-goal-v1.log'
ACTIVE='files/localpilot_vault/models/active/student-homebrew-v1.weights.bin'
CANDIDATE='files/localpilot_vault/models/candidates/student-homebrew-v1.weights.bin'


def decode(raw):
    if not 160 <= len(raw) <= 7*1024*1024+4096 or raw[:4]!=b'NSV1':
        raise RuntimeError('Save schema/size invalid')
    if hashlib.sha256(raw[:-32]).digest()!=raw[-32:]:
        raise RuntimeError('Save SHA256 invalid')
    f=io.BytesIO(raw[:-32]); f.read(4)
    def integer():return struct.unpack('>i',f.read(4))[0]
    def utf():return f.read(struct.unpack('>H',f.read(2))[0]).decode('ascii')
    core=utf();frame=integer();pixel=integer();dendy=f.read(1)
    actions=struct.unpack('>q',f.read(8))[0];policy=utf();rom_hash=f.read(32)
    rom_size=integer()
    if not 16<=rom_size<=3*1024*1024:raise RuntimeError('Bad saved ROM size')
    rom=f.read(rom_size);n=integer()
    if not 1<=n<=4*1024*1024:raise RuntimeError('Bad CPU/PPU state size')
    state=json.loads(f.read(n))
    if f.read() or hashlib.sha256(rom).digest()!=rom_hash or core!='84946f0dae4bcf3b2dfebe84ad3bfeb1dbcb413bd9b1a69e1f45910a95fd7b41':
        raise RuntimeError('Saved ROM/core identity mismatch')
    if state['schema']!='localpilot-jsnes-save-v1' or state['frame']!=frame or state['hash']!=pixel or frame<15 or pixel==0 or actions<0:
        raise RuntimeError('Saved runtime frame identity mismatch')
    return dict(frame=frame,hash=pixel,actions=actions,policy=policy,romSha256=rom_hash.hex(),saveSha256=hashlib.sha256(raw).hexdigest())


def checkpoint():return decode(private_file(SAVE,7*1024*1024+4096))


def wait_progress(minimum,seconds=120):
    deadline=time.monotonic()+seconds
    while time.monotonic()<deadline:
        try:
            saved=checkpoint()
            if saved['actions']>=minimum:return saved
        except EvidencePending:pass
        time.sleep(.3)
    raise RuntimeError('Autoplay checkpoint did not advance to '+str(minimum))


def main(apk,output):
    if not ui.is_emulator():raise RuntimeError('Isolated AVD required')
    source=json.loads(__import__('zipfile').ZipFile(apk).read('assets/localpilot-source.json'))['sourceSha']
    installed=ui.adb('shell','pm','path',ui.PACKAGE).strip().removeprefix('package:')
    if '\n' in installed or not installed.startswith('/data/app/') or ui.adb('shell','sha256sum',installed).split()[0]!=hashlib.sha256(apk.read_bytes()).hexdigest():
        raise RuntimeError('Autoplay test APK differs from installed single package')
    active_before=hashlib.sha256(private_file(ACTIVE)).hexdigest()
    candidate_before=hashlib.sha256(private_file(CANDIDATE)).hexdigest()
    # Start a fresh foreground session through the real native console.
    ui.adb('shell','am','force-stop',ui.PACKAGE)
    ui.adb('shell','am','start','-W','-n',ui.PACKAGE+'/'+ui.MAIN)
    ui.wait_activity(ui.MAIN);ui.tap_harness_and_verify()
    ui.tap_text('Автоигра Student · 256 ходов с сохранением')
    ui.wait_activity(ui.NES)
    first=wait_progress(32)
    ui.tap_text('STOP',swipes=0)
    time.sleep(.3)
    stable_goals=private_file(GOALS,131072);stable_replays=ui.adb('shell','run-as',ui.PACKAGE,'ls','files/localpilot_vault/episodes/onpolicy-v1')
    time.sleep(1)
    if private_file(GOALS,131072)!=stable_goals or ui.adb('shell','run-as',ui.PACKAGE,'ls','files/localpilot_vault/episodes/onpolicy-v1')!=stable_replays:
        raise RuntimeError('New completed actions/replays appeared after STOP')
    saved=checkpoint();old_process=ui.one_process()
    ui.adb('shell','am','force-stop',ui.PACKAGE)
    dead=subprocess.run(['adb','shell','pidof',ui.PACKAGE],capture_output=True,timeout=10)
    if dead.stdout.strip():raise RuntimeError('Old process survived force-stop')
    # Only an explicit foreground Resume button can reacquire new input authority.
    ui.adb('shell','am','start','-W','-n',ui.PACKAGE+'/'+ui.MAIN)
    ui.wait_activity(ui.MAIN);ui.tap_harness_and_verify()
    ui.tap_text('Продолжить сохранённую автоигру');ui.wait_activity(ui.NES)
    resumed=wait_progress(saved['actions']+16)
    ui.tap_text('STOP',swipes=0);time.sleep(.3)
    journal=private_file(GOALS,131072).decode().splitlines()
    old=stable_goals.decode().splitlines()
    if journal[:len(old)]!=old or len(journal)<=len(old):raise RuntimeError('Durable history was reset or no new verified action')
    first_restored=journal[len(old)].split('|')
    if int(first_restored[6])!=saved['frame']:raise RuntimeError('Resume reset ROM instead of continuing saved CPU/PPU frame')
    if resumed['frame']<=saved['frame'] or resumed['policy']!=active_before:
        raise RuntimeError('Resume did not continue with the pinned saved policy')
    if hashlib.sha256(private_file(ACTIVE)).hexdigest()!=active_before or hashlib.sha256(private_file(CANDIDATE)).hexdigest()!=candidate_before:
        raise RuntimeError('Autoplay silently promoted or overwrote an unexamined candidate')
    report=dict(schema='localpilot-android-autoplay-save-resume-v1',result='PASS',sourceSha=source,
                api=int(ui.adb('shell','getprop','ro.build.version.sdk').strip()),
                processRestarted=ui.one_process()!=old_process,saved=saved,resumed=resumed,
                apkSha256=hashlib.sha256(apk.read_bytes()).hexdigest(),
                stopBlocksFurtherCompletedActions=True,candidatePreserved=True,activePolicyPreserved=True,
                singlePackage=True,physicalDevice=False,releaseReady=False)
    if not report['processRestarted']:raise RuntimeError('Lifecycle test did not restart Android process')
    output.parent.mkdir(parents=True,exist_ok=True);output.write_text(json.dumps(report,indent=2)+'\n')
    print('ANDROID_AUTOPLAY_SAVE_RESUME_PASS '+json.dumps(report))


if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--apk',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args()
    try:main(a.apk,a.output)
    except Exception as error:
        report=dict(result='FAIL',error=str(error)[:1000],releaseReady=False)
        if ui.is_emulator():
            ui.write_failure_diagnostics(a.output,error)
            try:
                report['lastCheckpoint']=checkpoint()
                import xml.etree.ElementTree as ET
                report['emulatorUiText']=[n.get('text','')[:400] for n in ET.fromstring(ui.dump_ui()).iter('node') if n.get('text')][:35]
            except Exception as diagnostic:report['checkpointDiagnostic']=str(diagnostic)[:300]
        a.output.parent.mkdir(parents=True,exist_ok=True)
        a.output.write_text(json.dumps(report,indent=2)+'\n')
        raise
