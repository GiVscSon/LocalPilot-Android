#!/usr/bin/env python3
"""G2 admission check: correlate JNI GGUF calls with verified NES transitions.

Reads existing on-device logs exported from the SAME app session. Does not
fabricate a model, call a mock engine or claim reward/physical-device acceptance.
The signed logs prove recorded execution attempts plus core result, not that the
GGUF improved gameplay; a real-device end-to-end run remains mandatory.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import sys

SHA = re.compile(r"[0-9a-f]{64}\Z")
BUTTONS = {"LEFT", "RIGHT", "UP", "DOWN", "A", "B"}
MAX_LOG = 128 * 1024


def digest(body: str) -> str:
    return hashlib.sha256(body.encode("utf-8")).hexdigest()


def read_rows(raw: bytes, schema: str, field_count: int) -> list[list[str]]:
    if not 0 < len(raw) <= MAX_LOG:
        raise ValueError("MISSING_OR_OVERSIZED_LOG")
    rows = []
    for line in raw.decode("utf-8").splitlines():
        if len(line) > 450:
            raise ValueError("OVERSIZED_JOURNAL_ROW")
        fields = line.split("|")
        if (len(fields) != field_count or fields[0] != schema
                or fields[-1] != digest(line.rsplit("|", 1)[0])):
            raise ValueError("JOURNAL_SCHEMA_OR_SHA_INVALID")
        rows.append(fields)
    if not rows:
        raise ValueError("NO_JOURNAL_ROWS")
    return rows


def correlate(inference_raw: bytes, episode_raw: bytes, model_sha: str) -> dict:
    if not SHA.fullmatch(model_sha):
        raise ValueError("MODEL_SHA_INVALID")
    inference = read_rows(inference_raw, "NES_MODEL_INFERENCE_V1", 9)
    episodes = read_rows(episode_raw, "CONSOLE_AI_EXPERIENCE_V2", 12)
    offered = []
    rejected = 0
    for fields in inference:
        model_id, sha = fields[1], fields[2]
        if sha != model_sha:
            continue
        frame, fingerprint, latency, gpu_layers = (
            int(fields[3]), int(fields[4]), int(fields[5]), int(fields[7]))
        if (not re.fullmatch(r"[A-Za-z0-9:_.-]{1,96}", model_id)
                or frame < 15 or not 0 < fingerprint <= 0xffffffff
                or not 0 <= latency <= 3600000 or not 0 <= gpu_layers <= 512):
            raise ValueError("GGUF_EXECUTION_METADATA_INVALID")
        button = fields[6]
        if button == "REJECTED":
            rejected += 1
            continue
        if button not in BUTTONS:
            raise ValueError("GGUF_PROPOSAL_NOT_GAME_ACTION")
        offered.append((model_id, sha, frame, fingerprint, button, latency, gpu_layers))
    if not offered:
        raise ValueError("NO_VALID_GGUF_ACTION_PROPOSAL")
    used = set()
    accepted = []
    for fields in episodes:
        if fields[3] != model_sha:
            continue
        if fields[1] != "HOMEBREW" or fields[10] != "UNKNOWN":
            raise ValueError("NES_PROVENANCE_OR_REWARD_FORGED")
        button = fields[4]
        frame0, frame1 = int(fields[5]), int(fields[6])
        before_hash, after_hash = int(fields[7]), int(fields[8])
        if (button not in BUTTONS or frame0 < 15 or frame1 <= frame0
                or not 0 < before_hash <= 0xffffffff
                or not 0 < after_hash <= 0xffffffff
                or before_hash == after_hash or fields[9] != "PIXELS_DIFFER"):
            raise ValueError("NO_FRESH_VERIFIED_NES_TRANSITION")
        match = next((i for i, run in enumerate(offered)
                      if i not in used and
                      (run[0], run[1], run[2], run[3], run[4]) ==
                      (fields[2], fields[3], frame0, before_hash, button)), None)
        if match is None:
            raise ValueError("NES_ACTION_HAS_NO_EXACT_MODEL_DECISION")
        used.add(match)
        accepted.append((frame0, frame1))
    if not accepted:
        raise ValueError("NO_LINKED_MODEL_TO_NES_ACTION")
    if any(a[0] <= b[0] for a, b in zip(accepted[1:], accepted[:-1])):
        raise ValueError("NES_ACTION_ORDER_INVALID")
    return {
        "schema": "localpilot-g2-gguf-nes-trace-v1",
        "result": "LOG_CORRELATION_PASS_NOT_DEVICE_ACCEPTANCE",
        "modelSha256": model_sha,
        "inferenceCallsForModel": len(offered) + rejected,
        "matchedTransitions": len(accepted),
        "lastFrame": accepted[-1][1],
        "exactModelActionAndFrameMatch": True,
        "trustedGameReward": "UNKNOWN",
        "ggufWeightsTrained": False,
        "physicalDevice": "NOT_VERIFIED",
        "nativeBackendIndependentAttestation": "NOT_VERIFIED",
        "releaseReady": False
    }


def main() -> int:
    p = argparse.ArgumentParser()
    p.add_argument("--inference-log", type=Path, required=True)
    p.add_argument("--experience-log", type=Path, required=True)
    p.add_argument("--model-sha", required=True)
    p.add_argument("--output", type=Path, required=True)
    args = p.parse_args()
    try:
        for file in (args.inference_log, args.experience_log):
            if not file.is_file() or file.is_symlink() or file.stat().st_size > MAX_LOG:
                raise ValueError("UNTRUSTED_LOG_FILE")
        result = correlate(args.inference_log.read_bytes(),
                           args.experience_log.read_bytes(), args.model_sha)
    except (OSError, ValueError, UnicodeError) as error:
        print("G2_TRACE_FAIL: " + str(error), file=sys.stderr)
        return 1
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n")
    print("G2_LOGS_CORRELATED_UNVERIFIED_DEVICE " + str(result["matchedTransitions"]))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
