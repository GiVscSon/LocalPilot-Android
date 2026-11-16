# Phase 0: independent APK/certificate audit, 2026-11-16

Scope: owner instruction in [PR175 comment6914272049](https://github.com/GiVscSon/LocalPilot-Android/pull/175#issuecomment-6914272049).
Only Phase0. No main merge, release, signing-key change or promotion.

## Source and baseline actually inspected

- Main `32838fde8c2432dd060f3cf2923d4dd1910c9553`.
- PR175 HEAD/base for this patch `1410f3d16cd4a0c8829e147d052dce24027fc656`.
- Tested merge `298ffb954a14a271f8562211fdd0530d5fac080d` has exactly these parents;
  its tree equals HEAD tree `ac91ebbd87656a1eff199ebac88b31e3a2878f6d`.
  Workflow metadata head1410 and artifacts named298ffb therefore describe a
  legitimate PR merge checkout, not a source mismatch.
- [All-in-One43848333164](https://github.com/GiVscSon/LocalPilot-Android/actions/runs/43848333164):
  11/11 jobs SUCCESS. All17 pull_request workflows for HEAD1410 SUCCESS.
  PR101/102/123 remain historical open branches; their old ancestry is not merged here.

Actual ARM64 APK downloaded from artifact14216117880; archive SHA256
`74ed23d3333ff9af819316f9e16b2ccef6fb530d2357892de831ad26b4a338ed`.
ZIP CRC independently checked. APK21935257bytes, SHA256
`c79d423ede24e8dd4dacee2ff9610642ce8522957da189a78984657c15941f00`.
Android SDK aapt identifies `org.localpilot.android.dev.unified`, code57,
`0.8.5-unified-test-only`, min30/target35. This is not the final production package.
`aapt dump xmltree ... AndroidManifest.xml` and `aapt dump resources` succeed;
compiled resources are examined with SDK tools, not guessed from ZIP XML names.

SDK apksigner verifies v2; actual debug signer SHA256
`42a9949baf39b20f969b3648f785aa021cc3c85c8dc0111293aa9e3e54dc0b19`.
ZIP/source/DEX audit and ELF16K audit PASS. One native library:
`lib/arm64-v8a/liblocalpilot_embedded.so`, ELF machine183, minimum LOAD alignment16384.
No backup files or advertised phantom DOS/SEGA library were found by the APK audit.
No GGUF weights are embedded in this variant; import is not inference proof.

Actual DEX class definitions contain PilotApplication, HarnessDashboardActivity,
ModelLabActivity, HarnessModelRuntime, EmbeddedGgufRuntime, NesEmulatorActivity,
HarnessCoordinator and LearningCoordinator. Primary DEX3977688bytes has25346
method references/2868classes. Secondary DEX560bytes has2references/1class:
`com.android.tools.r8.RecordTag`. Primary DEX has57classes extending that marker.
It is D8 Java-record desugaring support; deleting classes2.dex would remove a
referenced superclass. No removal/minification is attempted.

## First reproduced defect and minimal fix

`tools/verify_g0_chain.py` checked only whether `debugSignerSha256` was64hex.
On the ACTUAL APK above, replacing only this field with64 `d` characters still
returned `G0_CANDIDATE_PROVENANCE_PASS` and `apksignerDebugSignature=PASS`.
SDK verification of the same bytes independently reports42a9949b..., notdddd....
The APK itself verifies; the defect is a false positive in certificate provenance.

The gate now requires explicit trusted SDK `--apksigner`, invokes only
`verify --verbose --print-certs`, checks exit status, one certificate and modern
signature scheme, compares the actual SDK fingerprint with the receipt, and
re-hashes the APK after verification. Missing SDK, timeout, rejected signature,
wrong signer and changed APK bytes fail closed. No key/password is accessed.
The existing All-in-One workflow supplies the pinned SDK executable and performs
an actual-APK negative receipt test after the normal positive audit.

Reproduced on source1410 before patch: wrong receipt exit0/PASS. After patch on
the identical downloaded APK: genuine receipt exit0/PASS; wrong receipt exit1,
`G0_APK_SIGNER_DOES_NOT_MATCH_SDK`, and no PASS output file.15 synthetic contract
checks (SDK mocked),9 existing one-APK contracts,17 packaging and3 ELF tests PASS;
actionlint PASS. These unit counts do not substitute for the actual SDK test.
New-source CI is required separately; old APK evidence is not its build result.

## Matrix, model and device evidence remain distinct

Actual x86 artifact14216233488 archive SHA256
`9392ddf107882afae91d3d711c9dc0a7b5d0ca81139095aa03c97e7290f7b51c`;
APK SHA256 `b5c96540e10f0377099a21f937d7e2aab9bd24d93f976d4964293848c363b9ba`.
Its ZIP/source/ABI and SDK signature were independently checked. It shares
source298ffb with ARM64, not APK bytes or physical hardware.
Matrix14216469018, runtime14217257685 and sustained14216547419 ZIP digests/CRC
were verified, their source/x86 APK identities cross-checked. Six API30/35/36
phone/tablet reports PASS. GGUF inference/transition journals re-correlate4exact
MNN-Pocket decisions to4NES transitions (CPU TEXT_ONLY, no mmproj/Vulkan;
reward UNKNOWN, GGUF not trained). Sustained report records256Student steps,
16immutable batches, frames21→4117, RAM return−7.6875 and STOP; execution is
not good strategy.20-pair holdouts are HOST_REAL_JSNES_NOT_ANDROID: one candidate
accepted, five NO_PROVEN_IMPROVEMENT. Full Android learning/promotion is unproven.

Local Android doctor: SDK35/build-tools35.0.0, platform-tools37.0.1, JDK17;
KVM unavailable, no booted device/physical POCO (ADB device list empty).
EMULATOR_ANDROID proof above comes from the named CI, not a local install.
BUILD/PACKAGING PASS; UI/INFERENCE/TRANSITION emulator evidence validated;
full TRAINING/DEVICE/RELEASE acceptance remains UNKNOWN/NOT_READY.

Protected keystore material is present locally (metadata-only check,0600);
private key/password contents were not opened. Public certificate sidecar SHA256
`b960ab2feccf7f6c47602779695d13297755f97f654444d9e0f0737d3a1408c2`
was read with openssl. Its association/usability with the private keystore,
CI secret connection and installed production certificate were not tested here.
It differs from this debug APK. [Issue99](https://github.com/GiVscSon/LocalPilot-Android/issues/99)
remains open: stable signing and data-preserving sequential upgrade are not PASS.

## Reproduction and next small gates

Downloaded evidence/logs remain outside Git at
`/workspace/scratch/localpilot-phase0-evidence-20261116`; no APK/model/private
key/screenshots are committed. Patched commands (SDK paths explicit):

```sh
python3 tools/verify_harness_apk.py APK --expected-source-sha 298ffb954a14a271f8562211fdd0530d5fac080d --report packaging.json
python3 tools/verify_android_elf.py APK --report elf.json
python3 tools/verify_g0_chain.py --apk APK --apksigner SDK/build-tools/35.0.0/apksigner --source-sha 298ffb954a14a271f8562211fdd0530d5fac080d --packaging packaging.json --signed-manifest TEST-ONLY-MANIFEST.json --elf elf.json --output g0.json
python3 -m unittest discover -s tests -p test_verify_g0_chain.py -v
```

1. Finish new-source G0/All-in-One CI; inspect the new APK/SDK certificate and
   real false-receipt rejection. Keep this PR draft and main unchanged.
2. Separately accept stable production signer/package and real `adb install -r`
   across sequential builds, retaining private data; physical evidence is required.
3. Subsequent ordered gates: WINDOW_FULLSCREEN/API30 capture/ROI and bounded
   HardwareBuffer/Image.Plane conversion; GridPuzzle UNKNOWN/BitSet/STOP;
   actual offline HTTPS/loopback egress guard and scoped-storage cancellation.
4. Then existing JNI+mtmd, STEP/REALTIME, provenance/reward=null, actual Android
   twenty-pair candidate exam and atomic rollback. Physical CPU/Vulkan/mmproj and
   POCO/tablet acceptance remain independent. No automatic phase advancement.

Unpublished Android-holdout work was paused when the owner restricted scope;
it is preserved only in local stash `fd510090236cc466ba61a856a991e1cbf85a8bc8`.
This patch changes no app controller, input permit, training code or signing key.
