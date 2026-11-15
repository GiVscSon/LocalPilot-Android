"""Synthetic negative controls for actual GGUF->NES log correlation gate."""
import hashlib
import importlib.util
from pathlib import Path
import unittest

path = Path(__file__).resolve().parents[1] / "tools/verify_g2_trace.py"
spec = importlib.util.spec_from_file_location("g2", path)
g2 = importlib.util.module_from_spec(spec)
spec.loader.exec_module(g2)
SHA = "a" * 64


def record(items):
    body = "|".join(str(v) for v in items)
    return (body + "|" + hashlib.sha256(body.encode()).hexdigest() + "\n").encode()


def inference(frame=64, button="RIGHT", fingerprint=1234567):
    return record(["NES_MODEL_INFERENCE_V1", "model:mnn", SHA,
                   frame, fingerprint, 340, button, 0])


def transition(before=64, after=80, button="RIGHT", h0=1234567, h1=7654321,
               reward="UNKNOWN"):
    return record(["CONSOLE_AI_EXPERIENCE_V2", "HOMEBREW", "model:mnn", SHA,
                   button, before, after, h0, h1, "PIXELS_DIFFER", reward])


class CorrelationTests(unittest.TestCase):
    def test_real_log_contract_requires_exact_evidence_but_no_device_claim(self):
        result = g2.correlate(inference(), transition(), SHA)
        self.assertEqual(1, result["matchedTransitions"])
        self.assertEqual("UNKNOWN", result["trustedGameReward"])
        self.assertEqual("NOT_VERIFIED", result["physicalDevice"])
        self.assertFalse(result["releaseReady"])

    def test_reject_tampered_hash(self):
        with self.assertRaisesRegex(ValueError, "JOURNAL_SCHEMA_OR_SHA_INVALID"):
            g2.correlate(inference().replace(b"RIGHT", b"LEFT", 1), transition(), SHA)

    def test_reject_ack_without_visual_change(self):
        with self.assertRaisesRegex(ValueError, "NO_FRESH_VERIFIED_NES_TRANSITION"):
            g2.correlate(inference(), transition(h1=1234567), SHA)

    def test_reject_wrong_frame_or_stale_gguf(self):
        with self.assertRaisesRegex(ValueError, "NES_ACTION_HAS_NO_EXACT_MODEL_DECISION"):
            g2.correlate(inference(frame=63), transition(), SHA)

    def test_reject_wrong_button(self):
        with self.assertRaisesRegex(ValueError, "NES_ACTION_HAS_NO_EXACT_MODEL_DECISION"):
            g2.correlate(inference(button="LEFT"), transition(), SHA)

    def test_reject_fake_reward(self):
        with self.assertRaisesRegex(ValueError, "NES_PROVENANCE_OR_REWARD_FORGED"):
            g2.correlate(inference(), transition(reward="1000"), SHA)

    def test_reject_reused_one_inference_for_two_actions(self):
        with self.assertRaises(ValueError):
            g2.correlate(inference(), transition() + transition(before=80, after=96), SHA)

    def test_reject_rejected_model_proposal(self):
        with self.assertRaisesRegex(ValueError, "NO_VALID_GGUF_ACTION_PROPOSAL"):
            g2.correlate(inference(button="REJECTED"), transition(), SHA)

    def test_accept_two_independent_verified_decisions(self):
        a = inference(frame=64) + inference(frame=80, fingerprint=7654321)
        b = transition() + transition(before=80, after=96, h0=7654321, h1=55555)
        self.assertEqual(2, g2.correlate(a, b, SHA)["matchedTransitions"])


if __name__ == "__main__":
    unittest.main()
