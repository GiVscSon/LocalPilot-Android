package org.localpilot.android;

import java.util.Arrays;

/** Scene-level regression cases; these are not scores from a phone. */
public final class ArcadeVisionTemporalTest {
 private static int checks;
 private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
 private static final class Scene implements PixelBoardReader.Pixels {
  final int w,h;final int[] px;
  Scene(int w,int h,int color){this.w=w;this.h=h;px=new int[w*h];Arrays.fill(px,color);}
  public int rgb(int x,int y){return px[y*w+x];}
  void rect(float l,float t,float r,float b,int color){for(int y=(int)(t*h);y<(int)(b*h);y++)for(int x=(int)(l*w);x<(int)(r*w);x++)px[y*w+x]=color;}
  void circle(float x,float y,float radius,int color){int cx=(int)(x*w),cy=(int)(y*h),rr=(int)(radius*w);for(int j=cy-rr;j<=cy+rr;j++)for(int i=cx-rr;i<=cx+rr;i++)if(i>=0&&i<w&&j>=0&&j<h&&(i-cx)*(i-cx)+(j-cy)*(j-cy)<=rr*rr)px[j*w+i]=color;}
  ArcadeObservation read(ArcadeProfile p,long time,ArcadeObservation previous){return ArcadeVision.detect(p,this,w,h,0,0,w,h,0,0,0,0,time,previous);}
 }
 public static void main(String[] args)throws Exception{
  for(int width:new int[]{240,480}){
   Scene road=new Scene(width,width*3/2,0x3d8f28);road.rect(.2f,0,.8f,1,0x7b7578);
   road.rect(.44f,.75f,.56f,.93f,0xe43020);road.rect(.46f,.79f,.54f,.85f,0x126bd8);
   ArcadeObservation own=road.read(ArcadeProfile.TRAFFIC,1000,null);
   check(own.usable(),"car including windshield remains recognized");
   check(own.obstacles.isEmpty(),"player's blue windshield is not a nearby enemy");
   road.rect(.44f,.35f,.56f,.53f,0xe43020);road.rect(.46f,.39f,.54f,.45f,0x126bd8);
   ArcadeObservation enemy=road.read(ArcadeProfile.TRAFFIC,1125,own);
   check(enemy.obstacles.size()==1,"enemy body and windshield deduplicate");
   Scene flying=new Scene(width,width*3/2,0xffdfba);flying.rect(0,.49f,1,1,0x9e5428);
   flying.circle(.1f,.335f,.025f,0x454545);flying.rect(.08f,.29f,.13f,.31f,0xc93020);
   // Large top hole .045..~.20, narrower reachable hole ~.26..~.40.
   for(float y:new float[]{.23f,.43f})flying.rect(.56f,y,.64f,y+.012f,0x387e25);
   ArcadeObservation s=flying.read(ArcadeProfile.FLAPPY,1000,null);
   check(s.usable(),"bird plus two obstacles recognized");
   check(s.gapTop>.24f&&s.gapBottom>.38f,"passage chosen near bird instead of largest unreachable hole");
   Scene oneBand=new Scene(width,width*3/2,0xffdfba);oneBand.rect(0,.49f,1,1,0x9e5428);
   oneBand.circle(.1f,.26f,.025f,0x454545);oneBand.rect(.08f,.215f,.13f,.235f,0xc93020);
   // A brown shelf crossing one row must not replace the real counter boundary.
   oneBand.rect(.04f,.40f,.96f,.409f,0x9e5428);
   oneBand.rect(.56f,.40f,.64f,.412f,0x387e25);
   ArcadeObservation single=oneBand.read(ArcadeProfile.FLAPPY,1000,null);
   check(single.usable(),"single visible food band does not disable flying");
   check(single.bottom>.47f,"thin background shelf is not the counter floor");
   check(single.obstacles.size()==1&&single.gapBottom<.4f,"single band retained as obstacle, not empty sky");
   ArcadeObservation next=flying.read(ArcadeProfile.FLAPPY,1125,s);
   check(next.tracked,"position tracking retained across distinct frames");
  }
  // Counter fruit near y=.80 must never enter the playable obstacle column.
  Scene shop=new Scene(480,720,0xffdfba);
  shop.rect(0,.68f,1,1,0x9e5428);
  shop.circle(.1f,.26f,.025f,0x454545);shop.rect(.08f,.215f,.13f,.235f,0xc93020);
  shop.rect(.58f,.39f,.67f,.402f,0x387e25); // active food skewer
  shop.rect(.76f,.79f,.86f,.81f,0x387e25);  // background produce below counter
  shop.rect(.82f,.82f,.90f,.84f,0x387e25);
  ArcadeObservation confined=shop.read(ArcadeProfile.FLAPPY,1000,null);
  check(confined.usable()&&confined.bottom<=.68f+.005f,"counter defines strict upper gameplay region");
  check(confined.obstacles.size()==1,"fruit shop below counter never becomes extra obstacle");
  ArcadeVision.Readout diagnostic=ArcadeVision.inspect(ArcadeProfile.FLAPPY,shop,shop.w,shop.h,
    0,0,shop.w,shop.h,0,0,0,0,1000,null);
  check(diagnostic.counterConfirmed()&&diagnostic.observation().usable()==confined.usable(),
    "diagnostic uses same detector and confirmed counter");
  check(diagnostic.belowCounterRejected()==2,"two candidate-like background fruit blobs rejected below counter");
  ArcadeVision.Readout empty=ArcadeVision.inspect(ArcadeProfile.FLAPPY,(x,y)->0xffdfba,
    480,720,0,0,480,720,0,0,0,0,1000,null);
  check(empty.belowCounterRejected()==0&&!empty.counterConfirmed(),"diagnostic state cannot leak between frames");
  if(args.length>0){
   for(int n:new int[]{26,27,28,29,30,32,36,37,38,39}){
    var path=java.nio.file.Path.of(args[0],"FLAPPY",String.format(java.util.Locale.ROOT,"%04d.jpg",n));
    var image=javax.imageio.ImageIO.read(path.toFile());int w=image.getWidth(),h=image.getHeight();
    var s=ArcadeVision.detect(ArcadeProfile.FLAPPY,image::getRGB,w,h,0,0,w,h,(int)(w*.17f),(int)(h*.13f),(int)(w*.4f),(int)(h*.18f),1000,null);
    check(s.usable(),"reviewed active frame must remain usable: "+n);
    check(!s.obstacles.isEmpty(),"single food stack stays represented: "+n);
    check(s.bottom>.45f&&s.bottom<.54f,"shop shelf must not replace floor: "+n);
   }
  }
  System.out.println("Arcade vision temporal: "+checks+" assertions PASS (synthetic scenes + optional private real frames)");
 }
}
