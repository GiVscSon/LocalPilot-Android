# LocalPilot0.8.2/code54: tested candidate, release blocked

Application source: `8b6cfca368f991aa6eff28af69b72d77c6b5b304`. Reviewed PR head: `e5dc8e29fd1bde3df771ab6d13f0d7fcde5a122a`. PR #141 merged into main without changing the tested source commit.

37 checks SUCCESS. Four older concurrent jobs were cancelled and superseded by passing PR jobs. One optional preloaded standalone build was SKIPPED. These are integration results; no stable release is declared.

ARM64 test APK:21,852,085 bytes; SHA256 `9325edffdae25060b4f857d5f4bf593f0dff508b6f46177aedb1cde9fe882d4e`. Debug-signed, non-debuggable, not a compatible production update.

Unsigned production APK:11,733,245 bytes; SHA256 `86f49eb6e3541da692f2a0eea3a48bf53ffd2bf4f53b2a89a1dd62259a9441e2`. Packaging, source, ZIP CRC and ELF/ZIP16KiB verified; cannot be installed as a signed release.

## Actual Android WebView/Harness/PPO acceptance

Same x86 test APK in all six isolated emulators: `0dd6709d96e529229ad31b048c64908a078bf98f98ccc24317d95fa0549cb665`. Only this release-blocked test derivative enables run-as evidence access. ARM64 and production remain non-debuggable.

| API | Profile | Result | Verified steps | Core frames | Changed parameters |
|---|---|---|---|---|---|
| 30 | pixel_2 | PASS | 16 | 256 | 4999 |
| 30 | pixel_c | PASS | 16 | 256 | 4999 |
| 35 | pixel_2 | PASS | 16 | 256 | 4999 |
| 35 | pixel_c | PASS | 16 | 256 | 4999 |
| 36 | pixel_2 | PASS | 16 | 256 | 4999 |
| 36 | pixel_c | PASS | 16 | 256 | 4999 |

Each run verifies real CPU RAM transitions, candidate/active SHA and unchanged active weights. The six batches total96 actions/1536 emulated frames. Their random initial policies differ; individual returns in CSV are execution evidence, not a comparative quality benchmark.

## Independent precision-task evaluation

| Data seed | Known cases reached | Frozen cases reached by Student | Scripted | Random |
|---|---|---|---|---|
| 571 | 15/15 | 20/20 | 20/20 | 0/20 |
| 9091 | 15/15 | 20/20 | 20/20 | 0/20 |
| 12097 | 15/15 | 20/20 | 20/20 | 0/20 |
| 65537 | 15/15 | 20/20 | 20/20 | 0/20 |
| 270113 | 15/15 | 20/20 | 20/20 | 0/20 |

These are actual headless JSNES CPU/PPU task trials with independently rechecked trajectories and frozen weights. They do not establish physical Android acceptance or generic ROM skill. The Android matrix above tests the separate PPO64 profile. PPO weight updates are proven; improved independent PPO gameplay is not, so promotion remains blocked.

## Legacy ten-game simulator results

LEGACY-SIMULATORS.csv contains all ten scenes separately on three data seeds (30rows). Scores are specific to each mechanic. Provenance is SIMULATED_GAME; no result is promoted to VERIFIED_REAL.

## Fixed issues

- Completed observation timers/replies can no longer cancel a later step of the same episode.
- PPO reserves input between steps; generic start cannot bypass its prepared observation.
- STOP remains nonce/epoch/TTL fenced and waits for input-release ACK.
- Corrupt experience journals block append/rotation and are preserved.
- Checkpoint reads are bounded before allocation.
- Strict capture accepts actual API30/34/35/36 and rejects secure/stale windows; isolated immersive onboarding no longer steals fixture focus.
- ARM64 LOAD segments now align to16KiB, checked independently from ZIP alignment.
- Signing is isolated from application builds; legacy build-with-key jobs are retired.

## Release blockers and limits

- Permanent compatible production signing key unavailable; protected android-signing creation returns403
- Physical phone/tablet and non-destructive installed-app upgrade NOT_RUN
- PPO independent improvement not proven; candidates remain blocked
- Precision 7-input task success is headless; Android matrix exercises PPO64
- Imported ROMs have no generic trusted reward; built-in homebrew task only
- Unified APK uses CPU backend; Vulkan remains experimental, not physical POCO proof
- GGUF weights are on-demand/imported; 16 profiles are not16 bundled pretrained GGUF models
- Legacy ten-game simulator metrics are SIMULATED_GAME and not external-game acceptance

[PR #141](https://github.com/GiVscSon/LocalPilot-Android/pull/141) · [Runtime matrix](https://github.com/GiVscSon/LocalPilot-Android/actions/runs/43506503407) · [Headless task evaluation](https://github.com/GiVscSon/LocalPilot-Android/actions/runs/43506503114) · [Unsigned build](https://github.com/GiVscSon/LocalPilot-Android/actions/runs/43506753483)
