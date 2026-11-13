# LocalPilot Harness: staged implementation and release gates

Status: IMPLEMENTATION PLAN. This document never declares a release, mobile training, or Vulkan support by itself.
Created: 2026-11-13. Authority: [LOCALPILOT-HARNESS-RFC-v1.md](LOCALPILOT-HARNESS-RFC-v1.md).
Target: **one arm64-v8a Android APK**, API 30+, with a local NES GameLab, local ModelLab and shared HarnessCoordinator.
Target physical device for first acceptance: POCO X7 Pro. Test artifacts for other ABIs are **not** additional user apps.

## 0. Snapshot, provenance and source of truth

Read-only snapshot from 2026-11-13 before this planning branch:
- main: 72273456215a179739813a7e152a2ccbaa008be8
- Harness integration: feature/harness-unified-runtime-20261113 at da365dc047fb3c1b8bf13a03c28fa45f367cffa6
- Draft PR #120: stage-one WebView, ROM preflight and packaging hygiene, a311df9d1d653168095d2b7c44696fdb1a05a4d0
- Draft PR #121 (stacked on #120): near-black screenshot quarantine and bounded retries, 4c05f7395abaa33597c6884c906fea469ed6f592
- Legacy parallel Draft PRs #90/#101/#102 require reconciliation; do not merge or copy competing implementations automatically.

A green build on one PR is not proof for another SHA. Never replace new code with an old patch; pin and re-read every touched file before modifying it. Maintain a protected rollback path. Release approval is explicitly separate from Draft PR merge.

## 1. One coherent architecture, not eight disconnected apps

Single application process and launcher:
- **HarnessCoordinator** owns session lifecycle, agent selection, STOP revision, epoch, budgets, and exactly one input lease.
- **EnvironmentAdapter**: first real JSNES (user-supplied ROM or verified homebrew), later capability-checked libretro cores. STEP_MODE pauses emulation outside bounded step(frameCount); REALTIME_MODE advances continuously regardless of LLM latency. Neither mode turns model output directly into OS gestures.
- **ObservationPipeline**: immutable frameId, screenshot source, schema/versioned obs64, ROI, timebase, legalMask, and UNKNOWN; separate Accessibility HardwareBuffer from MediaProjection Image.Plane. No pixelsDiff-as-reward shortcut.
- **ModelRouter**: installed -> hashed -> runtime-probed -> bound -> active -> observed-used. GGUF text/multimodal via existing local llama.cpp/mtmd JNI is a slow strategic advisor; StudentPolicy is the fast motor. A model merely listed in ModelLab is **not** an agent.
- **AgentController + existing ActionGate/InputPermit**: validates action schema, physical/legal mask, epoch, TTL, STOP, singleton owner, and serialization of press/release.
- **TransitionVerifier -> ExperienceStore v2**: system ACK is only transport. Record visual/core frame advance and trusted game reward separately. Every event records provenance.
- **LearningCoordinator -> EvaluationGate**: StudentPolicy candidate weights change for real on eligible reward-bearing trajectories, then compete on disjoint held-out seeds. Fail -> rollback; pass -> atomic promotion.

One UI with views: **Library** (ROMs + models + actual capability/backend), **Play** (game/agent binding, STOP, source), **Episodes** (frame -> observation -> inference -> action -> independent result), **Learning** (eligible counts, weights hashes, provenance, held-out evaluation), **Diagnostics** (capture/READ/POLICY/ACK, memory, backend, STOP causes). These are views of common state, **not** separate engines.

No Yandex mini-game *simulators*. Retain older Android Accessibility game-reading only as a quarantined compatibility/test path while its ROI, capture and gesture safety is repaired; it must not control the NES emulator or confer PPO reward without verified evidence. No independent Python agent inside APK.

## 2. Blocking phases (0–6) and post-release extension (7)

### Phase 0. Source integrity / clean APK / CI

