package org.localpilot.android;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Native, dependency-free UI smoke test for an isolated Android test device.
 * Enable PilotService before running. UiAutomation must not suppress that service.
 * This exercises DemoActivity and overlay controls, not screenshot/OCR gameplay.
 */
public final class PilotUiInstrumentation extends Instrumentation {
    private static final long TEST_LIMIT_MS = 90_000;
    private static final Pattern SCORE = Pattern.compile("^Счёт:\\s*(\\d+)");

    private final JSONObject evidence = new JSONObject();
    private final JSONArray checks = new JSONArray();
    private UiAutomation automation;
    private PilotService service;
    private DemoActivity demo;
    private long started;
    private long limit;

    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        start();
    }

    @Override public void onStart() {
        started = SystemClock.uptimeMillis();
        limit = started + TEST_LIMIT_MS;
        Bundle result = new Bundle();
        boolean passed = false;
        try {
            automation = getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
            android.accessibilityservice.AccessibilityServiceInfo info = automation.getServiceInfo();
            info.flags |= android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
            automation.setServiceInfo(info);
            evidence.put("package", getTargetContext().getPackageName());
            evidence.put("scope", "real DemoActivity gestures and overlay controls; no screenshot parser acceptance");
            evidence.put("setup", "showPanel/hidePanel use public service API; gameplay starts only through visible UI");
            evidence.put("checks", checks);

            await(() -> onMain(() -> PilotService.instance != null), 10_000,
                    "PilotService is not connected; enable its Accessibility service first");
            service = onMain(() -> PilotService.instance);

            checkCollapseAndReopen();
            checkFullDemo();
            checkStopDuringDemo();
            evidence.put("metrics", onMain(() -> service.exportMetrics()));
            passed = true;
            result.putString("result", "PASS");
            result.putString("summary", "Demo x10, compact Stop, collapse binding, reopen collapse, and Stop stability passed");
        } catch (Throwable error) {
            result.putString("result", "FAIL");
            result.putString("summary", error.toString());
            StringWriter stack = new StringWriter();
            error.printStackTrace(new PrintWriter(stack));
            result.putString("stack", stack.toString());
            try {
                evidence.put("failure", error.toString());
                evidence.put("stack", stack.toString());
                if (service != null) evidence.put("metrics", onMain(() -> service.exportMetrics()));
            } catch (Throwable secondary) {
                result.putString("cleanup_error", secondary.toString());
            }
        } finally {
            try {
                evidence.put("result", passed ? "PASS" : "FAIL");
                evidence.put("duration_ms", SystemClock.uptimeMillis() - started);
                File folder = getTargetContext().getExternalFilesDir("instrumentation");
                if (folder == null) folder = new File(getTargetContext().getFilesDir(), "instrumentation");
                if (!folder.isDirectory() && !folder.mkdirs()) throw new IllegalStateException("Cannot create evidence directory");
                File output = new File(folder, "pilot-ui-evidence.json");
                Files.write(output.toPath(), evidence.toString(2).getBytes(StandardCharsets.UTF_8));
                result.putString("evidence", output.getAbsolutePath());
            } catch (Throwable error) {
                result.putString("result", "FAIL");
                result.putString("evidence_error", error.toString());
                passed = false;
            }
            finish(passed ? Activity.RESULT_OK : Activity.RESULT_CANCELED, result);
        }
    }

    private void checkCollapseAndReopen() throws Exception {
        launchDemo();
        click("Привязать");
        await(this::armed, 2_000, "Overlay did not bind to DemoActivity");
        allowInput();
        long epoch = onMain(() -> gate().epoch());
        click("Свернуть");
        await(this::compact, 2_000, "Collapse click did not compact panel");
        require(present("STOP") && present("Развернуть"), "Compact panel must retain visible STOP and expand control");
        require(armed() && onMain(() -> gate().epoch()) == epoch, "Collapse revoked the current binding");
        require(onMain(() -> permit().allows(gate().epoch(), SystemClock.elapsedRealtime())), "Collapse revoked input consent");
        click("Развернуть");
        await(() -> !compact(), 2_000, "Expand click did not expand panel");
        require(armed() && onMain(() -> gate().epoch()) == epoch, "Expand revoked the current binding");
        addCheck("collapse_expand_preserves_binding_and_permit", new JSONObject().put("active_demo", false));

        click("Свернуть");
        await(this::compact, 2_000, "Panel did not compact before hide/show setup");
        onMain(() -> { service.hidePanel(); service.showPanel(); return null; });
        await(() -> present("Свернуть"), 2_000, "Reopened panel is not expanded");
        require(!compact(), "Reopened panel retained stale compact flag");
        click("Свернуть");
        await(this::compact, 2_000, "First collapse after hide/show failed");
        require(present("STOP"), "STOP missing after reopen/collapse");
        addCheck("first_collapse_after_hide_show", new JSONObject().put("hide_show_method", "public_service_api"));
    }

    private void checkFullDemo() throws Exception {
        launchDemo();
        armAndStart();
        await(() -> score() > 0, 6_000, "Demo made no first move");
        require(compact(), "Demo did not compact panel before starting");
        require(present("STOP") && present("Развернуть"), "Running demo lost compact STOP controls");
        await(() -> score() == 10, 12_000, "Demo did not reach exactly ten real target clicks");
        await(() -> !armed() && onMain(() -> intField("autoLeft")) == 0, 2_000,
                "Completed demo left input armed");
        addCheck("demo_ten_real_target_clicks", new JSONObject().put("score", score()).put("stop_visible_during_run", true));
    }

    private void checkStopDuringDemo() throws Exception {
        launchDemo();
        armAndStart();
        await(() -> score() >= 1, 6_000, "Second demo made no move before Stop");
        require(score() < 10, "Second demo completed before Stop could be exercised");
        click("STOP");
        int afterClick = score();
        await(() -> !onMain(() -> booleanField("busy")), 3_000, "Already dispatched gesture did not settle");
        int settled = score();
        require(settled <= afterClick + 1, "More than one target click happened after Stop returned");
        require(!armed() && onMain(() -> intField("autoLeft")) == 0, "Stop did not disarm future actions");
        long until = SystemClock.uptimeMillis() + 1_100;
        while (SystemClock.uptimeMillis() < until) {
            require(score() == settled, "Demo continued after Stop and gesture completion");
            SystemClock.sleep(60);
        }
        addCheck("stop_during_demo", new JSONObject().put("score_after_stop_click", afterClick)
                .put("settled_score", settled).put("stable_for_ms", 1_100));
    }

    private void launchDemo() throws Exception {
        onMain(() -> { service.hidePanel(); service.showPanel(); return null; });
        Intent intent = new Intent(getTargetContext(), DemoActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        demo = (DemoActivity) startActivitySync(intent);
        waitForIdleSync();
        await(() -> score() == 0 && present("Привязать"), 3_000, "Fresh visible DemoActivity is unavailable");
    }

    private void armAndStart() throws Exception {
        click("Привязать");
        await(this::armed, 2_000, "Demo binding failed");
        allowInput();
        click("Демо ×10");
    }

    private void allowInput() throws Exception {
        click("Разрешить ввод");
        click("Разрешить");
        await(() -> onMain(() -> permit().allows(gate().epoch(), SystemClock.elapsedRealtime())),
                2_000, "Input permission dialog did not grant bounded consent");
    }

    private int score() throws Exception {
        return onMain(() -> visibleScore(demo.getWindow().getDecorView()));
    }

    private int visibleScore(View view) {
        if (view instanceof TextView && view.isShown()) {
            Matcher matcher = SCORE.matcher(((TextView) view).getText().toString());
            if (matcher.find()) return Integer.parseInt(matcher.group(1));
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                int score = visibleScore(group.getChildAt(i));
                if (score >= 0) return score;
            }
        }
        return -1;
    }

    private void click(String text) throws Exception {
        await(() -> {
            AccessibilityNodeInfo node = findText(text);
            if (node == null) return false;
            try { return node.performAction(AccessibilityNodeInfo.ACTION_CLICK); }
            finally { node.recycle(); }
        }, 4_000, "Visible clickable UI control not found: " + text);
    }

    private boolean present(String text) {
        AccessibilityNodeInfo node = findText(text);
        if (node == null) return false;
        node.recycle();
        return true;
    }

    private AccessibilityNodeInfo findText(String text) {
        int[] remaining = {2_000};
        for (AccessibilityWindowInfo window : automation.getWindows()) {
            AccessibilityNodeInfo root = window.getRoot();
            if (root == null) continue;
            try {
                AccessibilityNodeInfo found = findText(root, text, 0, remaining);
                if (found != null) return found;
            } finally { root.recycle(); }
        }
        return null;
    }

    private AccessibilityNodeInfo findText(AccessibilityNodeInfo node, String text, int depth, int[] remaining) {
        if (depth > 40 || --remaining[0] < 0) return null;
        if (node.isVisibleToUser() && node.isEnabled() && node.isClickable()
                && text.contentEquals(node.getText() == null ? "" : node.getText())) {
            return AccessibilityNodeInfo.obtain(node);
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) continue;
            try {
                AccessibilityNodeInfo found = findText(child, text, depth + 1, remaining);
                if (found != null) return found;
            } finally { child.recycle(); }
        }
        return null;
    }

    private boolean armed() throws Exception { return onMain(() -> gate().armed()); }
    private boolean compact() throws Exception { return onMain(() -> booleanField("panelCompact")); }
    private ActionGate gate() throws Exception { return (ActionGate) field("gate"); }
    private InputPermit permit() throws Exception { return (InputPermit) field("permit"); }
    private boolean booleanField(String name) throws Exception { return (Boolean) field(name); }
    private int intField(String name) throws Exception { return (Integer) field(name); }

    private Object field(String name) throws Exception {
        Field field = PilotService.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(service);
    }

    private <T> T onMain(Callable<T> operation) throws Exception {
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        runOnMainSync(() -> {
            try { result.set(operation.call()); }
            catch (Throwable caught) { error.set(caught); }
        });
        if (error.get() != null) throw new Exception("Main-thread assertion/setup failed", error.get());
        return result.get();
    }

    private void await(Condition condition, long timeout, String failure) throws Exception {
        long end = Math.min(limit, SystemClock.uptimeMillis() + timeout);
        do {
            if (condition.get()) return;
            SystemClock.sleep(60);
        } while (SystemClock.uptimeMillis() < end);
        throw new AssertionError(failure);
    }

    private void addCheck(String name, JSONObject details) throws Exception {
        details.put("name", name).put("result", "PASS");
        checks.put(details);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private interface Condition { boolean get() throws Exception; }
}
