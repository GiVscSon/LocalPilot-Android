# LocalPilot: single source of truth across chats and branches

**Status:** coordination ledger, not a release certificate. **Updated:** 2026-11-15. **Canonical implementation:** current GitHub `main`, then separately reviewed changes rebased onto its latest SHA. This file must be updated after every accepted integration; a conversation, screenshot, or draft PR never overrides verified repository state.

## 1. One project, one product

One offline-first Android ARM64 APK (`org.localpilot.android` for final release) containing the actual GameLab/NES, ModelLab, local models, agents and training, coordinated by **the existing** `HarnessCoordinator`. Optional downloads are user-initiated. A test-only application ID and debug signer are not a production release. Preserve STOP, one ActionGate/InputPermit owner, epoch/TTL, legalMask, fail-closed behavior and visual verification; Android ACK is not proof of gameplay. No duplicate JNI, second emulator, duplicate trainer, or decorative-only screens.

Read these as authoritative *design contracts*:
- `docs/LOCALPILOT-HARNESS-RFC-v1.md`
- `docs/LOCALPILOT-HARNESS-EXECUTION-PLAN.md`
- Draft PR #157 `docs/LOCALPILOT-HARNESS-END-TO-END-GATES.md` is a **proposed** cross-component acceptance checklist, not merged code.

## 2. Reconciled implementation history

- Old development: 0.7.x, ten Yandex Market minigames, GridPuzzleVision/Policy, Bubble, 2048, Jumper, capture/ROI/STOP. These requirements remain in scope, but historical APKs and test logs are **not** evidence of current 0.8.x behavior.
- PR #90, #101, #102 and #120–#124: historical/parallel Draft integration lines. PR #124 SHA `ee841e0ccf3bac46405478ee570a20cbf6731e6b`; CI on that SHA may be green, but that does not make it the newest canonical implementation. Do **not** merge these wholesale onto a newer main.
- **PR #146 merged into main 2026-11-14**, head `62c796bfcb3880905584611ce79efc39400e934c`: one Console Lab/ModelLab route into existing NES; real JSNES homebrew ROM frame/6502 RAM verification; up to four GGUF decisions with logged inference; 16 on-policy Student actions, persisted verified replay and PPO/GAE update of **candidate** weights only. Its PR body is a change claim, not physical device evidence.
- PR #150 Draft, SHA `bcbff8eabd39b3a904330a011221c20894138f92`: 20-pair holdout on actual JSNES; candidate/baseline median and mean returns both zero, `NO_PROVEN_IMPROVEMENT`, **no policy promotion**. PR description reports full green CI on that SHA, but no physical POCO acceptance.
- PR #155 Draft: bounded-step final-frame painting and signed FNV/save-PPU consistency checks; no skipped core frames or weakened leases. Source `ce71fece2c14c6a62b92b0d434c113c2d63ce964` passed direct API30/35/36 CI (run43717890315). Local TCG cold autoplay timed out and confirmed STOP; no physical speedup or sustained-play PASS. Latest head must be read live. [Detailed scoped evidence](LOCAL_ANDROID_RUNTIME_VALIDATION_2026-11-15.ru.md).
- PR #156 Draft, SHA `825dc1eefbf179ba3bf43e6f4d9c4a2ead5d4495`: bound source-provenance JSON reads and native ELF header inspection in APK audit. Its base is **old PR #124**, so cherry-pick/reimplement narrowly on latest main after checking whether equivalent code exists; never merge its entire ancestry.
- PR #157 Draft, SHA `259f8629945d870049361d3c44c2ec9a70536cc3`: proposed cross-component gates. Also based on old PR #124; migrate its document selectively to main after reviewing conflicts.
- Issue #99: rotating debug signing keys and non-destructive update compatibility, open at last check.

**Version warning:** older conversations refer to 0.7.34, 0.8.1 and 0.8.2; PR #155 describes a 0.8.3/code55 base. Determine versionName/versionCode from the **built APK** and the **current main** on every report. Do not treat branch names or historical handoff files as current version evidence.

## 3. What actually works vs what remains unproven

**Observed/reported in current integrated code or linked CI:** built-in NES homebrew ROM, real bounded JSNES frames, StudentPolicy selecting actions, verified RAM-derived reward traces, replay saved, PPO candidate weights change, ModelLab GGUF inference auditing, emulator Android API30/35/36 tests in some named PRs. Each is scoped to the exact source SHA and test; passing one does not imply the others.

**Not yet proven for a stable release:** candidate holdout improvement and automatic safe promotion/rollback on real device; generalization to arbitrary user ROMs; sustained REALTIME autonomous play; GGUF+mmproj native vision on physical POCO X7 Pro; Vulkan backend p50/p95/p99 and correctness on that phone; comprehensive offline egress enforcement; stable release signer/pinned SHA and non-destructive upgrade; one fully accepted release artifact on a single exact source SHA; all ten Market games in the latest unified build.

