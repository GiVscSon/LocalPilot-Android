package org.localpilot.android;

/** Explicit circular ready-button signature, scoped to a permitted game viewport.
 * Button bounds are screen-global. The viewport may include UI below the board ROI.
 */
public final class BubbleBoosterCalibration {
 private static final String FORMAT="bubble-booster-v1";
 public final float leftFraction,topFraction,rightFraction,bottomFraction,aspect;
 private final int[] probes;
 private BubbleBoosterCalibration(float left,float top,float right,float bottom,float aspect,int[] probes){
  if(!Float.isFinite(left)||!Float.isFinite(top)||!Float.isFinite(right)||!Float.isFinite(bottom)||!Float.isFinite(aspect)
    ||left<0||top<0||right>1||bottom>1||right<=left||bottom<=top||right-left>.30f||bottom-top>.30f
    ||aspect<.15f||aspect>3||probes==null||probes.length!=17||!BubbleVision.multicolor(probes)
    ||(right-left)*aspect/(bottom-top)<.70f||(right-left)*aspect/(bottom-top)>1.40f)
   throw new IllegalArgumentException("booster calibration geometry");
  leftFraction=left;topFraction=top;rightFraction=right;bottomFraction=bottom;this.aspect=aspect;this.probes=probes.clone();
 }
 public static BubbleBoosterCalibration capture(BubbleVision.Pixels pixels,int width,int height,
   int viewLeft,int viewTop,int viewRight,int viewBottom,int buttonLeft,int buttonTop,int buttonRight,int buttonBottom){
  if(viewLeft<0||viewTop<0||viewRight>width||viewBottom>height||viewRight<=viewLeft||viewBottom<=viewTop
    ||buttonLeft<viewLeft||buttonTop<viewTop||buttonRight>viewRight||buttonBottom>viewBottom
    ||buttonRight-buttonLeft<12||buttonBottom-buttonTop<12)throw new IllegalArgumentException("booster calibration bounds");
  float ratio=(buttonRight-buttonLeft)/(float)(buttonBottom-buttonTop);
  if(ratio<.70f||ratio>1.40f)throw new IllegalArgumentException("circular booster button required");
  float cx=(buttonLeft+buttonRight)*.5f,cy=(buttonTop+buttonBottom)*.5f,radius=Math.min(buttonRight-buttonLeft,buttonBottom-buttonTop)*.5f;
  if(cx-radius*1.22f<viewLeft||cx+radius*1.22f>=viewRight||cy-radius*1.22f<viewTop||cy+radius*1.22f>=viewBottom)
   throw new IllegalArgumentException("booster boundary leaves viewport");
  int[] probes=BubbleVision.captureCalibrationPattern(pixels,width,height,cx,cy,radius);
  float w=viewRight-viewLeft,h=viewBottom-viewTop;
  return new BubbleBoosterCalibration((buttonLeft-viewLeft)/w,(buttonTop-viewTop)/h,(buttonRight-viewLeft)/w,(buttonBottom-viewTop)/h,w/h,probes);
 }
 public boolean validFor(int left,int top,int right,int bottom){return right>left&&bottom>top&&Math.abs((right-left)/(float)(bottom-top)-aspect)<=aspect*.04f;}
 public float centerX(int left,int right){return left+(right-left)*(leftFraction+rightFraction)*.5f;}
 public float centerY(int top,int bottom){return top+(bottom-top)*(topFraction+bottomFraction)*.5f;}
 public int left(int left,int right){return Math.round(left+(right-left)*leftFraction);}
 public int right(int left,int right){return Math.round(left+(right-left)*rightFraction);}
 public int top(int top,int bottom){return Math.round(top+(bottom-top)*topFraction);}
 public int bottom(int top,int bottom){return Math.round(top+(bottom-top)*bottomFraction);}
 public boolean ready(BubbleVision.Pixels pixels,int width,int height,int left,int top,int right,int bottom){return ready(pixels,width,height,left,top,right,bottom,0,0,0,0);}
 public boolean ready(BubbleVision.Pixels pixels,int width,int height,int left,int top,int right,int bottom,
   int excludedLeft,int excludedTop,int excludedRight,int excludedBottom){
  if(left<0||top<0||right>width||bottom>height||!validFor(left,top,right,bottom))return false;
  int bl=left(left,right),bt=top(top,bottom),br=right(left,right),bb=bottom(top,bottom);
  if(br-bl<12||bb-bt<12)return false;
  if(excludedRight>excludedLeft&&excludedBottom>excludedTop&&bl<excludedRight&&excludedLeft<br&&bt<excludedBottom&&excludedTop<bb)return false;
  float radius=Math.min(br-bl,bb-bt)*.5f;
  return BubbleVision.matchesCalibrationPattern(pixels,width,height,centerX(left,right),centerY(top,bottom),radius,probes);
 }
 public String encode(){StringBuilder out=new StringBuilder(FORMAT).append(',').append(leftFraction).append(',').append(topFraction).append(',').append(rightFraction).append(',').append(bottomFraction).append(',').append(aspect);for(int probe:probes)out.append(',').append(Integer.toHexString(probe));return out.toString();}
 public static BubbleBoosterCalibration decode(String text){
  if(text==null||text.length()>512)throw new IllegalArgumentException("booster calibration format");String[] fields=text.split(",",-1);
  if(fields.length!=23||!FORMAT.equals(fields[0]))throw new IllegalArgumentException("booster calibration format");
  try{int[] probes=new int[17];for(int i=0;i<17;i++){
    String field=fields[i+6];if(field.isEmpty()||field.length()>8)throw new IllegalArgumentException("booster calibration color");
    for(int j=0;j<field.length();j++)if(Character.digit(field.charAt(j),16)<0)throw new IllegalArgumentException("booster calibration color");
    probes[i]=(int)Long.parseLong(field,16);
   }
   return new BubbleBoosterCalibration(Float.parseFloat(fields[1]),Float.parseFloat(fields[2]),Float.parseFloat(fields[3]),Float.parseFloat(fields[4]),Float.parseFloat(fields[5]),probes);
  }catch(NumberFormatException error){throw new IllegalArgumentException("booster calibration values",error);}
 }
}
