# Single APK integration acceptance · 2026-11-16

This is an evidence handoff for Draft [PR #175](https://github.com/GiVscSon/LocalPilot-Android/pull/175),
not a release declaration. Re-read GitHub HEAD/main and
[Control Tower #159](https://github.com/GiVscSon/LocalPilot-Android/issues/159) before continuing.

## Verified starting point

- `main`: `32838fde8c2432dd060f3cf2923d4dd1910c9553`.
- Initial PR head: `23288d896fb7fa4544da1d1b998b8f49b5a7f7d1`.
- Actual PR-merge checkout used by the verified CI:
  `1cb915e1951ace1a82318851f3a16de786dddcd1` (includes that main).
- All 17 discovered PR workflows completed SUCCESS. This count is scoped to
  this head and PR-triggered runs; it is not a complete repository inventory.
- [All-in-One 43836824959](https://github.com/GiVscSon/LocalPilot-Android/actions/runs/43836824959):
  ARM64 packaging, x86 derivative, runtime ownership, sustained NES256,
  API30/35/36 × phone/tablet and matrix aggregation all SUCCESS.
- [Phase0 43836825156](https://github.com/GiVscSon/LocalPilot-Android/actions/runs/43836825156):
  synthetic negative packaging tests and actual unified APK SUCCESS.

The downloaded All-in-One artifact14211392142 was SHA256/ZIP-CRC checked and
extracted. Existing tools were run again on its actual APK bytes with SDK35:
`verify_harness_apk.py --expected-source-sha 1cb915e1951ace1a82318851f3a16de786dddcd1`,
`verify_all_in_one.py`, `verify_android_elf.py`, `aapt dump badging`,
`aapt dump xmltree ... AndroidManifest.xml`, and
`apksigner verify --verbose --print-certs`: all PASS.

- APK SHA256: `142cfee7d7b368aa336a7439c737c6b587fea9e9b5a79c24ce38ddc6ae64ed5b`.
- Bytes21935253; package `org.localpilot.android.dev.unified`;
  version `0.8.5-unified-test-only`, code57.
- Debug certificate SHA256:
  `0f3c1a8a372b42d00dd3eae8a55808e6417e1bf1c3169c79f771de5c1f37b98e`.
- `classes.dex` and `classes2.dex` retained; ARM64 `localpilot_embedded`,
  real JSNES assets and source provenance present; no bundled GGUF weights.
- This debug signer is not accepted as a permanent production signer or upgrade proof.

Downloaded artifacts14212580998 (matrix),14212266019 (NES256),14211468223
(runtime/GGUF) also passed SHA256/ZIP-CRC checks. The original job log and
machine-readable evidence agree:

- API35 x86 emulator APK SHA256
  `2c4df4758dc26eb774dfc60b96491034e03728fca0425010ab5045ee54126c22`.
- Real JSNES256 Student actions,16 immutable replay batches, frames20→4116,
  verified ROM-RAM task return1.6875, STOP blocks completed actions,
  active policy preserved. This does not prove policy improvement.
- Four real CPU TEXT_ONLY GGUF calls matched four NES transitions; model SHA
  `a281c127e388b16537fd3f5d31ab32d3f87739e6dc514ac1093c80d9eac8d097`;
  final frame81. GGUF rewards remain UNKNOWN and cannot enter reward-based PPO.
- Six UI profiles passed, but their prior host holdout had only six pairs and
  `NO_PROVEN_IMPROVEMENT`. They were not the required20-pair evaluation.

## Accepted source update; new CI still required

The existing integration branch incorporates current main through merge
`f9c49e1188bd40aa137d45d3293ffbc43fb9220c`; parents are the initial PR head and
`32838fde`. Android QA skill/scripts/workflow/AGENTS instructions are preserved.
No main merge, rebase, force push, duplicate PR, second JNI or second controller.

One missing measurable G5 contract was confirmed in current code:
`StudentEvaluationGate.MIN_SCENARIOS=5`, with mean-only improvement; the actual
Android exam generated six pairs. A new19-pair winning synthetic contract test
failed against that implementation before the fix: it was incorrectly accepted.

Only compatible evaluation invariants from #150 are adapted:

- At least20 disjoint paired resets, exact deployed weights/ROM/action/epoch and
  training-state exclusions preserved; current `HomebrewObs64.taskMask` retained.
- Median improvement must exceed `max(0.05, abs(baselineMedian)*0.05)`, alongside
  the previous positive return, mean improvement>=0.1 and>=60% pair wins.
- Reports include baseline/candidate medians. A high-mean outlier with negligible
  median progress is rejected.19 complete pairs cannot pass either the Java
  evaluator or Android matrix collector.
- The existing JSNES evaluator selects the first20 eligible preset recipes by
  real button warmup, never by reward or candidate wins. No fabricated RAM resets.
  Warmups retain the minimum four-frame button latch and release assertion.
- No promotion is enabled: `NO_PROVEN_IMPROVEMENT` retains active weights;
  physical-device holdout and atomic promotion/rollback remain separate gates.

The synthetic Java/Python tests are contract tests only. Real host-JSNES
evaluation and new Android CI results must be read separately. A fixed test-only
left/right policy positive control is never evidence that PPO improved.
Final head/source SHA, CI links and artifact hashes are published in PR #175
and #159 after those runs actually finish; this earlier source-bound evidence
must not be substituted for the new SHA.

Local real-JSNES check with the modified gate:16 real rollout steps,4 Adam
updates,4999 changed parameters, distinct persisted candidate SHA. Then20 pairs,
40 independent resets,160 evaluated actions: active/candidate mean0 and median0,
`NO_PROVEN_IMPROVEMENT`, active bytes unchanged. A separate fixed left/right
test-only positive control evaluates20 pairs and is accepted; it is not a trained
candidate. Synthetic contract20 assertions and Android matrix9 tests also PASS.

## Reproduction and scope

### First new matrix failure: navigation before sustained play

At head7df5f14 / tested merge58de9e3, All-in-One run43846086850
job133228961525 failed before any game action: after the Harness navigation tap,
MainActivity remained resumed and the test refused `Unexpected app route`.
Artifact14216095729 SHA256
`51146c843f893a0fd84f20776fc01378bb6d1f0a2b6f47fda68bb42541bf301a`
was downloaded, CRC checked and extracted. The screenshot shows the actual Main
screen and Harness button; bounded crash log is empty. Pre-tap UI/coordinates
were not retained, so the low-level reason for the first no-op remains UNKNOWN.
It is not evidence of a broken Student loop or a verified application crash.

The exact x86 APK2061c671d2220261b6159e194503bc639db44976a297273a17d9a6f1821ee740
was installed only into previously absent `org.localpilot.android.dev.unified`
in the local API30 software AVD, preserving existing0.8.4 data/package. Three cold
Main→Harness taps succeeded on their first attempt. This is scoped local
navigation evidence, not reproduction of the API35 failure or game acceptance.

The test-only recovery now treats this observed Main no-op like its pre-existing
known ModelLab-neighbor recovery: at most one new UI navigation attempt, only
after verifying Main, visible Harness and no app ANR. Each tap uses a fresh dump;
actual Dashboard resume is still mandatory. Foreign routes, missing app/button,
ANR or a second no-op fail. No hidden Activity launch, game-action retry, timeout
increase or production app/STOP/input change is introduced.26 negative/positive
UI contract tests pass. Successful/failed reports now retain bounded tap
coordinates/route metadata; failure also retains the pre-tap XML outside Git.
This fixes the test's missing known-route recovery, not an asserted compositor
or application cause. Full CI on the follow-up head is required again.

Use the existing `.github/workflows/nes-runtime.yml` for real training plus
20-pair holdout, and `.github/workflows/localpilot-all-in-one.yml` for actual
ARM64 packaging, identical x86 acceptance APK, sustained256, GGUF and six AVDs.
Focused host commands:

```sh
python3 -m unittest discover -s tests -p test_verify_harness_apk.py -v
python3 -m unittest discover -s tests -p test_summarize_android_matrix.py -v
python3 -m unittest discover -s .agents/skills/android-qa-emulator-pro/tests -v
```

Local SDK35/JDK21 and a booted API30 x86 AVD were inspected. `/dev/kvm`, NDK
and CMake are absent locally; native builds and full Android gates use CI.
No APK was installed over preserved local0.8.4/code56, no app data/model/replay
or old version was deleted. Raw downloaded evidence remains outside Git at
`/workspace/harness175-evidence/`; weights and screenshots are not committed.

Unverified: physical POCO/tablet; stable signer and non-destructive upgrade
([#99](https://github.com/GiVscSon/LocalPilot-Android/issues/99)); runtime offline
egress guard; physical Vulkan/mmproj; arbitrary-ROM autonomy; REALTIME sustained
policy; on-device holdout/promotion/rollback and all ten Market games.
BUILD/PACKAGING/UI/INFERENCE/TRANSITION evidence stays scope-specific.
TRAINING_PASS with accepted improvement, DEVICE_PASS and RELEASE_PASS are not claimed.

Next measurable gate after the new same-source CI: independently verify the
candidate lifecycle on Android with20 disjoint pairs and retained active bytes;
do not enable promotion until rollback and physical acceptance are proven.
