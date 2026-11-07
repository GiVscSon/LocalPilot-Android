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

/** Independent synthetic drag-and-merge game. It never calls the production Hexa engine or its verifier. */
public final class HexaFixtureActivity extends Activity {
 private TextView status;
 @Override public void onCreate(Bundle state){
  super.onCreate(state);FrameLayout root=new FrameLayout(this);root.setBackgroundColor(0xff202834);
  status=new TextView(this);status.setTextColor(Color.WHITE);status.setTextSize(16);status.setPadding(dp(12),dp(12),dp(12),0);
  status.setText("Hexa acceptance · placed=0");status.setContentDescription("LOCALPILOT_HEXA_PLACEMENTS_0");
  root.addView(status,new FrameLayout.LayoutParams(-1,dp(80)));
  FrameLayout.LayoutParams area=new FrameLayout.LayoutParams(-1,-1);area.topMargin=dp(80);root.addView(new Arena(),area);setContentView(root);
 }
 private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
 private final class Arena extends FrameLayout {
  private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
  private final int[] colors={0xffd0d4dc,0xffe52c32,0xff4ab63b,0xfff0cf20,0xff385adf};
  private final int[] cells={1,0,2,0,3,0,0,0,0,0,0,-2,4,0,0,0,0,0,0,0,0,0,0,-2};
  private final int[] heights=new int[24],hands={1,2,3},queue=new int[24];
  private final boolean[] connected=new boolean[24];
  private final View boardMarker;
  private int left,top,right,bottom,from=-1,placed;
  private float fromX,fromY;
  Arena(){
   super(HexaFixtureActivity.this);setWillNotDraw(false);setContentDescription("LOCALPILOT_HEXA_ARENA");
   boardMarker=new View(HexaFixtureActivity.this);boardMarker.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);boardMarker.setContentDescription("LOCALPILOT_HEXA_BOARD");
   addView(boardMarker,new FrameLayout.LayoutParams(1,1));for(int i=0;i<24;i++)heights[i]=cells[i]>0?1:0;
  }
  @Override protected void onSizeChanged(int width,int height,int oldWidth,int oldHeight){
   super.onSizeChanged(width,height,oldWidth,oldHeight);left=Math.round(width*.06f);right=Math.round(width*.94f);top=Math.round(height*.05f);bottom=Math.round(height*.70f);
   FrameLayout.LayoutParams bounds=new FrameLayout.LayoutParams(right-left,bottom-top);bounds.leftMargin=left;bounds.topMargin=top;boardMarker.setLayoutParams(bounds);
  }
  private float cw(){return (right-left)/6f;}
  private float ch(){return (bottom-top)/4f;}
  private float cx(int i){return left+(i%6+.5f+(i/6%2)*.5f)*cw();}
  private float cy(int i){return top+(i/6+.5f)*ch();}
  private float handX(int s){return left+(.2f+.3f*s)*(right-left);}
  private float handY(){return bottom+.12f*(bottom-top);}
  @Override protected void onDraw(Canvas canvas){
   super.onDraw(canvas);canvas.drawColor(0xff202834);float radius=Math.min(cw(),ch())*.41f;
   for(int i=0;i<24;i++)if(cells[i]>=0)drawStack(canvas,cx(i),cy(i),radius,cells[i]);
   for(int s=0;s<3;s++)drawStack(canvas,handX(s),handY(),radius,hands[s]);
  }
  private void drawStack(Canvas canvas,float x,float y,float radius,int color){
   if(color==0){paint.setColor(0xff46505d);canvas.drawCircle(x,y,radius+3,paint);paint.setColor(colors[0]);canvas.drawCircle(x,y,radius-3,paint);}
   else{paint.setColor(colors[color]);canvas.drawCircle(x,y,radius*.95f,paint);}
  }
  @Override public boolean onTouchEvent(MotionEvent event){
   if(event.getPointerCount()!=1){from=-1;return true;}
   if(event.getActionMasked()==MotionEvent.ACTION_DOWN){from=-1;fromX=event.getX();fromY=event.getY();for(int s=0;s<3;s++)if(hands[s]>0&&Math.hypot(fromX-handX(s),fromY-handY())<cw()*.35f){from=s;break;}return true;}
   if(event.getActionMasked()==MotionEvent.ACTION_CANCEL){from=-1;return true;}
   if(event.getActionMasked()==MotionEvent.ACTION_UP){
    long duration=event.getEventTime()-event.getDownTime();int target=-1;
    if(from>=0&&duration>=300&&duration<=1000&&Math.hypot(event.getX()-fromX,event.getY()-fromY)>cw()*.5f)
     for(int i=0;i<24;i++)if(cells[i]==0&&Math.hypot(event.getX()-cx(i),event.getY()-cy(i))<cw()*.30f){target=i;break;}
    if(target>=0){int color=hands[from];cells[target]=color;heights[target]=1;merge(target,color);hands[from]=color%4+1;placed++;status.setContentDescription("LOCALPILOT_HEXA_PLACEMENTS_"+placed);status.setText("Hexa acceptance · placed="+placed+" hand="+from+" target="+target+" color="+color+" duration="+duration);invalidate();}
    from=-1;return true;
   }
   return true;
  }
  private boolean adjacent(int a,int b){int ar=a/6,br=b/6,ac=a%6,bc=b%6;return ar==br?Math.abs(ac-bc)==1:Math.abs(ar-br)==1&&Math.abs(2*ac+ar%2-(2*bc+br%2))==1;}
  private void merge(int target,int color){
   java.util.Arrays.fill(connected,false);queue[0]=target;connected[target]=true;int head=0,tail=1;
   while(head<tail){int at=queue[head++];for(int i=0;i<24;i++)if(!connected[i]&&cells[i]==color&&adjacent(at,i)){connected[i]=true;queue[tail++]=i;}}
   for(int i=0;i<tail;i++){int at=queue[i];if(at==target)continue;heights[target]+=heights[at];heights[at]=0;cells[at]=0;}
  }
 }
}
