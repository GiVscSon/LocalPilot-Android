# Android model coverage

GGUF is a weight-file format. LocalPilot's GGUF inference uses its existing
llama.cpp JNI runtime. A model name such as MNN-Pocket does not mean that the
MNN inference engine is integrated.

The actual Android downloadable catalogue currently contains:

| ID | Model | Weight bytes | Required additional vision file | This matrix tests |
|---|---|---:|---|---|
| pocket | MNN-Pocket 0.3B Q4_1 | 191896672 | None | Text/RAM decisions and actual NES inputs |
| qwen35 | Qwen3.5 0.8B Q4_0 | 563036064 | None | Text/RAM decisions and actual NES inputs |
| lfm350 | LFM2.5 350M Q4_0 | 219309792 | None | Text/RAM decisions and actual NES inputs |
| lfmvl450 | LFM2.5-VL 450M Q4_0 | 219311264 | mmproj, 102815168 bytes | Text/RAM path only; visual inference remains NOT_RUN here |

These entries come from ModelLabPresetCatalog.java; its exact revisions and
SHA-256 values remain authoritative. CI discovers all preset IDs from this same
file, fails if it cannot parse every entry, and runs one isolated Android API35
x86_64 job per entry with fail-fast disabled. All jobs consume one identical APK.
The runner explicitly selects the requested weights; an unknown model fails,
never falls back to Qwen. Each model has a separate evidence artifact containing
model ID, SHA, source/APK identity, verified decisions and real-game outcome.
Successful loading or native inference alone cannot pass the existing semantic
control/score-progress/STOP gate. No weaker pass criterion was introduced.

This change launches broader testing; it does not assert that any previously
untested model works. LFM2.5-VL must additionally pass a future actual-image/mmproj
test before a visual-inference claim. CPU performance on x86 emulators is not
ARM64 phone latency or Vulkan acceptance.

StudentPolicyNet/PPO64 remains covered by the existing separate real-JSNES and
Android student tests. It is a different local policy, not a GGUF language model;
training and improvement need their own held-out evidence. A GGUF model does not
get PPO updates merely by appearing in this matrix.

The Android ecosystem also has ONNX Runtime, LiteRT/TFLite, MNN, ExecuTorch and
other inference engines. Those engines support different model families and
operators, but are not all integrated in LocalPilot. Their weights cannot be
tested through this JNI backend just by renaming or importing a file. Likewise,
arbitrary GGUF architectures, quantizations and user imports do not inherit the
catalogue's results. Testing every model that could theoretically run on Android
is unbounded; coverage here is every model actually offered by this application.

HOST_UNIT: three matrix/catalogue tests and eleven existing real-game/SAF
correlation checks PASS. EMULATOR_ANDROID results must be read from the current
Actions run after completion. PHYSICAL_ANDROID and Vulkan remain unconfirmed.
