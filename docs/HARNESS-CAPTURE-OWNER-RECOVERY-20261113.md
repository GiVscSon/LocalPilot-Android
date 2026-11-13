# Window capture owner recovery, 2026-11-13

Base PR#131 source6dc2d8f. Actual API34 evidence artifact14023100582 was independently checked against ZIP digest656156a8fc05f312eb49ca505e87084685c5a78bd9bdfb2a8be6e30d045830e0 and CRC. First takeScreenshotOfWindow succeeded with all four landmarks and exact1080x1920 geometry. Failure happened afterward, before any production capture/input: no verified focused fixture owner. API30 passed; API34/35/36 must not be declared passing from compilation alone.

The production root reader now makes at most one read-only interaction-cache refresh on API34+ when no verified focused application root exists. It still requires TYPE_APPLICATION, focused=true, matching root/window IDs. Window/ROI identity and TTL remain unchanged; no permission or isAccessibilityTool escalation, no gesture retry and no fallback after secure failure.

The isolated instrumentation records bounded window metadata before finish (the runner's later dumps observe a process already stopped by Instrumentation.finish). It reports a cache-recovered observation explicitly, or fails with pre/post root/focus details. The refresh is a safe recovery hypothesis pending actual emulator confirmation, not a claimed final diagnosis. Test-only metadata contains no screenshots and is limited to the isolated emulator.

Local Android/app+AndroidTest compilation and lint PASS. Actual API30/34/35/36 CI pending new source. Concurrency avoids duplicate push/PR jobs. These two Harness workflows use github.sha, the same concrete PR test-merge source as other mandatory workflows; report/APK source metadata must match that SHA, not be confused with PR head metadata.
