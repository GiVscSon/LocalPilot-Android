# Student: independent execution review, 2026-11-13

Base: PR #134, ba0140e570ced373d09f40693999d8a3d6c9c036. All ten PR workflows passed on that source. Those statuses do not transfer to this change.

The original fresh-ROM test pressed RIGHT directly in Node after Java checked a model prediction separately. It did not pass the serialized model decision to the emulator. The revised test calls production Java inference on the reloaded weights for every decision, and sends the returned legal button to actual JSNES CPU/PPU. NONE abstains without input.

Nine evaluations compare random (fixed seed 9137), scripted and Student on three fresh-ROM conditions: initial state, two LEFT steps, two UP steps. Each rollout has at most twelve sixteen-frame actions. Prefixes are evaluation setup, excluded from the training set. Existing four training/holdout sessions replay the same deterministic trajectory: session separation is not evidence of independent state distribution or generalization.

Local observation: Student return -73 on all three conditions; random -43/-44/-44; scripted +59/+89/+59. No policy reached the goal within this budget. The first Student action improves distance 64 to 49, but sustained control regresses. Export parity and offline TD MAE 0.016083670021422747 do not establish behavioral improvement.

CI now saves onpolicy-evaluation.json with actual weights SHA, provenance RETRO_EMULATED, per-policy returns, and explicit selection_status. A Student failing to beat both baselines in every condition is REJECTED_ONPOLICY_REGRESSION. Automatic promotion remains false; release_ready remains false. A passing execution/parity test means the measurement completed, not that the model passed selection. No physical-device, Vulkan, signing or third-party ROM reward claim.

Reproduce with the existing Whole System workflow, or compile its student Java source list and run tests/nes_student_real_onpolicy.cjs with pinned JSNES 2.1.0, original HomebrewNes ROM, compiled Java classes and an output directory.

Next: collect diverse trajectories covering all actions and goal overshoot; improve/retrain only on training data; repeat frozen held-out evaluation; then Android UI/controller and physical-device acceptance. This change does not alter deployed weights or the input controller.
