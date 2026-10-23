package org.localpilot.android;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.WindowManager;

import java.nio.ByteBuffer;

/** Foreground MediaProjection session. Frames are delivered in RAM and immediately closed. */
public final class JumperCaptureService extends Service {
    static final String EXTRA_SESSION = "capture_session";
    private long session;
    static final String EXTRA_RESULT = "projection_result";
    static final String EXTRA_DATA = "projection_data";
    private static final String EXTRA_ACTION = "projection_action";
    private static final String ACTION_STOP = "org.localpilot.android.STOP_JUMPER_CAPTURE";
    private static final String CHANNEL = "jumper_capture";
    private static final int NOTIFICATION_ID = 8032;
    private HandlerThread thread;
    private Handler worker;
    private MediaProjection projection;
    private VirtualDisplay display;
    private ImageReader reader;
    private volatile boolean closing;
    private long lastFrameMs;
    private int width,height,density,screenWidth,screenHeight;
    private int[] argbBuffer;

    @Override public void onCreate() {
        super.onCreate();
        thread = new HandlerThread("LocalPilot-Arcade-Capture");
        thread.start();
        worker = new Handler(thread.getLooper());
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            if(intent.getLongExtra(EXTRA_SESSION,0)!=session){if(projection==null)stopSelf();return START_NOT_STICKY;}
            PilotService pilot = PilotService.instance;
            closing = true;
            if (pilot != null) pilot.stopJumperFromNotification(session);
            stopSelf();
            return START_NOT_STICKY;
        }
        long requested=intent==null?0:intent.getLongExtra(EXTRA_SESSION,0);
        PilotService owner=PilotService.instance;
        if(owner==null||!owner.captureSessionActive(requested)){if(projection==null)stopSelf();return START_NOT_STICKY;}
        if (projection != null) { return START_NOT_STICKY; }
        session=requested;
        if (intent == null) { fail("Нет разрешения Android на захват"); return START_NOT_STICKY; }
        int result = intent.getIntExtra(EXTRA_RESULT, 0);
        Intent data = parcelableIntent(intent, EXTRA_DATA);
        if (result != android.app.Activity.RESULT_OK || data == null) { fail("Нет разрешения Android на захват"); return START_NOT_STICKY; }
        try {
            startForeground(NOTIFICATION_ID, notification(), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
            MediaProjectionManager manager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            projection = manager.getMediaProjection(result, data);
            if (projection == null) throw new IllegalStateException("projection unavailable");
            projection.registerCallback(new MediaProjection.Callback() {
                @Override public void onStop() {
                    if (!closing) {
                        closing = true;
                        PilotService pilot = PilotService.instance;
                        if (pilot != null) pilot.onJumperCaptureLost(session,"Android завершил захват экрана");
                    }
                    stopSelf();
                }
            }, worker);
            createDisplay();
            PilotService pilot = PilotService.instance;
            if (pilot != null) pilot.onJumperCaptureReady(session,screenWidth,screenHeight);
            // Publish readiness before the worker can hand over the first frame.
            reader.setOnImageAvailableListener(this::onImage, worker);
            worker.post(() -> { ImageReader current=reader; if(!closing&&current!=null)onImage(current); });
            return START_NOT_STICKY;
        } catch (RuntimeException|OutOfMemoryError error) {
            fail("Не удалось включить поток экрана",error.getClass().getSimpleName());
            return START_NOT_STICKY;
        }
    }

    private static Intent parcelableIntent(Intent from, String key) {
        if (Build.VERSION.SDK_INT >= 33) return from.getParcelableExtra(key, Intent.class);
        //noinspection deprecation
        return from.getParcelableExtra(key);
    }

    private void createDisplay() {
        DisplayMetrics metrics = new DisplayMetrics();
        Display real = ((WindowManager) getSystemService(WINDOW_SERVICE)).getDefaultDisplay();
        real.getRealMetrics(metrics);
        screenWidth=metrics.widthPixels;screenHeight=metrics.heightPixels;
        CaptureResolution size=new CaptureResolution(screenWidth,screenHeight);
        width=size.width;height=size.height;
        CrashDiagnostics.phase("CAPTURE_SURFACE_"+width+"x"+height);
        argbBuffer=new int[width*height];
        density = metrics.densityDpi;
        if (width < 120 || height < 180 || (long) width * height > 8_000_000) throw new IllegalStateException("display bounds");
        reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2);
        display = projection.createVirtualDisplay("LocalPilot Arcade", width, height, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader.getSurface(), null, worker);
        if (display == null) throw new IllegalStateException("virtual display unavailable");
    }

    private synchronized void onImage(ImageReader source) {
        // onDestroy must not close native plane storage while this callback reads it.
        if (closing || source != reader) return;
        Image image = null;
        Bitmap frame = null;
        try {
            image = source.acquireLatestImage();
            if (image == null) return;
            long now = SystemClock.elapsedRealtime();
            PilotService pilot = PilotService.instance;
            if (now - lastFrameMs < 80 || pilot==null || !pilot.wantsJumperFrame(session)) return;
            if (image.getWidth() != width || image.getHeight() != height) throw new IllegalStateException("display changed");
            Image.Plane plane = image.getPlanes()[0];
            ByteBuffer bytes = plane.getBuffer();
            int[] argb=argbBuffer;
            RgbaFrameDecoder.decode(bytes, width, height, plane.getPixelStride(), plane.getRowStride(), argb);
            frame = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            frame.setPixels(argb, 0, width, 0, 0, width, height);
            lastFrameMs = now;
            Bitmap owned = frame;
            frame = null;
            pilot.onJumperFrame(session,owned, now);
        } catch (RuntimeException|OutOfMemoryError error) {
            closing = true;
            PilotService pilot = PilotService.instance;
            if (pilot != null) pilot.onCaptureStartupError(session,"Ошибка кадра захвата",error.getClass().getSimpleName());
            stopSelf();
        } finally {
            if (frame != null) frame.recycle();
            if (image != null) image.close();
        }
    }

    private Notification notification() {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(new NotificationChannel(CHANNEL,
                "Захват экрана LocalPilot", NotificationManager.IMPORTANCE_LOW));
        Intent stop = new Intent(this, JumperCaptureService.class).setAction(ACTION_STOP).putExtra(EXTRA_SESSION,session);
        PendingIntent pending = PendingIntent.getService(this, 8032, stop,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = new Notification.Builder(this, CHANNEL);
        return builder.setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle("LocalPilot · "+(PilotService.instance==null?"игровой профиль":PilotService.instance.captureProfileLabel()))
                .setContentText("Захват экрана активен · нажмите STOP для остановки")
                .setOngoing(true).setCategory(Notification.CATEGORY_SERVICE)
                .addAction(android.R.drawable.ic_media_pause, "STOP", pending).build();
    }

    private void fail(String message) { fail(message,"CAPTURE_SETUP"); }

    private void fail(String message,String type) {
        closing = true;
        PilotService pilot = PilotService.instance;
        if (pilot != null) pilot.onCaptureStartupError(session,message,type);
        stopSelf();
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public synchronized void onDestroy() {
        closing = true;
        argbBuffer=null;
        if (reader != null) { reader.setOnImageAvailableListener(null, null); reader.close(); reader = null; }
        if (display != null) { display.release(); display = null; }
        if (projection != null) { projection.stop(); projection = null; }
        if (thread != null) { thread.quitSafely(); thread = null; }
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }
}
