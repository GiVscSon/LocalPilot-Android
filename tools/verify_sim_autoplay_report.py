#!/usr/bin/env python3
"""Fail-closed evidence checks for unattended offline GameLab training.

This verifies *simulated* observations only. It never authorizes Android
gestures, restores real-game weights, or claims physical-device acceptance.
"""
from __future__ import annotations

import csv
import hashlib
import json
import math
import pathlib
import sys

GAMES = {
    "BUBBLE", "ARI", "MATCH", "PADDLE", "TRAFFIC", "FLAPPY",
    "BOARD2048", "HEXON", "PROTECTED_SWAP", "CAT_TOWER",
}
COLUMNS = ("game", "episode", "step", "result", "score_before",
           "score_after", "from_x", "from_y", "to_x", "to_y")
VALID_RESULTS = {"CHANGED", "NO_CHANGE", "BLOCKED"}
MAX_BYTES = 512 * 1024


def validate(directory: pathlib.Path) -> dict:
    report_file = directory / "report.json"
    report = json.loads(report_file.read_text(encoding="utf-8"))
    if report.get("schema") != "localpilot-gamelab-autoplay-v1":
        raise ValueError("invalid lab-only report schema")
    if report.get("source") != "SIMULATED_GAME":
        raise ValueError("simulator provenance missing")
    if report.get("real_game_accepted") is not False:
        raise ValueError("falsely claims Market acceptance")
    if report.get("weights_promoted_to_real_games") is not False:
        raise ValueError("unsafe model promotion")
    train_episodes = int(report["train_episodes_per_game"])
    eval_episodes = int(report["evaluation_episodes_per_game"])
    steps = int(report["steps_per_episode"])
    if not 1 <= train_episodes <= 32 or not 1 <= eval_episodes <= 32:
        raise ValueError("invalid episode bounds")
    if not 1 <= steps <= 80:
        raise ValueError("invalid step bound")
    expected_train = len(GAMES) * train_episodes * steps
    expected_eval = len(GAMES) * eval_episodes * steps
    if expected_train > 1024 or report["training_events"] != expected_train:
        raise ValueError("unexpected replay episode count")

    replay_file = directory / "training-experience.lpx"
    if replay_file.stat().st_size > MAX_BYTES:
        raise ValueError("oversize lab replay")
    lines = replay_file.read_text(encoding="utf-8").splitlines()
    if not lines or lines[0] != "LOCALPILOT-LAB-EXPERIENCE-v1":
        raise ValueError("unexpected lab replay header")
    if len(lines) != expected_train + 1:
        raise ValueError("missing simulator training observations")
    training_counts = {game: 0 for game in GAMES}
    for sequence, line in enumerate(lines[1:], 1):
        parts = line.split("|")
        if len(parts) != 14:
            raise ValueError("broken simulator row")
        content, digest = line.rsplit("|", 1)
        actual = hashlib.sha256(content.encode("utf-8")).hexdigest()
        if digest != actual:
            raise ValueError("simulator replay checksum failed")
        if int(parts[0]) != sequence or parts[1] not in GAMES:
            raise ValueError("spoofed game or sequence")
        if parts[2] != "NONE":
            raise ValueError("fault injection unexpectedly included in self-play")
        if parts[12] not in VALID_RESULTS:
            raise ValueError("unrecognized transition result")
        if (parts[12] == "CHANGED") != (parts[8] != parts[9]):
            raise ValueError("wrong game-change evidence")
        training_counts[parts[1]] += 1
    if any(n != train_episodes * steps for n in training_counts.values()):
        raise ValueError("training does not cover all ten games")

    evaluations = {}
    for name in ("random", "lab-policy"):
        filename = directory / f"evaluation-{name}.csv"
        counts = {game: [0, 0] for game in GAMES}
        with filename.open(encoding="utf-8", newline="") as handle:
            reader = csv.DictReader(handle)
            if reader.fieldnames != list(COLUMNS):
                raise ValueError("invalid evaluation CSV schema")
            for item in reader:
                game = item["game"]
                if game not in GAMES or item["result"] not in VALID_RESULTS:
                    raise ValueError("unknown evaluation game or outcome")
                counts[game][0] += 1
                if item["result"] == "CHANGED":
                    counts[game][1] += 1
                for field in ("from_x", "from_y", "to_x", "to_y"):
                    if not 0 <= int(item[field]) <= 1000:
                        raise ValueError("evaluation coordinates outside 0..1000")
                if not 0 <= int(item["episode"]) < eval_episodes:
                    raise ValueError("evaluation episode outside range")
                if not 0 <= int(item["step"]) < steps:
                    raise ValueError("evaluation step outside range")
        if sum(x[0] for x in counts.values()) != expected_eval:
            raise ValueError("evaluation is incomplete")
        if any(x[0] != eval_episodes * steps for x in counts.values()):
            raise ValueError("uneven game evaluation")
        evaluations[name] = counts

    found = set()
    for row in report["games"]:
        game = row["game"]
        if game not in GAMES or game in found:
            raise ValueError("duplicate, missing or unknown game")
        found.add(game)
        if row["training_actions"] != training_counts[game]:
            raise ValueError("training sample count inconsistent")
        a = evaluations["random"][game]
        b = evaluations["lab-policy"][game]
        for key in ("random_change_rate", "lab_change_rate",
                    "delta_percentage_points", "random_mean_score",
                    "lab_mean_score"):
            if not math.isfinite(float(row[key])):
                raise ValueError("nonfinite game metric")
        if abs(row["random_change_rate"] - 100 * a[1] / a[0]) > 0.002:
            raise ValueError("random change rate not supported by CSV")
        if abs(row["lab_change_rate"] - 100 * b[1] / b[0]) > 0.002:
            raise ValueError("learned change rate not supported by CSV")
        delta = row["lab_change_rate"] - row["random_change_rate"]
        if abs(delta - row["delta_percentage_points"]) > 0.002:
            raise ValueError("evaluation delta mismatch")
    if found != GAMES:
        raise ValueError("not all game models evaluated")
    if not (directory / "report.md").is_file():
        raise ValueError("missing human-readable report")
    return report


def main() -> int:
    directory = pathlib.Path(sys.argv[1] if len(sys.argv) > 1
                             else "build/reports/sim-autoplay")
    try:
        result = validate(directory)
    except (OSError, ValueError, TypeError, KeyError, json.JSONDecodeError) as exc:
        print("GAMELAB_AUTOPLAY_FAIL", str(exc), file=sys.stderr)
        return 1
    print("GAMELAB_AUTOPLAY_REPORT_PASS",
          len(result["games"]), "scenes", result["training_events"], "training actions",
          "source=SIMULATED_GAME")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
