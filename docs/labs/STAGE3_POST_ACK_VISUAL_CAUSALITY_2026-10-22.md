# LocalPilot Android · Stage 3: feedback from post-ACK frames only (22.10.2026)

## Scope and version honesty

**Lab is based on the user-provided 0.6.0-dev source plus isolated Stage 2 patches.** This GitHub checkout is still 0.4.0-dev and the last independently received installable APK is 0.7.10-dev. The 0.7.11-dev Cloud version has not been recovered in full. This commit records tests and handoff only; no production APK or Android source in GitHub is modified.

## Confirmed race and patch

The real `PilotService.finishArcadeFrame` in 0.6.0 uses `ArcadePolicy.response(arcadePending,arcadeBefore,observation)`, which checks only that `after.timestampMs>before.timestampMs`. It could therefore accept a frame **captured before Android onCompleted** but processed after callback, misleadingly marking visual action success.

Stage 3 adds `ArcadePolicy.responseAfterAck(action,before,after,ackAtMs)` that demands `after.timestampMs>ackAtMs` and `ackAtMs>=before.timestampMs`, in addition to original same-profile/useful-observation/direction checks. The `PilotService` callback records `SystemClock.elapsedRealtime()` as `arcadeAckAt`; pending, STOP, accepted response and restart clear it. A pre-ACK frame cannot verify a gesture, regardless of its processing time.

## Tests

- `java tests/ArcadeVerify.java`: 44 profile + 9 previous temporal + 87 traffic pixel-loop + **276 causal-feedback**, ALL PASS.
- The 276 comprise 18 scenario checks and a **258-combination grid** for ACK/capture times, plus one-use ActionGate/STOP/rearm/window/package tests. New test first verifies the old response accepts an obviously pre-ACK image, then verifies the guarded response refuses it.
- `java tests/Verify.java tests/legacy_parity.txt`: **9691 Java assertions PASS**, including these new checks (sum of group counts).
- Standalone full RL `java tests/RlVerify.java` in Stage3 attempted but interrupted by execution timeout; **no new PASS claim**. Previous Stage2 unchanged RL code had **680187 PASS**, with saved logs.
- Patch verification `git apply --check` and `git apply` PASS against clean Stage2 source, plus byte-exact comparison of 5 changed/new files. Nine source-level Android `PilotService` invariants PASS.
- **Not tested:** Android SDK Gradle compile, instrumented Android, actual MediaProjection, external-game achievements, current 0.7.10 or 0.7.11 code. A binary does not result from this patch.

## Artifacts and next gate

Google Drive: https://drive.google.com/drive/folders/1nhnjW4e3CAz8hQiOtKSosZGY2PVcEYOM

- `LOCALPILOT_ANDROID_STAGE3_VERIFIED_2026-10-22.zip` (234439 B, SHA-256 `4c02d5cadec58db68f45a51823858d34ca22184e21243b7b34c306595fcea017`), including full 0.6 stage3 source ZIP, patch, tests/logs/manifest.
- `LOCALPILOT_STAGE3_POST_ACK_VISUAL_GUARD.patch` SHA-256 `2da87a33dcf720a3bf1a43832a4e4bd9c82a581bf71b7f7b7c09d7d7952d145b` and separate file.
- `LocalPilot_Android_0.6.0_stage3_source.zip` SHA-256 `cacc22841cf111cc0eaaf4d27bb23cfcbeddc8ec3d3c497f299f3405b02422da`.
- Copies in ChatGPT Library under `/LocalPilot Android/`.

**Next step:** in actual newest Android checkout first check version/git status/commit/source hashes and original tests. Look for equivalent fix in ArcadeFeedback/ArcadePolicy/PilotService; merge only if missing; build source-derived 0.7.11 in its own workspace and run real instrumented/device input, stale-frame, STOP/background and window-change acceptance. Do not replace active Cloud code with 0.6 code, reinstall via incompatible key or lose experience stores.