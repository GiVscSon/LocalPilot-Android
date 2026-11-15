#!/usr/bin/env bash
# Install Android SDK command-line tools on a persistent Linux runner. Does not grant KVM.
set -euo pipefail
MODE="${1:---check}"
ROOT="${ANDROID_SDK_ROOT:-$HOME/.local/share/android-sdk}"
URL='https://dl.google.com/android/repository/commandlinetools-linux-15859902_latest.zip'
HASH='4e4c464f145a7512b57d088ac6c278c03c9eea610886b35a5e0804e74eedf583'
check() {
  echo "SDK root: $ROOT"
  for c in java curl unzip sha256sum adb emulator sdkmanager; do
    command -v "$c" 2>/dev/null || echo "$c MISSING"
  done
  for c in "$ROOT/platform-tools/adb" "$ROOT/emulator/emulator" "$ROOT/cmdline-tools/latest/bin/sdkmanager"; do
    [[ ! -x "$c" ]] || echo "INSTALLED $c"
  done
  if [[ -c /dev/kvm && -r /dev/kvm && -w /dev/kvm ]]; then
    echo "KVM available"
  else
    echo "KVM unavailable: use a KVM-enabled GitHub runner for emulator tests"
  fi
}
case "$MODE" in
  --check) check ;;
  --install-sdk|--install-emulator)
    for c in java curl unzip sha256sum; do
      command -v "$c" >/dev/null || { echo "Missing $c" >&2; exit 2; }
    done
    if [[ "$MODE" == --install-emulator && ! -c /dev/kvm ]]; then
      echo "Cannot install functional accelerated emulator without /dev/kvm" >&2; exit 3
    fi
    mkdir -p "$ROOT/cmdline-tools"
    TMP="$(mktemp -d)"
    trap 'rm -rf "$TMP"' EXIT
    if [[ ! -x "$ROOT/cmdline-tools/latest/bin/sdkmanager" ]]; then
      curl --fail --location --retry 2 --connect-timeout 15 -o "$TMP/sdk.zip" "$URL"
      echo "$HASH  $TMP/sdk.zip" | sha256sum -c --status || { echo "Bad Android SDK SHA256" >&2; exit 4; }
      unzip -q "$TMP/sdk.zip" -d "$TMP/unpacked"
      [[ -x "$TMP/unpacked/cmdline-tools/bin/sdkmanager" ]] || { echo "Unexpected SDK archive" >&2; exit 5; }
      mv "$TMP/unpacked/cmdline-tools" "$ROOT/cmdline-tools/latest"
    fi
    MANAGER="$ROOT/cmdline-tools/latest/bin/sdkmanager"
    "$MANAGER" --sdk_root="$ROOT" "platform-tools" "platforms;android-35" "build-tools;35.0.0"
    if [[ "$MODE" == --install-emulator ]]; then
      "$MANAGER" --sdk_root="$ROOT" "emulator" "system-images;android-35;default;x86_64"
    fi
    echo "SDK installed at $ROOT. Review SDK licenses with: $MANAGER --sdk_root=$ROOT --licenses"
    echo "Add $ROOT/platform-tools and $ROOT/cmdline-tools/latest/bin to PATH"
    check
    ;;
  *) echo "Usage: $0 [--check|--install-sdk|--install-emulator]" >&2; exit 2 ;;
esac
