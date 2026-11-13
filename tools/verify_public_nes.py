#!/usr/bin/env python3
"""Fetch ONLY an author's MIT-licensed pinned ROM for NES CI, never commercial titles."""
from pathlib import Path
from urllib.request import Request, urlopen
import hashlib
import sys

SOURCE="https://github.com/Riyoway/void-lancer-nes/releases/download/v1.0.0/VOID-LANCER-v1.0.0.nes"
LENGTH=40976
SHA256="5eb801aca68131a3d17fa82d8901d46f6098b878d1e00cd1c47d00c001c2405a"

def main():
    if len(sys.argv)!=2: raise SystemExit("usage: verify_public_nes.py OUTPUT.nes")
    output=Path(sys.argv[1])
    request=Request(SOURCE,headers={"User-Agent":"LocalPilot-GameLab-ROM-Acceptance/1.0"})
    with urlopen(request,timeout=50) as stream:
        data=stream.read(LENGTH+1)
    if len(data)!=LENGTH or data[:4]!=b"NES\\x1a".replace(b"\\x1a",bytes([26])):
        raise ValueError("upstream ROM truncated / not iNES")
    got=hashlib.sha256(data).hexdigest()
    if got!=SHA256: raise ValueError("author's ROM SHA256 mismatch: "+got)
    output.parent.mkdir(parents=True,exist_ok=True)
    output.write_bytes(data)
    print("NES_PUBLIC_HOMEBREW_SHA256_PASS",len(data),got)

if __name__=="__main__":
    main()
