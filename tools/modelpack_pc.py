#!/usr/bin/env python3
"""LocalPilot-Android -> Windows/Linux LocalPilot PC model pack reader.

This tool verifies content digests and reads model weights. It does not
control mouse, keyboard, Android or other programs. Integrations must map
the exact original observation/action schema and retain their safety gates.
Python 3.9+ standard library only.
"""
import argparse
import hashlib
import json
import math
from pathlib import Path
import struct
import sys
import zipfile

FORMAT = "localpilot-modelpack-v1"
MAX_ARCHIVE = 150 * 1024 * 1024
MAX_ENTRY = 32 * 1024 * 1024
MAX_TOTAL = 135 * 1024 * 1024


def load_pack(path):
    path = Path(path)
    if path.stat().st_size > MAX_ARCHIVE:
        raise ValueError("archive exceeds size limit")
    with zipfile.ZipFile(path, "r") as z:
        info = z.infolist()
        names = [e.filename for e in info]
        if len(names) > 33 or len(names) != len(set(names)):
            raise ValueError("too many or duplicate archive entries")
        if "SHA256SUMS" not in names or "manifest.json" not in names:
            raise ValueError("incomplete model pack")
        if any(e.file_size > MAX_ENTRY or e.is_dir() or e.filename.startswith("/")
               or "\\" in e.filename or ".." in e.filename.split("/")
               for e in info):
            raise ValueError("unsafe archive path/size")
        if sum(e.file_size for e in info) > MAX_TOTAL:
            raise ValueError("archive expansion limit")
        sums = z.read("SHA256SUMS").decode("ascii")
        expected = {}
        for line in sums.splitlines():
            if len(line) < 68 or line[64:66] != "  ":
                raise ValueError("invalid SHA256SUMS")
            digest, filename = line[:64], line[66:]
            if filename in expected or len(digest) != 64 or any(c not in "0123456789abcdef" for c in digest):
                raise ValueError("invalid SHA256SUMS entry")
            expected[filename] = digest
        if set(expected) != set(names) - {"SHA256SUMS"}:
            raise ValueError("missing or extra digests")
        entries = {}
        for name in expected:
            data = z.read(name)
            if hashlib.sha256(data).hexdigest() != expected[name]:
                raise ValueError("checksum mismatch: " + name)
            entries[name] = data
    manifest = json.loads(entries["manifest.json"])
    if manifest.get("format") != FORMAT:
        raise ValueError("unsupported model pack schema")
    return manifest, entries


def dqn_predict(entries, genre, observation):
    genre = genre.upper()
    info_path = f"dqn/{genre.lower()}/policy.json"
    data_path = f"dqn/{genre.lower()}/policy.f32le"
    if info_path not in entries or data_path not in entries:
        raise ValueError("no exported DQN weights for " + genre)
    metadata = json.loads(entries[info_path])
    schema = metadata["checkpoint_manifest"]
    if (metadata.get("format") != "localpilot-dqn-policy-v1"
        or metadata.get("genre") != genre or schema.get("genre") != genre
        or schema.get("observation") != "arcade-objects-v1"
        or schema.get("shape") != [64] or schema.get("network")[:3] != [64, 64, 64]):
        raise ValueError("incompatible DQN observation/action schema")
    actions = schema["actions"]
    output_count = len(actions)
    if schema["network"][3] != output_count or not 1 <= output_count <= 16:
        raise ValueError("invalid output count")
    raw = entries[data_path]
    count = 8320 + 65 * output_count
    if len(raw) != 4 * count or metadata.get("weights") != count:
        raise ValueError("incorrect network length")
    if not isinstance(observation, list) or len(observation) != 64:
        raise ValueError("observation must be 64 floats")
    if any(not isinstance(x, (float, int)) or not math.isfinite(x) or abs(x) > 1.000001
           for x in observation):
        raise ValueError("observation contains invalid/unscaled features")
    w = struct.unpack("<" + "f" * count, raw)
    def relu(x):
        return max(0.0, x)
    a = [relu(w[4096+j] + sum(observation[i]*w[64*i+j] for i in range(64)))
         for j in range(64)]
    b = [relu(w[8256+j] + sum(a[i]*w[4160 + 64*i+j] for i in range(64)))
         for j in range(64)]
    logits = [w[8320 + 64*output_count+j] +
              sum(b[i]*w[8320 + i*output_count+j] for i in range(64))
              for j in range(output_count)]
    winner = max(range(output_count), key=lambda j: logits[j])
    return {"action": actions[winner], "index": winner, "q_values": logits,
            "training_updates": metadata["updates"], "genre": genre,
            "warning": "Simulation action schema; requires explicit desktop adapter."}


