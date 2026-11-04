# LocalPilot GameLab: Android test companion + DOS / NES / Dendy

**Status:** the companion with ten original simulated games is a separate Android application under `game-lab/`. The ROM/core preflight for three historic platforms is implemented. **Full emulation has not yet been linked or physically tested.** Never label a ROM-header check a working emulator.

## Why a separate APK

The real `PilotService` correctly prevents itself from selecting its own package as a game window. A separate package `org.localpilot.gamelab` can be selected as the target while preserving ActionGate/InputPermit/STOP/TTL/Fail-Closed and genuine Android AccessibilityService + MediaProjection paths.

The GameLab module is deliberately distinct from the main LocalPilot Android application; this also isolates emulator-native crashes, ROM access, and copyleft dependencies. Building GameLab does not replace the original 0.8.1 app.

## Platforms

| Platform | Core | Mode | ROMs or DOS content |
| --- | --- | --- | --- |
| Nintendo NES / Famicom | FCEUmm | `fceumm_region=NTSC`, approx. 60 fps | `.nes`, `.unf`, `.unif` |
| Dendy | **same FCEUmm** | `fceumm_region=Dendy`, 50 fps | same NES cartridge files |
| MS-DOS | DOSBox Pure | Independent DOS timing/config | `.zip`, `.dosz`, `.exe`, `.com`, `.bat`, `.img`, `.ima`, `.iso`, `.conf` |

FCEUmm also supports Famicom Disk System, but `.fds` is not enabled in our UI until BIOS and rights handling are implemented.

Sources:
- https://docs.libretro.com/library/fceumm/
- https://docs.libretro.com/library/dosbox_pure/
- https://github.com/Swordfish90/LibretroDroid
- https://github.com/Swordfish90/Lemuroid

**License review is mandatory before bundling actual native cores.** FCEUmm and DOSBox Pure are GPLv2-licensed, while the LibretroDroid frontend is GPLv3. Build/distribution must preserve notices, compatible source rights and relevant conditions. We must not simply copy copyrighted games or firmware into the APK.

## What is already in this branch

- `game-lab/src/main/java/org/localpilot/gamelab/RetroEmulatorCatalog.java` maps the three consoles to core names, native library names, region options and content formats.
- `RetroLibraryActivity` is reached from the main GameLab screen. It uses Android Storage Access Framework to select a file, checks file type and header, and **explicitly reports missing native cores and runtime**. No fake playback, arbitrary Java execution or unsafe ZIP extraction.
- `tests/GameLabRetroVerify.java` tests core mapping and 21 safety/integrity assertions (deterministic JVM).
- `game-lab/build.gradle` and manifest package the independent target Android application.
- CI builds a separate `LocalPilot-GameLab-<SHA>` test APK when all compile/test gates succeed.

## Work still required for actual DOS/NES/Dendy playback

1. Choose and pin a legally distributable Libretro frontend bridge, with reproducible Android arm64-v8a and x86_64 build; check the GPLv2/v3 combination before shipping.
2. Build/pin **native FCEUmm and DOSBox Pure cores** for device ABIs, with provenance, exact source revisions, SHA-256 and applicable licenses; package appropriately in `jniLibs`.
3. Implement Android on-screen D-pad/A/B/Start/Select and DOS virtual keyboard/mouse; render real emulated pixels and obtain genuine core frames/audio.
4. Select `fceumm_region=Dendy` **before core content launch** when Dendy is requested. Test 50 fps versus NTSC 60 fps.
5. Set up deterministic open-source/public-domain test cartridges/DOS programs with explicit distribution rights, not commercial ROMs. Verify boot, run, reset, save-state, speed and hardware rotation.
6. Use separate `capture → read → action → Android ACK → next-frame visual witness` test traces for PilotService. Emulation results are **not** proof that the 10 Market games work on real phone/tablet.

No ROMs, BIOS, game collections or native emulator cores are included yet, so this branch must not be advertised as an installed fully functioning DOS/NES/Dendy emulator.

## Important distinction

This environment runs an **emulated older console or DOS guest**, not a virtual Android OS. The Android 0.8.1 app remains a separate agent. GameLab is a real Android app hosting games, but an emulated game's win condition must be assessed separately from OS ACK or raw pixel motion.
