package org.localpilot.android;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Conservative descriptors for a manually selected grid of two to twelve chromatic token types.
 * Neutral/special icons and empty cells are uncertain, not assumed ordinary puzzle pieces. */
public final class GridPuzzleVision {
 public interface Pixels { int get(int x,int y); }
 /** Trace-only reasons; these labels do not authorize a game gesture. */
 public enum CellReason {
  KNOWN, KNOWN_SPECIAL, PROTECTED_SKULL, EXCLUDED_OVERLAY,
  RING_NO_MATCH, RING_AMBIGUOUS, COLOR_NO_MATCH, COLOR_AMBIGUOUS, NO_SAMPLE
 }
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
  private final boolean usable,partialUsable;
  private final java.util.BitSet specialCellsMask;
  private final CellReason[] cellReasons;
  private final String[] cellSamples;
  private Observation(int rows,int cols,long time,int[] cells,Palette palette,float confidence,boolean usable,boolean partialUsable,java.util.BitSet special,CellReason[] reasons,String[] samples){
   specialCellsMask=(java.util.BitSet)special.clone();this.rows=rows;this.cols=cols;timestampMs=time;
   values=cells.clone();this.palette=palette;this.confidence=confidence;this.usable=usable;this.partialUsable=partialUsable;
   cellReasons=reasons.clone();cellSamples=samples.clone();
  }
  public CellReason reason(int row,int col){
   if(row<0||row>=rows||col<0||col>=cols)throw new IllegalArgumentException("cell");
   return cellReasons[row*cols+col];
  }
  /** Row-major indices of cells the current detector could not identify. */
  public int[] unknownIndices(){
   int count=0;for(int value:values)if(value<0)count++;
   int[] out=new int[count];int n=0;
   for(int i=0;i<values.length;i++)if(values[i]<0)out[n++]=i;
   return out;
  }
  /** Bounded, pixel-free diagnostics suitable for the on-screen status panel. */
  public String unknownSummary(int limit){
   if(limit<0||limit>rows*cols)throw new IllegalArgumentException("limit");
   StringBuilder out=new StringBuilder();int shown=0,total=0;
   for(int i=0;i<values.length;i++)if(values[i]<0){
    total++;
    if(shown<limit){
     if(shown>0)out.append("; ");
     out.append("R").append(i/cols+1).append("C").append(i%cols+1).append(":").append(cellReasons[i]);
     shown++;
    }
   }
   if(total>shown)out.append(" (+").append(total-shown).append(")");
   return out.toString();
  }
  /** Counts every unknown reason, without storing pixels or assuming cell semantics. */
  public String unknownReasonCounts(){
   int[] counts=new int[CellReason.values().length];
   for(int i=0;i<values.length;i++)if(values[i]<0)counts[cellReasons[i].ordinal()]++;
   StringBuilder out=new StringBuilder();
   for(CellReason reason:CellReason.values())if(counts[reason.ordinal()]>0){
    if(out.length()>0)out.append(", ");out.append(reason).append("=").append(counts[reason.ordinal()]);
   }
   return out.toString();
  }
  /** For every refused or protected cell, report one-based row/column and bounded
   * center/ring RGB+HSV evidence. Does not change any cell classification. */
  public String cellReasonDetails(){
   StringBuilder out=new StringBuilder();
   for(int i=0;i<values.length;i++)if(cellReasons[i]!=CellReason.KNOWN&&cellReasons[i]!=CellReason.KNOWN_SPECIAL){
    if(out.length()>0)out.append("; ");
    out.append("R").append(i/cols+1).append("C").append(i%cols+1)
       .append(":").append(cellReasons[i]).append(" ").append(cellSamples[i]);
   }
   return out.length()==0?"none":out.toString();
  }
  public java.util.BitSet specialCellsMask(){return (java.util.BitSet)specialCellsMask.clone();}
  public int[] cells(){return values.clone();}
  public int type(int row,int col){if(row<0||row>=rows||col<0||col>=cols)throw new IllegalArgumentException("cell");return values[row*cols+col];}
  public Palette palette(){return palette;}
  public boolean complete(){for(int i=0;i<values.length;i++)if(values[i]<1&&!(values[i]==0&&specialCellsMask.get(i)))return false;return true;}
  public boolean usable(){return usable;}
  /** A geometrically stable incomplete board: unknowns remain -1, and
   * overlay occlusion is never accepted as partial evidence. The controller
   * still requires an authenticated target, two fresh frames and post-ACK CV.
   */
  public boolean partialUsable(){return partialUsable;}
  /** Depends on dimensions and symbolic cells, never the timestamp or palette serialization. */
  public long signature(){long h=0xcbf29ce484222325L;h=(h^rows)*0x100000001b3L;h=(h^cols)*0x100000001b3L;for(int i=0;i<values.length;i++){h=(h^values[i])*0x100000001b3L;h=(h^(specialCellsMask.get(i)?1:0))*0x100000001b3L;}return h;}
 }

 public static Observation detect(Pixels pixels,int left,int top,int right,int bottom,int rows,int cols,
   int excludedLeft,int excludedTop,int excludedRight,int excludedBottom,long timestampMs,Palette previous){
  return detect(pixels,left,top,right,bottom,rows,cols,excludedLeft,excludedTop,excludedRight,excludedBottom,timestampMs,previous,false);
 }
 /** The protected-skull preset is opt-in for the recorded octopus swap game.
  * A confirmed skull is an unplayable zero cell, never an ordinary colour token. */
 public static Observation detect(Pixels pixels,int left,int top,int right,int bottom,int rows,int cols,
   int excludedLeft,int excludedTop,int excludedRight,int excludedBottom,long timestampMs,Palette previous,boolean protectSkulls){
  if(pixels==null||rows<3||rows>10||cols<3||cols>10||left<0||top<0||right-left<cols*12||bottom-top<rows*12||timestampMs<0)throw new IllegalArgumentException("grid geometry");
  float[][] rings=new float[rows*cols][];java.util.BitSet special=new java.util.BitSet(rows*cols);
  float[][] samples=new float[rows*cols][];int[] cells=new int[samples.length];java.util.Arrays.fill(cells,-1);
  CellReason[] reasons=new CellReason[samples.length];
  java.util.Arrays.fill(reasons,CellReason.NO_SAMPLE);
  for(int row=0;row<rows;row++)for(int col=0;col<cols;col++){
   float l=left+col*(right-left)/(float)cols,t=top+row*(bottom-top)/(float)rows;
   float r=left+(col+1)*(right-left)/(float)cols,b=top+(row+1)*(bottom-top)/(float)rows;
   int index=row*cols+col;
   if(excludedRight>excludedLeft&&excludedBottom>excludedTop&&Math.max(l,excludedLeft)<Math.min(r,excludedRight)&&Math.max(t,excludedTop)<Math.min(b,excludedBottom)){
    reasons[index]=CellReason.EXCLUDED_OVERLAY;continue;
   }
   if(protectSkulls&&protectedSkull(pixels,l,t,r,b)){
    cells[index]=0;special.set(index);reasons[index]=CellReason.PROTECTED_SKULL;continue;
   }
   float[] ring=ring(pixels,l,t,r,b);
   if(ring!=null){rings[index]=ring;reasons[index]=CellReason.RING_NO_MATCH;continue;}
   samples[index]=describe(pixels,l,t,r,b);
   // A flat or visually unsupported cell has no token descriptor. Keep it
   // UNKNOWN: it may be an absent corner, an obstacle, or an occluded tile.
   reasons[index]=samples[index]==null?CellReason.NO_SAMPLE:CellReason.COLOR_NO_MATCH;
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
   if(cells[index]==0&&special.get(index)){quality+=1;continue;}
   float[] sample=samples[index];
   if(rings[index]!=null){
    int nearest=-1;float first=Float.POSITIVE_INFINITY,second=Float.POSITIVE_INFINITY;
    for(int n=0;n<prototypes.size();n++){
     if(n>=fixed&&counts[n]<2)continue;
     float d=ringDistance(rings[index],prototypes.get(n));
     if(d<first){second=first;first=d;nearest=n;}else second=Math.min(second,d);
    }
    if(nearest<0||first>.09f){reasons[index]=CellReason.RING_NO_MATCH;complete=false;continue;}
    if(second-first<.03f){reasons[index]=CellReason.RING_AMBIGUOUS;complete=false;continue;}
    cells[index]=nearest+1;special.set(index);reasons[index]=CellReason.KNOWN_SPECIAL;
    distinct.add(nearest);quality+=1-first/.09f;continue;
   }
   if(sample==null){complete=false;continue;}
   int nearest=-1;float first=Float.POSITIVE_INFINITY,second=Float.POSITIVE_INFINITY;
   for(int n=0;n<prototypes.size();n++){float d=distance(sample,prototypes.get(n));if(d<first){second=first;first=d;nearest=n;}else second=Math.min(second,d);}
   if(nearest<0||first>MATCH){reasons[index]=CellReason.COLOR_NO_MATCH;complete=false;continue;}
   if(second-first<AMBIGUITY){reasons[index]=CellReason.COLOR_AMBIGUOUS;complete=false;continue;}
   cells[index]=nearest+1;reasons[index]=CellReason.KNOWN;
   distinct.add(nearest);quality+=Math.max(0,1-first/MATCH);
  }
  // A previously learned palette is not evidence that the current picture is a
  // puzzle board. Monochrome textures/menus must abstain even if their colour is known.
  boolean sceneDiverse=distinct.size()>=2&&distinct.size()<=12;
  boolean usable=complete&&sceneDiverse;
  int known=0;boolean overlayOccluded=false;
  for(int i=0;i<cells.length;i++){
   if(cells[i]>0||cells[i]==0&&special.get(i))known++;
   if(reasons[i]==CellReason.EXCLUDED_OVERLAY)overlayOccluded=true;
  }
  // Incomplete boards need strong, independent evidence. One unknown
  // is no longer a global stop, but the ROI still must be mostly readable.
  boolean partialUsable=!complete&&sceneDiverse&&!overlayOccluded
    &&known>=Math.max(6,(cells.length*72+99)/100);
  float resultConfidence=usable?.72f+.23f*quality/samples.length:
    partialUsable?.60f+.20f*quality/samples.length:0f;
  // Snapshot compact sample statistics only for failures / protected objects.
  // Unknown values remain -1; these statistics grant no policy authority.
  String[] sampleDiagnostics=new String[cells.length];
  for(int row=0;row<rows;row++)for(int col=0;col<cols;col++){
   int index=row*cols+col;
   if(reasons[index]==CellReason.KNOWN||reasons[index]==CellReason.KNOWN_SPECIAL)continue;
   if(reasons[index]==CellReason.EXCLUDED_OVERLAY){sampleDiagnostics[index]="center=masked ring=masked";continue;}
   float l=left+col*(right-left)/(float)cols,t=top+row*(bottom-top)/(float)rows;
   float r=left+(col+1)*(right-left)/(float)cols,b=top+(row+1)*(bottom-top)/(float)rows;
   sampleDiagnostics[index]=diagnosticSamples(pixels,l,t,r,b);
  }
  return new Observation(rows,cols,timestampMs,cells,new Palette(prototypes),resultConfidence,usable,partialUsable,special,reasons,sampleDiagnostics);
 }

 /** Same 7x7 ring and 3x3 center positions used by ring(), reported as means. */
 private static String diagnosticSamples(Pixels pixels,float l,float t,float r,float b){
  long[] center=new long[3],ring=new long[3];int nc=0,nr=0;
  for(int yy=0;yy<7;yy++)for(int xx=0;xx<7;xx++){
   float x=.2f+xx*.1f,y=.2f+yy*.1f;
   int rgb=sample(pixels,l,t,r,b,x,y);
   long[] into=xx>=2&&xx<=4&&yy>=2&&yy<=4?center:ring;
   for(int ch=0;ch<3;ch++)into[ch]+=(rgb>>(16-8*ch))&255;
   if(into==center)nc++;else nr++;
  }
  return "center="+colorDiagnostic(center,nc)+" ring="+colorDiagnostic(ring,nr);
 }
 private static String colorDiagnostic(long[] sum,int count){
  int r=(int)((sum[0]+count/2)/count),g=(int)((sum[1]+count/2)/count),b=(int)((sum[2]+count/2)/count);
  int hi=Math.max(r,Math.max(g,b)),lo=Math.min(r,Math.min(g,b)),delta=hi-lo;
  float hue=0;
  if(delta>0){
   if(hi==r)hue=((g-b)/(float)delta+6)%6;
   else if(hi==g)hue=(b-r)/(float)delta+2;
   else hue=(r-g)/(float)delta+4;
   hue*=60;
  }
  int sat=hi==0?0:Math.round(delta*100f/hi),val=Math.round(hi*100f/255f);
  return "RGB("+r+","+g+","+b+") HSV("+Math.round(hue)+","+sat+"%,"+val+"%)";
 }
 private static int sample(Pixels pixels,float l,float t,float r,float b,float x,float y){return pixels.get((int)(l+x*(r-l)),(int)(t+y*(b-t)));}
 private static int brightness(int rgb){return Math.max(rgb>>16&255,Math.max(rgb>>8&255,rgb&255));}
 private static boolean bone(int rgb){int high=brightness(rgb),low=Math.min(rgb>>16&255,Math.min(rgb>>8&255,rgb&255));return high>=75&&high-low<high*.32f;}
 /** Red tile, pale head, two separate dark eyes and two lower bones must agree.
  * Uniform grey tokens, a red fruit, a white bonus or an occluded cell cannot qualify. */
 private static boolean protectedSkull(Pixels pixels,float l,float t,float r,float b){
  int red=0,rim=0;
  for(int yy=0;yy<17;yy++)for(int xx=0;xx<17;xx++)if(xx<3||xx>13||yy<3||yy>13){
   int rgb=sample(pixels,l,t,r,b,.14f+xx*.045f,.14f+yy*.045f),rr=rgb>>16&255,g=rgb>>8&255,bb=rgb&255;
   if(rr>g*1.18f&&rr>bb*1.10f)red++;rim++;
  }
  if(red<rim*.50f)return false;
  int count=0,total=0;
  for(int yy=0;yy<9;yy++)for(int xx=0;xx<9;xx++){
   int rgb=sample(pixels,l,t,r,b,.25f+xx*.45f/8,.20f+yy*.45f/8);
   if(bone(rgb)){count++;total+=brightness(rgb);}
  }
  if(count<49)return false;
  float eyeThreshold=total/(float)count*.65f;
  for(int eye=0;eye<2;eye++){
   int dark=0;float x=eye==0?.28f:.48f;
   for(int yy=0;yy<9;yy++)for(int xx=0;xx<9;xx++)if(brightness(sample(pixels,l,t,r,b,x+xx*.14f/8,.32f+yy*.17f/8))<eyeThreshold)dark++;
   if(dark<13||dark>57)return false;
  }
  for(int side=0;side<2;side++){
   int pale=0;float x=side==0?.15f:.62f,y=side==0?.52f:.64f,spanY=side==0?.23f:.20f;
   for(int yy=0;yy<9;yy++)for(int xx=0;xx<9;xx++)if(bone(sample(pixels,l,t,r,b,x+xx*.17f/8,y+yy*spanY/8)))pale++;
   if(pale<37)return false;
  }
  return true;
 }

 /** Returns a chromatic ring descriptor for a contrasting centre; invalid rings carry NaN. */
 private static float[] ring(Pixels pixels,float l,float t,float r,float b){
  float[] mean=new float[3],sq=new float[3],center=new float[3];int count=0;
  for(int yy=0;yy<7;yy++)for(int xx=0;xx<7;xx++){
   float x=.2f+xx*.1f,y=.2f+yy*.1f;if(x>=.35f&&x<=.65f&&y>=.35f&&y<=.65f)continue;
   int rgb=pixels.get((int)(l+x*(r-l)),(int)(t+y*(b-t)));
   for(int c=0;c<3;c++){float v=(rgb>>(16-8*c)&255)/255f;mean[c]+=v;sq[c]+=v*v;}count++;
  }
  for(int c=0;c<3;c++)mean[c]/=count;
  for(int yy=0;yy<3;yy++)for(int xx=0;xx<3;xx++){
   int rgb=pixels.get((int)(l+(.35f+xx*.15f)*(r-l)),(int)(t+(.35f+yy*.15f)*(b-t)));
   for(int c=0;c<3;c++)center[c]+=(rgb>>(16-8*c)&255)/(255f*9);
  }
  float contrast=0,spread=0;for(int c=0;c<3;c++){contrast+=(mean[c]-center[c])*(mean[c]-center[c]);spread+=Math.max(0,sq[c]/count-mean[c]*mean[c]);}
  if(Math.sqrt(contrast/3)<.18)return null;
  float hi=Math.max(mean[0],Math.max(mean[1],mean[2])),lo=Math.min(mean[0],Math.min(mean[1],mean[2]));
  if(Math.sqrt(spread/3)>.07||hi<.2||hi-lo<.18)return new float[]{Float.NaN,0,0};
  return mean;
 }
 private static float ringDistance(float[] ring,float[] descriptor){
  if(!Float.isFinite(ring[0]))return Float.POSITIVE_INFINITY;
  float distance=0;
  for(int c=0;c<3;c++){float average=0;for(int block:new int[]{0,2,6,8})average+=descriptor[block*3+c]*.25f;
   float delta=average-ring[c];distance+=delta*delta;
   // Do not label a patterned reference object solely by its average colour.
   for(int block:new int[]{0,2,6,8})if(Math.abs(descriptor[block*3+c]-average)>.12f)return Float.POSITIVE_INFINITY;
  }
  return (float)Math.sqrt(distance/3);
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
  if((d[39]+d[40]+d[41])/count>.30f){
   // Rounded icons can have a large neutral background. Require a strongly
   // chromatic centre instead of discarding every fish/ham silhouette with it.
   int chromatic=0;
   for(int yy=0;yy<7;yy++)for(int xx=0;xx<7;xx++){
    int rgb=pixels.get((int)(l+(.35f+.30f*xx/6)*(r-l)),(int)(t+(.35f+.30f*yy/6)*(b-t)));
    float hi=Math.max(rgb>>16&255,Math.max(rgb>>8&255,rgb&255))/255f;
    float lo=Math.min(rgb>>16&255,Math.min(rgb>>8&255,rgb&255))/255f;
    if(hi>=.2f&&hi-lo>=.12f&&(hi-lo)/hi>=.3f)chromatic++;
   }
   if(chromatic<40)return null;
  }
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
