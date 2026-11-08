#!/usr/bin/env python3
"""Script-only NES Agent Lab: verified JSNES runtime, homebrew ROM, visual training.

No Android SDK, phone connection, or real game authority is needed.
Python 3.10+, Node 22+, Java 17+, npm. Internet only to download pinned jsnes.
"""
import argparse
import base64
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import sys
import tarfile

ROOT = Path(__file__).resolve().parents[1]
SRI_SHA512 = "LUa++E5Dfs1A6ZYB5HiSOR4JrUzrZEXGLZ5Imczhu9lkg7f+94SqnLZ/MXwGa7oc02+j/xYyHR0azGQ6LCU68g=="


def run(args, cwd=None):
    print("+", " ".join(map(str, args)), flush=True)
    subprocess.run(list(map(str, args)), cwd=cwd or ROOT, check=True, timeout=300)


def core_from_npm(directory):
    npm = shutil.which("npm")
    if npm is None:
        raise RuntimeError("npm required: install Node.js 22+")
    cache = directory / "jsnes-2.1.0.tgz"
    if not cache.exists():
        run([npm, "pack", "jsnes@2.1.0", "--ignore-scripts", "--silent"], cwd=directory)
    raw = cache.read_bytes()
    if len(raw) > 5 * 1024 * 1024:
        raise RuntimeError("NES dependency too large")
    actual = base64.b64encode(hashlib.sha512(raw).digest()).decode("ascii")
    if actual != SRI_SHA512:
        raise RuntimeError("Pinned JSNES 2.1.0 SHA512 mismatch; refusing this NES core")
    target = directory / "jsnes.min.js"
    with tarfile.open(cache, "r:gz") as archive:
        source = archive.extractfile("package/dist/jsnes.min.js")
        if source is None:
            raise RuntimeError("Pinned NES package lacks JS runtime")
        contents = source.read(2 * 1024 * 1024 + 1)
        if len(contents) > 2 * 1024 * 1024:
            raise RuntimeError("NES emulator JavaScript exceeds size limit")
        target.write_bytes(contents)
    return target


def build_rom(directory):
    javac, java = shutil.which("javac"), shutil.which("java")
    if not javac or not java:
        raise RuntimeError("Java 17+ JDK required (javac and java)")
    classes = directory / "classes"
    classes.mkdir(exist_ok=True)
    run([javac, "--release", "17", "-d", classes,
         ROOT / "game-lab/src/main/java/org/localpilot/gamelab/HomebrewNes.java",
         ROOT / "tests/ExportHomebrewNes.java"])
    rom = directory / "homebrew.nes"
    run([java, "-cp", classes, "ExportHomebrewNes", rom])
    if rom.stat().st_size != 24592:
        raise RuntimeError("Generated homebrew iNES cartridge length is invalid")
    return rom


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=("smoke", "train"))
    parser.add_argument("--output", default="build/nes-agent-lab")
    parser.add_argument("--episodes", type=int, default=160)
    args = parser.parse_args()
    if not (10 <= args.episodes <= 1000):
        parser.error("--episodes must be 10..1000")
    base = Path(args.output).expanduser()
    if not base.is_absolute():
        base = ROOT / base
    base = base.resolve()
    base.mkdir(parents=True, exist_ok=True)
    core = core_from_npm(base)
    rom = build_rom(base)
    node = shutil.which("node")
    if not node:
        raise RuntimeError("Node.js 22+ required")
    run([node, ROOT / "tests/jsnes_nes_smoke.cjs", core, rom])
    if args.action == "smoke":
        print("NES_CPU_PPU_SMOKE_PASS")
        return
    run([node, ROOT / "tests/nes_training_contract.cjs", core, rom])
    checkpoint_dir = base / "learned"
    run([node, ROOT / "tools/nes-agent/train.cjs", "--core", core,
         "--rom", rom, "--output", checkpoint_dir, "--episodes", args.episodes])
    checkpoint = json.loads((checkpoint_dir / "nes-policy.json").read_text())
    if (checkpoint.get("source") != "NES_EMULATED" or
        checkpoint.get("eligible_for_market_reward") is not False or
        checkpoint.get("android_input_authorized") is not False or
        checkpoint.get("rom_sha256") != hashlib.sha256(rom.read_bytes()).hexdigest()):
        raise RuntimeError("NES checkpoint is invalid or provenance is contaminated")
    print("NES_SCRIPT_TRAINING_PASS", json.dumps({
        "policy": str(checkpoint_dir / "nes-policy.json"),
        "episodes": str(checkpoint_dir / "nes-episodes.jsonl"),
        "learned": checkpoint["evaluation"]["learned"],
        "baseline": checkpoint["evaluation"]["baseline"],
        "provenance": "NES_EMULATED",
    }, ensure_ascii=False))


if __name__ == "__main__":
    try:
        main()
    except (OSError, RuntimeError, subprocess.CalledProcessError,
            subprocess.TimeoutExpired, ValueError, tarfile.TarError, KeyError) as exc:
        print("NES_SCRIPT_FAILED:", exc, file=sys.stderr)
        raise SystemExit(1)
