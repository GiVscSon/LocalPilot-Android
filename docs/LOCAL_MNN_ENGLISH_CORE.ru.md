# LocalPilot: MNN-Pocket as the default local English LLM backend

## Scope and current limitations
The user's same-device Termux llama-server is the primary **local LLM interface** for manual diagnostics.
UI is Russian, internal model messages are English. Local language generation is not yet trusted to control real game gestures, replace computer vision, or train a production policy.
The deterministic PilotService, STOP, ActionGate, InputPermit, TTL, strict ROI, visual post-ACK verification, and real-vs-SIM provenance are untouched.

## User setup
1. Start llama-server in Termux with the installed MNN-Pocket GGUF, bound to 127.0.0.1:8080. Keep Termux alive; this build does **not** bundle weights or the server.
2. Open LocalPilot -> **Локальное ИИ-ядро · MNN-Pocket (English)**.
3. Tap **Проверить подключение**. HTTP GET /health must report status=ok.
4. Tap **Самопроверка английского · BLUE / 27**. Two independent short completions must return exactly BLUE and 27; a healthy endpoint alone does not prove quality.
5. Enter a short question in English and tap **Спросить локальную модель**.
6. If the model loops, mixes languages or reaches the token limit, the response is rejected without any game action.

## Implementation
- LocalCoreContract: English instructions; bounded, fail-closed response guard.
- LocalModelCore: background HTTP client, one in-flight request, strict localhost only; /health and OpenAI-compatible /v1/chat/completions.
- LocalCoreActivity: visible Russian controls; no background use or automatic input.
- Network security configuration permits plaintext HTTP **only** for the loopback host 127.0.0.1; other cleartext remains blocked.
- The model receives the existing whitelisted telemetry snapshot, not images, window labels, app names, coordinates, or raw private logs.

## Acceptance before enabling real-game model policy
- Build + JVM contract + lint + API30/35/36 on same SHA.
- Validate locally with real Termux and complete model, including 200 healthy, unreachable, 503, malformed JSON, overlong and repetitive answers.
- Test on phone/tablet; allow no model-derived real-world action without existing independent visual and motion verification.
- This step is **not** a stable APK release, embedded GGUF inference, or an autonomous 10-game LLM policy.

## Smoke-test evidence
- Native Android acceptance checks the same app's loopback-only cleartext security policy and local screen controls on API 30/35/36.
- Local model inference is not executed in GitHub's emulator jobs because Termux and GGUF weights are not installed there. This must be confirmed on the user's POCO and tablet separately.
- Local language UI requires Termux to keep running. Android cannot silently start a cross-app Termux process; this APK deliberately does not claim bundled/offline-without-Termux native inference.
