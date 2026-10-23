package org.localpilot.android;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;

/** Obtains Android's explicit, per-session screen capture consent. */
public final class JumperCaptureConsentActivity extends Activity {
    private static final int REQUEST_CAPTURE = 8031;
    private long session;
    private boolean requested,leftForPicker,resultHandled,resumed;
    private final android.os.Handler callbacks=new android.os.Handler(android.os.Looper.getMainLooper());

    @Override protected void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        session = getIntent().getLongExtra(JumperCaptureService.EXTRA_SESSION, 0);
        PilotService pilot=PilotService.instance;
        if(pilot==null||!pilot.captureSessionActive(session)){finishConsent();return;}
        if (savedState != null) {
            requested=savedState.getBoolean("capture_requested");
            leftForPicker=savedState.getBoolean("left_for_picker");
            resultHandled=savedState.getBoolean("result_handled");
            if(resultHandled)return;
            if(requested)return;
        }
        MediaProjectionManager manager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        if (manager == null) { denied("Захват экрана недоступен"); return; }
        try {
            Intent capture = android.os.Build.VERSION.SDK_INT >= 34
                    ? manager.createScreenCaptureIntent(android.media.projection.MediaProjectionConfig.createConfigForDefaultDisplay())
                    : manager.createScreenCaptureIntent();
            requested=true;startActivityForResult(capture, REQUEST_CAPTURE);
        }
        catch (RuntimeException error) { pilot.onCaptureStartupError(session,"Android не открыл разрешение захвата",error.getClass().getSimpleName());finishConsent(); }
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putBoolean("capture_requested", requested);
        state.putBoolean("left_for_picker", leftForPicker);
        state.putBoolean("result_handled", resultHandled);
        super.onSaveInstanceState(state);
    }

    @Override protected void onPause(){
        super.onPause();resumed=false;callbacks.removeCallbacksAndMessages(null);if(requested&&!resultHandled)leftForPicker=true;
    }

    @Override protected void onResume(){
        super.onResume();resumed=true;
        if(resultHandled){awaitService();return;}
        if(leftForPicker&&!resultHandled)callbacks.postDelayed(()->{
            PilotService pilot=PilotService.instance;
            if(!resultHandled&&!isFinishing()&&!isDestroyed()&&pilot!=null&&pilot.captureSessionActive(session)){
                resultHandled=true;
                pilot.onCaptureStartupError(session,"Android закрыл выбор экрана без результата. Запустите профиль повторно","RESULT_MISSING");
                finishConsent();
            }
        },3000);
    }

    @Override protected void onDestroy(){callbacks.removeCallbacksAndMessages(null);super.onDestroy();}

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != REQUEST_CAPTURE || resultHandled) return;
        resultHandled=true;callbacks.removeCallbacksAndMessages(null);
        if (result != RESULT_OK || data == null) { denied("Захват экрана отменён"); return; }
        PilotService pilot=PilotService.instance;
        if(pilot==null||!pilot.onCaptureConsentGranted(session)){finishConsent();return;}
        try {
            Intent service = new Intent(this, JumperCaptureService.class)
                    .putExtra(JumperCaptureService.EXTRA_SESSION, session)
                    .putExtra(JumperCaptureService.EXTRA_RESULT, result)
                    .putExtra(JumperCaptureService.EXTRA_DATA, data);
            startForegroundService(service);
        } catch (RuntimeException error) { if(pilot!=null)pilot.onCaptureStartupError(session,"Не удалось запустить службу захвата",error.getClass().getSimpleName()); finishConsent(); return; }
        // Wait for the service handshake and a resumed Activity before removing its task.
        // Keep the foreground-start handoff alive until its readiness or bounded failure.
        if(resumed)callbacks.post(this::awaitService);
    }

    private void awaitService(){
        if(!resumed||isFinishing()||isDestroyed())return;
        PilotService pilot=PilotService.instance;
        if(pilot==null||pilot.captureConsentMayClose(session)){finishConsent();return;}
        // PilotService owns the bounded SERVICE timeout; STOP also releases this Activity.
        callbacks.postDelayed(this::awaitService,100);
    }

    /** The consent UI owns a transient task, so closing it reveals the existing game task. */
    private void finishConsent() {
        PilotService pilot=PilotService.instance;
        if(pilot!=null)pilot.captureConsentUiClosed(session);
        if (isTaskRoot()) finishAndRemoveTask();
        else finish();
    }

    private void denied(String reason) {
        PilotService service = PilotService.instance;
        if (service != null) service.onJumperCaptureDenied(session,reason);
        finishConsent();
    }
}
