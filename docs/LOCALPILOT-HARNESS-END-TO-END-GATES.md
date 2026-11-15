# Harness end-to-end integration: one executable pipeline

Status: acceptance plan, NOT evidence of implemented features. Base: PR #124 HEAD `ee841e0ccf3bac46405478ee570a20cbf6731e6b`. Read together with `LOCALPILOT-HARNESS-RFC-v1.md` and `LOCALPILOT-HARNESS-EXECUTION-PLAN.md`. Do not replace their phase gates.

## The one data path

```text
UI (Play / Models / Episodes / Learn / Diagnostics)
             | commands + read-only snapshot
       existing HarnessCoordinator (single owner, epoch, STOP)
             |
 EnvironmentAdapter(JSNES) -> Observation(frameId, epoch, ROM SHA, legalMask)
             |                              |
             +--> ModelRouter (real probe) --+
                        | Proposal(action, frameId, epoch, TTL, model SHA)
                        v
       AgentController -> existing ActionGate/InputPermit
                        | approved action only
                        v
                JSNES button press/release
                        |
       fresh core frame -> TransitionVerifier
                        |
      ExperienceStore v2 (provenance, nullable reward)
                        |
       eligibility -> LearningCoordinator -> candidate weights
                        |
       EvaluationGate -> promote OR atomic rollback
```

UI MUST NOT own a second emulator, agent runner, model session, experience database or input lease. A catalogue entry is not proof of runtime usage. Each screen is a projection of the coordinator's shared state.

## Minimal contracts, to implement incrementally

**EpisodeIdentity:** `episodeId, epoch, romSha256, coreId, coreVersion, mode, policySha256, startedElapsedMs`. Immutable within an episode. Core nondeterminism and unavailable savestate must be explicit.

**Observation:** `frameId, epoch, elapsedMs, schemaVersion, roi, pixelsHash, legalMask, unknownReason`. No mutable shared bitmap passed to asynchronous inference.

**Proposal:** `requestId, observationFrameId, epoch, stopRevision, modelSha256, action, durationFrames, deadlineElapsedMs`. Missing or stale fields mean REJECT, never default action.

**ActionResult:** `permitId, inputTransportAck, releasedAllButtons, newFrameId, transitionVerified, failureReason`. ACK alone is not success. A STOP increments revision and invalidates pending proposals.

**Transition:** `episodeId, sequence, oldFrameId, newFrameId, action, provenance, rewardNullable, rewardEvidenceRef, terminated, truncated, stopReason`. `RETRO_EMULATED`, `VERIFIED_REAL`, `SIMULATED_GAME`, `SYNTHETIC` and `MANUAL` are never silently converted into each other.

**TrainingReceipt:** `eligibleEpisodeIds, excludedCountsByReason, algorithm, candidateShaBefore, candidateShaAfter, holdoutSeedManifest, evaluationReportSha, promoted, rollbackSha`. Hash change alone is not a quality metric.

## Work order and measurable gates

### Gate 0: build and evidence (first)
- Reconcile PR #120 -> #124 and separate #156 without merging; pin a single source SHA and record merge-commit SHA if CI checks a synthetic merge.
- Inspect the actual unified arm64-v8a APK with `tools/verify_harness_apk.py`, ZIP/DEX/ROM/source SHA; compiled manifest and resources via `aapt`; verify `apksigner` certificate SHA-256, versionCode/package, native library ABI and signed upgrade compatibility.
- Require unit/JVM, NDK, API30/35/36 instrumentation and one-APK UI smoke on same candidate; distinguish BUILD_PASS/UI_PASS from runtime.
- Fail gate on unknown signer or missing artifact, not on absent plain `res/xml` ZIP paths. Preserve existing `classes2.dex` until method counts prove it redundant.
- Output: immutable `artifact-manifest.json` with source SHA, artifact SHA, certificate SHA, API matrix, test run URLs and unresolved gaps.

