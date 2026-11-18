#!/usr/bin/env python3
"""Strict discovery/holdout accounting. Writes candidates, never activates a policy."""
import argparse
import json
from pathlib import Path
import statistics

MODELS=('qwen35','lfm350','lfm1200')

def discover(reports):
    found={};snapshots={};source=None;apk=None
    for report in reports:
        if report.get('result')!='DIAGNOSTICS_COMPLETED' or report.get('errors') or report.get('holdoutIndex') is not None:
            raise ValueError('Incomplete discovery evidence')
        identity=report['identity'];key=(identity['modelProfile'],report['variant'])
        if key in found or key[0] not in MODELS or key[1] not in 'ABCDE':raise ValueError('Duplicate/unknown discovery arm')
        if source is None:source=identity['sourceSha'];apk=identity['apkSha256']
        if identity['sourceSha']!=source or identity['apkSha256']!=apk:raise ValueError('Mixed source/APK')
        cases=report['cases']
        if len(cases)!=12:raise ValueError('Expected9 actual probes and3 episodes per arm')
        expected={(s,seed,False) for s in ('TITLE','PLAYING','THREAT') for seed in (42,1042,2042)}|{('TITLE',seed,True) for seed in (42,1042,2042)}
        if {(c['snapshot'],c['seed'],c['episode']) for c in cases}!=expected or any(c['variant']!=key[1] for c in cases):
            raise ValueError('Duplicated/missing discovery case')
        for case in cases:
            name=case['snapshot'];sha=case['snapshotSha256']
            if name in snapshots and snapshots[name]!=sha:raise ValueError('Different initial states between arms/models')
            snapshots[name]=sha
        episodes=[c for c in cases if c['episode']]
        if len(episodes)!=3 or {c['seed'] for c in episodes}!={42,1042,2042}:raise ValueError('Incomplete independent discovery seeds')
        found[key]=dict(successC=sum(c['C'] for c in episodes),medianLivesLost=statistics.median(c['livesLost'] for c in episodes),
                        medianScore=statistics.median(c['scoreDelta'] for c in episodes),episodes=episodes)
    if set(found)!={(m,v) for m in MODELS for v in 'ABCDE'}:raise ValueError('Missing model/variant from45 real episodes')
    selections=[];matrix=[]
    def rank(value):return (value['successC'],-value['medianLivesLost'],value['medianScore'])
    for model in MODELS:
        # D/E are diagnostic controls only; production keeps phase grammar.
        best='A'
        for variant in 'BC':
            if rank(found[model,variant])>rank(found[model,best]):best=variant
        # Fewer lost lives with no verified goal event is not gameplay improvement.
        # Do not spend 20 held-out runs on a candidate with zero real progress.
        if best!='A' and found[model,best]['successC']==0:best='A'
        selections.append(dict(model=model,variant=best,ranking={v:found[model,v] for v in 'ABCDE'}))
        if best!='A':matrix.extend(dict(model=model,variant=best,pair=i) for i in range(20))
    return dict(result='DISCOVERY_COMPLETE',sourceSha=source,apkSha256=apk,discoverySnapshots=snapshots,selections=selections,
                holdoutMatrix=dict(include=matrix),hasCandidates=bool(matrix),promotion='BLOCKED_PENDING_HOLDOUT',weightsTrained=False)

def evaluate(discovery,reports):
    expected={(s['model'],i):s['variant'] for s in discovery['selections'] if s['variant']!='A' for i in range(20)}
    found={};snapshot_by_pair={};identity=(discovery['sourceSha'],discovery['apkSha256'])
    for report in reports:
        key=(report['identity']['modelProfile'],report.get('holdoutIndex'))
        if key not in expected or key in found or report.get('result')!='DIAGNOSTICS_COMPLETED' or report.get('errors'):
            raise ValueError('Missing, duplicate or invalid held-out pair')
        if (report['identity']['sourceSha'],report['identity']['apkSha256'])!=identity:raise ValueError('Held-out source/APK changed')
        pair=key[1];cases={c['variant']:c for c in report['cases']}
        variant=expected[key]
        if set(cases)!={'A',variant}:raise ValueError('Incomplete baseline/candidate pair')
        a,b=cases['A'],cases[variant]
        if (a['snapshot']!='HOLDOUT_'+str(pair) or a['snapshotSha256']!=b['snapshotSha256'] or
            a['seed']!=10042+1000*pair or b['seed']!=a['seed'] or not a['episode'] or not b['episode']):
            raise ValueError('Unpaired or reused selection state/seed')
        if a['snapshotSha256'] in discovery['discoverySnapshots'].values():raise ValueError('Selection state reused in holdout')
        if pair in snapshot_by_pair and snapshot_by_pair[pair]!=a['snapshotSha256']:raise ValueError('Holdout state differs between models')
        if any(i!=pair and s==a['snapshotSha256'] for i,s in snapshot_by_pair.items()):raise ValueError('Holdout states reused between pairs')
        snapshot_by_pair[pair]=a['snapshotSha256']
        found[key]=(a,b)
    if set(found)!=set(expected):raise ValueError('Missing held-out pair')
    results=[]
    for model in MODELS:
        pairs=[found[model,i] for i in range(20) if (model,i) in found]
        if not pairs:continue
        old=[a for a,b in pairs];new=[b for a,b in pairs]
        old_score=statistics.median(c['scoreDelta'] for c in old);new_score=statistics.median(c['scoreDelta'] for c in new)
        passed=(new_score>0 if old_score==0 else new_score>=old_score*1.05) and sum(c['C'] for c in new)>=sum(c['C'] for c in old) and statistics.median(c['livesLost'] for c in new)<=statistics.median(c['livesLost'] for c in old)
        results.append(dict(model=model,variant=new[0]['variant'],pairs=20,baselineMedianScore=old_score,candidateMedianScore=new_score,
                            baselineC=sum(c['C'] for c in old),candidateC=sum(c['C'] for c in new),result='IMPROVEMENT_VERIFIED' if passed else 'NO_PROVEN_IMPROVEMENT'))
    return dict(result='HOLDOUT_EVALUATION_COMPLETE',policies=results,automaticallyActivated=False,weightsTrained=False,physicalDevice=False,releaseReady=False)

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('mode',choices=['discovery','holdout']);p.add_argument('--reports',type=Path,required=True)
    p.add_argument('--discovery',type=Path);p.add_argument('--output',type=Path,required=True);p.add_argument('--github-output',type=Path);a=p.parse_args()
    reports=[json.loads(path.read_text()) for path in sorted(a.reports.rglob('report.json'))]
    try:report=discover(reports) if a.mode=='discovery' else evaluate(json.loads(a.discovery.read_text()),reports)
    except Exception as error:report=dict(result='INCOMPLETE_EVIDENCE',error=str(error),hasCandidates=False,holdoutMatrix=dict(include=[]),weightsTrained=False)
    a.output.parent.mkdir(parents=True,exist_ok=True);a.output.write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report))
    if a.github_output:
        with a.github_output.open('a') as out:
            out.write('has_candidates='+str(report.get('hasCandidates',False)).lower()+'\n')
            out.write('matrix='+json.dumps(report.get('holdoutMatrix',dict(include=[])),separators=(',',':'))+'\n')
    if report['result']=='INCOMPLETE_EVIDENCE':raise SystemExit(1)

if __name__=='__main__':main()
