package org.localpilot.android;
import android.app.*;import android.os.*;import android.view.*;import android.graphics.*;import java.util.*;
/** Visible test game. Autoplayer has no direct board accessor; uses screenshot+gesture. */
public class Game2048Activity extends Activity {
 @Override public void onCreate(Bundle state){super.onCreate(state);setContentView(new Field());}
 private final class Field extends View {
  final Paint paint=new Paint(3);final Random random=new Random();Board2048 board=new Board2048(new int[16]);int score;float sx,sy;RectF area=new RectF();Insets safe=Insets.NONE;
  final BoardAccessibility boardAccessibility=new BoardAccessibility(this,()->{Rect r=new Rect();area.round(r);return r;},"LOCALPILOT_BOARD_2048");
  Field(){super(Game2048Activity.this);spawn();spawn();setContentDescription("LocalPilot 2048: поле, управление свайпом");setOnApplyWindowInsetsListener((view,insets)->{safe=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());invalidate();return insets;});}
  void spawn(){int count=0;for(int x:board.cells)if(x==0)count++;if(count==0)return;int pick=random.nextInt(count);int[] c=board.cells.clone();for(int i=0;i<16;i++)if(c[i]==0&&pick--==0){c[i]=random.nextInt(10)==0?4:2;break;}board=new Board2048(c);}
  void text(Canvas canvas,String t,float x,float y,int color,float size){paint.setColor(color);paint.setTextSize(size);canvas.drawText(t,x,y,paint);}
  @Override public android.view.accessibility.AccessibilityNodeProvider getAccessibilityNodeProvider(){return boardAccessibility;}
  @Override protected void onDraw(Canvas canvas){canvas.drawColor(0xfffaf8ef);float w=getWidth()-safe.left-safe.right,h=getHeight()-safe.top-safe.bottom,side=Math.min(w*.94f,h*.56f);area.set(safe.left+(w-side)/2,safe.top+h-side-35,safe.left+(w+side)/2,safe.top+h-35);boardAccessibility.onBoardDrawn();text(canvas,"LocalPilot · 2048",safe.left+20,safe.top+55,0xff776e65,38);text(canvas,"Счёт: "+score+" · максимум: "+board.max(),safe.left+20,safe.top+105,0xff776e65,28);text(canvas,"Панель: Окно → Игра → 2048 → Старт",safe.left+20,safe.top+145,0xff776e65,23);paint.setColor(0xffbbada0);canvas.drawRect(area,paint);float cell=side/4;for(int i=0;i<16;i++){int n=board.cells[i],exp=n==0?0:Integer.numberOfTrailingZeros(n);float x=area.left+(i%4)*cell,y=area.top+(i/4)*cell;paint.setColor(0xff000000|Board2048.PALETTE[Math.min(exp,11)]);canvas.drawRoundRect(x+6,y+6,x+cell-6,y+cell-6,8,8,paint);if(n>0){paint.setColor(n<=4?0xff776e65:0xfff9f6f2);paint.setTextSize(cell*.27f);String t=""+n;canvas.drawText(t,x+(cell-paint.measureText(t))/2,y+cell*.60f,paint);}}}
  @Override public boolean onTouchEvent(MotionEvent e){if(e.getAction()==MotionEvent.ACTION_DOWN){sx=e.getX();sy=e.getY();return true;}if(e.getAction()==MotionEvent.ACTION_UP){float dx=e.getX()-sx,dy=e.getY()-sy;if(board.max()<2048&&area.contains(sx,sy)&&Math.max(Math.abs(dx),Math.abs(dy))>35){int d=Math.abs(dx)>Math.abs(dy)?(dx<0?Board2048.LEFT:Board2048.RIGHT):(dy<0?Board2048.UP:Board2048.DOWN);Board2048.Move m=board.move(d);if(!board.same(m.board)){board=m.board;score+=m.reward;spawn();invalidate();}}performClick();return true;}return true;}
  @Override public boolean performClick(){super.performClick();return true;}
 }
}
