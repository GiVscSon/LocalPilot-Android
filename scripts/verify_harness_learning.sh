#!/usr/bin/env bash
set -euo pipefail
# core and cartridge are genuine, integrity-verified build outputs, never substitutes.
if [ "$#" -ne 2 ]; then
  echo 'usage: bash scripts/verify_harness_learning.sh VERIFIED_JSNES_JS HOMEBREW_NES' >&2
  exit 2
fi
mkdir -p build/harness-learning
javac --release 17 -d build/harness-learning \
  shared/src/main/java/org/localpilot/shared/ConsoleAgentContract.java \
  shared/src/main/java/org/localpilot/shared/HarnessCoordinator.java \
  shared/src/main/java/org/localpilot/shared/harness/*.java \
  tests/HarnessContractsTest.java tests/HarnessLearningVerify.java
java -cp build/harness-learning HarnessContractsTest
java -cp build/harness-learning HarnessLearningVerify "$1" "$2"
