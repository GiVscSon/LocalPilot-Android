# Android CLI skill for LocalPilot coding agents

**Status: PROPOSED (Draft PR), not yet merged.** Source baseline: `main` at `fbbbeb9cb4ffa54ecc26582e4a640b963b80b8d3`, 2026-11-15.

## What is installed in the repository

- `.agents/skills/android-cli/SKILL.md` is Google's official Android CLI agent skill, from [android/skills](https://github.com/android/skills/blob/main/devtools/android-cli/SKILL.md), upstream metadata `last-updated: 2026-11-14`.
- `.agents/skills/android-cli/LICENSE.txt` is the upstream Apache 2.0 license.
- Codex can discover project-scoped skills under `.agents/skills`. For another agent or a local machine, use the official installer rather than manually copying partial instructions.
- **Skill ≠ executable:** committing a `SKILL.md` does not install the `android` binary, SDK, platform-tools or emulators, and does not grant a GitHub agent access to a USB-connected phone.

## Local Windows installation (Codex and Gemini)

Install the Android CLI binary from the [official Android CLI guide](https://developer.android.com/tools/agents/android-cli). At a PowerShell prompt in the repository:

```powershell
android --version
./tools/install_android_cli_skill.ps1
# Or directly, without the wrapper:
android skills add android-cli --agent=codex,gemini --project=.
android skills list --long
```

If the CLI is not installed, the PowerShell script fails clearly rather than downloading/running an unreviewed installer. For other coding agents, specify `-Agents claude` or use `android skills add` with an explicit `--agent`. The official Codex plugin route is also available:

```powershell
codex plugin marketplace add android/android-cli-plugin
codex plugin add android-cli@android-cli-plugin
```

Do not assume the plugin or SDK is installed until the real command succeeds on the machine where the agent executes.

## Real hardware acceptance workflow

After an Android SDK and authorized device are available, start **read-only**, keeping the selected serial explicit:

```powershell
android info
adb devices -l
android layout --device=<device-serial>
android screen capture --device=<device-serial> -o localpilot-screen.png
android analyze app/build/outputs/apk/debug/app-debug.apk
```

Replace the placeholder serial and APK path with observed values; inspect the screenshot before any UI action. `android screen capture` is the correct command (not `android screenshot`). Record device/API, source SHA, package version, APK hash, signer hash, command output and timestamps. If there is no hardware/SDK, report `NOT_RUN` or `UNKNOWN`, never `DEVICE_PASS`.

Any user-authorized interactive test must follow **launch → observe → propose → ActionGate/InputPermit → act → verify a fresh screen/game state**, not use adb taps as evidence that an in-app game agent is safe. ADB/Android CLI UI automation is a diagnostic/test instrument; it must not bypass LocalPilot's STOP, epoch, TTL, ROI, UNKNOWN, fail-closed or single executor for production actions. `ANDROID_ACK` is not proof of game progress or training.

The skill is build/test **tooling only**: no new app screen, Android component, model runtime, emulator, trainer or extra APK. For acceptance and governance read [Control Tower #159](https://github.com/GiVscSon/LocalPilot-Android/issues/159) and [the single source of truth](LOCALPILOT-SINGLE-SOURCE-OF-TRUTH.md). Re-verify the latest main SHA and CI before release or merge.

## Validation

- Confirm the local skill has `name: android-cli` in YAML frontmatter and the license file is present.
- Confirm `android --version`, `android skills list --long`, `adb devices -l` and one read-only screenshot against the selected device in the actual workstation environment.
- This PR provides **no** `BUILD_PASS`, `UI_PASS`, `TRANSITION_PASS`, `TRAINING_PASS`, or `DEVICE_PASS` evidence; only reviewable tooling instructions.
