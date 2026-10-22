package org.localpilot.android;
import android.content.Context;import android.graphics.*;import android.view.*;
/** Native full-display region picker; all touches are consumed locally. */
final class GameAreaOverlay extends View {
 interface Listener{void selected(Rect area);void cancelled();}
 private final Rect window;private final Listener listener;private final Paint paint=new Paint(3);private final RectF box=new RectF();
 private float sx,sy;private boolean dragging,finished;private String message="Обведите поле 4×4 · сверху справа — отмена";
 GameAreaOverlay(Context c,Rect window,Listener listener){super(c);this.window=new Rect(window);this.listener=listener;setContentDescription("Выделение области игры");}
 private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density);}
 @Override protected void onDraw(Canvas c){c.drawColor(0x66303030);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(2));paint.setColor(Color.CYAN);c.drawRect(window,paint);if(dragging||!box.isEmpty()){paint.setColor(Color.GREEN);c.drawRect(box,paint);float cw=box.width()/4f,ch=box.height()/4f;paint.setColor(0xCCFFFFFF);paint.setStrokeWidth(dp(1));for(int i=1;i<4;i++){c.drawLine(box.left+cw*i,box.top,box.left+cw*i,box.bottom,paint);c.drawLine(box.left,box.top+ch*i,box.right,box.top+ch*i,paint);}}paint.setStyle(Paint.Style.FILL);paint.setColor(0xEE202830);c.drawRect(0,0,getWidth(),dp(64),paint);paint.setColor(Color.WHITE);paint.setTextSize(dp(12));c.drawText(message,dp(8),dp(42),paint);paint.setTextSize(dp(28));c.drawText("×",getWidth()-dp(42),dp(30),paint);}
 @Override public boolean onTouchEvent(MotionEvent e){
  if(finished)return true;
  if(e.getPointerCount()>1||e.getActionMasked()==MotionEvent.ACTION_CANCEL){finished=true;listener.cancelled();return true;}
  float x=e.getX(),y=e.getY();
  if(e.getActionMasked()==MotionEvent.ACTION_DOWN){if(y<dp(64)){finished=true;listener.cancelled();return true;}sx=x;sy=y;dragging=true;box.set(x,y,x,y);invalidate();return true;}
  if(dragging&&(e.getActionMasked()==MotionEvent.ACTION_MOVE||e.getActionMasked()==MotionEvent.ACTION_UP)){
   box.set(Math.min(sx,x),Math.min(sy,y),Math.max(sx,x),Math.max(sy,y));invalidate();
   if(e.getActionMasked()==MotionEvent.ACTION_UP){dragging=false;try{
    GameViewport.Box b=GameViewport.selection(new GameViewport.Box(window.left,window.top,window.right,window.bottom),Math.round(sx),Math.round(sy),Math.round(x),Math.round(y));
    finished=true;listener.selected(new Rect(b.left(),b.top(),b.right(),b.bottom()));
   }catch(IllegalArgumentException error){message="Выделите прямоугольник внутри голубого окна · × отмена";box.setEmpty();invalidate();}}
  }return true;
 }
}
