package org.localpilot.android;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CancellationException;

/** Bounded, resolution-normalized perception for the three recorded Market games. */
public final class ArcadeVision {
 private static final int RED=1,WOOD=2,BLUE=4,LIGHT=8,DARK=16,GREEN=32,BALL=64,DARK_CAR=128,SATURATED=256;
 private ArcadeVision(){}

 public static ArcadeObservation detect(ArcadeProfile profile,PixelBoardReader.Pixels pixels,
   int width,int height,int left,int top,int right,int bottom,
   int excludedLeft,int excludedTop,int excludedRight,int excludedBottom,
   long timestampMs,ArcadeObservation previous){
  if(profile==null||profile==ArcadeProfile.ARI||pixels==null||width<1||height<1
    ||(long)width*height>8_000_000||left<0||top<0||right>width||bottom>height
    ||right-left<120||bottom-top<180||timestampMs<0
    ||previous!=null&&timestampMs<=previous.timestampMs)
   throw new IllegalArgumentException("arcade frame geometry/time");
  Frame f=new Frame(pixels,left,top,right,bottom,excludedLeft,excludedTop,excludedRight,excludedBottom);
  switch(profile){
   case PADDLE:return paddle(f,timestampMs,previous);
   case TRAFFIC:return traffic(f,timestampMs,previous);
   case FLAPPY:return flappy(f,timestampMs,previous);
   default:throw new IllegalArgumentException("unsupported profile");
  }
 }

 /** Always check the original scene/result first; at most one exposure retry per frame. */
 public static ArcadeObservation detectRecovering(ArcadeProfile profile,PixelBoardReader.Pixels pixels,
   int width,int height,int left,int top,int right,int bottom,int el,int et,int er,int eb,
   long timestamp,ArcadeObservation previous,SceneRecovery.Pass pass){
  ArcadeObservation raw=detect(profile,pixels,width,height,left,top,right,bottom,el,et,er,eb,timestamp,previous);
  if(raw.usable()||raw.terminal||pass==SceneRecovery.Pass.ORIGINAL)return raw;
  ArcadeObservation retry=detect(profile,SceneRecovery.pixels(pixels,pass),width,height,left,top,right,bottom,
    el,et,er,eb,timestamp,previous);
  return retry.usable()?retry:raw;
 }

 private static ArcadeObservation paddle(Frame f,long time,ArcadeObservation previous){
  List<Blob> balls=new ArrayList<>(),paddles=new ArrayList<>();
  for(Blob b:f.blobs(BALL))if(b.box.top>.065f&&b.box.bottom<.955f
    &&b.box.width()>=.014f&&b.box.width()<=.09f&&b.box.height()>=.008f
    &&b.aspect()>=.50f&&b.aspect()<=1.8f&&b.density()>.3f)balls.add(b);
  for(Blob b:f.blobs(WOOD))if(b.box.top>.76f&&b.box.bottom<.965f
    &&b.box.width()>.14f&&b.box.width()<.43f&&b.box.height()>.012f
    &&b.box.height()<.095f&&b.aspect()>1.8f&&b.density()>.18f)paddles.add(b);
  ArcadeObservation.Box ball=choose(balls,previous==null?null:previous.player,.5f,.55f);
  ArcadeObservation.Box paddle=choose(paddles,previous==null?null:previous.control,.5f,.86f);
  return observation(ArcadeProfile.PADDLE,time,ball,paddle,List.of(),0,.07f,1,.96f,0,0,
    ball!=null&&paddle!=null?.88f:0,false,previous);
 }