Own: build.gradle, Gradle tasks, APK ZIP audit, pinned dependencies, source SHA, CI.
Already in #120: virtual-HTTPS WebViewAssetLoader, NES ROM preflight, *.bak exclusion, phantom DOS core honestly unavailable, local loopback network exception.
Implement now: audited ZIP membership and actual bytes, report all DEX files without incorrectly forcing multiDexEnabled=false; detect .bak, missing classes.dex, wrong DEX headers, missing NES assets, corrupt/empty required entries, mismatched sourceSha, duplicate filenames. Validate AndroidManifest.xml and resources.arsc as **compiled Android resources**, never grep for plain res/xml filenames inside APK.
Exit: one fixed source SHA; script + synthetic tests PASS; API 30/35/36 Android CI and separate one-APK branch suite green; APK bytes/SHA and signature provenance recorded. No claim that CI alone proves device success.

### Phase 1. Capture/vision/gesture reliability

Own: PilotService, JumperCaptureService, RgbaFrameDecoder, CaptureRetry, WindowCaptureBridge, GridPuzzleVision/Policy, scene settling.
Already in #121: near-black ROI -> UNKNOWN/read-only bounded retry -> Fail-Closed. Detecting near-black pixels never identifies compositor cause with certainty.
Next: Android API34+ window-screenshot route with verified windowId, owner, bounds, timestamp and bitmap geometry; API30–33 safe display capture. Never map window-relative pixels to screen coordinates without an explicit checked transform. Keep STOP-panel input safety even if window screenshot omits its visual overlay. Never circumvent FLAG_SECURE or platform policies.
Fix full-frame allocations by source-specific decoding (HardwareBuffer is **not** Image.Plane), and keep 10x10 GridPuzzle unknown/protected masks via BitSet (not 64-bit long). Classify ANIMATION_PENDING vs UNCHANGED with bounded settling.
Exit: bounded peak allocation, no UNKNOWN->invented action, no unverified game-over/ACK-as-motion. Device traces for all claimed Android fixes.

### Phase 2. True offline mode + bounded local vault

Own: URI/network policy on every network-producing client, app-owned model directory, frame/journal/audit store.
Default STRICT_OFFLINE blocks *external* remote network operations by policy and testable interception; explicitly allowed loopback stays separate from cloud. A Network Security Config denying cleartext is **not** a general HTTPS firewall.
Opt-in user downloads of legal ROMs/models are a separate operation and must be explicitly authorized, hash-checked, then usable without network.
Vault: private filesDir/localpilot_vault with active/candidates/rollback models, atomic write/sync where supported, frame ring quota 64 MiB, bounded logs. Audit/cleanup only while not playing; STOP fences asynchronous operations.
Exit: tests for HTTP/HTTPS/DNS and alternate clients, quota stress, crash/restart recovery and safe local loopback; no unauthorized egress observed during offline inference/emulation.

### Phase 3. HarnessCoordinator -> real NES STEP_MODE

Own: existing org.localpilot.shared.HarnessCoordinator (do not replace with a second class), existing JSNES page/bridge, AgentController.
Contracts: reset(ROM identity + seed or declared nondeterminism), observe immutable frame state/obs64, step(action,ticks), frameId, releaseAllInputs, STOP, timeouts and stale callback invalidation. One owner even when ModelLab, training and simulator UI are all open.
Exit: deterministic, **real-emulated** frame increments for supported homebrew; press/release honored; no input after STOP; separate reproducible real-ROM evidence. Snapshot/seed claims require actual core support.

### Phase 4. One local ModelRouter and actual backend probes

Own: existing EmbeddedGgufRuntime + localpilot_embedded JNI (llama.cpp/mtmd). No parallel LocalLlamaBridge/liblocalpilot_llama or incompatible JNI.
Verify dependency pin and provenance for reported GGUF parsing CVEs before a version-bump claim; do not assume a user-supplied issue title proves a fix. Preflight untrusted GGUF, bounded tensors, family/projector compatibility; descriptor lifecycle and native interruption must not race free/use-after-free.
CPU-first; Vulkan flag only when a real backend probes successfully, measures CPU/Vulkan p50/p95/p99 and passes validation on the phone. Single bounded asynchronous VLM strategy queue; stale suggestions discarded. ExecuTorch/MLC are **optional future providers**, not another bundled parallel runtime.
Exit: documented text AND vision inference traces with model SHA, CPU/Vulkan selection, output schema, timings, no stale strategy press after STOP.

