# Real NES agent continuation · #183 / Draft #186

Updated 2026-11-17 (Europe/Moscow). User: work autonomously toward a universal agent; real games and real control only; NES first; no educational scenes or fabricated measurements. Do not ask routine permission. Do not call an intermediate PR completion of #183. Release, main merge and signing changes remain paused.

## Exact source and current acceptance

- Main observed: `32838fde8c2432dd060f3cf2923d4dd1910c9553`.
- Base PR #185: `23d82aa1563ab6416b368e5b1d378ca7ab6bb56d` (model-library work, all15 CI passed).
- [Draft #186](https://github.com/GiVscSon/LocalPilot-Android/pull/186), branch `feat/agent-complete-workspace-183`.
- Validated baseline code HEAD: `8ad64d4ff96ca1f593450eda666c5eb8b9685004`.
- Tested PR merge candidate: `455816f888f11a81394b5217e4d3c8fd8053b008`.
- [All-in-One44013427041](https://github.com/GiVscSon/LocalPilot-Android/actions/runs/44013427041): ARM64 build/lint/packaging and actual native compiler audit PASS; x86 build/audit PASS; all six API30/35/36 phone/tablet UI jobs PASS; Application runtime ownership and visible model import/bind/unbind/delete/STOP PASS; internal fixture sustained regression PASS.
- **Actual game job133807909040 FAILED.** Fourteen correlated native decisions all chose UP. Actual ship moved upward, lives fell2→0, score/kills/shots stayed0; the ROM ended in real GAME OVER. No game-progress pass. Measured inference durations6090–9104ms; native prefill5665–8583ms, decode284–517ms. Optimization fixed the prior latency blocker, not the policy. The old runner waited after terminal loss until its global deadline; the prepared runner rejects that loss immediately.
- Same x86 APK SHA256: `5e8a5b2924e203f53eb3e78208f63ef1f526c849d3cb7920e19fc99d050c6935`.
- Control Tower #159 comment6937223956 is the current coordination entry. Update it after a material result, through the Android QA skill authorization.

## Actual implementation

The primary Main→NES workspace always selects REAL_GAMES_ONLY, including x86 acceptance. ARM64 HARNESS_TEST_FIXTURES=false. User controls hide/refuse educational auto-launches, bundled exercise replacement, Student/CPU demo controls, simulator-training archives and legacy ModelLab episode-sharing/observation tools. Existing private checkpoints, models, ROMs and archives are preserved. Public evidence uses real-ROM records, never historical fixture/PPO results. The same Activity has actual model library management, Agent, manual handover, save and STOP. Busy menus/model selectors cannot cover STOP.

There is one existing Application-owned runtime/catalog, JSNES WebView, HarnessCoordinator and input executor. STOP, original120s per-observation TTL, frame/hash/epoch, nonce and one-use permits remain. No second trainer/runtime/core or scripted model answer was added. Completed ordinary-ROM trials are cleared only after verified permit completion and persistence; lifecycle backgrounding stops between-step episodes too.

Unknown ROMs require a genuine vision GGUF plus mmproj and actual fresh PNG. They have UNKNOWN RAM/reward=NULL and never inherit another game's memory map. **Vision has not passed Android acceptance.** The user workspace exposes no claim of universal support.

Exact VOID LANCER v1.0.0, ROM SHA `5eb801aca68131a3d17fa82d8901d46f6098b878d1e00cd1c47d00c001c2405a` (40976 bytes, official Riyoway MIT-licensed release), has a SHA-bound GAME_RAM profile: ship coordinates, lives, bombs, stage, real decimal score, kills, shots, bullets and nearest actual enemy/hostile bullet. Source memory map is in `VoidLancerGameState` and the existing JSNES bridge. Inactive slots are excluded. New14-field records validate full coordinate pairs; old10-field records remain readable as unknown geometry.

GAME_RAM sends those actual observations to the selected real GGUF via the same text JNI endpoint. It is explicitly not vision inference. Its bounded context includes real recent ship/score/kill/life deltas, not an invented reward or a claim of weight training. Generic journals bind ROM SHA, model SHA and epoch, have nested integrity checks and cannot enter trusted homebrew PPO. Model output alone has no input permission.

The existing JNI has typed controller mode, exact BUTTON grammar and16-token output. General diagnostics remain diagnostic. Native Debug kernels now get -O2 while retaining symbols/assertions; no NDEBUG, fast-math or march=native was introduced. Both actual ARM64/x86 compile_commands audits passed for JNI/ggml sources. Threads are bounded by actual online CPUs, max4. Native phase logs contain only actual phase names/start/end/durations, no prompts/paths/frames. Compiler optimization is not itself a measured performance/gameplay success.

## Evidence and failures

The actual game runner uses the pinned ROM and MNN-Pocket GGUF SHA `a281c127e388b16537fd3f5d31ab32d3f87739e6dc514ac1093c80d9eac8d097` (191896672 bytes), both via visible SAF. It selects16 actions and requires exact native/model/frame/hash/action correlation, semantic ship/fire/bomb control, actual score/kill progress or real win state, then STOP. Pixel changes and action counts cannot pass. Every inference retains its original120s TTL. A longer job budget does not extend that TTL.

HOST_UNIT/CORE PASS:49 typed model assertions, shared journal integrity/history isolation,31 Harness Python checks,28 UI-runner,7 SAF/game-state checks and4 compiler-audit negative tests. Actual canonical JSNES2.1.0 core/profile/revocation/control regressions pass. The official ROM was rebuilt from its tagged assembler/assets and exactly matched release SHA. Core tests compare target/threat geometry against live RAM and exclude stale inactive slots. Host Android bridge authority is modeled; these tests are not neural or physical-device proof.

Earlier failures remain failures:

- fb5e5c0 and bbd790f8: actual Android vision exceeded unchanged120s TTL. No accepted vision or universal-game claim.
- 4cbab45f: real model-controlled transition, then stale pendingTrial blocked the next observation. Fixed in a9396cc.
- a9396cc: actual game failed with broad stale-frame text; the old condition also covered eligibility/frozen mode/TTL, so do not assert a proven hash mismatch.
- d313889: run44011897759/job133802458642 failed on120s model TTL before a completed inference record. Its actual x86 compiler log explicitly showed CMAKE_BUILD_TYPE=Debug. This led to the optimized kernel build in8ad64d4.
- Internal G2 runner previously failed only at capitalized Android delete confirmation after four correlated calls. New8ad64d4 actually passed visible delete and all four calls; this is still an internal fixture regression, not real-game success.

No physical Android device is connected locally; no SDK/ADB/KVM is available in this workspace. Use actual GitHub Android runners, never claim local AVD or PHYSICAL_ANDROID success. No game-learning improvement, universal support, external Android-game generalization or release acceptance is proven.

## Continue immediately

Fetch real-game job133807909040 and inspect the actual report or failure diagnostics. Preserve model choices, before/after states, source/APK identity and measured native timings. Fix the actual cause without relaxing TTL, semantic progress, STOP or provenance. Runner correction rejects a verified real GAME OVER immediately instead of waiting until its global deadline; it must not turn loss into success.

After actual model-to-semantic-control and score progress pass, improve sustained gameplay, title/restart controls, combined buttons and independently verified vision across more real NES games. Single-button control and exact-ROM text observations are not universal support. Reuse the existing policy/trainer/control plane. Do not move to external environments before actual NES acceptance.

Changes after8ad (not yet Android-accepted): ordinary-ROM Agent entry re-authorizes the native JS game profile even when already frozen after loading a save. Previously aiFrozen bypassed profile selection after a fresh core load, so a restored ordinary ROM could reject its first step. The actual VOID core test now saves, reloads into a fresh epoch, proves that saved data carries no input profile, then explicitly re-authorizes and steps. Shared six-action fixture regression still passes. Runner diagnostics also attach measured inferenceElapsedMs only from one integrity-checked exact model/frame/hash/action record (10 SAF/game-state/correlation unit checks pass). Real GAME OVER is an immediate failure, not progress or a32-minute wait.


Also changed (not yet Android-accepted): START/SELECT are scoped by a new ordinary-game observation capability in the same HarnessCoordinator. Legacy/Student tickets keep six buttons; only a consumed full-game GGUF ticket can authorize menu input, in frozen mode, with the same TTL/nonce/STOP. JNI modes are now explicit0 diagnostic /1 six-button fixture /2 ordinary NES; all Java/host-native declarations are updated together. Parser and native grammar remain strict. The actual core test enters the real title using permitted START, and the next Android game runner no longer presses START by ADB: the model must begin the game. HOST_UNIT90 coordinator assertions and56 typed host assertions pass, along with the actual core/revocation/save tests; this does not prove that the GGUF chose START on Android. Combined buttons are still unfinished.


The next real-game run selects the existing catalog Qwen3.5-0.8B-Q4_0 (563036064 bytes, SHA57d1997790d1744fba5b40a7317df71ea5e2acee28c47e78f0cce39c0703f8cf, pinned repo revision807dfd9a6fadbf703207aac3bfb0b2b3040d077c). This is an actual different selected model, not a scripted replacement for failed Pocket decisions. Pocket remains in the separate internal G2 regression. The new real-game gate also requires a correlated model START from actual title0→playing1; ADB no longer starts gameplay. Score/kill progress must be a positive actual playing→playing delta or actual playing→win, never a menu counter change or terminal loss. Do not force a successful action to make the test green.


## 2026-11-17 08:10 Moscow · latest actual result and working changes

HEAD6b4ed2ac29868f276c4c552635c6da8e73ff5620 / tested merge5d95386547ae1968ae93e7fe824409c76a4ed900: all16 other workflows passed; All-in-One44018811504 failed only real-game acceptance after a rerun of sustained navigation. Actual Qwen3.5-0.8B's first decision was NONE in5797ms, no game START or accepted progress. Job133825233034 failed safely and released input. The sustained job133825232935 initially could not find Harness Console; exact-source rerun133829087016 passed. Preserve the initial navigation failure; do not count retries as new source proof.

Working changes after6b4 (not yet Android-accepted): full-game capabilities permit canonical NES chords ordered vertical(UP/DOWN), horizontal(LEFT/RIGHT), A, B, maximum4 held buttons. START/SELECT stay alone; opposing axes, duplicates, noncanonical orders and oversized chords fail closed. One observation and one existing permit cover one16-frame chord; all components release at the boundary or STOP. Legacy/Student controls remain six buttons. Native controller mode2 has37 real button/chord commands plus NONE, bounded24 output tokens; diagnostics and legacy mode remain unchanged. Trial/inference journals preserve the exact chord. Explicit isolated compiler source lists include the shared parser dependency.

Actual VOID core tests verify simultaneous movement+fire, four-button diagonal/bomb action after actual cooldown frames, save/restore profile re-authorization, invalid chord refusal and mid-chord STOP with all actual core buttons released and no completed partial transition. These are real CPU/PPU with modeled Android authority, not neural/Android game success. Source/target17 HOST_UNIT:94 coordinator and69 typed-host assertions, journal integrity/context, bridge codec and inference journal tests pass; Python31 Harness /30 UI runner /11 SAF-game-correlation checks pass. C++ controller-format/grammar contract12 checks pass with -Wall -Wextra -Werror. Existing core/Student regressions pass.

The pinned llama.cpp legacy ChatML formatter emits only the assistant header, while its Qwen3.5 template explicitly provides a non-thinking generation branch. A new controller-only helper conditionally uses the model template's empty think block when its actual metadata advertises enable_thinking and both think tags and the recognized ChatML generation boundary is present. No action or result is supplied by that formatter. Diagnostics mode0 is untouched; unknown/already-complete templates are not rewritten. This is a formatting compatibility correction, not a proven explanation or cure for NONE. The next actual model run must verify it. Native logs report whether the actual model took this branch, without prompts or private frames.

Title observations now describe the actual title phase and inactive gameplay counters, rather than presenting inactive lives=0 as an active lost run. Actual raw ROM state remains in the journal. Recent verified context is newest-first so its latest outcomes survive the bounded prompt. Test-packaging-only logs include actual observation profile/frame/hash/game state for debugging even when a model returns NONE. CI failure diagnostics preserve a fresh observed UI and bounded text in logs; this helper refuses to capture a physical-device UI.

Continue on the same Draft #186, same existing model/runtime/core/controller. Select actual pinned Qwen through visible SAF and require model START, real permitted game control, positive active-game score/kill delta or true win and STOP. Do not manufacture success from the controller/format fixes. Vision, sustained successful play, weight learning and universal support remain unaccepted; release/main merge/signing stay paused.
