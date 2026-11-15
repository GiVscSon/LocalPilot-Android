#!/usr/bin/env python3
"""Actual AVD UI: archive -> cancel export -> SAF write/readback -> resume NES.

Reads only fresh isolated-emulator experience. Raw archives stay off CI artifacts.
"""
import argparse
import hashlib
import io
import json
from pathlib import Path
import re
import time
import zipfile
import xml.etree.ElementTree as ET
import unified_in_app_smoke as ui
from harness_student_device_acceptance import private_file
from nes_autoplay_resume_acceptance import ACTIVE, CANDIDATE, GOALS, SAVE, checkpoint, wait_progress

ARCHIVES='files/localpilot_vault/exports/nes-experience-v1/sealed'
REPLAYS='files/localpilot_vault/episodes/onpolicy-v1'
LIMIT=6*1024*1024


def verify_archive(raw, expected):
    if not 0<len(raw)<=LIMIT:raise RuntimeError('Archive size invalid')
    with zipfile.ZipFile(io.BytesIO(raw)) as z:
        names=z.namelist()
        if len(names)!=len(set(names)) or set(names)!=set(expected)|{'MANIFEST.csv'}:
            raise RuntimeError('Archive entries differ from original experience')
        if sum(i.file_size for i in z.infolist())>LIMIT or z.testzip() is not None:
            raise RuntimeError('Archive expanded size or CRC invalid')
        manifest='LOCALPILOT_NES_EXPERIENCE_ARCHIVE_V1\nsha256,bytes,path\n'
        for n in names:
            if n=='MANIFEST.csv':continue
            b=z.read(n)
            if b!=expected[n]:raise RuntimeError('Archived original bytes differ')
            manifest+=f'{hashlib.sha256(b).hexdigest()},{len(b)},{n}\n'
        if z.read('MANIFEST.csv').decode()!=manifest:raise RuntimeError('Archive checksum manifest differs')
    return hashlib.sha256(raw).hexdigest()


def listing(path):return ui.adb('shell','run-as',ui.PACKAGE,'ls',path).split()


def wait_until(test, message, seconds=60):
    deadline=time.monotonic()+seconds
    while time.monotonic()<deadline:
        if test():return
        time.sleep(.3)
    raise RuntimeError(message)


def cancel_documents_picker():
    # Back first closes the IME, then traverses the document hierarchy. Only
    # verified DocumentsUI windows may receive these bounded navigation keys.
    for _ in range(4):
        packages={n.get('package','') for n in ET.fromstring(ui.dump_ui()).iter('node')}
        documents=bool(packages&{'com.android.documentsui','com.google.android.documentsui'})
        if not documents and ui.PACKAGE in packages:
            ui.wait_activity(ui.DASH);return
        if not documents:
            raise RuntimeError('Unexpected foreground window while cancelling document export')
        ui.adb('shell','input','keyevent','KEYCODE_BACK');time.sleep(.4)
    ui.wait_activity(ui.DASH)


