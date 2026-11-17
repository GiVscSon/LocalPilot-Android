#!/usr/bin/env python3
"""Summarize all catalogue models without promoting missing or failed evidence."""
import argparse
import csv
import json
from pathlib import Path
import re
from model_lab_catalog import presets


def summarize(directory, source):
    catalog=presets(); found={}; problems=[]; apk_hashes=set()
    for path in sorted(Path(directory).rglob('report.json')):
        try:
            report=json.loads(path.read_text())
            identity=report.get('attemptIdentity',{}) if report.get('result')=='FAIL' else report
            key=identity.get('modelId')
            if key not in catalog:raise ValueError('missing or unknown model identity')
            if key in found:raise ValueError('duplicate model report')
            if identity.get('sourceSha')!=source:raise ValueError('different source SHA')
            if identity.get('modelSha256')!=catalog[key]['sha256']:raise ValueError('different model SHA')
            apk=identity.get('apkSha256','')
            if not re.fullmatch('[0-9a-f]{64}',apk):raise ValueError('missing APK SHA')
            apk_hashes.add(apk)
            passed=(report.get('result')=='REAL_ROM_GGUF_GAME_PROGRESS_PASS' and
                    all(report.get(flag) is True for flag in ('exactModelActionAndFrameMatch',
                        'semanticGameControlVerified','gameProgressVerified','gameStartedByModel')) and
                    report.get('physicalDevice') is False and report.get('visionInferencePerformed') is False and
                    report.get('inferenceCallsForModel',0)>0 and bool(report.get('transitions')))
            found[key]=dict(model=key,status='PASS' if passed else 'FAIL',
                            inference='PASS' if passed else 'NOT_CONFIRMED',
                            control='PASS' if passed else 'NOT_CONFIRMED',
                            vision='NOT_RUN',physical='NOT_RUN',
                            reason=report.get('error','Incomplete acceptance evidence') if not passed else '',
                            artifact=str(path),apkSha256=apk)
        except (ValueError,TypeError,KeyError) as error:
            problems.append(str(path)+': '+str(error))
    if len(apk_hashes)>1:problems.append('Models were tested against different APK bytes')
    rows=[found.get(key,dict(model=key,status='MISSING',inference='NOT_CONFIRMED',
          control='NOT_CONFIRMED',vision='NOT_RUN',physical='NOT_RUN',reason='No valid report',
          artifact='',apkSha256='')) for key in catalog]
    return dict(sourceSha=source,rows=rows,problems=problems,
                complete=not problems and all(r['status']=='PASS' for r in rows),releaseReady=False)


def write_reports(report, prefix):
    prefix=Path(prefix);prefix.parent.mkdir(parents=True,exist_ok=True)
    prefix.with_suffix('.json').write_text(json.dumps(report,indent=2)+'\n')
    with prefix.with_suffix('.csv').open('w',newline='') as out:
        writer=csv.DictWriter(out,fieldnames=list(report['rows'][0]));writer.writeheader();writer.writerows(report['rows'])
    lines=['| Model | Game acceptance | Native inference | Control | Vision | Physical |',
           '|---|---|---|---|---|---|']
    lines += ['| '+' | '.join(r[k] for k in ('model','status','inference','control','vision','physical'))+' |' for r in report['rows']]
    lines += ['', 'Source: '+report['sourceSha'], 'Release ready: false', '',
              'FAIL does not establish that inference never ran; partial evidence remains unaccepted.']
    lines += report['problems']
    prefix.with_suffix('.md').write_text('\n'.join(lines)+'\n')


if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--directory',type=Path,required=True);p.add_argument('--source',required=True)
    p.add_argument('--output-prefix',type=Path,required=True);a=p.parse_args()
    report=summarize(a.directory,a.source);write_reports(report,a.output_prefix)
    raise SystemExit(0 if report['complete'] else 1)
