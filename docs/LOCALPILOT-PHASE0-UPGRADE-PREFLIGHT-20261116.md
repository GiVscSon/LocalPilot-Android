# Phase0 continuation: bind the predecessor receipt to the actual APK

Scope: read-only identity/version preflight in the existing signed-candidate
gate. PR177 remains unchanged/draft, main is not merged, no signing key is read
or changed. Base `0d506312f00b0015fa32256e4b8da6ea570e394c` follows the completed
G0 certificate-receipt fix. This is not an Android install/data-retention result.

## Reproduction before the patch

Actual saved production APK0.8.4 is package `org.localpilot.android`, code56,
source `3ab42116fbd04661e0a3ed1d588c1d0625cbb35d`, SHA256
`ed0d044a5a839064d229cdc5df491509a11325384964f90e524312ab1150d3b7`.
It verifies with Android SDK and public signer fingerprint
`b960ab2feccf7f6c47602779695d13297755f97f654444d9e0f0737d3a1408c2`.

The previous gate reads `--previous` JSON but no previous APK. Copying this
APK's own manifest and changing only versionCode56→55 causes the SAME APK to
pass the version-increment check as a signed candidate. It returns
`SIGNED_UNIFIED_CANDIDATE_PASS`/exit0. No Android update was performed or bypassed:
the defect is false predecessor/version admission in the offline gate.

## Minimal implementation

`--previous` now requires `--previous-apk`, and conversely. The gate reuses its
existing payload/source/aapt/apksigner validation on the actual predecessor,
then matches sourceCommit, package, versionCode/name, signer, SHA256 and byte size
to the prior receipt. The current version must exceed the verified prior version.
Incorrect/missing/null receipts, mismatched bytes, source and signer fail closed.
A JSON null cannot silently turn an explicit upgrade check into a first-install
candidate check. No extra signing system or runtime is introduced.

Without a predecessor, output explicitly says `upgradeCompatibility.status=NOT_EVALUATED`. With a verified pair it says `APK_PAIR_IDENTITY_VERSION_PASS`,
records prior source/hash/version/signer and keeps `deviceInstall=NOT_TESTED`,
`upgradeDataPreservation=NOT_VERIFIED`, `physicalAndroidTest=NOT_VERIFIED` and
`releaseReady=false`. It does not prove app migration, Android package-manager
acceptance, retained user data, or compatibility with an unknown installed APK.
Supported inputs are existing unified signed-candidate-v1 APKs/receipts;
this change does not introduce migration from unrelated packages or old schemas.

## Actual SDK checks, without private key access

The saved APKs were read, not installed, modified or signed:

- 0.8.2/code54, source `630415b0e23f6383c5055cccc5b1af882189db82`,
  APK SHA256 `892f6bf4ad2b9380e93ccbb2b01f7a9c81f06492b382eed5bfb0c542e32fd7fa`.
- 0.8.3/code55, source `1413aadc7760459d9e1fc5311b51f5e7cfc6a927`,
  APK SHA256 `6dd518b64f0d78fc3601174ba0ee0436fae48f15e8219552f5ed7b67e481d84b`.
- 0.8.4/code56, source/SHA above.

Both genuine pairs0.8.2→0.8.3 and0.8.3→0.8.4 pass the new gate using actual
SDK aapt/apksigner and the same pinned public certificateb960ab2f... . This
establishes static identity/version continuity for these three saved APKs.
It does not establish the current CI secret connection or a new release signature.
The same APK + false prior version fails with
`Prior manifest versionCode differs from actual previous APK`;0.8.4→0.8.3
fails `Release upgrade versionCode is not incremented`. No PASS file is produced
for the rejected cases. Keys/passwords and installed app data are untouched.

Local raw commands/results are outside Git:
`/workspace/scratch/localpilot-upgrade-preflight-20261116/baseline-reproduction.json`
and `actual-apk-pair-regressions.json`; the latter pins the tested verifier file
SHA256. No APK/private key/model/data is committed.

HOST_UNIT:16 signed-gate contracts PASS normally and with Python-O, including
missing pair, changed predecessor bytes, false version/hash/size/type, explicit
NOT_EVALUATED, and null CLI receipt.8 unsigned-artifact and13 isolated signing
policy tests PASS. SDK-output mocks in those unit tests are not actual signatures;
the real SDK checks above are separate. New-source CI must be inspected separately.

## Reproduction and next evidence

Use the same existing verifier; no signing command is needed:

```sh
python3 tools/unified_signed_release_gate.py \
  --apk /workspace/localpilot-release-0.8.4/LocalPilot-0.8.4-UNIFIED-arm64.apk \
  --aapt SDK/build-tools/35.0.0/aapt --apksigner SDK/build-tools/35.0.0/apksigner \
  --cert b960ab2feccf7f6c47602779695d13297755f97f654444d9e0f0737d3a1408c2 \
  --source-sha 3ab42116fbd04661e0a3ed1d588c1d0625cbb35d \
  --previous /workspace/localpilot-release-0.8.3/SIGNED-MANIFEST.json \
  --previous-apk /workspace/localpilot-release-0.8.3/LocalPilot-0.8.3-UNIFIED-arm64.apk \
  --output upgrade-preflight.json
python3 -m unittest discover -s tests -p test_localpilot_signed_candidate.py -v
python3 -O -m unittest discover -s tests -p test_localpilot_signed_candidate.py -v
```

Next: finish the patch's CI, then obtain a relevant installed-device baseline
certificate/version and a genuinely release-signed new APK with the same identity.
Only a real `adb install -r` + retained data/model/replay/settings checks can close
Issue99's upgrade-data gate. Local device list was empty at the previous audit;
no PHYSICAL_ANDROID/DEVICE_PASS or emulator-install proof is claimed here.
No main merge/release, key change or subsequent phase advancement is enabled.
