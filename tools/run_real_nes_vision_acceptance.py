#!/usr/bin/env python3
"""Real VOID LANCER + pinned GGUF, using verified ROM state and visible NES UI.

No educational scene, synthetic model answer, substituted policy, or guessed
reward. Model-to-input/frame evidence is distinct from game score or victory.
"""
import argparse
import hashlib
import json
import re
from pathlib import Path
import time
import xml.etree.ElementTree as ET
import zipfile
import urllib.request
import bundle_optional_models as models
import unified_in_app_smoke as ui
from run_g2_direct_gguf_acceptance import import_picker_file, picker_select_fixture, wait_library_result, click_exact
from run_harness_runtime_acceptance import command, PACKAGE
from harness_student_device_acceptance import private_file, EvidencePending

def bundled_profile(repository,filename):
    return repository,filename,models.PINNED_REVISIONS[repository],models.PINNED_FILES[filename]

MODEL_PROFILES={
    # Existing pinned ModelLab GGUF, verified on real Android NES in PR #195.
    # This is llama.cpp inference, not a native MNN engine or vision inference.
    'pocket':('taobao-mnn/MNN-Pocket-0.3B-GGUF','MNN-Pocket-0.3B-Instruct-Q4_1.gguf',
              '791b7e92fa790a5ca5bbd7604feb1ce1d2429d14',
              (191896672,'a281c127e388b16537fd3f87739e6dc514ac1093c80d9eac8d097')),
    'qwen35':bundled_profile('ggml-org/Qwen3.5-0.8B-GGUF','Qwen3.5-0.8B-Q4_0.gguf'),
    'lfm350':bundled_profile('LiquidAI/LFM2.5-350M-GGUF','LFM2.5-350M-Q4_0.gguf'),
    # Reviewed on-demand pin; does not add weights to the bundled APK budget.
    'lfm1200':('LiquidAI/LFM2.5-1.2B-Instruct-GGUF','LFM2.5-1.2B-Instruct-Q4_0.gguf',
               '8ed288026e23958ad9dfa92d53ed773a8eee7125',
               (695751488,'2ea801949d760cdf1a2cc04a54262c22c3c0c54f0769d57760c9adeb0e59233f')),
}

def select_model(profile):
    global MODEL_PROFILE,REPO,MODEL,REVISION,FILES
    REPO,MODEL,REVISION,spec=MODEL_PROFILES[profile]
    MODEL_PROFILE=profile
    FILES={MODEL:spec}

select_model('qwen35')
ROM='VOID-LANCER-v1.0.0.nes'
ROM_SHA='5eb801aca68131a3d17fa82d8901d46f6098b878d1e00cd1c47d00c001c2405a'
ROM_URL='https://github.com/Riyoway/void-lancer-nes/releases/download/v1.0.0/'+ROM

def prepare(directory):
    directory.mkdir(parents=True,exist_ok=True)
    revision=REVISION
    for name in (MODEL,):
        size,digest=FILES[name];path=directory/name
        if path.is_file() and path.stat().st_size==size and hashlib.sha256(path.read_bytes()).hexdigest()==digest:continue
        with models.get(models.BASE+'/'+REPO+'/resolve/'+revision+'/'+name) as source,path.open('wb') as out:
            actual,amount=models.sha_stream(source,out,limit=size)
        if (actual,amount)!=(digest,size):raise RuntimeError('Pinned game model download mismatch')
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
        parts=body.split('|',9)
        if len(parts)!=10 or parts[0]!='NES_USER_ROM_V2' or parts[1]!=ROM_SHA or parts[4]!=model_sha or parts[5:7]!=['RETRO_EMULATED','NULL']:
            raise ValueError('Wrong ROM/model/provenance or fabricated reward')
        before=parse_game_state(parts[7]);after=parse_game_state(parts[8])
        trial,inner=parts[9].rsplit('|',1)
        if hashlib.sha256(trial.encode()).hexdigest()!=inner:raise ValueError('Trial checksum')
        p=trial.split('|')
        if len(p)!=9 or p[2]!='USER_ROM' or not valid_game_button(p[3]) or int(p[5])-int(p[4])!=16:raise ValueError('Wrong real-game step')
        result.append(dict(button=p[3],beforeFrame=int(p[4]),afterFrame=int(p[5]),beforeHash=p[6],afterHash=p[7],status=p[8],
                           gameBefore=before,gameAfter=after,semanticControl=confirms_control(p[3],before,after)))
    return result

