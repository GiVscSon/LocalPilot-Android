"""Synthetic negative controls for Android evidence admission, not device proof."""
import copy
import hashlib
from pathlib import Path
import struct
import sys
import unittest
from unittest.mock import patch
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
import harness_student_device_acceptance as runtime

class AndroidCandidateEvidence(unittest.TestCase):
 def fixture(self):
  active,candidate=b'old synthetic weights',b'new synthetic weights'
  m=dict(schema='localpilot-student-candidate-v1',task='builtin-homebrew-right-progress-v1',
   observationSchema='obs64-homebrew-x-v1',policyBeforeSha256=hashlib.sha256(active).hexdigest(),
   policyAfterSha256=hashlib.sha256(candidate).hexdigest(),romSha256='a'*64,
   verifiedRolloutSteps=16,changedParameters=20,adamSteps=4,evaluation='NOT_RUN',
   promotion='BLOCKED_UNTIL_INDEPENDENT_EVALUATION')
  return m,active,candidate
 def test_integrity_and_activation_fail_closed(self):
  m,a,c=self.fixture();runtime.verified_manifest(m,a,c)
  for key,value in [('task','reach-other-goal'),('verifiedRolloutSteps',15),
   ('changedParameters',True),('promotion','ACTIVE'),('evaluation','PASS'),
   ('policyAfterSha256','b'*64),('observationSchema','7-input')]:
   with self.subTest(key=key),self.assertRaises(RuntimeError):
    changed=copy.deepcopy(m);changed[key]=value;runtime.verified_manifest(changed,a,c)
  with self.assertRaises(RuntimeError):runtime.verified_manifest(m,a,a)
 def journal(self,manifest,wrong_reward=False):
  previous='0';out=[]
  for i in range(16):
   reward=1.0 if wrong_reward and i==8 else 0.0
   fields=['LOCALPILOT_HOMEBREW_GOAL_V1','555',manifest['romSha256'],
    'student-homebrew-v1',manifest['policyBeforeSha256'],'A',str(45+16*i),str(61+16*i),
    '128','128',struct.pack('>f',reward).hex(),manifest['task'],'PPO_BLOCKED',previous]
   body='|'.join(fields);previous=hashlib.sha256(body.encode()).hexdigest()
   out.append(body+'|'+previous)
  return ('\n'.join(out)+'\n').encode()
 def test_rehashing_cannot_invent_reward(self):
  m,_,_=self.fixture()
  report=runtime.verify_journal(self.journal(m),m)
  self.assertEqual(report['coreFrames'],256)
  with self.assertRaises(RuntimeError):runtime.verify_journal(self.journal(m,True),m)
  with self.assertRaises(RuntimeError):runtime.verify_journal(self.journal(m).split(b'\n',1)[1],m)
 def test_remote_file_failure_is_not_transport_success(self):
  import subprocess
  missing=subprocess.CompletedProcess([],1,b'cat: no such file',b'')
  with patch.object(runtime.subprocess,'run',return_value=missing) as run:
   with self.assertRaises(RuntimeError):runtime.private_file('files/candidate.json')
   self.assertEqual(run.call_args.args[0][:4],['adb','shell','-T','run-as'])
 def test_permission_failure_is_not_retried_as_pending_data(self):
  import subprocess
  denied=subprocess.CompletedProcess([],1,b'run-as: package not debuggable',b'')
  with patch.object(runtime.subprocess,'run',return_value=denied):
   with self.assertRaises(RuntimeError) as result:runtime.private_file('files/candidate.json')
   self.assertNotIsInstance(result.exception,runtime.EvidencePending)
  absent=subprocess.CompletedProcess([],1,b'cat: files/candidate.json: No such file or directory',b'')
  with patch.object(runtime.subprocess,'run',return_value=absent):
   with self.assertRaises(runtime.EvidencePending):runtime.private_file('files/candidate.json')
 def test_no_device_setup_on_physical_phone(self):
  with patch.object(runtime.ui,'is_emulator',return_value=False),patch.object(runtime.ui,'adb') as adb:
   with self.assertRaisesRegex(RuntimeError,'Isolated'):runtime.run(Path('missing.apk'),Path('out'))
   adb.assert_not_called()
if __name__=='__main__':unittest.main()
