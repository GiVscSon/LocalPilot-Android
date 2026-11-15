#!/usr/bin/env python3
"""Real public GGUF -> existing JNI -> same foreground NES -> verified steps.

Only an isolated AVD may import the reviewed public preset into a fresh catalog.
No fake model response, raw checkpoint upload, Android gesture authority or reward.
"""
import argparse
import hashlib
import json
from pathlib import Path
import time
import subprocess
import zipfile
import unified_in_app_smoke as ui
from harness_student_device_acceptance import private_file, EvidencePending
from run_harness_runtime_acceptance import command, PACKAGE
from bundle_embedded_gguf import EXPECTED_SHA256, EXPECTED_BYTES, sha_stream
from verify_g2_trace import correlate

def main(apk,gguf,output):
    if not ui.is_emulator():raise RuntimeError('Isolated AVD required')
    with zipfile.ZipFile(apk) as z:source=json.loads(z.read('assets/localpilot-source.json'))['sourceSha']
    output.parent.mkdir(parents=True,exist_ok=True)
    with gguf.open('rb') as fixture_stream:actual,size=sha_stream(fixture_stream,limit=EXPECTED_BYTES)
    if actual!=EXPECTED_SHA256 or size!=EXPECTED_BYTES:raise RuntimeError('Host GGUF fixture identity mismatch')
    # App-private fixture on an isolated AVD only; no public storage permission,
    # no user data, no DNS dependency for a local inference acceptance test.
    with gguf.open('rb') as fixture_stream:
        subprocess.run(['adb','exec-in','run-as',PACKAGE,'dd','of=files/g2-public-fixture.gguf','bs=1048576'],
            stdin=fixture_stream,capture_output=True,check=True,timeout=120)
    raw=command('adb','shell','am','instrument','-w','-e','mode','import-local-pocket',PACKAGE+'.test/org.localpilot.android.HarnessRuntimeAcceptanceInstrumentation',timeout=150)
    (output.parent/'model-import-instrumentation.txt').write_text(raw)
    prefix='INSTRUMENTATION_RESULT: evidence_json='
    setup=json.loads(next(s[len(prefix):] for s in raw.splitlines() if s.startswith(prefix)))
    if setup.get('result')!='MODEL_IMPORT_PASS_NOT_INFERENCE' or setup.get('modelSha256')!=EXPECTED_SHA256 or setup.get('modelBytes')!=EXPECTED_BYTES:
        raise RuntimeError('Pinned production model import failed: '+str(setup))
    ui.adb('shell','am','start','-W','-n',PACKAGE+'/'+ui.MAIN);ui.wait_activity(ui.MAIN)
    ui.tap_harness_and_verify();ui.tap_text('Играть: назначенная GGUF');ui.wait_activity(ui.NES)
    # Measured CPU calls take70-74s on this AVD. Allow FOUR independent
    #120s requests plus30s transport; each original controller TTL is unchanged.
    last='NO_TRACE';deadline=time.monotonic()+4*120+30;checked=None
    while time.monotonic()<deadline:
        if PACKAGE+'/'+ui.NES not in ui.resumed_activity():raise RuntimeError('Automatic GGUF inference switched away from NES')
        try:
            inference=private_file('files/harness-model-execution-v1.log',131072)
            episodes=private_file('files/console-ai-experience-v2.log',131072)
            # Public fixture metadata only; preserve the real decisions on failure.
            (output.parent/'gguf-inference.log').write_bytes(inference)
            (output.parent/'gguf-transitions.log').write_bytes(episodes)
            checked=correlate(inference,episodes,EXPECTED_SHA256)
            last=f"MATCHED_{checked['matchedTransitions']}_OF_4"
            if checked['matchedTransitions']>=4:break
        except (EvidencePending,ValueError) as pending:last=str(pending)
        time.sleep(.5)
    else:raise RuntimeError('No four real GGUF -> NES transitions: '+last)
    ui.tap_text('STOP',swipes=0)
    before=private_file('files/console-ai-experience-v2.log',131072);time.sleep(1)
    if before!=private_file('files/console-ai-experience-v2.log',131072):raise RuntimeError('A new action completed after STOP')
    checked.update(result='EMULATOR_GGUF_ACTION_TRANSITION_PASS',sourceSha=source,
        apkSha256=hashlib.sha256(apk.read_bytes()).hexdigest(),api=int(ui.adb('shell','getprop','ro.build.version.sdk')),
        foregroundStayedNes=True,modelImport=setup,stopBlocksFurtherTransitions=True,
        modelKind='TEXT_ONLY_CPU_NO_MMPROJ',physicalDevice=False,releaseReady=False)
    output.write_text(json.dumps(checked,indent=2)+'\n');print('G2_DIRECT_GGUF_ANDROID_PASS '+json.dumps(checked))

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--apk',type=Path,required=True);p.add_argument('--gguf',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args()
    try:main(a.apk,a.gguf,a.output)
    except Exception as error:
        if ui.is_emulator():ui.write_failure_diagnostics(a.output,error)
        a.output.parent.mkdir(parents=True,exist_ok=True);a.output.write_text(json.dumps(dict(result='FAIL',error=str(error)[:1700],releaseReady=False))+'\n');raise