def parse_game_state(text):
    values=[int(x) for x in text.split(',')]
    keys=('state','x','y','lives','bombs','stage','score','kills','shots','bullets')
    maxima=(3,255,255,9,9,3,999999,255,255,8)
    if len(values) not in (10,14) or any(x<0 or x>limit for x,limit in zip(values[:10],maxima)):raise ValueError('Missing or impossible actual game state')
    result=dict(zip(keys,values[:10]))
    if len(values)==14:
        for name,(x,y) in zip(('nearestEnemy','nearestHostileBullet'),(values[10:12],values[12:14])):
            if not ((x,y)==(-1,-1) or 0<=x<=255 and 0<=y<=255):raise ValueError('Invalid real-game object coordinates')
            result[name]=None if x==-1 else dict(x=x,y=y)
    return result

def valid_game_button(button):
    if button in ('A','B','UP','DOWN','LEFT','RIGHT','START','SELECT','WAIT'):return True
    keys=button.split('+')
    if not 2<=len(keys)<=4:return False
    ranks={'UP':0,'DOWN':0,'LEFT':1,'RIGHT':1,'A':2,'B':3}
    order=[ranks.get(key,-1) for key in keys]
    return all(rank>=0 for rank in order) and all(a<b for a,b in zip(order,order[1:]))

def confirms_control(button,before,after):
    if not valid_game_button(button):return False
    if '+' in button:return any(confirms_control(key,before,after) for key in button.split('+'))
    if button=='START':return before['state']==0 and after['state']==1 or before['state'] in (2,3) and after['state']==0
    if before['state']!=1 or after['state']!=1:return False
    return {'LEFT':after['x']<before['x'],'RIGHT':after['x']>before['x'],
            'UP':after['y']<before['y'],'DOWN':after['y']>before['y'],
            'A':after['shots']!=before['shots'] or after['bullets']>before['bullets'],
            'B':after['bombs']<before['bombs']}.get(button,False)

def verified_game_progress(row):
    before,after=row['gameBefore'],row['gameAfter']
    if before['state']!=1:return False
    return after['state']==3 or after['state']==1 and (after['score']>before['score'] or after['kills']>before['kills'])

def correlate_inference(raw, rows, model_sha):
    calls=[]
    for line in raw.decode().splitlines():
        body,checksum=line.rsplit('|',1)
        if hashlib.sha256(body.encode()).hexdigest()!=checksum:raise ValueError('Inference checksum')
        p=body.split('|')
        if len(p)!=8 or p[0]!='NES_MODEL_INFERENCE_V1':raise ValueError('Inference schema')
        elapsed=int(p[5])
        if elapsed<0:raise ValueError('Invalid measured inference duration')
        if p[2]==model_sha:calls.append((int(p[3]),p[4],p[6],elapsed))
    for row in rows:
        matches=[c for c in calls if c[:3]==(row['beforeFrame'],row['beforeHash'],row['button'])]
        if len(matches)!=1:
            raise ValueError('Game action lacks an exact model/frame/hash match')
        row['inferenceElapsedMs']=matches[0][3]
    return len(calls)

