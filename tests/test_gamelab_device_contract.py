"""Offline contract tests for the real Android GameLab accessibility reader.

These do NOT replace running uiautomator and compositor gestures on a device.
They make sure the test cannot silently count synthetic changes as Market wins.
"""
import importlib.util
from pathlib import Path
import unittest
from xml.etree import ElementTree as ET

ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location(
    "gamelab_device_smoke",ROOT/"tools"/"gamelab_device_smoke.py")
smoke=importlib.util.module_from_spec(spec)
spec.loader.exec_module(smoke)


class GameLabDeviceStateContractTest(unittest.TestCase):
    def build(self,content,package="org.localpilot.gamelab"):
        element=ET.Element("hierarchy")
        ET.SubElement(element,"node",attrib={
            "package":package,"content-desc":content,
            "text":"Проба","clickable":"false"})
        return element

    def test_machine_readable_counters(self):
        content=("gamelab-state-v1 synthetic=true game=BUBBLE "
                 "touches=3 changes=2 rejected=1 profile=9")
        self.assertEqual(smoke.game_state(self.build(content)),
                         {"game":"BUBBLE","touches":3,"changes":2,
                          "rejected":1,"profile":9})

    def test_negative_profile_is_explicitly_unsupported(self):
        text=("gamelab-state-v1 synthetic=true game=HEXON "
              "touches=0 changes=0 rejected=0 profile=-1")
        self.assertEqual(smoke.game_state(self.build(text))["profile"],-1)

    def test_rejects_non_laboratory_package(self):
        text=("gamelab-state-v1 synthetic=true game=BUBBLE "
              "touches=1 changes=1 rejected=0 profile=9")
        with self.assertRaises(RuntimeError):
            smoke.game_state(self.build(text,"ru.beru.android"))

    def test_no_real_game_claim(self):
        text=("gamelab-state-v1 synthetic=false game=BUBBLE "
              "touches=1 changes=1 rejected=0 profile=9")
        with self.assertRaises(RuntimeError):
            smoke.game_state(self.build(text))

    def test_missing_change_counter_rejected(self):
        with self.assertRaises(RuntimeError):
            smoke.game_state(self.build(
                "gamelab-state-v1 synthetic=true game=BUBBLE touches=1 rejected=0 profile=9"))

    def test_ambiguous_state_rejected(self):
        root=self.build("gamelab-state-v1 synthetic=true game=BUBBLE "
                        "touches=0 changes=0 rejected=0 profile=9")
        ET.SubElement(root,"node",attrib={
            "package":"org.localpilot.gamelab",
            "content-desc":"gamelab-state-v1 synthetic=true game=BUBBLE "
                           "touches=1 changes=1 rejected=0 profile=9"})
        with self.assertRaises(RuntimeError):
            smoke.game_state(root)


if __name__=="__main__":
    unittest.main()
