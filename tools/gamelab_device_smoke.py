"""GameLab smoke on an ACTUAL Android emulator compositor.

Installs the separate package, reads its accessibility view bounds, injects a
real Android touchscreen gesture and verifies BOTH:
  1) independent GameLab state reported in an Android TextView;
  2) changed rendered screenshot pixels.
This is not a test that LocalPilot itself dispatched the gesture and must never
be counted as Market-game acceptance or as neural training reward.
"""
import hashlib
import json
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET

OUT=Path("device-evidence")
PACKAGE="org.localpilot.gamelab"
APK=Path("game-lab/build/outputs/apk/debug/game-lab-debug.apk")
PROBE_URI="content://org.localpilot.gamelab.evidence/state"
_probe_only=False

def adb(*args,timeout=35,binary=False):
    command=["adb",*args]
    proc=subprocess.run(command,stdin=subprocess.DEVNULL,stdout=subprocess.PIPE,
                        stderr=subprocess.PIPE,timeout=timeout,
                        text=not binary)
    if proc.returncode:
        raise RuntimeError("ADB command failed: "+" ".join(command[:7])+
                           " "+str(proc.stderr)[-500:])
    return proc.stdout

def probe():
    """Read only current GameLab-owned UI metadata, never send actions."""
    result=adb("shell","content","query","--uri",PROBE_URI,timeout=15)
    if "Row: 0 " not in result:
        raise RuntimeError("Read-only GameLab evidence provider has no active scene: "+result[-250:])
    row=result.split("Row: 0 ",1)[1].splitlines()[0]
    values=dict(re.findall(r"([a-z_]+)=([^,]+)",row))
    if values.get("source")!="gamelab-only":
        raise RuntimeError("GameLab probe identity mismatch")
    for field in ("scene","game"):
        if field not in values:raise RuntimeError("GameLab probe missing "+field)
    for field in ("touches","changes","rejected","profile","frames","pixel_hash",
      "canvas_left","canvas_top","canvas_right","canvas_bottom",
      "primary_left","primary_top","primary_right","primary_bottom",
      "secondary_left","secondary_top","secondary_right","secondary_bottom",
      "tertiary_left","tertiary_top","tertiary_right","tertiary_bottom",
      "updated_elapsed_ms"):
        if field not in values:raise RuntimeError("GameLab probe missing "+field)
        values[field]=int(values[field])
    return values


def screen_dump():
    """Bounded synchronization with a *real* UI hierarchy on slow API30 devices.

    Older Android may return 'null root node' directly after the previous
    instrumentation's UiAutomation disconnects. Never reuse stale XML and never
    claim success from activity metadata alone.
    """
    failures=[]
    for attempt in range(6):
        destination="/sdcard/window-gamelab.xml"
        try:
            adb("shell","rm","-f",destination,timeout=8)
            arguments=["shell","uiautomator","dump"]
            if attempt%2:
                arguments.append("--compressed")
            arguments.append(destination)
            output=adb(*arguments,timeout=25)
            raw=adb("exec-out","cat",destination,timeout=12)
            begin=raw.find("<?xml")
            if begin>=0:
                xml=ET.fromstring(raw[begin:])
                if any(node.get("package")==PACKAGE for node in xml.iter()):
                    return xml
                failures.append("no GameLab nodes in returned UI hierarchy")
            else:
                failures.append("no XML: "+output.strip()[-140:])
        except (RuntimeError, ET.ParseError, subprocess.TimeoutExpired) as error:
            failures.append(type(error).__name__+": "+str(error)[-180:])
        time.sleep(.65+attempt*.2)
    raise RuntimeError("GameLab accessibility XML unavailable after 6 real dumps: "+
        " | ".join(failures[-4:]))

def bounds(node):
    encoded=node.get("bounds","")
    match=re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]",encoded)
    if not match:raise RuntimeError("GameLab view has no real touchscreen bounds")
    return list(map(int,match.groups()))

def scene_tree():
    """Prefer Android Accessibility; fall back to a live READ-ONLY shell probe.

    Both paths are consumed by an independent OS touch + PNG screenshot witness.
    No fallback can perform input, influence scene state, or claim a Market win.
    """
    global _probe_only
    if _probe_only:return None
    try:return screen_dump()
    except (RuntimeError, ET.ParseError):
        probe()  # Do not silently pass if the app is not live in foreground.
        _probe_only=True
        return None


def probe_bounds(data,field):
    rect=[data[field+"_left"],data[field+"_top"],
          data[field+"_right"],data[field+"_bottom"]]
    if rect[2]-rect[0]<10 or rect[3]-rect[1]<10:
        raise RuntimeError("GameLab "+field+" has no visible touchscreen bounds")
    return rect