### Phase 5. ExperienceStore v2 and eligibility

Own: provenance-aware immutable transition journal, replay filter and schema migrations. Preserve ability to read legacy v5 artifacts without relabeling them.
Separate **RETRO_EMULATED**, **VERIFIED_REAL**, **SIMULATED_GAME**, **SYNTHETIC**, **MANUAL**. Require stable observation schema, complete contiguous epoch, oldLogProb/valueEstimate/action legalMask and durationFrames for on-policy PPO. reward=null when no trustworthy score/goal signal exists, including when pixels changed or Android ACK succeeded. STOP => truncated, not terminal.
Exit: reward-null transitions never train PPO; strict provenance isolation; SHA-linked local evidence and audit of old/new observations and model identity.

### Phase 6. Real StudentPolicy + PPO/GAE + EvaluationGate + release

Own: [64 -> 64 -> 64 -> Actions] actor + value head; big-endian versioned weights.bin; PpoGaeTrainer and LearningCoordinator; rollbackable EvaluationGate.
Semi-MDP discounts gamma^durationFrames, untouched raw value targets, legal-only mask/entropy, finite parameters/gradients, bounded Adam steps, PPO clip and measured approximate KL early stop. Define tested hyperparameters rather than declare them universal constants.
Training proof: before/after different SHA-256 of actual serialized weights, finite parameters, genuine eligible rollout and action probability changes. SHA change is **necessary but not sufficient** for improved behavior.
Evaluation: disjoint held-out seeds, baseline and candidate tested with same core/profile, no safety violations, strict return improvement and deterministic rollback test for deliberately degraded candidate. Promote via atomic file replacement only after success.
Exit: installed single APK + signature + matching source SHA, all Android CI/functional gates, physical POCO acceptance, no late actions after STOP, sustained NES REALTIME loop with GGUF async. Preserve unverified functions as SHADOW_MODE.

### Phase 7. After release, not a release blocker

Plug-in console cores via capability-checked libretro adapters and actual core binary/license review (SEGA, DOSBox Pure if available); optional LoRA training in isolated offline research mode; individually consented MCP integrations with per-tool permissions, no direct input privileges. License or ABI limitations must be surfaced, never hidden.

## 3. Review protocol for every commit

Every proposed change must include:
1. Exact branch and HEAD SHA, diff paths, optional APK byte delta from a verified baseline.
2. Reproducible JVM tests and named GitHub CI run(s) on **that** SHA; distinguish BUILD_PASS, UI_PASS, INFERENCE_PASS, TRANSITION_PASS, TRAINING_PASS, DEVICE_PASS.
3. For Phase 5/6, training provenance, raw policy weight hashes, held-out seed manifest, EvaluationGate report and negative rollback proof.
4. For claims about POCO or Android system behavior, *real* SAF audit/report, CPU/Vulkan/capture p50/p95/p99 and zero post-STOP actions; no synthetic tests mislabeled as device proof.
5. Open gaps: disabled/unprobed model, unsupported ROM/core, shadow modes, uncertain reward, untested Android APIs, or missing signing evidence.

## 4. Security, policy and safe defaults

STOP always available; no flag disables OS protections, legalMask, single-writer, TTL, provenance or real reward verification.
Do **not** set android:isAccessibilityTool=true merely to evade restricted views: a game agent is not a disability accessibility tool. Android Play distribution requires independent policy review; prefer **in-app emulator buttons** instead of Accessibility injection for autonomous games. No circumventing FLAG_SECURE or sensitive fields.
No fake performance metrics or unsupported "CVE fixed" / "Vulkan working" claims. External downloads of models or ROMs remain user-initiated and separate from inference; verify checksum, model format and license.
