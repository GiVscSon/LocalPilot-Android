package org.localpilot.android;
import java.util.*;
/** Closed-loop physics experiments; deliberately not acceptance evidence for any phone game. */
public class ArcadeControllerTest {
 private static int checks;
 private static void check(boolean b,String message){checks++;if(!b)throw new AssertionError(message);}
 private static ArcadeObservation.Box box(float x,float y,float w,float h){return new ArcadeObservation.Box(x-w/2,y-h/2,x+w/2,y+h/2);}
 private static ArcadeObservation bird(long t,float y,float v,float gapLo,float gapHi,List<ArcadeObservation.Box> obstacles){
  var p=box(.12f,y,.055f,.045f);
  return new ArcadeObservation(ArcadeProfile.FLAPPY,t,p,p,obstacles,0,.04f,1,.62f,gapLo,gapHi,0,v,.9f,true,false);
 }
 private static ArcadeObservation road(long t,float x,List<ArcadeObservation.Box> obstacles){
  var p=box(x,.84f,.10f,.13f);
  return new ArcadeObservation(ArcadeProfile.TRAFFIC,t,p,p,obstacles,.2f,.08f,.8f,.98f,0,0,0,0,.9f,true,false);
 }
 private static ArcadeObservation roadWithFuel(float x,List<ArcadeObservation.Box> obstacles,float fuelX){
  var p=box(x,.84f,.10f,.13f);
  return new ArcadeObservation(ArcadeProfile.TRAFFIC,1000,p,p,obstacles,List.of(box(fuelX,.55f,.08f,.06f)),
    .2f,.08f,.8f,.98f,0,0,0,0,.9f,true,false);
 }
 private static boolean overlap(ArcadeObservation.Box a,ArcadeObservation.Box b){return a.left<b.right&&a.right>b.left&&a.top<b.bottom&&a.bottom>b.top;}
 public static void main(String[] args){
  invariants();
  int baselineBird=0,baselineFeedbackBird=0,newBird=0,integratedBird=0,total=0;
  for(float gravity:new float[]{.9f,1.3f,1.8f,2.3f})for(float impulse:new float[]{-.34f,-.44f,-.56f})for(int latency:new int[]{40,100})for(int frameAge:new int[]{0,80,160}){
   baselineBird+=flight(false,false,gravity,impulse,latency,frameAge);baselineFeedbackBird+=flight(false,true,gravity,impulse,latency,frameAge);newBird+=flight(true,false,gravity,impulse,latency,frameAge);integratedBird+=flight(true,true,gravity,impulse,latency,frameAge);total++;
  }
  System.out.println("Closed-loop bird simulations: predictive gates="+newBird+", baseline gates="+baselineBird+", baseline+feedback gates="+baselineFeedbackBird+", integrated gates="+integratedBird+", scenarios="+total);
  check(newBird>baselineBird,"predictive bird must outperform baseline across physics sweep");
  int baselineRoad=0,baselineFeedbackRoad=0,newRoad=0,integratedRoad=0;
  for(float speed:new float[]{.28f,.45f,.7f,.95f})for(int latency:new int[]{40,100,160})for(int seed=0;seed<4;seed++){
   baselineRoad+=drive(false,false,speed,latency,seed);baselineFeedbackRoad+=drive(false,true,speed,latency,seed);newRoad+=drive(true,false,speed,latency,seed);integratedRoad+=drive(true,true,speed,latency,seed);
  }
  System.out.println("Closed-loop road simulations: predictive rows="+newRoad+", baseline rows="+baselineRoad+", baseline+feedback rows="+baselineFeedbackRoad+", integrated rows="+integratedRoad+", scenarios=48");
  check(integratedBird>baselineFeedbackBird,"predictive advantage with identical feedback");
  check(integratedRoad>baselineFeedbackRoad,"predictive road advantage with identical feedback");
  check(integratedBird>baselineBird,"integrated feedback bird improves baseline");
  check(integratedRoad>=baselineRoad,"integrated feedback road improves baseline");
  check(newRoad>=baselineRoad,"predictive road must not regress obstacle survival in sweep");
  int continuousOld=0,continuousNew=0;
  for(float speed:new float[]{.35f,.65f,.95f})for(float steer:new float[]{.6f,1f,1.4f})for(int seed=0;seed<4;seed++){
   continuousOld+=continuous(false,speed,steer,seed);continuousNew+=continuous(true,speed,steer,seed);
  }
  System.out.println("Continuous steering hold simulations: predictive rows="+continuousNew+", baseline+feedback rows="+continuousOld+", scenarios=36");
  check(continuousNew>continuousOld,"held steering improves continuous-actuation model");
  timing();
  System.out.println("Arcade controller: "+checks+" assertions PASS. Simulations do not establish physical-device performance.");
 }
 private static void timing(){
  var controller=new ArcadeController();long[] times=new long[600];
  for(int i=0;i<times.length;i++){
   var sample=bird(1000+i*80,.3f,.1f,.12f,.48f,List.of(new ArcadeObservation.Box(.55f,.045f,.62f,.12f),new ArcadeObservation.Box(.55f,.48f,.62f,.62f)));
   long start=System.nanoTime();controller.decide(sample,sample.timestampMs+80);times[i]=System.nanoTime()-start;
  }
  Arrays.sort(times);System.out.printf(java.util.Locale.ROOT,"Controller CPU on cloud JVM only: p50=%.3fms p95=%.3fms max=%.3fms%n",times[300]/1e6,times[570]/1e6,times[599]/1e6);
 }
 private static void invariants(){
  ArcadeController c=new ArcadeController();
  check(!c.decide(null,0).sendsInput(),"missing scene");
  check(!c.decide(bird(1000,.3f,.2f,.1f,.5f,List.of()),1500).sendsInput(),"stale scene");
  var s=road(1000,.5f,List.of(box(.5f,.66f,.11f,.15f),box(.3f,.84f,.11f,.15f)));
  check(c.decide(s,1000).action==ArcadePolicy.Action.RIGHT,"side-by-side car blocks swept transition");
  c.reset();var action=c.decide(road(1000,.5f,List.of(box(.5f,.5f,.11f,.15f))),1000);
  check(action.sendsInput(),"approaching threat avoided");c.actionDispatched(action,1000);
  check(!c.decide(road(1100,.5f,List.of(box(.5f,.55f,.11f,.15f))),1100).sendsInput(),"pending lane change does not repeat");
  c.reset();check(!c.decide(bird(1000,.065f,-.2f,.06f,.4f,List.of()),1000).sendsInput(),"ceiling guard");
  c.reset();check(!c.decide(bird(1000,.28f,-.31f,.22f,.39f,List.of()),1000).sendsInput(),"already rising into passage does not re-flap");
  c.reset();var impulseAction=new ArcadePolicy.Decision(ArcadePolicy.Action.FLAP,.62f,.3f,"test");c.actionDispatched(impulseAction,1000);String impulseModel=c.model();
  c.observeGesture(impulseAction,bird(1000,.3f,.4f,.1f,.5f,List.of()),bird(1080,.32f,-.1f,.1f,.5f,List.of()));
  check(c.model().equals(impulseModel),"mixed pre/post-flap velocity must not weaken learned impulse");
  c.reset();var first=c.decide(road(1000,.5f,List.of(box(.5f,.5f,.11f,.15f))),1000);c.actionDispatched(first,1000);
  check(!c.decide(road(1320,.38f,List.of()),1320).sendsInput(),"unknown discrete input does not get a centering tap from one partial observation");
  c.reset();check(c.decide(roadWithFuel(.5f,List.of(),.3f),1000).action==ArcadePolicy.Action.LEFT,"reachable fuel in a clear adjacent lane attracts the car");
  c.reset();check(!c.decide(roadWithFuel(.5f,List.of(box(.3f,.84f,.11f,.15f)),.3f),1000).sendsInput(),"fuel never justifies crossing a car beside the player");
  c.reset();check(!c.decide(roadWithFuel(.3f,List.of(),.7f),1000).sendsInput(),"fuel two lanes away does not cause an unreachable maneuver");
 }
 private static int flight(boolean predictive,boolean integrated,float g,float impulse,int latency,int frameAge){
  ArcadeController controller=new ArcadeController();ArcadeFeedback feedback=new ArcadeFeedback();long feedbackToken=0,ackAt=-1;
  float dt=.02f,y=.3f,v=0,gateX=.95f,gateCenter=.31f;int passed=0;
  float[] ys=new float[2000],vs=new float[2000],xs=new float[2000],cs=new float[2000];
  long clock=1000,nextObserve=1000,pendingAt=-1,lastAction=-10000;ArcadeObservation last=null,before=null;ArcadePolicy.Decision pending=null;
  for(int frame=0;frame<1800;frame++,clock+=20){
   if(pendingAt>=0&&clock>=pendingAt){v=impulse;pendingAt=-1;}
   y+=v*dt+.5f*g*dt*dt;v+=g*dt;gateX-=.28f*dt;
   if(y<.063f||y>.597f)return passed;
   float gapLo=gateCenter-.115f,gapHi=gateCenter+.115f;
   if(gateX<.16f&&gateX+.065f>.09f&&(y-.0225f<gapLo||y+.0225f>gapHi))return passed;
   if(gateX+.065f<.09f){passed++;gateX=.95f;gateCenter=new float[]{.25f,.38f,.29f,.36f}[passed%4];}
   ys[frame]=y;vs[frame]=v;xs[frame]=gateX;cs[frame]=gateCenter;
   if(clock<nextObserve||frame<frameAge/20)continue;nextObserve=clock+80;
   int observed=frame-frameAge/20;float observedX=xs[observed],observedCenter=cs[observed];
   var obstacles=new ArrayList<ArcadeObservation.Box>();if(observedX>0&&observedX+.065f<1){obstacles.add(new ArcadeObservation.Box(observedX,.045f,observedX+.065f,observedCenter-.115f));obstacles.add(new ArcadeObservation.Box(observedX,observedCenter+.115f,observedX+.065f,.62f));}
   var s=bird(clock-frameAge,ys[observed],vs[observed],observedCenter-.115f,observedCenter+.115f,obstacles);
   if(predictive&&before!=null&&pending!=null&&clock-lastAction>=latency+80){controller.observeGesture(pending,before,s);pending=null;}
   var decision=predictive?controller.decide(s,clock):ArcadePolicy.next(s);
   if(integrated){if(ackAt>=0&&clock>=ackAt){feedback.acknowledge(feedbackToken);ackAt=-1;}var state=feedback.observe(s,clock);if(state==ArcadeFeedback.State.WAITING||state==ArcadeFeedback.State.PAUSED)continue;}
   if(decision.action==ArcadePolicy.Action.FLAP&&pendingAt<0&&clock-lastAction>=140){pendingAt=clock+latency;lastAction=clock;before=s;pending=decision;if(predictive)controller.actionDispatched(decision,clock);if(integrated){feedbackToken=feedback.dispatched(decision,s,clock);ackAt=clock+decision.durationMs+20;}}
   last=s;
  }
  return passed;
 }
 private static int continuous(boolean predictive,float speed,float steering,int seed){
  var random=new Random(seed);var controller=new ArcadeController();var feedback=new ArcadeFeedback();
  float x=.5f;int direction=0,passed=0;long clock=1000,nextObserve=1000,lastAction=-10000,start=-1,end=-1,ackAt=-1,token=0;
  ArcadePolicy.Decision pending=null;ArcadeObservation before=null;
  var cars=new ArrayList<ArcadeObservation.Box>();int safe=random.nextInt(3);
  for(int lane=0;lane<3;lane++)if(lane!=safe)cars.add(box(.3f+lane*.2f,.25f,.105f,.14f));
  for(int frame=0;frame<1800;frame++,clock+=20){
   if(clock>=start&&clock<end)x=Math.max(.25f,Math.min(.75f,x+direction*steering*.02f));
   var updated=new ArrayList<ArcadeObservation.Box>();for(var car:cars){float cy=car.y()+speed*.02f;if(cy+.07f<1)updated.add(box(car.x(),cy,car.width(),car.height()));}cars=updated;
   var player=box(x,.84f,.1f,.13f);for(var car:cars)if(overlap(player,car))return passed;
   if(cars.isEmpty()){passed++;safe=random.nextInt(3);for(int lane=0;lane<3;lane++)if(lane!=safe)cars.add(box(.3f+lane*.2f,.15f,.105f,.14f));}
   if(clock<nextObserve)continue;nextObserve=clock+80;
   var s=road(clock,x,cars);var action=predictive?controller.decide(s,clock):ArcadePolicy.next(s);
   if(ackAt>=0&&clock>=ackAt){feedback.acknowledge(token);ackAt=-1;}
   long wasVerified=feedback.verified();var state=feedback.observe(s,clock);
   if(predictive&&feedback.verified()>wasVerified&&pending!=null)controller.observeGesture(pending,before,s);
   if(state==ArcadeFeedback.State.WAITING||state==ArcadeFeedback.State.PAUSED)continue;
   if((action.action==ArcadePolicy.Action.LEFT||action.action==ArcadePolicy.Action.RIGHT)&&clock-lastAction>=180){
    direction=action.action==ArcadePolicy.Action.LEFT?-1:1;start=clock+60;end=start+action.durationMs;ackAt=end+20;lastAction=clock;
    pending=action;before=s;token=feedback.dispatched(action,s,clock);if(predictive)controller.actionDispatched(action,clock);
   }
  }
  return passed;
 }
 private static int drive(boolean predictive,boolean integrated,float speed,int latency,int seed){
  Random random=new Random(seed);ArcadeController controller=new ArcadeController();ArcadeFeedback feedback=new ArcadeFeedback();long feedbackToken=0,ackAt=-1;
  float x=.5f,from=x,dest=x,changeStart=-100;long clock=1000,nextObserve=1000,lastAction=-10000;int passed=0;
  var cars=new ArrayList<ArcadeObservation.Box>();
  int safe=random.nextInt(3);for(int lane=0;lane<3;lane++)if(lane!=safe)cars.add(box(.3f+lane*.2f,.25f,.105f,.14f));
  for(int frame=0;frame<2200;frame++,clock+=20){
   if(changeStart>=0){float fraction=Math.max(0,Math.min(1,(clock-changeStart)/220));x=from+(dest-from)*fraction;}
   var updated=new ArrayList<ArcadeObservation.Box>();for(var car:cars){float cy=car.y()+speed*.02f;if(cy+.07f<1)updated.add(box(car.x(),cy,car.width(),car.height()));}
   cars=updated;var p=box(x,.84f,.1f,.13f);for(var car:cars)if(overlap(p,car))return passed;
   if(cars.isEmpty()){passed++;safe=random.nextInt(3);for(int lane=0;lane<3;lane++)if(lane!=safe)cars.add(box(.3f+lane*.2f,.15f,.105f,.14f));}
   if(clock<nextObserve)continue;nextObserve=clock+80;
   var s=road(clock,x,cars);var d=predictive?controller.decide(s,clock):ArcadePolicy.next(s);
   if(integrated){if(ackAt>=0&&clock>=ackAt){feedback.acknowledge(feedbackToken);ackAt=-1;}var state=feedback.observe(s,clock);if(state==ArcadeFeedback.State.WAITING||state==ArcadeFeedback.State.PAUSED)continue;}
   if((d.action==ArcadePolicy.Action.LEFT||d.action==ArcadePolicy.Action.RIGHT)&&clock-lastAction>350){from=x;int current=Math.max(0,Math.min(2,Math.round((x-.3f)/.2f)));int target=Math.max(0,Math.min(2,current+(d.action==ArcadePolicy.Action.LEFT?-1:1)));dest=.3f+target*.2f;changeStart=clock+latency;lastAction=clock;if(predictive)controller.actionDispatched(d,clock);if(integrated){feedbackToken=feedback.dispatched(d,s,clock);ackAt=clock+d.durationMs+20;}}
  }
  return passed;
 }
}
