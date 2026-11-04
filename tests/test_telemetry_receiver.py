"""Socket-level tests of the optional LocalPilot diagnostic ingest endpoint."""
import importlib.util
import json
from pathlib import Path
import threading
import unittest
from urllib.error import HTTPError
from urllib.request import Request, urlopen

source = Path(__file__).resolve().parents[1] / "tools" / "localpilot_telemetry_receiver.py"
spec = importlib.util.spec_from_file_location("localpilot_telemetry_receiver", source)
receiver = importlib.util.module_from_spec(spec)
spec.loader.exec_module(receiver)

TOKEN = "t" * 48
FRAME = {
    "schema": "localpilot-telemetry-v1",
    "uptime_ms": 1000, "profile": 9, "game_active": True,
    "strict_vision": True, "training": False, "window_armed": False,
    "roi_selected": True, "frames": 19, "reads": 19, "decisions": 17,
    "gestures_sent": 0, "android_ack": 0, "grid_verified": 0,
    "arcade_verified": 0, "bubble_verified": 0, "capture_busy": False,
    "grid_status": "WAIT_STABLE", "bubble_decision": "VISION_REJECTED",
    "last_stop_code": "STOP",
}

class TelemetryTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.server = receiver.Receiver(("127.0.0.1", 0), TOKEN)
        cls.thread = threading.Thread(target=cls.server.serve_forever, daemon=True)
        cls.thread.start()
        cls.url = f"http://127.0.0.1:{cls.server.server_port}/v1/telemetry"

    @classmethod
    def tearDownClass(cls):
        cls.server.shutdown()
        cls.server.server_close()
        cls.thread.join(timeout=2)

    def request(self, path, body=None, token=TOKEN, method=None):
        data=None if body is None else json.dumps(body).encode()
        request=Request(self.url+path, data=data, method=method,
                        headers={"Authorization": "Bearer "+token,
                                 "Content-Type": "application/json"})
        try:
            with urlopen(request, timeout=2) as resp:
                return resp.status, resp.read()
        except HTTPError as err:
            return err.code, err.read()

    def test_bearer_and_roundtrip(self):
        status, body=self.request("",FRAME)
        self.assertEqual(status,204)
        self.assertEqual(body,b"")
        status, body=self.request("/latest",method="GET")
        self.assertEqual(status,200)
        last=json.loads(body)
        self.assertEqual(last["latest"],FRAME)
        self.assertGreater(last["received"],0)
        self.assertEqual(self.request("",FRAME,token="bad")[0],401)
        self.assertEqual(self.request("/latest",token="bad",method="GET")[0],401)

    def test_privacy_and_input_rejections(self):
        for extra in ({"screenshot":"data:image/png;base64..."},{"target":"ru.beru.android"},
                      {"roi_x":120},{"question":"hidden user prompt"}):
            sample={**FRAME,**extra}
            self.assertEqual(self.request("",sample)[0],400)
        self.assertEqual(self.request("",{**FRAME,"frames":-1})[0],400)
        self.assertEqual(self.request("",{**FRAME,"frames":True})[0],400)
        self.assertEqual(self.request("",{**FRAME,"bubble_decision":"\nsecret"})[0],400)
        self.assertEqual(self.request("",{**FRAME,"schema":"unknown"})[0],400)
        self.assertEqual(self.request("?token=leak",FRAME)[0],404)
        self.assertEqual(self.request("/latest?token=leak",method="GET")[0],404)

    def test_empty_and_oversize(self):
        self.assertEqual(self.request("",None,method="POST")[0],413)
        huge={**FRAME,"padding":"X"*10000}
        self.assertEqual(self.request("",huge)[0],413)

if __name__=="__main__":
    unittest.main()
