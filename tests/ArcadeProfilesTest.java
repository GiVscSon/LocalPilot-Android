package org.localpilot.android;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CancellationException;
import javax.imageio.ImageIO;

/** Policy invariants, synthetic perception and optional replay of private user recordings. */
public final class ArcadeProfilesTest {
 private static int checks;
 public static void main(String[] args)throws Exception{
  policies();perception();
  System.out.println("arcade-profiles: "+checks+" assertions PASS");
  if(args.length>0)replay(Path.of(args[0]));
  if(args.length>1)recordedTallPhone(Path.of(args[1]));
 }
 private static void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
 private static void near(float value,float expected,float delta,String label){check(Math.abs(value-expected)<delta,label+" got "+value);}
 private static ArcadeObservation.Box box(float x,float y,float width,float height){return new ArcadeObservation.Box(x-width/2,y-height/2,x+width/2,y+height/2);}
 private static ArcadeObservation state(ArcadeProfile profile,ArcadeObservation.Box player,ArcadeObservation.Box control,
   List<ArcadeObservation.Box> obstacles,float vx,float vy,boolean tracked,float gapTop,float gapBottom){
  return new ArcadeObservation(profile,1000,player,control,obstacles,profile==ArcadeProfile.TRAFFIC?.2f:0,.045f,
   profile==ArcadeProfile.TRAFFIC?.8f:1,profile==ArcadeProfile.FLAPPY?.49f:.97f,gapTop,gapBottom,vx,vy,.9f,tracked,false);
 }
 private static void policies(){
  ArcadeObservation.Box ball=box(.65f,.65f,.04f,.03f),paddle=box(.5f,.85f,.24f,.04f);
  ArcadeObservation s=state(ArcadeProfile.PADDLE,ball,paddle,List.of(),.5f,1,true,0,0);
  ArcadePolicy.Decision move=ArcadePolicy.next(s);
  check(move.action==ArcadePolicy.Action.MOVE_PADDLE&&move.x>.7f,"paddle extrapolates descending trajectory");
  ArcadeObservation resting=state(ArcadeProfile.PADDLE,box(.4f,.825f,.044f,.032f),box(.4f,.854f,.288f,.058f),List.of(),0,0,true,0,0);
  ArcadePolicy.Decision serve=ArcadePolicy.next(resting);
  check(serve.action==ArcadePolicy.Action.LAUNCH,"stationary ball on basket launches (user report)");
  check(!ArcadePolicy.response(serve,resting,resting),"ACK alone does not confirm serve");
  check(ArcadePolicy.response(serve,resting,state(ArcadeProfile.PADDLE,box(.4f,.72f,.044f,.032f),resting.control,List.of(),0,-.2f,true,0,0)),"moving ball confirms serve");
  check(ArcadePolicy.next(state(ArcadeProfile.PADDLE,box(.4f,.5f,.044f,.032f),resting.control,List.of(),0,0,true,0,0)).action!=ArcadePolicy.Action.LAUNCH,"stationary distant ball is not a serve");
  near(ArcadePolicy.reflect(1.3f,0,1),.7f,.001f,"right wall reflection");
  near(ArcadePolicy.reflect(-.3f,0,1),.3f,.001f,"left wall reflection");
  near(ArcadePolicy.reflect(4.3f,0,1),.3f,.001f,"multiple wall reflections");
  check(!ArcadePolicy.next(state(ArcadeProfile.PADDLE,box(.5f,.65f,.04f,.03f),paddle,List.of(),0,1,true,0,0)).sendsInput(),"aligned paddle waits");
  check(ArcadePolicy.response(move,s,state(ArcadeProfile.PADDLE,ball,box(.62f,.85f,.24f,.04f),List.of(),0,1,true,0,0)),"paddle motion confirmed");
  check(!ArcadePolicy.response(move,s,s),"unchanged paddle not confirmed");

  ArcadeObservation.Box car=box(.5f,.85f,.12f,.18f),enemy=box(.5f,.6f,.12f,.16f);
  ArcadeObservation traffic=state(ArcadeProfile.TRAFFIC,car,car,List.of(enemy),0,0,true,0,0);
  ArcadePolicy.Decision left=ArcadePolicy.next(traffic);
  check(left.action==ArcadePolicy.Action.LEFT,"traffic chooses clear left lane");
  check(ArcadePolicy.next(state(ArcadeProfile.TRAFFIC,car,car,List.of(enemy,box(.3f,.6f,.12f,.16f)),0,0,true,0,0)).action==ArcadePolicy.Action.RIGHT,"blocked left chooses right");
  check(!ArcadePolicy.next(state(ArcadeProfile.TRAFFIC,car,car,List.of(enemy,box(.3f,.6f,.12f,.16f),box(.7f,.6f,.12f,.16f)),0,0,true,0,0)).sendsInput(),"no escape lane does not oscillate");
  check(!ArcadePolicy.next(state(ArcadeProfile.TRAFFIC,car,car,List.of(box(.5f,.99f,.1f,.01f)),0,0,true,0,0)).sendsInput(),"passed car ignored");
  check(!ArcadePolicy.next(state(ArcadeProfile.TRAFFIC,car,car,List.of(),0,0,true,0,0)).sendsInput(),"clear lane waits");
  ArcadeObservation.Box carLeft=box(.3f,.85f,.12f,.18f);
  check(ArcadePolicy.response(left,traffic,state(ArcadeProfile.TRAFFIC,carLeft,carLeft,List.of(),0,0,true,0,0)),"left response confirmed");
  check(!ArcadePolicy.response(left,traffic,state(ArcadeProfile.TRAFFIC,box(.7f,.85f,.12f,.18f),box(.7f,.85f,.12f,.18f),List.of(),0,0,true,0,0)),"wrong direction rejected");
  check(!ArcadePolicy.response(left,traffic,traffic),"stationary car rejected");

  ArcadeObservation.Box bird=box(.1f,.3f,.08f,.05f);
  ArcadeObservation falling=state(ArcadeProfile.FLAPPY,bird,bird,List.of(),0,.3f,true,.10f,.32f);
  ArcadePolicy.Decision flap=ArcadePolicy.next(falling);
  check(flap.action==ArcadePolicy.Action.FLAP,"falling below gap target flaps");
  check(!ArcadePolicy.next(state(ArcadeProfile.FLAPPY,bird,bird,List.of(),0,-.3f,true,.1f,.32f)).sendsInput(),"rising bird waits");
  check(!ArcadePolicy.next(state(ArcadeProfile.FLAPPY,bird,bird,List.of(),0,.3f,false,.1f,.32f)).sendsInput(),"one frame cannot estimate lift");
  check(!ArcadePolicy.next(state(ArcadeProfile.FLAPPY,bird,bird,List.of(),0,.3f,true,.2f,.25f)).sendsInput(),"narrow unconfirmed gap rejected");
  var lifted=box(.1f,.29f,.08f,.05f);
  check(ArcadePolicy.response(flap,falling,new ArcadeObservation(ArcadeProfile.FLAPPY,1200,lifted,lifted,List.of(),0,.045f,1,.49f,.1f,.32f,0,-.3f,.9f,true,false)),"later lift response confirmed");
  check(!ArcadePolicy.response(flap,falling,falling),"unchanged falling state rejected");
  check(!ArcadePolicy.next(null).sendsInput(),"missing scene never sends input");
 }
 private static final class Scene implements PixelBoardReader.Pixels {
  final int w,h;final int[] pixels;
  Scene(int w,int h,int color){this.w=w;this.h=h;pixels=new int[w*h];Arrays.fill(pixels,color);}
  public int rgb(int x,int y){return pixels[y*w+x];}
  void rect(float l,float t,float r,float b,int color){for(int y=(int)(t*h);y<(int)(b*h);y++)for(int x=(int)(l*w);x<(int)(r*w);x++)pixels[y*w+x]=color;}
  void circle(float x,float y,float radius,int color){int cx=(int)(x*w),cy=(int)(y*h),rr=(int)(radius*w);for(int yy=cy-rr;yy<=cy+rr;yy++)for(int xx=cx-rr;xx<=cx+rr;xx++)if(xx>=0&&xx<w&&yy>=0&&yy<h&&(xx-cx)*(xx-cx)+(yy-cy)*(yy-cy)<=rr*rr)pixels[yy*w+xx]=color;}
  ArcadeObservation read(ArcadeProfile p,long time,ArcadeObservation previous){return ArcadeVision.detect(p,this,w,h,0,0,w,h,0,0,0,0,time,previous);}
 }
 private static void perception(){
  for(ArcadeProfile p:List.of(ArcadeProfile.PADDLE,ArcadeProfile.TRAFFIC,ArcadeProfile.FLAPPY))
   check(!new Scene(480,720,0).read(p,1000,null).usable(),"black capture rejected "+p);
  for(int width:new int[]{480,960}){
   Scene scene=new Scene(width,width*3/2,0x80c9ec);scene.circle(.7f,.5f,.025f,0xb40d18);scene.rect(.4f,.82f,.68f,.87f,0xb56d32);
   ArcadeObservation s=scene.read(ArcadeProfile.PADDLE,1000,null);check(s.usable(),"paddle scene detected "+width);
   near(s.player.x(),.7f,.01f,"ball x normalized");near(s.control.x(),.54f,.01f,"paddle x normalized");
   ArcadeObservation excluded=ArcadeVision.detect(ArcadeProfile.PADDLE,scene,width,width*3/2,0,0,width,width*3/2,
     (int)(width*.6f),(int)(width*3/2*.4f),(int)(width*.8f),(int)(width*3/2*.6f),1000,null);
   check(!excluded.usable(),"overlay excluded from ball detection");
  }
  for(int width:new int[]{324,480}){
   Scene tall=new Scene(width,width*20/9,0x80c9ec);
   tall.rect(.442f,.274f,.475f,.294f,0x713d2a); // Dark wood satisfies the old generic red mask.
   tall.circle(.5f,.882f,.025f,0x870909);tall.rect(.36f,.89f,.64f,.925f,0xb56d32);
   ArcadeObservation actual=tall.read(ArcadeProfile.PADDLE,1000,null);
   check(actual.usable(),"tall phone paddle scene detected "+width);
   near(actual.player.y(),.882f,.015f,"wooden brick is not the ball");
   check(!ArcadePolicy.next(actual).sendsInput(),"aligned real ball does not chase wooden brick");
   tall.rect(.46f,.86f,.54f,.89f,0x80c9ec);
   check(!tall.read(ArcadeProfile.PADDLE,1125,actual).usable(),"wooden brick alone cannot authorize paddle input");
  }
  Scene road=new Scene(480,720,0x3d8f28);road.rect(.2f,0,.8f,1,0x7b7578);
  road.rect(.44f,.75f,.56f,.93f,0xe43020);road.rect(.44f,.45f,.56f,.61f,0x126bd8);
  ArcadeObservation driving=road.read(ArcadeProfile.TRAFFIC,1000,null);
  check(driving.usable(),"traffic scene detected");check(driving.obstacles.size()==1,"blue traffic obstacle detected");
  check(ArcadePolicy.next(driving).action==ArcadePolicy.Action.LEFT,"vision feeds lane policy");
  road.rect(.28f,.65f,.75f,.95f,0xffffff);ArcadeObservation result=road.read(ArcadeProfile.TRAFFIC,1125,driving);
  check(result.terminal&&!ArcadePolicy.next(result).sendsInput(),"traffic result dialog blocks input");

  Scene openSky=new Scene(480,720,0xffdfba);openSky.rect(0,.49f,1,1,0x9e5428);
  openSky.circle(.1f,.26f,.04f,0x454545);openSky.rect(.08f,.21f,.13f,.235f,0xc93020);
  ArcadeObservation openFirst=openSky.read(ArcadeProfile.FLAPPY,1000,null);
  check(openFirst.usable(),"bird can be tracked before first obstacle");
  ArcadeObservation openSecond=openSky.read(ArcadeProfile.FLAPPY,1200,openFirst);
  check(ArcadePolicy.next(openSecond).action==ArcadePolicy.Action.FLAP,"bird maintains height in open sky");
  Scene darkRoad=new Scene(480,720,0x3d8f28);darkRoad.rect(.2f,0,.8f,1,0x7b7578);
  darkRoad.rect(.44f,.75f,.56f,.93f,0xe43020);darkRoad.rect(.44f,.25f,.56f,.41f,0x252525);
  ArcadeObservation darkTraffic=darkRoad.read(ArcadeProfile.TRAFFIC,1000,null);
  check(darkTraffic.usable()&&!darkTraffic.obstacles.isEmpty(),"dark car detected");
  check(ArcadePolicy.next(darkTraffic).action==ArcadePolicy.Action.LEFT,"early avoidance of dark car");
  Scene flappy=new Scene(480,720,0xffdfba);flappy.rect(0,.49f,1,1,0x9e5428);
  flappy.circle(.1f,.26f,.04f,0x454545);flappy.rect(.08f,.21f,.13f,.235f,0xc93020);
  for(float y:new float[]{.08f,.12f,.37f,.41f})flappy.rect(.56f,y,.64f,y+.012f,0x387e25);
  ArcadeObservation flying=flappy.read(ArcadeProfile.FLAPPY,1000,null);
  check(flying.usable(),"flappy bird/food/counter detected");
  check(flying.gapTop>.14f&&flying.gapBottom<.37f,"food passage located");
  check(!ArcadePolicy.next(flying).sendsInput(),"first visual frame waits for speed");
  flappy.rect(.27f,.43f,.74f,.57f,0xffffff);ArcadeObservation finished=flappy.read(ArcadeProfile.FLAPPY,1125,flying);
  check(finished.terminal&&!ArcadePolicy.next(finished).sendsInput(),"flappy result dialog blocks input");
  try{ArcadeVision.detect(ArcadeProfile.PADDLE,null,480,720,0,0,480,720,0,0,0,0,1000,null);throw new AssertionError("null pixels accepted");}
  catch(IllegalArgumentException expected){checks++;}
  try{road.read(ArcadeProfile.TRAFFIC,1000,driving);throw new AssertionError("repeated timestamp accepted");}
  catch(IllegalArgumentException expected){checks++;}
  Thread.currentThread().interrupt();try{road.read(ArcadeProfile.TRAFFIC,1200,null);throw new AssertionError("cancel ignored");}
  catch(CancellationException expected){checks++;}finally{Thread.interrupted();}
 }

