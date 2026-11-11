#!/usr/bin/env python3
"""Bundle optional GGUF models as verified, offline APK assets.

Only used for -PbundleLocalModelCatalog=true. Download a single immutable
HF snapshot per repo. Every file is checked against its snapshot LFS oid and
size before it is added to the signed APK; model code cannot fetch at runtime.

NOTE: revision resolution uses the latest public snapshot at build time.
The immutable commit id, bytes and digest are written to the artifact report.
Promotion to a reproducible release requires moving those values into a
reviewed lock file. This is a development candidate, not a stable release.
"""
from __future__ import annotations
import hashlib
import json
import os
from pathlib import Path
import tempfile
import urllib.parse
import urllib.request
import zipfile

BASE = "https://huggingface.co"
ROOT = Path("build/embedded-model-assets")
REPORT = Path("build/reports/embedded-extra-models.json")
ASSET_MANIFEST = ROOT / "model-catalog.json"
MAX_FILE = 900_000_000
MAX_TOTAL = 1_700_000_000
# Locked after the first *fully verified* ARM64 APK at GitHub Actions
# run 43028634955, SHA256 818b350cd1fe57776f41248a521c93e426b8b410feb2e68dea48906b744bbb63.
PINNED_REVISIONS = {
    "ggml-org/Qwen3.5-0.8B-GGUF": "807dfd9a6fadbf703207aac3bfb0b2b3040d077c",
    "LiquidAI/LFM2.5-350M-GGUF": "657e078c94084481950a2d555a941481f715536b",
    "LiquidAI/LFM2.5-VL-450M-GGUF": "1abed04b6fe71314d8c446a1371c03d7c332266d",
}
PINNED_FILES = {
    "Qwen3.5-0.8B-Q4_0.gguf": (563_036_064, "57d1997790d1744fba5b40a7317df71ea5e2acee28c47e78f0cce39c0703f8cf"),
    "LFM2.5-350M-Q4_0.gguf": (219_309_792, "85e32858daafad55b7bcd6b97a1343ee0661188e8036f9862d14d6b563142f50"),
    "LFM2.5-VL-450M-Q4_0.gguf": (219_311_264, "6d2757dd0f0b98aea7dc90477bb5b3a0df1089be85ef92943f8cecb05121ccbf"),
    "mmproj-LFM2.5-VL-450m-Q8_0.gguf": (102_815_168, "ebfc428baa37efad8bae93864f914b2634a09009f91ad59f974fe1a1565d8561"),
}
EXTRAS = (
    ("qwen35", "ggml-org/Qwen3.5-0.8B-GGUF", "Qwen3.5-0.8B-Q4_0.gguf", False),
    ("lfm350", "LiquidAI/LFM2.5-350M-GGUF", "LFM2.5-350M-Q4_0.gguf", False),
    ("lfmvl450", "LiquidAI/LFM2.5-VL-450M-GGUF", "LFM2.5-VL-450M-Q4_0.gguf", False),
    ("lfmvl450", "LiquidAI/LFM2.5-VL-450M-GGUF", "mmproj-LFM2.5-VL-450m-Q8_0.gguf", True),
)

def get(url):
    return urllib.request.urlopen(urllib.request.Request(
        url, headers={"User-Agent": "LocalPilot-Android-Verified-Model-Catalog/1.0"}), timeout=120)

def sha_stream(stream,output=None,limit=MAX_FILE):
    digest=hashlib.sha256()
    size=0
    while chunk:=stream.read(1024*1024):
        size+=len(chunk)
        if size>limit:
            raise ValueError("Oversized model")
        digest.update(chunk)
        if output:
            output.write(chunk)
    return digest.hexdigest(),size

def repo_snapshot(repository):
    revision=PINNED_REVISIONS.get(repository)
    if not revision or len(revision)!=40:
        raise ValueError("Unreviewed HF repository revision")
    with get(BASE+"/api/models/"+repository+"/tree/"+revision+"?recursive=true") as src:
        tree=json.load(src)
    return revision,{x.get("path"):x for x in tree if isinstance(x,dict)}

