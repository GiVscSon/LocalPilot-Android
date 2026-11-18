# G4 continuation · 2026-11-18

Scope: G4.1 phase-conditioned native generation and its independent actuator
gate. No main merge, stable release or signing change is authorized.

## Source reconciliation

- main checked: `32838fde8c2432dd060f3cf2923d4dd1910c9553`.
- #193: `2a9fa689a8e9b76e833d963dd08894f9e485d6a8`, based on #192
  `72d831b5504bbe9887289e578e51723063e176f1` and the older #186
  `71e7c48fdedb4901a1b017a437d66bea6f14cc78`.
- #186 has since advanced to `1c7fc6493adb8b169a71b00403e72b72608064fd`.
  That commit already supplies phase-conditioned native JSON tool grammar;
  do not implement a second runtime or repeat that patch.
- The continuation combines these histories in a separate Draft branch:
  preserve #192 complete-record/PLAYING-only history, #193 independent
  `phaseAllows` check before ticket consumption, and #186 native tools.

## Confirmed failure and correction

#193 Actions [44199986288](https://github.com/GiVscSon/LocalPilot-Android/actions/runs/44199986288),
merge `0da3892474ed5ad59dd0d99693ec997aa2775daf`: Qwen job134455127952
selected START in PLAYING; LFM350 job134455127933 selected LEFT in TITLE;
LFM1200 job134455128012 selected A in TITLE. All three FAIL, correctly stopped.
The post-inference gate did not constrain the vocabulary sampled by native.

The existing newer #186 correction passes verified `Observation.game()` into
`NesAgentTools.scope` → `EmbeddedGgufRuntime.proposeNesTools` → existing JNI
controllerMode. `controller_grammar.h` supplies the grammar to the actual
`llama_sampler_init_grammar`, before model sampling. Modes3/4 respectively
offer menu start/wait/stop or playing press/wait/stop without START/SELECT.
Only the JSON key is prefilled; no function/action is preselected. Parser and
the #193 actuator gate independently reject incompatible outputs. No automatic
START/A or replacement. Unknown ROM observations cannot carry the VOID RAM
profile and use generic physical controls/actual image requirements.

The executor,16-frame input bound,120s NES TTL, epoch/nonce/hash, STEP_MODE
freeze, STOP and key release remain the existing implementations.

## Newer actual game evidence, distinct from #193

#186 HEAD `1c7fc6493adb8b169a71b00403e72b72608064fd`, tested merge
`ffa2631f53945fe549116bce096f6adef2dc68f6`, Actions
[44207529453](https://github.com/GiVscSon/LocalPilot-Android/actions/runs/44207529453).
Real API35 x86_64 emulator, visible SAF imports, pinned official VOID LANCER
ROM SHA `5eb801aca68131a3d17fa82d8901d46f6098b878d1e00cd1c47d00c001c2405a`.
Shared test APK SHA `5e3b0e8c52d65c41afbff9513d0de838ae3d9172c29e5e8e00d8644c2664ac86`.
These results belong to that source/APK, not automatically to this integration.

- REAL_MODEL_NES Qwen job134480114624 PASS at the existing progress gate:
  model START, actual UP/LEFT movement, A raised shots0→3 at frame300→316,
  then WAIT at316→332 produced real score0→100/kills0→1.14 transitions,
  lives3→2. Every completed action correlated with chosen tool/model/frame/hash.
  Inference7502–13176ms. This establishes A/B/C, not D/E/F.
- REAL_MODEL_NES LFM1200 job134480114595 PASS: model START then actual A
  choices, shots/flying bullets observed; frame230→246 score0→100/kills0→1.
  9 transitions, lives3→2; inference13681–28023ms. Firing and C established,
  directional movement was not demonstrated by this episode. No victory.
- REAL_MODEL_NES LFM350 job134480114574 FAIL: actual `agent_stop`/NONE
  on TITLE frame102; no game start. STOP released JS input. No fallback.
- ANDROID_BUILD ARM64/x86 and six phone/tablet UI jobs PASS; internal
  sustained256 and runtime-ownership PASS are separate infrastructure proofs.
  Overall workflow remains FAIL because LFM350 failed.
- Separate capture workflow44207529325 API30 job134477301004 FAIL:
  screenshot fixture quadrant0 mismatch; do not classify as proven SDK outage.
  Scripted APK workflow44207529283 initial job134477224016 failed dependency
  resolution; exact-source retry succeeded. Preserve the original failure.

No weights were trained or promoted. Replay reward for this generic game path
remains null; actual counters in a report are not an implemented PPO reward
provider. No PHYSICAL_ANDROID/RELEASE_SIGNED evidence. Debug signing is not a
stable signer/upgrade acceptance.

## Verification and next gates

HOST_UNIT:83 tool/phase checks,92 typed-host checks, C++ grammar/framing/sampling,
Harness live-cycle7, real-model evidence15, and actual JSNES VOID profile
smoke are run on the combined source. The JSNES smoke includes explicitly
scripted core baselines; it is not neural gameplay evidence. See PR for the
exact commit and execution results. New combined Android/REAL_MODEL_NES
results remain pending until CI finishes.

Next: run the combined source's three real-model jobs without a fallback;
then compare the five requested prompt/grammar/history variants from identical
serialized real-core states with pinned sampling seeds. The previous two
positive single episodes do not replace this experiment. Capture exact bounded
prompts, native prefill/decode, legal set, model/ROM identity and actual before/
after states. Diagnose LFM350's deliberate stop without removing stop authority.
Repeat disjoint episodes before claiming D. Only then connect the existing
StudentPolicy/trainer to verified VOID reward provenance and independent
evaluation (E); real victory (F) and physical POCO acceptance remain separate.
