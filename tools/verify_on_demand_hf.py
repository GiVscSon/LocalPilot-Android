#!/usr/bin/env python3
"""Lightweight CI gate for exact HF GGUF links used by the ~18 MB ModelLab APK.

No model weights are downloaded. Queries the immutable HF LFS metadata, sends
HEAD on direct URLs and checks the final redirect host against Android policy.
Any upstream repo revision or content mismatch fails closed.
"""
from __future__ import annotations
import json
import sys
from pathlib import Path
from urllib.parse import urlparse
from urllib.request import Request,urlopen

sys.path.insert(0,str(Path(__file__).resolve().parent))
import bundle_embedded_gguf as pocket
import bundle_optional_models as optional

ALLOWED={
 "huggingface.co","hf.co","cdn-lfs.huggingface.co",
 "cdn-lfs-us-1.hf.co","cas-bridge.xethub.hf.co",
 "transfer.xethub.hf.co","cas-server.xethub.hf.co",
 "us.aws.cdn.hf.co"
}
SOURCE=Path("model-lab/src/main/java/org/localpilot/android/ModelLabPresetCatalog.java").read_text()

def verify(repo,revision,name,expected_bytes,expected_sha):
    base="https://huggingface.co/"
    url=base+repo+"/resolve/"+revision+"/"+name
    if name not in SOURCE or expected_sha not in SOURCE or revision not in SOURCE:
        raise RuntimeError("Android catalogue differs from trusted asset "+name)
    req=Request(url,method="HEAD",headers={"User-Agent":"LocalPilot-ModelLab-OnDemandCI/1.0"})
    with urlopen(req,timeout=45) as reply:
        if reply.status!=200:raise RuntimeError("HEAD status invalid: "+url)
        final=urlparse(reply.geturl())
        if final.scheme!="https" or final.hostname not in ALLOWED:
            raise RuntimeError("Android cannot trust HF redirect host for "+name+": "+repr(final.hostname))
        # Dynamic Hugging Face CDN may omit Content-Length. When present, enforce it.
        advertised=reply.headers.get("Content-Length")
        if advertised is not None and int(advertised)!=expected_bytes:
            raise RuntimeError("CDN HEAD content length differs: "+name)
    print("HF_PINNED_GGUF_URL_OK",name,expected_bytes,expected_sha,flush=True)

def main():
    root=pocket.REPO
    rev=pocket.REVISION
    with pocket.request(pocket.BASE+"/api/models/"+root+"/tree/"+rev) as stream:
        rows=json.load(stream)
    entry=next((row for row in rows if row.get("path")==pocket.NAME),None)
    if (not entry or entry.get("size")!=pocket.EXPECTED_BYTES or
        entry.get("lfs",{}).get("oid")!=pocket.EXPECTED_SHA256):
        raise RuntimeError("MNN Pocket pinned LFS asset missing/mutated")
    verify(root,rev,pocket.NAME,pocket.EXPECTED_BYTES,pocket.EXPECTED_SHA256)
    snapshots={}
    for _,repo,name,_ in optional.EXTRAS:
        if repo not in snapshots:
            snapshots[repo]=optional.repo_snapshot(repo)
        rev,tree=snapshots[repo]
        meta=tree.get(name,{})
        size,digest=optional.PINNED_FILES[name]
        if meta.get("size")!=size or meta.get("lfs",{}).get("oid")!=digest:
            raise RuntimeError("Pinned GGUF LFS metadata changed: "+name)
        verify(repo,rev,name,size,digest)
    print("MODELLAB_FIVE_PINNED_HF_LINKS_PASS")

if __name__=="__main__":
    main()
