package org.localpilot.android;

import android.content.Context;
import android.graphics.*;
import android.view.*;
import android.widget.*;

/** Native region editor. Its visible grid is a candidate, confirmed only with Done. */
final class GameAreaOverlay extends FrameLayout {
 interface Listener{void selected(Rect area);void cancelled();}
 private final GameViewport.Box window;
 private final Listener listener;
 private final Paint paint=new Paint(3);
 private final RectF box=new RectF();
 private final int[] origin=new int[2];
 private final int rows,cols;
 private final TextView message;
 private final Button confirm;
 private GameViewport.Box candidate;
 private float sx,sy;
 private boolean dragging,finished;

 GameAreaOverlay(Context c,Rect window,Listener listener){this(c,window,4,4,listener);}
 GameAreaOverlay(Context c,Rect bounds,int rows,int cols,Listener listener){
  super(c);if(bounds==null||listener==null)throw new IllegalArgumentException("region editor");
  this.window=new GameViewport.Box(bounds.left,bounds.top,bounds.right,bounds.bottom);
  candidate=RegionPickerGeometry.initial(window,rows,cols);
  this.rows=rows;this.cols=cols;this.listener=listener;
  setWillNotDraw(false);setContentDescription("Выделение области игры "+rows+" на "+cols);
  LinearLayout header=new LinearLayout(c);header.setOrientation(LinearLayout.VERTICAL);header.setBackgroundColor(0xF0202830);
  header.setPadding(0,dp(24),0,0);
  setOnApplyWindowInsetsListener((view,insets)->{getLocationOnScreen(origin);Insets safe=insets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());header.setPadding(Math.max(0,safe.left-origin[0]),Math.max(dp(24),safe.top-origin[1]),Math.max(0,safe.right),0);return insets;});
  LinearLayout buttons=new LinearLayout(c);
  Button cancel=new Button(c);cancel.setText("STOP · отмена");cancel.setTextSize(12);cancel.setOnClickListener(v->cancel());
  buttons.addView(cancel,new LinearLayout.LayoutParams(0,dp(44),1));
  confirm=new Button(c);confirm.setText("Готово");confirm.setTextSize(12);confirm.setOnClickListener(v->confirm());
  buttons.addView(confirm,new LinearLayout.LayoutParams(0,dp(44),1));header.addView(buttons);
  message=new TextView(c);message.setTextColor(Color.WHITE);message.setTextSize(12);message.setPadding(dp(8),0,dp(8),dp(4));
  message.setText("Обведите клетки "+rows+"×"+cols+", затем нажмите «Готово»");header.addView(message);
  addView(header,new FrameLayout.LayoutParams(-1,-2,Gravity.TOP));
 }
 private int dp(int n){return Math.max(1,(int)(n*getResources().getDisplayMetrics().density));}
 private void cancel(){if(!finished){finished=true;listener.cancelled();}}
 private void confirm(){
  if(finished||dragging||candidate==null)return;
  GameViewport.Box selected=GameViewport.selection(window,candidate.left(),candidate.top(),candidate.right(),candidate.bottom());
  finished=true;listener.selected(new Rect(selected.left(),selected.top(),selected.right(),selected.bottom()));
 }
 private void drawBox(Canvas canvas,GameViewport.Box screen,int color){
  GameViewport.Box local=RegionPickerGeometry.local(screen,origin[0],origin[1]);
  box.set(local.left(),local.top(),local.right(),local.bottom());paint.setColor(color);canvas.drawRect(box,paint);
 }
 @Override protected void onDraw(Canvas canvas){
  super.onDraw(canvas);canvas.drawColor(0x55303030);getLocationOnScreen(origin);
  paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(2));drawBox(canvas,window,Color.CYAN);
  if(candidate!=null){
   drawBox(canvas,candidate,Color.GREEN);paint.setColor(0xEEFFFFFF);paint.setStrokeWidth(dp(1));
   for(int i=1;i<cols;i++)canvas.drawLine(box.left+box.width()*i/cols,box.top,box.left+box.width()*i/cols,box.bottom,paint);
   for(int i=1;i<rows;i++)canvas.drawLine(box.left,box.top+box.height()*i/rows,box.right,box.top+box.height()*i/rows,paint);
  }
 }
 @Override public boolean dispatchTouchEvent(MotionEvent event){
  if(finished)return true;
  if(event.getPointerCount()>1||event.getActionMasked()==MotionEvent.ACTION_CANCEL){cancel();return true;}
  return super.dispatchTouchEvent(event);
 }
 @Override public boolean onTouchEvent(MotionEvent event){
  if(finished)return true;
  getLocationOnScreen(origin);
  float x=event.getX(),y=event.getY();
  if(event.getActionMasked()==MotionEvent.ACTION_DOWN){
   if(x+origin[0]<window.left()||x+origin[0]>window.right()||y+origin[1]<window.top()||y+origin[1]>window.bottom())return true;
   sx=x;sy=y;dragging=true;confirm.setEnabled(false);return true;
  }
  if(dragging&&(event.getActionMasked()==MotionEvent.ACTION_MOVE||event.getActionMasked()==MotionEvent.ACTION_UP)){
   try{
    candidate=RegionPickerGeometry.selection(window,origin[0],origin[1],sx,sy,x,y);
    message.setText("Сетка "+rows+"×"+cols+" · "+candidate.width()+"×"+candidate.height()+" · «Готово» подтвердит область");
   }catch(IllegalArgumentException error){candidate=null;message.setText("Выделите поле целиком внутри голубого окна");}
   if(event.getActionMasked()==MotionEvent.ACTION_UP){dragging=false;confirm.setEnabled(candidate!=null);}
   invalidate();return true;
  }
  return true;
 }
}
