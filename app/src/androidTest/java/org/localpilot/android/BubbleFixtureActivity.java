package org.localpilot.android;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

/** Synthetic, independent one-shot fixture in the test APK; never a shipped game. */
public final class BubbleFixtureActivity extends Activity {
 private TextView status;
 @Override public void onCreate(Bundle state){
  super.onCreate(state);
  FrameLayout root=new FrameLayout(this);root.setBackgroundColor(0xff183747);
  status=new TextView(this);status.setTextColor(Color.WHITE);status.setTextSize(16);
  status.setPadding(dp(12),dp(100),dp(12),0);status.setText("Bubble acceptance · accepted=0");
  status.setContentDescription("LOCALPILOT_BUBBLE_SHOTS_0");
  root.addView(status,new FrameLayout.LayoutParams(-1,dp(160)));
  Surface arena=new Surface();FrameLayout.LayoutParams bounds=new FrameLayout.LayoutParams(-1,-1);
  bounds.setMargins(dp(8),dp(160),dp(8),dp(8));root.addView(arena,bounds);
  setContentView(root);
 }
 private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
 private final class Surface extends View {
  private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
  private final int[] colors={0xff87ceeb,0xffe52c32,0xfff08016,0xfff0cf20,0xff4ab63b};
  private final int[][] rows={{1,1,2,3,4,4},{0,0,4,1,2}};
  private boolean aiming,accepted;private float fromX,fromY;
  Surface(){super(BubbleFixtureActivity.this);setContentDescription("LOCALPILOT_BUBBLE_ARENA");}
  private float step(){return getWidth()/7f;}
  private float launcherY(){return getHeight()*.88f;}
  @Override protected void onDraw(Canvas canvas){
   canvas.drawColor(colors[0]);float d=step(),r=d*.46f;
   for(int row=0;row<rows.length;row++)for(int col=0;col<rows[row].length;col++){
    int color=rows[row][col];if(color==0||accepted&&row==0&&col<2)continue;
    paint.setColor(colors[color]);canvas.drawCircle((.8f+col+row*.5f)*d,(1.5f+row*.8660254f)*d,r,paint);
   }
   paint.setColor(colors[accepted?2:1]);canvas.drawCircle(getWidth()*.5f,launcherY(),r,paint);
  }
  @Override public boolean onTouchEvent(MotionEvent event){
   if(event.getPointerCount()!=1){aiming=false;return true;}
   if(event.getActionMasked()==MotionEvent.ACTION_DOWN){
    fromX=event.getX();fromY=event.getY();
    aiming=Math.hypot(fromX-getWidth()*.5f,fromY-launcherY())<step()*.6f;
    return true;
   }
   if(event.getActionMasked()==MotionEvent.ACTION_CANCEL){aiming=false;return true;}
   if(event.getActionMasked()==MotionEvent.ACTION_UP){
    // This fixture only accepts the intended ordinary red-pair maneuver.
    // It deliberately does not call the production vision, policy or resolver.
    boolean release=aiming&&!accepted&&event.getY()<fromY-step()*2&&event.getX()<getWidth()*.42f;
    aiming=false;
    if(release){accepted=true;status.setText("Bubble acceptance · accepted=1");status.setContentDescription("LOCALPILOT_BUBBLE_SHOTS_1");invalidate();}
    return true;
   }
   return true;
  }
 }
}
