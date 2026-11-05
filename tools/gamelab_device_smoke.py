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

def tap_label(text):
    for _ in range(5):
        tree=screen_dump()
        buttons=[n for n in tree.iter() if n.get("package")==PACKAGE
          and n.get("text")==text and n.get("clickable")=="true"]
        if buttons:
            x1,y1,x2,y2=bounds(buttons[0])
            adb("shell","input","tap",str((x1+x2)//2),str((y1+y2)//2),timeout=15)
            return
        time.sleep(.5)
    raise RuntimeError("NES GameLab button unavailable: "+text)


def nes_metrics(tree):
    from re import search
    current=state(tree)
    matches=search(r"Кадров\\s*(\\d+)\\s*·\\s*экран CRC=([0-9a-fA-F]+)",current)
    if not matches:return None
    return int(matches.group(1)),matches.group(2).lower()


def verify_nes_android():
    # Navigate via actual Android activities. No shell start of a private
    # emulator Activity; no fake JS frames and no direct WebView injections.
    tap_label("Ретро · NES / Денди / DOS")
    tap_label("Играть в NES / Денди или проверить DOS-файл")
    for attempt in range(4):
        time.sleep(1.2)
        tap_label("Запустить тестовую NES-игру")
        for _ in range(8):
            current=nes_metrics(screen_dump())
            if current and current[0]>=60:break
            time.sleep(.35)
        if current and current[0]>=60:break
    if not current or current[0]<60:
        raise RuntimeError("NES did not execute 60 actual emulated video frames in Android WebView")
    first_frames,first_hash=current
    before=screenshot(OUT/"gamelab-nes-before.png")
    # Hold the real Android A button across several 6502 frame polls.
    tree=screen_dump()
    buttons=[n for n in tree.iter() if n.get("package")==PACKAGE
      and n.get("text")=="A" and n.get("clickable")=="true"]
    if not buttons:raise RuntimeError("NES A touchscreen button not found")
    x1,y1,x2,y2=bounds(buttons[0])
    x,y=(x1+x2)//2,(y1+y2)//2
    adb("shell","input","touchscreen","swipe",str(x),str(y),str(x),str(y),"280",timeout=15)
    final=None
    for _ in range(10):
        final=nes_metrics(screen_dump())
        if final and final[0]>first_frames and final[1]!=first_hash:break
        time.sleep(.5)
    if not final or final[0]<=first_frames or final[1]==first_hash:
        raise RuntimeError("NES PPU pixels did not respond to a real held A press: "+str(final))
    after=screenshot(OUT/"gamelab-nes-after.png")
    if before==after:raise RuntimeError("Android compositor did not show changed NES screen")
    proof={"kind":"separate-nes-android-emulation-v1","origin":"RETRO_EMULATED",
      "market_acceptance":False,"pilot_dispatched":False,"rom":"original_6502_nrom_homebrew",
      "cpu_ppu_frames_before":first_frames,"cpu_ppu_frames_after":final[0],
      "ppu_hash_before":first_hash,"ppu_hash_after":final[1],
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
    verify_nes_android()
    # Return to the Pilot launcher; main instrumentation will launch its own UI.
    adb("shell","input","keyevent","KEYCODE_HOME",timeout=15)
    return report
