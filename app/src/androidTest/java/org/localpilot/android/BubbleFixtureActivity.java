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
 private boolean ricochet;
 @Override public void onCreate(Bundle state){
  super.onCreate(state);
  ricochet=getIntent().getBooleanExtra("ricochet",false);
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
  private final int[][] rows=ricochet
   ?new int[][]{{3,3,4,3,3,2},{2,3,3,1,2},{0,0,1,3,4,4},{0,2,2,1,2}}
   :new int[][]{{1,1,2,3,4,4},{0,0,4,1,2}};
  private final int[] visibleCells=initialCells();
  private boolean aiming,accepted;private float fromX,fromY;
  private String traceMessage="no release";
  Surface(){super(BubbleFixtureActivity.this);setContentDescription("LOCALPILOT_BUBBLE_ARENA");}
  private float step(){return getWidth()/(ricochet?6.4f:7f);}
  private float x0(){return (ricochet?.7f:.8f)*step();}
  private float launcherY(){return getHeight()*.88f;}
  private int[] initialCells(){int[] cells=new int[36];for(int row=0;row<rows.length;row++)
   System.arraycopy(rows[row],0,cells,row*6,rows[row].length);return cells;}
  private int visibleCount(){int count=0;for(int color:visibleCells)if(color>0)count++;return count;}
  @Override protected void onDraw(Canvas canvas){
   canvas.drawColor(colors[0]);float d=step(),r=d*.46f;
   for(int row=0;row<rows.length;row++)for(int col=0;col<rows[row].length;col++){
    int color=ricochet?visibleCells[row*6+col]:rows[row][col];
    if(color==0||accepted&&!ricochet&&row==0&&col<2)continue;
    paint.setColor(colors[color]);canvas.drawCircle(x0()+(col+(row%2)*.5f)*d,(1.5f+row*.8660254f)*d,r,paint);
   }
   paint.setColor(colors[accepted?(ricochet?3:2):(ricochet?2:1)]);canvas.drawCircle(getWidth()*.5f,launcherY(),r,paint);
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
    boolean release=aiming&&!accepted&&(ricochet?reflectedImpact(event.getX(),event.getY()):event.getY()<fromY-step()*2&&event.getX()<getWidth()*.42f);
    aiming=false;
    if(release){accepted=true;status.setContentDescription("LOCALPILOT_BUBBLE_SHOTS_1");invalidate();}
    status.setText("Bubble acceptance · accepted="+(accepted?1:0)+(ricochet?" "+traceMessage
     +" from="+Math.round(fromX)+","+Math.round(fromY)+" to="+Math.round(event.getX())+","+Math.round(event.getY())
     +" size="+getWidth()+"x"+getHeight()+" cells="+visibleCount()+" ms="+(event.getEventTime()-event.getDownTime()):""));
    return true;
   }
   return true;
  }
  /** Independent small-step physics: no production board, policy, ray resolver or predicted cells. */
  private boolean reflectedImpact(float endX,float endY){
   float d=step(),dx=endX-fromX,dy=endY-fromY,length=(float)Math.hypot(dx,dy);
   if(length<d*.5f||dy>=-d*.5f){traceMessage="no upward aim";return false;}
   dx/=length;dy/=length;float x=fromX,y=fromY,left=x0(),right=left+5*d;
   int reflections=0;
   for(int tick=0;tick<4096;tick++){
    x+=dx*d*.01f;y+=dy*d*.01f;
    if(x<left){x=2*left-x;dx=-dx;reflections++;}
    else if(x>right){x=2*right-x;dx=-dx;reflections++;}
    if(reflections>1||y<d*1.5f){traceMessage="left board refs="+reflections;return false;}
    int hit=-1;float hitDistance=Float.POSITIVE_INFINITY;
    for(int row=0;row<rows.length;row++)for(int col=0;col<rows[row].length;col++)if(rows[row][col]>0){
     float distance=(float)Math.hypot(x-(left+(col+(row%2)*.5f)*d),y-(1.5f+row*.8660254f)*d);
     if(distance<d&&distance<hitDistance){hit=row*6+col;hitDistance=distance;}
    }
    if(hit<0)continue;
    int nearest=-1;float distance=Float.POSITIVE_INFINITY;
    float hx=left+(hit%6+(hit/6%2)*.5f)*d,hy=(1.5f+hit/6*.8660254f)*d;
    for(int row=0;row<6;row++)for(int col=0;col<6-row%2;col++){
     if(row<rows.length&&col<rows[row].length&&rows[row][col]>0)continue;
     float cx=left+(col+(row%2)*.5f)*d,cy=(1.5f+row*.8660254f)*d;
     if(Math.hypot(cx-hx,cy-hy)>d*1.03f)continue;
     float at=(float)Math.hypot(x-cx,y-cy);
     if(at<distance){distance=at;nearest=row*6+col;}
    }
    // Resolve the actual contact using this fixture's independent game rules.
    // A shorter arena can legitimately expose a different orange match.
    traceMessage="refs="+reflections+" hit="+hit+" land="+nearest+" distance_milli="+Math.round(distance/d*1000);
    return reflections==1&&nearest>=0&&distance<d*.46f&&resolveNormalMatch(nearest);
   }
   traceMessage="trajectory limit";return false;
  }
  private boolean adjacent(int a,int b){
   int rowA=a/6,rowB=b/6,columnA=a%6,columnB=b%6;
   return rowA==rowB?Math.abs(columnA-columnB)==1:Math.abs(rowA-rowB)==1
    &&Math.abs(2*columnA+rowA%2-(2*columnB+rowB%2))==1;
  }
  private boolean resolveNormalMatch(int landing){
   int[] next=visibleCells.clone(),queue=new int[next.length];boolean[] seen=new boolean[next.length];
   next[landing]=2;queue[0]=landing;seen[landing]=true;int head=0,tail=1;
   while(head<tail){int at=queue[head++];for(int i=0;i<next.length;i++)
    if(next[i]==2&&!seen[i]&&adjacent(at,i)){seen[i]=true;queue[tail++]=i;}}
   if(tail<3)return false;
   int matched=tail;for(int i=0;i<tail;i++)next[queue[i]]=0;
   java.util.Arrays.fill(seen,false);head=tail=0;
   for(int i=0;i<6;i++)if(next[i]>0){seen[i]=true;queue[tail++]=i;}
   while(head<tail){int at=queue[head++];for(int i=0;i<next.length;i++)
    if(next[i]>0&&!seen[i]&&adjacent(at,i)){seen[i]=true;queue[tail++]=i;}}
   int dropped=0;for(int i=0;i<next.length;i++)if(next[i]>0&&!seen[i]){next[i]=0;dropped++;}
   System.arraycopy(next,0,visibleCells,0,next.length);
   traceMessage+=" matched="+matched+" dropped="+dropped;return true;
  }
 }
}
