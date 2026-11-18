# E1: Pocket-first controlled NES model cohort

Status: **DRAFT / NOT MODEL IMPROVEMENT OR RELEASE**. 2026-11-18. Stacked after #200 and #198; read [visual roadmap](LOCALPILOT-VISUAL-STAGE-ROADMAP-2026-11-18.md) in PR #199 and [Control Tower #159](https://github.com/GiVscSon/LocalPilot-Android/issues/159). No main merge.

## Why Pocket gets priority
- Existing isolated Android real-model acceptance [PR #195, workflow 44266929630](https://github.com/GiVscSon/LocalPilot-Android/actions/runs/44266929630) confirmed MNN-Pocket Q4_1 GGUF (SHA256 `a281c127e388b16537fd3f5d31ab32d3f87739e6dc514ac1093c80d9eac8d097`) actually launched pinned VOID LANCER, fired and reached score=100, kills=1. This is **llama.cpp text/RAM inference**; not a native MNN runtime, no multimodal image inference. No level victory or trained motor.
- #198 pre-existing G4 experiment omitted Pocket. Its discovery compared Qwen, LFM350, LFM1200; LFM350 had **0 score and 0 verified C success** in 3 episodes for every A–E arm. That remains FAIL. #200 separately fixes false selection of a no-progress candidate.
- New primary controlled cohort: Pocket, Qwen3.5-0.8B, LFM2.5-1.2B. LFM350 stays in the ordinary real-game acceptance CI (FAIL remains visible) and in the model catalog; it is **excluded only from costly G4 discovery and holdout selection**. No automatic user-file deletion, model disablement or rank based on model name alone.
- Other catalogue models, e.g. VL vision GGUF+mmproj, must remain NOT_RUN for a given actual vision/ROM test unless a matching real inference, outcome and independent evidence are available.

## Exact experimental constraints
- Reuse the same existing native llama.cpp, JNI, actual ROM, actor, controller and 5-arm G4 runner from #198. Do not introduce a duplicate runtime or hidden heuristic input.
- Real model tests explicitly run Pocket on Android x86_64 alongside existing Qwen, LFM1200 and LFM350 (latter continues to fail until proven otherwise).
- Pocket uses actual native `REQUESTED` greedy sampler with **effective seed=0**. The requested case seeds 42/1042/2042 refer to *experiment cases*, not three independent stochastic Pocket RNG streams. Greedy is deterministic on identical prompt/state; diversity, if any, comes from different restored states and action histories. Record samplerMode `GREEDY_NO_RNG`.
- Run five A–E arms on matching real Android snapshots; only phase-safe B/C can be proposed after full discovery. Continue 20 paired holdout on real, distinct state offsets **only for candidates that had verified game-goal progress**. A green `DIAGNOSTICS_COMPLETED` means measured, not won.
- Keep reward=null on unproven reward; no trained Student weights, no hardware performance, no victory, no release; STOP/epoch/nonce/TTL/ROM hash/action gate unchanged.

## Gate to leave E1
Required: one exact source/test merge SHA, complete model/ROM hashes, native samplers/inference evidence, all discovery reports, honestly labeled failures, 20 held-out pairs for any Pocket candidate, positive improvement criterion and no additional safety violations. Successful Pocket score 100/kill1 in prior PR #195 **does not** establish new model/prompt improvement. If no improvement, keep original A. Then only after sustained autonomous gameplay consider E2/M1 motor integration.