def wait_scene():
    errors=[]
    for _ in range(8):
        try:
            tree=scene_tree()
            if tree is not None:
                surfaces=[n for n in tree.iter()
                     if "GameLab synthetic game display" in n.get("content-desc","")
                     and n.get("package","")==PACKAGE]
                if len(surfaces)==1:return tree,bounds(surfaces[0])
            data=probe()
            if data["scene"]=="gamelab":
                return None,probe_bounds(data,"canvas")
            errors.append("active scene="+data["scene"])
        except (RuntimeError, ET.ParseError) as ex:
            errors.append(str(ex)[-150:])
        time.sleep(.5)
    raise RuntimeError("Separate GameLab Canvas never became visible: "+" | ".join(errors[-3:]))


def state(tree):
    if tree is None:return ""
    texts=[n.get("text","") for n in tree.iter() if n.get("package")==PACKAGE]
    return " ".join(texts)

def game_state(tree):
    """Read a real UI state: Android Accessibility or shell-only app probe.

    In both cases only a subsequent Android touch and compositor pixel witness
    can confirm a synthetic GameLab transition.
    """
    if tree is None:
        values=probe()
        if values["scene"]!="gamelab":
            raise RuntimeError("Expected live GameLab scene")
        return dict(game=values["game"],touches=values["touches"],
                    changes=values["changes"],rejected=values["rejected"],
                    profile=values["profile"])
    nodes=[n for n in tree.iter() if n.get("package")==PACKAGE
           and n.get("content-desc","").startswith("gamelab-state-v1 ")]
    if len(nodes)!=1:
        raise RuntimeError("Exactly one real GameLab state node required; found "+
                           str(len(nodes))+" UI="+state(tree)[-300:])
    match=re.fullmatch(
        r"gamelab-state-v1 synthetic=true game=([A-Z0-9_]+) "
        r"touches=(\d+) changes=(\d+) rejected=(\d+) profile=(-?\d+)",
        nodes[0].get("content-desc",""))
    if not match:raise RuntimeError("Invalid GameLab evidence schema")
    return dict(game=match.group(1),touches=int(match.group(2)),
                changes=int(match.group(3)),rejected=int(match.group(4)),
                profile=int(match.group(5)))

def screenshot(path):
    raw=adb("exec-out","screencap","-p",timeout=20,binary=True)
    if not raw.startswith(b"\x89PNG\r\n\x1a\n"):
        raise RuntimeError("Android compositor did not return PNG screenshot")
    path.write_bytes(raw)
    return hashlib.sha256(raw).hexdigest()

def button_bounds(text):
    """Return visible Android button bounds, using only live view geometry.

    API30 does not always expose UIAutomator XML after instrumentation; the
    separate app's read-only provider may supply bounds, but never clicks.
    """
    global _probe_only
    wanted=" ".join(text.split()).casefold()
    last=[]
    if not _probe_only:
        try:
            tree=screen_dump()
            clickable=[node for node in tree.iter() if
                node.get("package")==PACKAGE and node.get("clickable")=="true"]
            last=[node.get("text","") for node in clickable]
            matching=[node for node in clickable
                if " ".join(node.get("text","").split()).casefold()==wanted]
            if len(matching)==1:return bounds(matching[0])
            if len(matching)>1:raise RuntimeError("Ambiguous NES control: "+text)
        except (RuntimeError, ET.ParseError):
            _probe_only=True
    mapping={
      "ретро · nes / денди / dos":("gamelab","primary"),
      "играть в nes / денди или проверить dos-файл":("retro","primary"),
      "запустить тестовую nes-игру":("nes","primary"),
      "a":("nes","secondary"),
      "b":("nes","tertiary")
    }
    expected=mapping.get(wanted)
    if expected is None:raise RuntimeError("Unrecognized device smoke control: "+text)
    for _ in range(8):
        data=probe()
        if data["scene"]==expected[0]:
            return probe_bounds(data,expected[1])
        time.sleep(.35)
    raise RuntimeError("NES button unavailable: "+text+
       " expected scene="+expected[0]+" actual="+data["scene"]+
       " tree_labels="+repr(last[-8:]))


