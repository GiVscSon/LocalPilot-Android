# G4.2 verified prompt adoption and Pocket cohort · 2026-11-18 UTC

Base: #203 `e33efd3b2a941b3a11683d57f4c613996b285bad`.
The parent contains #200's zero-progress selection guard, #201's Pocket-first
cohort, #202's picker changes and #203's corrected Pocket model checksum.
Those existing commits are inherited, not reapplied. Frozen #198 is untouched.
No main merge, stable release, signing change or trained weights.

## Actual evidence before this change

#203 run44418009292 tested merge
`b450c1f1b45cb0e14b39ce688b09f98c476fd04f`:

- HOST_UNIT: all five Pocket pin/catalogue/sampler tests PASS, also repeated locally
  on the checksum-verified canonical source archive (724 source files).
- ANDROID_BUILD: ARM64 job135203275368 and x86/test companion135205673387 PASS.
- REAL_MODEL_NES: Pocket job135207395143 PASS on x86 APK
  `ad5449296f1f897655bfbc150173bb82f0fd6a53b090c02ad0c69ba87233678d`.
  Actual decisions: START then eight A. Score100, kills1, shots3, lives2.
  Movement and victory were not confirmed. This is GGUF/llama.cpp text plus
  verified VOID LANCER RAM, not native MNN-engine or visual inference evidence.
  Pocket uses greedy decoding; repeated seed0 receipts are not random trials.
- Pocket model SHA:
  `a281c127e388b16537fd3f5d31ab32d3f87739e6dc514ac1093c80d9eac8d097`.

The independent evaluation in frozen #198 run44285809059 uses source
`10e509f0df6deff4076772972ad3505c4227135f`, x86 APK
`c45ee8c5c0275aa8b8169d815ace34f006a815410f924445da5aa66d7ddeb66c`.
All15 discovery arms (135 probes and45 bounded episodes) and60 held-out pairs
are complete. The initial Qwen pair7 failed at cold UI entry before inference;
original artifact14425954010 was preserved. Same-source retry135204931902 PASS
produced artifact14481517062. Evaluation135208690512 PASS, artifact14480229156.
The original failure and initial INCOMPLETE_EVIDENCE are retained in CI history.

| Model | Pairs | A median score | C median score | A/C productive episodes | Gate |
|---|---:|---:|---:|---:|---|
| Qwen3.5 |20|200|350|15/18|IMPROVEMENT_VERIFIED|
| LFM1200 |20|400|500|20/20|IMPROVEMENT_VERIFIED|
| LFM350 |20|0|0|0/0|NO_PROVEN_IMPROVEMENT|

The independent gate additionally requires no worse median lives lost. These
are short, bounded actual episodes, not completed levels or victory evidence.
No weights were trained or automatically activated by the experiment.

## Minimal change

`NesDecisionExperiment.productionPrompt` chooses the exact measured C text only
for Qwen SHA `57d1997790d1744fba5b40a7317df71ea5e2acee28c47e78f0cce39c0703f8cf`
and LFM1200 SHA `2ea801949d760cdf1a2cc04a54262c22c3c0c54f0769d57760c9adeb0e59233f`.
The existing runtime uses this formatter only for its already verified ROM RAM
profile. Pocket, LFM350, names without matching hashes and other imported
weights retain A. Unknown ROM handling does not change.

The diagnostic A recipe remains the frozen pre-adoption reference. Its explicit
experiment override still selects each A/B/C/D/E arm independently of the normal
production choice. D/E cannot become production choices. Native grammar,
sampling, STOP, deadlines, phase checks, input executor and training are unchanged.

Changed files: shared prompt formatter, existing HarnessModelRuntime call site,
its Java contract test, and this evidence handoff. No UI/catalogue expansion.

## Verification and next gate

HOST_UNIT PASS:53 prompt checks,23 strict experiment checks,5 Pocket checks,
9 one-APK contracts, existing cancellation10+6. A separate read-only check
reconstructed all114 actual C requests from discovery artifacts14419904423 and
14424947020: new production text equals their measured userPrompt byte-for-byte.
That parity check reuses real logs; it does not execute new neural inference.

Android and real-game results above belong to their stated parent sources.
New-child ANDROID_BUILD, EMULATOR_ANDROID and REAL_MODEL_NES remain pending until
its own CI completes. The Draft title opts into the existing Pocket/Qwen/LFM1200
five-arm workflow after the normal build. LFM350 stays visible in ordinary
regression but is excluded from the expensive primary cohort. Missing or partial
cases block selection; zero productive episodes cannot create a candidate.
Pocket stays on A unless its own independent held-out gate proves improvement.

VERIFIED_TRAINING, PHYSICAL_ANDROID (POCO X7 Pro) and RELEASE_SIGNED: NOT_RUN.
No universal-agent, motor-training, completed-level or Yandex-game acceptance.
