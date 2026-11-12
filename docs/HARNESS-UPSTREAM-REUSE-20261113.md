# LocalPilot Harness: upstream reuse decisions (13 Nov 2026)

Status: RESEARCHED / selection only. No claim of APK integration, physical GPU operation, MCP connection or successful learning is made by this document.

Base integration: PR #117 (HarnessCoordinator, one NES input owner, fail-closed STOP) and PR #115 (real JSNES + GGUF + bounded shared experience). Preserve one application package. Every imported component must have a real consumer in the same Harness, executable test, pinned version, license attribution and evidence.

## Reuse-first rule

Before implementing an emulator, MCP client, JS engine or ML runtime: inspect maintained upstream library, LICENSE, release date, Android minSdk/ABI compatibility, transitive dependencies, security advisory status, exact execution interface and a reproducible hardware/CI smoke test. Prefer existing audited libraries. Do not paste entire GitHub "agent harness" projects into our APK, silently run downloaded source, trust MCP tool descriptions or bypass HarnessCoordinator.

## Decision A: NES core. KEEP already bundled JSNES 2.1.0

- Upstream: https://github.com/bfirsh/jsnes ; Apache-2.0, latest tagged release 2.1.0.
- Existing LocalPilot workflow already verifies pinned jsnes@2.1.0 npm tarball SHA-512 and executes a true NROM homebrew in the JavaScript CPU/PPU. Existing WebView includes the core.
- **Reuse upstream**: nes.frame(), nes.toJSON()/fromJSON() save states, reloadROM(), buttonDown()/buttonUp(). No new CPU/PPU implementation.
- Required addition is only our integration adapter around those public APIs: deterministic STEP_MODE, ROM/session epoch, read-only observation, frame-count verification and input permit from the existing coordinator.
- Target benchmark: baseline 150 frames vs deterministic repeated restore+action on the same cartridge. A save state is not valid replay unless same ROM SHA, core build and deterministic replay test agree. Gamepad release and STOP must be independently tested.
- Do not execute external scripts/ROM-supplied JavaScript in the game WebView.

## Decision B: MCP. USE official SDK, not a home-written wire protocol

- Preferred candidate: https://github.com/modelcontextprotocol/kotlin-sdk , published client-only Maven artifact io.modelcontextprotocol:kotlin-sdk-client, release 0.16.0 (27 Oct 2026), new code Apache-2.0 with legacy MIT.
- Supports Streamable HTTP and standard tools/resources/prompts, client-side cancellation; we need CLIENT only. SSE is deprecated. Use remote tools over authenticated HTTPS or an explicitly configured loopback endpoint. A portable Android MCP client is **not yet proven** by this research: run a dependency-resolution + API30/35/36 build/DEX/smoke test on the current Java 17 + AGP 8.7.x/Gradle 8.9 project before adding Kotlin plugin and Ktor dependencies.
- Alternative: https://github.com/modelcontextprotocol/java-sdk , MIT license. It uses reactive streams; compatibility, size and Java Android dependencies MUST be measured. Do not add both SDKs.
- No arbitrary Python/Node STDIO server launch inside Android APK. Desktop/Termux server is a separate process and only a tool provider; Android's Harness remains sole owner of game input.
- MCP offers external observations/analysis/tool calls, **not direct buttonDown or AccessibilityService access**. Remote proposed gameplay actions still require local policy, one-time Harness input permit, ROM epoch and fresh-frame proof.

## Decision C: scripting. REUSE current WebView and proven JS first

- The existing local WebView executes pinned JSNES and our controller scripts. Push repeated emulated frame steps, feature extraction and bounded action batches into this same JS isolate, avoiding per-frame Java↔WebView roundtrips.
- https://github.com/OpenQuickJS/quickjs-android offers an Android AAR with minSdk 22, newest tagged v0.2.1. However the repository's last observed update is in 2023. This is **not** a default addition. Review license and maintained alternatives, ARM64 JNI safety, script limits, interrupt/cancel semantics and isolated bindings before adopting. Extra JS engines cost APK size and attack surface.
- Script files are versioned app assets by default. Importing untrusted scripts requires provenance, explicit sandbox, CPU/memory budgets and NO implicit Android bridge capabilities. Do not use eval of downloaded extension content in our trusted game WebView.

## Decision D: fast inference/training. USE existing llama.cpp plus LiteRT where eligible

- GGUF and optional mmproj: retain the already integrated native llama.cpp runtime for actual inference; do not port an LLM in JavaScript. Vulkan is experimental and must show loaded layers and measured speed on the physical POCO X7 Pro.
- https://github.com/google-ai-edge/litert-samples : source and Android CPU/GPU inference examples for small .tflite policies. Prefer LiteRT CompiledModel for modern inference after a measured tensor-contract and GPU benchmark, CPU fallback mandatory.
- https://developers.google.com/edge/litert/conversion/tensorflow/build/ondevice_training : on-device weight changes via models exported with explicit train/infer/save/restore signatures (Interpreter API); not every .tflite or GGUF can be trained in-place. P4 training gate requires actual changed policy artifact hash, separate holdout episodes and rollback. A history ring buffer or in-context prompting is not weight training.
- Desktop preprocessing and CI scripts may use Python. Do not bundle a full Python interpreter in the Android APK solely for model training.

## Decision E: other console cores. REUSE Libretro ABI after license review

