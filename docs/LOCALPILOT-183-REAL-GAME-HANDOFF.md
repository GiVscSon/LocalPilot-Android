# Current-game agent, not an educational replacement

Date: 2026-11-17 Europe/Moscow. Base: PR #185, `23d82aa1563ab6416b368e5b1d378ca7ab6bb56d`. Main observed: `32838fde8c2432dd060f3cf2923d4dd1910c9553`.

User requirement: no educational scenes in the actual agent. The user ARM64 build now has HARNESS_TEST_FIXTURES=false. It hides fixture/Student demo controls, refuses fixture auto-workflow intents and cannot replace the user's game with the bundled exercise. Old CPU/Student engine regression fixtures remain available only in isolated test packaging. They are not acceptance of real gameplay.

The current imported NES ROM is controlled through its existing WebView, existing controller permits and existing Application-owned GGUF runtime. An imported vision GGUF with mmproj receives the verified current PNG. Ordinary ROMs use VISION_EXPLORATION with UNKNOWN RAM and reward=NULL; text-only models cannot substitute for vision. A native-selected vision profile is required before the JS core can enter autonomous step mode. No second core, JNI runtime, trainer or Android gesture executor is created.

Current-ROM records extend ConsoleExperienceMemory with a bounded integrity-checked stream identified by ROM SHA, model SHA and epoch. They cannot enter homebrew rewards or PPO. Context is filtered by exact ROM/model identity. STOP, model changes, lifecycle backgrounding and profile revocation fence delayed actions. A stale/invalid reply or unchanged frame stops the episode. Budgets are 1/4/16/64/256, with the user path defaulting to 256; the known RAM test task also stops on its verified goal.

Checks prepared:

- Python Harness contracts (31), UI runner contracts (28), safe SAF target contracts (4) PASS locally.
- Source/target17 JVM tests PASS locally through the installed Java compiler module: typed model host (38 assertions) and shared experience integrity tests, including wrong-ROM history and forged reward rejection. The local runtime lacks ct.sym for --release17; exact release17 and Android SDK compilation are CI gates.
- Real JSNES 2.1.0 CPU/PPU regression PASS locally using canonical upstream tagged sources. It verifies that unprofiled ROMs cannot act; native-selected vision stepping uses real core frames and emits no fabricated homebrew RAM/reward callbacks; revoked profile rejects further input. The Android bridge in this host test is modeled, and its test ROM is not real-game acceptance.
- Existing G2 acceptance now imports its actual pinned model through visible SAF controls, verifies and binds it through the NES menu, then unbinds/deletes it through visible controls. No private import/reflection bypass is used by that runner.
- A new Android job uses actual MIT-licensed VOID LANCER v1.0.0, ROM SHA 5eb801aca68131a3d17fa82d8901d46f6098b878d1e00cd1c47d00c001c2405a, with pinned LFM2.5-VL-450M and matching projector from the existing catalog. Both models and the actual game are selected through SAF. It requires four actual model-to-input/frame matches and STOP, preserves actual failures, and never marks a score/victory as verified from changing pixels.

Not accepted until this head's CI executes: Android compilation/lint, real SAF selection, vision JNI compatibility/latency, four real-game transitions and STOP. A passing input/frame chain still does not prove winning the game, general skill, universal game support, physical POCO results or model improvement. External Android games continue to use the existing PilotService and game-specific capture/ROI/ActionGate profiles; generalized model-driven external adapters remain unfinished.

Next work is to fix the real-game Android run until its actual evidence is available, then verify semantic game progress and extend external-game profiles without weakening permissions. No user approval question is needed for authorized development/testing. Release/main merge/signing remain on hold. Preserve user ROMs, models, existing saves and checkpoints.
