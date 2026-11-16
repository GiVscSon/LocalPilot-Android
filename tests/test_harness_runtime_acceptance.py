from pathlib import Path
import sys
import unittest
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
from run_harness_runtime_acceptance import validate

class RuntimeProofLabels(unittest.TestCase):
    def fixture(self):
        return dict(schema='localpilot-application-runtime-contract-v1',result='ANDROID_CONTRACT_PASS',sameEngineAcrossActivityRecreation=True,sameCatalogAcrossScreens=True,missingModelFailsClosed=True,duplicateRequestRejected=True,stopRevokesPendingReply=True,modelLibraryLifecycle=True,modelLibraryBusyFailsClosed=True,syntheticObservationMetadata=True,inputAuthorityConsumed=False,nativeInferencePerformed=False,physicalDevice=False,releaseReady=False)
    def test_valid_contract(self):validate(self.fixture())
    def test_missing_proof(self):
        for key in self.fixture():
            e=self.fixture();e.pop(key)
            with self.subTest(key=key),self.assertRaises(RuntimeError):validate(e)
    def test_invented_native_or_device_proof_rejected(self):
        for key in ('nativeInferencePerformed','physicalDevice','releaseReady','inputAuthorityConsumed'):
            e=self.fixture();e[key]=True
            with self.subTest(key=key),self.assertRaises(RuntimeError):validate(e)