- https://docs.libretro.com/development/cores/developing-cores/ : Libretro ABI as the extension boundary (not a brand-new emulator interface).
- https://docs.libretro.com/development/licenses/ : Libretro API is MIT; individual emulation cores vary (GPL, non-commercial restrictions and other terms). Select and individually pin distributable cores and verify JNI Android ARM64 support; do not bundle RetroArch GPL components or noncommercial cores without a distribution review.
- Each real core must support capability probing, load/reset, frame step, legal buttons, stop and verified fresh state before appearing as an available environment. Do not list imaginary working consoles.

## MCP permission and extension design, all DENY by default

Proposed first-party gate, implemented in LocalPilot and independent of any SDK's server-provided tool description:

1. Server origin pinned to HTTPS with verified authentication, or explicitly approved loopback endpoint. No broad local-network scanning or wildcard origins.
2. On connect, inspect tools/list including exact tool name + input schema hash. The user must grant capabilities **per server and per tool**; a changed tool description/schema revokes prior approval.
3. Capabilities: READ_MODEL_METADATA; READ_REDACTED_EPISODE; READ_GAME_OBSERVATION; EXPORT_EXPERIMENT; NETWORK_LOOKUP; WRITE_USER_CHOSEN_FILE; PROPOSE_GAME_ACTION. Default is NONE; filesystem and network need explicit scope; exporting raw images requires separate consent.
4. Every tool call checks scope, session/ROM epoch, time-to-live and STOP revision at **execution time**, not just discovery. Strict timeout, bounded payload and output validation; show tool usage and revoke in one unified ADDONS view.
5. No tool can obtain raw gesture, direct emulator controller or OS AccessibilityService privilege. PROPOSE_GAME_ACTION means proposal only, fed through HarnessCoordinator and the existing safe approval path.
6. Persist minimal per-install approvals encrypted where applicable; no API secrets in logs, prompts or GitHub source. Reauthorize after server origin/identity/schema change.
7. Fail closed on unreachable server, untrusted tool, loss of auth, unknown reward, stale frame and manual STOP. Never count MCP response, ACK or pixel change as earned reward.

Security reference: https://cheatsheetseries.owasp.org/cheatsheets/MCP_Security_Cheat_Sheet.html (least privilege, schema hashes, human approval, audit).

## Staged integration with proof, not switches that do nothing

G1: deterministic JSNES step mode using already bundled core; prove exact N frames, frozen-between-step time and repeatable save state on real ROM, keep manual mode stable.

G2: integrate exactly one official MCP client SDK behind an opt-in ADDONS screen with a read-only built-in diagnostics tool. One connected test server, genuine initialize -> tools/list -> tools/call -> response, Android API30/35/36 tests, tool and schema pinning. No unverified SDK dependency in release.

G3: reward source for a known homebrew with trusted ROM RAM/score evidence; independent pixel proof; nullable unknown rewards. Persistent immutable transition store.

G4: on-device trainable small policy, changed weight hash, holdout and rollback; GGUF acts as teacher or strategist, never falsely marked as fine-tuned.

G5: realtime small policy and optional slow teacher, tested deadlines/thermals and device Vulkan baseline. One installable APK only.

G6: individual licensed Libretro cores after capability and physical tests, not placeholder entries.

## Acceptance labels

UPSTREAM_REVIEWED != DEPENDENCY_BUILT != ANDROID_CONNECTED != ACTION_VERIFIED != REWARD_CONFIRMED != TRAINING_CONFIRMED != DEVICE_PASS != RELEASE_READY.

This file is an actionable choice record; it does NOT perform any of those integration steps by itself.

## Complementary upstream tooling: discovery, test harness, desktop research

- https://github.com/modelcontextprotocol/registry — official community MCP server catalog and metadata API. Reuse it for *discovery only*: registry entries and remotely supplied tool descriptions are untrusted. Never auto-install, auto-run, or auto-grant a discovered server. Show source repo, release, declared permissions and requested tools for review.
- https://github.com/modelcontextprotocol/conformance — official protocol conformance tests. Reuse this suite in CI for the real selected MCP client; do not reinvent JSON-RPC conformance. Verify protocol-version negotiation separately for 2025-11-25 and newer releases as supported by the pinned client SDK.
- https://github.com/modelcontextprotocol/inspector — reference MCP server Inspector with CLI/TUI/Web, helpful for development diagnostics on a PC/Termux. It is a *test tool*, not a library to embed into the APK.
- https://github.com/Farama-Foundation/stable-retro — maintained Python Gymnasium/Retro training integrations, genuine game-variable reward descriptions, ROM hashes and holdout design. Reuse its **integration format and desktop test procedures** for game profiles where licensing allows; it targets Python/desktop, not plug-and-play Android. Do not ship its copyrighted game data or presume installed console cores.
- https://github.com/google-ai-edge/litert-samples — Android examples of efficient inference/model benchmarking. Adapt only the tested component that actually connects to Harness and keep artifact version/license pinned; do not fork a second standalone ModelLab UI.

Additional acceptance for MCP: authentic SDK client -> authorized HTTPS or approved loopback server -> discover tool schema -> user grant -> actual read-only tool call -> schema-validated return -> audit -> revoke grant -> call denied. Repeat on device and with a changed-schema poisoned tool. A catalog entry alone is NEVER a working connection.
