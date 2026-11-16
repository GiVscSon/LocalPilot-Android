# Android CLI & emulator professional runbook

## Environment and SDK

Run:
`python3 .agents/skills/android-qa-emulator-pro/scripts/android_qa_probe.py doctor --output build/android-doctor.json`

- Host script reports what is **installed**, not what has been run.
- In LocalPilot prefer reviewed `tools/android_sdk_update.sh`, `tools/android_sdk_inventory.py` and PR #173 (verify current PR/merge state). Keep required API30/35/36, pinned NDK/Build Tools/JNI.
- Android Linux accelerated AVD needs available /dev/kvm. Android SDK cannot provision KVM. On Windows use Windows Hypervisor Platform; on macOS use Hypervisor.Framework.
- `emulator -accel-check` and `emulator -list-avds` help verify the host. Use software emulation only as explicit extremely slow experimental fallback, not main CI device acceptance.

## Real AVD and ADB

For a configured **existing** AVD on an authorized KVM host:

`bash .agents/skills/android-qa-emulator-pro/scripts/android_avd_smoke.sh EXISTING_AVD emulator-5554`

The smoke tool uses a specific serial, checks KVM and boot, then terminates the test AVD. It **does not** install or run an app.

For a running device, read-only probes:

`adb devices -l`
`adb -s emulator-5554 shell getprop sys.boot_completed`
`adb -s emulator-5554 shell getprop ro.build.version.sdk`
`adb -s emulator-5554 shell getprop ro.product.cpu.abi`
`adb -s emulator-5554 shell wm size`
`adb -s emulator-5554 shell wm density`
`adb -s emulator-5554 exec-out screencap -p > screenshot.png`
`adb -s emulator-5554 logcat -d -t 500 -v threadtime > logcat.txt`

Use source/target screenshots together with game-specific vision/RAM state. Different screenshot hashes prove only changed pixels, not that intended movement occurred. Use sanitized logs; collect full `bugreport` only with explicit approval.

## APK and emulator acceptance

1. Exact SHA and source.
2. Build debug ARM64 candidate using existing Gradle workflow.
3. Verify AAPT badging, `apksigner verify --print-certs`, native .so ABI, dex, source provenance and 16KiB alignment where relevant.
4. Build **test-only x86_64 APK** for x86_64 AVD. Never claim ARM64 bytes were the ones installed in an x86_64 AVD.
5. Boot and run phone/tablet API30/35/36 from existing `.github/workflows/localpilot-all-in-one.yml`. Retain JSON from real app actions.
6. If one AVD job times out or drops ADB, distinguish infrastructure vs app failure; preserve evidence, rerun failed job, then full matrix on one SHA.
7. LocalPilot STOP is fail-closed. Check stale observations and post-STOP attempts.

## Common defects and first actions

- DNS blocked: SDK Google archive cannot be downloaded inside restricted sandbox; use connected CI with official SDK or environment with network. No bypass or invented install.
- CLI v12 still active after installing v23: Google may install in `cmdline-tools/latest-2`; use project-reviewed `tools/promote_android_cli.py` with rollback, not destructive overwriting.
- ADB offline: recheck device list and explicit serial; never accept partially returned binary save stream with nonzero exit.
- Native/ABI mismatch: inspect .so architecture and emulator image ABI.
- Model listed but no JNI inference: prove actual model engine call, model SHA, action provenance, frame/RAM semantic change.
- Emulator graphics: check `emulator -help-gpu`; software-rendered AVD GPU performance is not Vulkan/POCO performance.

## Authoritative references

- https://developer.android.com/tools/adb
- https://developer.android.com/studio/run/emulator-commandline
- https://developer.android.com/training/testing/fundamentals
- https://developer.android.com/studio/test/managed-devices
- https://developer.android.com/tools/logcat
