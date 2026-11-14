"""Run actual WebView/CPU-policy instrumentation on the isolated CI emulator only."""
import json
from pathlib import Path
import re
import subprocess

PACKAGE = "org.localpilot.android.dev.install1081"
OUT = Path("device-evidence")


def adb(*args, timeout=30):
    return subprocess.run(["adb", *args], check=True, text=True,
                          capture_output=True, timeout=timeout).stdout


def main():
    if adb("shell", "getprop", "ro.kernel.qemu").strip() != "1":
        raise RuntimeError("This test is authorized only for an isolated CI emulator")
    OUT.mkdir(exist_ok=True)
    text = adb("shell", "am", "instrument", "-w", "-e", "harness_only", "true",
               PACKAGE + ".test/org.localpilot.android.PilotUiInstrumentation", timeout=400)
    (OUT / "harness-instrumentation.txt").write_text(text)
    result = dict(re.findall(r"^INSTRUMENTATION_RESULT: ([A-Za-z]+)=(.*)$", text, re.M))
    if result.get("result") != "PASS":
        raise RuntimeError("Actual Harness UI failed: " + text[-4000:])
    for field in ("baselineSha", "trainedSha"):
        if not re.fullmatch(r"[a-f0-9]{64}", result.get(field, "")):
            raise RuntimeError("Missing actual policy artifact SHA")
    if result["baselineSha"] == result["trainedSha"]:
        raise RuntimeError("No changed policy artifact")
    (OUT / "harness-ui-report.json").write_text(json.dumps(result, indent=2))
    adb("pull", "/sdcard/Android/data/" + PACKAGE + "/files/harness-ui.png",
        str(OUT / "harness-ui.png"))
    print("HARNESS_ANDROID_WEBVIEW_PASS", result["trainedSha"])


if __name__ == "__main__":
    main()