def tap_label(text):
    x1,y1,x2,y2=button_bounds(text)
    adb("shell","input","tap",str((x1+x2)//2),str((y1+y2)//2),timeout=15)


def nes_metrics(tree):
    if tree is not None:
        match=re.search(r"Кадров\s*(\d+)\s*·\s*экран CRC=([0-9a-fA-F]+)",state(tree))
        if match:return int(match.group(1)),match.group(2).lower()
    data=probe()
    if data["scene"]!="nes":return None
    if data["frames"]<=0:return None
    return data["frames"],format(data["pixel_hash"] & 0xffffffff,"x")


def verify_nes_android():
    # Navigate via actual Android activities. No shell start of a private
    # emulator Activity; no fake JS frames and no direct WebView injections.
    tap_label("Ретро · NES / Денди / DOS")
    tap_label("Играть в NES / Денди или проверить DOS-файл")
    for attempt in range(4):
        time.sleep(1.2)
        tap_label("Запустить тестовую NES-игру")
        for _ in range(8):
            current=nes_metrics(scene_tree())
            if current and current[0]>=60:break
            time.sleep(.35)
        if current and current[0]>=60:break
    if not current or current[0]<60:
        raise RuntimeError("NES did not execute 60 actual emulated video frames in Android WebView")
    first_frames,first_hash=current
    before=screenshot(OUT/"gamelab-nes-before.png")
    # The ROM latches the last A/B palette, so an already-red scene can
    # legitimately ignore a repeated A. Exercise two distinct real OS touches.
    final=None
    changed_by=None
    observations=[]
    for key in ("B","A"):
        x1,y1,x2,y2=button_bounds(key)
        x,y=(x1+x2)//2,(y1+y2)//2
        adb("shell","input","touchscreen","swipe",str(x),str(y),str(x),str(y),"450",timeout=15)
        for _ in range(10):
            final=nes_metrics(scene_tree())
            if final and final[0]>first_frames and final[1]!=first_hash:
                changed_by=key
                break
            time.sleep(.5)
        observations.append({"key":key,"frames":final[0] if final else None,
                             "hash":final[1] if final else None})
        if changed_by:break
    if not changed_by:
        raise RuntimeError("NES PPU did not respond to real held B/A touches: "
                           +repr(observations))
    after=screenshot(OUT/"gamelab-nes-after.png")
    if before==after:raise RuntimeError("Android compositor did not show changed NES screen")
    proof={"kind":"separate-nes-android-emulation-v1","origin":"RETRO_EMULATED",
      "market_acceptance":False,"pilot_dispatched":False,"rom":"original_6502_nrom_homebrew",
      "cpu_ppu_frames_before":first_frames,"cpu_ppu_frames_after":final[0],
      "ppu_hash_before":first_hash,"ppu_hash_after":final[1],
       "controller_button":changed_by,"controller_observations":observations,
      "android_pixel_change":True,"result":"PASS"}
    (OUT/"gamelab-nes-evidence.json").write_text(json.dumps(proof,indent=2),encoding="utf-8")
    print("GAMELAB_NES_ACTUAL_ANDROID_PASS "+json.dumps(proof),flush=True)
    return proof


def run():
    if not APK.exists():raise RuntimeError("Build standalone GameLab before device tests")
    OUT.mkdir(parents=True,exist_ok=True)
    adb("install","-r",str(APK),timeout=85)
    adb("shell","am","start","-W","-n",
        PACKAGE+"/.TestDeviceActivity",timeout=40)
    # Accessibility view trees may lag behind ActivityManager resume on API30.
    time.sleep(1.0)
    tree,rect=wait_scene()
    original=game_state(tree)
    if original["game"]!="BUBBLE" or original["touches"]!=0 or original["changes"]!=0:
        raise RuntimeError("Non-reset or wrong initial GameLab state: "+str(original))
    x1,y1,x2,y2=rect
    if x2-x1<140 or y2-y1<180:
        raise RuntimeError("GameLab target area too small for real gameplay")
    # The default test is Bubble, whose real MotionEvent adds a colored ball.
    # Coordinates are normalized to the Canvas, not the complete display.
    startX=int(x1+(x2-x1)*.50)
    startY=int(y1+(y2-y1)*.75)
    endX=int(x1+(x2-x1)*.70)
    endY=int(y1+(y2-y1)*.70)
    before=screenshot(OUT/"gamelab-before.png")
    adb("shell","input","touchscreen","swipe",
        str(startX),str(startY),str(endX),str(endY),"160",timeout=15)
    updated=None
    for _ in range(8):
        tree=scene_tree();updated=game_state(tree)
        if updated["touches"]>=1:break
        time.sleep(.25)
    if updated is None or updated["game"]!="BUBBLE" or updated["touches"]!=1 or updated["changes"]<1:
        raise RuntimeError("Android touch did not change actual GameLab state: "+str(updated))
    after=screenshot(OUT/"gamelab-after.png")
    if before==after:raise RuntimeError("Android screenshot did not render gesture outcome")
    report={"kind":"external-game-lab-touch-smoke-v1","target_package":PACKAGE,
       "game":"BUBBLE","synthetic":True,"market_acceptance":False,
       "pilot_dispatched":False,"touches":updated["touches"],"changes":updated["changes"],"screen_modified":True,
       "before_sha256":before,"after_sha256":after,"canvas_bounds":rect,
       "touch_start":[startX,startY],"touch_end":[endX,endY],"result":"PASS"}
    (OUT/"gamelab-touch-evidence.json").write_text(json.dumps(report,indent=2),encoding="utf-8")
    print("GAMELAB_REAL_ANDROID_TOUCH_PASS "+json.dumps(report),flush=True)
    verify_nes_android()
    # Return to the Pilot launcher; main instrumentation will launch its own UI.
    adb("shell","input","keyevent","KEYCODE_HOME",timeout=15)
    return report
