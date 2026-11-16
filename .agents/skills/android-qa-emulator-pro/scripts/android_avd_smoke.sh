#!/usr/bin/env bash
# Strict KVM-only AVD smoke; never installs APK or injects inputs.
set -euo pipefail
AVD="${1:?Supply an existing AVD name}"
SERIAL="${2:?Supply expected emulator serial}"
[[ "$AVD" =~ ^[A-Za-z0-9_.-]+$ ]] || { echo "Invalid AVD name" >&2; exit 2; }
[[ "$SERIAL" =~ ^emulator-[0-9]+$ ]] || { echo "Refusing ambiguous/non-emulator serial" >&2; exit 2; }
command -v adb >/dev/null || { echo "Missing ADB" >&2; exit 3; }
command -v emulator >/dev/null || { echo "Missing Android Emulator" >&2; exit 3; }
[[ -c /dev/kvm && -r /dev/kvm && -w /dev/kvm ]] || {
  echo "KVM unavailable; use GitHub Actions KVM runner" >&2; exit 4;
}
emulator -list-avds | grep -Fxq "$AVD" || { echo "Unknown AVD" >&2; exit 5; }
if adb devices | grep -Eq "^${SERIAL}[[:space:]]"; then
  echo "Serial already occupied; do not attach to another AVD" >&2; exit 6
fi
LOG="${ANDROID_QA_EMULATOR_LOG:-android-avd-emulator.log}"
emulator "@$AVD" -no-window -no-snapshot -noaudio -no-boot-anim -gpu swiftshader_indirect -accel on >"$LOG" 2>&1 &
PID=$!
stop() { if kill -0 "$PID" 2>/dev/null; then kill "$PID" 2>/dev/null || true; wait "$PID" 2>/dev/null || true; fi; }
trap stop EXIT
for _ in $(seq 1 120); do
  kill -0 "$PID" 2>/dev/null || { echo "Emulator exited: see $LOG" >&2; exit 7; }
  if [[ "$(adb -s "$SERIAL" get-state 2>/dev/null || true)" == device ]]; then
    BOOT="$(adb -s "$SERIAL" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')"
    QEMU="$(adb -s "$SERIAL" shell getprop ro.kernel.qemu 2>/dev/null | tr -d '\r')"
    if [[ "$BOOT" == 1 && "$QEMU" == 1 ]]; then
      API="$(adb -s "$SERIAL" shell getprop ro.build.version.sdk | tr -d '\r')"
      echo "EMULATOR_ANDROID_BOOT_PASS serial=$SERIAL api=$API (application untested)"
      exit 0
    fi
  fi
  sleep 2
done
echo "BOOT_TIMEOUT: not a successful Android test; inspect $LOG" >&2
exit 8