def matrix_predict(entries, kind, features):
    name = f"weightspace/{kind.lower()}.json"
    if name not in entries:
        raise ValueError("not in exported model pack: " + kind)
    item = json.loads(entries[name])
    if item.get("schema") != "weightspace-3x4-v1":
        raise ValueError("unknown matrix schema")
    if len(features) != 4 or any(not math.isfinite(x) or x < 0 or x > 1 for x in features):
        raise ValueError("expected four [0,1] features")
    weights = item["weights"]
    if len(weights) != 3 or any(len(row) != 4 for row in weights):
        raise ValueError("matrix shape")
    x = features.copy()
    if item.get("genre") == "PLATFORM_RECOVERY":
        x[3] *= x[0]
    scores = [sum(row[i]*x[i] for i in range(4)) for row in weights]
    count = 2 if item.get("genre") == "GAP_FLIGHT" else 3
    best = max(range(count), key=lambda i: scores[i])
    action = (["WAIT", "FLAP"] if count == 2 else ["LEFT", "STAY", "RIGHT"])[best]
    return {"genre": item["genre"], "action": action, "scores": scores,
            "origin": item["origin"], "not_a_verified_live_game_action": True}


def paddle_predict(entries, variant, features):
    name = "paddle/" + variant + ".json"
    if name not in entries:
        raise ValueError("no paddle model " + variant)
    item = json.loads(entries[name])
    if item.get("schema") != "paddle-2x4x1-v1" or len(item.get("weights", [])) != 13:
        raise ValueError("paddle model shape")
    x, y, vx, vy, paddle = features
    if any(not math.isfinite(v) for v in features) or not (.014 <= x <= .986 and 0 <= y <= 1 and .075 <= paddle <= .925):
        raise ValueError("invalid paddle observation")
    if vy > .03 and y + .014 < .92:
        period = 2 * (.986 - .014)
        pos = (x + vx*((.92 - .014 - y)/vy) - .014) % period
        impact = .014 + (pos if pos <= period/2 else period-pos)
    else:
        impact = x
    inp = [x-paddle, impact-paddle]
    weights = item["weights"]
    val = sum(weights[8+j]*max(0, weights[j*2]*inp[0] + weights[j*2+1]*inp[1])
              for j in range(4)) + weights[12]
    return {"steer": max(-1.0, min(1.0, 6*val)), "origin": item["origin"],
            "schema": item["schema"], "not_a_verified_live_game_action": True}


def main(argv=None):
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("pack", help="ZIP exported by the Android app")
    sp = p.add_subparsers(dest="command", required=True)
    sp.add_parser("list")
    a = sp.add_parser("dqn")
    a.add_argument("genre", choices=["JUMPER", "PLATFORMER", "LANES", "JETPACK"])
    a.add_argument("observation", help="JSON file with exactly 64 normalized floats")
    b = sp.add_parser("weightspace")
    b.add_argument("model", choices=["platform_recovery", "platform_recovery_patched",
                                      "lane_escape", "gap_flight"])
    b.add_argument("features", help="JSON file with four floats in [0,1]")
    c = sp.add_parser("paddle")
    c.add_argument("model", choices=["follow_ball", "impact", "trained_current"])
    c.add_argument("features", help="JSON [ballX,ballY,vx,vy,paddleX]")
    args = p.parse_args(argv)
    manifest, entries = load_pack(args.pack)
    if args.command == "list":
        output = manifest
    elif args.command == "dqn":
        output = dqn_predict(entries, args.genre, json.loads(Path(args.observation).read_text("utf-8")))
    elif args.command == "weightspace":
        output = matrix_predict(entries, args.model, json.loads(Path(args.features).read_text("utf-8")))
    else:
        output = paddle_predict(entries, args.model, json.loads(Path(args.features).read_text("utf-8")))
    print(json.dumps(output, indent=2, ensure_ascii=False))


if __name__ == "__main__":
    try:
        main()
    except (OSError, ValueError, KeyError, zipfile.BadZipFile) as exc:
        print("ModelPack error:", exc, file=sys.stderr)
        sys.exit(2)
