"""Negative source-level evidence tests for sustained NES session (not an emulator)."""
import hashlib
import importlib.util
from pathlib import Path
import struct
import sys
import unittest
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'tools'))

path = Path(__file__).resolve().parents[1] / "tools/nes_sustained_256_acceptance.py"
spec = importlib.util.spec_from_file_location("sustained", path)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)
POLICY = "a" * 64
ROM = "b" * 64


def make_journal(count=32, bad_action=None):
    lines = []
    prev = "0"
    for i in range(count):
        frm = 20 + i * 16
        reward = struct.pack(">f", 1 / 16).hex()
        fields = [
            module.GOAL_SCHEMA, "1", ROM, "student-homebrew-v1", POLICY,
            "RIGHT", str(frm), str(frm + 16), "100", "101", reward,
            "builtin-homebrew-right-progress-v1", "PPO_BLOCKED", prev
        ]
        if bad_action is not None and i == bad_action:
            fields[5] = "LEFT"
        body = "|".join(fields)
        digest = hashlib.sha256(body.encode("utf-8")).hexdigest()
        lines.append(body + "|" + digest)
        prev = digest
    return ("\n".join(lines) + "\n").encode()


class SustainedProofTest(unittest.TestCase):
    def test_32_chain_sample_proves_structure_only(self):
        result = module.verify_sustained_goals(make_journal(), POLICY, ROM, expected_steps=32)
        self.assertEqual(32, result["steps"])
        self.assertEqual(2, result["batches"])
        self.assertEqual("VERIFIED_ROM_RAM", result["rewardSource"])

    def test_reject_truncated_history(self):
        with self.assertRaisesRegex(ValueError, "WRONG_SUSTAINED_ACTION_COUNT"):
            module.verify_sustained_goals(make_journal(16), POLICY, ROM, 32)

    def test_reject_modified_hash(self):
        damaged = make_journal().replace(b"RIGHT", b"LEFT", 1)
        with self.assertRaisesRegex(ValueError, "SUSTAINED_HASH_CHAIN_BROKEN"):
            module.verify_sustained_goals(damaged, POLICY, ROM, 32)

    def test_reject_self_consistent_but_wrong_action(self):
        with self.assertRaisesRegex(ValueError, "SUSTAINED_RAM_ACTION_CONTRADICTION"):
            module.verify_sustained_goals(make_journal(bad_action=6), POLICY, ROM, 32)

    def test_reject_other_rom_identity(self):
        with self.assertRaisesRegex(ValueError, "UNTRUSTED_SUSTAINED_RECORD"):
            module.verify_sustained_goals(make_journal(), POLICY, "c" * 64, 32)

    def test_bad_policy_identity(self):
        with self.assertRaisesRegex(ValueError, "INVALID_POLICY_OR_ROM_SHA"):
            module.verify_sustained_goals(make_journal(), "partial", ROM, 32)


if __name__ == "__main__":
    unittest.main()
