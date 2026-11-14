# Harness runtime and genuine policy learning · 2026-11-14

Status: implementation candidate, NOT a release or physical-device acceptance.
Architecture: [RFC v1](LOCALPILOT-HARNESS-RFC-v1.md).
The exact source SHA and fresh Actions links belong to the candidate PR, since a
commit cannot embed its own hash. Never substitute prior-branch CI for this candidate.

## Preserved baseline

- Audited `main`: `189b3b0a3edb2f27bb366a4e335d6da013372b83`, version 0.8.1/code53.
- Existing baseline Build: https://github.com/GiVscSon/LocalPilot-Android/actions/runs/43344069155
- Existing baseline one-APK candidate: https://github.com/GiVscSon/LocalPilot-Android/actions/runs/43344069145
- Local backup ref `backup/harness-main-20261113`, bundle `/workspace/baseline-main-189b3b0.bundle`.
- Independently rebuilt baseline APK: `/workspace/baseline-189b3b0/LocalPilot-main-189b3b0-debug.apk`.
  SHA-256 `88d36589a8f4616bb74a4a5cbe1bf0adfff55aecbefb0dafc4cb46b09f465b9a`.
  This is a local debug signer, not proof of compatibility with an installed production app.
- The original Actions artifact download returned HTTP 403. The saved APK is the
  subsequent successful local build, not the inaccessible CI binary.
- Existing frozen-time runtime from PR #117, source
  `da365dc047fb3c1b8bf13a03c28fa45f367cffa6`, integrated onto main in
  `6f9e0c9`. Main's newer signing/payload verification was retained at the two merge conflicts.
  No other open PR is implicitly declared merged or device-verified.

## Execution and ownership

`HarnessCoordinator.nes()` remains the only process-local permit issuer. Its
runtime connects `EnvironmentAdapter`, `ModelRouter`, `AgentController`,
`ExperienceStore`, `LearningCoordinator` and `EvaluationGate`. The environment is
the existing NES WebView, not a second game engine or Accessibility executor.

The GGUF route calls the existing real `EmbeddedGgufRuntime.proposeNesButton`,
with verified imported GGUF and optional matching projector. The router becomes
active only after a valid native response. Cancellation fences delayed callbacks;
the JNI busy flag remains authoritative until native work finishes. No model gets
an emulator or OS input capability. Native CPU/Vulkan behavior still requires the
fresh unified build and device evidence described below.

Manual/heuristic/GGUF/fast-policy actions share the same coordinator. STEP permits
now require exactly their approved frame count, including rejection of 15/17
frames for a 16-frame request. STOP, epoch, nonce, one-use ownership and TTL remain.
The policy controller rejects unknown observations and expired proposals.

STEP_MODE uses the real core with no interval while deciding. REALTIME_MODE uses
the core's interval continuously, releases each fast-policy input after exactly
16 emulated frames, and caches its actual ending observation. A new command more
than two frames behind the core is rejected, rather than pausing emulation for a
slow decision. The Android adapter also has a bounded observation timeout. This
does not establish phone latency or a sustained FPS guarantee.

## First supported reward task

Only the original `HomebrewNes.cartridge()` has this task profile. The explicit
objective is to move its actual white 8×8 sprite to target X. It is a Harness task,
not a score/victory rule attributed to arbitrary NES games.

The profile checks the actual framebuffer bounding box against known 6502 RAM
coordinates, allowing the documented one-pixel CPU/PPU phase difference. The
reward is `(oldDistance-newDistance)/16`, plus one for reaching within four pixels
of the target. Neither ACK nor an unrelated color/pixel change contributes reward.
Unknown visual/RAM agreement yields `reward=null` and cannot enter reward RL or
evaluation. User ROMs keep the pre-existing unknown-reward behavior.

The task seed selects one of six target coordinates; it does not randomize the
cartridge itself. Held-out seed/episode comparisons are independent executions
of this same deterministic task, not evidence of generalization to new games.

## Real learning and deployment

- The compact CPU policy has 66 tile-coded linear Q parameters. Q-learning updates
  use genuine `(state, action, reward, nextState)` transitions, discount 0.95 and
  learning rate 0.12 over 24 bounded replay passes.
- Collection, training, held-out evaluation and deployment are separate UI actions
  or phases. Collection alone never claims trained weights.
- Candidate weights, feature contract, SHA-256, exact training episode IDs/seeds and
  update count are written independently of the active pointer. GGUF weights are
  never modified. LoRA/GGUF fine-tuning is not implemented here.
- Twenty baseline episodes and twenty candidate episodes use paired reserved seeds,
  with no train/holdout seed or episode overlap. Evaluation runs frozen policies;
  it does not invoke the optimizer. Reports preserve both sets of actual outcomes.
