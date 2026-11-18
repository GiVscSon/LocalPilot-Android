#!/usr/bin/env python3
"""Controlled actual Android/GGUF/ROM diagnostics; completion is not gameplay PASS."""
import argparse
import hashlib
import io
import json
from pathlib import Path
import subprocess
import tarfile
import zipfile
import run_real_nes_vision_acceptance as real
import unified_in_app_smoke as ui
from run_g2_direct_gguf_acceptance import import_picker_file
from run_harness_runtime_acceptance import command, PACKAGE

SEEDS=(42,1042,2042)
SNAPSHOTS=('TITLE','PLAYING','THREAT')

def digest(raw):return hashlib.sha256(raw).hexdigest()

def validate_tar(raw,prefix):
    if len(raw)>32*1024*1024:raise ValueError('Archive quota')
    with tarfile.open(fileobj=io.BytesIO(raw)) as archive:
        names=set();total=0
        for member in archive.getmembers():
            name=member.name.rstrip('/')
            if not name.startswith(prefix) or name.startswith('/') or '..' in Path(name).parts or name in names:
                raise ValueError('Unsafe archive path')
            if not(member.isfile() or member.isdir()):raise ValueError('Unsafe archive member')
            names.add(name);total+=member.size
        if total>32*1024*1024:raise ValueError('Expanded archive quota')

def setup(apk,test_apk,directory,profile,output):
    if not ui.is_emulator() or ui.adb('shell','getprop','ro.product.cpu.abi').strip()!='x86_64' or ui.adb('shell','getprop','ro.build.version.sdk').strip()!='35':
        raise RuntimeError('Isolated API35 x86_64 required')
    if ui.adb('shell','pm','list','packages',PACKAGE).strip():
        raise RuntimeError('Fresh isolated AVD required; refusing existing app data')
    with zipfile.ZipFile(apk) as z:source=json.loads(z.read('assets/localpilot-source.json'))['sourceSha']
    if source!=command('git','rev-parse','HEAD').strip():raise RuntimeError('Checked-out source differs from actual APK')
    real.select_model(profile);real.prepare(directory)
    identity=dict(sourceSha=source,apkSha256=digest(apk.read_bytes()),testApkSha256=digest(test_apk.read_bytes()),
                  modelProfile=profile,modelSha256=real.FILES[real.MODEL][1],romSha256=real.ROM_SHA,
                  evidenceType='EMULATOR_ANDROID',physicalDevice=False,weightsTrained=False,releaseReady=False)
    output.mkdir(parents=True,exist_ok=True);(output/'attempt-identity.json').write_text(json.dumps(identity,indent=2))
    command('adb','install','-r',str(apk),timeout=120);command('adb','install','-r','-t',str(test_apk),timeout=120)
    ui.adb('shell','am','start','-W','-n',PACKAGE+'/'+ui.MAIN);ui.wait_activity(ui.MAIN)
    ui.tap_text('Рабочее окно · игра и агент');ui.wait_activity(ui.NES)
    import_picker_file(directory/real.MODEL,real.FILES[real.MODEL][1],'LOCALPILOT_CI_ONLY_G4_MODEL.gguf',output/'ui.json')
    ui.tap_text('Управление моделями NES',swipes=0);ui.tap_text('Привязать выбранный GGUF к NES',swipes=0)
    push_private((directory/real.ROM).read_bytes(),'tee','files/g4-input-rom.nes')
    return identity

def push_private(raw,*args):
    p=subprocess.run(['adb','shell','-T','run-as',PACKAGE,*args],input=raw,capture_output=True,timeout=90)
    if p.returncode:raise RuntimeError(p.stderr.decode(errors='replace')[-1000:])

