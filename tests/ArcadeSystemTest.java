package org.localpilot.android;

import java.util.*;

/** Pixel-rendered closed-loop integration experiment. This is not an Android/game acceptance test. */
public final class ArcadeSystemTest {
 private static int checks;private static boolean trace;
 private static void check(boolean v,String m){checks++;if(!v)throw new AssertionError(m);}
 private static final int W=160,H=240;
 private static final class Canvas implements PixelBoardReader.Pixels {
  final int[] a=new int[W*H];
  Canvas(int color){Arrays.fill(a,color);}
  public int rgb(int x,int y){return a[y*W+x];}
  void rect(float l,float t,float r,float b,int c){
   int x0=Math.max(0,(int)(l*W)),x1=Math.min(W,(int)(r*W));
   int y0=Math.max(0,(int)(t*H)),y1=Math.min(H,(int)(b*H));
   for(int y=y0;y<y1;y++)for(int x=x0;x<x1;x++)a[y*W+x]=c;
  }
  void circle(float x,float y,float radius,int c){
   int cx=(int)(x*W),cy=(int)(y*H),rr=Math.max(1,(int)(radius*W));
   for(int yy=cy-rr;yy<=cy+rr;yy++)for(int xx=cx-rr;xx<=cx+rr;xx++)
    if(xx>=0&&xx<W&&yy>=0&&yy<H&&(xx-cx)*(xx-cx)+(yy-cy)*(yy-cy)<=rr*rr)a[yy*W+xx]=c;
  }
 }
 private record Snapshot(float y,float velocity,float playerX,float obstacleX,float obstacleY,float center,int safeLane){}
 private static final class Result {
  int passed,reads,usable,actions,deaths,paused,confirmed,unsafe;
  void add(Result r){passed+=r.passed;reads+=r.reads;usable+=r.usable;actions+=r.actions;deaths+=r.deaths;paused+=r.paused;confirmed+=r.confirmed;unsafe+=r.unsafe;}
  public String toString(){return "passed="+passed+" deaths="+deaths+" usable="+usable+"/"+reads+" actions="+actions+" visualConfirmed="+confirmed+" paused="+paused;}
 }
 public static void main(String[]args){
  long start=System.nanoTime();trace=args.length>0&&args[0].equals("trace");
  for(boolean flying:new boolean[]{false,true})for(boolean continuous:new boolean[]{false,true}){
   if(flying&&continuous)continue;
   Result baseline=new Result(),predictive=new Result();int cases=0;
   for(int latency:new int[]{40,100})for(int age:new int[]{0,80}){
    baseline.add(run(flying,false,latency,age,cases,continuous));predictive.add(run(flying,true,latency,age,cases,continuous));cases++;
   }
   check(baseline.unsafe==0&&predictive.unsafe==0,"input only from current usable scene");
   check(predictive.reads>10&&predictive.usable>10,"pixel perception participates");
   check(predictive.actions>0&&predictive.confirmed>0,"real rendered response, not ACK-only success");
   System.out.println("Pixel closed-loop "+(flying?"bird":continuous?"road-continuous":"road-discrete")+" scenarios="+cases+" baseline {"+baseline+"} predictive {"+predictive+"}");
  }
  System.out.printf(Locale.ROOT,"Arcade system: %d assertions PASS; wall=%.2fs. Synthetic images and physics; no phone success implied.%n",checks,(System.nanoTime()-start)/1e9);
 }
 private static Result run(boolean flying,boolean predictive,int latency,int age,int seed,boolean continuous){
  Result result=new Result();ArcadeProfile profile=flying?ArcadeProfile.FLAPPY:ArcadeProfile.TRAFFIC;
  ArcadeController controller=new ArcadeController();ArcadeFeedback feedback=new ArcadeFeedback();SceneRecovery recovery=new SceneRecovery();
  ArcadeObservation previous=null,before=null;ArcadePolicy.Decision pending=null;long token=0,ackAt=-1;
  float y=.25f,v=0,x=.5f,obstacleX=.9f,obstacleY=.18f,center=.25f;
  int safe=seed%3;float from=x,dest=x;long changeStart=-1,flapAt=-1,lastAction=-10000,steerEnd=-1;int steerDirection=0;
  float gravity=seed%2==0?1.2f:1.7f,impulse=-.44f,speed=.28f+seed*.07f;
  var history=new ArrayList<Snapshot>();var actionHistory=new ArrayDeque<String>();int maxFrames=1100;String deathReason="none";int deathFrame=-1;float perceivedLo=0,perceivedHi=0;
  for(int frame=0;frame<maxFrames;frame++){
   long now=1000+frame*20L;
   if(flying){
    if(flapAt>=0&&now>=flapAt){v=impulse;flapAt=-1;}
    y+=v*.02f+.5f*gravity*.02f*.02f;v+=gravity*.02f;obstacleX-=.28f*.02f;
    if(y<.065f||y>.468f){result.deaths++;deathReason=y<.065f?"ceiling":"floor";deathFrame=frame;break;}
    if(obstacleX<.133f&&obstacleX+.08f>.067f&&(y-.021f<center-.105f||y+.021f>center+.105f)){result.deaths++;deathReason="obstacle";deathFrame=frame;break;}
    if(obstacleX+.08f<.06f){result.passed++;obstacleX=.9f;center=new float[]{.24f,.31f,.22f,.29f}[result.passed%4];}
   }else{
    if(continuous){if(now>=changeStart&&now<steerEnd)x=Math.max(.3f,Math.min(.7f,x+steerDirection*.95f*.02f));}
    else if(changeStart>=0){float f=Math.max(0,Math.min(1,(now-changeStart)/220f));x=from+(dest-from)*f;}
    obstacleY+=speed*.02f;
    for(int lane=0;lane<3;lane++)if(lane!=safe&&Math.abs(x-(.3f+lane*.2f))<.102f&&obstacleY+.07f>.765f&&obstacleY-.07f<.915f){result.deaths++;deathReason="vehicle";deathFrame=frame;break;}
    if(result.deaths>0)break;
    if(obstacleY-.07f>1){result.passed++;obstacleY=.12f;safe=(safe+1+seed%2)%3;}
   }
   history.add(new Snapshot(y,v,x,obstacleX,obstacleY,center,safe));
   if(frame%4!=0||frame<age/20)continue;
   Snapshot observed=history.get(frame-age/20);Canvas image=render(flying,observed);
   boolean dropout=result.reads>0&&result.reads%37==0;if(dropout)image=new Canvas(0);
   long timestamp=now-age;result.reads++;
   ArcadeObservation detected=ArcadeVision.detect(profile,image,W,H,0,0,W,H,0,0,0,0,timestamp,
       previous!=null&&timestamp-previous.timestampMs<=500?previous:null);
   if(detected.usable()){result.usable++;previous=detected;}
   perceivedLo=detected.gapTop;perceivedHi=detected.gapBottom;
   ArcadePolicy.Decision decision=detected.usable()?(predictive?controller.decide(detected,now):ArcadePolicy.next(detected)):null;
   if(!recovery.observe(detected.usable(),timestamp))continue;
   if(ackAt>=0&&now>=ackAt){feedback.acknowledge(token);ackAt=-1;}
   long verified=feedback.verified();ArcadeFeedback.State response=feedback.observe(detected,now);
   if(feedback.verified()>verified){result.confirmed++;if(predictive)controller.observeGesture(pending,before,detected);before=null;pending=null;}
   if(response==ArcadeFeedback.State.PAUSED){result.paused++;break;}
   if(response==ArcadeFeedback.State.WAITING||ackAt>=0||decision==null||!decision.sendsInput())continue;
   if(now-lastAction<(predictive?0:ArcadePolicy.interval(profile)))continue;
   if(!detected.usable()||timestamp>now||now-timestamp>300){result.unsafe++;continue;}
   if(flying){if(decision.action!=ArcadePolicy.Action.FLAP||flapAt>=0)continue;flapAt=now+latency;}
   else{
    if(decision.action!=ArcadePolicy.Action.LEFT&&decision.action!=ArcadePolicy.Action.RIGHT)continue;
    from=x;int current=Math.max(0,Math.min(2,Math.round((x-.3f)/.2f)));
    int target=Math.max(0,Math.min(2,current+(decision.action==ArcadePolicy.Action.LEFT?-1:1)));
    dest=.3f+target*.2f;changeStart=now+latency;steerEnd=changeStart+decision.durationMs;steerDirection=decision.action==ArcadePolicy.Action.LEFT?-1:1;
   }
   lastAction=now;result.actions++;before=detected;pending=decision;
   actionHistory.add("t="+now+" y="+detected.player.y()+" vy="+detected.vy+" gap="+detected.gapTop+".."+detected.gapBottom+" action="+decision.action+" hold="+decision.durationMs);if(actionHistory.size()>10)actionHistory.removeFirst();
   token=feedback.dispatched(decision,detected,now);ackAt=now+Math.max(latency,decision.durationMs)+20;
   if(predictive)controller.actionDispatched(decision,now);
  }
  if(trace&&result.deaths>0)System.out.println("FAILURE "+profile+" predictive="+predictive+" continuous="+continuous+" latency="+latency+" frameAge="+age+" seed="+seed+" reason="+deathReason+" frame="+deathFrame+" x="+x+" safeLane="+safe+" carY="+obstacleY+" y="+y+" v="+v+" truegap="+(center-.105f)+".."+(center+.105f)+" perceivedgap="+perceivedLo+".."+perceivedHi+" actions="+actionHistory);
  return result;
 }
 private static Canvas render(boolean flying,Snapshot s){
  if(!flying){
   Canvas image=new Canvas(0x3d8f28);image.rect(.2f,0,.8f,1,0x7b7578);
   for(int lane=0;lane<3;lane++)if(lane!=s.safeLane){float cx=.3f+lane*.2f;image.rect(cx-.0525f,s.obstacleY-.07f,cx+.0525f,s.obstacleY+.07f,lane%2==0?0x126bd8:0x252525);}
   image.rect(s.playerX-.05f,.765f,s.playerX+.05f,.915f,0xe43020);image.rect(s.playerX-.035f,.80f,s.playerX+.035f,.85f,0x126bd8);
   return image;
  }
  Canvas image=new Canvas(0xffdfba);image.rect(0,.49f,1,1,0x9e5428);
  if(s.obstacleX<1&&s.obstacleX+.08f>0){
   for(float yy=.05f;yy<s.center-.105f;yy+=.03f)image.rect(s.obstacleX,yy,s.obstacleX+.08f,Math.min(yy+.012f,s.center-.105f),0x387e25);
   for(float yy=s.center+.105f;yy<.475f;yy+=.03f)image.rect(s.obstacleX,yy,s.obstacleX+.08f,Math.min(yy+.012f,.475f),0x387e25);
  }
  image.circle(.1f,s.y,.032f,0x454545);image.rect(.08f,s.y-.04f,.13f,s.y-.022f,0xc93020);return image;
 }
}
