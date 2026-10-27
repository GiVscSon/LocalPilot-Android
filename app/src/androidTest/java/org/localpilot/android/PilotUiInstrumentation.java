package org.localpilot.android;

import android.app.Activity;
import android.app.Application;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Insets;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.WindowMetrics;
import android.view.accessibility.AccessibilityEvent;
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
import java.util.ArrayDeque;
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
    private Activity launchedActivity;
    private long started;
    private long limit;
    private String bubblePixelSample="not sampled";
    private final Object traceLock=new Object();
    private final ArrayDeque<TraceEntry> lifecycleTrace=new ArrayDeque<>();
    private final ArrayDeque<TraceEntry> clickTrace=new ArrayDeque<>();
    private final ArrayDeque<TraceEntry> slowMainTrace=new ArrayDeque<>();
    private volatile long mainDispatchAt;
    private volatile String mainDispatch="";
    private volatile boolean samplingMain;
    private Thread mainSampler;
    private final android.util.Printer mainQueueMonitor=message->{
        if(message.startsWith(">>>>>")){mainDispatchAt=SystemClock.uptimeMillis();mainDispatch=message;}
        else if(message.startsWith("<<<<<")&&mainDispatchAt>0){
            long elapsed=SystemClock.uptimeMillis()-mainDispatchAt;
            if(elapsed>=500)appendTrace(slowMainTrace,20,"main dispatch "+elapsed+"ms "+mainDispatch.substring(0,Math.min(220,mainDispatch.length())));
            mainDispatchAt=0;
        }
    };
    private String lastClickTrace="";
    private long traceSequence;
    private Application tracedApplication;
    private Activity resumedActivity;
    private int safeFallbackTouches;
    private final Application.ActivityLifecycleCallbacks lifecycleCallbacks=new Application.ActivityLifecycleCallbacks(){
        private void record(Activity activity,String stage){
            String pkg=activity.getPackageName();
            if(!pkg.equals(getTargetContext().getPackageName())&&!pkg.equals(getContext().getPackageName()))return;
            appendTrace(lifecycleTrace,80,stage+" "+activity.getClass().getSimpleName()+"@"+Integer.toHexString(System.identityHashCode(activity))+" finishing="+activity.isFinishing()+" config="+activity.isChangingConfigurations());
        }
        @Override public void onActivityCreated(Activity activity,Bundle state){record(activity,"create");}
        @Override public void onActivityStarted(Activity activity){record(activity,"start");}
        @Override public void onActivityResumed(Activity activity){resumedActivity=activity;record(activity,"resume");}
        @Override public void onActivityPaused(Activity activity){if(resumedActivity==activity)resumedActivity=null;record(activity,"pause");}
        @Override public void onActivityStopped(Activity activity){record(activity,"stop");}
        @Override public void onActivitySaveInstanceState(Activity activity,Bundle state){record(activity,"save");}
        @Override public void onActivityDestroyed(Activity activity){record(activity,"destroy");}
    };

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
            onMain(()->{tracedApplication=(Application)getTargetContext().getApplicationContext();tracedApplication.registerActivityLifecycleCallbacks(lifecycleCallbacks);android.os.Looper.getMainLooper().setMessageLogging(mainQueueMonitor);return null;});
            samplingMain=true;
            mainSampler=new Thread(()->{
                long sampled=0;
                while(samplingMain){
                    long at=mainDispatchAt;
                    if(at>0&&at!=sampled&&SystemClock.uptimeMillis()-at>=1000){
                        sampled=at;StringBuilder stack=new StringBuilder("main stack: ");
                        for(StackTraceElement frame:android.os.Looper.getMainLooper().getThread().getStackTrace()){
                            if(stack.length()>1500)break;stack.append(frame).append(" | ");
                        }
                        appendTrace(slowMainTrace,20,stack.toString());
                        for(java.util.Map.Entry<Thread,StackTraceElement[]> entry:Thread.getAllStackTraces().entrySet()){
                            if(entry.getKey()==android.os.Looper.getMainLooper().getThread())continue;
                            boolean relevant=false;for(StackTraceElement frame:entry.getValue())if(frame.getClassName().contains("AccessibilityInteractionClient")||frame.getClassName().contains("AccessibilityCache")){relevant=true;break;}
                            if(!relevant)continue;
                            StringBuilder other=new StringBuilder("accessibility thread ").append(entry.getKey().getName()).append(": ");
                            for(StackTraceElement frame:entry.getValue()){if(other.length()>1100)break;other.append(frame).append(" | ");}
                            appendTrace(slowMainTrace,20,other.toString());
                        }
                    }
                    SystemClock.sleep(250);
                }
            },"acceptance-main-sampler");mainSampler.setDaemon(true);mainSampler.start();
            automation = getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
            android.accessibilityservice.AccessibilityServiceInfo info = automation.getServiceInfo();
            info.flags |= android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
                    | android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
            // Android 11 UiAutomation runs in this test process: a content-change
            // cache refresh can hold AccessibilityCache's lock while synchronously
            // waiting for the app main thread, which then needs the same lock to
            // process PilotService's event. Do not subscribe this test observer to
            // that event; PilotService's own subscription remains unchanged.
            // UI lookups explicitly refresh the automation cache before use.
            info.eventTypes &= ~AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;
            automation.setServiceInfo(info);
            require((automation.getServiceInfo().eventTypes&AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)==0,
                    "UiAutomation content-change event filter was not applied");
            // am instrument restarts the target process. The isolated CI runner enables
            // our Accessibility service only after UiAutomation has attached here.
            Bundle ready=new Bundle();ready.putString("localpilot_ready","1");sendStatus(1,ready);
            evidence.put("package", getTargetContext().getPackageName());
            evidence.put("scope", "native launcher/picker/overlay, DemoActivity real touches, rotation STOP, screenshot-verified built-in 2048 and independent synthetic bubble fixture; not arbitrary external-game acceptance");
            evidence.put("setup", "showPanel/hidePanel use public service API; gameplay starts only through visible UI");
            evidence.put("checks", checks);

            await(() -> onMain(() -> PilotService.instance != null), 10_000,
                    "PilotService is not connected; enable its Accessibility service first");
            service = onMain(() -> PilotService.instance);

            checkLauncherAndPicker();
            checkCollapseAndReopen();
            checkFullDemo();
            checkStopDuringDemo();
            check2048ScreenshotProgress();
            checkBubbleScreenshotProgress(false);
            checkBubbleScreenshotProgress(true);
            // Keep ordinary app launches before the destructive Demo rotation:
            // service configuration can arrive before Activity recreation ends.
            checkRotationStopsInput();
            checkProjectionLifecycle();
            evidence.put("metrics", onMain(() -> service.exportMetrics()));
            passed = true;
            result.putString("result", "PASS");
            result.putString("summary", "Launcher/picker, panel reopen, Demo x10 real touches, STOP, rotation disarm, region cancel, screenshot-verified 2048/direct and single-reflection bubble fixtures and two real projection consent/start/stop cycles passed");
        } catch (Throwable error) {
            result.putString("result", "FAIL");
            result.putString("summary", error.toString());
            StringWriter stack = new StringWriter();
            error.printStackTrace(new PrintWriter(stack));
            result.putString("stack", stack.toString());
            try {
                evidence.put("failure", error.toString());
                evidence.put("failed_at_ms", SystemClock.uptimeMillis()-started);
                evidence.put("windows", windowSummary());
                evidence.put("stack", stack.toString());
                evidence.put("visible_ui", visibleUi());
                evidence.put("interaction_trace",interactionTrace());
                evidence.put("slow_main_trace",slowMainTrace());
                String fixtureState=bubbleFixtureState();
                if(service!=null)evidence.put("own_state",onMain(()->"connected="+(PilotService.instance==service)+" registered="+field("panelRegistered")+" panel="+(field("panel")!=null)+" note="+field("note")+" demo_score="+(demo==null?-1:visibleScore(demo.getWindow().getDecorView()))+" demo_verified="+field("demoVerified")+" gesture="+field("lastGestureContext")+bubbleControllerState())+" fixture="+fixtureState);
                if (service != null) evidence.put("metrics", onMain(() -> service.exportMetrics()));
            } catch (Throwable secondary) {
                result.putString("cleanup_error", secondary.toString());
            }
        } finally {
            samplingMain=false;
            try {
                if (automation != null) automation.setRotation(UiAutomation.ROTATION_UNFREEZE);
                if (service != null) onMain(() -> { service.hidePanel(); return null; });
            } catch (Throwable ignored) { /* Preserve the primary acceptance result. */ }
            try {
                evidence.put("interaction_trace",interactionTrace());
                evidence.put("safe_fallback_touches",safeFallbackTouches);
                if(tracedApplication!=null)onMain(()->{android.os.Looper.getMainLooper().setMessageLogging(null);tracedApplication.unregisterActivityLifecycleCallbacks(lifecycleCallbacks);return null;});
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
        await(()->onMain(this::launcherInsetsApplied),3_000,"Launcher did not inset its scroll viewport from system bars and cutouts");
        click("Показать панель");
        await(() -> present("STOP") && present("Игра") && present("Окно"), 3_000,
                "Main screen did not recover visible overlay controls");
        require(!armed(), "Showing the panel must not authorize input");
        onMain(() -> { service.hidePanel(); return null; });
        click("Выбрать игру или браузер");
        await(() -> present("Все приложения"), 10_000, "Installed-app picker did not become visible");
        click("Все приложения");
        await(() -> present("Только игры"), 5_000, "Picker cannot switch to browser/all-app mode");
        require(!armed(), "Opening the picker must not authorize input");
        sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
        waitForIdleSync();
        click("Показать панель");
        await(() -> present("STOP"), 3_000, "Panel could not be reopened after returning from picker");
        addCheck("launcher_picker_and_panel_recovery", new JSONObject().put("input_armed", false).put("system_bar_padding_verified",true));
    }

    private Activity launch(Class<? extends Activity> type) {
        Intent intent = new Intent(getTargetContext(), type)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        Activity activity = startActivitySync(intent);
        launchedActivity=activity;
        waitForIdleSync();
        return activity;
    }

    private void checkCollapseAndReopen() throws Exception {
        launchDemo();
        click("Игра");click("Гексон · пока не поддерживается");
        await(()->onMain(()->booleanField("unsupportedGame")&&!gate().armed()),3_000,"Unsupported game was not safely marked");
        click("Игра");click(ArcadeProfile.ARI.label);
        await(()->onMain(()->!booleanField("unsupportedGame")&&field("overlayDialog")==null),3_000,"Supported game did not clear the unsupported selection");
        addCheck("unsupported_game_selection_disarmed",new JSONObject().put("game","Гексон").put("input_armed",false));
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
        long configEpoch=onMain(()->gate().epoch());
        onMain(()->{
            Configuration metadata=new Configuration(service.getResources().getConfiguration());
            metadata.screenHeightDp=Math.max(1,metadata.screenHeightDp-1);
            metadata.fontScale+=0.05f;
            service.onConfigurationChanged(metadata);
            return null;
        });
        require(onMain(()->gate().armed()&&gate().epoch()==configEpoch&&permit().allows(configEpoch,SystemClock.elapsedRealtime())),
                "Metadata-only configuration revoked an unchanged physical input space");
        addCheck("metadata_configuration_preserves_session",new JSONObject().put("injected_callback",true).put("physical_rotation_test_separate",true));
        require(compact(), "Demo did not compact panel before starting");
        require(present("STOP") && present("Развернуть"), "Running demo lost compact STOP controls");
        await(() -> score() == 10, 65_000, "Demo did not reach exactly ten real target clicks");
        // Score changes at touch delivery, before Android's completion callback.
        // Allow the existing 3s gesture watchdog plus the 2s visual verification
        // window; still require both authority and consent to be revoked.
        await(() -> onMain(() -> !gate().armed() && intField("autoLeft") == 0 && intField("demoVerified")==10
                        && !permit().allows(gate().epoch(),SystemClock.elapsedRealtime())),
                GestureFlight.TIMEOUT_MS + 2_500,
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
        await(()->onMain(this::launcherInsetsApplied),3_000,"2048 launcher did not apply safe scroll viewport insets");
        // The real launcher intentionally starts a selected built-in game when
        // automatic control is enabled. Configure observation through its visible
        // switch first so this manual-region scenario starts from a stopped game.
        if(onMain(()->getTargetContext().getSharedPreferences(MainActivity.PREF,Context.MODE_PRIVATE).getBoolean("auto_control",true)))
            click("Автоуправление выбранной игрой");
        await(()->onMain(()->!getTargetContext().getSharedPreferences(MainActivity.PREF,Context.MODE_PRIVATE).getBoolean("auto_control",true)),3_000,"Launcher observation mode did not commit");
        traceClick("2048 before input "+onMain(this::activitySummary));
        click("Открыть 2048");
        traceClick("2048 after input "+onMain(this::activitySummary));
        // Observe the real rendered destination. Older Android can publish an
        // Activity to the screen without notifying this process's class monitor.
        await(() -> markerBounds("LOCALPILOT_BOARD_2048") != null, 10_000, "Main screen did not show the actual 2048 board");
        onMain(() -> { service.showPanel(); return null; });
        click("Игра");click("2048");
        await(()->onMain(()->intField("selectedProfile")==4&&field("overlayDialog")==null),3_000,"2048 mechanic selection did not commit before binding its window");
        bindDemoWindow();allowInput();

        click("Область");
        await(() -> present("Готово") && present("STOP · отмена"), 3_000,
                "Manual region picker has no initial confirmation/cancel controls");
        Rect grid = awaitRegionMarker();
        click("STOP · отмена");
        await(()->onMain(()->field("picker")==null),3_000,"Region cancel did not close its window");
        require(!onMain(() -> booleanField("playing2048")), "Cancelled region picker started automatic input");
        addCheck("manual_2048_region_visible_and_cancelable", new JSONObject().put("bounds", grid.toShortString()));

        bindDemoWindow();click("Область");
        await(()->present("Готово")&&present("STOP · отмена"),3_000,"Region editor did not finish its first layout before dragging");
        awaitRegionMarker();
        automation.waitForIdle(120,3_000);
        Rect selected=markerBounds("LOCALPILOT_BOARD_2048");require(selected!=null,"Visible board geometry missing for manual drag");
        tapAt(selected.left,selected.top);tapAt(selected.right,selected.bottom);
        require(present("Готово"),"Selecting two region corners confirmed it without explicit Done");
        require(touchOwnControl("Готово"),"Done could not be tapped below system bars");
        await(()->onMain(()->booleanField("manualVisualRoi")&&selected.equals(field("visualRoi"))),3_000,"Manual grid confirmation did not preserve the actual screen coordinates");
        require(!onMain(()->booleanField("playing2048")),"Confirming a region silently started gameplay");
        addCheck("manual_2048_two_corners_and_explicit_done",new JSONObject().put("bounds",selected.toShortString()).put("input","real touch events"));

        onMain(() -> { service.hidePanel(); return null; });
        Board2048 before = read2048Screenshot();
        onMain(() -> { service.showPanel(); return null; });
        bindDemoWindow();
        click("Старт");
        await(() -> onMain(() -> booleanField("playing2048")), 12_000,
                "2048 did not start through its visible profile/start controls");
        await(()->onMain(()->intField("moves2048")>=2&&!booleanField("busy")&&!booleanField("captureBusy")),20_000,
                "2048 did not reach its between-read interval before focus injection");
        long boardEpoch=onMain(()->gate().epoch());
        onMain(()->{injectFocusLoss();return null;});
        await(()->onMain(()->!booleanField("focusPaused")),2_000,"2048 focus pause did not settle");
        require(onMain(()->gate().armed()&&gate().epoch()==boardEpoch&&booleanField("playing2048")),"2048 lost its session during transient focus recovery");
        await(() -> onMain(() -> intField("moves2048")) >= 4, 25_000,
                "2048 did not produce at least three visually verified swipes");
        addCheck("2048_focus_resume_without_parallel_capture",new JSONObject().put("loss_notification_injected",true).put("real_screenshot_and_gestures",true));
        click("STOP");
        await(() -> !armed() && !onMain(() -> booleanField("busy")), 4_000,
                "2048 STOP did not settle and revoke input");
        int[] stoppedInput=onMain(()->new int[]{intField("moves2048"),intField("direction2048")});
        Board2048 after = read2048Screenshot();
        require(total(after) > total(before), "2048 screen did not gain spawned tile value after real swipes");
        SystemClock.sleep(600);
        Board2048 settled=read2048Screenshot();boolean lastMoveAppeared=!after.same(settled);
        // Android ACK can precede delivery/composition of the final UP. STOP
        // revokes future actions; at most one previously sent, legal move may
        // become visible. It must then stay still, with no new proposals.
        require(!lastMoveAppeared||after.verifies(settled,stoppedInput[1]),"2048 showed more than its final legal dispatched move after STOP");
        require(onMain(()->!gate().armed()&&!permit().allows(gate().epoch(),SystemClock.elapsedRealtime())&&intField("moves2048")==stoppedInput[0]),"2048 restored input while its final frame settled");
        SystemClock.sleep(1_100);
        require(settled.same(read2048Screenshot()), "2048 kept changing after the final dispatched move settled");
        require(onMain(()->!gate().armed()&&!permit().allows(gate().epoch(),SystemClock.elapsedRealtime())&&intField("moves2048")==stoppedInput[0]),"2048 proposed another action or restored input after STOP");
        addCheck("2048_real_screenshot_progress_and_stop", new JSONObject()
                .put("input_start", "visible profile/window/start controls")
                .put("minimum_visual_verifications", 3)
                .put("tile_total_before", total(before)).put("tile_total_after", total(settled))
                .put("last_dispatched_move_appeared_after_ack",lastMoveAppeared)
                .put("stable_after_final_move_ms",1_100)
                .put("android_ack_is_not_success", true));
    }

    private static long total(Board2048 board) { long sum = 0; for (int tile : board.cells) sum += tile; return sum; }

    private Rect awaitRegionMarker()throws Exception{
        AtomicReference<Rect> observed=new AtomicReference<>();
        // Local button layout may precede publication of the new overlay window
        // to UiAutomation. Require actual Android accessibility geometry too.
        await(()->{Rect rect=markerBounds("Выделение области игры 4 на 4");if(rect==null||rect.width()<=100||rect.height()<=100)return false;observed.set(rect);return true;},4_000,"Initial 4x4 region selection is not visible");
        return observed.get();
    }

    private void checkBubbleScreenshotProgress(boolean ricochet) throws Exception {
        String fixturePackage=getContext().getPackageName();
        onMain(()->{service.hidePanel();service.showPanel();return null;});
        getTargetContext().startActivity(new Intent().setClassName(fixturePackage,BubbleFixtureActivity.class.getName()).putExtra("ricochet",ricochet)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
        await(()->markerBounds("LOCALPILOT_BUBBLE_ARENA")!=null,5_000,"Independent bubble fixture did not launch");
        onMain(()->{service.selectInstalledTarget(fixturePackage,ricochet?"Bubble ricochet acceptance":"Bubble acceptance");return null;});
        String profile="Шипучка · обычные шары (эксперимент)";
        await(()->present(profile),6_000,"External bubble app did not request an explicit mechanic");
        click(profile);
        await(()->onMain(()->intField("selectedProfile")==9&&field("overlayDialog")==null),3_000,"Bubble mechanic did not commit");
        if(!onMain(()->field("picker")!=null)){
            bindWindow(fixturePackage);click("Область");
        }
        // Done is intentionally disabled until both corners exist. Presence()
        // searches enabled controls, so use the visible area and cancel button.
        await(()->markerBounds("Выделение области игры 1 на 1")!=null&&present("STOP · отмена"),4_000,"Bubble arena picker is unavailable");
        Rect arena=markerBounds("LOCALPILOT_BUBBLE_ARENA");require(arena!=null&&!arena.isEmpty(),"Bubble arena geometry disappeared");
        tapAt(arena.left,arena.top);tapAt(arena.right,arena.bottom);
        require(touchOwnControl("Готово"),"Bubble area Done control could not receive a real tap");
        await(()->onMain(()->booleanField("manualVisualRoi")&&arena.equals(field("visualRoi"))),3_000,"Confirmed bubble ROI differs from the selected screen rectangle");
        require(!onMain(()->booleanField("playingGrid")),"Choosing the bubble area silently started input");
        bindWindow(fixturePackage);
        if(!compact())click("Свернуть");
        // ACTION_CLICK/layout flags can finish before SurfaceFlinger has
        // removed the expanded overlay from the actual screenshot. Wait for
        // the original, unmodified target colors to become visible.
        awaitBubblePairPixels(arena,true,ricochet,5_000,"Fixture screenshot did not show all target-colored source circles before dispatch");
        addCheck(ricochet?"bubble_ricochet_explicit_profile_and_manual_area":"bubble_explicit_profile_and_manual_area",new JSONObject().put("bounds",arena.toShortString()).put("real_region_touches",true));
        if(ricochet){
            float gunX=arena.exactCenterX(),gunY=arena.top+arena.height()*.88f;
            require(tapAt(gunX,gunY),"Ricochet fixture plain tap was rejected by Android");
            drag(gunX,gunY,gunX,arena.top+arena.height()*.4f);
            require(markerBounds("LOCALPILOT_BUBBLE_SHOTS_0")!=null&&markerBounds("LOCALPILOT_BUBBLE_SHOTS_1")==null,
                    "A plain tap or non-reflecting direction falsely completed the ricochet fixture");
            awaitBubblePairPixels(arena,true,true,3_000,"Negative ricochet controls changed source cells");
            addCheck("bubble_ricochet_rejects_tap_and_direct_gesture",new JSONObject().put("real_gestures",2).put("visible_board_unchanged",true));
        }
        if(compact())click("Развернуть");
        click("Старт");
        await(()->onMain(()->booleanField("playingGrid")&&intField("selectedProfile")==9),8_000,"Bubble loop did not start through the visible Start control");
        await(()->markerBounds("LOCALPILOT_BUBBLE_SHOTS_1")!=null,20_000,"No real aim/release reached the independent bubble fixture");
        await(()->onMain(()->((BubbleSession)field("bubbleSession")).verified()>=1),12_000,"Delivered bubble gesture did not yield the predicted visually verified board");
        int verified=onMain(()->((BubbleSession)field("bubbleSession")).verified());
        AtomicReference<String> observedFixture=new AtomicReference<>();
        await(()->{
            String state=bubbleFixtureState();
            if(!state.contains("accepted=1")||ricochet&&!Pattern.compile("\\bcells=(16|17)\\b").matcher(state).find())return false;
            observedFixture.set(state);return true;
        },3_000,"Independent fixture did not publish its accepted ordinary match");
        String fixtureState=observedFixture.get();
        Matcher remainingMatcher=Pattern.compile("\\bcells=(\\d+)\\b").matcher(fixtureState);
        int remaining=ricochet&&remainingMatcher.find()?Integer.parseInt(remainingMatcher.group(1)):7;
        awaitBubblePairPixels(arena,false,ricochet,5_000,"Predicted removal is not visible in the real Android screenshot");
        click("STOP");
        await(()->!armed()&&!onMain(()->booleanField("busy")),4_000,"Bubble STOP did not revoke the gesture session");
        long sent=onMain(()->((Number)field("dispatchedInputs")).longValue());
        SystemClock.sleep(1_100);
        require(onMain(()->!gate().armed()&&!permit().allows(gate().epoch(),SystemClock.elapsedRealtime())
                &&!booleanField("playingGrid")&&((Number)field("dispatchedInputs")).longValue()==sent),"Bubble input resumed after STOP");
        require(markerBounds("LOCALPILOT_BUBBLE_SHOTS_1")!=null,"Stopped fixture no longer reports its first accepted ordinary shot");
        awaitBubblePairPixels(arena,false,ricochet,3_000,"Stopped fixture changed after its first accepted ordinary shot");
        require(onMain(()->!gate().armed()&&!permit().allows(gate().epoch(),SystemClock.elapsedRealtime())
                &&((Number)field("dispatchedInputs")).longValue()==sent),"Bubble input resumed while the final screenshot settled");
        addCheck(ricochet?"bubble_ricochet_real_screenshot_aim_release_verification_stop":"bubble_real_screenshot_aim_release_verification_stop",new JSONObject()
                .put("fixture","independent synthetic test-APK view")
                .put("visible_target_cells_before",ricochet?19:9).put("visible_target_cells_after",remaining)
                .put("independent_fixture_state",fixtureState)
                .put("independent_wall_reflections",ricochet?1:0)
                .put("verified_shots",verified).put("android_ack_is_not_success",true)
                .put("source_color_pixel_removal",true).put("stable_after_stop_ms",1_100));
    }

    private String bubbleControllerState()throws Exception{
        BubbleBoard board=(BubbleBoard)field("bubbleGeometry");
        String geometry=board==null?"none":board.width+"x"+board.height+" grid="+board.cols+"x"+board.rows
                +" x0="+board.x0+" y0="+board.y0+" step="+board.step+" gun="+board.launcherX+","+board.launcherY;
        return " bubble_roi="+field("boardRoi")+" geometry="+geometry+" "+((BubbleSession)field("bubbleSession")).report();
    }
    private String bubbleFixtureState(){
        AccessibilityNodeInfo root=automation.getRootInActiveWindow();
        if(root==null)return "no active root";
        try{return bubbleFixtureState(root,0,new int[]{2_000});}finally{root.recycle();}
    }
    private String bubbleFixtureState(AccessibilityNodeInfo node,int depth,int[] remaining){
        if(depth>40||--remaining[0]<0)return "";
        String text=String.valueOf(node.getText());
        if(node.isVisibleToUser()&&text.startsWith("Bubble acceptance"))return text.substring(0,Math.min(360,text.length()));
        for(int i=0;i<node.getChildCount();i++){
            AccessibilityNodeInfo child=node.getChild(i);if(child==null)continue;
            try{String found=bubbleFixtureState(child,depth+1,remaining);if(!found.isEmpty())return found;}finally{child.recycle();}
        }
        return "";
    }

    private void awaitBubblePairPixels(Rect arena,boolean before,boolean ricochet,long timeout,String failure)throws Exception{
        long end=Math.min(limit,SystemClock.uptimeMillis()+timeout);
        do{
            if(bubblePairPixels(arena,before,ricochet))return;
            SystemClock.sleep(100);
        }while(SystemClock.uptimeMillis()<end);
        String overlay=onMain(()->{
            View view=(View)field("panel");Rect visible=new Rect();
            if(view!=null)view.getGlobalVisibleRect(visible);
            return " compact="+booleanField("panelCompact")+" panel="+visible.toShortString()+" picker="+(field("picker")!=null);
        });
        throw new AssertionError(failure+"; "+bubblePixelSample+overlay);
    }

    private boolean bubblePairPixels(Rect arena,boolean before,boolean ricochet){
        Bitmap screenshot=automation.takeScreenshot();if(screenshot==null){bubblePixelSample="null screenshot";return false;}
        try{
            float d=arena.width()/(ricochet?6.4f:7f);int expected=before?(ricochet?0xf08016:0xe52c32):0x87ceeb;
            boolean matches=true;StringBuilder sample=new StringBuilder("arena=").append(arena.toShortString())
                    .append(" screenshot=").append(screenshot.getWidth()).append('x').append(screenshot.getHeight())
                    .append(" expected=").append(Integer.toHexString(expected)).append(" probes=");
            int[] probes=ricochet?(before?new int[]{6,19,20}:new int[]{19,20}):new int[]{0,1};
            for(int cell:probes){
                int row=cell/6,col=cell%6;
                int x=Math.round(arena.left+((ricochet?.7f:.8f)+col+(row%2)*.5f)*d),y=Math.round(arena.top+(1.5f+row*.8660254f)*d);
                sample.append('(').append(x).append(',').append(y).append(")=");
                if(x<0||x>=screenshot.getWidth()||y<0||y>=screenshot.getHeight()){sample.append("outside ");matches=false;continue;}
                int rgb=screenshot.getPixel(x,y),r=(rgb>>16&255)-(expected>>16&255),g=(rgb>>8&255)-(expected>>8&255),b=(rgb&255)-(expected&255);
                sample.append(Integer.toHexString(rgb&0xffffff)).append(' ');
                if(r*r+g*g+b*b>55*55)matches=false;
            }
            bubblePixelSample=sample.toString();return matches;
        }finally{screenshot.recycle();}
    }

    private void checkProjectionLifecycle() throws Exception {
        String fixturePackage = getContext().getPackageName();
        onMain(() -> { service.hidePanel(); service.showPanel(); return null; });
        // The instrumentation runs with the target app's UID. Its launch caller
        // must use that app's attribution, while the explicit component remains
        // in the separate test APK. Android 11 rejects the test context's package
        // as a caller because it belongs to a different installed UID.
        getTargetContext().startActivity(new Intent().setClassName(fixturePackage, CaptureFixtureActivity.class.getName())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        await(() -> markerBounds("LOCALPILOT_CAPTURE_FIXTURE") != null, 8_000, "Projection fixture marker did not become visible after its single launch");
        onMain(()->{service.selectInstalledTarget(fixturePackage,"Capture acceptance");return null;});
        await(()->present(ArcadeProfile.ARI.label),6_000,"New external app reused the previous 2048 profile instead of requesting its mechanic");
        click(ArcadeProfile.ARI.label);
        awaitBrowserMechanic();
        require(onMain(()->booleanField("targetIsBrowser")),"Isolated VIEW/HTTPS fixture was not classified as a browser context");
        onMain(()->{service.selectInstalledTarget(fixturePackage,"Capture acceptance");return null;});
        await(()->present(ArcadeProfile.ARI.label),6_000,"Reopening a browser before gameplay retained an old mechanic confirmation");
        click(ArcadeProfile.ARI.label);
        awaitBrowserMechanic();
        bindWindow(fixturePackage);
        long previous = -1;
        for(int cycle=0;cycle<2;cycle++) {
            if(compact())click("Развернуть");
            if(cycle>0){click("Старт");await(()->present(ArcadeProfile.ARI.label),5_000,"A new browser capture reused mechanic confirmation after STOP");click(ArcadeProfile.ARI.label);awaitBrowserMechanic();}
            click("Старт");
            await(this::acceptProjectionDialog, 8_000, "Android screen capture consent did not appear");
            await(() -> onMain(() -> booleanField("jumperCaptureReady") && ((Number)field("captureFrames")).longValue() >= 2),
                    10_000, "Projection did not deliver two decoded frames after consent");
            long session = onMain(() -> ((Number)field("captureSession")).longValue());
            require(session > previous, "Projection restart reused the previous session token");previous=session;
            require(present("STOP"), "STOP panel was not restored after screen-capture consent");
            if(cycle==0){
                checkFocusRecovery(session);
                // Revoke via the real STOP control after a second injected loss.
                onMain(()->{injectFocusLoss();return null;});
                click("STOP");
            }
            else {
                require(automation.setRotation(UiAutomation.ROTATION_FREEZE_90), "Cannot rotate active projection");
            }
            await(() -> !armed() && !onMain(() -> booleanField("playingJumper")), 5_000,
                    "Projection STOP/rotation did not end the input session");
            require(onMain(() -> PilotService.instance == service), "Projection teardown disconnected or crashed the Accessibility service");
        }
        SystemClock.sleep(WindowFocus.GRACE_MS+200);
        require(onMain(()->!gate().armed()&&!booleanField("playingJumper")&&!booleanField("jumperCaptureReady")),"Delayed focus watch restarted a stopped capture");
        require(automation.setRotation(UiAutomation.ROTATION_FREEZE_0), "Cannot restore portrait after projection");
        addCheck("projection_consent_restart_frames_stop_rotation", new JSONObject().put("decoded_frames_per_session", 2)
                .put("sessions", 2).put("isolated_test_apk", true).put("gameplay_claim", false));
    }

    /** Inject the loss notification, but use real window queries, timer, gate and projection. */
    private void injectFocusLoss()throws Exception{
        java.lang.reflect.Method method=PilotService.class.getDeclaredMethod("deferFocus",String.class);
        method.setAccessible(true);
        require(Boolean.TRUE.equals(method.invoke(service,"WINDOW_STATE_UNAVAILABLE")),"Focus loss was not deferred");
        require(gate().armed()&&gate().paused()&&booleanField("focusPaused"),"Focus loss did not pause the gate");
        require(!service.wantsJumperFrame(((Number)field("captureSession")).longValue()),"Paused capture still accepted work");
    }
    private void checkFocusRecovery(long session)throws Exception{
        await(()->onMain(()->!booleanField("jumperConsentPending")),3_000,"Consent target handoff did not settle before focus check");
        long epoch=onMain(()->gate().epoch());
        ActionGate.Ticket old=onMain(()->gate().offer(SystemClock.elapsedRealtime()));
        long frames=onMain(()->((Number)field("captureFrames")).longValue());
        long inputs=onMain(()->((Number)field("dispatchedInputs")).longValue());
        onMain(()->{injectFocusLoss();return null;});
        await(()->onMain(()->!booleanField("focusPaused")),2_000,"Focus did not recover or stop");
        require(onMain(()->gate().armed()&&gate().epoch()==epoch&&booleanField("playingJumper")&&booleanField("jumperCaptureReady")&&((Number)field("captureSession")).longValue()==session),
                "Transient focus recovery discarded the capture session");
        require(onMain(()->{
            Rect bounds=(Rect)field("armedBounds");
            return "FOCUS_CHANGED".equals(gate().rejection(old,SystemClock.elapsedRealtime(),(String)field("target"),old.window,bounds.width(),bounds.height()));
        }),"Pre-pause input ticket survived recovery");
        require(onMain(()->((Number)field("dispatchedInputs")).longValue()==inputs),"Unrecognized fixture received input while recovering");
        await(()->onMain(()->((Number)field("captureFrames")).longValue()>frames&&((Number)field("jumperLastFrameMs")).longValue()>=((Number)field("focusRecoveredAt")).longValue()),4_000,
                "Projection did not deliver a fresh frame after focus recovery");
        addCheck("transient_focus_preserves_capture_and_rejects_old_ticket",new JSONObject().put("loss_notification_injected",true).put("real_projection_and_window_query",true).put("same_capture_session",true));
    }

    private boolean acceptProjectionDialog() {
        for(AccessibilityWindowInfo window:automation.getWindows()) {
            AccessibilityNodeInfo root=window.getRoot();if(root==null)continue;
            try {
                String owner=String.valueOf(root.getPackageName());
                if(!owner.equals("com.android.systemui")&&!owner.contains("permissioncontroller"))continue;
                // Android 11 may publish the status bar before the separate
                // SystemUI consent window. A root without a consent button is
                // not proof that the other system windows have none.
                if(acceptProjectionDialog(root,0,new int[]{500}))return true;
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
        refreshAutomationCache();
        int[] remaining = {2_000};
        for (AccessibilityWindowInfo window : automation.getWindows()) {
            AccessibilityNodeInfo root = window.getRoot();
            if (root == null) continue;
            try { Rect found = markerBounds(root, marker, 0, remaining); if (found != null) return found; }
            finally { root.recycle(); }
        }
        // During an Activity transition within the same fixture package,
        // window-list roots may lag the actual focused content. Query the real
        // active root as well, with its own bounded traversal; never infer a
        // marker from package identity or an ActivityManager launch record.
        AccessibilityNodeInfo active=automation.getRootInActiveWindow();
        if(active!=null)try{return markerBounds(active,marker,0,new int[]{2_000});}finally{active.recycle();}
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

    private void bindDemoWindow() throws Exception {bindWindow(getTargetContext().getPackageName());}
    private void bindWindow(String pkg)throws Exception{
        click("Окно");
        String query="@contains:"+pkg;
        // Require the current live dialog, but do not equate decor bounds with
        // accessibility window frames: shadows/insets differ across Android.
        await(()->{
            String state=onMain(()->"epoch="+gate().epoch()+" note="+field("note")+" dialog="+(field("overlayDialog")!=null));
            traceClick("window-selection "+state+" "+windowSummary());
            return onMain(()->{
                Object object=field("overlayDialog");
                if(!(object instanceof android.app.Dialog dialog)||!dialog.isShowing())return false;
                View decor=dialog.getWindow().getDecorView();
                if(!decor.isLaidOut())return false;
                Rect row=ownControlBounds(decor,query);
                return row!=null&&!row.isEmpty();
            });
        },3_000,"The current window dialog has no visible target row");
        // click() performs a fresh lookup and refreshes the actionable node.
        click(query);
        await(()->onMain(()->gate().armed()&&pkg.equals(field("target"))&&field("overlayDialog")==null),3_000,"Window selection did not commit before the next control");
    }
    private void awaitBrowserMechanic()throws Exception{await(()->onMain(()->booleanField("browserProfileConfirmed")&&field("overlayDialog")==null),3_000,"Browser mechanic selection did not finish before the next action");}

    private void allowInput() throws Exception {
        if(onMain(() -> permit().allows(gate().epoch(),SystemClock.elapsedRealtime())))return;
        click("Ещё");click("Включить автоуправление");
        await(()->onMain(()->getTargetContext().getSharedPreferences(MainActivity.PREF,Context.MODE_PRIVATE).getBoolean("auto_control",false)&&field("overlayDialog")==null),3_000,"Automatic input consent did not commit before rebinding");
        bindDemoWindow();
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
        traceClick("click begin label="+text+" "+onMain(this::activitySummary));
        await(() -> {
            AccessibilityNodeInfo node = findText(text);
            if (node == null) { if(touchOwnControl(text))return true;boolean scrolled=scrollForward();traceClick("click label="+text+" path=own-app-scroll result="+scrolled);return false; }
            try {
                if(!node.refresh()||!node.isVisibleToUser()||!node.isEnabled()||!node.isClickable()){traceClick("click label="+text+" path=node refreshed-actionable=false");return false;}
                Rect bounds=new Rect();node.getBoundsInScreen(bounds);
                boolean handled=node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                traceClick("click label="+text+" path=node pkg="+node.getPackageName()+" window="+node.getWindowId()+" bounds="+bounds.toShortString()+" handled="+handled);
                return handled;
            }
            finally { node.recycle(); }
        }, 6_000, "Visible clickable UI control not found: " + text);
    }

    private boolean scrollForward() {
        int[] remaining = {2_000};
        for (AccessibilityWindowInfo window : automation.getWindows()) {
            if(window.getType()!=AccessibilityWindowInfo.TYPE_APPLICATION||!window.isFocused())continue;
            AccessibilityNodeInfo root = window.getRoot();if (root == null) continue;
            try {
                String pkg=String.valueOf(root.getPackageName());
                // A late app tree must never turn a test scroll into a system
                // notification/quick-settings gesture or affect another app.
                if(!pkg.equals(getTargetContext().getPackageName())&&!pkg.equals(getContext().getPackageName()))continue;
                if (scrollForward(root, 0, remaining)) return true;
            }
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

    /** Inspect only our live controls; coordinate input must also avoid system bars. */
    private ControlTarget ownControlTarget(String text) throws Exception {
        if(service==null)return null;
        for(String name:new String[]{"picker","overlayDialog","panel"}){
            Object object=field(name);View root=object instanceof View?(View)object:object instanceof android.app.Dialog?((android.app.Dialog)object).getWindow().getDecorView():null;
            ControlTarget found=ownControlTarget(root,text);if(found!=null)return found;
        }
        // The actual launched Activity can be laid out before its accessibility
        // tree is published. Use only its focused live decor as a geometry
        // fallback, so a stopped Main behind the picker cannot receive input.
        Activity activity=launchedActivity;
        if(activity!=null&&!activity.isFinishing()&&!activity.isDestroyed()){
            View decor=activity.getWindow().getDecorView();
            if(decor.isAttachedToWindow()&&decor.getWindowVisibility()==View.VISIBLE&&decor.hasWindowFocus())return ownControlTarget(decor,text);
        }
        return null;
    }
    private Rect ownControlBounds(String text)throws Exception{
        ControlTarget target=ownControlTarget(text);return target==null||!target.canTap()?null:new Rect(target.clipped);
    }
    private Rect ownControlBounds(View view,String text){
        ControlTarget target=ownControlTarget(view,text);return target==null||!target.canTap()?null:new Rect(target.clipped);
    }
    private ControlTarget ownControlTarget(View view,String text){
        if(view==null||!view.isAttachedToWindow()||!view.isShown()||!view.isEnabled())return null;
        if(view instanceof TextView){
            String label=String.valueOf(((TextView)view).getText());
            if(matchesText(label,text)&&(view.isClickable()||view.getParent() instanceof android.widget.AbsListView)){
                Rect visible=new Rect();if(view.getLocalVisibleRect(visible)&&!visible.isEmpty())return new ControlTarget(view,visible);
            }
        }
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){ControlTarget found=ownControlTarget(group.getChildAt(i),text);if(found!=null)return found;}}
        return null;
    }
    private static boolean matchesText(String label,String query){return query.startsWith("@contains:")?label.toLowerCase(java.util.Locale.ROOT).contains(query.substring(10).toLowerCase(java.util.Locale.ROOT)):query.equalsIgnoreCase(label);}
    private static final class ControlTarget{
        final View view;final Rect full,visible,window,safe,clipped;final Insets insets;
        ControlTarget(View view,Rect localVisible){
            this.view=view;int[] xy=new int[2];view.getLocationOnScreen(xy);
            full=new Rect(xy[0],xy[1],xy[0]+view.getWidth(),xy[1]+view.getHeight());
            visible=new Rect(localVisible);visible.offset(xy[0],xy[1]);
            WindowMetrics metrics=view.getContext().getSystemService(WindowManager.class).getCurrentWindowMetrics();
            window=new Rect(metrics.getBounds());insets=metrics.getWindowInsets().getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
            safe=new Rect(window);safe.set(safe.left+insets.left,safe.top+insets.top,safe.right-insets.right,safe.bottom-insets.bottom);
            clipped=new Rect(visible);if(!clipped.intersect(safe))clipped.setEmpty();
        }
        // Scroll a clipped control into view instead of tapping its first sliver.
        boolean canTap(){return !full.isEmpty()&&clipped.contains(full.centerX(),full.centerY());}
        String describe(){return "full="+full.toShortString()+" visible="+visible.toShortString()+" window="+window.toShortString()+" insets="+insets+" safe="+safe.toShortString()+" clipped="+clipped.toShortString()+" xy="+full.centerX()+","+full.centerY();}
    }
    private boolean touchOwnControl(String text)throws Exception{
        ControlTarget target=onMain(()->ownControlTarget(text));if(target==null)return false;
        if(!target.canTap()){traceClick("click label="+text+" path=touch blocked "+target.describe());return false;}
        require(target.safe.contains(target.full.centerX(),target.full.centerY()),"Fallback tap center lies outside safe system-bar/cutout content");
        boolean fresh=onMain(()->{
            ControlTarget current=ownControlTarget(text);
            return current!=null&&current.view==target.view&&current.canTap()&&current.full.equals(target.full)&&current.clipped.equals(target.clipped)&&current.safe.equals(target.safe);
        });
        if(!fresh){traceClick("click label="+text+" path=touch geometry changed before input");return false;}
        traceClick("click label="+text+" path=touch "+target.describe());
        AtomicReference<Throwable> injectionError=new AtomicReference<>();
        AtomicReference<Boolean> injected=new AtomicReference<>(false);
        AccessibilityEvent clicked;
        try{
            clicked=automation.executeAndWaitForEvent(()->{
                try{injected.set(tapAt(target.full.centerX(),target.full.centerY()));}
                catch(Throwable error){injectionError.set(error);}
            },event->{
                String pkg=String.valueOf(event.getPackageName());
                if(event.getEventType()!=AccessibilityEvent.TYPE_VIEW_CLICKED||(!pkg.equals(getTargetContext().getPackageName())&&!pkg.equals(getContext().getPackageName())))return false;
                for(CharSequence label:event.getText())if(matchesText(String.valueOf(label),text))return true;
                return false;
            },500);
        }catch(java.util.concurrent.TimeoutException error){
            traceClick("click label="+text+" path=touch injected="+injected.get()+" matching-click-event=false "+onMain(this::activitySummary));
            if(injectionError.get()!=null)throw new Exception("Fallback input failed",injectionError.get());
            require(injected.get(),"Android rejected fallback control input: "+text);
            safeFallbackTouches++;
            // A control may detach before emitting TYPE_VIEW_CLICKED. This event
            // is diagnostic; each caller still asserts its actual destination,
            // dialog commitment, authorization or rendered game result.
            return true;
        }
        try{
            if(injectionError.get()!=null)throw new Exception("Fallback input failed",injectionError.get());
            require(injected.get(),"Android rejected fallback control input: "+text);
            safeFallbackTouches++;
            traceClick("click label="+text+" path=touch injected=true matching-click-event=true "+onMain(this::activitySummary));
            return true;
        }finally{clicked.recycle();}
    }
    private boolean tapAt(float x,float y)throws Exception{
        long down=SystemClock.uptimeMillis();
        android.view.MotionEvent start=android.view.MotionEvent.obtain(down,down,android.view.MotionEvent.ACTION_DOWN,x,y,0);
        start.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);
        boolean accepted;try{accepted=automation.injectInputEvent(start,true);}finally{start.recycle();}
        SystemClock.sleep(60);
        android.view.MotionEvent end=android.view.MotionEvent.obtain(down,SystemClock.uptimeMillis(),android.view.MotionEvent.ACTION_UP,x,y,0);
        end.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);
        try{return automation.injectInputEvent(end,true)&&accepted;}finally{end.recycle();}
    }

    private boolean launcherInsetsApplied(){
        Activity activity=launchedActivity;
        if(!(activity instanceof MainActivity)||activity.isFinishing()||activity.isDestroyed())return false;
        View content=activity.findViewById(android.R.id.content);
        if(!(content instanceof ViewGroup group)||group.getChildCount()!=1||!(group.getChildAt(0) instanceof android.widget.ScrollView scroll))return false;
        WindowInsets applied=scroll.getRootWindowInsets();
        if(applied==null||!scroll.isLaidOut())return false;
        Insets expected=applied.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
        return scroll.getPaddingLeft()==expected.left&&scroll.getPaddingTop()==expected.top&&scroll.getPaddingRight()==expected.right&&scroll.getPaddingBottom()==expected.bottom;
    }

    private String activitySummary(){
        Activity activity=resumedActivity!=null?resumedActivity:launchedActivity;
        if(activity==null)return "activity=none";
        View decor=activity.getWindow().getDecorView();
        WindowMetrics metrics=activity.getWindowManager().getCurrentWindowMetrics();
        Insets insets=metrics.getWindowInsets().getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
        Rect safe=new Rect(metrics.getBounds());safe.set(safe.left+insets.left,safe.top+insets.top,safe.right-insets.right,safe.bottom-insets.bottom);
        return "activity="+activity.getClass().getSimpleName()+" finishing="+activity.isFinishing()+" destroyed="+activity.isDestroyed()+" focus="+decor.hasWindowFocus()+" attached="+decor.isAttachedToWindow()+" insets="+insets+" safe="+safe.toShortString();
    }

    private static final class TraceEntry{
        final long sequence,at;final String message;
        TraceEntry(long sequence,long at,String message){this.sequence=sequence;this.at=at;this.message=message;}
    }
    private void appendTrace(ArrayDeque<TraceEntry> entries,int maximum,String message){
        synchronized(traceLock){while(entries.size()>=maximum)entries.removeFirst();entries.addLast(new TraceEntry(++traceSequence,SystemClock.uptimeMillis()-started,message));}
    }
    private void traceClick(String message){
        synchronized(traceLock){if(message.equals(lastClickTrace))return;lastClickTrace=message;appendTrace(clickTrace,40,message);}
    }
    private JSONArray interactionTrace(){
        synchronized(traceLock){
            java.util.ArrayList<TraceEntry> entries=new java.util.ArrayList<>(lifecycleTrace);entries.addAll(clickTrace);entries.addAll(slowMainTrace);entries.sort(java.util.Comparator.comparingLong(entry->entry.sequence));
            JSONArray result=new JSONArray();for(TraceEntry entry:entries)result.put(entry.at+"ms "+entry.message);return result;
        }
    }
    private JSONArray slowMainTrace(){
        synchronized(traceLock){JSONArray result=new JSONArray();for(TraceEntry entry:slowMainTrace)result.put(entry.at+"ms "+entry.message);return result;}
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

    private void refreshAutomationCache(){
        // Public UiAutomation API. Before API34, reapplying the unchanged info
        // also clears its interaction cache (AOSP UiAutomation.setServiceInfo).
        // Do not trust cached windows/children across Activity/dialog replacement.
        if(android.os.Build.VERSION.SDK_INT>=34)automation.clearCache();
        else automation.setServiceInfo(automation.getServiceInfo());
    }

    private AccessibilityNodeInfo findText(String text) {
        refreshAutomationCache();
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

    private String windowSummary() throws Exception {
        StringBuilder out=new StringBuilder("automation: ");
        describeWindows(automation.getWindows(),out);
        out.append(" service: ");
        out.append(onMain(()->{StringBuilder own=new StringBuilder();describeWindows(service.getWindows(),own);return own.toString();}));
        return out.toString();
    }
    private static void describeWindows(java.util.List<AccessibilityWindowInfo> windows,StringBuilder out){
        for(AccessibilityWindowInfo window:windows){
            Rect bounds=new Rect();window.getBoundsInScreen(bounds);
            out.append("id=").append(window.getId()).append(" type=").append(window.getType())
                .append(" focused=").append(window.isFocused()).append(" bounds=").append(bounds.toShortString());
            AccessibilityNodeInfo root=window.getRoot();
            try{out.append(" root=").append(root==null?"null":root.getPackageName());if(root!=null)out.append(" rootWindow=").append(root.getWindowId());out.append(';');}
            finally{if(root!=null)root.recycle();}
        }
    }

    private String visibleUi(){
        StringBuilder text=new StringBuilder();int[] left={120};
        java.util.List<AccessibilityWindowInfo> windows=automation.getWindows();
        // The focused system consent or ANR dialog is the relevant failure
        // evidence. Status/navigation trees must not consume the bounded dump.
        for(int pass=0;pass<2;pass++)for(AccessibilityWindowInfo window:windows){
            if(window.isFocused()!=(pass==0))continue;
            AccessibilityNodeInfo root=window.getRoot();if(root==null)continue;
            try{describe(root,text,0,left);}finally{root.recycle();}
        }
        return text.toString();
    }
    private void describe(AccessibilityNodeInfo node,StringBuilder text,int depth,int[] left){
        if(depth>24||--left[0]<0||text.length()>3500)return;
        if(node.isVisibleToUser())text.append(node.getPackageName()).append(' ').append(node.getClassName())
                .append(" id=").append(node.getViewIdResourceName()).append(" enabled=").append(node.isEnabled())
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
