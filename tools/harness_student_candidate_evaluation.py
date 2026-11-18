#!/usr/bin/env python3
"""Evaluate the EXACT fresh Android PPO candidate using the existing JSNES gate.

Runs only on an isolated CI AVD after the 16-step production training test.
Private weights/replays exist only in a temporary directory, never artifacts.
The Android manifest/active weights are not modified; no promotion is attempted.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import tempfile
import zipfile
import unified_in_app_smoke as ui
from harness_student_device_acceptance import private_file, verified_manifest, VAULT

CORE_SHA='84946f0dae4bcf3b2dfebe84ad3bfeb1dbcb413bd9b1a69e1f45910a95fd7b41'
REPLAY_DIR='files/localpilot_vault/episodes/onpolicy-v1'


def binding(manifest, active, candidate, replay, filename):
    before,after=verified_manifest(manifest,active,candidate)
    digest=hashlib.sha256(replay).hexdigest()
    if (manifest.get('trainingReplaySha256')!=digest or filename!=digest+'.lpr' or
        not re.fullmatch(r'[1-9][0-9]{0,18}',str(manifest.get('trainingEpoch','')))):
        raise RuntimeError('Candidate is not bound to this exact immutable training replay')
    return before,after,digest


def run(*args):
    result=subprocess.run(list(map(str,args)),capture_output=True,text=True,timeout=240)
    if result.returncode:raise RuntimeError('Candidate exam stage failed: '+str(args[0])+' '+(result.stdout+result.stderr)[-2500:])
    print(result.stdout.strip())


def main(apk, output):
    if not ui.is_emulator():raise RuntimeError('Isolated Android emulator required')
    with zipfile.ZipFile(apk) as z:
        source=json.loads(z.read('assets/localpilot-source.json'))['sourceSha']
        core=z.read('assets/nes/jsnes.min.js')
    if hashlib.sha256(core).hexdigest()!=CORE_SHA:raise RuntimeError('Unpinned JSNES runtime')
    apk_sha=hashlib.sha256(apk.read_bytes()).hexdigest()
    installed=ui.adb('shell','pm','path',ui.PACKAGE).strip().removeprefix('package:')
    if '\n' in installed or not installed.startswith('/data/app/') or ui.adb('shell','sha256sum',installed).split()[0]!=apk_sha:
        raise RuntimeError('Candidate APK differs from installed production trainer')
    manifest_raw=private_file(VAULT+'candidates/student-homebrew-v1.manifest.json',4096)
    manifest=json.loads(manifest_raw)
    active=private_file(VAULT+'active/student-homebrew-v1.weights.bin')
    candidate=private_file(VAULT+'candidates/student-homebrew-v1.weights.bin')
    names=ui.adb('shell','run-as',ui.PACKAGE,'ls',REPLAY_DIR).split()
    if len(names)!=1:raise RuntimeError('Exam must follow the single original training batch before autoplay')
    replay=private_file(REPLAY_DIR+'/'+names[0],65536)
    before,after,replay_sha=binding(manifest,active,candidate,replay,names[0])
    with tempfile.TemporaryDirectory(prefix='localpilot-private-candidate-exam-') as tmp:
        private=Path(tmp);classes=private/'classes';classes.mkdir()
        active_path=private/'active.weights.bin';candidate_path=private/'candidate.weights.bin'
        replay_path=private/names[0];core_path=private/'jsnes.js';rom_path=private/'demo.nes'
        train=private/'training.tsv';holdout=private/'holdout.tsv';report=private/'evaluation.json'
        for p,b in ((active_path,active),(candidate_path,candidate),(replay_path,replay),(core_path,core)):
            p.write_bytes(b);p.chmod(0o600)
        base=Path('shared/src/main/java/org/localpilot/shared')
        sources=[base/(n+'.java') for n in ('ConsoleAgentContract','NesTrial','HomebrewGoalReward',
            'ConsoleExperienceMemory','VoidLancerGameState','VerifiedReplayFilter','VerifiedPpoReplayStore','StudentPolicyNet',
            'HomebrewObs64','StudentEvaluationGate')]
        sources += [Path('game-lab/src/main/java/org/localpilot/gamelab')/(n+'.java') for n in ('HomebrewNes','HomebrewRamReward')]
        sources += [Path('tests')/(n+'.java') for n in ('ExportHomebrewNes','StudentPolicyCli','AndroidCandidateReplayExport','StudentHoldoutRealNesVerify')]
        run('javac','--release','17','-d',classes,*sources)
        run('java','-cp',classes,'ExportHomebrewNes',rom_path)
        if hashlib.sha256(rom_path.read_bytes()).hexdigest()!=manifest['romSha256']:
            raise RuntimeError('Compiled cartridge differs from Android training cartridge')
        run('java','-cp',classes,'org.localpilot.shared.AndroidCandidateReplayExport',replay_path,active_path,train)
        epochs={line.split('\t')[1] for line in train.read_text().splitlines()}
        if epochs!={manifest['trainingEpoch']}:raise RuntimeError('Manifest training epoch differs from durable samples')
        run('node','tests/nes_student_holdout_smoke.cjs',core_path,rom_path,classes,active_path,candidate_path,train,holdout)
        run('java','-cp',classes,'org.localpilot.shared.StudentHoldoutRealNesVerify',rom_path,active_path,
            candidate_path,train,holdout,report,'EXPECT_EVALUATION')
        result=json.loads(report.read_text())
        selection=json.loads(Path(str(holdout)+'.selection.json').read_text())
    if (result['activePolicySha256']!=before or result['candidatePolicySha256']!=after or
        result['trainingEpoch']!=manifest['trainingEpoch']):raise RuntimeError('Independent exam identity differs')
    for path,raw in ((VAULT+'active/student-homebrew-v1.weights.bin',active),
        (VAULT+'candidates/student-homebrew-v1.weights.bin',candidate),
        (VAULT+'candidates/student-homebrew-v1.manifest.json',manifest_raw)):
        if private_file(path,100000)!=raw:raise RuntimeError('Read-only candidate exam changed Android weights/manifest')
    result.update(sourceSha=source,apkSha256=apk_sha,api=int(ui.adb('shell','getprop','ro.build.version.sdk')),
        candidateOrigin='PRODUCTION_ANDROID_WEBVIEW_PPO',evaluationRuntime='HOST_REAL_JSNES_NOT_ANDROID',
        trainingBinaryReplaySha256=replay_sha,taskMask='homebrew-task-mask-v1',
        scenarioSelection=selection,
        androidWeightsAndManifestUnchanged=True,releaseReady=False)
    output.parent.mkdir(parents=True,exist_ok=True);output.write_text(json.dumps(result,indent=2)+'\n')
    print('ANDROID_CANDIDATE_INDEPENDENT_EXAM_COMPLETE '+json.dumps(result))


if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--apk',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args()
    try:main(a.apk,a.output)
    except Exception as error:
        a.output.parent.mkdir(parents=True,exist_ok=True);a.output.write_text(json.dumps(dict(result='FAIL',error=str(error)[:1600],releaseReady=False))+'\n');raise
