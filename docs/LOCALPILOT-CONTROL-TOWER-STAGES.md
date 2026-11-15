# LocalPilot Control Tower: согласованный поэтапный план внедрения

Status: **IMPLEMENTATION PLAN**, not release or completed feature. Date: 2026-11-15.
Coordination entry: [Issue #159](https://github.com/GiVscSon/LocalPilot-Android/issues/159).
Read first: [AGENTS.md](../AGENTS.md), [single source of truth](LOCALPILOT-SINGLE-SOURCE-OF-TRUTH.md), [Harness RFC](LOCALPILOT-HARNESS-RFC-v1.md), [execution plan](LOCALPILOT-HARNESS-EXECUTION-PLAN.md).

## Rules common to every stage

- Only current `main` is implementation authority; record its full SHA before creating any branch. An issue or chat cannot override source/tests. One task = one owner/PR = one gate = one measurable exit criterion.
- Do not replace the existing HarnessCoordinator, JSNES, localpilot_embedded JNI, PPO trainer, ActionGate or InputPermit. Keep one APK; no decorative parallel consoles.
- Always report `baseSha`, `headSha`, source paths, run IDs, exact evidence artifacts, tested API/device, failure or PASS category. `UNKNOWN` is not `PASS`.
- Required evidence classes remain separate: BUILD_PASS, UI_PASS, INFERENCE_PASS, ACTION_PASS, TRANSITION_PASS, GOAL_PASS, TRAINING_PASS, GENERALIZATION_PASS, DEVICE_PASS.
- All changes go through small reviewed PRs and CI. A merge is not a release. Never auto-promote candidate policy or bypass STOP/epoch/TTL/legalMask/fail-closed. No self-triggering workflow loops.
- Issue #159 is the human-readable live index. Machine snapshots are generated and replace a single bounded comment or artifact, **not** append unlimited comments or commit generated status back into main on every workflow run. The ledger is updated only with confirmed changes.
- Coordination stages C0–C3 improve project operations; product acceptance gates G0–G6 in the existing execution plan remain independently mandatory.

## C0. Baseline and inventory (first; no new engines)

**Inputs:** current main, PR #146 merged, PR #150/#155/#156/#157, issues #99/#159, existing workflows and actual APK audits.
**Work:** inventory workflow names, PR base ancestry, issue labels, branch protections, file ownership, exact latest main SHA; identify duplicated/superseded branches.
**Deliverables:** initial immutable `control-tower-snapshot.json` with `generatedAt`, `mainSha`, `openPrs`, `workflows`, `blockingIssues`, `unknowns`; linked from #159. No invented device results.
**Exit:** every active blocking PR classified `MERGED` / `ACTIVE` / `SUPERSEDED` / `NEEDS_REBASE`; snapshot schema validated; links resolvable.
**Owner:** coordination/CI. **Dependency:** none. **State:** PROPOSED.

## C1. Automated status dispatcher

**Trigger:** `pull_request` (open, synchronize, close), `workflow_run` completed and `workflow_dispatch`; schedule fallback if needed. Permissions: contents:read, pull-requests:read, actions:read, issues:write, least privilege. Prevent forked PR write escalation; no untrusted PR code executed with write token.
**Work:** collect API state and exact SHA from GitHub; generate deterministic JSON and Markdown; update one designated bot comment in #159 using a stable marker. Cap payload and API pagination, handle rate limits and missing data as UNKNOWN.
**Deliverables:** `tools/control_tower.py`, tests with recorded synthetic API fixtures, `.github/workflows/control-tower.yml`, single issue comment.
**Exit:** idempotent re-run updates the same comment; 0 duplicates; stale/out-of-order run cannot overwrite newer main; missing CI != green; failed CI is attributed to exact SHA/job; fork PR cannot gain write credentials.
**Owner:** CI. **Dependency:** C0. **State:** PROPOSED.

## C2. PR coordination and evidence contracts

**Work:** PR template with gate, owner, base/head, touched modules, conflict/duplicate check, test matrix, real-vs-synthetic evidence, rollback; static linter verifies metadata and catches known duplicate runtime entrypoints (advisory first, enforced after false-positive review). No name-only rule may reject valid refactors without review.
**Exit:** new PR reports exact evidence and gate; missing device evidence never shows DEVICE_PASS; two conflicting modifications to singleton ownership are flagged for human review; protected STOP tests remain required.
**Owner:** repository quality. **Dependency:** C1. **State:** PROPOSED.

## C3. Cross-chat handoff

**Work:** AGENTS.md/README.md already point to #159. Publish a compact copyable handoff template containing issue URL, main SHA, current gate, PR URL, first failing test, next owner and acceptance criterion. New ChatGPT/Codex/Gemini sessions read it at start.
**Limit:** GitHub cannot inject messages into already open chats or force third-party agents to read the repository. A webhook may notify a configured external endpoint only with explicit consent and credentials; do not claim universal chat synchronization.
**Exit:** fresh agent with repository access can reconstruct current gate without relying on chat history; handoff tested manually in at least two independent fresh sessions. **Owner:** coordination. **Dependency:** C1. **State:** PARTIAL (links in main; cross-chat verification outstanding).

## Product gate sequence after coordination

| Gate | Scope | Exit evidence | Current confidence |
| --- | --- | --- | --- |
| G0 | APK integrity, same-SHA CI, signer, upgrade | APK/cert SHA, ZIP/DEX/manifest, API30/35/36 | UNKNOWN for release |
| G1 | Real JSNES loop and STOP | monotonic real frames, legal action, STOP/stale rejection, sustained episode | Partial from #146 |
| G2 | GGUF+mmproj bound to decisions | actual native inference, model SHA, action accepted and verified | Partial; device UNKNOWN |
| G3 | Offline, ROI/capture, thermal and GPU | egress negative tests and physical POCO metrics | UNKNOWN |
| G4 | Provenance-aware replay | immutable reward evidence, no training on reward=null | Partial |
| G5 | PPO/GAE and held-out promotion | actual weight delta, disjoint >=20 pairs, improvement and rollback | #150 NO_PROVEN_IMPROVEMENT |
| G6 | Single stable Android release | physical POCO acceptance, update/data preservation, signed artifact | NOT RELEASED |

G0–G6 are **not replaced** by C0–C3. Prioritize first failing gate; independent feature PRs may run in parallel only when ownership and shared code are nonconflicting.

## Rollout and rollback

1. Merge C0 documentation/snapshot contract after review.
2. Deploy C1 in **read-only dry run** using workflow_dispatch and inspect output. Only then enable a single issue-comment update.
3. Enable C2 checks as warnings, evaluate false positives, then make required where appropriate.
4. Verify C3 fresh-chat reconstruction; retain manual handoff fallback.
5. Advance G0–G6 with separate evidence-bearing PRs, never promote weights or release automatically.
6. Rollback coordination automation by disabling its workflow; preserve issue history, AGENTS.md and ledger. Failures in C1 must never block runtime STOP or change app input permissions.

## Current next action

Implement **C0 inventory + snapshot schema/tests** from current main; then C1 dry-run dispatcher. Track each milestone in #159. Do not interpret this plan itself as implementation or CI evidence.

## System-level integration contract: one operational product

**Single runtime graph:** `GameLab/Console/ModelLab/Play/Learn/Evidence UI -> existing HarnessCoordinator -> EnvironmentAdapter (real JSNES or safe Android capture) -> ObservationPipeline -> ModelRouter (existing GGUF JNI + fast StudentPolicy) -> AgentController/ActionGate/InputPermit -> verified environment step -> TransitionVerifier -> ExperienceStore -> LearningCoordinator -> EvaluationGate -> ModelRouter`. UI is a projection of this graph, not independent state.

**Shared identity:** every screen, command, model inference and journal record refers to one `episodeId`, `epoch`, `frameId`, `romSha256`, `modelSha256`, `policySha256`, `stopRevision`, `sourceKind` and `evidenceRef`. Missing/contradictory identifiers cause `UNKNOWN` and no action. Exactly one active input owner; changing ROM/profile or STOP invalidates stale proposals and releases held buttons.

**Proof of real integration:** an end-to-end acceptance test must launch a verified ROM in the **existing** emulator, bind a model through the **existing** registry, capture a fresh observation, obtain a genuine model decision, validate the legal action, execute it through the single coordinator, independently verify frame/RAM advancement, persist the immutable transition, train only on eligible reward-bearing evidence, evaluate candidate on disjoint holdouts, and either atomically promote or roll back. Negative tests: delayed GGUF response after STOP, epoch mismatch, missing reward, no model backend, unchanged frame, duplicate input owner, incompatible mmproj, candidate worse than baseline. Every test labels which links are real and which are mocks.

**Product UI acceptance:** selecting a ROM and model in the Library must change the actual active Play session, and the Episodes/Learn/Diagnostics views must show the same episode and model hashes. No status ACTIVE, PLAYING, TRAINED, IMPROVED, DEVICE_PASS or RELEASED is inferred from file presence, UI animation, synthetic frame or ACK. Unknown/unsupported capabilities are disabled with an explicit reason, not fake controls.

**No two control planes:** GitHub Control Tower coordinates development and evidence; Android HarnessCoordinator alone coordinates gameplay. CI workflows cannot send device inputs or change live model weights. Real-time emulator input must remain independent of slow GitHub/LLM calls.

**Release acceptance:** a single APK with real integrated screens and shared runtime state, on-device verification of the full loop, same-source-SHA CI, stable signer and non-destructive upgrade. Keep any unverified capabilities in explicit SHADOW/UNAVAILABLE mode rather than separate mock applications.
