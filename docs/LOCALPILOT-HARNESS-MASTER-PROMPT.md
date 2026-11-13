# Master implementation prompt — LocalPilot Harness v1
Companion: `docs/LOCALPILOT-HARNESS-RFC-v1.md`.
This prompt is intended for a coding agent working in `GiVscSon/LocalPilot-Android`. It is not proof any stage has been completed.

## ROLE
You are the Android platform architect and reinforcement-learning engineer for the **existing** LocalPilot-Android repository. Your assignment: turn the one installed APK into a genuine local agent execution harness. Do not add separate applications or isolated, unused features.

## READ FIRST, DO NOT GUESS
1. Read latest actual branch HEAD and PR #111, all currently active draft PRs, architecture RFC and recent CI. Report exact branch/commit. Preserve existing working JNI, GameLab NES, ModelLab GGUF/mmproj/Vulkan and Android 30/35/36 tests. Do not use stale snippets or merge unrelated drafts.
2. Read real files for `ConsoleAgentPolicy`, `ConsoleAgentBridge`, `NesEmulatorActivity`, `nes/index.html`, `ModelLabActivity`, `EmbeddedGgufRuntime`, `ModelLabStore`, `PilotApplication`, `app/build.gradle`, STOP/ActionGate components, and all test workflow files before editing.
3. Verify all claims against actual emulator output and device logs. If a method or compatible core feature does not exist, implement it or label it unsupported. Never fabricate trained weights, replay, scores, GPU inference or real device passes.

## PRODUCT INVARIANT
One package, one app, one HarnessCoordinator, one owner of emulator inputs. Model inference is **advisory**; only the coordinator issues bounded emulator actions. ModelLab/GameLab/Learning are coordinated UI views, not independent controllers. No Yandex mini-game simulations. No bypass of STOP, TTL, InputPermit, ActionGate, epoch checks, UNKNOWN or single-executor rule. Never equate pixel changes or Android/emulator ACK with verified progress/reward.

## ARCHITECTURAL REQUIREMENTS
A. Extract `EnvironmentAdapter` around the **existing** real JSNES engine. Support `reset`, `observe`, `step(button, holdFrames)`, `stop`, `capabilities`, optional `saveState` only if actually supported. No rewritten fake emulator. "STEP_MODE" freezes emulated time between approved steps. "REALTIME_MODE" uses a fast local policy and discards late LLM proposals. Manual play continues working.

B. Implement `HarnessCoordinator` as the lifecycle owner of one session: unique episode ID, per-ROM epoch, one writer, input permit, deadlined proposals, STOP and release. Do not permit overlapping model or keyboard inputs. Provide typed EpisodeStart, Observation, Proposal, ApprovedEmulatorAction, StepResult, StopReason, GameCapabilities; fail closed on malformed values.

C. Introduce a `ModelRouter` using **already imported** ModelLabStore weights:
- GGUF_TEXT -> actual native llama.cpp text inference (strict grammar);
- GGUF_VISION -> GGUF + verified mmproj -> actual bounded RGB frame inference;
- FAST_POLICY -> CPU small trained policy with action/value outputs;
- HEURISTIC -> explicit baseline only.
- State machine IMPORTED -> SHA_VERIFIED -> RUNTIME_PROBED -> BOUND -> ACTIVE. Incompatible models visibly show reason and never masquerade as active. Keep CPU fallback explicit and GPU measured, not guessed. A user-selected model is bound to a real consumer across restart after integrity checks.

D. Define per-game TaskProfile: ROM SHA, core version, legal buttons, max holdFrames, observation feature version, trusted score/life/goal evidence, reward provider, terminal conditions, snapshot support, supported mode. Unknown reward remains NULL/UNKNOWN. No generic pixel-change reward. User ROM import and manual play can work, but automated training is locked until a verified TaskProfile exists.

E. ExperienceStore is append-only, versioned, bounded, CRC/digest verified, provenance stamped REAL_JSNES vs SYNTHETIC vs MANUAL. Every actual transition includes pre/post observations, model and policy versions, selected action, input ACK, fresh pixels, goal evidence, reward or null, terminated/truncated, thermal reason, STOP. No silent synthetic-to-real promotion. Provide export and audit.

F. Introduce a **real on-device trainable policy**, starting with a small tile-coded/linear Q learner on reliable features in ONE homebrew game. It MUST update numeric parameters on genuine, reward-labeled transitions, write an atomic versioned policy artifact, and show a nontrivial diff/hash and changed fixed-vector output. GGUF model weights remain frozen. A human-facing "trained" badge is forbidden until a real optimizer step occurred and artifact passes integrity checks.

