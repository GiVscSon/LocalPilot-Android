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

def adb(*args,timeout=35,binary=False):
    command=["adb",*args]
    proc=subprocess.run(command,stdin=subprocess.DEVNULL,stdout=subprocess.PIPE,
                        stderr=subprocess.PIPE,timeout=timeout,
                        text=not binary)
    if proc.returncode:
        raise RuntimeError("ADB command failed: "+" ".join(command[:7])+
                           " "+str(proc.stderr)[-500:])
    return proc.stdout

def screen_dump():
    adb("shell","uiautomator","dump","/sdcard/window-gamelab.xml",timeout=32)
    raw=adb("exec-out","cat","/sdcard/window-gamelab.xml",timeout=15)
    start=raw.find("<?xml")
    if start<0:raise RuntimeError("GameLab accessibility XML unavailable")
    return ET.fromstring(raw[start:])

def bounds(node):
    encoded=node.get("bounds","")
    match=re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]",encoded)
    if not match:raise RuntimeError("GameLab view has no real touchscreen bounds")
    return list(map(int,match.groups()))

def wait_scene():
    for _ in range(8):
        tree=screen_dump()
        surfaces=[n for n in tree.iter()
             if "GameLab synthetic game display" in n.get("content-desc","")
             and n.get("package","")==PACKAGE]
        if surfaces:return tree,bounds(surfaces[0])
        time.sleep(.5)
    raise RuntimeError("Separate GameLab Canvas view did not appear")

def state(tree):
    texts=[n.get("text","") for n in tree.iter() if n.get("package")==PACKAGE]
    return " ".join(texts)

def number(text,part):
    match=re.search(re.escape(part)+r"\s*(\d+)",text,re.I)
    if not match:raise RuntimeError("GameLab state missing: "+part+" in "+text[-500:])
    return int(match.group(1))

def screenshot(path):
    raw=adb("exec-out","screencap","-p",timeout=20,binary=True)
    if not raw.startswith(b"\x89PNG\r\n\x1a\n"):
        raise RuntimeError("Android compositor did not return PNG screenshot")
    path.write_bytes(raw)
    return hashlib.sha256(raw).hexdigest()

def run():
    if not APK.exists():raise RuntimeError("Build standalone GameLab before device tests")
    OUT.mkdir(parents=True,exist_ok=True)
    adb("install","-r",str(APK),timeout=85)
    adb("shell","am","start","-W","-n",
        PACKAGE+"/.TestDeviceActivity",timeout=40)
    tree,rect=wait_scene()
    original=state(tree)
    if number(original,"касаний")!=0 or number(original,"изменений стенда")!=0:
        raise RuntimeError("Non-reset GameLab state")
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
        tree=screen_dump();updated=state(tree)
        if number(updated,"касаний")>=1:break
        time.sleep(.25)
    if number(updated,"касаний")!=1 or number(updated,"изменений стенда")<1:
        raise RuntimeError("Android touch did not change real GameLab state: "+updated[-400:])
    after=screenshot(OUT/"gamelab-after.png")
    if before==after:raise RuntimeError("Android screenshot did not render gesture outcome")
    report={"kind":"external-game-lab-touch-smoke-v1","target_package":PACKAGE,
       "game":"BUBBLE","synthetic":True,"market_acceptance":False,
       "pilot_dispatched":False,"touches":1,"screen_modified":True,
       "before_sha256":before,"after_sha256":after,"canvas_bounds":rect,
       "touch_start":[startX,startY],"touch_end":[endX,endY],"result":"PASS"}
    (OUT/"gamelab-touch-evidence.json").write_text(json.dumps(report,indent=2),encoding="utf-8")
    print("GAMELAB_REAL_ANDROID_TOUCH_PASS "+json.dumps(report),flush=True)
    # Return to the Pilot launcher; main instrumentation will launch its own UI.
    adb("shell","input","keyevent","KEYCODE_HOME",timeout=15)
    return report
