package org.localpilot.android.web;
import android.graphics.Bitmap;
public final class WebFrames {
 public static float[] features(Bitmap source,float[] previous){
  Bitmap small=Bitmap.createScaledBitmap(source,8,8,true);float[] out=new float[WebLesson.FEATURES];
  for(int y=0;y<8;y++)for(int x=0;x<8;x++){
   int at=y*8+x,p=small.getPixel(x,y);out[at*3]=(p>>16&255)/255f;out[at*3+1]=(p>>8&255)/255f;out[at*3+2]=(p&255)/255f;
   if(previous!=null)out[192+at]=(out[at*3]+out[at*3+1]+out[at*3+2]-previous[at*3]-previous[at*3+1]-previous[at*3+2])/3;
  }
  if(small!=source)small.recycle();return out;
 }
}