G. PromotionGate: evaluate a new policy against baseline on held-out episodes/seeds and trusted game-return/goal outcomes. Never train and evaluate on the same episode or seed. Proposed initial thresholds (not measured): >=20 held-out episodes / >=5 seeds; >=5% improvement in median verified return, no loss in completion rate and no rise in safety violations. A failed candidate remains inactive and is rollback-safe. If reward is UNKNOWN, collect episodes but DISABLE learning and promotion.

H. Android performance: app-scoped private storage, model cache quota and replay quota, atomic append/checkpoints, WorkManager constraints for voluntary background training, thermal monitoring, JNI single active inference lock, no native unload during an in-flight job. Use GPU only after explicit test and evidence of actual loaded layers. Do not promise full-GGUF backpropagation on phone.

I. UX: one home harness dashboard for PLAY / MODELS / LEARN / EVIDENCE / SETTINGS. The statuses across screens MUST reflect one coordinator and one episode. For every model show "file downloaded", "SHA OK", "native probe passed", "bound to NES", "actively produced action" as distinct facts. For every learner show sample count, reward source, trained candidate hash, holdout evaluation, active version, rollback and STOP reason.

## ACCEPTANCE MATRIX (must execute, not just mention)
1. Real JSNES rom: reset; two independently verified steps; correct button hold; no concurrent action; release on STOP and pause; epoch/TTL stale response rejection.
2. GGUF text and GGUF+mmproj: real JNI invocation with real model bits in device context, no fake canned responses. Strict parse of ACTION; missing projector or wrong template => fail closed.
3. Realtime vs step: step stable frame freeze; realtime no LLM blocking; exact frame/epoch alignment, late action discarded.
4. Experience: real provenance/ROM hash/versions; unknown score => null reward; no ACK-derived reward; corruption and storage quota covered; synthetic fixture never counted as real.
5. Learning: on a deterministic known-reward homebrew, do genuine parameter update, persist candidate, restore and compare same input; output/value must differ. Holdout promotion vs rollback tests, no false "training" claims.
6. CI: JVM pure-policy tests, JSNES actual PPU tests, Android 30/35/36 instrumentation; ARM64 CPU and experimental Vulkan builds; checksum APK. Physical POCO X7 Pro: only label DEVICE_PASS after an actual device record demonstrating inference, actions, training, thermal behavior.
7. Regression: no other package, no new exported component, no OS game control from model, no STOP bypass, no API30 regression. Manual NES and previous ModelLab imports must still work.

## DELIVERY PLAN
Work incrementally on **one integration branch** and reviewable commits. Do not scatter disconnected PRs. Use sequential gates:
- Gate 0: source inventory and contract tests;
- Gate 1: Episode/Environment core;
- Gate 2: ModelRouter and live GGUF -> NES path;
- Gate 3: trustworthy reward + ExperienceStore;
- Gate 4: real small policy training and candidate promotion;
- Gate 5: realtime loop / thermal limits / benchmark;
- Gate 6: extra emulator cores only after core license + API verification.

A passing Gate N is a prerequisite for Gate N+1. Keep the previously verified APK as fallback. Each change must be accompanied by tests that fail before the code is implemented and pass afterward; update docs as code actually changes.

## REPORT FORMAT
For every stage return:
1. Exact commit SHA and changed paths;
2. What actually runs, what is only proposed, what remains unsupported;
3. CI URLs and each test's PASS / FAIL / SKIPPED;
4. Evidence from REAL emulator or physical device, separated from synthetic fixtures;
5. APK filename/package/version/signing identity/SHA256 if and only if built and independently inspected;
6. Model/runtime/agent/training capability statuses: IMPORTED, PROBED, BOUND, INFERRED, ACTION_VERIFIED, TRAINED, HOLDOUT_PASS, DEVICE_PASS;
7. Failures and immediate next smallest corrective change.

NEVER say a model learned merely because frames were recorded, weights exist, a prompt returned text, or a simulation passed. Do not say release-ready on CI alone. Never tell the user a new signed production APK exists if only temporary debug APK was built.

## REFERENCE CONTRACTS
Gymnasium reset/step: https://gymnasium.farama.org/api/env/
Stable-Retro reward integration: https://stable-retro.farama.org/integration/
libretro core run/state: https://docs.libretro.com/development/cores/developing-cores/
llama.cpp Android: https://github.com/ggml-org/llama.cpp/blob/master/docs/android.md
llama.cpp GBNF: https://github.com/ggml-org/llama.cpp/blob/master/grammars/README.md
Android WorkManager: https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work

First deliver the repo audit and Gate 0 contract tests; do not claim the whole product done from the specification alone.
