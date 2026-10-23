package org.localpilot.android;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
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
 * Uses an isolated synthetic device. Input is driven through visible controls;
 * 2048 progress is independently read from real screenshots, never Android ACK.
 */
public final class PilotUiInstrumentation extends Instrumentation {
    private static final long TEST_LIMIT_MS = 240_000;
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
            info.flags |= android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
                    | android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
            automation.setServiceInfo(info);
            // am instrument restarts the target process. The isolated CI runner enables
            // our Accessibility service only after UiAutomation has attached here.
            Bundle ready=new Bundle();ready.putString("localpilot_ready","1");sendStatus(1,ready);
            evidence.put("package", getTargetContext().getPackageName());
            evidence.put("scope", "native launcher/picker/overlay, DemoActivity real touches, rotation STOP, and screenshot-verified built-in 2048; not arbitrary external-game acceptance");
            evidence.put("setup", "showPanel/hidePanel use public service API; gameplay starts only through visible UI");
            evidence.put("checks", checks);

            await(() -> onMain(() -> PilotService.instance != null), 10_000,
                    "PilotService is not connected; enable its Accessibility service first");
            service = onMain(() -> PilotService.instance);

            checkLauncherAndPicker();
            checkCollapseAndReopen();
            checkFullDemo();
            checkStopDuringDemo();
            checkRotationStopsInput();
            check2048ScreenshotProgress();
            checkProjectionLifecycle();
            evidence.put("metrics", onMain(() -> service.exportMetrics()));
            passed = true;
            result.putString("result", "PASS");
            result.putString("summary", "Launcher/picker, panel reopen, Demo x10 real touches, STOP, rotation disarm, region cancel, screenshot-verified 2048 and two real projection consent/start/stop cycles passed");
        } catch (Throwable error) {
            result.putString("result", "FAIL");
            result.putString("summary", error.toString());
            StringWriter stack = new StringWriter();
            error.printStackTrace(new PrintWriter(stack));
            result.putString("stack", stack.toString());
            try {
                evidence.put("failure", error.toString());
                evidence.put("stack", stack.toString());
                evidence.put("visible_ui", visibleUi());
                if(service!=null)evidence.put("own_state",onMain(()->"connected="+(PilotService.instance==service)+" registered="+field("panelRegistered")+" panel="+(field("panel")!=null)+" note="+field("note")));
                if (service != null) evidence.put("metrics", onMain(() -> service.exportMetrics()));
            } catch (Throwable secondary) {
                result.putString("cleanup_error", secondary.toString());
            }
        } finally {
            try {
                if (automation != null) automation.setRotation(UiAutomation.ROTATION_UNFREEZE);
                if (service != null) onMain(() -> { service.hidePanel(); return null; });
            } catch (Throwable ignored) { /* Preserve the primary acceptance result. */ }
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
            try{evidence.put("result",passed?"PASS":"FAIL");result.putString("evidence_json",evidence.toString());}
            catch(org.json.JSONException error){result.putString("result","FAIL");passed=false;}
            finish(passed ? Activity.RESULT_OK : Activity.RESULT_CANCELED, result);
        }
    }

    private void checkLauncherAndPicker() throws Exception {
        onMain(() -> { service.hidePanel(); return null; });
        launch(MainActivity.class);
        click("Показать панель");
        await(() -> present("STOP") && present("Игра") && present("Окно"), 3_000,
                "Main screen did not recover visible overlay controls");
        require(!armed(), "Showing the panel must not authorize input");
        onMain(() -> { service.hidePanel(); return null; });
        ActivityMonitor picker = addMonitor(GamePickerActivity.class.getName(), null, false);
        try {
            click("Выбрать игру или браузер");
            require(waitForMonitorWithTimeout(picker, 5_000) != null, "Installed-app picker did not launch");
            click("Все приложения");
            await(() -> present("Только игры"), 5_000, "Picker cannot switch to browser/all-app mode");
            require(!armed(), "Opening the picker must not authorize input");
        } finally { removeMonitor(picker); }
        sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
        waitForIdleSync();
        click("Показать панель");
        await(() -> present("STOP"), 3_000, "Panel could not be reopened after returning from picker");
        addCheck("launcher_picker_and_panel_recovery", new JSONObject().put("input_armed", false));
    }

    private Activity launch(Class<? extends Activity> type) {
        Intent intent = new Intent(getTargetContext(), type)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        Activity activity = startActivitySync(intent);
        waitForIdleSync();
        return activity;
    }

    private void checkCollapseAndReopen() throws Exception {
        launchDemo();
        bindDemoWindow();
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
        click("Начать проверку ×10");
        await(() -> score() > 0, 6_000, "Demo made no first move");
        require(compact(), "Demo did not compact panel before starting");
        require(present("STOP") && present("Развернуть"), "Running demo lost compact STOP controls");
        await(() -> score() == 10, 65_000, "Demo did not reach exactly ten real target clicks");
        await(() -> !armed() && onMain(() -> intField("autoLeft")) == 0, 2_000,
                "Completed demo left input armed");
        addCheck("demo_ten_real_target_clicks", new JSONObject().put("start", "single visible test button").put("score", score()).put("stop_visible_during_run", true));
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

    private void checkRotationStopsInput() throws Exception {
        launchDemo();
        click("Начать проверку ×10");
        await(() -> score() >= 1, 6_000, "No real target touch before rotation");
        require(automation.setRotation(UiAutomation.ROTATION_FREEZE_90), "Cannot rotate isolated test device");
        try {
            await(() -> onMain(() -> service.getResources().getConfiguration().orientation)
                    == Configuration.ORIENTATION_LANDSCAPE, 6_000, "Landscape configuration was not delivered");
            await(() -> !armed() && onMain(() -> intField("autoLeft")) == 0, 3_000,
                    "Rotation did not revoke the active input session");
            await(() -> present("STOP") && !onMain(() -> booleanField("busy")), 4_000,
                    "Rotation lost STOP or left a gesture permanently in flight");
        } finally { require(automation.setRotation(UiAutomation.ROTATION_FREEZE_0), "Cannot restore portrait orientation"); }
        await(() -> onMain(() -> service.getResources().getConfiguration().orientation)
                == Configuration.ORIENTATION_PORTRAIT, 6_000, "Portrait configuration was not restored");
        require(!armed(), "Rotation back silently reauthorized input");
        addCheck("rotation_stops_and_does_not_rearm", new JSONObject().put("orientations", "portrait-landscape-portrait"));
    }

    private void check2048ScreenshotProgress() throws Exception {
        onMain(() -> { service.hidePanel(); return null; });
        launch(MainActivity.class);
        ActivityMonitor game = addMonitor(Game2048Activity.class.getName(), null, false);
        try {
            click("Открыть 2048");
            require(waitForMonitorWithTimeout(game, 5_000) != null, "Main screen did not open built-in 2048");
        } finally { removeMonitor(game); }
        await(() -> markerBounds("LOCALPILOT_BOARD_2048") != null, 5_000, "2048 board geometry is unavailable");
        onMain(() -> { service.showPanel(); return null; });
        click("Игра");click("2048");bindDemoWindow();allowInput();

        click("Область");
        await(() -> present("Готово") && present("STOP · отмена"), 3_000,
                "Manual region picker has no initial confirmation/cancel controls");
        Rect grid = markerBounds("Выделение области игры 4 на 4");
        require(grid != null && grid.width() > 100 && grid.height() > 100,
                "Initial 4x4 region selection is not visible");
        click("STOP · отмена");
        require(!onMain(() -> booleanField("playing2048")), "Cancelled region picker started automatic input");
        addCheck("manual_2048_region_visible_and_cancelable", new JSONObject().put("bounds", grid.toShortString()));

        bindDemoWindow();click("Область");
        Rect selected=markerBounds("LOCALPILOT_BOARD_2048");require(selected!=null,"Visible board geometry missing for manual drag");
        drag(selected.left,selected.top,selected.right,selected.bottom);
        require(present("Готово"),"Releasing a region drag confirmed it without explicit Done");
        require(touchOwnControl("Готово"),"Done could not be tapped below system bars");
        await(()->onMain(()->booleanField("manualVisualRoi")&&selected.equals(field("visualRoi"))),3_000,"Manual grid confirmation did not preserve the actual screen coordinates");
        require(!onMain(()->booleanField("playing2048")),"Confirming a region silently started gameplay");
        addCheck("manual_2048_drag_and_explicit_done",new JSONObject().put("bounds",selected.toShortString()).put("input","real touch events"));

        onMain(() -> { service.hidePanel(); return null; });
        Board2048 before = read2048Screenshot();
        onMain(() -> { service.showPanel(); return null; });
        bindDemoWindow();
        click("Старт");
        await(() -> onMain(() -> booleanField("playing2048")), 12_000,
                "2048 did not start through its visible profile/start controls");
        await(() -> onMain(() -> intField("moves2048")) >= 4, 25_000,
                "2048 did not produce at least three visually verified swipes");
        click("STOP");
        await(() -> !armed() && !onMain(() -> booleanField("busy")), 4_000,
                "2048 STOP did not settle and revoke input");
        Board2048 after = read2048Screenshot();
        require(total(after) > total(before), "2048 screen did not gain spawned tile value after real swipes");
        int[] settled = after.cells.clone();
        SystemClock.sleep(600);
        require(java.util.Arrays.equals(settled, read2048Screenshot().cells), "2048 kept changing after STOP settled");
        addCheck("2048_real_screenshot_progress_and_stop", new JSONObject()
                .put("input_start", "visible profile/window/start controls")
                .put("minimum_visual_verifications", 3)
                .put("tile_total_before", total(before)).put("tile_total_after", total(after))
                .put("android_ack_is_not_success", true));
    }

    private static long total(Board2048 board) { long sum = 0; for (int tile : board.cells) sum += tile; return sum; }

    private void checkProjectionLifecycle() throws Exception {
        String fixturePackage = getContext().getPackageName();
        onMain(() -> { service.hidePanel(); service.showPanel(); return null; });
        getContext().startActivity(new Intent().setClassName(fixturePackage, CaptureFixtureActivity.class.getName())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        await(() -> markerBounds("LOCALPILOT_CAPTURE_FIXTURE") != null, 5_000, "Isolated projection fixture did not launch");
        onMain(()->{service.selectInstalledTarget(fixturePackage,"Capture acceptance");return null;});
        await(()->present(ArcadeProfile.ARI.label),6_000,"New external app reused the previous 2048 profile instead of requesting its mechanic");
        click(ArcadeProfile.ARI.label);
        click("Окно");click("@contains:" + fixturePackage);
        long previous = -1;
        for(int cycle=0;cycle<2;cycle++) {
            if(compact())click("Развернуть");
            click("Старт");
            await(this::acceptProjectionDialog, 8_000, "Android screen capture consent did not appear");
            await(() -> onMain(() -> booleanField("jumperCaptureReady") && ((Number)field("captureFrames")).longValue() >= 2),
                    10_000, "Projection did not deliver two decoded frames after consent");
            long session = onMain(() -> ((Number)field("captureSession")).longValue());
            require(session > previous, "Projection restart reused the previous session token");previous=session;
            require(present("STOP"), "STOP panel was not restored after screen-capture consent");
            if(cycle==0)click("STOP");
            else {
                require(automation.setRotation(UiAutomation.ROTATION_FREEZE_90), "Cannot rotate active projection");
            }
            await(() -> !armed() && !onMain(() -> booleanField("playingJumper")), 5_000,
                    "Projection STOP/rotation did not end the input session");
            require(onMain(() -> PilotService.instance == service), "Projection teardown disconnected or crashed the Accessibility service");
        }
        require(automation.setRotation(UiAutomation.ROTATION_FREEZE_0), "Cannot restore portrait after projection");
        addCheck("projection_consent_restart_frames_stop_rotation", new JSONObject().put("decoded_frames_per_session", 2)
                .put("sessions", 2).put("isolated_test_apk", true).put("gameplay_claim", false));
    }

    private boolean acceptProjectionDialog() {
        for(AccessibilityWindowInfo window:automation.getWindows()) {
            AccessibilityNodeInfo root=window.getRoot();if(root==null)continue;
            try {
                String owner=String.valueOf(root.getPackageName());
                if(!owner.equals("com.android.systemui")&&!owner.contains("permissioncontroller"))continue;
                return acceptProjectionDialog(root,0,new int[]{500});
            } finally { root.recycle(); }
        }
        return false;
    }
    private boolean acceptProjectionDialog(AccessibilityNodeInfo node,int depth,int[] remaining) {
        if(depth>24||--remaining[0]<0)return false;
        String text=String.valueOf(node.getText()).toLowerCase(java.util.Locale.ROOT),id=String.valueOf(node.getViewIdResourceName());
        if(node.isVisibleToUser()&&node.isEnabled()&&node.isClickable()
                &&(id.equals("android:id/button1")||text.equals("start now")||text.equals("start recording")||text.equals("share screen")||text.equals("start")))
            return node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        for(int i=0;i<node.getChildCount();i++) {
            AccessibilityNodeInfo child=node.getChild(i);if(child==null)continue;
            try{if(acceptProjectionDialog(child,depth+1,remaining))return true;}finally{child.recycle();}
        }
        return false;
    }

    private Board2048 read2048Screenshot() throws Exception {
        Rect roi = markerBounds("LOCALPILOT_BOARD_2048");
        require(roi != null && !roi.isEmpty(), "Visible 2048 board bounds missing");
        Bitmap screenshot = automation.takeScreenshot();
        require(screenshot != null, "Android returned no test screenshot");
        try {
            return PixelBoardReader.read(screenshot::getPixel, screenshot.getWidth(), screenshot.getHeight(),
                    roi.left, roi.top, roi.right, roi.bottom);
        } finally { screenshot.recycle(); }
    }

    private Rect markerBounds(String marker) {
        int[] remaining = {2_000};
        for (AccessibilityWindowInfo window : automation.getWindows()) {
            AccessibilityNodeInfo root = window.getRoot();
            if (root == null) continue;
            try { Rect found = markerBounds(root, marker, 0, remaining); if (found != null) return found; }
            finally { root.recycle(); }
        }
        return null;
    }

    private Rect markerBounds(AccessibilityNodeInfo node, String marker, int depth, int[] remaining) {
        if (depth > 40 || --remaining[0] < 0) return null;
        if (node.isVisibleToUser() && marker.contentEquals(node.getContentDescription() == null ? "" : node.getContentDescription())) {
            Rect result = new Rect();node.getBoundsInScreen(result);return result;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);if (child == null) continue;
            try { Rect found = markerBounds(child, marker, depth + 1, remaining);if (found != null) return found; }
            finally { child.recycle(); }
        }
        return null;
    }

    private void launchDemo() throws Exception {
        onMain(() -> { service.hidePanel(); service.showPanel(); return null; });
        demo = (DemoActivity) launch(DemoActivity.class);
        await(() -> score() == 0 && present("Начать проверку ×10"), 3_000, "Fresh visible DemoActivity is unavailable");
    }

    private void armAndStart() throws Exception {
        bindDemoWindow();
        await(this::armed, 2_000, "Demo binding failed");
        allowInput();
        click("Ещё");click("Демо ×10");
    }

    private void bindDemoWindow() throws Exception {click("Окно");click("@contains:"+getTargetContext().getPackageName());}

    private void allowInput() throws Exception {
        if(onMain(() -> permit().allows(gate().epoch(),SystemClock.elapsedRealtime())))return;
        click("Ещё");click("Включить автоуправление");bindDemoWindow();
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
            if (node == null) { if(touchOwnControl(text))return true;scrollForward();return false; }
            try { return node.performAction(AccessibilityNodeInfo.ACTION_CLICK); }
            finally { node.recycle(); }
        }, 6_000, "Visible clickable UI control not found: " + text);
    }

    private boolean scrollForward() {
        int[] remaining = {2_000};
        for (AccessibilityWindowInfo window : automation.getWindows()) {
            AccessibilityNodeInfo root = window.getRoot();if (root == null) continue;
            try { if (scrollForward(root, 0, remaining)) return true; }
            finally { root.recycle(); }
        }
        return false;
    }

    private boolean scrollForward(AccessibilityNodeInfo node, int depth, int[] remaining) {
        if (depth > 40 || --remaining[0] < 0) return false;
        if (node.isVisibleToUser() && node.isScrollable()
                && node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) return true;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);if (child == null) continue;
            try { if (scrollForward(child, depth + 1, remaining)) return true; }
            finally { child.recycle(); }
        }
        return false;
    }

    private boolean present(String text) {
        AccessibilityNodeInfo node = findText(text);
        if (node == null)try{return onMain(()->ownControlBounds(text)!=null);}catch(Exception error){return false;}
        node.recycle();
        return true;
    }

    /** Inspect only visible UI geometry; input still traverses Android's real dispatcher. */
    private Rect ownControlBounds(String text) throws Exception {
        if(service==null)return null;
        for(String name:new String[]{"picker","overlayDialog","panel"}){
            Object object=field(name);View root=object instanceof View?(View)object:object instanceof android.app.Dialog?((android.app.Dialog)object).getWindow().getDecorView():null;
            Rect found=ownControlBounds(root,text);if(found!=null)return found;
        }
        return null;
    }
    private Rect ownControlBounds(View view,String text){
        if(view==null||!view.isAttachedToWindow()||!view.isShown()||!view.isEnabled())return null;
        if(view instanceof TextView){
            String label=String.valueOf(((TextView)view).getText());
            boolean match=text.startsWith("@contains:")?label.toLowerCase(java.util.Locale.ROOT).contains(text.substring(10).toLowerCase(java.util.Locale.ROOT)):text.equalsIgnoreCase(label);
            if(match&&(view.isClickable()||view.getParent() instanceof android.widget.AbsListView)){
                Rect visible=new Rect();if(view.getGlobalVisibleRect(visible)&&!visible.isEmpty()){int[] xy=new int[2];view.getLocationOnScreen(xy);return new Rect(xy[0],xy[1],xy[0]+view.getWidth(),xy[1]+view.getHeight());}
            }
        }
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){Rect found=ownControlBounds(group.getChildAt(i),text);if(found!=null)return found;}}
        return null;
    }
    private boolean touchOwnControl(String text)throws Exception{
        Rect rect=onMain(()->ownControlBounds(text));if(rect==null)return false;
        long down=SystemClock.uptimeMillis();
        android.view.MotionEvent start=android.view.MotionEvent.obtain(down,down,android.view.MotionEvent.ACTION_DOWN,rect.centerX(),rect.centerY(),0);
        start.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);
        boolean accepted;try{accepted=automation.injectInputEvent(start,true);}finally{start.recycle();}
        SystemClock.sleep(60);
        android.view.MotionEvent end=android.view.MotionEvent.obtain(down,SystemClock.uptimeMillis(),android.view.MotionEvent.ACTION_UP,rect.centerX(),rect.centerY(),0);
        end.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);
        try{return automation.injectInputEvent(end,true)&&accepted;}finally{end.recycle();}
    }
    private void drag(float x,float y,float endX,float endY)throws Exception{
        long down=SystemClock.uptimeMillis();
        for(int step=0;step<=6;step++){
            int action=step==0?android.view.MotionEvent.ACTION_DOWN:step==6?android.view.MotionEvent.ACTION_UP:android.view.MotionEvent.ACTION_MOVE;
            float part=step/6f;
            android.view.MotionEvent event=android.view.MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x+(endX-x)*part,y+(endY-y)*part,0);
            event.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);
            try{require(automation.injectInputEvent(event,true),"Android rejected region drag input");}finally{event.recycle();}
            SystemClock.sleep(50);
        }
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
        AccessibilityNodeInfo active=automation.getRootInActiveWindow();
        if(active!=null)try{return findText(active,text,0,new int[]{2000});}finally{active.recycle();}
        return null;
    }

    private String visibleUi(){
        StringBuilder text=new StringBuilder();int[] left={120};
        for(AccessibilityWindowInfo window:automation.getWindows()){
            AccessibilityNodeInfo root=window.getRoot();if(root==null)continue;
            try{describe(root,text,0,left);}finally{root.recycle();}
        }
        return text.toString();
    }
    private void describe(AccessibilityNodeInfo node,StringBuilder text,int depth,int[] left){
        if(depth>24||--left[0]<0||text.length()>3500)return;
        if(node.isVisibleToUser())text.append(node.getPackageName()).append(' ').append(node.getClassName())
                .append(" clickable=").append(node.isClickable()).append(" text=")
                .append(String.valueOf(node.getText()).substring(0,Math.min(90,String.valueOf(node.getText()).length()))).append('\n');
        for(int i=0;i<node.getChildCount();i++){
            AccessibilityNodeInfo child=node.getChild(i);if(child==null)continue;
            try{describe(child,text,depth+1,left);}finally{child.recycle();}
        }
    }

    private AccessibilityNodeInfo findText(AccessibilityNodeInfo node, String text, int depth, int[] remaining) {
        if (depth > 40 || --remaining[0] < 0) return null;
        if (node.isVisibleToUser() && node.isEnabled() && node.isClickable()
                && (text.startsWith("@contains:")?String.valueOf(node.getText()).toLowerCase(java.util.Locale.ROOT).contains(text.substring(10).toLowerCase(java.util.Locale.ROOT)):text.equalsIgnoreCase(String.valueOf(node.getText())))) {
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
    private int intField(String name) throws Exception { return ((Number) field(name)).intValue(); }

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
