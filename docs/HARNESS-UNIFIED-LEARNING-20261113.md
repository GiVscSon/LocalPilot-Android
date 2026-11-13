# One controller and two typed NES learning tasks

Integration PR#141 combines precision Student (#139), the existing PPO implementation (#136), and strict capture acceptance (#131/#140). Original branches and data are preserved. This does not activate a policy or release an APK.

The 7→12→6 classifier uses verified zero-page X/Y observations, teacher imitation and train-only DAgger for the reach-(192,112) task. Its V3 checkpoint, 4/8/16-frame adapter and goal journal remain distinct from PPO64. The actual headless five-data-seed acceptance passed on #139; the combined source must rerun it.

PPO64 uses HomebrewObs64, StudentPolicyNet, PpoGaeTrainer and LearningCoordinator already present in #136. Its task is fixed16-frame rightward progress, not the 2D reach goal. Homebrew RAM right-progress journal never becomes the 2D reach-goal journal. Generic changed-pixel history supplies no reward and is rejected for PPO.

HarnessCoordinator alone reserves a task/policy-SHA-bound episode of1–16 steps. The reservation spans freeze, inference and between-step gaps; every action additionally consumes a nonce/epoch/frame/hash/TTL-bound observation. Generic start cannot grant PPO input. The JS bridge checks the same one-use permit before DOWN and every core frame; STOP revokes the reservation and requires release ACK. Old callbacks cannot steal or revoke a new owner. Autonomous START/SELECT remain prohibited.

ConsoleExperienceMemory validates both legacy generations and the reach-goal journal before append/rotation. A corrupt journal is preserved unchanged. This ports the concrete integrity correction from #137 without deleting newer goal schemas.

Local verification: unified Android Java compile/lint;73 coordinator assertions;450 runtime model assertions;42 PPO gradient assertions;40 replay assertions;16 evaluation-gate adversarial fixtures; real packaged JSNES STEP replay/STOP test. Actual JSNES generated16 on-policy actions (256coreframes); PPO changed4999 parameters. Independent6-scenario paired holdout found old/candidate mean0 and no proven improvement. Candidate activation remains blocked. These are headless emulator facts, not Android WebView proof.

The existing all-in-one Android workflow now also runs `tools/harness_student_device_acceptance.py` against the installed single APK. It checks exact APK/source identity, launches the real homebrew and16-step UI command, reads only newly generated homebrew journals/checkpoints in an isolated AVD, rechecks ROM reward continuity and SHA, and confirms active weights stayed unchanged. New runtime acceptance is pending CI; a navigation-only PASS cannot satisfy it.

Release blockers: this combined SHA needs every mandatory CI gate; physical phone/tablet acceptance has no attached device; creation of protected android-signing returns403 and no compatible permanent private signing key is available. main remains189b3b0a3edb2f27bb366a4e335d6da013372b83 (0.8.1/code53). Do not publish a stable release or replace installed data/signing identity to bypass these gates.

## Runtime regression and 0.8.2 candidate

The first production WebView run exposed a real false STOP: the timeout of an already-consumed RAM observation could interrupt a later gap/observation in the SAME16-step episode. Epoch/episode matching alone was insufficient. HarnessCoordinator.awaiting(ticket) now correlates each reply and deadline with the exact still-pending immutable observation; consumed/older tickets cannot stop a later step. JS reply cancellation also checks the exact issued permit. The failure was observed as “PPO observation timeout” in Android evidence, not inferred from a synthetic test. The corrected combined runtime requires a new CI run.

Actual ARM64 APK8062a1c showed valid16KiB ZIP offsets but ELF LOAD p_align=4096. NDKr27 does not enable flexible page sizes by default. The Android target now links with max-page-size/common-page-size16384; `verify_android_elf.py` checks the actual packaged LOAD segments AND stored ZIP data offsets. The old APK correctly FAILS this new gate. A rebuilt APK must pass; no16KiB physical-device run is claimed.

Candidate version0.8.2/code54 allows a later non-destructive update from0.8.1/code53 with the same signing identity. One x86_64 derivative is built once and used by the six API30/35/36 Pixel2/PixelC WebView acceptance jobs. ARM64 user candidate is a separate build of the same pinned source, so emulator success does not claim ARM64 physical execution. Native file reads are bounded before allocation. All new gates remain pending until CI records their actual source SHA.

The f375cfb matrix reached the actual PPO candidate UI in all six emulators, but evidence extraction failed: ARM64 and the inherited x86 debug variant intentionally had `debuggable=false`, so run-as was unavailable. No UI text is accepted as substitute for checkpoint/journal proof. Only the release-blocked x86 test derivative now enables debug access; production/ARM64 remain false. The runner tests run-as before gameplay and distinguishes permission/transport errors from a genuinely not-yet-written file. The six-way matrix must be repeated on this corrected source.
