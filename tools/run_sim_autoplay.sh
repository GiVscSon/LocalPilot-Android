#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
OUT="build/reports/sim-autoplay"
if [[ "${1:-}" == "--out" ]]; then
  [[ $# -ge 2 ]] || { echo "Missing output directory" >&2; exit 2; }
  OUT="$2"; shift 2
fi
CLASSES="$(mktemp -d)"
trap 'rm -rf "$CLASSES"' EXIT
javac --release 17 -d "$CLASSES" \
  game-lab/src/main/java/org/localpilot/gamelab/GameLabEngine.java \
  shared/src/main/java/org/localpilot/shared/SimExperience.java \
  shared/src/main/java/org/localpilot/shared/SimLabPolicy.java \
  tools/SimAutoplay.java
java -ea -cp "$CLASSES" SimAutoplay --out "$OUT" "$@"
python3 tools/verify_sim_autoplay_report.py "$OUT"
