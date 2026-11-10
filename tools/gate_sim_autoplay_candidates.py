#!/usr/bin/env python3
"""Compare multiple independent GameLab-only evaluation seeds.

The output is a lab-only decision registry, NEVER a permission to deploy
training, change Android game models, or claim Market success.
"""
from __future__ import annotations

import json
import math
from pathlib import Path
import sys

from verify_sim_autoplay_report import validate as validate_report

GAMES = (
    "BUBBLE", "ARI", "MATCH", "PADDLE", "TRAFFIC", "FLAPPY",
    "BOARD2048", "HEXON", "PROTECTED_SWAP", "CAT_TOWER",
)
SEEDS = (20261108, 20261201, 177777)

def classify(deltas: list[float], score_deltas: list[float],
             coach_lag: list[float], coach_score_lag: list[float]) -> str:
    if any(len(series) != 3 for series in
           (deltas, score_deltas, coach_lag, coach_score_lag)):
        raise ValueError("three independent seeds required")
    if not all(math.isfinite(n) for n in deltas + score_deltas + coach_lag + coach_score_lag):
        raise ValueError("nonfinite metric")
    # A single material regression is a veto; averages cannot hide it.
    if any(d < -2 for d in deltas):
        return "LAB_QUARANTINED_REGRESSION"
    # A scripted expert provides a second reference beyond easy random-play wins.
    if (sum(lag >= 5 and score_lag > 0 for lag, score_lag in
            zip(coach_lag, coach_score_lag)) >= 2):
        return "LAB_BELOW_SCRIPTED_REFERENCE"
    # A candidate must improve on a majority of seeds and never regress.
    if (all(d >= -0.002 for d in deltas)
         and sum(d >= 5 for d in deltas) >= 2
         and sum(s > 0 for s in score_deltas) >= 2):
        return "LAB_CANDIDATE_ONLY"
    return "LAB_INCONCLUSIVE"

def produce(base: Path) -> dict:
    sources = []
    for seed in SEEDS:
        path = base / f"seed-{seed}" / "report.json"
        # Reconstruct every metric from the current-version episode CSVs before
        # comparing seeds. JSON-only results from the old fixture physics are
        # not interchangeable with measured physical-scene evaluations.
        report = validate_report(path.parent)
        if (report.get("source") != "SIMULATED_GAME"
            or report.get("real_game_accepted") is not False
            or report.get("weights_promoted_to_real_games") is not False
            or report.get("seed") != seed):
            raise ValueError("simulator provenance or seed mismatch")
        if len(report.get("games", [])) != len(GAMES):
            raise ValueError("one or more games missing from seed report")
        games = {row["game"]: row for row in report["games"]}
        if len(games) != len(GAMES) or set(games) != set(GAMES):
            raise ValueError("duplicate or missing game")
        sources.append(games)
    decisions=[]
    for game in GAMES:
        delta = [float(src[game]["delta_percentage_points"]) for src in sources]
        score_delta = [round(float(src[game]["lab_mean_score"]) -
                             float(src[game]["random_mean_score"]), 3)
                       for src in sources]
        coach_lag = [round(float(src[game]["scripted_change_rate"]) -
                           float(src[game]["lab_change_rate"]), 3)
                     for src in sources]
        coach_score_lag = [round(float(src[game]["scripted_mean_score"]) -
                                 float(src[game]["lab_mean_score"]), 3)
                           for src in sources]
        decisions.append({
            "game":game,
            "change_rate_delta_pp":delta,
            "score_delta":score_delta,
            "scripted_reference_gap_pp":coach_lag,
            "scripted_reference_score_gap":coach_score_lag,
            "status":classify(delta,score_delta,coach_lag,coach_score_lag),
            "scope":"SIMULATOR_ONLY",
            "may_change_real_model":False,
        })
    return {
        "schema":"gamelab-multiseed-policy-gate-v1",
        "source":"SIMULATED_GAME",
        "physics_rules":"2048-random-spawn_bubble-ray_paddle-bricks-v1",
        "evaluation_score_attribution":"EPISODE_TOTAL_SPLIT_PHYSICS_GESTURE",
        "evaluated_seeds":list(SEEDS),
        "may_dispatch_real_gestures":False,
        "may_transfer_to_market":False,
        "decisions":decisions,
    }

def main() -> int:
    base=Path(sys.argv[1] if len(sys.argv)>1 else "build/reports/sim-autoplay")
    try:
        report=produce(base)
        raw=json.dumps(report,indent=2,ensure_ascii=False)+"\n"
        (base/"model-gate.json").write_text(raw,encoding="utf-8")
        lines=["# GameLab multiseed model gate", "",
               "Лабораторный результат, не разрешение на реальные игровые жесты.",
               ""]
        for row in report["decisions"]:
            lines.append("- "+row["game"]+": **"+row["status"]+"**; "
                         +"Δ изменившихся ходов (п.п.): "
                         +", ".join(str(d) for d in row["change_rate_delta_pp"])
                         +"; Δ среднего счёта: "
                         +", ".join(str(d) for d in row["score_delta"])
                         +"; отставание от сценарного контроллера (п.п.): "
                         +", ".join(str(d) for d in row["scripted_reference_gap_pp"]))
        lines += ["", "Ни одна из политик не перенесена в Market или реальные веса.",
                  "LAB_CANDIDATE_ONLY означает лишь устойчивый результат в трёх упрощённых стендах.\n"]
        (base/"model-gate.md").write_text("\n".join(lines),encoding="utf-8")
        counts={}
        for row in report["decisions"]:
            counts[row["status"]]=counts.get(row["status"],0)+1
        print("GAMELAB_POLICY_GATE_PASS",counts,
              "market_transfer=BLOCKED")
        return 0
    except (OSError, ValueError, KeyError, TypeError, json.JSONDecodeError) as error:
        print("GAMELAB_POLICY_GATE_FAIL",error,file=sys.stderr)
        return 1

if __name__=="__main__":
    raise SystemExit(main())
