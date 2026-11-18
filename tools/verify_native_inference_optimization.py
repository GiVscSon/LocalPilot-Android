#!/usr/bin/env python3
"""Audit actual compiler commands for the JNI and ggml tensor kernels."""
import argparse
import hashlib
import json
from pathlib import Path
import shlex

TARGETS={'localpilot_embedded.cpp','ggml.c','ggml-cpu.c'}

def verify(rows):
    found=set();count=0
    for row in rows:
        source=Path(row['file']).name
        if source not in TARGETS:continue
        args=row.get('arguments') or shlex.split(row['command'])
        optimizations=[x for x in args if x in ('-O0','-O1','-O2','-O3','-Os','-Oz','-Og','-Ofast')]
        if not optimizations or optimizations[-1] not in ('-O2','-O3'):
            raise ValueError('Unoptimized native inference source: '+source)
        if any(x in args for x in ('-Ofast','-ffast-math','-march=native')):
            raise ValueError('Unreviewed arithmetic or host-specific optimization: '+source)
        found.add(source);count+=1
    if found!=TARGETS:raise ValueError('Missing actual JNI/ggml compiler commands: '+','.join(sorted(TARGETS-found)))
    return dict(result='NATIVE_COMPILER_OPTIONS_PASS',sources=sorted(found),commands=count,
                gamePerformanceMeasured=False)

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--root',type=Path,required=True);p.add_argument('--abi',required=True)
    p.add_argument('--output',type=Path,required=True);a=p.parse_args()
    files=sorted(x for x in a.root.rglob('compile_commands.json') if a.abi in x.parts)
    if not files:raise SystemExit('No actual compiler command database for '+a.abi)
    rows=[]
    for path in files:rows.extend(json.loads(path.read_text()))
    report=verify(rows);report['abi']=a.abi
    report['databases']=[dict(path=str(x),sha256=hashlib.sha256(x.read_bytes()).hexdigest()) for x in files]
    a.output.parent.mkdir(parents=True,exist_ok=True)
    a.output.write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report))
