"""Bit-exact Java-subprocess vs Python/JPype replay of one Android RL core.

Run from the repository root:
    python training/verify_bit_parity.py

Requires a complete JDK 17+ and jpype1. This gate compares float32 wire
bits, not approximate numpy values. It does NOT prove physical Android GPU,
screen perception, rewards for Market games or device gesture behavior.
"""
from __future__ import annotations

import hashlib
import json
import os
from pathlib import Path
import struct
import subprocess
import sys
import tempfile
import zipfile

try:
    import jpype
except ImportError as exc:
    raise SystemExit("Install jpype1: python -m pip install jpype1") from exc

from build_core import build

ROOT = Path(__file__).resolve().parents[1]
SEED = 20261028
STEPS = 64
TRAIN_STEPS = 1040
GENRES = ("JUMPER", "PLATFORMER", "LANES", "JETPACK")


def java_tool(name: str) -> str:
    home = os.environ.get("JAVA_HOME")
    return str(Path(home) / "bin" / (name + (".exe" if os.name == "nt" else ""))) if home else name


def native_vectors(jar: Path, output: Path) -> dict[str, str]:
    source = ROOT / "tests/RlParityProbe.java"
    subprocess.run(
        [java_tool("javac"), "--release", "17", "-cp", str(jar),
         "-d", str(output), str(source)],
        check=True, cwd=ROOT,
    )
    out = subprocess.check_output(
        [java_tool("java"), "-cp", os.pathsep.join([str(jar), str(output)]),
         "org.localpilot.android.rl.RlParityProbe"],
        cwd=ROOT, text=True,
    )
    values: dict[str, str] = {}
    for line in out.strip().splitlines():
        parts = line.split()
        if len(parts) == 2:
            name, sha = parts
        elif len(parts) == 3 and parts[0] == "TRAINER":
            name, sha = f"TRAINER:{parts[1]}", parts[2]
        else:
            raise AssertionError(f"Unexpected Java probe output: {line!r}")
        if name in values or len(sha) != 64:
            raise AssertionError(f"Invalid or repeated Java fingerprint: {line!r}")
        values[name] = sha
    expected = set(GENRES) | {f"TRAINER:{genre}" for genre in GENRES}
    if set(values) != expected:
        raise AssertionError(f"Missing/unexpected Java fingerprints: {values!r}")
    return values


def jpype_vectors(jar: Path, output: Path) -> dict[str, str]:
    jpype.startJVM(classpath=[str(jar), str(output)], convertStrings=True)
    env_cls = jpype.JClass("org.localpilot.android.rl.ArcadeEnv")
    genre_cls = jpype.JClass("org.localpilot.android.rl.ArcadeEnv$Genre")
    trainer_cls = jpype.JClass("org.localpilot.android.rl.DqnTrainer")
    java_float = jpype.JClass("java.lang.Float")
    java_file = jpype.JClass("java.io.File")

    def raw(value) -> bytes:
        bits = int(java_float.floatToRawIntBits(jpype.JFloat(value)))
        return struct.pack(">I", bits & 0xFFFFFFFF)

    result: dict[str, str] = {}
    with tempfile.TemporaryDirectory(prefix="localpilot-rl-checkpoint-") as tmp:
        for name in GENRES:
            genre = genre_cls.valueOf(name)
            ordinal = int(genre.ordinal())
            env = env_cls(genre)
            env.reset(SEED + ordinal)
            trace = bytearray()

            for tick in range(STEPS):
                if env.terminated or env.truncated:
                    env.reset(SEED + 1000 + tick + ordinal * 100)
                action = (tick * 7 + 3) % len(env.actions())
                for value in env.observe():
                    trace.extend(raw(value))
                step = env.step(action)
                trace.extend(struct.pack(">i", action))
                trace.extend(raw(step.reward))
                trace.extend(raw(step.progress))
                trace.append(1 if step.terminated else 0)
                trace.append(1 if step.truncated else 0)
                trace.extend(struct.pack(">i", int(step.durationFrames)))
                for value in step.observation:
                    trace.extend(raw(value))

            expected_length = STEPS * (64 * 8 + 18)
            if len(trace) != expected_length:
                raise AssertionError(f"{name}: trace length {len(trace)}, expected {expected_length}")
            result[name] = hashlib.sha256(trace).hexdigest()

            # Python independently checks the real checkpoint's byte order,
            # three-entry contract and checksums without modifying any user file.
            checkpoint = Path(tmp) / f"{name}.zip"
            trainer = trainer_cls(genre, SEED + 77 + ordinal)
            for _ in range(TRAIN_STEPS):
                trainer.step()
            if int(trainer.updates) <= 0:
                raise AssertionError(f"{name}: Double DQN optimizer did not update")
            trainer.save(java_file(str(checkpoint)))
            with zipfile.ZipFile(checkpoint) as archive:
                expected = {"manifest.json", "state.bin", "SHA256SUMS"}
                if set(archive.namelist()) != expected:
                    raise AssertionError(f"{name}: checkpoint entries differ from v1 contract")
                state = archive.read("state.bin")
                result[f"TRAINER:{name}"] = hashlib.sha256(state).hexdigest()
                metadata = archive.read("manifest.json")
                sums = archive.read("SHA256SUMS").decode("utf-8")
                valid = (hashlib.sha256(state).hexdigest() + "  state.bin\n"
                         + hashlib.sha256(metadata).hexdigest() + "  manifest.json\n")
                if sums != valid:
                    raise AssertionError(f"{name}: checksum mismatch")
                if struct.unpack(">II", state[:8]) != (0x4C505231, ordinal):
                    raise AssertionError(f"{name}: checkpoint endian/version mismatch")
                manifest = json.loads(metadata)
                if (manifest["format"] != "localpilot-dqn-v1"
                    or manifest["observation"] != "arcade-objects-v1"
                    or manifest["shape"] != [64]
                    or manifest["network"] != [64, 64, 64, len(env.actions())]
                    or manifest["byte_order"] != "big-endian"):
                    raise AssertionError(f"{name}: manifest contract mismatch")
    return result


def main() -> None:
    jar = build()
    with tempfile.TemporaryDirectory(prefix="localpilot-rl-probe-") as tmp:
        native = native_vectors(jar, Path(tmp))
        python = jpype_vectors(jar, Path(tmp))
    for genre in GENRES:
        for key in (genre, f"TRAINER:{genre}"):
            if native[key] != python[key]:
                raise SystemExit(
                    f"BIT-EXACT PARITY FAILED: {key}: Java={native[key]} "
                    f"Python/JPype={python[key]}"
                )
        print(f"PASS {genre} raw-float32-sha256={native[genre]}")
        print(f"PASS {genre} trained-state-sha256={native[f'TRAINER:{genre}']}")
    print("PASS: exact Java/JVM and Python/JPype simulator, Double-DQN training state and checkpoint v1")
    print("NOTE: physical Android runtime and real-game action parity NOT tested")


if __name__ == "__main__":
    main()