 private static ArcadeObservation traffic(Frame f,long time,ArcadeObservation previous){
  int[] road=road(f);float left=road[0]/(float)f.w,right=road[1]/(float)f.w;
  List<Blob> red=f.blobs(RED),colored=f.blobs(SATURATED),players=new ArrayList<>();
  // Paint alone does not identify the player: traffic can be red or green too.
  // Keep an observed identity and use the distinctive red/yellow hood only for
  // this known skin; an ambiguous unmarked scene requires manual calibration.
  if(right-left>.40f&&right-left<.86f)for(Blob b:colored)
   if(vehicle(b,left,right)&&b.box.top>.58f&&b.box.bottom>.72f&&b.box.bottom<.97f
     &&!fuelCan(f,b,left,right))players.add(b);
  int hint=previous!=null&&previous.profile==ArcadeProfile.TRAFFIC
    &&Math.abs(left-previous.left)<.015f&&Math.abs(right-previous.right)<.015f?previous.playerIdentity:0;
  ArcadeObservation trackedPrevious=hint!=0&&previous.usable()&&time-previous.timestampMs<=500?previous:null;
  Blob own=chooseTrafficPlayer(f,players,trackedPrevious,right-left,hint);
  ArcadeObservation.Box player=own==null?null:own.box;int identity=own==null?hint:trafficIdentity(f,own);
  List<ArcadeObservation.Box> obstacles=new ArrayList<>(),collectibles=new ArrayList<>();
  for(Blob b:red)if(fuelCan(f,b,left,right))addVehicle(collectibles,b.box);
  List<Blob> cars=new ArrayList<>(colored);cars.addAll(f.blobs(LIGHT));cars.addAll(f.blobs(DARK_CAR));
  for(Blob b:cars)if(vehicle(b,left,right)&&b.box.top>.13f&&!fuelCan(f,b,left,right)
    &&(player==null||overlap(b.box,player)<.45f)){
    // The same pickup may appear in several colour masks. A dark edge or
    // saturated body must not return as a collision hazard.
    boolean isPickup=false;
    for(ArcadeObservation.Box fuel:collectibles)if(overlap(fuel,b.box)>.38f){isPickup=true;break;}
    if(!isPickup)addVehicle(obstacles,b.box);
  }
  // A large pale result dialog covers the bottom road, unlike narrow lane marks.
  boolean terminal=right-left>.4f&&f.fraction(LIGHT,Math.max(left,.28f),.65f,Math.min(right,.75f),.95f)>.30f;
  ArcadeObservation base=observation(ArcadeProfile.TRAFFIC,time,player,player,obstacles,left,.13f,right,.97f,0,0,
    player!=null&&right-left>.4f?.90f:0,terminal,trackedPrevious);
  return new ArcadeObservation(base.profile,base.timestampMs,base.player,base.control,base.obstacles,collectibles,
    base.left,base.top,base.right,base.bottom,base.gapTop,base.gapBottom,base.vx,base.vy,base.confidence,base.tracked,base.terminal,terminal?0:identity);
 }
 private static Blob chooseTrafficPlayer(Frame f,List<Blob> players,ArcadeObservation previous,float roadWidth,int hint){
  Blob unique=null,marked=null;int candidates=0,marks=0;
  for(Blob b:players){
   if(b.box.width()<.065f||b.box.height()<.065f||b.aspect()>.95f)continue;
   int identity=trafficIdentity(f,b);if(identity==0)continue;
   // A lost/stale position cannot authorize motion, but an incompatible paint
   // cannot become the player merely because it is now the sole visible car.
   if(hint!=0&&((identity&255)!=(hint&255)||(hint&256)!=0&&(identity&256)==0))continue;
   if(previous!=null){
    ArcadeObservation.Box old=previous.player;
    if((identity&255)!=(previous.playerIdentity&255)||(previous.playerIdentity&256)!=0&&(identity&256)==0
      ||Math.abs(b.box.x()-old.x())>Math.min(.34f,roadWidth*.60f)
      ||Math.abs(b.box.y()-old.y())>Math.max(.025f,old.height()*.20f)
      ||b.box.width()<old.width()*.70f||b.box.width()>old.width()*1.4f
      ||b.box.height()<old.height()*.70f||b.box.height()>old.height()*1.4f)continue;
   }else if(b.box.bottom<.84f)continue;
   candidates++;unique=b;if((identity&256)!=0){marks++;marked=b;}
  }
  if(marks==1)return marked;
  return marks==0&&candidates==1?unique:null;
 }
 private static int trafficIdentity(Frame f,Blob car){
  int[] hues=new int[8];int samples=0;
  int l=Math.max(0,(int)(car.box.left*f.w)),r=Math.min(f.w,(int)Math.ceil(car.box.right*f.w));
  int t=Math.max(0,(int)(car.box.top*f.h)),b=Math.min(f.h,(int)Math.ceil(car.box.bottom*f.h));
  for(int y=t;y<b;y+=2)for(int x=l;x<r;x+=2){
   int rgb=f.rgb[y*f.w+x],red=rgb>>16&255,green=rgb>>8&255,blue=rgb&255;
   int hi=Math.max(red,Math.max(green,blue)),lo=Math.min(red,Math.min(green,blue)),d=hi-lo;
   if(hi<85||d<55||d/(float)hi<.35f)continue;
   float hue=hi==red?60f*(green-blue)/d:hi==green?120+60f*(blue-red)/d:240+60f*(red-green)/d;if(hue<0)hue+=360;
   int bucket=hue<18||hue>=345?1:hue<45?2:hue<80?3:hue<165?4:hue<210?5:hue<285?6:7;
   hues[bucket]++;samples++;
  }
  int paint=0;for(int i=1;i<hues.length;i++)if(hues[i]>hues[paint])paint=i;
  if(samples<8||hues[paint]<samples*.55f)return 0;
  if(paint!=1)return paint;
  float x0=car.box.left+car.box.width()*.15f,x1=car.box.right-car.box.width()*.15f;
  float y0=car.box.top+car.box.height()*.42f,y1=car.box.top+car.box.height()*.92f;
  int yellow=0,total=0;
  for(int y=Math.max(0,(int)(y0*f.h));y<Math.min(f.h,(int)(y1*f.h));y++)
   for(int x=Math.max(0,(int)(x0*f.w));x<Math.min(f.w,(int)(x1*f.w));x++){
    int c=f.rgb[y*f.w+x],red=c>>16&255,green=c>>8&255,blue=c&255;total++;
    if(red>150&&green>125&&blue<115&&red>green*.88f&&red<green*1.45f)yellow++;
   }
  boolean hood=total>0&&yellow/(float)total>.07f&&yellow/(float)total<.65f
    &&f.fraction(DARK,car.box.left+car.box.width()*.2f,car.box.top+car.box.height()*.12f,
      car.box.right-car.box.width()*.2f,car.box.top+car.box.height()*.42f)>.12f;
  return paint|(hood?256:0);
 }
 /** A red fuel body has a dark handle, often protruding from the upper-left.
  * Cars have wide dark windshields centered across their upper half. */
 private static boolean fuelCan(Frame f,Blob b,float left,float right){
  if(b.box.x()<left+.025f||b.box.x()>right-.025f||b.box.width()<.055f||b.box.width()>.16f
    ||b.box.height()<.04f||b.box.height()>.13f||b.aspect()<.65f||b.aspect()>2.1f||b.density()<.22f)return false;
  if(f.fraction(RED,b.box.left,b.box.top,b.box.right,b.box.bottom)<.28f)return false;
  float w=b.box.width(),h=b.box.height();
  float inset=f.fraction(DARK,b.box.left+w*.22f,b.box.top+h*.02f,
    b.box.right-w*.22f,b.box.top+h*.36f);
  float handle=f.fraction(DARK,b.box.left-w*.25f,b.box.top-h*.30f,
    b.box.left+w*.40f,b.box.top+h*.26f);
  float windshield=f.fraction(DARK,b.box.left+w*.22f,b.box.top+h*.20f,
    b.box.right-w*.22f,b.box.top+h*.49f);
  return (inset>.035f||handle>.055f)&&windshield<.30f;
 }
 private static boolean vehicle(Blob b,float left,float right){
  return b.box.x()>left+.025f&&b.box.x()<right-.025f
    &&b.box.width()>.043f&&b.box.width()<.22f&&b.box.height()>.043f&&b.box.height()<.25f
    &&b.aspect()>.23f&&b.aspect()<1.35f&&b.density()>.18f;
 }
 private static int[] road(Frame f){
  boolean[] columns=new boolean[f.w];int from=(int)(f.h*.20f),to=(int)(f.h*.61f);
  for(int x=0;x<f.w;x++){
   int neutral=0;
   for(int y=from;y<to;y++){
    int color=f.rgb[y*f.w+x],r=color>>16&255,g=color>>8&255,b=color&255;
    int high=Math.max(r,Math.max(g,b)),low=Math.min(r,Math.min(g,b));
    if(high-low<32&&high<180&&low>40)neutral++;
   }
   columns[x]=neutral>(to-from)*.56f;
  }
  // Dashed lane markers can split an otherwise continuous road component.
  for(int x=1;x<f.w-1;x++)if(!columns[x]&&columns[x-1]){
   int end=x;while(end<f.w&&!columns[end]&&end-x<f.w*.035f)end++;
   if(end<f.w&&columns[end])for(int j=x;j<end;j++)columns[j]=true;
  }
  int bestL=0,bestR=0;
  for(int x=0;x<f.w;){if(!columns[x]){x++;continue;}int start=x;while(x<f.w&&columns[x])x++;
   if(x-start>bestR-bestL&&start<f.w*.6f&&x>f.w*.4f){bestL=start;bestR=x;}}
  return new int[]{bestL,bestR};
 }

