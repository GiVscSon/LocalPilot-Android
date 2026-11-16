---
name: android-qa-emulator-pro
description: Professional Android developer, emulator operator and evidence-driven QA tester. Use to debug Android SDK/ADB/KVM, build and inspect APKs, simulate Android conditions, boot AVDs, run UI/instrumented tests on phones and tablets, diagnose performance/crashes, and verify LocalPilot NES/ModelLab gameplay and model inference without faking physical device evidence.
---

# Android QA & Emulator Pro

Use this skill for Android development, testing, simulation, AVD, SDK, ADB, APK, Kotlin/Java, JVM/Android CI and device diagnosis. Report in Russian if the user writes Russian.

## Core rules

1. **Inspect first:** Read `AGENTS.md`, current GitHub `main` SHA, open PRs, and [Control Tower #159](https://github.com/GiVscSon/LocalPilot-Android/issues/159). Never substitute old chat/project memory for live source.
2. **Classify precisely:** `HOST_UNIT` (JVM/Python), `HARDWARE_TWIN_SIMULATED` (software model), `EMULATOR_ANDROID` (actually booted Android AVD), `PHYSICAL_ANDROID` (actually connected phone/tablet). Never mix their evidence.
3. **Observe real results:** build != installed; installed != running; `ANDROID_ACK` != game move; screenshot difference != task completion; file imported != model inference; PPO output != model improvement. An accepted action needs a fresh semantic game state transition.
4. **No fake capabilities:** KVM cannot be installed by an Android SDK tool. If /dev/kvm or internet is blocked, use verified GitHub Android runners or physical device; do not claim local emulator PASS.
5. **Single control plane:** Reuse LocalPilot `HarnessCoordinator`, `ActionGate`, `InputPermit`, `LearningCoordinator`, JSNES and existing JNI. No duplicate emulator, fake policy runner or second gesture owner. Preserve STOP, TTL, epoch, ROI safety and fail-closed.
6. **Separate provenance:** Record exact source SHA, APK SHA256, signer cert, ABI, SDK version, AVD serial/API/profile, ROM SHA, model SHA, and evidence label on each acceptance. No stable release claim from debug emulator runs.
7. **Use existing tests:** Prefer repo scripts, `.github/workflows/localpilot-all-in-one.yml`, `tools/verify_g0_chain.py`, `tools/unified_in_app_smoke.py`, `tools/harness_student_device_acceptance.py` and PR branch tests if not yet merged.

## Procedure

**1. Diagnose local tooling:** Run `python3 .agents/skills/android-qa-emulator-pro/scripts/android_qa_probe.py doctor --output build/android-host-doctor.json`. When a selected serial is connected run `python3 .agents/skills/android-qa-emulator-pro/scripts/android_qa_probe.py device --serial emulator-5554 --require-boot --output build/android-device.json`. CLI presence does NOT prove Android boot. Use `--require-sdk` and `--require-kvm` for strict gates.

**2. Choose execution tier:** Host/JVM for algorithms; hardware twin for simulation only; KVM-enabled Android emulator for real Android OS/UI; physical POCO X7 Pro/tablet for final APK/Vulkan/signer/upgrade acceptance. For API coverage use phone + tablet and API 30,35,36 where available. Never label simulated twin as physical device.

**3. Build and check:** Confirm `minSdk`/`compileSdk`/`targetSdk`/ABI and pinned SDK/Gradle versions, run JVM contracts then Gradle build, inspect APK (aapt, apksigner, 16-KiB ELF/ZIP), match byte SHA across reports. Do not alter release signing or uninstall user data to pass a test.

**4. Boot a selected AVD:** Only when KVM is verified and AVD exists, run `scripts/android_avd_smoke.sh AVD_NAME emulator-5554`, or the already established GitHub Android emulator workflow. Confirm ADB state, `sys.boot_completed=1`, API/ABI and device profile. Running the binary with no Android OS boot is a blocker.

**5. Prove UI and gameplay:** Install the correct ABI test APK and collect actual app process, screenshot, logcat and instrumentation reports. Verify chain `fresh frame → ROI/read → actual model policy → permitted action → Android ACK → NEW frame/game RAM → semantic state change/reward`. Test STOP after action in flight; ensure no new completed gestures after STOP. Run long sessions and save/resume. Use no ADB input in third-party apps without authorization.

**6. Debug responsibly:** Find first failing step and related timestamp in CI. Distinguish SDK network outage, offline ADB, emulator boot, ABI/JNI mismatch, screenshot staleness, ROI covering board, GGUF not loaded, and unverified movement. Collect bounded, sanitized logs; do not leak user tokens, private ROMs or model files.

**7. Accept and synchronize:** Use `references/ACCEPTANCE-MATRIX.md` for PASS/FAIL/UNKNOWN gates. Cite exact branch/head SHA, CI run/job, screenshot/report and blocker. Update Control Tower #159. GitHub issue does not broadcast to historical ChatGPT/Gemini conversations.

## References and tooling

- `scripts/android_qa_probe.py`: deterministic read-only doctor and serial-specific Android boot inspection.
- `scripts/android_avd_smoke.sh`: KVM-enforced Linux AVD boot smoke test, no APK install or input.
- `references/ANDROID-RUNBOOK.md`: commands, troubleshooting and security constraints.
- `references/ACCEPTANCE-MATRIX.md`: evidence tiers, QA matrix and release criteria.
- `tests/test_android_qa_probe.py`: negative and positive tests. Run `python3 -m unittest discover -s .agents/skills/android-qa-emulator-pro/tests -v`.

Authoritative Android docs: https://developer.android.com/tools/adb ; https://developer.android.com/studio/run/emulator-commandline ; https://developer.android.com/training/testing/fundamentals ; https://developer.android.com/studio/test/managed-devices
