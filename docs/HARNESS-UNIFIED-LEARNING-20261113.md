# One controller and two typed NES learning tasks

Integration PR#141 combines precision Student (#139), the existing PPO implementation (#136), and strict capture acceptance (#131/#140). Original branches and data are preserved. This does not activate a policy or release an APK.

The 7→12→6 classifier uses verified zero-page X/Y observations, teacher imitation and train-only DAgger for the reach-(192,112) task. Its V3 checkpoint, 4/8/16-frame adapter and goal journal remain distinct from PPO64. The actual headless five-data-seed acceptance passed on #139; the combined source must rerun it.

PPO64 uses HomebrewObs64, StudentPolicyNet, PpoGaeTrainer and LearningCoordinator already present in #136. Its task is fixed16-frame rightward progress, not the 2D reach goal. Homebrew RAM right-progress journal never becomes the 2D reach-goal journal. Generic changed-pixel history supplies no reward and is rejected for PPO.

HarnessCoordinator alone reserves a task/policy-SHA-bound episode of1–16 steps. The reservation spans freeze, inference and between-step gaps; every action additionally consumes a nonce/epoch/frame/hash/TTL-bound observation. Generic start cannot grant PPO input. The JS bridge checks the same one-use permit before DOWN and every core frame; STOP revokes the reservation and requires release ACK. Old callbacks cannot steal or revoke a new owner. Autonomous START/SELECT remain prohibited.

ConsoleExperienceMemory validates both legacy generations and the reach-goal journal before append/rotation. A corrupt journal is preserved unchanged. This ports the concrete integrity correction from #137 without deleting newer goal schemas.

Local verification: unified Android Java compile/lint;70 coordinator assertions;449 runtime model assertions;42 PPO gradient assertions;40 replay assertions;16 evaluation-gate adversarial fixtures; real packaged JSNES STEP replay/STOP test. Actual JSNES generated16 on-policy actions (256coreframes); PPO changed4999 parameters. Independent6-scenario paired holdout found old/candidate mean0 and no proven improvement. Candidate activation remains blocked. These are headless emulator facts, not Android WebView proof.

The existing all-in-one Android workflow now also runs `tools/harness_student_device_acceptance.py` against the installed single APK. It checks exact APK/source identity, launches the real homebrew and16-step UI command, reads only newly generated homebrew journals/checkpoints in an isolated AVD, rechecks ROM reward continuity and SHA, and confirms active weights stayed unchanged. New runtime acceptance is pending CI; a navigation-only PASS cannot satisfy it.

Release blockers: this combined SHA needs every mandatory CI gate; physical phone/tablet acceptance has no attached device; creation of protected android-signing returns403 and no compatible permanent private signing key is available. main remains189b3b0a3edb2f27bb366a4e335d6da013372b83 (0.8.1/code53). Do not publish a stable release or replace installed data/signing identity to bypass these gates.
