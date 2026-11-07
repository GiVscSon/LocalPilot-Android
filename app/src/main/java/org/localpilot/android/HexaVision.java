package org.localpilot.android;

/** Calibrated, allocation-free HexaSort sampling; absent evidence is UNKNOWN, never EMPTY. */
public final class HexaVision {
 public interface Pixels { int get(int x,int y); }
 private static final float[] COS={1f,.5f,-.5f,-1f,-.5f,.5f};
 private static final float[] SIN={0f,.8660254f,.8660254f,0f,-.8660254f,-.8660254f};
 private HexaVision(){}
 public static final class Calibration {
  private final int[] topColors;
  public final int emptyCenterArgb,emptyRingArgb;
  public Calibration(int emptyCenterArgb,int emptyRingArgb,int[] topColors){
   if(topColors==null||topColors.length<1||topColors.length>16||rgbDistance(emptyCenterArgb,emptyRingArgb)<30f)
    throw new IllegalArgumentException("hexa calibration");
   this.topColors=topColors.clone();this.emptyCenterArgb=emptyCenterArgb;this.emptyRingArgb=emptyRingArgb;
   for(int i=0;i<topColors.length;i++){
    if(saturation(topColors[i])<.20f||brightness(topColors[i])<45)throw new IllegalArgumentException("hexa chromatic palette");
    for(int j=0;j<i;j++)if(chromaDistance(topColors[i],topColors[j])<.045f)throw new IllegalArgumentException("ambiguous hexa palette");
   }
  }
  public int colors(){return topColors.length;}
  public String encode(){StringBuilder s=new StringBuilder("hexa-palette-v1,").append(Integer.toHexString(emptyCenterArgb)).append(',').append(Integer.toHexString(emptyRingArgb));for(int c:topColors)s.append(',').append(Integer.toHexString(c));return s.toString();}
  public static Calibration decode(String value){
   try{if(value==null||value.length()>220)throw new IllegalArgumentException();String[] p=value.split(",",-1);if(p.length<4||p.length>19||!p[0].equals("hexa-palette-v1"))throw new IllegalArgumentException();int[] colors=new int[p.length-3];for(int i=0;i<colors.length;i++)colors[i]=(int)Long.parseLong(p[i+3],16);return new Calibration((int)Long.parseLong(p[1],16),(int)Long.parseLong(p[2],16),colors);}
   catch(RuntimeException error){throw new IllegalArgumentException("invalid hexa palette",error);}
  }
 }
 public static final class Observation {
  private final HexaBoard board;
  private boolean usable;private float confidence;private long timestampMs;
  private String reason="not read";
  private Observation(HexaBoard board){this.board=board;}
  /** Reused on the next read. Sessions copy its values; callers must not retain it as a previous frame. */
  public HexaBoard board(){return board;}
  public boolean usable(){return usable;}
  public float confidence(){return confidence;}
  public long timestampMs(){return timestampMs;}
  public String reason(){return reason;}
 }
 public static final class Reader {
  private final HexaConfig config;private final Calibration calibration;
  private final HexaBoard board;private final Observation observation;
  private final int[] votes=new int[17];
  public Reader(HexaConfig config,Calibration calibration){
   if(config==null||calibration==null)throw new IllegalArgumentException("hexa reader");
   this.config=config;this.calibration=calibration;board=new HexaBoard(config);observation=new Observation(board);
  }
  public Observation read(Pixels pixels,int frameWidth,int frameHeight,long timestampMs){return read(pixels,frameWidth,frameHeight,timestampMs,0,0,0,0);}
  public Observation read(Pixels pixels,int frameWidth,int frameHeight,long timestampMs,int excludedLeft,int excludedTop,int excludedRight,int excludedBottom){
   board.clear();observation.timestampMs=timestampMs;observation.usable=false;observation.confidence=0;
   float radius=Math.min(config.cellWidth(),config.cellHeight())*.41f,handRadius=Math.min(radius,(config.right-config.left)*.18f);
   if(pixels==null||frameWidth<1||frameHeight<1||timestampMs<0||config.right>frameWidth||config.bottom>frameHeight
      ||config.slotY(0)+handRadius>=frameHeight||config.slotX(0)-handRadius<0||config.slotX(2)+handRadius>=frameWidth){observation.reason="Hexa: board and all three hand slots must be inside the frame";return observation;}
   float handBottom=config.slotY(0)+handRadius;
   if(excludedLeft<config.right&&excludedRight>config.left&&excludedTop<handBottom&&excludedBottom>config.top){observation.reason="Hexa: overlay covers board or hand";return observation;}
   int known=0,total=0,loaded=0;
   for(int i=0;i<config.size();i++)if(config.active(i)){
    int row=i/config.cols,col=i%config.cols;float x=config.cellCenterX(row,col),y=config.cellCenterY(row,col);
    int value=sample(pixels,x,y,radius);board.cell(i,value);total++;if(value>=HexaBoard.EMPTY)known++;
   }
   for(int s=0;s<3;s++){int value=sample(pixels,config.slotX(s),config.slotY(s),handRadius);board.hand(s,value);if(value>0)loaded++;}
   observation.confidence=total==0?0:known/(float)total;
   observation.usable=known==total&&loaded>0;
   observation.reason=known!=total?"Hexa: unknown or ambiguous board stack":loaded==0?"Hexa: no recognised hand stack":"Hexa: calibrated board and hand";
   return observation;
  }
  private int sample(Pixels pixels,float x,float y,float radius){
   int center=pixels.get(Math.round(x),Math.round(y));
   if(matchesEmpty(center,calibration.emptyCenterArgb)){
    int rings=0,centers=0;
    for(int i=0;i<6;i++){
     int ring=pixels.get(Math.round(x+COS[i]*radius),Math.round(y+SIN[i]*radius));
     if(matchesEmpty(ring,calibration.emptyRingArgb)&&rgbDistance(center,ring)>=Math.max(20f,rgbDistance(calibration.emptyCenterArgb,calibration.emptyRingArgb)*.45f))rings++;
     int inner=pixels.get(Math.round(x+COS[i]*radius*.35f),Math.round(y+SIN[i]*radius*.35f));
     if(matchesEmpty(inner,calibration.emptyCenterArgb))centers++;
    }
    if(rings==6&&centers==6)return HexaBoard.EMPTY;
   }
   for(int i=0;i<votes.length;i++)votes[i]=0;
   int centerColor=color(center);if(centerColor<1)return HexaBoard.UNKNOWN;votes[centerColor]++;
   for(int i=0;i<6;i++){
    int value=color(pixels.get(Math.round(x+COS[i]*radius*.35f),Math.round(y+SIN[i]*radius*.35f)));
    if(value>0)votes[value]++;
   }
   return votes[centerColor]>=6?centerColor:HexaBoard.UNKNOWN;
  }
  private int color(int rgb){
   if(brightness(rgb)<40||saturation(rgb)<.18f)return -1;
   float best=Float.POSITIVE_INFINITY,second=Float.POSITIVE_INFINITY;int color=-1;
   for(int i=0;i<calibration.topColors.length;i++){
    float d=chromaDistance(rgb,calibration.topColors[i]);
    if(d<best){second=best;best=d;color=i+1;}else if(d<second)second=d;
   }
   return best<.095f&&second-best>.025f?color:-1;
  }
 }
 /** Calibration is a setup operation, outside the frame hot path. The caller identifies a proven empty cell. */
 public static Calibration calibrate(Pixels pixels,HexaConfig config,int emptyRow,int emptyCol){
  if(pixels==null||config==null||!config.active(emptyRow*config.cols+emptyCol))throw new IllegalArgumentException("hexa empty calibration cell");
  float x=config.cellCenterX(emptyRow,emptyCol),y=config.cellCenterY(emptyRow,emptyCol),radius=Math.min(config.cellWidth(),config.cellHeight())*.41f;
  int center=pixels.get(Math.round(x),Math.round(y)),ring=pixels.get(Math.round(x+radius),Math.round(y));
  for(int i=0;i<6;i++)if(!matchesEmpty(pixels.get(Math.round(x+COS[i]*radius),Math.round(y+SIN[i]*radius)),ring))throw new IllegalArgumentException("hexa empty outline is not consistent");
  int[] candidates=new int[16];int count=0;
  for(int i=0;i<config.size()+3;i++){
   float sx,sy;
   if(i<config.size()){if(!config.active(i))continue;sx=config.cellCenterX(i/config.cols,i%config.cols);sy=config.cellCenterY(i/config.cols,i%config.cols);}
   else{sx=config.slotX(i-config.size());sy=config.slotY(i-config.size());}
   int rgb=pixels.get(Math.round(sx),Math.round(sy));if(saturation(rgb)<.25f||brightness(rgb)<50||matchesEmpty(rgb,center))continue;
   boolean found=false;for(int c=0;c<count;c++)if(chromaDistance(rgb,candidates[c])<.075f){found=true;break;}
   if(!found){if(count==16)throw new IllegalArgumentException("too many hexa colours");candidates[count++]=rgb;}
  }
  if(count==0)throw new IllegalArgumentException("no hexa colours to calibrate");
  int[] colors=new int[count];System.arraycopy(candidates,0,colors,0,count);return new Calibration(center,ring,colors);
 }
 /** Conservative setup helper: only two matching neutral-centre, closed-outline cells establish empty geometry. */
 public static Calibration calibrateFromScene(Pixels pixels,HexaConfig config,int width,int height){
  if(pixels==null||config==null||config.left<0||config.top<0||config.right>width||config.bottom>height)return null;
  float radius=Math.min(config.cellWidth(),config.cellHeight())*.41f,handRadius=Math.min(radius,(config.right-config.left)*.18f);
  if(config.slotY(0)+handRadius>=height||config.slotX(0)-handRadius<0||config.slotX(2)+handRadius>=width)return null;
  for(int i=0;i<config.size();i++)if(config.active(i)){
   float x=config.cellCenterX(i/config.cols,i%config.cols),y=config.cellCenterY(i/config.cols,i%config.cols);
   int center=pixels.get(Math.round(x),Math.round(y));if(saturation(center)>.20f||brightness(center)<45)continue;
   int ring=pixels.get(Math.round(x+radius),Math.round(y));if(rgbDistance(center,ring)<35f)continue;
   boolean closed=true;for(int k=0;k<6;k++)if(!matchesEmpty(pixels.get(Math.round(x+COS[k]*radius),Math.round(y+SIN[k]*radius)),ring)){closed=false;break;}
   if(!closed)continue;
   for(int j=i+1;j<config.size();j++)if(config.active(j)){
    float bx=config.cellCenterX(j/config.cols,j%config.cols),by=config.cellCenterY(j/config.cols,j%config.cols);
    if(!matchesEmpty(pixels.get(Math.round(bx),Math.round(by)),center))continue;
    boolean same=true;for(int k=0;k<6;k++)if(!matchesEmpty(pixels.get(Math.round(bx+COS[k]*radius),Math.round(by+SIN[k]*radius)),ring)){same=false;break;}
    if(same)try{return calibrate(pixels,config,i/config.cols,i%config.cols);}catch(IllegalArgumentException unsupported){return null;}
   }
  }
  return null;
 }
 private static boolean matchesEmpty(int actual,int expected){
  int ab=brightness(actual),eb=brightness(expected);if(eb<15||ab<15)return rgbDistance(actual,expected)<20f;
  float ratio=ab/(float)eb;
  return ratio>.60f&&ratio<1.55f&&chromaDistance(actual,expected)<.045f;
 }
 private static int brightness(int rgb){return Math.max(rgb>>16&255,Math.max(rgb>>8&255,rgb&255));}
 private static float saturation(int rgb){int hi=brightness(rgb),lo=Math.min(rgb>>16&255,Math.min(rgb>>8&255,rgb&255));return hi==0?0:(hi-lo)/(float)hi;}
 private static float chromaDistance(int a,int b){
  float ar=a>>16&255,ag=a>>8&255,ab=a&255,br=b>>16&255,bg=b>>8&255,bb=b&255;
  float as=Math.max(1,ar+ag+ab),bs=Math.max(1,br+bg+bb);float r=ar/as-br/bs,g=ag/as-bg/bs,bl=ab/as-bb/bs;
  return (float)Math.sqrt(r*r+g*g+bl*bl);
 }
 private static float rgbDistance(int a,int b){int r=(a>>16&255)-(b>>16&255),g=(a>>8&255)-(b>>8&255),bl=(a&255)-(b&255);return (float)Math.sqrt(r*r+g*g+bl*bl);}
}
