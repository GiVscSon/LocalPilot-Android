# Harness Console: execution, data and learning audit

Status: **experimental integrated Android interface**, not an autonomous released training suite.

## Open in the app
Build the existing **one** `org.localpilot.android.dev.unified` candidate using
`-PunifiedModelLab=true`. The main activity now has
`Harness Console · модели, NES, GPU, журнал и обучение`. The internal
`HarnessDashboardActivity` loads only the packaged
`app/src/main/assets/harness/dashboard.html` and exposes a limited JS bridge
to *existing* ModelLabStore, EmbeddedGgufRuntime and NES. WebView networking
and cross-origin file access are blocked.

The standalone HTML may be opened for UI preview, but intentionally has
**zero synthetic sample models, episodes, rewards or GPU measurements** and
cannot control a phone outside LocalPilot.

## Exactly what is real
- GGUF catalog: imported/verified files from ModelLabStore; pinned preset
  downloads, system picker imports, verified binding and deletion.
- NES: one real JSNES homebrew environment; same existing controller proposes
  bounded button presses, with fresh screenshots and STOP.
- Native model: actual `consoleAction` /`consoleActionImage` JNI paths,
  with strict text action parsing, and recorded inference elapsed milliseconds.
- GPU: actual compiled backend flag, native Vulkan description and actual
  loaded layer count. Compiled does not mean GPU performance confirmed.
- Episode events: frame -> 8x8 feature extraction -> state -> model -> decision
  -> action -> emulator ACK -> fresh frame -> UNKNOWN reward/STOP. Each event
  carries model identity and episode ID, stored in app-private 3 MiB bounded
  JSONL audit. No raw screenshots or model weights are written to audit.
- Experiments: user can export evidence, view each episode or compare native
  inference speeds across models. When audit cannot write, new agent actions
  must fail closed.

## Genuine lightweight training with no forged reward
The first trainer is **supervised imitation**. It uses only `EXAMPLE` records
from real NES, linked to the *actual 8x8 pre-action frame* and a model action
after which a fresh visual transition was confirmed. The model learns
6 × 65 softmax weights through real gradient updates. At least 4 independent
episodes are needed. A full-episode holdout evaluates model classification
accuracy against a majority-class baseline. The candidate is checksumed and
persisted even if evaluation fails, but can only become active when criteria
are met. An accepted `fast-policy:<sha>` is routed through the SAME
ConsoleAgentBridge as GGUF and is usable for subsequent real NES trials.
ModelLab GGUF weights are not changed.

**Not yet done:** proven game score/reward provider, game success labels,
reinforcement learning on verified returns, broader console integration,
unbounded production gameplay, physical phone Vulkan benchmarks or remote
ADB control of other phones. A visual change is not proof of gaming success,
so the trainer is never called PPO/DQN/RL and never awards positive reward.

## Verification sequence
1. CI JVM: `HarnessTelemetryTest` STOP/audit, real `HarnessImitationMathTest`
   optimizer update on explicitly synthetic test fixtures.
2. CI Node: actually parses bundled HTML JavaScript.
3. CI Python: source contract, one internal WebView, catalog binding, shared
   JNI, no OS input authority, reward remains NULL.
4. CI Android: builds the single ARM64 APK and opens the actual dashboard
   from MainActivity in a live x86_64 emulator.
5. Independent physical POCO X7 Pro acceptance required before releaseReady.

### Distinct evidence states
`DOWNLOADED` != `BOUND` != `MODEL_USED` != `VISUAL_CHANGED` !=
`GOAL_VERIFIED` != `POLICY_TRAINED` != `DEVICE_VERIFIED`.

Never collapse these states into a single green success label.
