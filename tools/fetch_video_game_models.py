#!/usr/bin/env python3
"""Fetch ORIGINAL released game-model weights from the Weight-Space Programming video.

No models are invented or re-trained. Downloads are checked against the Git
blob hashes in a pinned upstream commit, plus explicit checksums for the nine
published NPZ actor archives. Pure Python 3.9+; no external dependencies.

Usage:
  py tools/fetch_video_game_models.py --output D:/LocalPilot/models/weightspace-video
  py tools/fetch_video_game_models.py --groups minatar_astra grid_ppo --output ./video-models
  py tools/fetch_video_game_models.py --list
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import sys
import tempfile
import urllib.parse
import urllib.request

SOURCE_REPO = "RikiyaT/lm_understand-logs"
SOURCE_SHA = "4d09ba6d6a7e891dd8063974dfe81b3d78e3494f"
SOURCE_URL = "https://github.com/" + SOURCE_REPO + "/tree/" + SOURCE_SHA
API_TREE = "https://api.github.com/repos/" + SOURCE_REPO + "/git/trees/" + SOURCE_SHA + "?recursive=1"
RAW_BASE = "https://raw.githubusercontent.com/" + SOURCE_REPO + "/" + SOURCE_SHA + "/"

# All nine NPZ files independently verified to exist with their Git blob IDs.
NPZ_SHA = {
 "exp1_which_game/networks/a9f535cc.npz": "e90d977856dc261cfcc061e51bd3bb8fe2af079f",
 "exp1_which_game/networks/dfc6cb65.npz": "db66952ba50ab093ccccac3515e15d01552524ae",
 "exp1_which_game/networks/bdf99279.npz": "df6402349b186541dd1c651f89b9a0fa14ae8d68",
 "exp1_which_game/networks/0bbcc3ea.npz": "e6df56d1d2f7705a33ada2f0cf98958957f11f90",
 "exp4_fix_car/networks/ppo_final_seed0.npz": "0ed8c549357d5bcb017f083b136d999e7948c1b2",
 "exp4_fix_car/networks/edited_actor.npz": "52487c5c622087395f5b22a28e42ff30f83dff26",
 "exp5_write_from_scratch/networks/breakout/hand.npz": "5748e7a1cf34cc41a8b85c0a2cf118f79748593f",
 "exp5_write_from_scratch/networks/seaquest/hand.npz": "743b420fd11883e1db78fad191bbd8000e16f2f0",
 "exp5_write_from_scratch/networks/asterix/hand.npz": "6d290c54c80bff0e7359afd630ec7cb3e6311ee8",
}
GRID_PPO = (
 "exp1_which_game/networks/a9f535cc.npz",
 "exp1_which_game/networks/dfc6cb65.npz",
 "exp1_which_game/networks/bdf99279.npz",
 "exp1_which_game/networks/0bbcc3ea.npz",
)
KINETIX = (
 "exp4_fix_car/networks/ppo_final_seed0.npz",
 "exp4_fix_car/networks/edited_actor.npz",
)
MINATAR = tuple(p for p in NPZ_SHA if p.startswith("exp5_write_from_scratch/"))

GO_DIRS = {
 "go_astra": tuple("exp6_go_from_scratch/networks/budget_" + str(k) + "/" for k in (0,10,25,50)),
 "go_ppo": tuple("exp6_go_from_scratch/networks/ppo_same_size_lr3e-5_s" + str(k) + "/" for k in (0,1,2)),
}
GO_FILES = ("arch.json",) + tuple(
 f"{layer}.{suffix}.npy" for layer in ("L1","L2","L3","L4","L5","L6","out")
 for suffix in ("W","b")
)
GROUPS = ("grid_ppo","kinetix","minatar_astra","go_astra","go_ppo")
MAX_FILE = 18 * 1024 * 1024


def git_blob(data: bytes) -> str:
    return hashlib.sha1(b"blob " + str(len(data)).encode("ascii") + b"\\0" + data).hexdigest()


def request_bytes(url: str, max_bytes: int) -> bytes:
    req = urllib.request.Request(url,headers={"User-Agent":"LocalPilot-Research-Model-Downloader/1.0",
                                                "Accept":"application/vnd.github+json"})
    with urllib.request.urlopen(req,timeout=60) as response:
        data = response.read(max_bytes+1)
    if len(data)>max_bytes:
        raise ValueError("Upstream payload exceeds limit: "+url)
    return data


def get_tree() -> dict:
    data=json.loads(request_bytes(API_TREE,12*1024*1024))
    if data.get("truncated"):
        raise ValueError("Upstream git tree truncated; refusing unverified files")
    return {x["path"]:x for x in data.get("tree",[])
            if x.get("type")=="blob" and isinstance(x.get("sha"),str)}


def list_assets(tree: dict,selected) -> list:
    if selected==("all",) or selected==["all"]:
        selected=GROUPS
    if not selected or any(x not in GROUPS for x in selected):
        raise ValueError("Unsupported model group")
    models=[]
    for group in selected:
        if group=="grid_ppo":paths=GRID_PPO
        elif group=="kinetix":paths=KINETIX
        elif group=="minatar_astra":paths=MINATAR
        else:paths=tuple(prefix+suffix for prefix in GO_DIRS[group] for suffix in GO_FILES)
        for path in paths:
            obj=tree.get(path)
            if not obj or not re.fullmatch("[0-9a-f]{40}",obj["sha"]):
                raise ValueError("Published model missing: "+path)
            if not re.fullmatch("[A-Za-z0-9_./-]+",path) or ".." in Path(path).parts:
                raise ValueError("Unsafe source path")
            if path in NPZ_SHA and obj["sha"]!=NPZ_SHA[path]:
                raise ValueError("Pinned original model differs: "+path)
            if obj.get("size",0)>MAX_FILE:
                raise ValueError("Oversized model file: "+path)
            models.append((group,path,obj["sha"],obj.get("size",0)))
    return models


def save_one(out: Path,path: str,expected_sha: str,expected_size: int) -> dict:
    target=out/path
    target.parent.mkdir(parents=True,exist_ok=True)
    if target.is_file():
        content=target.read_bytes()
        if git_blob(content)==expected_sha:
            return {"path":path,"git_sha1":expected_sha,"sha256":hashlib.sha256(content).hexdigest(),
                    "size":len(content),"status":"already_verified"}
    quoted=urllib.parse.quote(path,safe="/")
    content=request_bytes(RAW_BASE+quoted,MAX_FILE)
    if (git_blob(content)!=expected_sha or
       expected_size not in (None,0,len(content))):
        raise ValueError("MODEL INTEGRITY ERROR: "+path)
    fd,temp=tempfile.mkstemp(dir=str(target.parent),prefix=".verify-")
    try:
        with os.fdopen(fd,"wb") as stream:
            stream.write(content);stream.flush();os.fsync(stream.fileno())
        os.replace(temp,target)
    finally:
        if os.path.exists(temp):os.remove(temp)
    return {"path":path,"git_sha1":expected_sha,"sha256":hashlib.sha256(content).hexdigest(),
            "size":len(content),"status":"downloaded_verified"}


def main(argv=None):
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output",default="video-game-models",help="Local folder for original weights")
    parser.add_argument("--groups",nargs="+",choices=("all",)+GROUPS,default=["all"])
    parser.add_argument("--list",action="store_true",help="Only print pinned model families")
    args=parser.parse_args(argv)
    if args.list:
        print(json.dumps({"source":SOURCE_URL,"groups":GROUPS,
                          "game_models":{"grid_ppo":4,"kinetix":2,"minatar_astra":3,
                                         "go_astra":4,"go_ppo":3},
                          "pinned_revision":SOURCE_SHA},indent=2))
        return
    tree=get_tree()
    chosen=list_assets(tree,args.groups)
    target=Path(args.output).resolve()
    target.mkdir(parents=True,exist_ok=True)
    entries=[]
    for idx,(_,path,blob,size) in enumerate(chosen,1):
        result=save_one(target,path,blob,size)
        entries.append(result)
        print(f"[{idx}/{len(chosen)}] {result['status']}: {path}")
    # Keep original CC-BY license and attribution alongside all downloaded assets.
    license_path="LICENSE"
    obj=tree.get(license_path)
    if not obj:raise ValueError("Upstream license missing")
    save_one(target,license_path,obj["sha"],obj.get("size",0))
    manifest={"catalog":"MIT-CSAIL-video-game-models-v1","source":SOURCE_URL,
              "upstream_commit":SOURCE_SHA,"models":entries,
              "selected_groups":list(args.groups),
              "note":"Original checkpoints only. MinAtar/Go Astra networks were hand-programmed, not PPO-trained. No automatic Android/PC control."}
    manifest_path=target/"MODEL_INDEX.json"
    manifest_path.write_text(json.dumps(manifest,indent=2,ensure_ascii=False)+"\\n",encoding="utf-8")
    print("Verified",len(chosen),"model files; index:",manifest_path)


if __name__=="__main__":
    try:main()
    except (OSError,ValueError,KeyError,urllib.error.URLError) as error:
        print("Download failed safely:",error,file=sys.stderr)
        sys.exit(2)
