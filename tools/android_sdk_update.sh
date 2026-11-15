#!/usr/bin/env bash
# Refresh the installed Google Android SDK on Linux. Stable channel only.
# No silent source-signing changes, no Android API target migration.
set -euo pipefail

MODE="${1:---check}"
ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/.local/share/android-sdk}}"
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MANAGER="$ROOT/cmdline-tools/latest/bin/sdkmanager"

if [[ "$MODE" != "--check" && "$MODE" != "--update" &&
      "$MODE" != "--update-ci" && "$MODE" != "--install-emulator" ]]; then
  echo "Usage: $0 [--check|--update|--update-ci|--install-emulator]" >&2
  exit 2
fi

print_state() {
  echo "ANDROID_SDK_ROOT=$ROOT"
  for name in platform-tools platforms/android-30 platforms/android-35 \
              platforms/android-36 build-tools/35.0.0 cmdline-tools/latest \
              emulator system-images/android-35/default/x86_64; do
    local prop="$ROOT/$name/source.properties"
    if [[ -f "$prop" ]]; then
      local rev
      rev="$(awk -F= '/^[[:space:]]*Pkg\.Revision[[:space:]]*=/ {gsub(/[[:space:]]/, "", $2); print $2; exit}' "$prop")"
      printf '  %-46s %s\n' "$name" "${rev:-installed}"
    else
      printf '  %-46s MISSING\n' "$name"
    fi
  done
  if [[ -c /dev/kvm && -r /dev/kvm && -w /dev/kvm ]]; then
    echo "KVM_AVAILABLE=yes"
  else
    echo "KVM_AVAILABLE=no (SDK installation can still work; emulation may be slow/unavailable)"
  fi
}

if [[ "$MODE" == "--check" ]]; then
  print_state
  exit 0
fi

# An SDK manager must be provided by the host, or obtained via the
# existing SHA-256 pinned official Android command-line-tools bootstrap.
if [[ ! -x "$MANAGER" ]]; then
  echo "SDK manager absent; attempting checksum-verified official bootstrap."
  if ! ANDROID_SDK_ROOT="$ROOT" bash "$HERE/bootstrap_android_sdk.sh" --install-sdk; then
    echo "Android SDK download is unavailable. Check DNS/network access and" >&2
    echo "the Android SDK license state. This host may block Google endpoints." >&2
    exit 3
  fi
fi

[[ -x "$MANAGER" ]] || { echo "sdkmanager was not installed in $ROOT" >&2; exit 4; }
export ANDROID_HOME="$ROOT" ANDROID_SDK_ROOT="$ROOT"

# Deliberately keep production compile ABI, AGP, pinned Build Tools,
# NDK and CMake versions unchanged. Update stable platform-tools (ADB),
# plus the SDK platforms required for the emulator compatibility matrix.
PACKAGES=('platform-tools' 'platforms;android-30' 'platforms;android-35'
          'platforms;android-36' 'build-tools;35.0.0')

if [[ "$MODE" == "--update-ci" ]]; then
  PACKAGES+=('cmake;3.22.1' 'ndk;27.2.12479018')
fi
if [[ "$MODE" == "--install-emulator" ]]; then
  PACKAGES+=('emulator' 'system-images;android-35;default;x86_64')
fi

echo "Installing/verifying Android SDK stable-channel packages..."
"$MANAGER" --sdk_root="$ROOT" --channel=0 "${PACKAGES[@]}"
# Update the SDK *manager itself* last, after using the existing binary to
# upgrade Android API packages. This package is officially cmdline-tools;latest.
# Google stable Command-line Tools 22.0 contains the new Android CLI.
"$MANAGER" --sdk_root="$ROOT" --channel=0 'cmdline-tools;latest'
# sdkmanager may install CLI 22 into 'latest-2' if preinstalled 'latest'
# is obsolete; switch only after checking both executables and revision.
python3 "$HERE/promote_android_cli.py" --sdk-root "$ROOT"
print_state
if [[ "$MODE" == "--install-emulator" && ! -c /dev/kvm ]]; then
  echo "Android Emulator binaries may be installed, but /dev/kvm is absent."
  echo "Use the GitHub Actions KVM runner for actual Android emulator acceptance."
fi
echo "If required, accept SDK licenses interactively with:"
echo "  $MANAGER --sdk_root=\"$ROOT\" --licenses"
