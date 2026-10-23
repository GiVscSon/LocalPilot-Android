package org.localpilot.android;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Conservative descriptors for a manually selected grid of two to six chromatic token types.
 * Neutral/special icons and empty cells are uncertain, not assumed ordinary puzzle pieces. */
public final class GridPuzzleVision {
 public interface Pixels { int get(int x,int y); }
 private static final int FEATURES=42,MAX_TYPES=24;
 private static final float MATCH=.14f,AMBIGUITY=.025f;
 private GridPuzzleVision(){}

 /** Stable one-based type IDs. Save candidate additions only after the board is stable. */
 public static final class Palette {
  private final float[][] descriptors;
  private Palette(List<float[]> values){descriptors=new float[values.size()][];for(int i=0;i<descriptors.length;i++)descriptors[i]=values.get(i).clone();}
  public int size(){return descriptors.length;}
  public int representativeColor(int type){if(type<1||type>size())throw new IllegalArgumentException("type");float[] d=descriptors[type-1];int r=0,g=0,b=0;for(int i=0;i<27;i+=3){r+=Math.round(d[i]*255);g+=Math.round(d[i+1]*255);b+=Math.round(d[i+2]*255);}return ((r/9)<<16)|((g/9)<<8)|(b/9);}
  public String encode(){StringBuilder s=new StringBuilder("v1");for(float[] d:descriptors){s.append(';');for(int i=0;i<d.length;i++){if(i>0)s.append(',');s.append(d[i]);}}return s.toString();}
  public static Palette decode(String value){
   try{if(value==null||value.length()>30000)throw new IllegalArgumentException();String[] groups=value.split(";",-1);if(!groups[0].equals("v1")||groups.length>MAX_TYPES+1)throw new IllegalArgumentException();List<float[]> out=new ArrayList<>();
    for(int n=1;n<groups.length;n++){String[] v=groups[n].split(",",-1);if(v.length!=FEATURES)throw new IllegalArgumentException();float[] d=new float[FEATURES];for(int i=0;i<FEATURES;i++){d[i]=Float.parseFloat(v[i]);if(!Float.isFinite(d[i])||d[i]<0||d[i]>1)throw new IllegalArgumentException();}out.add(d);}return new Palette(out);
   }catch(RuntimeException error){throw new IllegalArgumentException("invalid grid palette",error);}
  }
 }

 public static final class Observation {
  public final int rows,cols;
  public final long timestampMs;
  public final float confidence;
  private final int[] values;
  private final Palette palette;
  private final boolean usable;
  private Observation(int rows,int cols,long time,int[] cells,Palette palette,float confidence,boolean usable){this.rows=rows;this.cols=cols;timestampMs=time;values=cells.clone();this.palette=palette;this.confidence=confidence;this.usable=usable;}
  public int[] cells(){return values.clone();}
  public int type(int row,int col){if(row<0||row>=rows||col<0||col>=cols)throw new IllegalArgumentException("cell");return values[row*cols+col];}
  public Palette palette(){return palette;}
  public boolean complete(){for(int value:values)if(value<1)return false;return true;}
  public boolean usable(){return usable;}
  /** Depends on dimensions and symbolic cells, never the timestamp or palette serialization. */
  public long signature(){long h=0xcbf29ce484222325L;h=(h^rows)*0x100000001b3L;h=(h^cols)*0x100000001b3L;for(int value:values)h=(h^value)*0x100000001b3L;return h;}
 }

 public static Observation detect(Pixels pixels,int left,int top,int right,int bottom,int rows,int cols,
   int excludedLeft,int excludedTop,int excludedRight,int excludedBottom,long timestampMs,Palette previous){
  if(pixels==null||rows<3||rows>10||cols<3||cols>10||left<0||top<0||right-left<cols*12||bottom-top<rows*12||timestampMs<0)throw new IllegalArgumentException("grid geometry");
  float[][] samples=new float[rows*cols][];int[] cells=new int[samples.length];java.util.Arrays.fill(cells,-1);
  for(int row=0;row<rows;row++)for(int col=0;col<cols;col++){
   float l=left+col*(right-left)/(float)cols,t=top+row*(bottom-top)/(float)rows;
   float r=left+(col+1)*(right-left)/(float)cols,b=top+(row+1)*(bottom-top)/(float)rows;
   if(excludedRight>excludedLeft&&excludedBottom>excludedTop&&Math.max(l,excludedLeft)<Math.min(r,excludedRight)&&Math.max(t,excludedTop)<Math.min(b,excludedBottom))continue;
   samples[row*cols+col]=describe(pixels,l,t,r,b);
  }
  List<float[]> prototypes=new ArrayList<>();if(previous!=null)for(float[] d:previous.descriptors)prototypes.add(d.clone());
  int fixed=prototypes.size();int[] counts=new int[MAX_TYPES];
  for(float[] sample:samples){if(sample==null)continue;int nearest=-1;float distance=Float.POSITIVE_INFINITY;
   for(int n=0;n<prototypes.size();n++){float d=distance(sample,prototypes.get(n));if(d<distance){nearest=n;distance=d;}}
   if(distance>MATCH){if(prototypes.size()<MAX_TYPES){counts[prototypes.size()]=1;prototypes.add(sample.clone());}}
   else if(nearest>=fixed){int count=++counts[nearest];float[] p=prototypes.get(nearest);for(int n=0;n<FEATURES;n++)p[n]+=(sample[n]-p[n])/count;}
  }
  Set<Integer> distinct=new HashSet<>();float quality=0;boolean complete=true;
  for(int index=0;index<samples.length;index++){
   float[] sample=samples[index];if(sample==null){complete=false;continue;}
   int nearest=-1;float first=Float.POSITIVE_INFINITY,second=Float.POSITIVE_INFINITY;
   for(int n=0;n<prototypes.size();n++){float d=distance(sample,prototypes.get(n));if(d<first){second=first;first=d;nearest=n;}else second=Math.min(second,d);}
   if(nearest<0||first>MATCH||second-first<AMBIGUITY){complete=false;continue;}
   cells[index]=nearest+1;distinct.add(nearest);quality+=Math.max(0,1-first/MATCH);
  }
  // A previously learned palette is not evidence that the current picture is a
  // puzzle board. Monochrome textures/menus must abstain even if their colour is known.
  boolean usable=complete&&distinct.size()>=2&&distinct.size()<=6;
  return new Observation(rows,cols,timestampMs,cells,new Palette(prototypes),usable?.72f+.23f*quality/samples.length:0,usable);
 }

