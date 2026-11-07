#!/usr/bin/env python3
"""Reconcile stale integration tests with actual, safety-preserving LocalPilot APIs.

This deliberately does NOT add synthetic learning labels or grant gesture authority.
The original 16-model, simulator, manual-binding and asset checks remain.
"""
from pathlib import Path

p=Path("tests/Unified081IntegrationTest.java")
s=p.read_text(encoding="utf-8")
a=s.index("        // 1. Verify VisionV2Core & VisionNoveltyMemory integration")
b=s.index("        // 3. Verify PaddleTransfer & PaddleWeightLab bridge")
assert a < b and "VisionV2Core.detectMotionBlobs" in s[a:b]
s=s[:a]+'''        // 1. Actual temporal VisionV2 API, independent frames and novelty votes.
        VisionV2Core v2 = engine.visionV2();
        check(v2 != null, "Vision V2 wired");
        VisionV2Core.Region roi = new VisionV2Core.Region(0, 0, 100, 100);
        VisionV2Core.Frame priming = v2.observe((x,y)->0x101010,100,100,roi,null,1000,1,0);
        check(priming.status()==VisionV2Core.Status.PRIMING && !priming.ready(),
            "First screenshot is only priming, never outcome evidence");
        VisionV2Core.Frame changed = v2.observe((x,y)->(x>=20&&x<60&&y>=20&&y<60)?0xf0f0f0:0x101010,
            100,100,roi,null,1100,1,0);
        check(changed.ready() && changed.changedCells()>0 && changed.hasLocalMotion(),
            "Fresh aligned frames identify temporal change without game reward");
        check(v2.observe((x,y)->0x101010,100,100,roi,null,1100,1,0).status()
            ==VisionV2Core.Status.REPLAY_REJECTED,
            "Replayed timestamp rejected");

        VisionNoveltyMemory mem = engine.noveltyMemory();
        check(mem != null && mem.identify(0x224466,30)==0,
            "Novel appearance starts UNKNOWN");
        mem.observe(0x224466,30,1000);
        mem.observe(0x224466,30,1000);
        check(mem.stableCount()==0, "Same frame cannot fabricate confidence");
        mem.observe(0x224466,30,1001);
        mem.observe(0x224466,30,1002);
        check(mem.stableCount()==1 && mem.identify(0x224466,30)>0,
            "Three distinct-frame appearance votes identify a cluster, not a semantic game label");

        // 2. Existing critic accepts ONLY independently verified outcomes.
        RealGameNeuralCritic critic = engine.neuralCritic(ArcadeProfile.PADDLE);
        check(critic!=null && !critic.ready() && critic.updates()==0,
            "Untrained critic cannot veto real actions");
        check(engine.neuralCritic(ArcadeProfile.ARI)==null,
            "Jumper has no compatible real-game outcome-critic schema");
        check(!critic.learnVerified(null,null,null)
            && !critic.learnNonresponse(null,null,null,true),
            "Missing visual response never becomes a positive or negative label");
        String encodedCritic = critic.encode();
        RealGameNeuralCritic restoredCritic = RealGameNeuralCritic.decode(encodedCritic,ArcadeProfile.PADDLE);
        check(restoredCritic.updates()==0 && !restoredCritic.ready()
            && restoredCritic.profile()==ArcadeProfile.PADDLE,
            "Schema-bound neural checkpoint round-trip retains untrained state");
        boolean rejectedOtherProfile=false;
        try { RealGameNeuralCritic.decode(encodedCritic,ArcadeProfile.TRAFFIC); }
        catch(IllegalArgumentException expected) { rejectedOtherProfile=true; }
        check(rejectedOtherProfile,"Cross-game critic checkpoint rejected");

'''+s[b:]
a=s.index("        // 4. Verify JumperWeightShadow & TextRepetitionGuard")
b=s.index("        // 5. Verify all 16 bundled checkpoints")
assert a<b and "shadow.applyDelta" in s[a:b]
s=s[:a]+'''        // 4. Read-only Jumper shadow must never grant input.
        JumperWeightShadow.Review shadow = JumperWeightShadow.review(null,2);
        check(!shadow.usable() && !shadow.inputAuthorized(),
            "Missing jumper scene provides no action authorization");
        TextRepetitionGuard rep = engine.repetitionGuard();
        check(rep.checkAndRecord("TAP_LEFT") && rep.checkAndRecord("TAP_LEFT")
            && !rep.checkAndRecord("TAP_LEFT"),
            "Repetitive teacher output is vetoed, never treated as real game reward");

'''+s[b:]
assert "RealGameNeuralCritic.extractFeatures" not in s
assert "JumperWeightShadow shadow = new" not in s
p.write_text(s,encoding="utf-8")
print("Updated Unified081IntegrationTest to actual VisionV2/Novelty/Critic/Shadow APIs; safety checks preserved")
