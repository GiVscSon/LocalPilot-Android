package org.localpilot.android;
import android.app.Activity;
import android.os.Bundle;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

/** Isolated test-APK surface, never shipped in the user APK. Contains no user data. */
public final class CaptureFixtureActivity extends Activity {
 @Override public void onCreate(Bundle state){super.onCreate(state);setContentView(new Surface());}
 private final class Surface extends View {
  private final Paint paint=new Paint(3);private int tick;
  Surface(){super(CaptureFixtureActivity.this);setContentDescription("LOCALPILOT_CAPTURE_FIXTURE");}
  @Override protected void onDraw(Canvas canvas){
   canvas.drawColor(0xff183747);paint.setColor(Color.WHITE);paint.setTextSize(40);
   canvas.drawText("LocalPilot capture acceptance",24,150,paint);
   paint.setColor(0xffa477cc);canvas.drawCircle(getWidth()*.5f+(tick++%40),getHeight()*.5f,45,paint);
   if(isAttachedToWindow())postInvalidateDelayed(100);
  }
 }
}
