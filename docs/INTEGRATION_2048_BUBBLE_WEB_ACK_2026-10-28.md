# LocalPilot: integration candidate (2026-10-28)
Source: PR #12 (verified real-game learning; API30/35/36 PASS on be2d052). Integrates:
- PR #10 opt-in rated Web 2048 lesson transfer (Web2048Transfer; WebLearningActivity). The lesson is neither a gesture nor a permission.
- PR #11 ACK-gated Response2048 and bounded one-time read-only screenshot timeout recovery.
- Existing PR #12 LiveGameLearning in real 2048 and Bubble is preserved.
- The Live + Web bonuses are clamped to a combined ±0.4 ONLY among legal 2048 moves. The base heuristic still chooses.
- Only a post-ACK Response2048.VERIFIED permits Verified2048 reward, audit, and live learner update. An unconfirmed swipe causes STOP by timeout.
- PR #13 Native2048Learning intentionally not merged: separate model would double-count the same verified move, has weaker key scope, and duplicates LiveGameLearning. Retain as a review candidate, not a second on-device reward writer.
- 2048 game window / ROI / focus / panel / STOP safety remains fail-closed. If Android ACK is missing, no reward is recorded.
- Demo compact STOP docks outside its real touch target, with an instrumental no-overlap test.
Tests: javac tests/Verify.java; java Verify; javac tests/Upgrade727Verify.java; java Upgrade727Verify; Android Gradle CI; emulator API30/35/36.
Acceptance threshold: 3 complete green matrices on the SAME integration SHA; physical device 5 verified successive moves in 2048 and Bubble, overlay minimization, signing certificate comparison with installed APK. This has NOT been done at commit creation.
No production APK release or merge to main prior to acceptance.
