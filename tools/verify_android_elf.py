#!/usr/bin/env python3
"""Inspect actual ELF LOAD and APK data alignment; never claims a device run."""
import argparse
import hashlib
import json
from pathlib import Path
import struct
import zipfile

MACHINES={'arm64-v8a':183,'x86_64':62}
PAGE=16384


def inspect_elf(data, machine):
    if (len(data)<64 or len(data)>128*1024*1024 or data[:7]!=b'\x7fELF\x02\x01\x01' or
        struct.unpack_from('<HHI',data,16)!=(3,machine,1)):
        raise ValueError('Expected bounded little-endian Android ELF64 shared library')
    offset=struct.unpack_from('<Q',data,32)[0]
    ehsize,entrysize,count=struct.unpack_from('<HHH',data,52)
    if ehsize!=64 or entrysize!=56 or not 1<=count<=128 or offset<64 or offset+entrysize*count>len(data):
        raise ValueError('Invalid ELF program-header table')
    loads=[]
    for i in range(count):
        kind,flags,start,address,physical,size,memory,alignment=struct.unpack_from('<IIQQQQQQ',data,offset+entrysize*i)
        if kind!=1:continue
        if (alignment<PAGE or alignment&(alignment-1) or start%PAGE!=address%PAGE or
            start+size>len(data) or memory<size):
            raise ValueError('ELF LOAD segment is not 16KiB compatible')
        loads.append(alignment)
    if not loads:raise ValueError('ELF has no LOAD segments')
    return dict(elfMachine=machine,loadSegments=len(loads),minimumLoadAlignment=min(loads),
                sha256=hashlib.sha256(data).hexdigest())


def inspect_apk(path,abi='arm64-v8a'):
    if abi not in MACHINES:raise ValueError('Unsupported native ABI')
    if not path.is_file() or path.stat().st_size>128*1024*1024:raise ValueError('Missing/oversized APK')
    libraries=[]
    with zipfile.ZipFile(path) as archive,path.open('rb') as raw:
        infos=archive.infolist()
        if len(infos)>10000 or len({i.filename for i in infos})!=len(infos):raise ValueError('Invalid APK entry table')
        if sum(i.file_size for i in infos)>256*1024*1024 or archive.testzip() is not None:raise ValueError('APK CRC/quota failure')
        for info in infos:
            if not info.filename.startswith('lib/'+abi+'/') or not info.filename.endswith('.so'):continue
            elf=inspect_elf(archive.read(info),MACHINES[abi])
            raw.seek(info.header_offset);header=raw.read(30)
            if len(header)!=30 or header[:4]!=b'PK\x03\x04':raise ValueError('Invalid local ZIP header')
            name,extra=struct.unpack_from('<HH',header,26)
            data_offset=info.header_offset+30+name+extra
            if info.compress_type==zipfile.ZIP_STORED and data_offset%PAGE:
                raise ValueError('Stored native library is not ZIP aligned to16KiB')
            libraries.append(dict(path=info.filename,zipDataOffset=data_offset,
                extractedAtInstall=info.compress_type!=zipfile.ZIP_STORED,**elf))
    if not libraries:raise ValueError('No native library for '+abi)
    return dict(schema='localpilot-android-elf16k-v1',status='ELF_AND_ZIP_16K_PASS',abi=abi,
                apkSha256=hashlib.sha256(path.read_bytes()).hexdigest(),libraries=libraries,
                physicalDevice=False,runtimeAcceptance=False,releaseReady=False)


if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('apk',type=Path)
    p.add_argument('--abi',choices=tuple(MACHINES),default='arm64-v8a')
    p.add_argument('--report',type=Path,required=True)
    a=p.parse_args();result=inspect_apk(a.apk,a.abi)
    a.report.parent.mkdir(parents=True,exist_ok=True)
    a.report.write_text(json.dumps(result,indent=2)+'\n')
    print('ANDROID_ELF16K_PASS '+json.dumps(result))
