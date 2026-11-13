"""Source-contract: a real trainable CPU student must USE the existing NES owner."""
from pathlib import Path
import unittest
ROOT=Path(__file__).resolve().parents[1]
def src(p):return (ROOT/p).read_text(encoding="utf-8")
class StudentIntegrationContract(unittest.TestCase):
 def test_student_training_is_not_fake_gguf_reward(self):
  store=src("model-lab/src/main/java/org/localpilot/android/HomebrewStudentStore.java")
  math=src("shared/src/main/java/org/localpilot/shared/HomebrewStudentNetwork.java")
  for part in ("HomebrewStudentNetwork.train(rows)","ConsoleExperienceMemory.readGoals(",
               "AtomicFile","sha256(weights)","NOT_VERIFIED","heldoutTdMae"):
   self.assertIn(part,store)
  self.assertIn("Math.tanh",math)
  self.assertIn("Collections.shuffle(order",math)
  self.assertIn("HomebrewGoalReward.verify(",math)
  self.assertIn("Need three independent real NES sessions",math)
  self.assertIn("reached()",math)
  self.assertNotIn("success=true",store)
 def test_student_uses_the_same_one_step_controller(self):
  model=src("model-lab/src/main/java/org/localpilot/android/ModelLabActivity.java")
  nes=src("game-lab/src/main/java/org/localpilot/gamelab/NesEmulatorActivity.java")
  js=src("game-lab/src/main/assets/nes/index.html")
  binding=src("model-lab/src/main/java/org/localpilot/android/HarnessModelBinding.java")
  for term in ("HomebrewStudentStore.propose(this,",
               "ConsoleAgentContract.MOVE","ConsoleAgentContract.TOKEN",
               "ConsoleAgentContract.MODEL_SHA"):
   self.assertIn(term,model)
  self.assertIn("HarnessModelBinding.assignStudent",src("model-lab/src/main/java/org/localpilot/android/HomebrewStudentStore.java"))
  self.assertIn("onHomebrewObservation(",nes)
  self.assertIn("ConsoleAgentContract.TASK_X",nes)
  self.assertIn("ConsoleAgentContract.TASK_Y",model)
  self.assertIn("GameLabBridge.onHomebrewObservation(",js)
  self.assertIn("HarnessCoordinator.Actor.GGUF",nes)
  self.assertIn("assignStudent(Context",binding)
  self.assertNotIn("dispatchGesture(",model)
 def test_panel_shares_weights_and_experiment(self):
  panel=src("app/src/main/java/org/localpilot/android/HarnessDashboardActivity.java")
  for label in ("HomebrewStudentStore.train(","HomebrewStudentStore.bindSandbox(",
                "HomebrewStudentStore.status(","Физическая on-policy приёмка"):
   self.assertIn(label,panel)
  self.assertIn("trainingWorker.execute(",panel)
  self.assertIn("trainingWorker.shutdown()",panel)
  self.assertIn("GoalEpisode",src("shared/src/main/java/org/localpilot/shared/ConsoleExperienceMemory.java"))
if __name__=="__main__":unittest.main()