### Gate 1: one NES owner and real transitions
- Wrap existing JSNES bridge; do not create another JSNES page or duplicate HarnessCoordinator.
- Implement STEP_MODE reset/observe/step with bounded press/release, monotonic frame IDs, explicit capability flags; then REALTIME_MODE with asynchronous strategic inference and fast motor policy.
- Test STOP during held input, delayed model callback, ROM switch, activity rotation, timeout, invalid legalMask, no fresh frame, and owner contention.
- Output: a real homebrew NES episode trace, including input release, before/after frame hashes and actual core advance. Mark ACTION_PASS and TRANSITION_PASS separately.

### Gate 2: models actually influence play
- Reuse `EmbeddedGgufRuntime` + `localpilot_embedded` JNI/llama.cpp mtmd; no second JNI. Import GGUF+mmproj via SAF, hash and runtime probe, validate compatibility, cancellation and bounded CPU.
- UI states: INSTALLED, VERIFIED, PROBED, BOUND, ACTIVE, OBSERVED_USED, ERROR. Only OBSERVED_USED with requestId+episodeId is evidence of actual use.
- Test real inference -> strict legal proposal -> approved NES action -> verified new frame; missing projector, stale answer and STOP must not press buttons.
- Output: INFERENCE_PASS with model SHA, real native backend, input type, timings, parsed proposal and action receipt. Vulkan needs separate physical p50/p95/p99.

### Gate 3: offline and memory safety
- Enforce STRICT_OFFLINE at every network-producing client, including HTTP/HTTPS and alternate clients; explicitly allow local loopback and user-initiated opt-in downloads only.
- Bound capture allocations by source: Accessibility HardwareBuffer vs MediaProjection Image.Plane. Repair 10x10 BitSet masks, UNKNOWN, protected cells, ROI, API30 package switching and panel STOP.
- Output: no external egress during offline play, bounded memory, real API traces and failure injection.

### Gate 4: truthful experience and reward
- Store immutable provenance with source/frame/model/action hashes; reward=null unless trusted score/goal provider independently validates it.
- Never feed ACK, pixelsDiff, synthetic fixtures, MANUAL or reward-null into reward-based PPO. STOP means truncated.
- Output: replay/audit and negative tests showing ineligible records are rejected.

### Gate 5: real training and holdout
- Implement or connect one fast StudentPolicy with versioned `weights.bin`; train candidate only from eligible complete trajectories, with oldLogProb/valueEstimate/legalMask/durationFrames for PPO/GAE.
- Prove finite parameters, before/after SHA, changed fixed-vector action distribution; evaluate against baseline on disjoint holdout seeds, safetyViolations=0, deterministic rollback on degraded candidate.
- Output: TRAINING_PASS and GENERALIZATION_PASS are independent; no promotion without both.

### Gate 6: physical integrated acceptance and release
- Install ONE signed APK on POCO X7 Pro, verify upgrade with preserved app data, real NES play, actual model binding and inference, STOP, sustained REALTIME loop, temperature/RAM, and CPU/Vulkan measurements if Vulkan is enabled.
- Compare real device and emulator traces; require zero late input after STOP. Re-run full API30/35/36 matrix on final exact SHA and signature.
- Output: DEVICE_PASS and release candidate only after all preceding gates; release/merge/key changes require separate approval.

Gate 7 (future): additional legal ROM cores and independently consented MCP permissions only after core stability.

## Integration rule for every change

One small Draft PR per independently testable seam. Record: exact base/head SHA; touched files; commands and real stdout; CI run links; APK bytes and certificate when applicable; proof type (BUILD/UI/INFERENCE/ACTION/TRANSITION/GOAL/TRAINING/GENERALIZATION/DEVICE); missing proof; rollback plan. An empty, queued or synthetic-only run is NOT a pass. Never publish fake scores, phantom capabilities or placeholder panels as operational features.
