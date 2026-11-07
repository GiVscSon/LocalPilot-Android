package org.localpilot.android;

import java.util.Arrays;
import java.util.BitSet;

/** Session-owned calibrated grid reader. Reuses all read storage after construction.
 * Brightness adaptation changes appearance, never the calibrated token namespace.
 * A remembered object is diagnostic context only: a missing current sample is UNKNOWN.
 * This class is confined to the vision executor; Result is valid until the next read.
 */
public final class AdaptiveGridReader {
 public enum Status { NEEDS_PALETTE, WAIT, STABILIZING, READY, PARTIAL_READY }
 private static final int CELLS=100,TYPES=24,FEATURES=42;
 private static final long FRESH_MS=300,STABLE_MS=100,MEMORY_MS=1000;
 private static final float SOFT_LIMIT=.105f,MARGIN=.03f;
 private final float[][] reference=new float[TYPES][FEATURES],working=new float[TYPES][FEATURES];
 private final float[][] samples=new float[CELLS][FEATURES];
 private final float[] scratch=new float[9],ring=new float[3];
 private final int[] candidate=new int[CELLS],remembered=new int[CELLS];
 private final long[] rememberedAt=new long[CELLS];
 private final BitSet candidateSpecial=new BitSet(CELLS);
 private final Result result=new Result();
 private GridPuzzleVision.Palette source;
 private long context,lastFrame=-1,candidateAt=-1;
 private int left,top,right,bottom,rows,cols;
 private boolean protectSkulls,matchWorld,initialized;
 private int adaptations;
 private float matchQuality;

 public static final class Result {
  private final int[] cells=new int[CELLS];
  private final GridPuzzleVision.CellReason[] reasons=new GridPuzzleVision.CellReason[CELLS];
  private final String[] diagnostics=new String[CELLS];
  private final BitSet special=new BitSet(CELLS);
  private int rows,cols,unknown;
  private long timestamp,generation;
  private float confidence;
  private Status status=Status.WAIT;
  private boolean complete,usable,partial,soft;
  private GridPuzzleVision.Palette palette;
  private Result(){}
  public Status status(){return status;}
  public int rows(){return rows;}
  public int cols(){return cols;}
  public int size(){return rows*cols;}
  public long timestampMs(){return timestamp;}
  public long generation(){return generation;}
  public float confidence(){return confidence;}
  public int unknownCount(){return unknown;}
  public boolean complete(){return complete;}
  /** Readability is not stability. The ordinary session still verifies post-ACK changes. */
  public boolean usable(){return usable;}
  public boolean partialUsable(){return partial;}
  public boolean stable(){return status==Status.READY||status==Status.PARTIAL_READY;}
  public boolean usedPhotometricMatch(){return soft;}
  public int type(int row,int col){check(row,col);return cells[row*cols+col];}
  public GridPuzzleVision.CellReason reason(int row,int col){check(row,col);return reasons[row*cols+col];}
  public boolean special(int row,int col){check(row,col);return special.get(row*cols+col);}
  public void copyCells(int[] into){if(into==null||into.length<size())throw new IllegalArgumentException("cell buffer");System.arraycopy(cells,0,into,0,size());}
  public void copySpecial(BitSet into){if(into==null)throw new IllegalArgumentException("special buffer");into.clear();into.or(special);}
  /** Explicit allocating compatibility snapshot. Call outside the allocation-free read path. */
  public GridPuzzleVision.Observation snapshot(){
   int n=size();
   return new GridPuzzleVision.Observation(rows,cols,timestamp,Arrays.copyOf(cells,n),palette,
       confidence,usable,partial,special,Arrays.copyOf(reasons,n),Arrays.copyOf(diagnostics,n));
  }
  private void check(int row,int col){if(row<0||row>=rows||col<0||col>=cols)throw new IllegalArgumentException("cell");}
 }

 /** Explicit context invalidation: no old stable candidates or exposure estimates survive. */
 public void reset(){
  initialized=false;source=null;context=0;lastFrame=candidateAt=-1;adaptations=0;
  Arrays.fill(remembered,-1);Arrays.fill(rememberedAt,-1);candidateSpecial.clear();
  clearResult(0,0,0);result.status=Status.NEEDS_PALETTE;
 }
 public AdaptiveGridReader(){reset();}
 public int adaptationCount(){return adaptations;}
 /** Last known type, never suitable for a gesture or reward; expires after one second. */
 public int rememberedType(int row,int col,long now){
  if(!initialized||row<0||row>=rows||col<0||col>=cols)return -1;
  int i=row*cols+col;
  return rememberedAt[i]>=0&&now>=rememberedAt[i]&&now-rememberedAt[i]<=MEMORY_MS?remembered[i]:-1;
 }

