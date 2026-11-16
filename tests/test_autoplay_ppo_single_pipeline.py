"""Guard the ONE-APK NES autoplay, persistent state, PPO candidate and strict 20-pair gate.
This source-level contract is not a claim of measured win rate or physical performance.
"""
from pathlib import Path
import unittest
ROOT=Path(__file__).resolve().parents[1]
def src(p):return (ROOT/p).read_text(encoding="utf-8")

class OneNesAutoplayPpoPipeline(unittest.TestCase):
 def setUp(self):
  self.panel=src("app/src/main/java/org/localpilot/android/HarnessDashboardActivity.java")
  self.nes=src("game-lab/src/main/java/org/localpilot/gamelab/NesEmulatorActivity.java")
  self.criterion=src("shared/src/main/java/org/localpilot/shared/StudentEvaluationGate.java")
  self.trainer=src("shared/src/main/java/org/localpilot/shared/LearningCoordinator.java")
  self.save=src("shared/src/main/java/org/localpilot/shared/NesSaveStore.java")

 def test_single_student_and_no_second_training_panel(self):
  self.assertIn("StudentPolicyNet.load(",self.panel)
  self.assertIn("VerifiedPpoReplayStore.summary(",self.panel)
  self.assertIn("HarnessDemoWorkflow.STUDENT_TRAIN_16",self.panel)
  self.assertIn("Архивный Student 7→12→6",self.panel)
  for old in ("HomebrewStudentStore.train(", "HomebrewStudentStore.bindSandbox(",
              "trainingWorker.execute(", "trainingWorker.shutdown()"):
   self.assertNotIn(old,self.panel)
  self.assertIn("Отвязка не удаляет веса и игровые эпизоды",self.panel)
  self.assertIn("HarnessCoordinator.nes().snapshot()",self.panel)

 def test_same_controller_fences_autoplay_and_restored_inputs(self):
  self.assertIn("HarnessCoordinator.nes()",self.nes)
  self.assertIn("NesSaveStore.save(",self.nes)
  self.assertIn("NesSaveStore.load(",self.nes)
  self.assertIn("progressVerifiedActions=saved.verifiedActions()",self.nes)
  self.assertIn("saved.policySha256()",self.nes)
  self.assertIn("harness.stopRevision()",self.nes)
  self.assertIn("HarnessDemoWorkflow.AUTOPLAY_BATCHES",self.nes)
  self.assertIn("ATOMIC_MOVE",self.save.upper()) # check production atomic replace of NES save
  self.assertNotIn("ActionPermit",self.save,"NES save must never persist input ownership")
  self.assertNotIn("Ticket ",self.save,"NES save must never persist old model tickets")

 def test_ppo_candidates_never_activate_without_real_eval(self):
  self.assertIn("PPO_PENDING_CANDIDATE_MUST_BE_EVALUATED",self.trainer)
  self.assertIn("verifiedSteps.get(0).epoch()",self.trainer)
  self.assertIn('"BLOCKED_UNTIL_INDEPENDENT_EVALUATION"',self.trainer.replace("\\",""))
  self.assertIn("MIN_SCENARIOS=20",self.criterion)
  self.assertIn("activeMedianReturn",self.criterion)
  self.assertIn("candidateMedianReturn",self.criterion)
  self.assertIn("newMedian>oldMedian+Math.max(0.05f,Math.abs(oldMedian)*0.05f)",self.criterion)
  self.assertIn("trainingX.contains(row.xAfter())",self.criterion)
  self.assertIn("RewardVerifier",self.criterion)
  self.assertIn("NO_PROVEN_IMPROVEMENT",self.criterion)
  self.assertIn("Остальные ROM: награда UNKNOWN",self.panel)

 def test_real_jsnes_holdout_is_not_replay_or_fake_win(self):
  js=src("tests/nes_student_holdout_smoke.cjs")
  accept=src("tests/StudentHoldoutRealNesVerify.java")
  for token in ("const scenarios=[]","scenarios.length<20",
                "trainX.has(initial.x)","const baseline=runScenario(",
                "const proposed=runScenario(",
                "actual student action legal","machine.loadROM("):
   self.assertIn(token,js)
  self.assertIn("physicalAndroidVerified",accept)
  self.assertIn("BLOCKED_UNTIL_DEVICE_HOLDOUT_AND_ROLLBACK",accept)
  self.assertIn("activeMeanReturn",accept)
  self.assertIn("candidateMedianReturn",accept)

if __name__=="__main__":unittest.main()
