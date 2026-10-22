# LocalPilot Android · Stage 2: visual feedback and pixel closed loop (22.10.2026)

## Repository/source boundary

This is a **tested isolated patch** based on 0.6.0-dev source + the prior VisionLab patch. The actual GitHub branch still builds **0.4.0-dev**, and the latest received installable APK is 0.7.10-dev (source unavailable here); a newer 0.7.11-dev was referenced only via a Cloud workspace and partial snippets. Do **not** replace 0.7.10 / 0.7.11 with this older code or call it a new production APK. This commit adds documentation only, not Android runtime changes.

## Bug reproduced and fixed in isolated 0.6 source

`ArcadePolicy.response(action,before,after)` accepted a changed position with **the same timestamp**, giving a false confirmation of a gesture. A new test failed before the correction on `same timestamp must never verify a gesture`. Fix: require action nonnull, before+after usable and same profile, and `after.timestampMs > before.timestampMs`. Existing successful fixture responses now use genuinely later timestamps. Null action/previous/current, terminal and low-confidence observations are also tested.

## Whole-loop proof (synthetic only)

The new Java test `ArcadeTrafficLoopTest` uses **production** 0.6.0 `ArcadeVision`, `ArcadePolicy`, `ArcadePolicy.response` and `ActionGate`: draw RGB traffic scene -> vision -> policy -> one-use input ticket -> delayed toy steering effect -> render next RGB -> visually verify response. 12 deterministic scenarios (latency 0/100/200ms × 4 speed settings): idle baseline **0/12** obstacle clears, reactive control **12/12**; verifies no collision and receipt of visual response. Steering is deliberately simplified to a lane change after action latency; does **not** demonstrate real Android/game reliability or 0.7.11 predictive controller performance.

Additional authorization tests: revoked ticket on STOP/rearm, one-use consume, changed app/window and expired ticket. No physical Android, Gradle or emulator tests run in this environment.

## JVM evidence

- `java tests/Verify.java tests/legacy_parity.txt` **PASS**, totals **9415** assertions in the combined suite (counted from its printed individual groups).
- `java tests/RlVerify.java` **PASS 680187**.
- `java tests/ArcadeVerify.java` **PASS**: base 44, temporal 9, traffic-loop 87.
- `git apply --check` on pristine VisionLab source **PASS**, independently applying the diff to a copy produced byte-identical changed/new source files.

## Reproducible packages

Saved in Google Drive and ChatGPT Library:
https://drive.google.com/drive/folders/1nhnjW4e3CAz8hQiOtKSosZGY2PVcEYOM

- `LOCALPILOT_ANDROID_STAGE2_VERIFIED_2026-10-22.zip` (**233352 bytes**, SHA256 `a730415c7d0b2b9fb2cc09e7c3bac81bf929ba3b7104dcf5b0f5192a850c99d7`) contains patch, source ZIP, tests logs, SHA256SUMS and README.
- `LocalPilot_Android_0.6.0_stage2_source.zip` (**235590 bytes**, SHA256 `332474fabaa824ef1d914457ca8e99dfb3b922ba85a93689cef6a43e6bcca454`).
- `LOCALPILOT_STAGE2_FEEDBACK_CLOSED_LOOP.patch` is applicable **only** atop the prior VisionLab 0.6.0 version, not directly to 0.7.11.
- `STAGE2_FEEDBACK_CLOSED_LOOP_2026-10-22.md` describes actual test and reproduction.

**Next milestone:** obtain complete 0.7.11 code and freeze SHA, check guards before merging anything, instrument the actual dispatch timestamp (not just prior capture timestamp) on ACK→response validation, run full build and Android device game acceptance with STOP, collapsed overlay, window changes and gameplay outcome. Do not sign with an incompatible debug key or delete app training state.