def instrument(mode,profile,output,variant=None,pair=None):
    args=['adb','shell','am','instrument','-w','-e','mode',mode,'-e','modelProfile',profile]
    if variant:args+=['-e','variant',variant]
    if pair is not None:args+=['-e','holdoutIndex',str(pair)]
    args+=[PACKAGE+'.test/org.localpilot.android.HarnessRuntimeAcceptanceInstrumentation']
    raw=command(*args,timeout=55*60);(output/'instrumentation.txt').write_text(raw)
    prefix='INSTRUMENTATION_RESULT: evidence_json='
    lines=[line[len(prefix):] for line in raw.splitlines() if line.startswith(prefix)]
    if len(lines)!=1:raise RuntimeError('Missing actual instrumentation result')
    evidence=json.loads(lines[0]);(output/'instrumentation.json').write_text(json.dumps(evidence,indent=2))
    return evidence

def pull_archive(paths):
    p=subprocess.run(['adb','exec-out','run-as',PACKAGE,'tar','-cf','-',*paths],capture_output=True,timeout=90)
    if p.returncode:raise RuntimeError(p.stderr.decode(errors='replace')[-1000:])
    return p.stdout

def parse_wire(value):return real.parse_game_state(value)

def validate_case(report,traces,receipts,identity,variant,seed,snapshot,episode):
    if report.get('result')!='OBSERVED' or report.get('stopNoLateTransition') is not True:
        raise ValueError('Incomplete case: '+str(report.get('error','UNKNOWN')))
    if report.get('variant')!=variant or report.get('baseSeed')!=seed or report.get('snapshot')!=snapshot or report.get('episode')!=episode:
        raise ValueError('Wrong case identity')
    rows=report['transitions'];before=parse_wire(report['gameAtRestore']);maximum=16 if episode else 1
    if not 1<=len(traces)<=maximum or len(rows)!=len(receipts) or len(rows)>len(traces):raise ValueError('Missing actual inference/transition receipts')
    if len({t['nonce'] for t in traces})!=len(traces) or len({t['epoch'] for t in traces})!=1:raise ValueError('Reused nonce/mixed epoch')
    if traces[0]['gameBefore']!=report['gameAtRestore']:raise ValueError('Inference state differs from restored RAM')
    if len({r['actionNonce'] for r in receipts})!=len(receipts):raise ValueError('Reused action permit nonce')
    previous=None
    for index,trace in enumerate(traces):
        if (trace['caseId']!=report['caseId'] or trace['variant']!=variant or trace['seed']!=seed+index or
            trace['romSha256']!=identity['romSha256'] or trace['modelSha256']!=identity['modelSha256'] or
            trace['snapshotSha256']!=report['snapshotSha256'] or not trace['systemPrompt'] or not trace['userPrompt']):
            raise ValueError('Unbound actual inference')
        expected_scope=3 if parse_wire(trace['gameBefore'])['state']!=1 else 4
        if trace['grammarScope']!=(5 if variant in ('D','E') else expected_scope):raise ValueError('Wrong native grammar arm')
        if trace['prefillMs'] is None or trace['decodeMs'] is None or trace['prefillMs']<0 or trace['decodeMs']<0:
            raise ValueError('Missing actual native stage measurements')
        if trace['reason']=='PROPOSAL_NOT_YET_EXECUTED' and not any(r['observationNonce']==trace['nonce'] for r in receipts):
            raise ValueError('Valid proposal lacks actual completed actuator evidence')
    for row,receipt in zip(rows,receipts):
        matches=[t for t in traces if t['nonce']==receipt['observationNonce']]
        if len(matches)!=1:raise ValueError('Transition lacks exact observation nonce')
        trace=matches[0];a=parse_wire(row['beforeGame']);b=parse_wire(row['afterGame'])
        if trace['reason']!='PROPOSAL_NOT_YET_EXECUTED':raise ValueError('Rejected/stale inference became an action')
        if row['epoch']!=trace['epoch'] or receipt['epoch']!=trace['epoch'] or receipt['modelSha256']!=identity['modelSha256'] or receipt['romSha256']!=real.ROM_SHA:
            raise ValueError('Transition identity changed')
        for key in ('action','beforeFrame','afterFrame','beforeHash','afterHash','beforeGame','afterGame'):
            if receipt[key]!=row[key]:raise ValueError('Actual actuator receipt differs')
        if (row['beforeFrame']!=trace['frame'] or row['beforeHash']!=trace['frameHash'] or row['beforeGame']!=trace['gameBefore'] or
            row['afterFrame']-row['beforeFrame']!=16 or not real.valid_game_button(row['action']) or
            a['state']!=1 and row['action'] not in ('START','WAIT') or a['state']==1 and row['action'] in ('START','SELECT')):
            raise ValueError('Invalid actual phase/action/frame')
        raw=json.loads(trace['rawReply']);expected={'nes_start':'START','nes_wait':'WAIT','nes_press':raw.get('button')}.get(raw.get('tool'))
        if expected!=row['action'] or raw.get('frames')!=16:raise ValueError('Model call replaced before execution')
        if previous and (row['beforeGame']!=previous['afterGame'] or row['beforeFrame']!=previous['afterFrame'] or row['beforeHash']!=previous['afterHash']):
            raise ValueError('Discontinuous actual episode')
        previous=row
    if rows and rows[0]['beforeGame']!=report['gameAtRestore']:raise ValueError('Initial state differs from snapshot')
    last=parse_wire(rows[-1]['afterGame']) if rows else before
    started=any(parse_wire(r['beforeGame'])['state']==0 and parse_wire(r['afterGame'])['state']==1 and r['action']=='START' for r in rows)
    moved=any(parse_wire(r['beforeGame'])['state']==1 and parse_wire(r['afterGame'])['state']==1 and
              parse_wire(r['beforeGame'])['lives']==parse_wire(r['afterGame'])['lives'] and
              any(real.confirms_control(k,parse_wire(r['beforeGame']),parse_wire(r['afterGame'])) for k in r['action'].split('+') if k in ('LEFT','RIGHT','UP','DOWN')) for r in rows)
    fired=any('A' in r['action'].split('+') and real.confirms_control('A',parse_wire(r['beforeGame']),parse_wire(r['afterGame'])) for r in rows)
    score=sum(max(0,parse_wire(r['afterGame'])['score']-parse_wire(r['beforeGame'])['score']) for r in rows if parse_wire(r['beforeGame'])['state']==1 and parse_wire(r['afterGame'])['state'] in (1,2,3))
    kills=sum(max(0,parse_wire(r['afterGame'])['kills']-parse_wire(r['beforeGame'])['kills']) for r in rows if parse_wire(r['beforeGame'])['state']==1 and parse_wire(r['afterGame'])['state'] in (1,2,3))
    lost=sum(max(0,parse_wire(r['beforeGame'])['lives']-parse_wire(r['afterGame'])['lives']) for r in rows if parse_wire(r['beforeGame'])['state']==1)
    return dict(caseId=report['caseId'],episode=episode,variant=variant,seed=seed,snapshot=snapshot,snapshotSha256=report['snapshotSha256'],
                A=started if episode else None,B=moved and fired,C=bool(episode and started and (score>0 or kills>0)),
                moved=moved,fired=fired,scoreDelta=score,killsDelta=kills,livesLost=lost,gameVictory=last['state']==3,
                decisions=len(traces),transitions=len(rows),waits=sum(r['action']=='WAIT' for r in rows),weightsTrained=False)

