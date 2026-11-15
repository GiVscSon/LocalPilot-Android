import hashlib
from pathlib import Path
import sys
import unittest

sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
from harness_student_candidate_evaluation import binding


class CandidateBindingTests(unittest.TestCase):
    def setUp(self):
        self.active=b'synthetic-active';self.candidate=b'synthetic-candidate';self.replay=b'synthetic-replay'
        digest=lambda b:hashlib.sha256(b).hexdigest()
        self.filename=digest(self.replay)+'.lpr'
        self.manifest=dict(schema='localpilot-student-candidate-v1',task='builtin-homebrew-right-progress-v1',
            observationSchema='obs64-homebrew-x-v1',policyBeforeSha256=digest(self.active),
            policyAfterSha256=digest(self.candidate),verifiedRolloutSteps=16,changedParameters=1,adamSteps=1,
            romSha256='a'*64,evaluation='NOT_RUN',promotion='BLOCKED_UNTIL_INDEPENDENT_EVALUATION',
            trainingReplaySha256=digest(self.replay),trainingEpoch='123')

    def call(self):return binding(self.manifest,self.active,self.candidate,self.replay,self.filename)

    def test_exact_binding(self):self.assertEqual(self.call()[2],self.filename[:-4])

    def test_missing_replay_binding(self):
        del self.manifest['trainingReplaySha256']
        with self.assertRaises(RuntimeError):self.call()

    def test_other_replay(self):
        self.replay=b'changed'
        with self.assertRaises(RuntimeError):self.call()

    def test_other_filename(self):
        self.filename='0'*64+'.lpr'
        with self.assertRaises(RuntimeError):self.call()

    def test_unknown_epoch(self):
        self.manifest['trainingEpoch']='UNKNOWN'
        with self.assertRaises(RuntimeError):self.call()

    def test_changed_active_weights(self):
        self.active=b'changed'
        with self.assertRaises(RuntimeError):self.call()
