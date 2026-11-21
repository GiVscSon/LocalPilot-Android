import importlib.util
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('restore_workspace',ROOT/'ci/restore_unified_one_apk.py')
restore=importlib.util.module_from_spec(spec);spec.loader.exec_module(restore)

class RestoreWorkspaceServiceTest(unittest.TestCase):
 def test_legacy_overlay_cannot_remove_stop_or_installed_game_apis(self):
  with tempfile.TemporaryDirectory() as td:
   root=Path(td)
   for name in (*restore.PROTECTED,'game-lab/src/main/assets/nes/index.html','game-lab/src/main/assets/nes/latched-buttons.js'):
    p=root/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes((ROOT/name).read_bytes())
   subprocess.run(['git','init','-q',str(root)],check=True)
   subprocess.run(['git','-C',str(root),'add','.'],check=True)
   subprocess.run(['git','-C',str(root),'-c','user.name=Test','-c','user.email=test@example.invalid','commit','-qm','reviewed'],check=True)
   service=root/'app/src/main/java/org/localpilot/android/PilotService.java'
   expected=service.read_bytes()
   service.write_text('class PilotService { /* historical overlay */ }')
   dependencies={}
   for name in ('WindowFocus','BubbleVision','GridPuzzleVision'):
    path=root/('app/src/main/java/org/localpilot/android/'+name+'.java')
    dependencies[path]=path.read_bytes();path.write_text('class '+name+' {}')
   # Preserve additive model code while restoring the one actual executor.
   model=root/'app/src/main/java/org/localpilot/android/learning/OverlayModel.java'
   model.parent.mkdir(parents=True,exist_ok=True);model.write_text('class OverlayModel {}')
   previous=restore.ROOT;restore.ROOT=root
   try:
    restore.restore();self.assertEqual(service.read_bytes(),expected)
    for path,reviewed in dependencies.items():self.assertEqual(path.read_bytes(),reviewed)
    restore.restore();self.assertEqual(service.read_bytes(),expected)
    self.assertEqual(model.read_text(),'class OverlayModel {}')
   finally:restore.ROOT=previous

if __name__=='__main__':unittest.main()
