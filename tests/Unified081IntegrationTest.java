package org.localpilot.android;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import org.localpilot.android.learning.*;
import org.localpilot.android.retro.*;
import org.localpilot.android.rl.*;

/**
 * Verifies unified integration of LocalPilot 0.8.1 (VisionV2Core, VisionNoveltyMemory,
 * RealGameNeuralCritic, JumperWeightShadow, PaddleTransfer, PaddleWeightLab, WeightSpaceSkills,
 * TextRepetitionGuard, BundledModelInstaller) with the Adaptive Learning Engine, Main-Menu
 * AutonomousSelfTrainer, Main-Menu UnifiedSimulatorHub, and all 16 games (10 Market + 6 NES/DOS/SEGA).
 */
public final class Unified081IntegrationTest {
    private static int assertions = 0;
    private static void check(boolean cond, String msg) {
        assertions++;
        if (!cond) throw new AssertionError(msg);
    }

    public static void main(String[] args) throws Exception {
        File tmpDir = Files.createTempDirectory("lp-unified-081-").toFile();
        AdaptiveLearningEngine engine = AdaptiveLearningEngine.initGlobal(tmpDir);
        check(AdaptiveLearningEngine.global() == engine && tmpDir.equals(engine.storageRoot()),
            "AdaptiveLearningEngine.initGlobal binds global singleton to storageRoot");

        // 1. Actual temporal VisionV2 API, independent frames and novelty votes.
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

        // 3. Verify PaddleTransfer & PaddleWeightLab bridge
        PaddleTransfer.Candidate cand = PaddleTransfer.trainCandidate();
        check(cand != null && cand.eligible(), "PaddleTransfer candidate passes validation gate: " + cand.report());
        String encoded = PaddleTransfer.encode(cand);
        float decoded = PaddleTransfer.decode(encoded);
        check(Math.abs(decoded - cand.offset()) < 1e-4f, "PaddleTransfer LP-PADDLE-WEIGHTS-v1 round-trip");

        // 4. Read-only Jumper shadow must never grant input.
        JumperWeightShadow.Review shadow = JumperWeightShadow.review(null,2);
        check(!shadow.usable() && !shadow.inputAuthorized(),
            "Missing jumper scene provides no action authorization");
        TextRepetitionGuard rep = engine.repetitionGuard();
        check(rep.checkAndRecord("TAP_LEFT") && rep.checkAndRecord("TAP_LEFT")
            && !rep.checkAndRecord("TAP_LEFT"),
            "Repetitive teacher output is vetoed, never treated as real game reward");

        // 5. Verify all 16 bundled checkpoints (10 Market + 6 NES/DOS/SEGA) in app/src/main/assets/adaptive-engine-v1/
        check(TaskSchema.GameId.values().length == 16, "Exactly 16 GameIds (10 Market + 6 NES/DOS/SEGA)");
        File assetsRoot = new File("app/src/main/assets");
        AdaptiveLearningEngine prodEngine = new AdaptiveLearningEngine(assetsRoot);
        for (TaskSchema.GameId g : TaskSchema.GameId.values()) {
            TaskSchema schema = TaskSchema.forGame(g);
            File ckpt = prodEngine.checkpointFile(schema);
            check(ckpt.exists() && ckpt.length() > 1000, "Bundled asset checkpoint exists for " + g.id + ": " + ckpt);
            StudentPolicy loaded = ModelCheckpoint.loadRecovering(ckpt, schema);
            check(loaded != null, "Bundled checkpoint SHA-256 verified for " + g.id);
        }

        // 6. Verify API 30 (Android 11) window-binding fix & strict explicit-binding protection
        List<WindowFocus.WindowCandidate> api30UnfocusedActive = List.of(
            new WindowFocus.WindowCandidate(42, "org.localpilot.gamelab", false, true, 0, 0, 1080, 2400)
        );
        check(WindowFocus.selectApplicationWindow(api30UnfocusedActive, false, "", -1) == 42,
            "API 30: unfocused but active single GameLab window is selected for binding");

        List<WindowFocus.WindowCandidate> api30ExplicitAmongTwo = List.of(
            new WindowFocus.WindowCandidate(10, "org.localpilot.android", false, false, 0, 0, 1080, 2400),
            new WindowFocus.WindowCandidate(42, "org.localpilot.gamelab", false, false, 0, 0, 1080, 2400)
        );
        check(WindowFocus.selectApplicationWindow(api30ExplicitAmongTwo, true, "org.localpilot.gamelab", 42) == 42,
            "API 30: explicitly chosen GameLab window binds even before focus bit settles");
        check(WindowFocus.selectApplicationWindow(api30ExplicitAmongTwo, false, "", -1) == -1,
            "API 30: ambiguous unfocused multi-window state without explicit choice fails closed (-1)");
        check(WindowFocus.selectApplicationWindow(api30ExplicitAmongTwo, true, "org.localpilot.gamelab", 99) == -1,
            "API 30: explicit package with wrong windowId (99 != 42) fails closed (-1)");
        check(WindowFocus.selectApplicationWindow(List.of(
            new WindowFocus.WindowCandidate(10, "org.localpilot.android", true, true, 0, 0, 1080, 2400),
            new WindowFocus.WindowCandidate(42, "org.localpilot.gamelab", false, false, 0, 0, 1080, 2400)),
            true, "org.localpilot.gamelab", 42) == -1,
            "Explicit target cannot be replaced by a different focused and active window");
        check(WindowFocus.selectApplicationWindow(api30UnfocusedActive, true, "org.other", 99) == -1,
            "Sole active application is rejected when explicitly selected identity differs");
        check(WindowFocus.selectApplicationWindow(List.of(
            new WindowFocus.WindowCandidate(10, "org.first", false, true, 0, 0, 1080, 2400),
            new WindowFocus.WindowCandidate(42, "org.second", false, true, 0, 0, 1080, 2400)),
            false, "", -1) == -1,
            "Two active windows are ambiguous and reject autonomous binding");

        // 7. Verify BundledModelInstaller copies and verifies all 16 models on first run and preserves user checkpoints
        File freshRoot = Files.createTempDirectory("lp-first-run-models-").toFile();
        BundledModelInstaller.Result initial = BundledModelInstaller.install(freshRoot,
            name -> Files.newInputStream(java.nio.file.Path.of("app/src/main/assets/" + name)));
        check(initial.complete() && initial.installed() == 16,
            "Fresh Android installation loads all 16 preverified bundled model assets: " + initial.report());
        BundledModelInstaller.Result second = BundledModelInstaller.install(freshRoot,
            name -> { throw new java.io.IOException("Assets not needed after installation"); });
        check(second.complete() && second.preserved() == 16 && second.installed() == 0,
            "User model checkpoint files must be preserved across restarts: " + second.report());
        for (TaskSchema.GameId g : TaskSchema.GameId.values()) {
            TaskSchema schema = TaskSchema.forGame(g);
            check(ModelCheckpoint.load(new File(freshRoot, "adaptive-engine-v1/" + g.id + "-" + g.mechanicsVersion + ".zip"), schema) != null,
                "Bundled model checkpoint is usable for " + g.id);
        }
        File missingRoot = Files.createTempDirectory("lp-missing-model-").toFile();
        BundledModelInstaller.Result missing = BundledModelInstaller.install(missingRoot,
            name -> { throw new java.io.IOException("missing asset"); });
        check(!missing.complete() && missing.missing() == 16,
            "A missing bundle cannot be declared fully loaded");

        // 8. Verify Main-Menu UnifiedSimulatorHub (16 simulators render frames, learn themselves, and teach other games)
        UnifiedSimulatorHub simHub = engine.simulatorHub();
        check(simHub != null && simHub.isRunningInMainMenu(), "UnifiedSimulatorHub active by default on Main Menu");
        List<UnifiedSimulatorHub.SimulatorTickResult> simTicks = simHub.runAllSimulatorsCycle(20261106L);
        check(simTicks.size() == 16, "All 16 simulators stepped and trained in Main-Menu UnifiedSimulatorHub");
        check(simHub.totalSimStepsAllGames() == 16 && simHub.totalCrossGameTeachings() >= 10,
            "Simulators learned and taught other games across families: taught=" + simHub.totalCrossGameTeachings());
        for (TaskSchema.GameId g : TaskSchema.GameId.values()) {
            int[] rgb = simHub.renderSimulatorFrameRgb(g, 160, 120);
            check(rgb != null && rgb.length == 160 * 120, "Live RGB frame rendered for simulator " + g.id);
        }

        // 9. Verify Main-Menu AutonomousSelfTrainer & Simulator Hub UI wiring in MainActivity.java
        AutonomousSelfTrainer trainer = engine.selfTrainer();
        check(trainer != null && trainer.isAutoSelfLearnEnabled(), "AutonomousSelfTrainer active by default on Main Menu");
        String mainActivitySrc = Files.readString(java.nio.file.Path.of("app/src/main/java/org/localpilot/android/MainActivity.java"));
        check(mainActivitySrc.contains("org.localpilot.gamelab.TestDeviceActivity.class"),
            "Main menu opens embedded GameLab in the primary APK");
        check(mainActivitySrc.contains("Симуляция не является приёмкой Маркета"),
            "Main menu distinguishes simulation from real game acceptance");
        check(mainActivitySrc.contains("BundledModelInstaller.install(getFilesDir()") && mainActivitySrc.contains("AdaptiveLearningEngine.initGlobal(getFilesDir())"),
            "MainActivity installs all 16 bundled models and binds global engine on startup");

        System.out.println("Unified 0.8.1 + Retro (NES/DOS/SEGA) + Main-Menu Simulators & Self-Trainer integration: " + assertions + " assertions PASS");
    }
}
