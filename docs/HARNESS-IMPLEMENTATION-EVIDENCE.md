# Harness implementation evidence · 2026-11-13
Baseline branch: `fix/console-shared-agent-memory-20261112`
Baseline commit: `380b08daa8f329a729cd34f81f25ab94c4754de3` (PR #115).
Existing CI: 10 completed SUCCESS GitHub Actions runs on this SHA. This does **not** attest device inference or reward training.
Design source: PR #116, `design/harness-runtime-v1-20261112`, `3dc5d67747efdddb9249ed7cdd5b185cfc05e22d`.

## Evidence gates
- P0: RFC and machine-readable transition contract imported unmodified from PR #116 onto the existing NES+GGUF+shared-memory integration branch.
- P1 IN PROGRESS: `HarnessCoordinator` is wired to the actual Android NES activity for GGUF, manual and scripted controller permits, STOP, TTL/epoch and transport-vs-reward discipline. A pure JVM regression suite is added. P1 now includes a JSNES frozen-time STEP_MODE bridge for the built-in homebrew, with exact 16 emulated frames and an explicit REALTIME_MODE manual resume control. Source and CI tests are added. The new implementation is NOT device-verified; assertions of complete P1 remain blocked by full matrix + physical tests.
- P2: NOT COMPLETE (model router with capability probes/active binding).
- P3: NOT COMPLETE (trusted task-profile reward, proper real-transition store).
- P4: NOT COMPLETE (trainable policy, independent holdout, promotion, rollback).
- P5: NOT COMPLETE (realtime mode, physical latency/thermal/Vulkan evidence).
- P6: NOT STARTED (additional console cores).

## Safety
Preserve the baseline CI/test-only APK. No merge, production package/signing change or training/result claim without reproducible checks and device validation.
