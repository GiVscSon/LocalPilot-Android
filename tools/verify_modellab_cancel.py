#!/usr/bin/env python3
"""Guard cancellation against JNI reset races and accidental game-input access."""
from pathlib import Path

cpp = Path("app/src/main/cpp/localpilot_embedded.cpp").read_text(encoding="utf-8")
runtime = Path("model-lab/src/main/java/org/localpilot/android/EmbeddedGgufRuntime.java").read_text(encoding="utf-8")
activity = Path("model-lab/src/main/java/org/localpilot/android/ModelLabActivity.java").read_text(encoding="utf-8")
manifest = Path("model-lab/src/main/AndroidManifest.xml").read_text(encoding="utf-8")

def check(value: bool, why: str):
    if not value:
        raise SystemExit("MODELLAB_CANCEL_CONTRACT_FAIL: " + why)

start = cpp.index("Java_org_localpilot_android_EmbeddedGgufRuntime_nativeResetInterrupt")
text = cpp.index("Java_org_localpilot_android_EmbeddedGgufRuntime_nativeInfer")
vision = cpp.index("Java_org_localpilot_android_EmbeddedGgufRuntime_nativeAnalyzeImage")
interrupt = cpp.index("Java_org_localpilot_android_EmbeddedGgufRuntime_nativeInterrupt")
check(start < text < vision < interrupt, "JNI symbols missing")
check("cancelled.store(false)" in cpp[:text], "fresh-worker reset missing")
check("cancelled.store(false)" not in cpp[text:interrupt],
      "native inference cleared a concurrent STOP request")
check("if(!cancelled.load()&&init(f.str()))" in cpp[text:vision],
      "text inference starts even when previously interrupted")
check("if(!cancelled.load()&&init(f.str())&&!cancelled.load())" in cpp[vision:interrupt],
      "vision can initialize despite interrupted request")
check("nativeResetInterrupt();" in runtime, "worker must reset cancellation once")
check(runtime.index("nativeResetInterrupt();",runtime.index("private void submit(")) <
      runtime.index("result=work.run();",runtime.index("private void submit(")),
      "native reset must precede inference")
check("if(cancelRequested.get())" in runtime, "worker must honor cancellation")
check("cancelRequested.set(false);" in runtime and
      "busy.set(false);" in runtime, "worker must release cancellation state")
check("engine.requestCancel()" in activity, "user cannot stop inference")
check("BIND_ACCESSIBILITY_SERVICE" not in manifest and
      "SYSTEM_ALERT_WINDOW" not in manifest, "ModelLab must remain isolated")
print("MODELLAB_CANCEL_CONTRACT_PASS checks=10")

app_runtime=Path("app/src/main/java/org/localpilot/android/EmbeddedGgufRuntime.java").read_text(encoding="utf-8")
check("private static native void nativeResetInterrupt();" in app_runtime,
      "embedded LocalPilot adapter missing native reset declaration")
check("private void prepareNativeInference()" in app_runtime,
      "embedded LocalPilot adapter has no safe request start")
# The unified APK has six existing inference paths plus TWO real NES action
# paths (text and vision). Every JNI call must reset only once at request
# start and re-check STOP so cancellation cannot be silently cleared.
for name in ("public void consoleAction(", "public void consoleActionImage("):
    check(name in app_runtime, "real NES GGUF entrypoint missing: " + name)
check(app_runtime.count("prepareNativeInference();")==8,
      "not every embedded or unified inference job resets stale cancellation")
check("if(closed||cancelRequested.get()){\n   nativeInterrupt();" in app_runtime,
      "unified engine must re-interrupt when STOP races with JNI reset")
check("public synchronized boolean requestCancel()" in app_runtime and
      "cancelRequested.set(true);" in app_runtime,
      "in-app ModelLab STOP must interrupt the active native worker")
check("NATIVE_JOB_ACTIVE.compareAndSet(false,true)" in app_runtime,
      "both embedded and unified jobs must share a process-global native lock")
print("EMBEDDED_GGUF_CANCEL_COMPAT_PASS checks=6")