def main(apk, output):
    if not ui.is_emulator():raise RuntimeError('Isolated AVD required')
    with zipfile.ZipFile(apk) as z:source=json.loads(z.read('assets/localpilot-source.json'))['sourceSha']
    installed=ui.adb('shell','pm','path',ui.PACKAGE).strip().removeprefix('package:')
    if '\n' in installed or not installed.startswith('/data/app/') or ui.adb('shell','sha256sum',installed).split()[0]!=hashlib.sha256(apk.read_bytes()).hexdigest():
        raise RuntimeError('Installed APK differs from the pinned archive test input')
    saved=checkpoint()
    protected={p:private_file(p,7*1024*1024+4096) for p in (ACTIVE,CANDIDATE,SAVE)}
    goal=private_file(GOALS,131072)
    expected={Path(GOALS).name:goal}
    replay_names=listing(REPLAYS)
    for n in replay_names:
        if not re.fullmatch(r'[0-9a-f]{64}\.lpr',n):raise RuntimeError('Unexpected replay input')
        expected['replay/'+n]=private_file(REPLAYS+'/'+n,65536)
    ui.adb('shell','am','force-stop',ui.PACKAGE)
    ui.adb('shell','am','start','-W','-n',ui.PACKAGE+'/'+ui.MAIN)
    ui.wait_activity(ui.MAIN);ui.tap_harness_and_verify();ui.tap_text('Эпизоды',swipes=0)
    ui.tap_text('Архивировать опыт · освободить рабочий журнал')
    wait_until(lambda:bool(listing(ARCHIVES)) and not listing(REPLAYS), 'Archive/rotation did not complete')
    names=listing(ARCHIVES)
    if len(names)!=1 or not re.fullmatch(r'experience-[0-9a-f]{32}\.zip',names[0]):raise RuntimeError('Expected one bounded archive')
    name=names[0];raw=private_file(ARCHIVES+'/'+name,LIMIT);digest=verify_archive(raw,expected)
    if ui.adb('shell','run-as',ui.PACKAGE,'test','!','-e',GOALS,timeout=10).strip():
        raise RuntimeError('Unexpected output from active journal presence probe')
    # Private goal file is atomically retired, while unrelated files must be exact.
    for p,b in protected.items():
        if private_file(p,7*1024*1024+4096)!=b:raise RuntimeError('Archive changed game or model')
    ui.tap_text('Экспортировать архив в файл');time.sleep(1);cancel_documents_picker()
    ui.wait_activity(ui.DASH)
    if private_file(ARCHIVES+'/'+name,LIMIT)!=raw:raise RuntimeError('Cancel discarded local archive')
    ui.tap_text('Экспортировать архив в файл');time.sleep(1);ui.tap_text('Save',swipes=0)
    ui.wait_activity(ui.DASH,seconds=30)
    wait_until(lambda:not listing(ARCHIVES),'Verified SAF export did not retire local archive')
    external='/sdcard/Download/LocalPilot-'+name
    actual=ui.adb('shell','sha256sum',external).split()[0]
    if actual!=digest:raise RuntimeError('SAF file bytes differ from local sealed archive')
    ui.tap_text('Обзор',swipes=0);ui.tap_text('Продолжить сохранённую автоигру');ui.wait_activity(ui.NES)
    resumed=wait_progress(saved['actions']+16);ui.tap_text('STOP',swipes=0)
    if not private_file(GOALS,131072):raise RuntimeError('No new verified experience after rotation')
    for p in (ACTIVE,CANDIDATE):
        if private_file(p)!=protected[p]:raise RuntimeError('Archive/resume changed policy weights')
    result=dict(result='PASS',schema='localpilot-nes-archive-acceptance-v1',sourceSha=source,
        api=int(ui.adb('shell','getprop','ro.build.version.sdk')),archiveSha256=digest,
        archivedGoalRows=len(goal.decode().splitlines()),archivedReplayBatches=len(replay_names),
        originalBytesPreserved=True,cancelPreservesArchive=True,safExportReadbackVerified=True,
        activeJournalCapacityRecovered=True,resumedActions=resumed['actions'],savedActions=saved['actions'],
        gameAndWeightsPreserved=True,physicalDevice=False,releaseReady=False)
    output.parent.mkdir(parents=True,exist_ok=True);output.write_text(json.dumps(result,indent=2)+'\n')
    print('ANDROID_NES_EXPERIENCE_ARCHIVE_PASS '+json.dumps(result))


if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--apk',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args()
    try:main(a.apk,a.output)
    except Exception as error:
        if ui.is_emulator():ui.write_failure_diagnostics(a.output,error)
        a.output.parent.mkdir(parents=True,exist_ok=True);a.output.write_text(json.dumps(dict(result='FAIL',error=str(error)[:1000],releaseReady=False))+'\n');raise
