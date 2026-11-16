#!/usr/bin/env python3
"""Actual Android Application ownership and negative model admission, NOT inference."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import zipfile

PACKAGE='org.localpilot.android.dev.unified'

def command(*args, timeout=40):
    p=subprocess.run(args,capture_output=True,text=True,timeout=timeout)
    if p.returncode:raise RuntimeError((p.stdout+p.stderr)[-2000:])
    return p.stdout

def validate(e):
    if e.get('schema')!='localpilot-application-runtime-contract-v1' or e.get('result')!='ANDROID_CONTRACT_PASS':
        raise RuntimeError('Runtime contract failed: '+str(e.get('error','UNKNOWN')))
    for name in ('sameEngineAcrossActivityRecreation','sameCatalogAcrossScreens','missingModelFailsClosed','duplicateRequestRejected','stopRevokesPendingReply','syntheticObservationMetadata','modelLibraryLifecycle','modelLibraryBusyFailsClosed'):
        if e.get(name) is not True:raise RuntimeError('Missing contract proof: '+name)
    for name in ('inputAuthorityConsumed','nativeInferencePerformed','physicalDevice','releaseReady'):
        if e.get(name) is not False:raise RuntimeError('False inference/device claim: '+name)

def main(apk, test_apk, output):
    if '1' not in [command('adb','shell','getprop',key).strip() for key in ('ro.kernel.qemu','ro.boot.qemu')]:
        raise RuntimeError('Isolated AVD required')
    source=command('git','rev-parse','HEAD').strip()
    with zipfile.ZipFile(apk) as z:
        if json.loads(z.read('assets/localpilot-source.json'))['sourceSha']!=source:raise RuntimeError('App and test source differ')
    command('adb','install','-r',str(apk),timeout=90)
    command('adb','install','-r','-t',str(test_apk),timeout=90)
    raw=command('adb','shell','am','instrument','-w',PACKAGE+'.test/org.localpilot.android.HarnessRuntimeAcceptanceInstrumentation',timeout=150)
    output.parent.mkdir(parents=True,exist_ok=True);(output.parent/'instrumentation.txt').write_text(raw)
    prefix='INSTRUMENTATION_RESULT: evidence_json='
    evidence=json.loads(next(line[len(prefix):] for line in raw.splitlines() if line.startswith(prefix)))
    validate(evidence)
    evidence.update(sourceSha=source,apkSha256=hashlib.sha256(apk.read_bytes()).hexdigest(),testApkSha256=hashlib.sha256(test_apk.read_bytes()).hexdigest(),api=int(command('adb','shell','getprop','ro.build.version.sdk').strip()))
    output.write_text(json.dumps(evidence,indent=2)+'\n')
    print('APPLICATION_RUNTIME_ANDROID_CONTRACT_PASS '+json.dumps(evidence))

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--apk',type=Path,required=True);p.add_argument('--test-apk',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args()
    try:main(a.apk,a.test_apk,a.output)
    except Exception as error:
        a.output.parent.mkdir(parents=True,exist_ok=True);a.output.write_text(json.dumps(dict(result='FAIL',error=str(error)[:1600],releaseReady=False))+'\n');raise