def read_archive(raw):
    validate_tar(raw,'files/g4-')
    with tarfile.open(fileobj=io.BytesIO(raw)) as archive:
        return {m.name:archive.extractfile(m).read() for m in archive.getmembers() if m.isfile()}

def validate_journals(report,traces,journal,inference,model_sha):
    rows=real.verified_generic_rows(journal,model_sha)
    if len(rows)!=len(report['transitions']):raise ValueError('Actual journal rows missing')
    if real.correlate_inference(inference,rows,model_sha)!=len(traces):raise ValueError('Actual inference journal calls missing')
    epochs=[line.decode().split('|')[2] for line in journal.splitlines()]
    for actual,claimed,epoch in zip(rows,report['transitions'],epochs):
        if epoch!=claimed['epoch'] or actual['button']!=claimed['action']:raise ValueError('Journal actor/epoch differs')
        for key in ('beforeFrame','afterFrame','beforeHash','afterHash'):
            if actual[key]!=claimed[key]:raise ValueError('Journal frame/hash differs')
        if actual['gameBefore']!=parse_wire(claimed['beforeGame']) or actual['gameAfter']!=parse_wire(claimed['afterGame']):
            raise ValueError('Journal RAM differs')

def summarize(files,identity,variant,pair=None):
    result=[];errors=[]
    cases=[(seed,snapshot,episode,variant) for seed in SEEDS for snapshot,episode in [(s,False) for s in SNAPSHOTS]+[('TITLE',True)]] if pair is None else [
        (10042+1000*pair,'HOLDOUT_'+str(pair),True,v) for v in (['A'] if variant=='A' else ['A',variant])]
    for seed,snapshot,episode,arm in cases:
            case=f"{identity['modelProfile']}_{arm}_{snapshot}_{seed}_{'episode' if episode else 'probe'}"
            try:
                report=json.loads(files['files/g4-results/'+case+'.json'])
                traces=[json.loads(s) for s in files.get('files/g4-traces/'+case+'.jsonl',b'').splitlines()]
                receipts=[json.loads(s) for s in files.get('files/g4-traces/'+case+'.transitions.jsonl',b'').splitlines()]
                validate_journals(report,traces,files.get('files/g4-results/'+case+'.journal.log',b''),
                                  files.get('files/g4-results/'+case+'.inference.log',b''),identity['modelSha256'])
                result.append(validate_case(report,traces,receipts,identity,arm,seed,snapshot,episode))
            except Exception as error:errors.append(dict(caseId=case,error=str(error)))
    return dict(result='DIAGNOSTICS_COMPLETED' if not errors else 'INCOMPLETE_DIAGNOSTICS',identity=identity,variant=variant,cases=result,errors=errors,
                gameplayAcceptance=False,weightsTrained=False,physicalDevice=False,releaseReady=False)

