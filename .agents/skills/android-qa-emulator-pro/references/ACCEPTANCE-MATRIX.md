# LocalPilot Android QA acceptance matrix

Evidence is scoped. A passing simulator cannot be promoted to a real-Android or physical-phone result.

## Four tiers

| Tier | Evidence required | Allowed claim |
| --- | --- | --- |
| HOST_UNIT | JVM/Python result with SHA and exit status | HOST_UNIT_PASS |
| HARDWARE_TWIN_SIMULATED | named mock model and seed, simulated-only label | HARDWARE_TWIN_SIMULATED |
| EMULATOR_ANDROID | real booted Android OS, ADB serial/API/ABI, installed APK SHA, completed scenario | EMULATOR_PASS |
| PHYSICAL_ANDROID | real POCO/tablet identity, actual native APK input/transition, performance and signer/upgrade checks | DEVICE_PASS |

## Test gates

- SOURCE: checkout and tests pass at the exact recorded SHA.
- APK: the tested APK's SHA256 matches source/ZIP/DEX, build metadata, cert fingerprint and ELF/ZIP alignment.
- AVD: phone and tablet API 30/35/36 unless explicitly narrowed by the task. Distinguish Google APIs vs default images and actual ABI.
- UI: verified app process/navigation, accessibility, ROI, input, screenshots and failure diagnostics.
- GAME: verified fresh semantic transition per permitted move, not Android ACK alone. Run sustained NES 256 steps when that gate is applicable; confirm STOP + replay provenance.
- MODEL: real linked GGUF/Student inference and action from selected version, not file presence in catalogue.
- TRAINING: real, independent holdout versus active policy; no automatic promotion when improvement isn't proven.
- PHYSICAL: actual POCO X7 Pro acceptance, Vulkan/GPU measurement, signature continuity, no-data-loss upgrade and signed release only after all gates.
- Unknown or incomplete evidence -> UNKNOWN/FAIL/BLOCKED_INFRA, with first failing step and exact work scope.

## Minimal report (template, intentionally UNKNOWN)

```json
{
  "schema": "android-qa-evidence-v1",
  "sourceSha": null,
  "apkSha256": null,
  "signerSha256": null,
  "environment": "UNKNOWN",
  "deviceSerial": null,
  "androidApi": null,
  "abi": null,
  "gameId": null,
  "romSha256": null,
  "modelSha256": null,
  "beforeFrameSha256": null,
  "afterFrameSha256": null,
  "semanticTransitionVerified": false,
  "rewardProvenance": "UNKNOWN",
  "stopVerified": false,
  "result": "UNKNOWN",
  "reason": "Unexecuted template, not test evidence"
}
```

Keep raw device tokens, private files, ROM content and unredacted logcat out of publicly uploaded reports. Every PASS must name the actual runner, artifact SHA and version used.
