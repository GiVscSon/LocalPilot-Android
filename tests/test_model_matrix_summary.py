import json
from pathlib import Path
import sys
import tempfile
import unittest
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
from model_lab_catalog import presets
from summarize_model_matrix import summarize,write_reports

class ModelMatrixSummaryTest(unittest.TestCase):
    def test_missing_models_are_never_pass(self):
        with tempfile.TemporaryDirectory() as td:
            report=summarize(td,'source')
            self.assertFalse(report['complete'])
            self.assertTrue(all(r['status']=='MISSING' for r in report['rows']))
            write_reports(report,Path(td)/'summary')
            self.assertTrue((Path(td)/'summary.csv').exists())

    def test_failed_native_run_and_wrong_source_do_not_become_pass(self):
        key,model=next(iter(presets().items()))
        with tempfile.TemporaryDirectory() as td:
            path=Path(td)/'report.json'
            path.write_text(json.dumps(dict(result='FAIL',error='No game progress',attemptIdentity=dict(
                modelId=key,modelSha256=model['sha256'],apkSha256='b'*64,sourceSha='source'))))
            result=summarize(td,'source')
            self.assertEqual(result['rows'][0]['status'],'FAIL')
            self.assertEqual(result['rows'][0]['inference'],'NOT_CONFIRMED')
            self.assertTrue(summarize(td,'other-source')['problems'])
            self.assertFalse(result['releaseReady'])

if __name__=='__main__':unittest.main()
