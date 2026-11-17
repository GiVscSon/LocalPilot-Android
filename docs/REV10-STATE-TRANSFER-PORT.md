# Rev10: preserve PNG IPC while porting binary state assembly

Base main: `32838fde8c2432dd060f3cf2923d4dd1910c9553`.

The supplied bundle overwrites `NesFrameTransfer`, replacing the PNG validation
API used by ModelLab and GameLab with an unrelated binary state assembler.
The replacement drops EXTRA_PNG, WIDTH, HEIGHT, MAX_BYTES and verify(), causing
real Android compilation errors. These are distinct formats and contracts.

The binary helper is ported as `NesStateTransfer`, preserving the existing PNG
class byte-for-byte. It bounds allocation and chunk size, checks contiguous
offsets and tokens, optionally verifies a supplied SHA, and supports cancellation.
Hash mismatch now clears the incomplete transaction. Successful consumption also
clears counters. The class grants no action permission and does not infer rewards.

HOST_UNIT PASS: 20 binary-state checks and all 13 existing PNG checks.
BUILD_PASS: actual `:app:compileDebugJavaWithJavac -PunifiedModelLab=true`, JDK17,
7 seconds. No APK assembly, emulator or physical-device acceptance was run.

This increment is an optional library port, not an enabled feature. The existing
JSNES JSON save/restore path continues to run; this binary helper is not wired
into that different-format path. Bundle consumers must import NesStateTransfer
when assembling binary state, not replace the PNG codec. No extra controller,
JNI runtime or training engine was introduced.

The rest of rev10 remains blocked: its parallel UnifiedNesHarnessChain expects
incompatible GGUF/ledger APIs; its learning payload omits dependencies; several
tests only compile classes before printing success. Those components are not
imported here. The full bundle therefore still has BUILD_FAIL and cannot be
advertised as a release. Further work must adapt necessary behavior to the
existing Application-owned runtime, coordinator and learning pipeline.
