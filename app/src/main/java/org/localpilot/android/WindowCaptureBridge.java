package org.localpilot.android;

import android.accessibilityservice.AccessibilityService;
import android.os.Build;
import android.view.Display;
import java.util.concurrent.Executor;

/**
 * Android API 30..36 Accessibility screenshot invocation only.
 * No pixel remapping, unbounded retries, synthetic input or security fallback.
 * In particular a secure window error must NOT trigger a display screenshot.
 */
public final class WindowCaptureBridge {
 private WindowCaptureBridge() {}
 public static void request(
     AccessibilityService service, WindowCaptureRoute.Source source,
     int verifiedWindowId, Executor executor,
     AccessibilityService.TakeScreenshotCallback callback) {
  if(service==null||source==null||executor==null||callback==null)
   throw new IllegalArgumentException("WINDOW_CAPTURE_ARGUMENT");
  if(source==WindowCaptureRoute.Source.WINDOW_FULLSCREEN) {
   if(Build.VERSION.SDK_INT<34||verifiedWindowId<0)
    throw new IllegalArgumentException("WINDOW_CAPTURE_NOT_SUPPORTED");
   service.takeScreenshotOfWindow(verifiedWindowId,executor,callback);
  } else {
   service.takeScreenshot(Display.DEFAULT_DISPLAY,executor,callback);
  }
 }
}