 public Result read(GridPuzzleVision.Pixels pixels,int l,int t,int r,int b,int rowCount,int colCount,
     int excludedLeft,int excludedTop,int excludedRight,int excludedBottom,long frameAt,long now,
     long contextToken,GridPuzzleVision.Palette palette,boolean skulls,boolean crates,boolean pendingAction){
  if(pixels==null||l<0||t<0||rowCount<3||rowCount>10||colCount<3||colCount>10
      ||(long)r-l<colCount*12L||(long)b-t<rowCount*12L||now<0||contextToken<=0)
   throw new IllegalArgumentException("adaptive grid geometry");
  if(!initialized||context!=contextToken||source!=palette||left!=l||top!=t||right!=r||bottom!=b
      ||rows!=rowCount||cols!=colCount||protectSkulls!=skulls||matchWorld!=crates){
   reset();initialized=true;context=contextToken;source=palette;left=l;top=t;right=r;bottom=b;
   rows=rowCount;cols=colCount;protectSkulls=skulls;matchWorld=crates;
   if(palette!=null)for(int i=0;i<palette.size();i++){
    palette.copyDescriptor(i+1,reference[i]);System.arraycopy(reference[i],0,working[i],0,FEATURES);
   }
  }
  clearResult(rowCount,colCount,frameAt);result.palette=palette;
  if(palette==null||palette.size()<2||palette.size()>TYPES){candidateAt=-1;result.status=Status.NEEDS_PALETTE;return result;}
  if(frameAt<0||frameAt>now||now-frameAt>FRESH_MS||frameAt<=lastFrame){candidateAt=-1;return result;}
  lastFrame=frameAt;
  boolean excluded=false;int known=0;long types=0;float quality=0;
  for(int row=0;row<rows;row++)for(int col=0;col<cols;col++){
   int i=row*cols+col;float x0=l+col*(r-l)/(float)cols,y0=t+row*(b-t)/(float)rows;
   float x1=l+(col+1)*(r-l)/(float)cols,y1=t+(row+1)*(b-t)/(float)rows;
   if(excludedRight>excludedLeft&&excludedBottom>excludedTop&&Math.max(x0,excludedLeft)<Math.min(x1,excludedRight)
       &&Math.max(y0,excludedTop)<Math.min(y1,excludedBottom)){
    result.reasons[i]=GridPuzzleVision.CellReason.EXCLUDED_OVERLAY;excluded=true;continue;
   }
   if(crates&&rows==7&&cols==7&&GridPuzzleVision.matchWorldHole(row,col)&&flatBlue(pixels,x0,y0,x1,y1)){
    protect(i,GridPuzzleVision.CellReason.EMPTY_HOLE);known++;quality++;continue;
   }
   if(crates&&GridPuzzleVision.matchWorldWoodCrate(pixels,x0,y0,x1,y1)){
    protect(i,GridPuzzleVision.CellReason.BLOCKER_CRATE);known++;quality++;continue;
   }
   if(skulls&&GridPuzzleVision.protectedSkull(pixels,x0,y0,x1,y1)){
    protect(i,GridPuzzleVision.CellReason.PROTECTED_SKULL);known++;quality++;continue;
   }
   int ringKind=ring(pixels,x0,y0,x1,y1);
   if(ringKind!=0){
    int type=ringKind<0?-1:matchRing(palette.size());
    result.reasons[i]=type>0?GridPuzzleVision.CellReason.KNOWN_SPECIAL:
        type==-2?GridPuzzleVision.CellReason.RING_AMBIGUOUS:GridPuzzleVision.CellReason.RING_NO_MATCH;
    if(type>0){result.cells[i]=type;result.special.set(i);known++;quality+=.8f;types|=1L<<(type-1);}
    continue;
   }
   float[] descriptor=samples[i];
   if(!GridPuzzleVision.describeInto(pixels,x0,y0,x1,y1,descriptor,scratch))continue;
   int type=match(descriptor,palette.size());
   result.reasons[i]=type>0?GridPuzzleVision.CellReason.KNOWN:
       type==-2?GridPuzzleVision.CellReason.COLOR_AMBIGUOUS:GridPuzzleVision.CellReason.COLOR_NO_MATCH;
   if(type>0){result.cells[i]=type;known++;quality+=matchQuality;types|=1L<<(type-1);remembered[i]=type;rememberedAt[i]=frameAt;}
  }
  int n=rows*cols;result.unknown=n-known;result.complete=known==n;
  int distinct=Long.bitCount(types);boolean diverse=distinct>=2&&distinct<=12;
  result.usable=result.complete&&diverse&&!excluded;
  result.partial=!result.complete&&diverse&&!excluded&&known>=Math.max(6,(n*72+99)/100);
  result.confidence=result.usable?.72f+.23f*quality/n:result.partial?.60f+.20f*quality/n:0;
  if(!result.usable&&!result.partial){candidateAt=-1;return result;}
  boolean same=candidateAt>=0&&candidateSpecial.equals(result.special);
  if(same)for(int i=0;i<n;i++)if(candidate[i]!=result.cells[i]){same=false;break;}
  if(!same){System.arraycopy(result.cells,0,candidate,0,n);candidateSpecial.clear();candidateSpecial.or(result.special);candidateAt=frameAt;result.status=Status.STABILIZING;return result;}
  result.status=frameAt-candidateAt>=STABLE_MS?(result.usable?Status.READY:Status.PARTIAL_READY):Status.STABILIZING;
  if(result.status==Status.READY&&!pendingAction)adapt(palette.size(),n);
  return result;
 }
 private void clearResult(int rowCount,int colCount,long frameAt){
  result.rows=rowCount;result.cols=colCount;result.timestamp=frameAt;result.generation++;
  Arrays.fill(result.cells,-1);Arrays.fill(result.reasons,GridPuzzleVision.CellReason.NO_SAMPLE);
  Arrays.fill(result.diagnostics,"adaptive calibrated descriptor");result.special.clear();
  result.unknown=rowCount*colCount;result.complete=result.usable=result.partial=result.soft=false;
  result.confidence=0;result.status=Status.WAIT;result.palette=source;
 }
 private void protect(int i,GridPuzzleVision.CellReason reason){result.cells[i]=0;result.special.set(i);result.reasons[i]=reason;}
 private int match(float[] sample,int count){
  int best=-1;float first=Float.POSITIVE_INFINITY,second=Float.POSITIVE_INFINITY;
  for(int i=0;i<count;i++){float d=distance(sample,reference[i],false);if(d<first){second=first;first=d;best=i;}else second=Math.min(second,d);}
  if(first<=.045f&&second-first>=.025f){matchQuality=Math.max(0,1-first/.14f);return best+1;}
  best=-1;first=second=Float.POSITIVE_INFINITY;
  for(int i=0;i<count;i++){float d=distance(sample,working[i],true);if(d<first){second=first;first=d;best=i;}else second=Math.min(second,d);}
  if(best<0||first>SOFT_LIMIT)return -1;
  if(second-first<MARGIN)return -2;
  result.soft=true;matchQuality=Math.max(0,1-first/SOFT_LIMIT);return best+1;
 }
 private static float distance(float[] sample,float[] prototype,boolean photometric){
  float gain=1,offset=0;
  if(photometric){
   float a=0,b=0,aa=0,ab=0;
   for(int i=0;i<27;i++){a+=sample[i];b+=prototype[i];aa+=sample[i]*sample[i];ab+=sample[i]*prototype[i];}
   float variance=aa-a*a/27;
   if(variance>1e-6f)gain=Math.max(.65f,Math.min(1.55f,(ab-a*b/27)/variance));
   offset=Math.max(-.10f,Math.min(.10f,(b-gain*a)/27));
  }
  float rgb=0,hist=0;
  for(int i=0;i<27;i++){float d=sample[i]*gain+offset-prototype[i];rgb+=d*d;}
  for(int i=27;i<(photometric?39:42);i++)hist+=Math.abs(sample[i]-prototype[i]);
  if(photometric)hist+=Math.abs(sample[39]+sample[40]+sample[41]-prototype[39]-prototype[40]-prototype[41]);
  return .55f*(float)Math.sqrt(rgb/27)+.225f*hist;
 }
 private void adapt(int count,int size){
  boolean changed=false;
  for(int i=0;i<size;i++)if(result.cells[i]>0&&!result.special.get(i)){
   int type=result.cells[i]-1;float[] sample=samples[i],target=working[type],base=reference[type];
   // The initial calibrated semantic class remains the closest invariant class.
   float own=distance(sample,base,true),other=Float.POSITIVE_INFINITY;
   for(int j=0;j<count;j++)if(j!=type)other=Math.min(other,distance(sample,reference[j],true));
   if(own>SOFT_LIMIT||other-own<MARGIN)continue;
   for(int f=0;f<FEATURES;f++){
    float proposed=target[f]+(sample[f]-target[f])*.025f;
    float limit=f<27?.08f:.04f;
    proposed=Math.max(0,Math.min(1,Math.max(base[f]-limit,Math.min(base[f]+limit,proposed))));
    if(Math.abs(proposed-target[f])>1e-7f)changed=true;target[f]=proposed;
   }
  }
  if(changed&&adaptations<Integer.MAX_VALUE)adaptations++;
 }
 private static int at(GridPuzzleVision.Pixels p,float l,float t,float r,float b,float x,float y){float side=Math.min(r-l,b-t);return p.get((int)((l+r)*.5f+(x-.5f)*side),(int)((t+b)*.5f+(y-.5f)*side));}
 private static boolean flatBlue(GridPuzzleVision.Pixels p,float l,float t,float r,float b){
  long rr=0,gg=0,bb=0;
  for(int y=0;y<5;y++)for(int x=0;x<5;x++){int rgb=at(p,l,t,r,b,.2f+x*.15f,.2f+y*.15f);rr+=rgb>>16&255;gg+=rgb>>8&255;bb+=rgb&255;}
  int red=(int)(rr/25),green=(int)(gg/25),blue=(int)(bb/25);
  if(red<115||green-red<6||blue-green<6||blue-red>125)return false;
  for(int y=0;y<5;y++)for(int x=0;x<5;x++){int rgb=at(p,l,t,r,b,.2f+x*.15f,.2f+y*.15f);int dr=(rgb>>16&255)-red,dg=(rgb>>8&255)-green,db=(rgb&255)-blue;if(dr*dr+dg*dg+db*db>400)return false;}
  return true;
 }
 /** 0 ordinary descriptor, 1 chromatic special ring, -1 unsupported ring. */
 private int ring(GridPuzzleVision.Pixels p,float l,float t,float r,float b){
  Arrays.fill(scratch,0);int count=0;
  for(int yy=0;yy<7;yy++)for(int xx=0;xx<7;xx++){
   float x=.2f+xx*.1f,y=.2f+yy*.1f;if(x>=.35f&&x<=.65f&&y>=.35f&&y<=.65f)continue;
   int rgb=at(p,l,t,r,b,x,y);for(int c=0;c<3;c++){float v=(rgb>>(16-8*c)&255)/255f;scratch[c]+=v;scratch[3+c]+=v*v;}count++;
  }
  for(int c=0;c<3;c++)ring[c]=scratch[c]/count;
  for(int yy=0;yy<3;yy++)for(int xx=0;xx<3;xx++){int rgb=at(p,l,t,r,b,.35f+xx*.15f,.35f+yy*.15f);for(int c=0;c<3;c++)scratch[6+c]+=(rgb>>(16-8*c)&255)/(255f*9);}
  float contrast=0,spread=0;
  for(int c=0;c<3;c++){float d=ring[c]-scratch[6+c];contrast+=d*d;spread+=Math.max(0,scratch[3+c]/count-ring[c]*ring[c]);}
  if(Math.sqrt(contrast/3)<.18)return 0;
  float hi=Math.max(ring[0],Math.max(ring[1],ring[2])),lo=Math.min(ring[0],Math.min(ring[1],ring[2]));
  return Math.sqrt(spread/3)>.07||hi<.2||hi-lo<.18?-1:1;
 }
 private int matchRing(int count){
  int best=-1;float first=Float.POSITIVE_INFINITY,second=Float.POSITIVE_INFINITY;
  for(int type=0;type<count;type++){
   float error=0;boolean flat=true;
   for(int c=0;c<3;c++){
    float average=(working[type][c]+working[type][6+c]+working[type][18+c]+working[type][24+c])*.25f;
    if(Math.abs(working[type][c]-average)>.12f||Math.abs(working[type][6+c]-average)>.12f
        ||Math.abs(working[type][18+c]-average)>.12f||Math.abs(working[type][24+c]-average)>.12f)flat=false;
    float d=average-ring[c];error+=d*d;
   }
   float d=flat?(float)Math.sqrt(error/3):Float.POSITIVE_INFINITY;
   if(d<first){second=first;first=d;best=type;}else second=Math.min(second,d);
  }
  if(best<0||first>.09f)return -1;return second-first<.03f?-2:best+1;
 }
}
