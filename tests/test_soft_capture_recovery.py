"""Contract tests: recover harmless capture races, never weaken real input gates."""
from pathlib import Path
import re
import unittest

PILOT = Path("app/src/main/java/org/localpilot/android/PilotService.java")
GATE = Path("app/src/main/java/org/localpilot/android/ActionGate.java")
FRESHNESS = Path("app/src/main/java/org/localpilot/android/CaptureFreshness.java")


class SoftCaptureRecovery(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.pilot = PILOT.read_text(encoding="utf-8")
        cls.gate = GATE.read_text(encoding="utf-8")
        cls.fresh = FRESHNESS.read_text(encoding="utf-8")

    def test_stale_arcade_skips_input_not_session(self):
        p = self.pilot
        self.assertRegex(p, r'if\(timestampMs>now\|\|now-timestampMs>ActionGate\.TTL_MS\)')
        self.assertIn('metrics.failure(StageMetrics.Stage.CAPTURE,"ARCADE_FRAME_STALE")', p)
        self.assertNotIn('failStage(StageMetrics.Stage.CAPTURE,"ARCADE_FRAME_STALE"', p)
        self.assertIn('metrics.failure(StageMetrics.Stage.CAPTURE,"JUMPER_FRAME_STALE")', p)
        self.assertNotIn('failStage(StageMetrics.Stage.CAPTURE,"JUMPER_FRAME_STALE"', p)

    def test_inflight_request_does_not_stop_or_overlap(self):
        p = self.pilot
        self.assertIn('if(captureBusy){\n   // A concurrent read request', p)
        self.assertNotIn('failStage(StageMetrics.Stage.CAPTURE,"CAPTURE_INFLIGHT"', p)
        self.assertIn('captureBusy=true;', p)
        self.assertIn('pendingCapture=requestId', p)
        self.assertIn('final long requestId=++captureCounter', p)
        self.assertIn('pendingCapture=++captureCounter', p)
        self.assertIn('},4000);', p)
        self.assertIn('if(!retryCapture(epoch,result,"SCREENSHOT_TIMEOUT"))', p)

    def test_hard_input_authority_remains(self):
        p = self.pilot
        self.assertIn('if(!gate.consume(ticket,', p)
        self.assertIn('!permit.consume(', p)
        self.assertIn('if(Rect.intersects(panelRect,pathRect))', p)
        self.assertIn('if(!permit.allows(gate.epoch(),', p)
        self.assertIn('gate.stop()', p)
        self.assertIn('permit.revoke()', p)
        self.assertIn('String rejection=invalidReason(ticket)', p)
        self.assertIn('CaptureFreshness.valid(', p)
        self.assertIn('public static final long TTL_MS=700', self.gate)
        self.assertIn('if(paused)return "FOCUS_PAUSED"', self.gate)
        self.assertIn('nowUptime-timestamp<=700', self.fresh)

    def test_no_ack_is_substituted_for_visual_confirmation(self):
        p = self.pilot
        self.assertIn('ArcadePolicy.response(', p)
        self.assertIn('JumperFeedback.State', p)
        self.assertIn('SafeJournal.Event.VERIFIED_ARCADE', p)


if __name__ == "__main__":
    unittest.main()
