The repository previously contained only a handoff. This imports the complete Android 0.3.2 source supplied by the user; ZIP CRC and all 36 source checksums matched. No APK, signing key, SDK, model, cache, or user data is committed.

The original Hide action stopped playback, and Demo x10 started under the expanded overlay. Collapse/Expand now keeps STOP visible and preserves the active binding; Stop and Hide still revoke input. The demo waits for the compact panel layout before its first gesture.

A rejected parser previously left READ/POLICY/ANDROID_ACK at n=0 without an error count. Stage reports now include attempts, errors, cancellations, and stable last-error codes while retaining successful latency samples. Stop/timeout retires pending capture requests so a late callback cannot resume playback or overwrite a new capture.

Debug builds use org.localpilot.android.dev and install alongside the existing signed app. The original signing key was not provided. Gradle wrapper, a native UI instrumentation runner, portable packaging, and an updated handoff are included.

Validation:

- JVM Verify passed 8902 assertions, including real parser fixtures for valid palettes, rejected ROI/palette, and diagnostic failure propagation.
- Gradle app, Android test APK, and lint passed: 0 errors, 15 warnings. App/test signatures verified.
- Standalone build, zipalign, and signing passed; wrapper checksums verified against official values.
- Android instrumentation was compiled but did not run. The software-only API30 emulator restarted zygote/framework before app launch; adb install failed with Package Manager Broken pipe. This is an environment blocker, not a gameplay result.

The specific n=0 failure on the user's phone remains unconfirmed. Device validation still needs actual capture/read/policy/gesture rounds and Stop/focus/rotation checks. This draft is ready for code review, not a claim of full Android acceptance.
