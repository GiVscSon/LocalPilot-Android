#!/usr/bin/env python3
"""Real VOID LANCER ROM + pinned vision GGUF/mmproj, through visible NES UI.

No educational scene, synthetic model answer, substituted policy, or guessed
reward. Model-to-input/frame evidence is distinct from game score or victory.
"""
import argparse
import hashlib
import json
from pathlib import Path
import time
import xml.etree.ElementTree as ET
import zipfile
import urllib.request
import bundle_optional_models as models
import unified_in_app_smoke as ui
from run_g2_direct_gguf_acceptance import import_picker_file, picker_select_fixture, wait_library_result
from run_harness_runtime_acceptance import command, PACKAGE
from harness_student_device_acceptance import private_file, EvidencePending

REPO='LiquidAI/LFM2.5-VL-450M-GGUF'
MODEL='LFM2.5-VL-450M-Q4_0.gguf'
PROJECTOR='mmproj-LFM2.5-VL-450m-Q8_0.gguf'
ROM='VOID-LANCER-v1.0.0.nes'
ROM_SHA='5eb801aca68131a3d17fa82d8901d46f6098b878d1e00cd1c47d00c001c2405a'
ROM_URL='https://github.com/Riyoway/void-lancer-nes/releases/download/v1.0.0/'+ROM

def prepare(directory):
    directory.mkdir(parents=True,exist_ok=True)
    revision=models.PINNED_REVISIONS[REPO]
    for name in (MODEL,PROJECTOR):
        size,digest=models.PINNED_FILES[name];path=directory/name
        if path.is_file() and path.stat().st_size==size and hashlib.sha256(path.read_bytes()).hexdigest()==digest:continue
        with models.get(models.BASE+'/'+REPO+'/resolve/'+revision+'/'+name) as source,path.open('wb') as out:
            actual,amount=models.sha_stream(source,out,limit=size)
        if (actual,amount)!=(digest,size):raise RuntimeError('Pinned vision model download mismatch')
    path=directory/ROM
    if not path.is_file() or hashlib.sha256(path.read_bytes()).hexdigest()!=ROM_SHA:
        with urllib.request.urlopen(ROM_URL,timeout=60) as source:raw=source.read(40977)
        if len(raw)!=40976 or hashlib.sha256(raw).hexdigest()!=ROM_SHA:raise RuntimeError('Real ROM SHA/size differs')
        path.write_bytes(raw)

def verified_generic_rows(raw, model_sha):
    result=[]
    for line in raw.decode().splitlines():
        body,checksum=line.rsplit('|',1)
        if hashlib.sha256(body.encode()).hexdigest()!=checksum:raise ValueError('Generic journal checksum')
        parts=body.split('|',7)
        if len(parts)!=8 or parts[0]!='NES_USER_ROM_V1' or parts[1]!=ROM_SHA or parts[4]!=model_sha or parts[5:7]!=['RETRO_EMULATED','NULL']:
            raise ValueError('Wrong ROM/model/provenance or fabricated reward')
        trial,inner=parts[7].rsplit('|',1)
        if hashlib.sha256(trial.encode()).hexdigest()!=inner:raise ValueError('Trial checksum')
        p=trial.split('|')
        if len(p)!=9 or p[2]!='USER_ROM' or int(p[5])-int(p[4])!=16:raise ValueError('Wrong real-game step')
        result.append(dict(button=p[3],beforeFrame=int(p[4]),afterFrame=int(p[5]),beforeHash=p[6],afterHash=p[7],status=p[8]))
    return result

def correlate_inference(raw, rows, model_sha):
    calls=[]
    for line in raw.decode().splitlines():
        body,checksum=line.rsplit('|',1)
        if hashlib.sha256(body.encode()).hexdigest()!=checksum:raise ValueError('Inference checksum')
        p=body.split('|')
        if len(p)!=8 or p[0]!='NES_MODEL_INFERENCE_V1':raise ValueError('Inference schema')
        if p[2]==model_sha:calls.append((int(p[3]),p[4],p[6]))
    for row in rows:
        if calls.count((row['beforeFrame'],row['beforeHash'],row['button']))!=1:
            raise ValueError('Game action lacks an exact model/frame/hash match')
    return len(calls)

