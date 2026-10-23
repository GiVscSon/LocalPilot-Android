package org.localpilot.android;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

/** Touch-through is intentionally disabled while calibrating; taps select only image samples. */
@SuppressLint("ViewConstructor")
final class JumperCalibrationOverlay extends View {
    interface Listener { void completed(int player, int platform, int hazard); void cancelled(); }
    interface Samples { void completed(int[] colors,float[] x,float[] y); void cancelled(); }
    private static final String[] PROMPTS={"1/3 · нажмите на Ари", "2/3 · нажмите на оранжевую платформу",
            "3/3 · нажмите на красный шип · STOP: верхний правый угол"};
    private final Bitmap frame;
    private final Samples listener;
    private final String[] prompts;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final int[] colors=new int[3];
    private final float[] sampleX=new float[3],sampleY=new float[3];
    private int step;
    private RectF imageBounds=new RectF();

    JumperCalibrationOverlay(Context context,Bitmap frame,Listener listener){
        this(context,frame,PROMPTS,new Samples(){public void completed(int[] c,float[] x,float[] y){listener.completed(c[0],c[1],c[2]);}public void cancelled(){listener.cancelled();}});
    }
    JumperCalibrationOverlay(Context context,Bitmap frame,String[] prompts,Samples listener){
        super(context);if(prompts.length!=3)throw new IllegalArgumentException("three prompts required");this.frame=frame;this.listener=listener;this.prompts=prompts.clone();setLayerType(View.LAYER_TYPE_HARDWARE,null);
    }

    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);
        if(frame==null||frame.isRecycled())return;
        float scale=Math.min(getWidth()/(float)frame.getWidth(),getHeight()/(float)frame.getHeight());
        float w=frame.getWidth()*scale,h=frame.getHeight()*scale;
        imageBounds.set((getWidth()-w)/2f,(getHeight()-h)/2f,(getWidth()+w)/2f,(getHeight()+h)/2f);
        canvas.drawBitmap(frame,null,imageBounds,paint);
        paint.setColor(0x99000000);canvas.drawRect(0,0,getWidth(),Math.max(92,getHeight()*.09f),paint);
        paint.setColor(Color.WHITE);paint.setTextSize(Math.max(18,getWidth()*.034f));
        canvas.drawText(prompts[Math.min(step,2)],16,Math.max(36,getHeight()*.047f),paint);
        paint.setColor(0xffff4444);paint.setStyle(Paint.Style.FILL);
        float bx=getWidth()-64,by=46;canvas.drawCircle(bx,by,26,paint);
        paint.setColor(Color.WHITE);paint.setTextSize(15);canvas.drawText("STOP",bx-20,by+5,paint);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(4);
        for(int i=0;i<step;i++){paint.setColor(i==0?0xffffff00:i==1?0xffff9a00:0xffff3030);
            float x=imageBounds.left+sampleX[i]*imageBounds.width(),y=imageBounds.top+sampleY[i]*imageBounds.height();canvas.drawCircle(x,y,16,paint);}
        paint.setStyle(Paint.Style.FILL);
    }

    @Override public boolean onTouchEvent(MotionEvent event){
        if(event.getAction()!=MotionEvent.ACTION_UP)return true;
        performClick();
        float x=event.getX(),y=event.getY();
        if(x>getWidth()-120&&y<100){listener.cancelled();return true;}
        if(!imageBounds.contains(x,y)||step>=3||frame.isRecycled())return true;
        float nx=(x-imageBounds.left)/imageBounds.width(),ny=(y-imageBounds.top)/imageBounds.height();
        int px=Math.max(0,Math.min(frame.getWidth()-1,Math.round(nx*frame.getWidth())));
        int py=Math.max(0,Math.min(frame.getHeight()-1,Math.round(ny*frame.getHeight())));
        colors[step]=sampleMedian(px,py)&0x00ffffff;sampleX[step]=nx;sampleY[step]=ny;step++;
        if(step==3)listener.completed(colors.clone(),sampleX.clone(),sampleY.clone());else invalidate();
        return true;
    }

    @Override public boolean performClick(){super.performClick();return true;}

    private int sampleMedian(int x,int y){
        int[] rs=new int[25],gs=new int[25],bs=new int[25];int i=0;
        for(int dy=-2;dy<=2;dy++)for(int dx=-2;dx<=2;dx++){
            int color=frame.getPixel(Math.max(0,Math.min(frame.getWidth()-1,x+dx)),Math.max(0,Math.min(frame.getHeight()-1,y+dy)));
            rs[i]=color>>>16&255;gs[i]=color>>>8&255;bs[i]=color&255;i++;
        }
        java.util.Arrays.sort(rs);java.util.Arrays.sort(gs);java.util.Arrays.sort(bs);
        return Color.rgb(rs[12],gs[12],bs[12]);
    }

    @Override protected void onDetachedFromWindow(){if(!frame.isRecycled())frame.recycle();super.onDetachedFromWindow();}
}
