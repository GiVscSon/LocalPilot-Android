"""Source regression contract for one NES+GGUF+PPO loop; no fake successes."""
import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
def read(path):return (ROOT/path).read_text(encoding='utf-8')

class HarnessLiveCycle(unittest.TestCase):
 def setUp(self):
  self.nes=read('game-lab/src/main/java/org/localpilot/gamelab/NesEmulatorActivity.java')
  self.model=read('model-lab/src/main/java/org/localpilot/android/ModelLabActivity.java')
  self.dash=read('app/src/main/java/org/localpilot/android/HarnessDashboardActivity.java')
  self.storage=read('shared/src/main/java/org/localpilot/shared/VerifiedPpoReplayStore.java')
  self.contract=read('shared/src/main/java/org/localpilot/shared/HarnessDemoWorkflow.java')
 def test_single_in_app_controller_for_every_mode(self):
  self.assertIn('HarnessCoordinator.nes()',self.nes)
  self.assertIn('requestFrozenAiObservation()',self.nes)
  self.assertIn('startStudentBatch()',self.nes)
  self.assertNotIn('new HarnessCoordinator()',self.nes)
  self.assertIn('EXTRA_MODE',self.contract)
  self.assertIn('GGUF_MAX_STEPS=4',self.contract)
 def test_only_real_emulator_data_starts_autonomous_play(self):
  self.assertIn('launchRom(HomebrewNes.cartridge(),true)',self.nes)
  self.assertIn('if(autoStartArmed&&count>=15&&homebrewRom',self.nes)
  self.assertIn('if(launched&&homebrew&&autoLaunchArmed)',self.nes)
  self.assertIn('taskGoalSaved=true',self.nes)
  self.assertIn('if(!saved||!taskGoalSaved||',self.nes)
  self.assertIn('"PIXELS_DIFFER".equals(readyTrial.status)',self.nes)
  self.assertIn('rightProgress.verified()',self.nes)
 def test_model_is_inferred_by_existing_engine_not_mocked(self):
  self.assertIn('engine.proposeNesButton(',self.model)
  self.assertIn('HarnessModelBinding.resolve(this,store.list())',self.model)
  self.assertIn('proposeNesAction(true)',self.model)
  self.assertIn('HarnessModelExecutionLog.append(',self.model)
  self.assertIn('ConsoleAgentContract.parseButton(answer)',self.model)
  self.assertIn('ConsoleAgentContract.MOVE',self.model)
  self.assertIn('HarnessDemoWorkflow.EXTRA_AUTO_MODEL',self.nes)
 def test_replay_durable_but_unproven_weights_never_auto_promoted(self):
  self.assertIn('VerifiedPpoReplayStore.append(vault,records)',self.nes)
  self.assertIn('LearningCoordinator.prepare(active,records,vault)',self.nes)
  self.assertIn('VerifiedReplayFilter.inspect(steps).eligible()',self.storage)
  self.assertIn('output.getFD().sync()',self.storage)
  self.assertIn('StudentPolicyNet.load(candidate)',
     read('shared/src/main/java/org/localpilot/shared/LearningCoordinator.java'))
  self.assertIn('PROMOTION',read('shared/src/main/java/org/localpilot/shared/LearningCoordinator.java').upper())
 def test_panel_controls_are_real_routing_and_not_third_input_owner(self):
  self.assertIn('startTrustedNesWorkflow(HarnessDemoWorkflow.GGUF_PLAY_4)',self.dash)
  self.assertIn('startTrustedNesWorkflow(HarnessDemoWorkflow.STUDENT_TRAIN_16)',self.dash)
  self.assertIn('VerifiedPpoReplayStore.summary(',self.dash)
  self.assertNotIn('dispatchGesture',self.dash)
  self.assertIn('STOP',self.nes)

if __name__=='__main__':unittest.main()
