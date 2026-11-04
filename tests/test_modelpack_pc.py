import hashlib
import io
import json
import os
import struct
import tempfile
import unittest
import zipfile
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))
import modelpack_pc as pc


def demo_pack():
    n = 8320 + 65 * 3
    policy = struct.pack("<" + "f" * n, *([0.0] * n))
    meta = {
        "format": "localpilot-dqn-policy-v1", "genre": "JUMPER",
        "updates": 2, "weights": n,
        "checkpoint_manifest": {
            "genre": "JUMPER", "observation": "arcade-objects-v1",
            "shape": [64], "network": [64,64,64,3],
            "actions": ["left","right","neutral"],
        },
    }
    entries = {
        "manifest.json": json.dumps({"format": pc.FORMAT, "dqn_exported": 1}).encode(),
        "dqn/jumper/policy.f32le": policy,
        "dqn/jumper/policy.json": json.dumps(meta).encode(),
        "paddle/impact.json": json.dumps({"schema": "paddle-2x4x1-v1",
            "origin": "programmed_physics",
            "weights": [1,0,-1,0,0,1,0,-1,0,0,1,-1,0]}).encode(),
        "weightspace/lane_escape.json": json.dumps({
            "schema":"weightspace-3x4-v1", "genre":"LANE_ESCAPE",
            "origin":"programmed", "weights":[[1,0,0,0],[0,1,0,.12],[0,0,1,0]]
        }).encode(),
    }
    sums = "".join(hashlib.sha256(entries[k]).hexdigest()+"  "+k+"\n"
                   for k in sorted(entries))
    handle = io.BytesIO()
    with zipfile.ZipFile(handle,"w") as z:
        for name, value in entries.items():
            z.writestr(name,value)
        z.writestr("SHA256SUMS",sums)
    return handle.getvalue()


class ModelPackPCTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.path = Path(self.directory.name) / "saved-android-models.zip"
        self.path.write_bytes(demo_pack())

    def tearDown(self):
        self.directory.cleanup()

    def test_inference_from_exact_portable_weights(self):
        metadata, entries = pc.load_pack(self.path)
        self.assertEqual(metadata["format"], pc.FORMAT)
        predicted = pc.dqn_predict(entries, "JUMPER", [0.0] * 64)
        self.assertEqual(predicted["index"], 0)
        self.assertEqual(predicted["action"], "left")
        self.assertEqual(predicted["q_values"], [0.0, 0.0, 0.0])
        self.assertEqual(predicted["training_updates"], 2)
        self.assertEqual(pc.matrix_predict(entries, "lane_escape", [.8,.1,.2,0])["action"],"LEFT")
        paddle = pc.paddle_predict(entries, "impact", [.4,.5,.0,.6,.3])
        self.assertTrue(-1 <= paddle["steer"] <= 1)

    def test_corrupt_data_is_rejected(self):
        raw = self.path.read_bytes()
        with zipfile.ZipFile(io.BytesIO(raw)) as z:
            entries = {name:z.read(name) for name in z.namelist()}
        entries["dqn/jumper/policy.f32le"] = b"tampered"
        with zipfile.ZipFile(self.path,"w") as z:
            for name, val in entries.items():
                z.writestr(name,val)
        with self.assertRaisesRegex(ValueError, "checksum mismatch"):
            pc.load_pack(self.path)

    def test_missing_and_invalid_observations_are_rejected(self):
        _, e = pc.load_pack(self.path)
        for obs in ([[0.0]], [0.0]*63+[float("nan")], [0.0]*63+[2.0]):
            with self.assertRaises(ValueError):
                pc.dqn_predict(e, "JUMPER", obs)
        with self.assertRaises(ValueError):
            pc.dqn_predict(e, "PLATFORMER", [0]*64)

    def test_duplicate_archived_name_rejected(self):
        with zipfile.ZipFile(self.path,"a") as z:
            import warnings
            with warnings.catch_warnings():
                warnings.simplefilter("ignore",UserWarning)
                z.writestr("manifest.json", b"{}")
        with self.assertRaisesRegex(ValueError, "duplicate"):
            pc.load_pack(self.path)


if __name__=="__main__":
    unittest.main()