 private static void recordedTallPhone(Path path)throws Exception{
  BufferedImage original=ImageIO.read(path.toFile());
  for(int width:new int[]{324,480})for(SceneRecovery.Pass pass:SceneRecovery.Pass.values()){
   int height=Math.round(original.getHeight()*width/(float)original.getWidth());
   BufferedImage image=new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB);
   var graphics=image.createGraphics();graphics.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
   graphics.drawImage(original,0,0,width,height,null);graphics.dispose();
   ArcadeObservation s=ArcadeVision.detectRecovering(ArcadeProfile.PADDLE,image::getRGB,width,height,0,0,width,height,0,0,0,0,1000,null,pass);
   check(s.usable(),"recorded tall phone ball and basket");near(s.player.x(),.5f,.025f,"recorded red ball x");near(s.player.y(),.882f,.025f,"recorded red ball y");
   near(s.control.x(),.5f,.025f,"recorded basket x");check(!ArcadePolicy.next(s).sendsInput(),"recorded aligned basket waits");
  }
  System.out.println("Recorded tall phone:30 assertions PASS; no gestures dispatched");
 }

 private static void replay(Path root)throws Exception{
  for(ArcadeProfile profile:List.of(ArcadeProfile.PADDLE,ArcadeProfile.TRAFFIC,ArcadeProfile.FLAPPY)){
   List<Path> frames;try(var stream=Files.list(root.resolve(profile.name()))){frames=stream.filter(p->p.getFileName().toString().matches("[0-9]{4}\\.jpg")).sorted().toList();}
   if(frames.isEmpty())throw new AssertionError("No replay images for "+profile);
   ArcadeObservation previous=null;int valid=0,actions=0,terminal=0;List<Long> times=new ArrayList<>();
   for(int i=0;i<frames.size();i++){
    BufferedImage image=ImageIO.read(frames.get(i).toFile());int w=image.getWidth(),h=image.getHeight();long start=System.nanoTime();
    ArcadeObservation s=ArcadeVision.detect(profile,image::getRGB,w,h,0,0,w,h,(int)(w*.17f),(int)(h*.13f),(int)(w*.4f),(int)(h*.18f),1000+i*125L,previous);
    times.add(System.nanoTime()-start);ArcadePolicy.Decision d=ArcadePolicy.next(s);
    if(s.usable())valid++;if(s.terminal)terminal++;if(d.sendsInput())actions++;
    check(!d.sendsInput()||s.usable()&&Float.isFinite(d.x)&&Float.isFinite(d.y)&&d.x>0&&d.x<1&&d.y>0&&d.y<1,"replay action bounds "+frames.get(i));
    if(i==0)check(!d.sendsInput(),"tutorial/loading first frame has no input "+profile);
    String name=frames.get(i).getFileName().toString();
    if(profile==ArcadeProfile.PADDLE&&name.equals("0033.jpg")){check(s.usable(),"recorded paddle scene");near(s.control.x(),.23f,.04f,"recorded basket position");}
    if(profile==ArcadeProfile.TRAFFIC&&name.equals("0073.jpg"))check(s.usable()&&s.obstacles.size()>=1&&d.action==ArcadePolicy.Action.LEFT,"recorded approaching car chooses free lane");
    if(profile==ArcadeProfile.TRAFFIC&&name.equals("0093.jpg"))check(s.terminal&&!d.sendsInput(),"recorded traffic result");
    if(profile==ArcadeProfile.FLAPPY&&name.equals("0033.jpg"))check(s.usable()&&s.player.x()>.06f&&s.player.x()<.15f,"recorded Gosha player and gap");
    if(profile==ArcadeProfile.FLAPPY&&name.equals("0065.jpg"))check(s.terminal&&!d.sendsInput(),"recorded Gosha result");
    previous=s;
   }
   times.sort(Long::compare);System.out.printf(Locale.ROOT,"replay %s frames=%d usable=%d recommendations=%d result_frames=%d desktop_p50_ms=%.2f desktop_p95_ms=%.2f%n",profile,frames.size(),valid,actions,terminal,times.get((times.size()-1)/2)/1e6,times.get((int)Math.ceil(times.size()*.95)-1)/1e6);
  }
  System.out.println("Replay assertions PASS; recommendations are not dispatched, live-game success is UNVERIFIED.");
 }
}