def main(a):
    identity=setup(a.apk,a.test_apk,a.directory,'qwen35' if a.mode=='bootstrap' else a.model_profile,a.output)
    if a.mode=='bootstrap':
        evidence=instrument('g4-bootstrap','qwen35',a.output)
        if evidence.get('result')!='REAL_MODEL_FIXTURES_CAPTURED':raise RuntimeError(str(evidence))
        raw=pull_archive(['files/g4-fixtures']);validate_tar(raw,'files/g4-fixtures')
        (a.output/'fixtures.tar').write_bytes(raw);(a.output/'fixtures-identity.json').write_text(json.dumps(dict(**identity,archiveSha256=digest(raw)),indent=2))
    else:
        raw=a.fixtures.read_bytes();validate_tar(raw,'files/g4-fixtures')
        fixture_id=json.loads(a.fixtures.with_name('fixtures-identity.json').read_text())
        if fixture_id['archiveSha256']!=digest(raw) or fixture_id['sourceSha']!=identity['sourceSha'] or fixture_id['apkSha256']!=identity['apkSha256']:
            raise ValueError('Fixture source/APK/archive differs')
        push_private(raw,'tar','-xf','-')
        try:instrument('g4-matrix',a.model_profile,a.output,a.variant,a.holdout_index)
        finally:
            raw=pull_archive(['files/g4-results','files/g4-traces']);(a.output/'actual-cases.tar').write_bytes(raw)
        report=summarize(read_archive(raw),identity,a.variant,a.holdout_index);report['holdoutIndex']=a.holdout_index
        (a.output/'report.json').write_text(json.dumps(report,indent=2))
        print('G4_DIAGNOSTICS '+json.dumps(report),flush=True)
        if report['errors']:raise RuntimeError('Missing/invalid diagnostic evidence; partial cases preserved')

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('mode',choices=['bootstrap','matrix'])
    p.add_argument('--apk',type=Path,required=True);p.add_argument('--test-apk',type=Path,required=True)
    p.add_argument('--directory',type=Path,default=Path('build/real-nes-vision'));p.add_argument('--output',type=Path,required=True)
    p.add_argument('--model-profile',choices=['qwen35','lfm350','lfm1200'],default='qwen35');p.add_argument('--variant',choices=list('ABCDE'),default='A')
    p.add_argument('--fixtures',type=Path);p.add_argument('--holdout-index',type=int,choices=range(20));a=p.parse_args();main(a)