def verify(apk,directory,output):
    if not ui.is_emulator():raise RuntimeError('Isolated Android emulator required')
    output.parent.mkdir(parents=True,exist_ok=True)
    for name in (MODEL,PROJECTOR):
        size,digest=models.PINNED_FILES[name]
        path=directory/name
        if path.stat().st_size!=size or hashlib.sha256(path.read_bytes()).hexdigest()!=digest:raise RuntimeError('Local model identity mismatch')
    if hashlib.sha256((directory/ROM).read_bytes()).hexdigest()!=ROM_SHA:raise RuntimeError('Local real ROM identity mismatch')
    with zipfile.ZipFile(apk) as z:source=json.loads(z.read('assets/localpilot-source.json'))['sourceSha']
    command('adb','install',str(apk),timeout=120)
    ui.adb('shell','am','start','-W','-n',PACKAGE+'/'+ui.MAIN);ui.wait_activity(ui.MAIN)
    ui.tap_text('Рабочее окно · игра и агент');ui.wait_activity(ui.NES)
    import_picker_file(directory/MODEL,models.PINNED_FILES[MODEL][1],'LOCALPILOT_CI_ONLY_VISION.gguf',output)
    import_picker_file(directory/PROJECTOR,models.PINNED_FILES[PROJECTOR][1],'LOCALPILOT_CI_ONLY_MMPROJ.gguf',output,True)
    ui.tap_text('Управление моделями NES',swipes=0);ui.tap_text('Привязать выбранный GGUF к NES',swipes=0)
    command('adb','push',str(directory/ROM),'/sdcard/Download/LOCALPILOT_CI_ONLY_VOIDLANCER.nes',timeout=60)
    ui.tap_text('Игра',swipes=0);ui.tap_text('Открыть .nes',swipes=0)
    picker_select_fixture('LOCALPILOT_CI_ONLY_VOIDLANCER.nes');ui.wait_activity(ui.NES)
    ui.tap_text('START',swipes=0)
    ui.tap_text('Запустить выбранного агента NES',swipes=0)
    deadline=time.monotonic()+4*120+45;rows=[]
    while time.monotonic()<deadline:
        if PACKAGE+'/'+ui.NES not in ui.resumed_activity():raise RuntimeError('Vision agent left actual game screen')
        try:
            raw=private_file('files/console-ai-user-rom-v1.log',262144)
            rows=verified_generic_rows(raw,models.PINNED_FILES[MODEL][1])
            (output.parent/'real-game-transitions.log').write_bytes(raw)
            inference=private_file('files/harness-model-execution-v1.log',262144)
            calls=correlate_inference(inference,rows,models.PINNED_FILES[MODEL][1])
            (output.parent/'real-game-inference.log').write_bytes(inference)
            if len(rows)>=4:break
        except EvidencePending:pass
        text='\n'.join(n.get('text','') for n in ET.fromstring(ui.dump_ui()).iter('node'))
        if 'STOP:' in text:raise RuntimeError('Real game agent stopped before four transitions: '+text[:800])
        time.sleep(.5)
    else:raise RuntimeError('Real game did not confirm four vision actions')
    ui.tap_text('STOP',swipes=0)
    before=private_file('files/console-ai-user-rom-v1.log',262144);time.sleep(1)
    if before!=private_file('files/console-ai-user-rom-v1.log',262144):raise RuntimeError('Action completed after real-game STOP')
    report=dict(result='REAL_ROM_VISION_INPUT_FRAME_PASS',sourceSha=source,
                apkSha256=hashlib.sha256(apk.read_bytes()).hexdigest(),romSha256=ROM_SHA,
                modelSha256=models.PINNED_FILES[MODEL][1],projectorSha256=models.PINNED_FILES[PROJECTOR][1],
                transitions=rows,inferenceCallsForModel=calls,exactModelActionAndFrameMatch=True,
                modelTransport='VISIBLE_SAF',romTransport='VISIBLE_SAF',
                currentRomPreserved=True,educationalScene=False,reward=None,
                gameVictory='NOT_VERIFIED',physicalDevice=False,releaseReady=False)
    output.write_text(json.dumps(report,indent=2)+'\n');print('REAL_ROM_VISION_INPUT_FRAME_PASS '+json.dumps(report))

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('mode',choices=['prepare','verify'])
    p.add_argument('--directory',type=Path,default=Path('build/real-nes-vision'))
    p.add_argument('--apk',type=Path);p.add_argument('--output',type=Path,default=Path('real-nes-evidence/report.json'))
    a=p.parse_args()
    if a.mode=='prepare':prepare(a.directory)
    else:
        try:verify(a.apk,a.directory,a.output)
        except Exception as error:
            a.output.parent.mkdir(parents=True,exist_ok=True)
            a.output.write_text(json.dumps(dict(result='FAIL',error=str(error)[:1800],educationalScene=False,releaseReady=False))+'\n')
            if ui.is_emulator():ui.write_failure_diagnostics(a.output,error)
            raise
