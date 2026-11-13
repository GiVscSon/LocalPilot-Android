# LocalPilot · MNN advisory, CPU game control

## Accepted design
The embedded MNN-Pocket 0.3B is an **off-thread strategic explanation engine**.
It NEVER selects real-time directions, executes gestures, dispatches input or receives the right to change STOP, TTL, ActionGate, InputPermit or ROI. The existing deterministic CPU/CV/learning policies have *exclusive* control of all games, including 2048.

The strategy screen offers **Объяснить стратегию · MNN**. It uses the selected game-profile goal plus the existing whitelisted anonymized telemetry snapshot (schema localpilot-telemetry-v1). The model writes a couple of English sentences describing general priorities and relevant uncertainty. It does not see game pixels, player secrets, current tile coordinates or the full screen. If the model is unavailable, produces looping text or runs out of tokens, the screen displays a rejection and **CPU action policy is unchanged**. Normal play doesn't wait for the model.

No automatic model queries are issued from PilotService's gameplay or capture path. This is deliberate: the user's tiny model has produced an incorrect answer on 2048 even with detailed prompting and a free-form two-direction reply. Its latency and correctness have not been established as suitable for a real-time controller. Existing no-move/ACK vs visual-confirmation rules stay unchanged, and there is no automatic modification to GGUF weights.

## Model inspiration from existing projects
- **Google AI Edge Gallery / Tiny Garden:** small FunctionGemma on-device maps natural-language intentions to app-specific calls, while deterministic game logic belongs to the application. Unlike Tiny Garden, LocalPilot will not dispatch model-suggested functions.
  https://developers.googleblog.com/on-device-function-calling-in-google-ai-edge-gallery/
- **Alibaba MNN Chat:** portable offline Android model runtime with separate UI and session handling; memory and CPU resource constraints matter.
  https://github.com/alibaba/MNN/tree/master/apps/Android/MnnLlmChat
- **llama.cpp Android:** private model storage, JNI-based local inference, prefill/decoding separated from UI.
  https://github.com/ggml-org/llama.cpp/blob/master/docs/android.md
- **Voyager Minecraft agent:** high-level curriculum, skill library and environment feedback. For LocalPilot, learn from *verified simulator episodes*, not unverified LLM text.
  https://voyager.minedojo.org/
- **LiteRT / LiteRT-LM:** future optional backend for vision/LLM if models and hardware support them. GGUF is not directly loadable as LiteRT without conversion.
  https://developers.google.com/edge/litert

## Acceptance
1. Source-level check: PilotService has NO MNN calls, embeddings, strategy callback or GBNF gesture logic; original CPU 2048 implementation restored.
2. JVM contract test for strategy-only prompts and no command authority.
3. Build Android, unified game pipeline, native host MNN English inference and Android API30/35/36 CI all PASS on one SHA.
4. On physical POCO/tablet: trigger strategy while a game runs, confirm CPU gameplay uninterrupted, test STOP and watch for memory pressure; no APK release claim until checked.
