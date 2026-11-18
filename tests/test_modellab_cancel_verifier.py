"""HOST_UNIT mutation checks for the STOP source guard; no neural execution."""
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

ROOT=Path(__file__).resolve().parents[1]
APP='app/src/main/java/org/localpilot/android/EmbeddedGgufRuntime.java'
CPP='app/src/main/cpp/localpilot_embedded.cpp'
FILES=[APP,CPP,'model-lab/src/main/java/org/localpilot/android/EmbeddedGgufRuntime.java',
       'model-lab/src/main/java/org/localpilot/android/ModelLabActivity.java','model-lab/src/main/AndroidManifest.xml']

class CancellationVerifierTests(unittest.TestCase):
 def run_guard(self,mutate=None):
  with tempfile.TemporaryDirectory() as directory:
   root=Path(directory)
   for name in FILES:
    text=(ROOT/name).read_text()
    if mutate:text=mutate(name,text)
    dest=root/name;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_text(text)
   return subprocess.run([sys.executable,str(ROOT/'tools/verify_modellab_cancel.py')],cwd=root,text=True,capture_output=True)
 def test_current_inference_jobs_are_guarded(self):
  result=self.run_guard();self.assertEqual(result.returncode,0,result.stderr)
 def test_reset_in_another_job_cannot_hide_missing_experiment_reset(self):
  def mutation(name,text):
   if name!=APP:return text
   start=text.index(' public void proposeNesExperiment(');end=text.index('  public void probeGpu(',start)
   body=text[start:end].replace('prepareNativeInference();','/* missing reset */',1)
   return text[:start].replace('prepareNativeInference();','prepareNativeInference();prepareNativeInference();',1)+body+text[end:]
  result=self.run_guard(mutation);self.assertNotEqual(result.returncode,0)
 def test_experiment_cannot_reset_concurrent_stop_in_native(self):
  def mutation(name,text):
   if name!=CPP:return text
   point=text.index('Java_org_localpilot_android_EmbeddedGgufRuntime_nativeInferExperiment(')
   return text[:point]+text[point:].replace('std::lock_guard<std::mutex> guard(lock);','std::lock_guard<std::mutex> guard(lock);cancelled.store(false);',1)
  result=self.run_guard(mutation);self.assertNotEqual(result.returncode,0)

if __name__=='__main__':unittest.main()
