"""Reject partial/overclaimed evidence before it joins the pipeline report."""
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import unified_pipeline as pipeline


class EventAuditGate(unittest.TestCase):
    def valid(self):
        return dict(schema="localpilot-bubble-event-audit-v1",
                    scope="SYNTHETIC_CONTRACT_ONLY", traces=200, checks=1658,
                    real_transitions_audited=0, false_shadow_goals=0,
                    actions_accepted_after_stop=0, stale_acks_accepted=0,
                    trusted_training_rewards=0, pixel_benchmark=False,
                    physical_device=False, release_ready=False, shadow_goals=19)

    def test_complete_synthetic_contract(self):
        pipeline.validate_event_audit(self.valid())

    def test_rejects_each_missing_field(self):
        for key in self.valid():
            with self.subTest(key=key), self.assertRaises(RuntimeError):
                evidence = self.valid()
                del evidence[key]
                pipeline.validate_event_audit(evidence)

    def test_rejects_overclaims_partial_traces_and_failed_guards(self):
        for key, value in dict(scope="VERIFIED_REAL", traces=199, checks=0,
                               real_transitions_audited=200, false_shadow_goals=1,
                               actions_accepted_after_stop=1, stale_acks_accepted=1,
                               trusted_training_rewards=1, pixel_benchmark=True,
                               physical_device=True, release_ready=True,
                               shadow_goals=0).items():
            with self.subTest(key=key), self.assertRaises(RuntimeError):
                pipeline.validate_event_audit(self.valid() | {key: value})

    def test_boolean_cannot_impersonate_numeric_zero(self):
        with self.assertRaises(RuntimeError):
            pipeline.validate_event_audit(self.valid() | {"false_shadow_goals": False})

    def test_failed_compile_removes_previous_success_evidence(self):
        with tempfile.TemporaryDirectory() as folder:
            out = Path(folder)
            for suffix in ("json", "csv", "md"):
                (out / ("event-audit." + suffix)).write_text("old success")
            with patch.object(pipeline, "execute", side_effect=RuntimeError("compile failed")):
                with self.assertRaises(RuntimeError):
                    pipeline.event_audit(out)
            self.assertFalse(list(out.glob("event-audit.*")))


if __name__ == "__main__":
    unittest.main()