**Conflicting evidence:** Some separate branches and workflows report Android capture, ReleaseLab, provenance and emulator failures while older integration PRs report green. These are different SHAs and cannot be averaged into a global PASS. Identify each branch/head, failing job and first FAILED step before fixing. Do not mark an entire project green from a selected PR.

## 4. The canonical state machine (same for every UI)

`ROM_SELECTED -> CORE_RESET -> OBSERVED -> MODEL_PROBED -> PROPOSAL -> ACTION_PERMITTED -> CORE_STEPPED -> FRESH_FRAME_VERIFIED -> TRANSITION_STORED -> ELIGIBILITY_CHECKED -> CANDIDATE_TRAINED -> HOLDOUT_EVALUATED -> PROMOTED_OR_ROLLED_BACK`.

STOP can interrupt every state and invalidates all outstanding proposals. The Console, ModelLab, GameLab, Learn and Evidence screens are projections of the **same** episodeId/epoch/frameId/model SHA/STOP revision and evidence store. No screen may advertise ACTIVE or TRAINED solely from file presence.

Proof labels are independent: BUILD_PASS, UI_PASS, INFERENCE_PASS, ACTION_PASS, TRANSITION_PASS, GOAL_PASS, TRAINING_PASS, GENERALIZATION_PASS, DEVICE_PASS.

## 5. Single priority queue, not separate chat roadmaps

**Gate 0 / provenance:** Verify newest main SHA, actual APK ZIP/DEX/native libs/NES assets, source SHA, compiled resources via aapt, certificate SHA-256, and API30/35/36 tests on that same SHA. Resolve issue #99 and inspect outstanding provenance/ReleaseLab failures. Do not strip multidex or assume literal res/xml paths.

**Gate 1 / live loop:** Verify a real ROM episode, unique input owner, frame monotonicity, fresh-frame and RAM verification, STOP during step and delayed model reply, resume after restart, UI navigation and sustained REALTIME. Review #155 for painting optimization without bypassing safety.

**Gate 2 / model binding:** Trace selected GGUF(+mmproj) through SAF/PFD, existing llama.cpp/mtmd/`localpilot_embedded`, real inference, bounded CPU and accepted action. No second JNI. Unprobed models remain INSTALLED, not ACTIVE.

**Gate 3 / device/offline/capture:** Distinguish Accessibility HardwareBuffer from MediaProjection Image.Plane; API30 package switch and API34+ WINDOW_FULLSCREEN geometry; PANEL_COVERS_ROI, ROI/UNKNOWN and BitSet up to 10x10; network calls under STRICT_OFFLINE (allow loopback). Physical memory/thermal measurements.

**Gate 4 / evidence:** immutable RETRO_EMULATED, VERIFIED_REAL, SIMULATED_GAME, SYNTHETIC, MANUAL; reward=null excludes reward-based RL; STOP => truncated; trace source ROM/model/action/reward evidence.

**Gate 5 / learning:** real StudentPolicy PPO/GAE weight hashes and action-probability changes, eligible oldLogProb/valueEstimate/legalMask/durationFrames, disjoint holdout, >=20 pairs if using PR #150 contract, safetyViolations=0, no promotion on NO_PROVEN_IMPROVEMENT, deterministic rollback.

**Gate 6 / release:** single signed ARM64 APK, verified upgrade and data preservation, full same-SHA CI and physical POCO X7 Pro acceptance. Only then propose merge/release with explicit approval. Gate 7 console cores/MCP/LoRA comes later.

## 6. Chat handoff rule

At the start of any new LocalPilot chat or agent task:
1. Read this file plus RFC/Execution Plan from **latest main** and inspect current PRs/Actions. Record the exact HEAD and base SHAs.
2. Compare new work against **main and already open PRs**. Identify duplicate or superseded branches. Do not rebuild a second HarnessCoordinator, NES, JNI or Student trainer.
3. Select the first failing measurable gate; create at most one small Draft PR against the **latest correct base**, with real tests and negative tests. If another branch is already solving it, review that branch instead.
4. Report only evidence-based change: SHA, paths, Action run/job, first failure, artifact/cert, measured result and remaining blocker. Never conflate synthetic JVM assertions, Android emulator and physical device evidence.
5. Update this ledger after confirmed changes. Mark items `PROPOSED`, `IMPLEMENTED`, `CI_VERIFIED`, `DEVICE_VERIFIED`, `RELEASED` separately. If evidence is missing, write `UNKNOWN`, not PASS.

**No automated cross-chat synchronization exists merely because this document is present.** The repository ledger is the shared durable handoff; each future chat/agent must explicitly read it. Existing historical messages are not rewritten.
