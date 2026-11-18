# G4.2 controlled decision experiment · 2026-11-18

Base #194 `6d120c4adf8105b741073e5f94a39b4206c7e035`. This branch does not
replace parallel #195 Pocket acceptance or #196 motor contracts. Main merge,
stable release and signing changes remain paused.

## Implemented boundary

The existing Application-owned runtime snapshots immutable case options with
the original observation ticket. Explicit seeds and measured prefill/decode
use the same native model/context, lock, inference function and cancellation.
No second emulator, inference worker, trainer or input executor is introduced.

The regular JNI signatures and production generation modes0–4 remain unchanged.
A private, test-guarded text inference entry supplies explicit seed and two
per-call timing slots; unentered stages remain null. Diagnostic mode5 offers a
common tool vocabulary. It is inaccessible through the ordinary proposal API.
Both phase parsing and the independent Activity phase gate still apply before
input, including for counterfactual arms. No replacement START/A or hidden input.

Cases A/B/C retain phase grammar; A uses current formatting/history, B removes
history, C uses a concise goal and measured RAM. D/E use the same prompts as B/A
with common grammar, respectively. System catalogs remain identical within
grammar pairs. D/E are controls and can never be selected for production.

Actual call receipts record exact system/user text, raw reply, seed,
snapshot/model/ROM SHA, phase, native vocabulary, epoch, observation nonce and
prefill/decode. The existing controller separately records the actual distinct
action-permit nonce and verified before/after state. A proposal is not labeled
executed. Unknown ROMs cannot enter this experiment's verified VOID profile.

## Real experiment, not a synthetic success

The existing Android instrumentation runner opens the actual NES Activity and
uses its real model/action/save handlers. Qwen actually creates the PLAYING and
THREAT fixtures. No scripted gameplay buttons are supplied.20 held-out TITLE
states are frozen at separate neutral startup offsets before selection; these
are game-state offsets, not a claim of a supported ROM RNG-seed API. Saves carry
no authority; every restore creates a new epoch, checks SHA/frame/hash/RAM and
obtains fresh permits.

The same APK bytes and common fixtures are reused across all cases. Source,
APK/model/ROM pins and full bootstrap provenance are archived. Probe history
identifies its actual setup-model producer; it is not relabeled as the candidate's
own experience. Full episodes start at TITLE with cleared working journals and
collect their own verified history. Rewards remain null; no weights are trained.

Discovery:3 models ×5 variants ×3 seeds(42,1042,2042):135 one-step probes and45
episodes,16 decisions maximum per episode. Each decision gets baseSeed+index.
The10-minute case cap invokes ordinary STOP without lengthening the120s input
TTL. Negative bootstrap checks reject configuration changes while native is
busy and verify no transition after STOP during actual inference.

Selection requires all15 arm reports and identical snapshot SHAs. Rank actual
episode C count, fewer median lost lives, then median score; ties keep A.
Only B/C can become candidates. The held-out workflow evaluates20 A/candidate
pairs per changed model with distinct predefined states and seeds10042+1000*i.
Require >=5% higher median score (positive median when baseline is zero), no
worse C count or median lives lost, and complete verified evidence. This writes
an evaluation, never automatically changes active settings or weights.

## Run and evidence

The ordinary All-in-One workflow compiles the same ARM64/x86 sources and test
companion. A Draft PR whose title contains `[G4-EXPERIMENT]` opts into the reusable
G4 workflow after that build. No duplicate APK build is needed. Other PRs do
not launch the costly matrix. All jobs use isolated API35 x86_64 KVM Android.

`tools/run_g4_decision_experiment.py` handles visible SAF model import, actual
instrumentation, bounded archive transport and receipt validation.
`tools/summarize_g4_experiment.py` refuses missing/duplicate/mixed cases and
selection/holdout contamination. `DIAGNOSTICS_COMPLETED` means measurement
completion, not gameplay PASS. Missing native timing/identity/nonce evidence
is an incomplete experiment. Invalid model calls and ineffective WAIT remain
recorded failures of gameplay, not substituted answers.

Host checks: `java tests/NesDecisionExperimentVerify.java`,
`python3 -m unittest discover -s tests -p 'test_g4_decision_experiment.py'`,
and existing native/host/controller regressions. Synthetic negative unit
fixtures are explicitly HOST_UNIT. Real experiment results are NOT_RUN until
the exact source's Android jobs and raw evidence are reviewed; latest SHA,
run/job outcomes and blockers belong in the PR and Control Tower #159.

After discovery/holdout, adopt a model-specific prompt only with accepted actual
evidence and another ordinary real-game regression on the changed source.
If there is no improvement, retain A and report NO_PROVEN_IMPROVEMENT. D, E,
victory, physical POCO and release signing remain separate project gates.