 private static ArcadeObservation flappy(Frame f,long time,ArcadeObservation previous){
  float floor=.49f;boolean foundFloor=false;
   // Market counter/background occupies roughly the bottom third of the
   // recorded scene. Even with an unusually low detected wood boundary,
   // never mine fruit-shelf pixels below 68% as gameplay obstacles.
  for(int y=(int)(f.h*.40f);y<f.h*.70f;y++){
   float row=y/(float)f.h;
   // A shop shelf can be brown across one row. The counter remains wooden
   // below the boundary; require two separated support rows before accepting it.
   if(f.fraction(WOOD,.05f,row,.95f,Math.min(1,(y+3f)/f.h))>.80f
     &&f.fraction(WOOD,.05f,row+.02f,.95f,row+.024f)>.75f
     &&f.fraction(WOOD,.05f,row+.05f,.95f,row+.054f)>.75f){
    floor=Math.min(.68f,y/(float)f.h);foundFloor=true;break;
   }
  }
  List<Blob> dark=f.blobs(DARK),red=f.blobs(RED),players=new ArrayList<>();
  for(Blob b:dark)if(b.box.x()<.38f&&b.box.width()>.035f&&b.box.width()<.22f
    &&b.box.top>.035f&&b.box.bottom<floor&&b.box.height()>.012f
    &&b.box.height()<.11f&&b.aspect()>.4f&&b.aspect()<3.0f&&b.density()>.25f){
   for(Blob hat:red)if(hat.box.x()<.28f&&hat.box.width()>.012f&&hat.box.width()<.12f
     &&Math.abs(hat.box.x()-b.box.x())<Math.max(.035f,b.box.width()*.60f)
     &&Math.abs(hat.box.y()-b.box.y())<.07f){
    players.add(b);break;
   }
  }
  ArcadeObservation.Box player=choose(players,previous==null?null:previous.player,.105f,.22f);
  List<Column> columns=new ArrayList<>();
  for(Blob b:f.blobs(GREEN))if(b.box.top>.035f&&b.box.bottom<Math.min(floor,.68f)
    &&b.box.width()>.033f&&b.box.width()<.145f&&b.box.height()>.002f
    &&b.box.height()<.028f&&b.aspect()>1.5f&&b.density()>.25f){
   Column group=null;for(Column c:columns)if(Math.abs(c.x-b.box.x())<.03f){group=c;break;}
   if(group==null){group=new Column(b.box.x());columns.add(group);}group.bands.add(b.box);
  }
  columns.sort(Comparator.comparingDouble(c->c.x));
  float gapTop=.06f,gapBottom=floor;boolean gapFound=false;
  List<ArcadeObservation.Box> obstacles=new ArrayList<>();
  for(Column c:columns){
   if(c.bands.isEmpty())continue;
   // A short single food stack is still a real obstacle. Requiring two green
   // slices removed valid scenes for >1 second; keep its occupied band instead
   // of calling the visible column empty sky. Thin background fragments abstain.
   if(c.bands.size()==1&&c.bands.get(0).width()<.045f)continue;
   c.bands.sort(Comparator.comparingDouble(b->b.top));
   List<float[]> occupied=new ArrayList<>();
   for(ArcadeObservation.Box b:c.bands){
    float lo=Math.max(.035f,b.top-.023f),hi=Math.min(floor,b.bottom+.023f);
    if(!occupied.isEmpty()&&lo<=occupied.get(occupied.size()-1)[1]+.02f)
     occupied.get(occupied.size()-1)[1]=Math.max(hi,occupied.get(occupied.size()-1)[1]);
    else occupied.add(new float[]{lo,hi});
   }
   float start=.045f,bestLo=start,bestHi=start,bestScore=Float.POSITIVE_INFINITY;
   // Stay in a reachable passage; the largest hole can require an impossible dive.
   float predicted=player==null?.22f:player.y();
   if(previous!=null&&previous.tracked)predicted+=Math.max(-.04f,Math.min(.04f,previous.vy*.12f));
   float clearance=player==null?.04f:player.height()/2+.012f;
   for(int i=0;i<=occupied.size();i++){
    float end=i<occupied.size()?occupied.get(i)[0]:floor;
    if(end-start>clearance*2){
     float safeLo=start+clearance,safeHi=end-clearance;
     float distance=Math.max(0,Math.max(safeLo-predicted,predicted-safeHi));
     float score=distance-.015f*Math.min(.3f,end-start);
     if(score<bestScore){bestScore=score;bestLo=start;bestHi=end;}
    }
    if(i<occupied.size()){
     float[] interval=occupied.get(i);start=Math.max(start,interval[1]);
     obstacles.add(new ArcadeObservation.Box(Math.max(0,c.x-.045f),interval[0],Math.min(1,c.x+.045f),interval[1]));
    }
   }
   if(!gapFound&&player!=null&&c.x+.045f>=player.right-.01f&&bestHi-bestLo>clearance*2){
    gapTop=bestLo;gapBottom=bestHi;gapFound=true;
   }
  }
  boolean terminal=f.fraction(LIGHT,.27f,.43f,.74f,.57f)>.50f;
  return observation(ArcadeProfile.FLAPPY,time,player,player,obstacles,0,.045f,1,floor,gapTop,gapBottom,
    player!=null&&foundFloor&&(gapFound||columns.isEmpty())?.84f:0,terminal,previous);
 }
 private static final class Column{final float x;final List<ArcadeObservation.Box> bands=new ArrayList<>();Column(float x){this.x=x;}}