def verified_tool_calls(raw,rows,model_sha):
    calls=re.findall(r'chosen=(nes_start|nes_press|nes_wait|agent_stop) button=([A-Z+]+) frames=(\d+) frame=(\d+) hash=(\d+) model=([0-9a-f]{64})',raw)
    result=[]
    for row in rows:
        matches=[c for c in calls if (int(c[3]),c[4],c[1],c[5])==(row['beforeFrame'],row['beforeHash'],row['button'],model_sha)]
        if len(matches)!=1:raise ValueError('Action lacks a unique actual model tool invocation')
        name,button,frames,frame,hash_,sha=matches[0]
        expected='nes_wait' if button=='WAIT' else 'nes_start' if row['gameBefore']['state']!=1 and button=='START' else 'nes_press'
        if name!=expected or int(frames)!=16 or row['gameBefore']['state']!=1 and name=='nes_press':
            raise ValueError('Tool precondition or frame arguments differ from actual transition')
        if row['gameBefore']['state']==1 and button in ('START','SELECT'):
            raise ValueError('Playing tool cannot use known inactive menu controls')
        result.append(dict(tool=name,button=button,frames=int(frames),beforeFrame=int(frame),beforeHash=hash_,modelSha256=sha))
    return result

def verified_controller_settings(raw,profile,minimum_calls):
    expected={
        'pocket':('REQUESTED',0.0,20,0.8,1.18,0.0),
        'qwen35':('QWEN35_TEXT',1.0,20,1.0,1.0,2.0),
        'lfm350':('LFM2_TEXT',0.1,50,1.0,1.05,0.0),
        'lfm1200':('LFM2_TEXT',0.1,50,1.0,1.05,0.0),
    }[profile]
    matches=re.findall(r'controller_sampler=(\w+) temperature=([0-9.]+) top_k=(\d+) top_p=([0-9.]+) repeat=([0-9.]+) presence=([0-9.]+) seed=(\d+)',raw)
    records=[]
    for name,temp,top_k,top_p,repeat,presence,seed in matches:
        actual=(name,float(temp),int(top_k),float(top_p),float(repeat),float(presence))
        if actual!=expected:raise ValueError('Actual native sampling differs from selected model preset')
        records.append(dict(profile=name,temperature=float(temp),topK=int(top_k),topP=float(top_p),repeat=float(repeat),presence=float(presence),seed=int(seed)))
    if minimum_calls<1 or len(records)<minimum_calls:
        raise ValueError('Missing actual native sampling calls')
    if profile=='pocket':
        # Native greedy decoding has no stochastic seed. Seed=0 is a truthful
        # receipt, not 3 independent RNG streams; model snapshots still differ.
        greedy=re.findall(r'controller_sampler=REQUESTED [^\n]*seed=0 greedy=1',raw)
        if len(greedy)<minimum_calls or any(x['seed']!=0 for x in records):
            raise ValueError('Missing actual Pocket greedy sampling receipts')
    elif len({x['seed'] for x in records})!=len(records):
        raise ValueError('Replayed stochastic sampling seed')
    return records

