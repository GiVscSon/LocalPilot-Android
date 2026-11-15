## LocalPilot gate / ownership

- Gate: `C0 / C1 / C2 / C3 / G0 / G1 / G2 / G3 / G4 / G5 / G6`
- Canonical base SHA: 
- PR head SHA:
- Owning module and files:
- Existing implementation reused (HarnessCoordinator, JSNES, localpilot_embedded, StudentPolicy):
- Related Control Tower Issue #159 items / overlapping PRs:

## Evidence (do not conflate)

- [ ] Build/API check attached to exact SHA
- [ ] JVM/source checks, including negative cases
- [ ] Android emulator run ID + device/API + uploaded evidence
- [ ] Real model inference trace (if claimed)
- [ ] Actual action/transition/reward evidence (if claimed)
- [ ] Holdout training/evaluation and safe rollback (if claimed)
- [ ] Physical POCO report + signer/update verification (if claimed)

**Claimed proof labels:** BUILD_PASS / UI_PASS / INFERENCE_PASS / ACTION_PASS / TRANSITION_PASS / GOAL_PASS / TRAINING_PASS / GENERALIZATION_PASS / DEVICE_PASS. List only those actually proven. Others: UNKNOWN.

## Failure and rollback

- First failing job/step + run link:
- Negative tests for STOP, stale epoch, duplicate owner, reward null:
- How to disable/revert this PR without losing device data:
- Unproven functions exposed as SHADOW / NOT_AVAILABLE:

**Reminder:** one APK, one coordinator, one input owner. Green CI is not stable release or physical device proof.
