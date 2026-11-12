# Triad release signing: protected preparation (2026-11-12)

Status: PREPARATION ONLY. No release signing keys exist as part of this change. Do not tag, publish, or mark stable.

## Decision
Use a separate persistent Android release signing identity for each package: LocalPilot, GameLab, and ModelLab. Private keystores and passwords must be generated and retained in a user-controlled, durable secure environment, never on ephemeral CI runners, in repository commits, logs, chat, or build artifacts.

## Before enabling release
- [ ] Confirm all three applicationIds and versionCodes from the built manifests.
- [ ] Generate each keystore using Android keytool on trusted persistent hardware; make two encrypted backups and test recovery.
- [ ] Record public SHA-256 certificate fingerprints for all three apps in an audited release manifest.
- [ ] Provision protected GitHub Actions secrets for each keystore (encoded securely) and its passwords; restrict environment to approved releases. Do not expose secrets to pull_request workflows from untrusted forks.
- [ ] Ensure the signed workflow fails closed if any required secret is absent, rather than falling back to debug signing.
- [ ] Run apksigner verify --verbose --print-certs for each release APK and compare fingerprints with the manifest; check package ID, versionCode, ABI and SHA-256 artifact digest.
- [ ] Verify signed artifacts for all three applications are built from one exact commit SHA.
- [ ] Prove non-destructive upgrade from an earlier release-signed version using adb install -r and check retained app data.
- [ ] Previously debug-signed installed APKs cannot be silently upgraded to newly signed APKs. Plan an explicit user-approved data export/import or package migration; never require silent uninstall.
- [ ] Validate API 30/35/36, and real POCO X7 Pro: NES, GGUF import, vision/mmproj, STOP, repeated generation, ActionGate, InputPermit, TTL, actual gestures and visual verification.
- [ ] Close issue #99 only with concrete signed APK fingerprints, upgrade evidence, and real device logs.
- [ ] Publish a stable GitHub Release only after every gate is verified. A green CI matrix or policy-only signed-candidate job is not proof of signed release.

## Security invariant
Never generate permanent release keystores inside an ephemeral GitHub Actions runner. Do not use debug signing as fallback. Keep PR #98 draft until signed-release and real-device evidence are available.

References: PR #98; issue #99.
