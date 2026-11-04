#!/usr/bin/env python3
"""Download original OpenAI 2019 hide-and-seek game-agent policies for research.

The public example policies were trained in the multi-agent environment.
They are NOT Android checkpoints or directly runnable LocalPilot PC models.
Requires Python 3.9+ and internet access; verifies every blob against its
recorded original Git SHA-1. Use as an archival reference and training study.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import tempfile
import urllib.parse
import urllib.request

OWNER="openai/multi-agent-emergence-environments"
REV="bafaf1e11e6398624116761f91ae7c93b136f395"
RAW=f"https://raw.githubusercontent.com/{OWNER}/{REV}/"
FILES={
 "examples/hide_and_seek_full.npz":"0410630644426cb832c702d11013550d25f5c1c4",
 "examples/hide_and_seek_quadrant.npz":"d397441c82aacb8469f66549d9cb67a7a93985c6",
 "examples/hide_and_seek_quadrant_physics_exploits.npz":"1f7165128817a5678a3e5cb4cf5b211f927c7afc",
 "examples/blueprint.npz":"e063d352cab34944464f2fc75feab0d1c9e95798",
 "examples/lock_and_return.npz":"ef38a47c8bc41123892659e6d137b8c76223868b",
 "examples/sequential_lock.npz":"9f4b7eecb09134493561943ba0569ac618ef13ce",
 "examples/shelter.npz":"9248e54664a1275e789c159de0f0fc40bb8145f4",
 "examples/hide_and_seek_policy_phases/a_chasing.npz":"9cc630b7995fad11c14a64f69f50a67981e8401f",
 "examples/hide_and_seek_policy_phases/b_forts.npz":"e05444dded8275a54afbf8563a1865bf5ca3609c",
 "examples/hide_and_seek_policy_phases/c_ramps.npz":"15912c4ad1fc49a3b4033af269556239fcaeef2a",
 "examples/hide_and_seek_policy_phases/d_ramp_defense.npz":"63314af26410f0205a6199044fab1e259983d416",
 "examples/hide_and_seek_policy_phases/e_box_surfing.npz":"162d1ca975b5ee0f78c90fbcf8b18fa412b6cd6c",
}
CONFIGS={
 "examples/hide_and_seek_full.jsonnet":"0a21133daf956dfed6635581a15f9dbd460ef35a",
 "examples/hide_and_seek_quadrant.jsonnet":"4882af7e4a3806b8d2afe2bb0b58ecdbbd367a92",
}
LICENCE={"LICENSE":"cb36e12eef74cc696f4a56a0d5f93afa508eb57e"}
MAX_BYTES=32*1024*1024

def git_blob_sha(data):
    return hashlib.sha1(b"blob "+str(len(data)).encode("ascii")+bytes([0])+data).hexdigest()

def get(path):
    req=urllib.request.Request(RAW+urllib.parse.quote(path,safe="/"),
                               headers={"User-Agent":"LocalPilot-Research-Archive/1.0"})
    with urllib.request.urlopen(req,timeout=60) as response:
        data=response.read(MAX_BYTES+1)
    if len(data)>MAX_BYTES:raise ValueError("oversized file: "+path)
    return data

def fetch_checked(output,path,sha,fetcher=get):
    if not path.startswith(("examples/","LICENSE")) or ".." in Path(path).parts:
        raise ValueError("unsafe asset path")
    out=output/path
    if out.is_file() and git_blob_sha(out.read_bytes())==sha:
        data=out.read_bytes()
        return {"status":"verified_existing","path":path,"sha256":hashlib.sha256(data).hexdigest()}
    data=fetcher(path)
    if git_blob_sha(data)!=sha:
        raise ValueError("original SHA mismatch for "+path)
    out.parent.mkdir(parents=True,exist_ok=True)
    handle,tmp=tempfile.mkstemp(dir=str(out.parent),prefix=".verified-")
    try:
        with os.fdopen(handle,"wb") as file:
            file.write(data);file.flush();os.fsync(file.fileno())
        os.replace(tmp,out)
    finally:
        if os.path.exists(tmp):os.unlink(tmp)
    return {"status":"downloaded_verified","path":path,"sha256":hashlib.sha256(data).hexdigest()}

def main(argv=None):
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output",default="hide-seek-video-models")
    parser.add_argument("--list",action="store_true")
    args=parser.parse_args(argv)
    if args.list:
        print(json.dumps({"count":len(FILES),"policies":list(FILES),
          "source":f"https://github.com/{OWNER}/tree/{REV}"},indent=2))
        return
    output=Path(args.output).resolve()
    output.mkdir(parents=True,exist_ok=True)
    records=[]
    for path,sha in (list(FILES.items())+list(CONFIGS.items())+list(LICENCE.items())):
        record=fetch_checked(output,path,sha)
        records.append(record)
        print(record["status"],path)
    manifest={"schema":"localpilot-openai-hide-seek-2019-v1",
      "source_repo":OWNER,"source_commit":REV,"license":"MIT",
      "trained_example_policies":len(FILES),"assets":records,
      "usage":"research-only; requires original mujoco-worldgen environment and action/observation adapters"}
    (output/"MODEL_INDEX.json").write_text(
      json.dumps(manifest,indent=2,ensure_ascii=False)+chr(10),encoding="utf-8")
    print("Verified",len(FILES),"policy checkpoints. Original files preserved.")

if __name__=="__main__":
    main()
