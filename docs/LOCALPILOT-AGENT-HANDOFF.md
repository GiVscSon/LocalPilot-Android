# LocalPilot · handoff for fresh ChatGPT / Codex / Gemini agents

**2026-11-16 integration continuation:** [PR #175 evidence and next gate](LOCALPILOT-HARNESS-INTEGRATION-20261116.md).
Initial head23288d8 / actual tested merge1cb915e passed17 workflows, including
NES256, real GGUF and six UI profiles. Current main32838fde is incorporated
without dropping Android QA tooling. The existing evaluation gate is being
strengthened to20 disjoint pairs and material median improvement; read the
new head's CI in PR #175/#159 before accepting it. No main merge or release.

**Live index:** [Control Tower #159](https://github.com/GiVscSon/LocalPilot-Android/issues/159), including ONE continuously updated `github-actions[bot]` CI status comment. **Implementation authority:** [main](https://github.com/GiVscSon/LocalPilot-Android/tree/main), not the latest chat or draft PR. **Architecture:** [single source of truth](LOCALPILOT-SINGLE-SOURCE-OF-TRUTH.md), [staged acceptance](LOCALPILOT-CONTROL-TOWER-STAGES.md), [RFC](LOCALPILOT-HARNESS-RFC-v1.md).

## First five minutes of a fresh session

1. Read the current `main` commit SHA via GitHub; verify the source ledger and this handoff at the same SHA.
2. Read Issue #159, especially the latest bot CI status and open blockers. Verify whether each mentioned PR was merged or remains draft. Never assume this handoff itself is up-to-date.
3. Inspect open PRs and workflows; distinguish source HEAD SHA from `pull_request` merge SHA used for Android CI. No green state without the named SHA.
4. Assign ownership of exactly one first failing gate. Reuse existing HarnessCoordinator, JSNES, EmbeddedGgufRuntime and LearningCoordinator. Do not reimplement parallel services/agents.
5. Post concise evidence in Issue #159 after verified change. Other active chats are **not** automatically synced to GitHub: those agents must read the issue when starting or resuming.

## Copyable agent handoff (fill only from live GitHub)

> LocalPilot source: https://github.com/GiVscSon/LocalPilot-Android  
> Control Tower: https://github.com/GiVscSon/LocalPilot-Android/issues/159  
> Current `main` SHA: **VERIFY LIVE**  
> Owned gate: **C0/C1/C2/C3 or G0–G6, specify**  
> Working PR/head/base SHA: **VERIFY LIVE**  
> First failed CI job and run: **VERIFY LIVE**  
> Shared runtime owner: **existing HarnessCoordinator.nes()**  
> Working model binding and ROM identity: **VERIFY LIVE**  
> Next independent test + negative cases: **SPECIFY**  
> Current evidence labels: **PASS / FAIL / UNKNOWN by component**  
> Device/release: **UNKNOWN unless physical POCO evidence, signer and update pass**  
> Rollback path: **SPECIFY**

## A complete game-learning proof must be connected

One approved episode: real ROM -> fresh JSNES observation -> actual bound GGUF/Student inference -> legal permit -> single input executor -> fresh visual/RAM transition -> provenance-bound replay -> eligible candidate PPO training -> disjoint independent holdout -> keep active or safely rollback. A stored model, animation, Java-only test or emulator-only run does not verify that entire sequence.

### No implicit background or cross-chat synchronization

A new session may read Issue #159 using GitHub access. Neither this Markdown nor a bot comment can push messages into historical ChatGPT or Gemini threads automatically. Treat unverified / disconnected scopes as UNKNOWN and do not invent capabilities.