- Promotion requires >5% median return improvement (minimum absolute margin 0.05),
  no reduced completion count, and no added safety stops. A failed/unknown/stopped
  evaluation cannot promote. An evaluated regression retains the prior active
  artifact. Explicit rollback restores the exact predecessor artifact.
- Policy writes and active-pointer replacement are atomic. Checksums/tensor bounds
  are checked on reload. Corrupt artifacts fail closed. Synthetic negative-test
  outcomes are stored under a separate fixture repository, not real evidence.

## Data and UI

Private `harness-v1/` contains content-addressed full framebuffer PNGs, immutable
binary `localpilot.task-transition.v1` records, `.policy`, `.manifest` and
`.evaluation` artifacts, and active/previous pointers. Transitions preserve epoch,
ROM SHA, seed, mode, frames/hashes, image references, states, action, nullable reward,
reward source, termination/truncation and policy SHA/provenance. This minimal task
format is distinct from the broader RFC JSON interchange format; a complete JSON
schema exporter is not yet implemented.
Stopped/failed episodes also get an immutable truncated `.episode-end` record
with unknown final reward, rather than an invented next-state transition. Android
replay reload verifies each referenced PNG SHA before allowing training.

Images are bounded to 128 MiB/5000 files and transitions to 5000 records; reaching
a quota stops collection. No automatic deletion of experience or models occurs.
The export button writes these private files to a user-selected ZIP through SAF.
Exports may contain private frames and should be reviewed before sharing.

The task controls scroll while framebuffer, live status, STOP and manual pad stay
visible. Backgrounding/ROM replacement/destruction cancels a running task. Severe
thermal status pauses discretionary task work. No background-training service or
additional installed application is required by the runtime.

## Verification and limits

Local checks actually executed:

| Check | Result | Scope |
|---|---|---|
| baseline `:app:assembleDebug` | PASS | saved local baseline APK |
| candidate app/GameLab builds, app lint, Android test compilation | PASS | Java/resources/DEX/instrumentation compilation; no JNI performance claim |
| Harness coordinator regressions | PASS | STOP/epoch/TTL/one-use, exact step frames |
| native-model router negative contracts | PASS | cancellation, invalid output, ownership; synthetic API fixtures only |
| genuine JSNES policy learning | PASS | actual CPU/PPU, durable replay, changed estimates/SHA, paired evaluation and rollback |
| genuine JSNES realtime | PASS | continuous interval, policy input, stale-frame rejection |
| existing JVM/one-APK/signing/bridge regressions | PASS | see reproducible commands and PR evidence |
| new Android API30/35/36 Harness UI scenario | NOT RUN locally | added to device workflow, requires fresh CI |
| physical phone/tablet/POCO inference/gameplay | NOT RUN | `adb devices` has no devices |
| Vulkan offload, timing, thermals on hardware | NOT RUN | no GPU claim |

The headless integration executes the production WebView script and the pinned
JSNES core. Only canvas/image transport is replaced by a headless shim; pixels and
RAM come from actual CPU/PPU execution. Its artifacts are explicitly not Android
PNG-transport or physical-device proof. The new instrumentation separately uses
the real Android WebView, UI button dispatch, actual PNG storage, training,
evaluation, realtime and rollback; it must pass on the exact candidate SHA before
claiming Android acceptance. It does not claim OS-injected user touches.

Reproduce using the project-generated, integrity-verified core and real cartridge:

```sh
java tests/HarnessCoordinatorVerify.java
javac --release 17 -d build/nes-runtime game-lab/src/main/java/org/localpilot/gamelab/HomebrewNes.java tests/ExportHomebrewNes.java
java -cp build/nes-runtime ExportHomebrewNes build/nes-runtime/demo.nes
bash scripts/verify_harness_learning.sh game-lab/build/generated/jsnes/assets/nes/jsnes.min.js build/nes-runtime/demo.nes
./gradlew :app:assembleDebug :app:lintDebug :app:compileDebugAndroidTestJavaWithJavac :game-lab:assembleDebug
```

For the full one-APK GGUF engine use the existing pinned llama.cpp source-fetch
step and `-PunifiedModelLab=true :app:assembleDebug`, as in the all-in-one workflow.
The ordinary legacy debug build does not prove its native GGUF engine is enabled.

Still missing: physical acceptance, fresh full native/multimodal inference proof,
Vulkan/thermal measurements, arbitrary-ROM reward profiles, additional consoles,
LoRA/GGUF fine-tuning, background scheduling, an optional slow GGUF realtime
strategist, and the full RFC interchange/export contract. Do not call the overall
user completion criterion fulfilled until device-backed end-to-end evidence exists.
