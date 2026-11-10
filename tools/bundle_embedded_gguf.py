#!/usr/bin/env python3
"""Prepare and independently verify bundled MNN-Pocket GGUF from a single HF revision."""
import argparse
import hashlib
import json
import os
import pathlib
import tempfile
import urllib.request
import zipfile

REPO = "taobao-mnn/MNN-Pocket-0.3B-GGUF"
NAME = "MNN-Pocket-0.3B-Instruct-Q4_1.gguf"
BASE = "https://huggingface.co"
ROOT = pathlib.Path("build/embedded-model-assets")
REPORT = pathlib.Path("build/reports/embedded-gguf.json")
ASSET = "assets/models/" + NAME

def request(url):
    req = urllib.request.Request(url, headers={"User-Agent": "LocalPilot/embedded-offline-apk"})
    return urllib.request.urlopen(req, timeout=120)

def sha_stream(stream, output=None, limit=450_000_000):
    digest = hashlib.sha256()
    size = 0
    while True:
        chunk = stream.read(1024 * 1024)
        if not chunk:
            break
        size += len(chunk)
        if size > limit:
            raise ValueError("Model exceeds bound")
        digest.update(chunk)
        if output:
            output.write(chunk)
    return digest.hexdigest(), size

def prepare():
    info = json.load(request(f"{BASE}/api/models/{REPO}"))
    revision = info["sha"]
    assert len(revision) == 40 and all(c in "0123456789abcdef" for c in revision)
    tree = json.load(request(f"{BASE}/api/models/{REPO}/tree/{revision}"))
    entry = next((x for x in tree if x["path"] == NAME), None)
    if not entry or not entry.get("lfs") or not entry["lfs"].get("oid"):
        raise ValueError("Pinned LFS sha256 unavailable: refuse unverified model")
    expected = entry["lfs"]["oid"]
    assert len(expected) == 64 and all(c in "0123456789abcdef" for c in expected)
    outpath = ROOT / "models" / NAME
    outpath.parent.mkdir(parents=True, exist_ok=True)
    url = f"{BASE}/{REPO}/resolve/{revision}/{NAME}"
    fd, temp_path = tempfile.mkstemp(prefix="gguf-", dir=outpath.parent)
    try:
        with os.fdopen(fd, "wb") as out, request(url) as inp:
            actual, size = sha_stream(inp, out)
            out.flush()
            os.fsync(out.fileno())
        if actual != expected or size != entry["size"]:
            raise ValueError("Downloaded GGUF does not match official LFS digest/size")
        with open(temp_path, "rb") as f:
            if f.read(4) != b"GGUF":
                raise ValueError("Downloaded file is not GGUF")
        os.replace(temp_path, outpath)
    finally:
        if os.path.exists(temp_path):
            os.unlink(temp_path)
    # Include model license and attribution inside the installed APK.
    license_bytes = request(f"{BASE}/{REPO}/resolve/{revision}/LICENSE.txt").read(100_000)
    assert b"Apache License" in license_bytes
    licenses = ROOT / "licenses"
    licenses.mkdir(exist_ok=True)
    (licenses / "MNN-Pocket-LICENSE.txt").write_bytes(license_bytes)
    report = {"source": REPO, "revision": revision, "filename": NAME,
              "sha256": actual, "bytes": size, "format": "GGUF", "license": "Apache-2.0"}
    REPORT.parent.mkdir(parents=True, exist_ok=True)
    REPORT.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n")
    (ROOT / "model-manifest.json").write_text(json.dumps(report, sort_keys=True) + "\n")
    print("VERIFIED_MODEL", report)

def verify(apk_path):
    info = json.loads(REPORT.read_text())
    with zipfile.ZipFile(apk_path) as apk:
        actual_files = set(apk.namelist())
        assert ASSET in actual_files, "GGUF missing from APK"
        assert "lib/arm64-v8a/liblocalpilot_embedded.so" in actual_files, "native engine missing"
        assert "assets/licenses/MNN-Pocket-LICENSE.txt" in actual_files, "model license missing"
        assert "assets/licenses/llama.cpp-LICENSE.txt" in actual_files, "runtime license missing"
        assert apk.getinfo(ASSET).compress_type == zipfile.ZIP_STORED, "GGUF should be uncompressed"
        with apk.open(ASSET) as model:
            sha, size = sha_stream(model)
        assert sha == info["sha256"] and size == info["bytes"], "APK model digest mismatch"
    print("BUNDLED_APK_VERIFIED", str(apk_path), sha, size)

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("action", choices=["prepare", "verify"])
    parser.add_argument("apk", nargs="?")
    args = parser.parse_args()
    if args.action == "prepare":
        prepare()
    elif args.apk:
        verify(pathlib.Path(args.apk))
    else:
        parser.error("verify requires APK file")
