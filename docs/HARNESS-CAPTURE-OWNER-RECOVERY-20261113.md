# Strict capture acceptance: confirmed root cause, 2026-11-13

Base #131 source6dc2d8f: the first actual API34 takeScreenshotOfWindow succeeded with four landmarks and exact1080x1920 geometry. The next owner check failed before production capture or input.

Test-only bounded diagnostics on #140 identified the actual focused TYPE_SYSTEM window: Android's first-use immersive navigation tutorial, “Viewing full screen / To exit, swipe down from the top / GOT IT”. The fresh AVD therefore correctly lost a verified focused application owner. The interaction-cache hypothesis was disproved; the trial production clearCache change was removed. Production root/focus selection remains unchanged.

The runner, guarded to refuse physical devices, configures only this navigation onboarding (`immersive_mode_confirmations=confirmed`) before launching the fullscreen fixture, then records its readback. This is not screenshot permission, secure-content bypass or a production setting change. isAccessibilityTool=false, focused owner/window/geometry, TTL, InputPermit revocation and secure-window rejection remain mandatory.

All six jobs passed in [run43458665012](https://github.com/GiVscSon/LocalPilot-Android/actions/runs/43458665012), source HEAD ee03b48dfb0359026044e260eefeb24e12059d7d. Actual API30/34/35/36 screenshots verify pixels/geometry, rejection of replaced and secure windows, late callback suppression after STOP and no resurrection of a diagnostic frame after reader STOP. Evidence identifies the concrete test-merge SHA separately from the branch head.

PR#141 incorporates these changes; its combined SHA requires its own matrix. No physical phone/tablet, native inference, external-game skill or release readiness is claimed.
