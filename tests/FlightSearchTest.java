package org.localpilot.android;
import java.util.*;
import java.lang.reflect.Method;
/** Fixed 0.7.24 search oracle: protects trajectory semantics while changing memory layout. */
public final class FlightSearchTest {
 private static final class Oracle {
  float gravity=1.5f,flapVelocity=-.44f;long lastFlap=-10000;
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

 }
 public static void main(String[] args)throws Exception {
  Method search=Arrays.stream(ArcadeController.class.getDeclaredMethods()).filter(m->m.getName().equals("rollout")).findFirst().orElseThrow();search.setAccessible(true);
  var random=new Random(724025);var controller=new ArcadeController();var oracle=new Oracle();
  var player=new ArcadeObservation.Box(.1f,.35f,.15f,.4f);
  var sample=new ArcadeObservation(ArcadeProfile.FLAPPY,1000,player,player,List.of(),0,0,1,1,.2f,.7f,0,.1f,.9f,true,false);
  for(int i=0;i<2400;i++){
   oracle.gravity=.26f+random.nextFloat()*4.7f;oracle.flapVelocity=-.16f-random.nextFloat()*1.02f;
   controller.restore("gravity="+oracle.gravity+" flap="+oracle.flapVelocity);
   if(i%7==0){controller.nextRound();oracle.lastFlap=-10000;}
   if(i%5==0){oracle.lastFlap=950;controller.actionDispatched(new ArcadePolicy.Decision(ArcadePolicy.Action.FLAP,.5f,.5f,"test"),950);}
   float y=.05f+random.nextFloat()*.9f,v=-1+random.nextFloat()*2,lo=.08f+random.nextFloat()*.3f,hi=.6f+random.nextFloat()*.3f;
   float arrival=i%9==0?Float.POSITIVE_INFINITY:random.nextFloat()*1.8f,horizon=.45f+random.nextFloat()*.85f;
   boolean flap=i%2==0;long now=1000+random.nextInt(451);
   float expected=oracle.rollout(sample,y,v,flap,now,.03f,.97f,lo,hi,(lo+hi)*.5f,arrival,horizon);
   float actual=(Float)search.invoke(controller,sample,y,v,flap,now,.03f,.97f,lo,hi,(lo+hi)*.5f,arrival,horizon);
   if(Float.floatToIntBits(expected)!=Float.floatToIntBits(actual))throw new AssertionError("Search changed at case "+i+": "+expected+" != "+actual);
  }
  System.out.println("Flight search: 2400 bit-exact costs PASS vs frozen 0.7.24 oracle");
  var bean=java.lang.management.ManagementFactory.getThreadMXBean();
  if(!(bean instanceof com.sun.management.ThreadMXBean memory)||!memory.isThreadAllocatedMemorySupported()){
   System.out.println("Flight allocation: SKIP (JVM allocation counter unavailable)");return;
  }
  memory.setThreadAllocatedMemoryEnabled(true);controller.reset();oracle=new Oracle();
  Object[] parameters={sample,.4f,.1f,false,1080L,.03f,.97f,.2f,.7f,.45f,.5f,1.3f};
  for(int i=0;i<100;i++){search.invoke(controller,parameters);oracle.rollout(sample,.4f,.1f,false,1080,.03f,.97f,.2f,.7f,.45f,.5f,1.3f);}
  long thread=Thread.currentThread().getId(),start=memory.getThreadAllocatedBytes(thread);
  for(int i=0;i<500;i++)search.invoke(controller,parameters);
  long current=(memory.getThreadAllocatedBytes(thread)-start)/500;start=memory.getThreadAllocatedBytes(thread);
  float sink=0;for(int i=0;i<500;i++)sink+=oracle.rollout(sample,.4f,.1f,false,1080,.03f,.97f,.2f,.7f,.45f,.5f,1.3f);
  long legacy=(memory.getThreadAllocatedBytes(thread)-start)/500;
  if(!Float.isFinite(sink)||current>2048||current*10>=legacy)throw new AssertionError("Search allocations: "+current+" vs "+legacy);
  System.out.println("Flight search allocation: "+current+" vs old "+legacy+" bytes/search PASS (JVM, includes reflective return boxing)");
 }
}