 private static ArcadeObservation observation(ArcadeProfile p,long t,ArcadeObservation.Box player,
   ArcadeObservation.Box control,List<ArcadeObservation.Box> obstacles,float l,float top,float r,float bottom,
   float gapTop,float gapBottom,float confidence,boolean terminal,ArcadeObservation previous){
  float vx=0,vy=0;boolean tracked=false;
  if(previous!=null&&previous.profile==p&&previous.player!=null&&player!=null){
   float seconds=(t-previous.timestampMs)/1000f;
   if(seconds>=.015f&&seconds<=.75f&&Math.abs(player.x()-previous.player.x())<.35f
     &&Math.abs(player.y()-previous.player.y())<.40f){
    vx=(player.x()-previous.player.x())/seconds;vy=(player.y()-previous.player.y())/seconds;tracked=true;
   }
  }
  return new ArcadeObservation(p,t,player,control,obstacles,l,top,r,bottom,gapTop,gapBottom,vx,vy,confidence,tracked,terminal);
 }
 private static ArcadeObservation.Box choose(List<Blob> blobs,ArcadeObservation.Box previous,float x,float y){
  Blob best=null;float score=Float.MAX_VALUE;
  for(Blob b:blobs){float dx=b.box.x()-(previous==null?x:previous.x()),dy=b.box.y()-(previous==null?y:previous.y());
   float distance=dx*dx+dy*dy;if(distance<score){score=distance;best=b;}}
  return best==null?null:best.box;
 }
 /** Shared colored parts (windshield/body) are one vehicle, not competing hazards. */
 private static float overlap(ArcadeObservation.Box a,ArcadeObservation.Box b){
  float area=Math.max(0,Math.min(a.right,b.right)-Math.max(a.left,b.left))*Math.max(0,Math.min(a.bottom,b.bottom)-Math.max(a.top,b.top));
  return area/Math.max(.000001f,Math.min(a.width()*a.height(),b.width()*b.height()));
 }
 private static void addVehicle(List<ArcadeObservation.Box> vehicles,ArcadeObservation.Box candidate){
  for(int i=0;i<vehicles.size();i++){
   ArcadeObservation.Box current=vehicles.get(i);
   if(overlap(current,candidate)>.45f){
    vehicles.set(i,new ArcadeObservation.Box(Math.min(current.left,candidate.left),Math.min(current.top,candidate.top),
      Math.max(current.right,candidate.right),Math.max(current.bottom,candidate.bottom)));return;
   }
  }
  vehicles.add(candidate);
 }
 private static final class Blob {
  final ArcadeObservation.Box box;final int count,width,height;
  Blob(ArcadeObservation.Box box,int count,int width,int height){this.box=box;this.count=count;this.width=width;this.height=height;}
  float density(){return count/(float)(width*height);}
  float aspect(){return width/(float)height;}
 }
 private static final class Frame {
  final int w,h;final int[] rgb;final short[] mask;
  private short[] visited;private int[] queue;
  Frame(PixelBoardReader.Pixels source,int l,int t,int r,int b,int el,int et,int er,int eb){
   int step=Math.max(1,(r-l+479)/480);w=(r-l+step-1)/step;h=(b-t+step-1)/step;
   if((long)w*h>600_000)throw new IllegalArgumentException("arcade sample limit");
   rgb=new int[w*h];mask=new short[w*h];
   for(int y=0;y<h;y++){checkCancelled();for(int x=0;x<w;x++){
    int px=l+x*step,py=t+y*step,index=y*w+x;
    if(er>el&&eb>et&&px>=el&&px<er&&py>=et&&py<eb)continue;
    int color=source.rgb(px,py);rgb[index]=color;
    int red=color>>16&255,green=color>>8&255,blue=color&255;
    int high=Math.max(red,Math.max(green,blue)),low=Math.min(red,Math.min(green,blue));
    if(red>85&&red>green*1.65f&&red>blue*1.45f&&green<145)mask[index]|=RED;
    // A dark wood grain can satisfy RED; the paddle ball needs a saturated red core.
    if(red>85&&red>green*2.5f&&red>blue*2.2f&&green<100&&blue<100)mask[index]|=BALL;
    if(red>100&&green>45&&blue<150&&red>green*1.22f&&green>blue*1.12f&&red-blue>45)mask[index]|=WOOD;
    if(blue>85&&blue>red*1.3f&&blue>green*1.08f)mask[index]|=BLUE;
    if(low>185)mask[index]|=LIGHT;
    if(high<125&&high-low<65&&high>12)mask[index]|=DARK;
    if(high<65&&high-low<30&&high>12)mask[index]|=DARK_CAR;
    if(green>50&&green>red*1.12f&&green>blue*1.15f)mask[index]|=GREEN;
    if(high>100&&high-low>48)mask[index]|=SATURATED;
   }}
  }
  float fraction(int bit,float l,float t,float r,float b){
   int x0=Math.max(0,(int)(l*w)),x1=Math.min(w,(int)(r*w));
   int y0=Math.max(0,(int)(t*h)),y1=Math.min(h,(int)Math.ceil(b*h)),count=0;
   if(x1<=x0||y1<=y0)return 0;
   for(int y=y0;y<y1;y++)for(int x=x0;x<x1;x++)if((mask[y*w+x]&bit)!=0)count++;
   return count/(float)((x1-x0)*(y1-y0));
  }
  List<Blob> blobs(int bit){
   List<Blob> out=new ArrayList<>();
   // One bounded workspace per frame, reused for all color channels.
   if(visited==null){visited=new short[mask.length];queue=new int[mask.length];}
   for(int start=0;start<mask.length;start++){
    if((start&4095)==0)checkCancelled();if((visited[start]&bit)!=0||(mask[start]&bit)==0)continue;
    int head=0,tail=1,minX=start%w,maxX=minX,minY=start/w,maxY=minY;queue[0]=start;visited[start]|=bit;
    while(head<tail){
     if((head&4095)==0)checkCancelled();int at=queue[head++],x=at%w,y=at/w;
     minX=Math.min(minX,x);maxX=Math.max(maxX,x);minY=Math.min(minY,y);maxY=Math.max(maxY,y);
     for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++){
      int nx=x+dx,ny=y+dy;if(nx<0||ny<0||nx>=w||ny>=h)continue;int n=ny*w+nx;
      if((visited[n]&bit)==0&&(mask[n]&bit)!=0){visited[n]|=bit;queue[tail++]=n;}
     }
    }
    if(tail<3)continue;
    out.add(new Blob(new ArcadeObservation.Box(minX/(float)w,minY/(float)h,(maxX+1f)/w,(maxY+1f)/h),tail,maxX-minX+1,maxY-minY+1));
   }
   return out;
  }
 }
 private static void checkCancelled(){if(Thread.currentThread().isInterrupted())throw new CancellationException("arcade vision cancelled");}
}
