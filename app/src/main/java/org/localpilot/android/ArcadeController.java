package org.localpilot.android;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Temporal model-predictive control in normalized game coordinates. No Android dependency. */
public final class ArcadeController {
 private ArcadeObservation previous;
 private float trafficSpeed=.55f, scrollSpeed=.28f, gravity=1.5f, flapVelocity=-.44f;
 private long lastFlap=-10000,lastLane=-10000;
 private int intendedLane=-1,lastHold=150;
 private float steeringSpeed=1.1f,laneStartX;
 private boolean continuousSteering;
 public void reset(){previous=null;trafficSpeed=.55f;scrollSpeed=.28f;gravity=1.5f;flapVelocity=-.44f;lastFlap=lastLane=-10000;intendedLane=-1;steeringSpeed=1.1f;lastHold=150;continuousSteering=false;}
 /** New episode in the same window: keep learned physics, discard old objects/actions. */
 public void nextRound(){previous=null;lastFlap=lastLane=-10000;intendedLane=-1;}
 /** A loss is an outcome, not a measurement of gravity or flap impulse. */
 public void penalize(){nextRound();}
 public ArcadePolicy.Decision decide(ArcadeObservation s,long now){
  if(s==null||!s.usable()||now<s.timestampMs||now-s.timestampMs>450)return waitFor("Ожидаю свежую игровую сцену");
  if(previous!=null&&(previous.profile!=s.profile||s.timestampMs<previous.timestampMs))reset();
  learn(s,now);previous=s;
  return switch(s.profile){case TRAFFIC->drive(s,now);case FLAPPY->fly(s,now);default->ArcadePolicy.next(s);};
 }
 /** Call only after dispatch was accepted, not merely when a recommendation was produced. */
 public void actionDispatched(ArcadePolicy.Decision d,long now){
  if(d==null)return;
  if(d.action==ArcadePolicy.Action.FLAP)lastFlap=now;
  if(d.action==ArcadePolicy.Action.LEFT||d.action==ArcadePolicy.Action.RIGHT){lastLane=now;lastHold=d.durationMs;laneStartX=previous==null?0:previous.player.x();
   if(previous!=null){int current=lane(previous.player.x(),previous),direction=d.action==ArcadePolicy.Action.LEFT?-1:1;
    boolean finishing=intendedLane==current&&Math.abs(previous.player.x()-center(current,previous))>.025f
      &&(center(current,previous)-previous.player.x())*direction>0;
    if(!finishing)intendedLane=Math.max(0,Math.min(2,current+direction));
   }else intendedLane=-1;}
 }
 /** Measured response improves the motion model; an Android ACK alone is not evidence. */
 public void observeGesture(ArcadePolicy.Decision action,ArcadeObservation before,ArcadeObservation after){
  if(action==null||before==null||after==null||before.profile!=after.profile||!after.usable())return;
  if((action.action==ArcadePolicy.Action.LEFT||action.action==ArcadePolicy.Action.RIGHT)&&action.durationMs>0){
   float dx=Math.abs(after.player.x()-before.player.x());
   if(dx>.008f&&dx<.3f)steeringSpeed=blend(steeringSpeed,clamp(dx/(action.durationMs/1000f),.3f,2.5f),.2f);
  }

 }
 public String report(){return model();}
 public void restore(String saved){
  if(saved==null||saved.isEmpty())return;
  float road=trafficSpeed,scroll=scrollSpeed,g=gravity,flap=flapVelocity,steering=steeringSpeed;boolean continuous=continuousSteering;
  try{for(String p:saved.split(" ")){String[] kv=p.split("=",2);if(kv.length!=2)continue;
   if(kv[0].equals("continuous")){if(!kv[1].equals("true")&&!kv[1].equals("false"))return;continuous=Boolean.parseBoolean(kv[1]);continue;}
   float v=Float.parseFloat(kv[1]);if(!Float.isFinite(v))return;
   switch(kv[0]){case "roadSpeed":if(v<.025f||v>2.5f)return;road=v;break;case "scroll":if(v<.025f||v>2.5f)return;scroll=v;break;case "gravity":if(v<.25f||v>5)return;g=v;break;case "flap":if(v< -1.2f||v> -.15f)return;flap=v;break;case "steering":if(v<.3f||v>2.5f)return;steering=v;break;}
  }}catch(RuntimeException ignored){return;}
  trafficSpeed=road;scrollSpeed=scroll;gravity=g;flapVelocity=flap;steeringSpeed=steering;continuousSteering=continuous;
 }
 public String model(){return "roadSpeed="+trafficSpeed+" scroll="+scrollSpeed+" gravity="+gravity+" flap="+flapVelocity+" steering="+steeringSpeed+" continuous="+continuousSteering;}
 private void learn(ArcadeObservation s,long now){
  if(previous==null)return;
  float dt=(s.timestampMs-previous.timestampMs)/1000f;
  if(dt<.03f||dt>.65f)return;
  List<Float> speeds=new ArrayList<>();
  for(ArcadeObservation.Box b:s.obstacles){
   ArcadeObservation.Box best=null;float bestError=Float.MAX_VALUE;
   for(ArcadeObservation.Box a:previous.obstacles){
    float move=s.profile==ArcadeProfile.TRAFFIC?b.y()-a.y():a.x()-b.x();
    float cross=s.profile==ArcadeProfile.TRAFFIC?Math.abs(a.x()-b.x()):Math.abs(a.y()-b.y());
    if(move<0||move/dt>2.5f||cross>.04f||Math.abs(a.width()-b.width())>.05f||Math.abs(a.height()-b.height())>.07f)continue;
    float error=cross*3+move;
    if(error<bestError){bestError=error;best=a;}
   }
   if(best!=null)speeds.add((s.profile==ArcadeProfile.TRAFFIC?b.y()-best.y():best.x()-b.x())/dt);
  }
  if(!speeds.isEmpty()){
   speeds.sort(Float::compare);float measured=speeds.get(speeds.size()/2);
   if(measured>.025f){if(s.profile==ArcadeProfile.TRAFFIC)trafficSpeed=blend(trafficSpeed,measured,.45f);else scrollSpeed=blend(scrollSpeed,measured,.35f);}
  }
  if(s.profile==ArcadeProfile.FLAPPY&&s.tracked){
   float since=(s.timestampMs-lastFlap)/1000f;
   // First finite-difference response can mix ascent with pre-command descent.
   // Wait until the entire velocity sample is beyond normal input latency.
   if(since>=.20f&&since<=.48f&&previous.timestampMs>=lastFlap+120&&s.vy<-.04f){
    float measured=s.vy-gravity*Math.max(0,since-.06f-dt*.5f);
    flapVelocity=blend(flapVelocity,clamp(measured,-1.2f,-.15f),.18f);
   }
  }
  if(s.profile==ArcadeProfile.FLAPPY&&previous.tracked&&s.tracked&&now-lastFlap>220){
   float measured=(s.vy-previous.vy)/dt;
   if(measured>.25f&&measured<5f)gravity=blend(gravity,measured,.3f);
  }
 }
 private ArcadePolicy.Decision drive(ArcadeObservation s,long now){
  int current=lane(s.player.x(),s);
  // Infer continuous steering only from a settled, partial-lane response. A short
  // correction is unsafe for games where every tap advances one entire lane.
  if(intendedLane>=0&&now-lastLane>lastHold+180&&s.tracked&&Math.abs(s.vx)<.08f){
   float travelled=Math.abs(s.player.x()-laneStartX),laneWidth=(s.right-s.left)/3;
   if(travelled>.03f&&travelled<laneWidth*.72f)continuousSteering=true;
  }
  if(intendedLane>=0&&Math.abs(s.player.x()-center(intendedLane,s))<(s.right-s.left)*.06f)intendedLane=-1;
  if(intendedLane>=0&&now-lastLane<Math.max(300,lastHold+120))return waitFor("Проверяю перестроение");
  float age=Math.max(0,(now-s.timestampMs)/1000f)+.05f;
  float[] costs=new float[3];
  for(int dest=0;dest<3;dest++){
   if(Math.abs(dest-current)>1){costs[dest]=Float.POSITIVE_INFINITY;continue;}
   float bestPath=Float.POSITIVE_INFINITY;
   // Plan a second adjacent move: an empty outer lane may require traversing the middle.
   for(int finalLane=Math.max(0,dest-1);finalLane<=Math.min(2,dest+1);finalLane++){
    float cost=dest==current?0:.12f;
    if(finalLane!=dest)cost+=.14f;
    for(float t=0;t<=1.25f;t+=.065f){
     float phase=clamp((t-.08f)/.24f,0,1);
     float x=s.player.x()+(center(dest,s)-s.player.x())*phase;
     if(t>.42f)x=center(dest,s)+(center(finalLane,s)-center(dest,s))*clamp((t-.42f)/.24f,0,1);
     for(ArcadeObservation.Box b:s.obstacles){
      float top=b.top+trafficSpeed*(t+age),bottom=b.bottom+trafficSpeed*(t+age);
      if(top>s.player.bottom+.02f||bottom<s.player.top-.025f)continue;
      float margin=s.player.width()*.58f;
      if(b.left<x+margin&&b.right>x-margin)cost+=35f/(.22f+t);
     }
    }
    for(ArcadeObservation.Box b:s.obstacles){
     float arrival=(s.player.top-b.bottom)/Math.max(.1f,trafficSpeed)-age;
     if(arrival>1.25f&&arrival<1.8f&&Math.abs(b.x()-center(finalLane,s))<s.player.width()*.6f+b.width()*.5f)cost+=.16f/(arrival+.1f);
    }
    bestPath=Math.min(bestPath,cost);
   }
   costs[dest]=bestPath;
  }
  int best=current;
  for(int dest=Math.max(0,current-1);dest<=Math.min(2,current+1);dest++)if(costs[dest]+.03f<costs[best])best=dest;
  if(best==current){
   if(continuousSteering&&intendedLane==current&&Math.abs(s.player.x()-center(current,s))>.025f){
    boolean left=center(current,s)<s.player.x();
    int hold=(int)clamp(Math.abs(center(current,s)-s.player.x())/steeringSpeed*1000,70,160);
    return new ArcadePolicy.Decision(left?ArcadePolicy.Action.LEFT:ArcadePolicy.Action.RIGHT,left?.25f:.75f,.72f,"Выравниваю машину в безопасной полосе",hold);
   }
   return waitFor("Свободная траектория · оцениваю время до столкновения");
  }
  if(now-lastLane<180)return waitFor("Завершаю перестроение");
  int hold=(int)clamp(Math.abs(center(best,s)-s.player.x())/steeringSpeed*1000,100,180);
  return new ArcadePolicy.Decision(best<current?ArcadePolicy.Action.LEFT:ArcadePolicy.Action.RIGHT,best<current?.25f:.75f,.72f,"Объезд по прогнозу движения препятствий",hold);
 }
 private ArcadePolicy.Decision fly(ArcadeObservation s,long now){
  if(!s.tracked)return waitFor("Измеряю скорость птенца");
  float lo=s.gapTop+s.player.height()*.62f,hi=s.gapBottom-s.player.height()*.62f;
  if(hi<=lo)return waitFor("Проход уже персонажа");
  float ceiling=s.top+s.player.height()*.6f,floor=s.bottom-s.player.height()*.6f;
  if(s.player.y()<=ceiling+.01f||now-lastFlap<130)return waitFor("Проверяю подъём");
  float age=Math.max(0,(now-s.timestampMs)/1000f)+.05f;
  float projectedY=s.player.y()+s.vy*age+.5f*gravity*age*age;
  float projectedV=s.vy+gravity*age;
  if(projectedY<ceiling+.008f&&projectedV<0)return waitFor("Сохраняю запас под потолком");
  float target=(lo+hi)*.5f;
  if(s.vy<-.12f&&projectedY<target+.035f)return waitFor("Подъём уже ведёт к проходу");
  float distance=Float.POSITIVE_INFINITY;
  for(ArcadeObservation.Box b:s.obstacles)if(b.right>s.player.left)distance=Math.min(distance,Math.max(0,b.left-s.player.right));
  float arrives=Float.isFinite(distance)?Math.max(0,distance/Math.max(.1f,scrollSpeed)-age):Float.POSITIVE_INFINITY;
  float horizon=clamp(Float.isFinite(arrives)?arrives+.22f:.8f,.45f,1.3f);
  // Waiting for another observation is itself an action with latency. The
  // planner must not postpone until the next sample can no longer avert impact.
  float nextDecision=.10f;
  float nextY=projectedY+projectedV*nextDecision+.5f*gravity*nextDecision*nextDecision;
  if(projectedV>-.08f&&(nextY>floor-.025f||(arrives<.55f&&nextY>hi-.015f)))
   return new ArcadePolicy.Decision(ArcadePolicy.Action.FLAP,.62f,(s.top+s.bottom)*.5f,"Упреждаю нижнюю границу прохода");
  float wait=rollout(s,projectedY,projectedV,false,now,ceiling,floor,lo,hi,target,arrives,horizon);
  float flap=rollout(s,projectedY,projectedV,true,now,ceiling,floor,lo,hi,target,arrives,horizon);
  if(flap+.0004f<wait)return new ArcadePolicy.Decision(ArcadePolicy.Action.FLAP,.62f,(s.top+s.bottom)*.5f,"Прыжок по прогнозу высоты и прохода");
  return waitFor("Полёт по безопасной траектории");
 }
 private static final class Flight {
  final float y,v,cost;final int since;
  Flight(float y,float v,float cost,int since){this.y=y;this.v=v;this.cost=cost;this.since=since;}
 }
 private float rollout(ArcadeObservation s,float startY,float startV,boolean flap,long now,float ceiling,float floor,float lo,float hi,float target,float arrival,float horizon){
  float dt=.065f;int minimumFlapSteps=(int)Math.ceil((.16f+Math.max(0,now-s.timestampMs)/1000f)/dt);List<Flight> beam=new ArrayList<>();
  beam.add(new Flight(startY,flap?flapVelocity:startV,flap?.001f:0,flap?0:(int)Math.min(50,(now-lastFlap)/65)));
  int steps=(int)Math.ceil(horizon/dt);
  for(int step=0;step<steps;step++){
   List<Flight> next=new ArrayList<>();float time=(step+1)*dt;
   for(Flight f:beam)for(int action=0;action<2;action++){
    if(action==1&&(step<2||f.since<minimumFlapSteps))continue;
    float v=action==1?flapVelocity:f.v;
    float y=f.y+v*dt+.5f*gravity*dt*dt;v+=gravity*dt;
    float cost=f.cost+(y-target)*(y-target)*.12f+(action==1?.001f:0);
    if(y<ceiling)cost+=100+(ceiling-y)*1000;
    if(y>floor)cost+=100+(y-floor)*1000;
    if(time>=Math.max(0,arrival-.1f)&&time<=arrival+.32f){
     if(y<lo)cost+=20+(lo-y)*400;
     if(y>hi)cost+=20+(y-hi)*400;
    }
    next.add(new Flight(y,v,cost,action==1?0:f.since+1));
   }
   next.sort(Comparator.comparingDouble(f->f.cost));
   // Keep several distinct velocities/heights, not many copies of one trajectory.
   beam=new ArrayList<>();for(Flight candidate:next){boolean duplicate=false;for(Flight kept:beam)if(Math.abs(candidate.y-kept.y)<.008f&&Math.abs(candidate.v-kept.v)<.08f){duplicate=true;break;}if(!duplicate)beam.add(candidate);if(beam.size()>=10)break;}
  }
  return beam.get(0).cost;
 }
 private static ArcadePolicy.Decision waitFor(String reason){return new ArcadePolicy.Decision(ArcadePolicy.Action.WAIT,0,0,reason);}
 private static int lane(float x,ArcadeObservation s){return Math.max(0,Math.min(2,(int)((x-s.left)/((s.right-s.left)/3))));}
 private static float center(int lane,ArcadeObservation s){return s.left+(lane+.5f)*(s.right-s.left)/3;}
 private static float clamp(float x,float lo,float hi){return Math.max(lo,Math.min(hi,x));}
 private static float blend(float a,float b,float alpha){return a+(b-a)*alpha;}
}
