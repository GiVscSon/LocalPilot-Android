package org.localpilot.android;

/** Reacquire a scene from new frames; this class never creates an input action. */
public final class SceneRecovery {
 public enum Pass {
  ORIGINAL(100,"исходные цвета"), BRIGHTER(135,"светлее"), DIMMER(80,"темнее");
  final int percent;final String label;
  Pass(int percent,String label){this.percent=percent;this.label=label;}
 }
 private Pass pass=Pass.ORIGINAL;
 private long lastFrame=-1,totalMissing;
 private int modeMisses,goodFrames;
 private boolean searching=true;
 public void reset(){pass=Pass.ORIGINAL;lastFrame=-1;totalMissing=0;modeMisses=goodFrames=0;searching=true;}
 public Pass pass(){return pass;}
 public boolean searching(){return searching;}
 /** Two distinct usable frames are required initially and after any scene loss. */
 public boolean observe(boolean usable,long timestamp){
  if(timestamp<0||timestamp<=lastFrame)return false;
  lastFrame=timestamp;
  if(!usable){
   searching=true;goodFrames=0;totalMissing=Math.min(999999,totalMissing+1);
   if(++modeMisses>=4){pass=Pass.values()[(pass.ordinal()+1)%Pass.values().length];modeMisses=0;}
   return false;
  }
  goodFrames=Math.min(2,goodFrames+1);
  if(goodFrames<2)return false;
  modeMisses=0;searching=false;return true;
 }
 public String status(){return searching?(goodFrames==1?"Сцена найдена · подтверждаю следующим кадром":"Ищу сцену · попытка "+totalMissing+" · "+pass.label):"Сцена подтверждена";}
 public String report(){return "SCENE RECOVERY: mode="+pass+" missing="+totalMissing+" consecutive="+goodFrames+" searching="+searching;}
 public static PixelBoardReader.Pixels pixels(PixelBoardReader.Pixels source,Pass pass){
  if(source==null||pass==null)throw new IllegalArgumentException("recovery pixels");
  if(pass==Pass.ORIGINAL)return source;
  return (x,y)->adjust(source.rgb(x,y),pass);
 }
 /** Uniform exposure gain preserves black and never adds or invents image objects. */
 public static int adjust(int color,Pass pass){
  if(pass==null)throw new IllegalArgumentException("recovery pass");
  int r=Math.min(255,((color>>>16&255)*pass.percent+50)/100);
  int g=Math.min(255,((color>>>8&255)*pass.percent+50)/100);
  int b=Math.min(255,((color&255)*pass.percent+50)/100);
  return color&0xff000000|r<<16|g<<8|b;
 }
}
