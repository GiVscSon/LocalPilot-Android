"""A transport-successful or incomplete Android report must fail capture CI."""
import copy
import importlib.util
from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'tools'))
spec = importlib.util.spec_from_file_location('capture_acceptance', Path(__file__).resolve().parents[1] / 'tools/run_capture_acceptance.py')
runner = importlib.util.module_from_spec(spec)
spec.loader.exec_module(runner)


class CaptureEvidenceContract(unittest.TestCase):
    def report(self, api):
        return dict(schema='localpilot-android-window-capture-v1', result='PASS', api=api,
                    physical_device=False, checks=[dict(name=name, result='PASS',
                    route='WINDOW_FULLSCREEN' if api >= 34 else 'DISPLAY') for name in runner.CHECKS])

    def test_all_four_api_routes(self):
        for api in (30, 34, 35, 36):
            runner.validate_report(self.report(api), api)

    def test_failure_missing_duplicate_wrong_route_or_provenance_rejected(self):
        valid = self.report(35)
        mutations = [
            lambda r: r.update(result='FAIL', failure='late callback dispatched input'),
            lambda r: r.update(api=30),
            lambda r: r.update(physical_device=True),
            lambda r: r['checks'].pop(),
            lambda r: r['checks'].append(r['checks'][0]),
            lambda r: r['checks'][0].update(result='NOT_RUN'),
            lambda r: next(c for c in r['checks'] if c['name'] == 'actual_bridge_fullscreen_pixels_and_geometry').update(route='DISPLAY'),
        ]
        for mutate in mutations:
            report = copy.deepcopy(valid)
            mutate(report)
            with self.subTest(report=report), self.assertRaises(RuntimeError):
                runner.validate_report(report, 35)
