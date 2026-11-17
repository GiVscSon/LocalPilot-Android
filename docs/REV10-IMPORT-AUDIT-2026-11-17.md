# Rev10 import: blocked by actual compilation failures

Base audited: `32838fde8c2432dd060f3cf2923d4dd1910c9553` (main).
Bundle baseline: `3ab42116fbd04661e0a3ed1d588c1d0625cbb35d`.

The full Drive archive `LocalPilot_Android_0_8_5_MultifunctionalAgent_ControlTower159_rev10_2026-11-17.zip`
was downloaded and verified: 5,037,310 bytes, SHA-256
`7c85daa8a1221ebf2dedd349dfe170927453a5c7486a97e3c1b58a958106bd84`.
It contains 564 entries, including `tools/apply_0_8_5_to_github_main.py`.
The separate patch ZIP was not used. These findings concern the applicator in the full ZIP.

## Reproduction

In an isolated worktree at the audited main SHA, run the supplied applicator with
`--repo . --verify` (without `--commit` or `--sync-issue-159`).

Result: **HOST_UNIT FAIL**. `Release085LifecycleVerify` fails compilation:

```
HomebrewObs64.java:30: cannot find symbol StudentPolicyNet
ImportedNesRomAdapter.java:59: cannot find symbol pre.ok()
```

The lifecycle test supplies an incomplete explicit source list. The actual ROM
preflight result exposes `runnable()`, not `ok()`.

Run `./gradlew :app:compileDebugJavaWithJavac -PunifiedModelLab=true` with JDK 17
and the existing Android SDK. Result: **BUILD FAIL** (24 seconds).
The added AdaptivePpoGaeTrainer/AdaptiveLearningEngine reference missing
TaskSchema, ReplayStore, StudentPolicy, RealisticCurriculumEnv and other classes.
This is a bundle dependency failure, separate from the test source-list error.

## Import risks confirmed in source

- The applicator writes/deletes before validation, with no repository/base/dirty-tree guard.
- `--sync-issue-159` replaces the whole live issue body with a bundled historical report.
- `Release085LifecycleVerify` only compiles classes before printing its success message;
  it does not execute its advertised lifecycle assertions.
- `OnDeviceCandidateLifecycle.evaluateCandidateOnDevice` receives caller-supplied
  return arrays; it does not itself run an Android or NES evaluation episode.
- That new lifecycle uses six pairs, whereas the current Control Tower requires
  at least twenty disjoint paired holdouts. It is not an accepted replacement.
- The bundled controller/trainer rewrites must be reconciled with open PRs #179–#186;
  this audit does not discard their model-selection, storage, UI or actual-game fixes.

## Added non-mutating preflight

```
python3 tools/audit_embedded_patch.py /path/to/apply_0_8_5_to_github_main.py \
  --repo . --base 3ab42116fbd04661e0a3ed1d588c1d0625cbb35d
```

It parses literal payload metadata without executing the supplied program,
checks hashes, rejects unsafe paths and reports overwrites relative to the supplied
baseline. On audited main: 47 target/removal paths, one three-way conflict
(`.github/workflows/control-tower.yml`). Exit 2 means conflicts.
Exit 0 means only no textual conflicts; it is explicitly **not** build acceptance.
The regression test confirms non-execution, unchanged target bytes, integrity
rejection and path traversal rejection.

No main push, issue-body replacement, key change or release was performed.
UI_PASS / INFERENCE_PASS / TRANSITION_PASS / TRAINING_PASS / DEVICE_PASS for
this imported candidate are **NOT RUN**. The imported candidate is not installable
until the compilation failures are resolved.

Next: forward-port selected runtime changes onto the current integration line,
repair real dependencies and execute existing runtime tests; do not copy the full
old tree or weaken the evaluation gate to make the bundle's tests pass.
