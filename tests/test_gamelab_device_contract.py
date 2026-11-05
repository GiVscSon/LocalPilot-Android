"""Offline contract tests for the real Android GameLab accessibility reader.

These do NOT replace running uiautomator and compositor gestures on a device.
They make sure the test cannot silently count synthetic changes as Market wins.
"""
import importlib.util
from pathlib import Path
import unittest
from unittest.mock import patch
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

    def test_live_probe_row_parser(self):
        columns=["source=gamelab-only","scene=nes","game=NES",
          "touches=0","changes=0","rejected=0","profile=-1",
          "frames=75","pixel_hash=-1431655766",
          "canvas_left=0","canvas_top=0","canvas_right=0","canvas_bottom=0",
          "primary_left=12","primary_top=110","primary_right=300","primary_bottom=170",
          "secondary_left=600","secondary_top=1600","secondary_right=720","secondary_bottom=1710",
          "tertiary_left=470","tertiary_top=1600","tertiary_right=590","tertiary_bottom=1710",
          "updated_elapsed_ms=191200"]
        with patch.object(smoke,"adb",return_value="Row: 0 "+", ".join(columns)):
            data=smoke.probe()
        self.assertEqual((data["scene"],data["frames"],data["game"]),("nes",75,"NES"))
        self.assertEqual(smoke.probe_bounds(data,"secondary"),[600,1600,720,1710])
        self.assertEqual(smoke.probe_bounds(data,"tertiary"),[470,1600,590,1710])
        self.assertEqual(data["pixel_hash"] & 0xffffffff,0xaaaaaaaa)

    def test_incomplete_probe_is_rejected(self):
        with patch.object(smoke,"adb",return_value="Row: 0 source=gamelab-only, scene=nes"):
            with self.assertRaises(RuntimeError):
                smoke.probe()

    def test_probe_button_bounds_cannot_send_input(self):
        data={"scene":"nes","primary_left":5,"primary_top":60,
          "primary_right":150,"primary_bottom":115,
          "secondary_left":550,"secondary_top":1350,
          "secondary_right":650,"secondary_bottom":1470}
        previous=smoke._probe_only
        try:
            smoke._probe_only=True
            with patch.object(smoke,"probe",return_value=data),patch.object(smoke,"adb",
                side_effect=AssertionError("reading bounds must never dispatch input")):
                self.assertEqual(smoke.button_bounds("A"),[550,1350,650,1470])
        finally:
            smoke._probe_only=previous

    def test_independent_nes_b_button_bounds_read_only(self):
        data={"scene":"nes","tertiary_left":470,"tertiary_top":1600,
              "tertiary_right":590,"tertiary_bottom":1710}
        previous=smoke._probe_only
        try:
            smoke._probe_only=True
            with patch.object(smoke,"probe",return_value=data),patch.object(smoke,"adb",
                side_effect=AssertionError("reading B bounds must not inject input")):
                self.assertEqual(smoke.button_bounds("B"),[470,1600,590,1710])
        finally:
            smoke._probe_only=previous

    def test_missing_b_button_bounds_fail_closed(self):
        data={"scene":"nes","tertiary_left":0,"tertiary_top":0,
              "tertiary_right":0,"tertiary_bottom":0}
        previous=smoke._probe_only
        try:
            smoke._probe_only=True
            with patch.object(smoke,"probe",return_value=data),patch.object(smoke,"adb",
                side_effect=AssertionError("must not attempt fallback gesture")):
                with self.assertRaises(RuntimeError):
                    smoke.button_bounds("B")
        finally:
            smoke._probe_only=previous

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
