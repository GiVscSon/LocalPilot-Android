# Issue 183: NES model library increment

Date: 2026-11-17 (Europe/Moscow). Parent: Draft PR #184, exact HEAD `7a87de65920620dc0becdaf56d64f39b9778a691`, tree `176df685446ed049566afd3e637c4d7bbdce0464`. Observed main: `32838fde8c2432dd060f3cf2923d4dd1910c9553`. All 11 parent workflow runs completed successfully; this does not accept the new changes.

The existing NES Activity now exposes a visible Models menu beside the existing model selector. It imports GGUF or attaches mmproj using Android SAF, downloads only the existing pinned ModelLab preset catalog, verifies stored file hashes, assigns or clears the existing NES binding, and removes unbound imported models after confirmation. Built-in entries cannot be deleted. Missing prerequisites disable menu commands and state their reason. No operation starts inference or grants input authority.

Implementation reuses PilotApplication, HarnessModelRuntime's file executor, ModelLabStore and HarnessModelBinding. Library operations serialize with NES inference admission; pending input, active episode ownership and busy native inference reject mutation. STOP continues to use the existing controller. Transfers remain application-owned when the screen is backgrounded; an ongoing file transfer must finish before another library operation or GGUF inference. No transfer-cancel button is provided in this increment.

An import without an expected hash displays SHA_COMPUTED and explicitly marks the source unverified. Matching expected hashes and pinned downloads display HASH_VERIFIED. BOUND means assignment only. Native model loading, RUNTIME_PROBED, model fitness, improvements, physical Android and release acceptance are not implied.

Validation prepared:

- HOST_UNIT: 31 Harness Python contracts, 28 unified UI-runner contracts and 9 singleton checks PASS; Python compilation and git diff whitespace checks PASS.
- Android instrumentation extends the existing runtime contract with wrong-hash rejection, import/mmproj/verify/bind, refusal to delete bound weights, unbind/delete, concurrent inference refusal during import, and controller STOP during a blocked provider stream. Header-only synthetic GGUF bytes exercise storage only; they are never loaded by JNI or claimed to be valid model weights.
- Actual UI smoke now clicks Models, status, import and the real system SAF picker, cancels back into the same NES screen and checks STOP. Successful file selection through SAF, actual pinned-network download and real model inference remain separate acceptance steps.
- Local Android compilation and execution NOT RUN: this environment has no SDK, ADB, emulator, KVM or javac. Git HTTPS fetch is blocked; the 52 changed upstream blobs and the complete Git tree were recovered through the GitHub connector with exact SHA verification. The original upstream commit was recovered byte-for-byte and used as a shallow boundary.

Next steps:

1. Inspect Android build/lint and emulator CI for this increment's exact head and fix failures; do not substitute parent CI.
2. Finish user-driven SAF selection/import and actual pinned model download on isolated Android, preserving source/APK/model hashes.
3. Test model selection, ROM switch, agent, STOP, learning and save/resume across API30/35/36 and phone/tablet layouts.
4. Continue #183's shared controller/adapters, independent on-device candidate evaluation and external-game integration. No universal all-game claim.
5. Keep release, signing changes and main merge on hold. POCO X7 Pro physical acceptance remains UNKNOWN.

Reproduce host checks from repository root:

```sh
python3 -m unittest discover -s tests -p 'test_harness*.py' -q
python3 -m unittest discover -s tests -p 'test_unified_in_app_smoke.py' -q
python3 -m unittest discover -s tests -p 'test_verify_single_runtime.py' -q
python3 -m py_compile tools/unified_in_app_smoke.py tools/run_harness_runtime_acceptance.py
git diff --check
```

Use the existing All-in-One workflow for the x86_64 Android derivative, runtime instrumentation and real visible UI smoke. No physical device is attached here. #183 remains incomplete.