 private static float[] describe(Pixels pixels,float l,float t,float r,float b){
  float[] d=new float[FEATURES];float[] mean=new float[3],sq=new float[3];
  int count=0;
  for(int yy=0;yy<17;yy++)for(int xx=0;xx<17;xx++){
   float nx=.14f+.72f*xx/16,ny=.14f+.72f*yy/16;int rgb=pixels.get((int)(l+nx*(r-l)),(int)(t+ny*(b-t)));
   float red=(rgb>>16&255)/255f,green=(rgb>>8&255)/255f,blue=(rgb&255)/255f;
   mean[0]+=red;mean[1]+=green;mean[2]+=blue;sq[0]+=red*red;sq[1]+=green*green;sq[2]+=blue*blue;count++;
   float max=Math.max(red,Math.max(green,blue)),min=Math.min(red,Math.min(green,blue)),delta=max-min,sat=max==0?0:delta/max;
   if(sat<.18f||max<.12f)d[27+(max<.3f?12:max<.75f?13:14)]++;
   else{float hue=(max==red?(green-blue)/delta:max==green?2+(blue-red)/delta:4+(red-green)/delta)/6;if(hue<0)hue++;float bin=hue*12;int base=(int)bin;float fraction=bin-base;d[27+base%12]+=1-fraction;d[27+(base+1)%12]+=fraction;}
  }
  for(int ch=0;ch<3;ch++)mean[ch]/=count;
  float spread=0;for(int ch=0;ch<3;ch++)spread+=Math.max(0,sq[ch]/count-mean[ch]*mean[ch]);spread=(float)Math.sqrt(spread/3);
  float[] rim=new float[3];int rimCount=0;
  for(int side=0;side<4;side++)for(int n=0;n<9;n++){
   float p=.07f+.86f*n/8;float x=side<2?(side==0?.045f:.955f):p,y=side>=2?(side==2?.045f:.955f):p;
   int rgb=pixels.get((int)(l+x*(r-l)),(int)(t+y*(b-t)));rim[0]+=(rgb>>16&255)/255f;rim[1]+=(rgb>>8&255)/255f;rim[2]+=(rgb&255)/255f;rimCount++;
  }
  float contrast=0;for(int ch=0;ch<3;ch++){float v=mean[ch]-rim[ch]/rimCount;contrast+=v*v;}contrast=(float)Math.sqrt(contrast/3);
  // A flat field or a smooth background provides no evidence of an actual token.
  if(spread<.045f&&contrast<.09f)return null;
  // Grey/white special icons (e.g. skulls) need semantic handling. Do not merge
  // those into a similarly coloured ordinary piece and silently treat them as safe.
  if((d[39]+d[40]+d[41])/count>.30f)return null;
  for(int gy=0;gy<3;gy++)for(int gx=0;gx<3;gx++){
   int base=(gy*3+gx)*3;
   for(int yy=0;yy<5;yy++)for(int xx=0;xx<5;xx++){
    float nx=.25f+gx*.25f+(xx-2)*.035f,ny=.25f+gy*.25f+(yy-2)*.035f;
    int rgb=pixels.get((int)(l+nx*(r-l)),(int)(t+ny*(b-t)));d[base]+=(rgb>>16&255)/(255f*25);d[base+1]+=(rgb>>8&255)/(255f*25);d[base+2]+=(rgb&255)/(255f*25);
   }
  }
  for(int i=27;i<FEATURES;i++)d[i]/=count;
  for(int i=0;i<FEATURES;i++)d[i]=Math.max(0,Math.min(1,d[i]));
  return d;
 }

 private static float distance(float[] a,float[] b){float rgb=0,hist=0;for(int i=0;i<27;i++){float v=a[i]-b[i];rgb+=v*v;}for(int i=27;i<FEATURES;i++)hist+=Math.abs(a[i]-b[i]);return .55f*(float)Math.sqrt(rgb/27)+.225f*hist;}
}