def download():
    metadata={}
    files=[]
    total=0
    for model_id,repo,name,projector in EXTRAS:
        if repo not in metadata: metadata[repo]=repo_snapshot(repo)
        rev,tree=metadata[repo]
        entry=tree.get(name)
        if not entry or not entry.get("lfs"):
            raise ValueError("Official LFS metadata missing for "+name)
        expected=entry["lfs"].get("oid")
        size=entry.get("size")
        if not isinstance(expected,str) or len(expected)!=64 or any(x not in "0123456789abcdef" for x in expected):
            raise ValueError("Invalid original SHA256 for "+name)
        if not isinstance(size,int) or not (128<size<MAX_FILE):
            raise ValueError("Unexpected weight size for "+name)
        if PINNED_FILES.get(name)!=(size,expected):
            raise ValueError("HF GGUF changed from reviewed exact SHA256/size: "+name)
        total+=size
        if total>MAX_TOTAL: raise ValueError("Optional models exceed APK budget")
        out=ROOT/"models"/name
        out.parent.mkdir(parents=True,exist_ok=True)
        fd,temp=tempfile.mkstemp(dir=out.parent,prefix="hf-")
        try:
            with os.fdopen(fd,"wb") as dest,get(
                BASE+"/"+repo+"/resolve/"+rev+"/"+urllib.parse.quote(name)) as src:
                got,nbytes=sha_stream(src,dest)
                dest.flush()
                os.fsync(dest.fileno())
            if got!=expected or nbytes!=size: raise ValueError("LFS mismatch: "+name)
            with open(temp,"rb") as src:
                if src.read(4)!=b"GGUF": raise ValueError("Not a GGUF file: "+name)
            os.replace(temp,out)
        finally:
            if os.path.exists(temp):os.unlink(temp)
        files.append({"id":model_id,"repository":repo,"revision":rev,"filename":name,
                      "sha256":got,"bytes":size,"projector":projector})
    lic=ROOT/"licenses"
    lic.mkdir(exist_ok=True)
    for repo,licname in (("Qwen/Qwen3.5-0.8B","Qwen3.5-LICENSE.txt"),
                         ("LiquidAI/LFM2.5-350M-GGUF","LFM2.5-350M-LICENSE.txt"),
                         ("LiquidAI/LFM2.5-VL-450M-GGUF","LFM2.5-VL-450M-LICENSE.txt")):
        rev=metadata.get(repo,(None,None))[0]
        url=BASE+"/"+repo+"/resolve/"+(rev or "main")+"/LICENSE"
        with get(url) as src: body=src.read(100_000)
        if len(body)<100 or not (b"License" in body or b"LICENSE" in body):
            raise ValueError("License attribution missing: "+repo)
        (lic/licname).write_bytes(body)
    result={"schema":"localpilot-extra-models-v1","source":"immutable-HF-LFS",
            "runtime":"offline-only","files":files,"total_bytes":total,
            "weights_revision_locked":True,"reproducible_release":False}
    REPORT.parent.mkdir(parents=True,exist_ok=True)
    REPORT.write_text(json.dumps(result,indent=2,sort_keys=True)+"\n")
    ASSET_MANIFEST.write_text(json.dumps(result,sort_keys=True)+"\n")
    print("OPTIONAL_MODELS_PREPARED files="+str(len(files))+" bytes="+str(total),flush=True)

def verify(apk):
    metadata=json.loads(REPORT.read_text())
    if metadata.get("schema")!="localpilot-extra-models-v1":
        raise ValueError("Invalid catalogue metadata")
    with zipfile.ZipFile(apk) as zipped:
        manifest=json.loads(zipped.read("assets/model-catalog.json"))
        if manifest!=metadata: raise ValueError("APK catalogue differs from verified build report")
        for row in metadata["files"]:
            path="assets/models/"+row["filename"]
            entry=zipped.getinfo(path)
            if entry.compress_type!=zipfile.ZIP_STORED:
                raise ValueError("GGUF was compressed: "+path)
            with zipped.open(path) as src:
                got,bytes_=sha_stream(src)
            if got!=row["sha256"] or bytes_!=row["bytes"]:
                raise ValueError("APK weight mismatch: "+path)
        for name in ("Qwen3.5-LICENSE.txt","LFM2.5-350M-LICENSE.txt","LFM2.5-VL-450M-LICENSE.txt"):
            zipped.getinfo("assets/licenses/"+name)
    print("OPTIONAL_MODELS_APK_VERIFIED files="+str(len(metadata["files"])),flush=True)

if __name__=="__main__":
    import argparse
    p=argparse.ArgumentParser()
    p.add_argument("command",choices=["prepare","verify"])
    p.add_argument("apk",nargs="?")
    arg=p.parse_args()
    if arg.command=="prepare":download()
    elif arg.apk:verify(arg.apk)
    else:p.error("verify requires APK")
