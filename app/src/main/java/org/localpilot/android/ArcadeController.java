package org.localpilot.android;

import java.util.ArrayList;
import java.util.List;

/** Temporal model-predictive control in normalized game coordinates. No Android dependency. */
public final class ArcadeController {
 private ArcadeObservation previous,earlier;
 private float trafficSpeed=.55f, scrollSpeed=.28f, gravity=1.5f, flapVelocity=-.44f;
 private long lastFlap=-10000,lastLane=-10000,verifiedFlap=-10000;
 private int intendedLane=-1,lastHold=150;
 private float steeringSpeed=1.1f,laneStartX;
 private boolean continuousSteering;
 public void reset(){previous=earlier=null;trafficSpeed=.55f;scrollSpeed=.28f;gravity=1.5f;flapVelocity=-.44f;lastFlap=lastLane=verifiedFlap=-10000;intendedLane=-1;steeringSpeed=1.1f;lastHold=150;continuousSteering=false;}
 /** New episode in the same window: keep learned physics, discard old objects/actions. */
 public void nextRound(){previous=earlier=null;lastFlap=lastLane=verifiedFlap=-10000;intendedLane=-1;}
 /** A loss is an outcome, not a measurement of gravity or flap impulse. */
 public void penalize(){nextRound();}
 public ArcadePolicy.Decision decide(ArcadeObservation s,long now){
  if(s==null||!s.usable()||now<s.timestampMs||now-s.timestampMs>450)return waitFor("Ожидаю свежую игровую сцену");
  if(!Float.isFinite(s.vx)||!Float.isFinite(s.vy))return waitFor("Скорость не подтверждена");
  if(previous!=null&&previous.profile!=s.profile)reset();
  // Replayed/out-of-order pixels cannot erase the saved physics or renew input.
  if(previous!=null&&s.timestampMs<=previous.timestampMs)return waitFor("Ожидаю следующий кадр");
  learn(s,now);earlier=previous;previous=s;
  return switch(s.profile){case TRAFFIC->drive(s,now);case FLAPPY->fly(s,now);default->ArcadePolicy.next(s);};
 }
 /** Call only after dispatch was accepted, not merely when a recommendation was produced. */
 public void actionDispatched(ArcadePolicy.Decision d,long now){
  if(d==null)return;
  if(d.action==ArcadePolicy.Action.FLAP){lastFlap=now;verifiedFlap=-10000;}
  if(d.action==ArcadePolicy.Action.LEFT||d.action==ArcadePolicy.Action.RIGHT){lastLane=now;lastHold=d.durationMs;laneStartX=previous==null?0:previous.player.x();
   if(previous!=null){int current=lane(previous.player.x(),previous),direction=d.action==ArcadePolicy.Action.LEFT?-1:1;
    boolean finishing=intendedLane==current&&Math.abs(previous.player.x()-center(current,previous))>.025f
      &&(center(current,previous)-previous.player.x())*direction>0;
    if(!finishing)intendedLane=Math.max(0,Math.min(2,current+direction));
   }else intendedLane=-1;}
 }
 /** Measured response improves the motion model; an Android ACK alone is not evidence. */
 public void observeGesture(ArcadePolicy.Decision action,ArcadeObservation before,ArcadeObservation after){
  if(action==null||before==null||after==null||before.profile!=after.profile||!before.usable()||!after.usable())return;
  // A dispatched tap may have been ignored while an earlier jump still rises.
  // Only an extra visual impulse from this dispatch enables flap calibration.
  if(action.action==ArcadePolicy.Action.FLAP&&before.profile==ArcadeProfile.FLAPPY
    &&lastFlap>=0&&before.timestampMs<=lastFlap&&after.timestampMs>lastFlap
    &&after.timestampMs-lastFlap<=700&&sameFlightGeometry(before,after)
    &&ArcadePolicy.response(action,before,after))verifiedFlap=lastFlap;
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
  if(s.profile==ArcadeProfile.FLAPPY&&s.tracked&&lastFlap>=0&&verifiedFlap==lastFlap){
   float since=(s.timestampMs-lastFlap)/1000f;
   // First finite-difference response can mix ascent with pre-command descent.
   // Wait until the entire velocity sample is beyond normal input latency.
   if(since>=.20f&&since<=.48f&&previous.timestampMs>=lastFlap+120&&s.vy<-.04f){
    float measured=s.vy-gravity*Math.max(0,since-.06f-dt*.5f);
    flapVelocity=blend(flapVelocity,clamp(measured,-1.2f,-.15f),.18f);
   }
  }
  if(s.profile==ArcadeProfile.FLAPPY&&earlier!=null&&earlier.tracked&&previous.tracked&&s.tracked){
   // Vision velocities are interval averages, not instantaneous velocities.
   // Three positions correctly handle unequal capture intervals; do not fit
   // a parabola through an input impulse, a long dropout or a changed viewport.
   float priorDt=(previous.timestampMs-earlier.timestampMs)/1000f;
   if(priorDt>=.03f&&priorDt<=.30f&&dt<=.30f&&earlier.timestampMs>=lastFlap+160
     &&sameFlightGeometry(earlier,previous)&&sameFlightGeometry(previous,s)){
    float oldV=(previous.player.y()-earlier.player.y())/priorDt;
    float newV=(s.player.y()-previous.player.y())/dt;
    float measured=2*(newV-oldV)/(priorDt+dt);
    if(measured>.25f&&measured<5f)gravity=blend(gravity,measured,.15f);
   }
  }
 }
 private static boolean sameFlightGeometry(ArcadeObservation a,ArcadeObservation b){
  return Math.abs(a.left-b.left)<.001f&&Math.abs(a.right-b.right)<.001f
   &&Math.abs(a.top-b.top)<.001f&&Math.abs(a.bottom-b.bottom)<.001f
   &&Math.abs(a.player.width()-b.player.width())<.008f
   &&Math.abs(a.player.height()-b.player.height())<.008f;
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
  // A red fuel can is a pickup, never a traffic hazard. Seek one only if its
  // lane is one change away and the obstacle rollout already marked that path safe.
  for(ArcadeObservation.Box fuel:s.collectibles){
   float arrival=(s.player.top-fuel.bottom)/Math.max(.1f,trafficSpeed)-age;
   int target=lane(fuel.x(),s);
   if(arrival<-.15f||arrival>1.6f||Math.abs(target-current)>1
     ||!Float.isFinite(costs[target])||costs[target]>.45f)continue;
   costs[target]-=.34f/(.65f+Math.max(0,arrival));
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
  float nextDecision=.18f;
  float nextY=projectedY+projectedV*nextDecision+.5f*gravity*nextDecision*nextDecision;
  if(projectedV>-.08f&&(nextY>floor-.025f||(arrives<.55f&&nextY>hi-.015f)))
   return new ArcadePolicy.Decision(ArcadePolicy.Action.FLAP,.62f,(s.top+s.bottom)*.5f,"Упреждаю нижнюю границу прохода");
  float wait=rollout(s,projectedY,projectedV,false,now,ceiling,floor,lo,hi,target,arrives,horizon);
  float flap=rollout(s,projectedY,projectedV,true,now,ceiling,floor,lo,hi,target,arrives,horizon);
  if(flap+.0004f<wait)return new ArcadePolicy.Decision(ArcadePolicy.Action.FLAP,.62f,(s.top+s.bottom)*.5f,"Прыжок по прогнозу высоты и прохода");
  return waitFor("Полёт по безопасной траектории");
 }
 // Controller instances belong to one session/worker; scratch must not be shared across threads.
 private static final class Flight {
  float y,v,cost;int since;
  void set(float y,float v,float cost,int since){this.y=y;this.v=v;this.cost=cost;this.since=since;}
 }
 private static Flight[] flights(int size){Flight[] result=new Flight[size];for(int i=0;i<size;i++)result[i]=new Flight();return result;}
 private final Flight[] flightBeam=flights(10),flightCandidates=flights(20);
 private float rollout(ArcadeObservation s,float startY,float startV,boolean flap,long now,float ceiling,float floor,float lo,float hi,float target,float arrival,float horizon){
  float dt=.065f;int minimumFlapSteps=(int)Math.ceil((.16f+Math.max(0,now-s.timestampMs)/1000f)/dt);
  int beamSize=1;flightBeam[0].set(startY,flap?flapVelocity:startV,flap?.001f:0,flap?0:(int)Math.min(50,(now-lastFlap)/65));
  int steps=(int)Math.ceil(horizon/dt);
  for(int step=0;step<steps;step++){
   int count=0;float time=(step+1)*dt;
   for(int i=0;i<beamSize;i++){Flight f=flightBeam[i];for(int action=0;action<2;action++){
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
    flightCandidates[count++].set(y,v,cost,action==1?0:f.since+1);
   }}
   // Stable insertion sort on at most 20 references preserves old tie ordering.
   for(int i=1;i<count;i++){Flight candidate=flightCandidates[i];int j=i;
    while(j>0&&Float.compare(flightCandidates[j-1].cost,candidate.cost)>0){flightCandidates[j]=flightCandidates[j-1];j--;}
    flightCandidates[j]=candidate;
   }
   beamSize=0;
   for(int i=0;i<count;i++){Flight candidate=flightCandidates[i];boolean duplicate=false;
    for(int j=0;j<beamSize;j++){Flight kept=flightBeam[j];if(Math.abs(candidate.y-kept.y)<.008f&&Math.abs(candidate.v-kept.v)<.08f){duplicate=true;break;}}
    if(!duplicate){flightBeam[beamSize++].set(candidate.y,candidate.v,candidate.cost,candidate.since);if(beamSize==10)break;}
   }
  }
  return flightBeam[0].cost;
 }
 private static ArcadePolicy.Decision waitFor(String reason){return new ArcadePolicy.Decision(ArcadePolicy.Action.WAIT,0,0,reason);}
 private static int lane(float x,ArcadeObservation s){return Math.max(0,Math.min(2,(int)((x-s.left)/((s.right-s.left)/3))));}
 private static float center(int lane,ArcadeObservation s){return s.left+(lane+.5f)*(s.right-s.left)/3;}
 private static float clamp(float x,float lo,float hi){return Math.max(lo,Math.min(hi,x));}
 private static float blend(float a,float b,float alpha){return a+(b-a)*alpha;}
}
