package org.localpilot.android;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;

/** Test-companion-only, deterministic compositor surface. No user content. */
public final class CaptureAcceptanceFixtureActivity extends Activity {
 @Override public void onCreate(Bundle state){
  super.onCreate(state);
  if(getIntent().getBooleanExtra("secure",false))
   getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
  getWindow().setDecorFitsSystemWindows(false);
  getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
  getWindow().getAttributes().layoutInDisplayCutoutMode=
   WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
  setContentView(new Surface());hideSystemBars();
 }
 @Override public void onWindowFocusChanged(boolean focused){
  super.onWindowFocusChanged(focused);if(focused)hideSystemBars();
 }
 private void hideSystemBars(){
  getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
   |View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
   |View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
  if(getWindow().getInsetsController()!=null)
   getWindow().getInsetsController().hide(WindowInsets.Type.systemBars());
 }
 private final class Surface extends View {
  final Paint paint=new Paint();int touches;
  Surface(){super(CaptureAcceptanceFixtureActivity.this);
   setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);setClickable(true);updateMarker();}
  void updateMarker(){setContentDescription("LOCALPILOT_CAPTURE_ACCEPTANCE secure="
   +getIntent().getBooleanExtra("secure",false)+" touches="+touches);}
  @Override public void onDraw(Canvas canvas){
   int[] colors={0xffdf3b36,0xff38b76b,0xff335dde,0xffe8ba34};
   for(int i=0;i<4;i++){
    paint.setColor(colors[i]);float x=(i%2)*getWidth()/2f,y=(i/2)*getHeight()/2f;
    canvas.drawRect(x,y,x+getWidth()/2f,y+getHeight()/2f,paint);
   }
  }
  @Override public boolean onTouchEvent(MotionEvent event){
   if(event.getActionMasked()==MotionEvent.ACTION_DOWN){touches++;updateMarker();}
   return true;
  }
 }
}
