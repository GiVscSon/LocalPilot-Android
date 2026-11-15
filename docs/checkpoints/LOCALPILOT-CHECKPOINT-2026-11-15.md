# LocalPilot checkpoint · 2026-11-15 · Unified Lab

**Checkpoint type:** immutable evidence handoff, **not** a released APK and **not** an automatic synchronization of historical chats.

- Canonical repository: https://github.com/GiVscSon/LocalPilot-Android
- Verified `main` at checkpoint capture: `fbbbeb9cb4ffa54ecc26582e4a640b963b80b8d3`
- Single control hub: https://github.com/GiVscSon/LocalPilot-Android/issues/159
- Live handoff: `docs/LOCALPILOT-AGENT-HANDOFF.md`
- Living synchronization pointer: `docs/LOCALPILOT-SYNC-POINT.md`
- Parallel Google Drive handoff: https://docs.google.com/document/d/1d_U4NLmOQTyzvRQ1PdUVMkLGh0-eH_PCwleFPetbFK8/edit
- Drive rev9 hardware-twin archive: https://drive.google.com/file/d/1L06lGmr4SGe-Mpy5hFZjjzy7FSV1AqCZ/view

## Accepted common architecture

One Android APK; one `HarnessCoordinator` as episode/STOP/epoch and action owner, one actual input executor, no duplicate emulators, game controllers or PPO trainers. The observed state flows through:

real game/ROM -> fresh capture/ROI -> object/scene/mechanic understanding or UNKNOWN -> actual model/policy inference -> constrained action proposal -> single ActionGate/InputPermit owner -> action -> NEW frame/RAM evidence -> verified replay -> candidate learning -> independent holdout -> promote or rollback.

System organization for engineering:
1. ChatGPT sandbox: source/JVM/Python/vision/algorithm experiments (no locally working Android SDK/ADB/KVM).
2. GitHub Actions: Android SDK/ADB/KVM and **real Android emulator** test matrices for unified APK, not physical device proof.
3. User's Windows GPU workstation: optional large-model/learning service, not presumed connected or passing.
4. POCO X7 Pro ARM64: required final real-device performance/Vulkan/input/signer/upgrade acceptance. NOT YET VERIFIED.

Shared training data must be bound by source SHA, actual ROM SHA, model/policy SHA, generation/episode/frame/time/ROI, action identity, fresh visual/RAM result and reward provenance. Unknown outcome -> abstain/STOP, never fabricate reward or overwrite active weights.

## Proven at capture

- C0/C1/C2/C3 coordination: PRs #161, #163, #164, #165, #166 merged; verified CI-to-Issue bot comment exists.
- G0 APK candidate provenance: PR #167 merged as `398a609c62288db4c70b81d395972744a9451cd8`; 6/6 Android emulator matrix successful after an ADB transport re-run, source/ZIP/DEX/debug signing/ELF16K bound to same APK, NOT permanent release signing or upgrade acceptance.
- G1 sustained *existing homebrew JSNES + StudentPolicy* test: PR #168 HEAD `991ab923370999bb450d89597505ad0f9d259701`, **OPEN DRAFT** when captured; workflow https://github.com/GiVscSon/LocalPilot-Android/actions/runs/43742466592 all 10 jobs SUCCESS. Specific job `sustained-nes-256` emitted `EMULATOR_SUSTAINED_256_PASS`: Android 35 Pixel 2, 256 verified actions, 16 sealed replay batches, frame 24→4120, RAM-derived task return -1.25, STOP verified, active policy unchanged. The test's `sourceSha` was `f3f63ef184ad465bd67760784096570a95197b20` (PR test merge SHA), APK SHA `43d5142aaba9e3c0a02d731faba04760a0cbb8338787dad356e3035206fc2c10`. This is **emulator-only**, and a negative task return is NOT a learned win.
- G2 exact GGUF→NES journal correlation contract: PR #169 merged as `fbbbeb9cb4ffa54ecc26582e4a640b963b80b8d3`, tests passing; **real GGUF on-device inference not yet proven by that test**.
- PPO candidate holdout: `NO_PROVEN_IMPROVEMENT` in the previously accepted matrix; no automatic promotion justified. Independent evaluation and rollback still required for any new candidate.
- Google Drive 0.8.5 rev9: `HARDWARE_TWIN_SIMULATED`, 5/5 modeled gates for POCO X7 Pro and Xiaomi Pad 7, `real_usb_cable_attached=false`. It is a useful research snapshot, NOT physical phone evidence; not merged blindly into GitHub `main`.

## Outstanding G0–G6 blockers

- G1: review PR #168 against current main and merge only when correctly reconciled and CI remains green; then run sustained test on merged SHA.
- G2: bind a real GGUF/mmproj + runtime engine on Android to action/transition logs; validate model SHA and actual inference.
- G3/G4/G5: offline performance, safety, independent holdout, actual model improvement and robust rollback; no duplicate shadow input executor.
- G6: physical POCO X7 Pro, Vulkan, genuine gameplay, upgrade without data loss and **pinned production signing cert** (Issue #99).
- Drive rev9 `ModelRouter` is rule-driven in the inspected patch; its Android emulator sources use Java desktop APIs. Integrate selectively only after tests, avoid overwriting working GitHub Control Tower workflows.

## Protocol for any ChatGPT, Codex, Gemini or automation

1. At the start of each new session, read current `main` and Issue #159; do not trust this captured SHA as current.
2. Read `docs/LOCALPILOT-SYNC-POINT.md`, the latest immutable checkpoint and Google Drive handoff.
3. Compare exact SHA of each open PR/head, source, model, APK and test artifact; treat unverified statuses as UNKNOWN.
4. Claim **one stage and one module ownership** in Issue #159. Never implement a second Harness/controller/emulator input owner.
5. Make changes through tested branches/PRs; record run links, first failing step, evidence scope and rollback.
6. Update Issue #159 and the living pointer after verified changes. Historical/other chats are **not automatically messaged**; each agent must read the shared hub, or receive an explicit shared handoff.

**Freeze rule:** This checkpoint is not to be rewritten to follow future work. Publish a new dated checkpoint instead.