def verify(apk,directory,output,attempt=None):
    if attempt is None:attempt={}
    if not ui.is_emulator():raise RuntimeError('Isolated Android emulator required')
    output.parent.mkdir(parents=True,exist_ok=True)
    for name in (MODEL,):
        size,digest=FILES[name]
        path=directory/name
        if path.stat().st_size!=size or hashlib.sha256(path.read_bytes()).hexdigest()!=digest:raise RuntimeError('Local model identity mismatch')
    if hashlib.sha256((directory/ROM).read_bytes()).hexdigest()!=ROM_SHA:raise RuntimeError('Local real ROM identity mismatch')
    with zipfile.ZipFile(apk) as z:source=json.loads(z.read('assets/localpilot-source.json'))['sourceSha']
    api=int(ui.adb('shell','getprop','ro.build.version.sdk'))
    abi=ui.adb('shell','getprop','ro.product.cpu.abi').strip()
    if api!=35 or abi!='x86_64':raise RuntimeError('Actual real-game test device does not match API35 x86_64')
    attempt.update(modelProfile=MODEL_PROFILE,modelRepository=REPO,modelRevision=REVISION,sourceSha=source,apkSha256=hashlib.sha256(apk.read_bytes()).hexdigest(),
                   modelSha256=FILES[MODEL][1],romSha256=ROM_SHA,androidApi=api,abi=abi,
                   evidenceType='EMULATOR_ANDROID',physicalDevice=False)
    (output.parent/'attempt-identity.json').write_text(json.dumps(attempt,indent=2)+'\n')
    command('adb','install',str(apk),timeout=120)
    ui.adb('shell','am','start','-W','-n',PACKAGE+'/'+ui.MAIN);ui.wait_activity(ui.MAIN)
    main_ui='\n'.join(n.get('text','') for n in ET.fromstring(ui.dump_ui()).iter('node'))
    if 'новый рисунок' in main_ui or 'Награда не установлена; веса GGUF не обучались' in main_ui:
        raise RuntimeError('Primary screen exposes historical fixture measurements')
    ui.tap_text('Рабочее окно · игра и агент');ui.wait_activity(ui.NES)
    public_ui='\n'.join(n.get('text','') for n in ET.fromstring(ui.dump_ui()).iter('node'))
    if 'Учить Student' in public_ui or 'Внутренняя приёмка' in public_ui or 'Учебный ROM' in public_ui:
        raise RuntimeError('User workspace exposes educational controls or measurements')
    if ui.target_bounds(ui.dump_ui(),'Ручное') is None:raise RuntimeError('Manual control handover is missing')
    import_picker_file(directory/MODEL,FILES[MODEL][1],'LOCALPILOT_CI_ONLY_GAME_MODEL.gguf',output)
    ui.tap_text('Управление моделями NES',swipes=0);ui.tap_text('Привязать выбранный GGUF к NES',swipes=0)
    ui.tap_text('Управление моделями NES',swipes=0);ui.tap_text('Бюджет GGUF:',swipes=0)
    click_exact('16 действий',package=PACKAGE)
    command('adb','push',str(directory/ROM),'/sdcard/Download/LOCALPILOT_CI_ONLY_VOIDLANCER.nes',timeout=60)
    ui.tap_text('Игра',swipes=0);ui.tap_text('Открыть .nes',swipes=0)
    picker_select_fixture('LOCALPILOT_CI_ONLY_VOIDLANCER.nes');ui.wait_activity(ui.NES)
    ui.tap_text('Запустить выбранного агента NES',swipes=0)
    deadline=time.monotonic()+16*120+45;rows=[]
    while time.monotonic()<deadline:
        if PACKAGE+'/'+ui.NES not in ui.resumed_activity():raise RuntimeError('Vision agent left actual game screen')
        try:
            raw=private_file('files/console-ai-user-rom-v1.log',262144)
            rows=verified_generic_rows(raw,FILES[MODEL][1])
            (output.parent/'real-game-transitions.log').write_bytes(raw)
            inference=private_file('files/harness-model-execution-v1.log',262144)
            calls=correlate_inference(inference,rows,FILES[MODEL][1])
            (output.parent/'real-game-inference.log').write_bytes(inference)
            (output.parent/'partial-game-observation.json').write_text(json.dumps(dict(result='PARTIAL_NOT_ACCEPTED',observed=rows),indent=2)+'\n')
            progress=any(verified_game_progress(r) for r in rows)
            if rows and rows[-1]['gameAfter']['state']==2:
                raise RuntimeError('Actual VOID LANCER GAME OVER before accepted gameplay; preserved real observations')
            if len(rows)>=4 and progress:break
        except EvidencePending:pass
        text='\n'.join(n.get('text','') for n in ET.fromstring(ui.dump_ui()).iter('node'))
        if 'STOP:' in text:raise RuntimeError('Real game agent stopped before verified game progress: '+text[:800])
        if len(rows)>=16:raise RuntimeError('No real score/kill progress within the selected game episode')
        time.sleep(.5)
    else:raise RuntimeError('Real game did not confirm model-driven score/kill progress')
    started_by_model=any(row['button']=='START' and row['gameBefore']['state']==0 and row['gameAfter']['state']==1 for row in rows)
    if not started_by_model:raise RuntimeError('Real game did not start through a correlated model START')
    if not any(row['semanticControl'] and row['gameBefore']['state']==1 and row['gameAfter']['state']==1 for row in rows):
        raise RuntimeError('Frames changed but ship movement, firing or bomb use was not confirmed')
    ui.tap_text('STOP',swipes=0)
    before=private_file('files/console-ai-user-rom-v1.log',262144);time.sleep(1)
    if before!=private_file('files/console-ai-user-rom-v1.log',262144):raise RuntimeError('Action completed after real-game STOP')
    native_settings=ui.adb('shell','logcat','-d','-s','LocalPilotNative:I','*:S')
    native_sampling=verified_controller_settings(native_settings,MODEL_PROFILE,len(rows))
    tool_log=ui.adb('shell','logcat','-d','-s','LocalPilotTools:I','*:S')
    tool_calls=verified_tool_calls(tool_log,rows,FILES[MODEL][1])
    (output.parent/'model-tool-invocations.log').write_text(tool_log)
    (output.parent/'native-controller-settings.log').write_text('\n'.join(line for line in native_settings.splitlines() if 'controller_sampler=' in line)+'\n')
    report=dict(result='REAL_ROM_GGUF_GAME_PROGRESS_PASS',modelProfile=MODEL_PROFILE,modelRepository=REPO,modelRevision=REVISION,sourceSha=source,
                apkSha256=hashlib.sha256(apk.read_bytes()).hexdigest(),romSha256=ROM_SHA,
                modelSha256=FILES[MODEL][1],modelKind='TEXT_GGUF_WITH_VERIFIED_GAME_RAM',
                transitions=rows,inferenceCallsForModel=calls,nativeSamplingStarted=native_sampling,modelToolCalls=tool_calls,exactModelActionAndFrameMatch=True,semanticGameControlVerified=True,
                realScoreBefore=rows[0]['gameBefore']['score'],realScoreAfter=rows[-1]['gameAfter']['score'],
                gameProgressVerified=True,
                modelTransport='VISIBLE_SAF',romTransport='VISIBLE_SAF',decisionInput='VERIFIED_ROM_RAM',visionInferencePerformed=False,
                currentRomPreserved=True,gameStartedByModel=started_by_model,educationalScene=False,reward=None,
                gameVictory=rows[-1]['gameAfter']['state']==3,physicalDevice=False,releaseReady=False)
    output.write_text(json.dumps(report,indent=2)+'\n');print('REAL_ROM_GGUF_GAME_PROGRESS_PASS '+json.dumps(report))

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('mode',choices=['prepare','verify'])
    p.add_argument('--directory',type=Path,default=Path('build/real-nes-vision'))
    p.add_argument('--apk',type=Path);p.add_argument('--output',type=Path,default=Path('real-nes-evidence/report.json'))
    p.add_argument('--model-profile',choices=sorted(MODEL_PROFILES),default='qwen35')
    a=p.parse_args()
    select_model(a.model_profile)
    if a.mode=='prepare':prepare(a.directory)
    else:
        attempt={}
        try:verify(a.apk,a.directory,a.output,attempt)
        except Exception as error:
            a.output.parent.mkdir(parents=True,exist_ok=True)
            a.output.write_text(json.dumps(dict(result='FAIL',error=str(error)[:1800],attemptIdentity=attempt,educationalScene=False if attempt.get('romSha256')==ROM_SHA else None,releaseReady=False))+'\n')
            partial=a.output.parent/'partial-game-observation.json'
            if partial.is_file():print('ACTUAL_GAME_FAILURE_DIAGNOSTIC '+partial.read_text()[:24000],flush=True)
            if ui.is_emulator():
                # This isolated run contains only the pinned public CI ROM/model.
                # Capture the native call even when no game transition completed.
                try:
                    inference=private_file('files/harness-model-execution-v1.log',262144)
                    (a.output.parent/'real-game-inference.log').write_bytes(inference)
                    print('ACTUAL_GAME_INFERENCE_DIAGNOSTIC '+inference.decode()[-12000:],flush=True)
                except EvidencePending:pass
                timing=ui.adb('shell','logcat','-d','-s','LocalPilotNative:I','LocalPilotObservation:I','LocalPilotTools:I','*:S')[-16000:]
                (a.output.parent/'native-phase-timings.log').write_text(timing)
                print('ACTUAL_NATIVE_PHASE_TIMINGS '+timing,flush=True)
                ui.write_failure_diagnostics(a.output,error)
            raise
