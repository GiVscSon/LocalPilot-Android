# LocalPilot Android 0.7.11-dev · temporal guard review (2026-10-22)

**Source:** user-supplied complete `ArcadeSystemTest` snippet and incomplete `ArcadeController` snippet ending inside `fly()`; expected `ArcadeController.java` SHA256 `876dedcb3f8c47df7446d24ddf10a4c55eed7ef5304007f9d2abf1766710935c` is **not independently verified in this workspace**. This GitHub branch still contains older Android 0.4.0-dev sources; none of the production source files were modified.

## Proven issues in isolated model reproductions

1. In the supplied `ArcadeController.decide`, an older same-profile timestamp triggers `reset()` and is then still accepted, allowing a stale observation to produce an action. Duplicate timestamps can also produce actions. Proposed guard returns WAIT on timestamp <= previous.timestampMs and does not rewind history.
2. In `ArcadeController.observeGesture`, `abs(after.player.x - before.player.x)` learns steering speed from movement *opposite* to the requested LEFT/RIGHT action. Proposed guard requires sign-consistent displacement and ordered feedback timestamp, bounded to <=1500ms (must reconcile with actual feedback/gesture contracts).
3. `ArcadeSystemTest` checks `actions>0` and `confirmed>0` but lacks explicit `predictive.passed>=baseline.passed` and `predictive.deaths<=baseline.deaths` assertions. The age grid `{0,80}` never crosses the 300ms dispatch freshness boundary, making `unsafe==0` insufficient safety coverage.

## Evidence

- **14 isolated Java assertions PASS**, OpenJDK21, in test harness with a minimal substituted planner, NOT the full Cloud Android sources.
- Python patcher replacement anchors check PASS on six user-supplied snippet patterns.
- Safe patcher intentionally refuses `/mnt/data/localpilot_060_audit/LocalPilot-Android` (source file absent). Default output `*.candidate`, apply mode requires exact known SHA and creates `*.before-0711-safety` backups.
- No actual Android build, user-device test or complete 0.7.11 controller test was run. Do not call this a shipped fix.

Full code/test archive (8,067 bytes; SHA256 `00436521fc8ad1ae091f43f6dd9f295b4c2d5bfa85929f574eb7310d9276f344`) and README:
https://drive.google.com/drive/folders/1nhnjW4e3CAz8hQiOtKSosZGY2PVcEYOM , file `LOCALPILOT_0711_CONTROLLER_LAB_2026-10-22.zip`. Copy also in ChatGPT Library `/LocalPilot Android/`.

## Handoff to active Cloud/Codex thread

In **actual 0.7.11 checkout** first check git status and full ArcadeController SHA; use patcher dry-run, inspect candidates, then run real `java tests/ArcadeSystemTest.java` and relevant Gradle/Android tests with production classes. Do not replace newer Cloud sources with this old GitHub branch. Preserve private videos and all training checkpoints. An instrumentation-only compilation does not constitute live device acceptance.
