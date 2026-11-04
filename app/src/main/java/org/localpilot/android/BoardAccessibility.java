package org.localpilot.android;

import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Bundle;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeProvider;

import java.util.function.Supplier;

/** Exposes drawn board geometry, never the game's hidden tile state. */
final class BoardAccessibility extends AccessibilityNodeProvider {
    private static final int BOARD_ID = 1;
    private final View host;
    private final Supplier<Rect> bounds;
    private final String marker;
    private final Rect lastDrawnBounds = new Rect();
    private final Rect lastScreenBounds = new Rect();
    private boolean accessibilityFocused;

    BoardAccessibility(View host, Supplier<Rect> bounds, String marker) {
        this.host = host;
        this.bounds = bounds;
        this.marker = marker;
    }

    /** Called after Canvas layout; invalidate cached virtual geometry only when it changes. */
    void onBoardDrawn() {
        Rect drawn = new Rect(bounds.get());
        Rect screen = screenBounds(drawn);
        if (!drawn.equals(lastDrawnBounds) || !screen.equals(lastScreenBounds)) {
            lastDrawnBounds.set(drawn);
            lastScreenBounds.set(screen);
            if (host.isAttachedToWindow() && host.isShown()) {
                host.sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);
            }
        }
    }

    @Override public AccessibilityNodeInfo createAccessibilityNodeInfo(int id) {
        if (id == HOST_VIEW_ID) {
            AccessibilityNodeInfo info = AccessibilityNodeInfo.obtain(host);
            host.onInitializeAccessibilityNodeInfo(info);
            info.addChild(host, BOARD_ID);
            return info;
        }
        if (id != BOARD_ID) return null;

        Rect local = new Rect(bounds.get());
        Rect visible = new Rect();
        Rect screen = screenBounds(local);
        boolean fullyVisible = !local.isEmpty() && !screen.isEmpty()
                && host.isAttachedToWindow() && host.isShown()
                && host.getWindowVisibility() == View.VISIBLE
                && host.getLocalVisibleRect(visible) && visible.contains(local);

        AccessibilityNodeInfo info = AccessibilityNodeInfo.obtain();
        info.setSource(host, BOARD_ID);
        info.setParent(host);
        info.setPackageName(host.getContext().getPackageName());
        info.setClassName(View.class.getName());
        info.setContentDescription(marker);
        info.setBoundsInParent(local);
        info.setBoundsInScreen(screen);
        info.setVisibleToUser(fullyVisible);
        info.setEnabled(host.isEnabled());
        info.setClickable(false);
        info.setLongClickable(false);
        info.setFocusable(true);
        info.setScreenReaderFocusable(true);
        info.setAccessibilityFocused(accessibilityFocused);
        info.addAction(accessibilityFocused
                ? AccessibilityNodeInfo.AccessibilityAction.ACTION_CLEAR_ACCESSIBILITY_FOCUS
                : AccessibilityNodeInfo.AccessibilityAction.ACTION_ACCESSIBILITY_FOCUS);
        return info;
    }

    private Rect screenBounds(Rect local) {
        if (local.isEmpty()) return new Rect();
        Matrix transform = new Matrix();
        host.transformMatrixToGlobal(transform);
        // Arbitrarily rotated/skewed boards do not have a valid axis-aligned parsing ROI.
        if (!transform.rectStaysRect()) return new Rect();
        float[] origin = {0, 0};
        transform.mapPoints(origin);
        RectF mapped = new RectF(local);
        transform.mapRect(mapped);
        int[] location = new int[2];
        host.getLocationOnScreen(location);
        mapped.offset(location[0] - origin[0], location[1] - origin[1]);
        Rect result = new Rect();
        mapped.round(result);
        return result;
    }

    @Override public boolean performAction(int id, int action, Bundle arguments) {
        if (id != BOARD_ID) return false;
        boolean focus = action == AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS;
        if (!focus && action != AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS) return false;
        if (focus == accessibilityFocused) return false;
        if (focus) {
            AccessibilityNodeInfo node = createAccessibilityNodeInfo(BOARD_ID);
            boolean visible = node.isVisibleToUser();
            node.recycle();
            if (!visible) return false;
        }
        accessibilityFocused = focus;
        AccessibilityEvent event = AccessibilityEvent.obtain(focus
                ? AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED
                : AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED);
        event.setPackageName(host.getContext().getPackageName());
        event.setClassName(View.class.getName());
        event.setContentDescription(marker);
        event.setSource(host, BOARD_ID);
        if (host.getParent() != null) host.getParent().requestSendAccessibilityEvent(host, event);
        else event.recycle();
        return true;
    }

    @Override public AccessibilityNodeInfo findFocus(int focus) {
        return focus == AccessibilityNodeInfo.FOCUS_ACCESSIBILITY && accessibilityFocused
                ? createAccessibilityNodeInfo(BOARD_ID) : null;
    }
}
