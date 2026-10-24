package org.localpilot.android;

public class GateTest {
 private static int checks;

 public static void main(String[] args){
  transientFocus();
  completedGestureFreshness();
  initialBindingAndFreshness();
  incomingTargetAndPrecedence();
  issuerAndSession();
  consumptionContract();
  diagnosticsDoNotChangeAuthority();
  delayedObservationDoesNotRenewActions();
  System.out.println("PASS "+checks+" gate checks");
 }

 private static void transientFocus(){
  ActionGate g=armed();WindowFocus focus=new WindowFocus();ActionGate.Ticket old=g.offer(1000);
  check(g.consume(old,1000,"game",3,1080,2400));
  check(focus.observe(g.epoch(),"TARGET_WINDOW_UNAVAILABLE",1100)==WindowFocus.State.WAITING);g.pause();
  check(g.armed()&&g.paused());check(!g.consume(g.offer(1101),1101,"game",3,1080,2400));
  ActionGate.Ticket duringPause=g.offer(1102);
  check(g.identityRejection("game",3,1080,2400)==null);
  check(focus.observe(g.epoch(),null,1699)==WindowFocus.State.ACTIVE);g.resume();
  check("FOCUS_CHANGED".equals(g.rejection(old,1699,"game",3,1080,2400)));
  check("FOCUS_CHANGED".equals(g.completionRejection(old,1699,"game",3,1080,2400)));
  check("FOCUS_CHANGED".equals(g.rejection(duringPause,1699,"game",3,1080,2400)));
  check(g.consume(g.offer(1699),1699,"game",3,1080,2400));
  for(String reason:new String[]{"TARGET_APP_CHANGED","TARGET_WINDOW_UNAVAILABLE","WINDOW_STATE_UNAVAILABLE"}){
   focus.reset();check(focus.observe(g.epoch(),reason,2000)==WindowFocus.State.WAITING);
   check(focus.observe(g.epoch(),reason,2599)==WindowFocus.State.WAITING);
   check(focus.observe(g.epoch(),null,2600)==WindowFocus.State.STOP);
  }
  for(String reason:new String[]{"DEVICE_LOCKED","TARGET_WINDOW_CHANGED","TARGET_BOUNDS_CHANGED","TICKET_EXPIRED"}){
   focus.reset();check(focus.observe(g.epoch(),reason,3000)==WindowFocus.State.STOP);
  }
  focus.reset();focus.observe(g.epoch(),"TARGET_WINDOW_UNAVAILABLE",4000);
  check(focus.observe(g.epoch(),null,3999)==WindowFocus.State.STOP);
  g.pause();ActionGate.Ticket paused=g.offer(4001);g.stop();g.resume();check(!g.armed()&&!g.valid(paused,4002,"game",3,1080,2400));
  check(focus.observe(g.epoch(),null,5000)==WindowFocus.State.ACTIVE);
 }

 private static void completedGestureFreshness(){
  for(long delay:new long[]{50,700,701,2000,10000}){
   ActionGate g=armed();ActionGate.Ticket t=g.offer(1000);
   check(g.consume(t,1000,"game",3,1080,2400));
   check(g.completionRejection(t,1000+delay,"game",3,1080,2400)==null);
   check("TARGET_APP_CHANGED".equals(g.completionRejection(t,1000+delay,"other",3,1080,2400)));
   check("TARGET_WINDOW_CHANGED".equals(g.completionRejection(t,1000+delay,"game",4,1080,2400)));
   check("TARGET_BOUNDS_CHANGED".equals(g.completionRejection(t,1000+delay,"game",3,1000,2400)));
   check("CLOCK_REVERSED".equals(g.completionRejection(t,999,"game",3,1080,2400)));
   g.stop();check("NOT_BOUND".equals(g.completionRejection(t,1000+delay,"game",3,1080,2400)));
  }
  ActionGate g=armed();ActionGate.Ticket t=g.offer(1000);
  check("TICKET_NOT_CONSUMED".equals(g.completionRejection(t,3000,"game",3,1080,2400)));
  check(!g.consume(t,3000,"game",3,1080,2400));
  check("NO_TICKET".equals(g.completionRejection(null,3000,"game",3,1080,2400)));
  ActionGate other=armed();check("FOREIGN_TICKET".equals(other.completionRejection(t,3000,"game",3,1080,2400)));
  g.arm("game",3,1080,2400);check("SESSION_CHANGED".equals(g.completionRejection(t,3000,"game",3,1080,2400)));
 }

 private static ActionGate armed(){
  ActionGate gate=new ActionGate();
  gate.arm("game",3,1080,2400);
  return gate;
 }

 private static void initialBindingAndFreshness(){
  ActionGate gate=new ActionGate();
  check(!gate.armed());
  check(gate.offer(100)==null);
  rejected(gate,null,100,"game",3,1080,2400,"NOT_BOUND");
  gate.arm("game",3,1080,2400);
  rejected(gate,null,100,"game",3,1080,2400,"NO_TICKET");
  ActionGate.Ticket ticket=gate.offer(100);
  accepted(gate,ticket,100,"game",3,1080,2400);
  accepted(gate,ticket,799,"game",3,1080,2400);
  accepted(gate,ticket,800,"game",3,1080,2400);
  rejected(gate,ticket,801,"game",3,1080,2400,"TICKET_EXPIRED");
  rejected(gate,ticket,99,"game",3,1080,2400,"CLOCK_REVERSED");
  check(ActionGate.TTL_MS==700);
 }

 private static void incomingTargetAndPrecedence(){
  ActionGate gate=armed();
  ActionGate.Ticket ticket=gate.offer(100);
  rejected(gate,ticket,101,"other",3,1080,2400,"TARGET_APP_CHANGED");
  rejected(gate,ticket,101,null,3,1080,2400,"TARGET_APP_CHANGED");
  rejected(gate,ticket,101,"game",4,1080,2400,"TARGET_WINDOW_CHANGED");
  rejected(gate,ticket,101,"game",3,2400,1080,"TARGET_BOUNDS_CHANGED");
  rejected(gate,ticket,101,"game",3,1080,2399,"TARGET_BOUNDS_CHANGED");
  rejected(gate,ticket,99,"other",4,1,1,"TARGET_APP_CHANGED");
  rejected(gate,ticket,801,"game",4,1,1,"TARGET_WINDOW_CHANGED");
  rejected(gate,ticket,99,"game",3,1,1,"TARGET_BOUNDS_CHANGED");
  rejected(gate,null,99,null,-1,0,0,"NO_TICKET");
  accepted(gate,ticket,102,"game",3,1080,2400);
 }

 private static void issuerAndSession(){
  ActionGate gate=armed(),foreign=armed();
  ActionGate.Ticket ticket=gate.offer(100),other=foreign.offer(100);
  rejected(gate,other,101,"game",3,1080,2400,"FOREIGN_TICKET");
  rejected(gate,other,99,"other",4,1,1,"FOREIGN_TICKET");
  gate.stop();
  check(!gate.armed());
  check(gate.offer(101)==null);
  rejected(gate,ticket,101,"game",3,1080,2400,"NOT_BOUND");
  rejected(gate,other,99,null,-1,0,0,"NOT_BOUND");
  gate.arm("game",3,1080,2400);
  rejected(gate,ticket,101,"game",3,1080,2400,"SESSION_CHANGED");
  rejected(gate,ticket,99,"other",4,1,1,"SESSION_CHANGED");
  ActionGate.Ticket fresh=gate.offer(102);
  accepted(gate,fresh,102,"game",3,1080,2400);
  gate.arm("new-game",4,720,1280);
  rejected(gate,fresh,103,"new-game",4,720,1280,"SESSION_CHANGED");
  accepted(gate,gate.offer(104),104,"new-game",4,720,1280);
 }

 private static void consumptionContract(){
  ActionGate gate=armed();
  ActionGate.Ticket ticket=gate.offer(100);
  check(!gate.consume(ticket,101,"other",3,1080,2400));
  check(gate.consume(ticket,102,"game",3,1080,2400));
  accepted(gate,ticket,103,"game",3,1080,2400);
  check(!gate.consume(ticket,104,"game",3,1080,2400));
  accepted(gate,ticket,800,"game",3,1080,2400);
  rejected(gate,ticket,801,"game",3,1080,2400,"TICKET_EXPIRED");
  ActionGate.Ticket another=gate.offer(900);
  check(gate.consume(another,900,"game",3,1080,2400));
  check(!gate.consume(another,900,"game",3,1080,2400));
  gate.stop();
  rejected(gate,another,901,"game",3,1080,2400,"NOT_BOUND");
 }

 private static void diagnosticsDoNotChangeAuthority(){
  ActionGate gate=armed();
  long epoch=gate.epoch();
  ActionGate.Ticket ticket=gate.offer(100);
  for(int i=0;i<3;i++){
   rejected(gate,ticket,101,"other",3,1080,2400,"TARGET_APP_CHANGED");
   check(gate.epoch()==epoch);
   check(gate.armed());
  }
  check(gate.consume(ticket,102,"game",3,1080,2400));
  check(gate.epoch()==epoch);
  check(gate.matches("game",3,1080,2400));
 }

 private static void delayedObservationDoesNotRenewActions(){
  ActionGate gate=armed();ActionGate.Ticket action=gate.offer(100);
  // Read-only window identity is observed after a slow framework query.
  ActionGate.Ticket observation=gate.offer(2000);
  accepted(gate,observation,2000,"game",3,1080,2400);
  rejected(gate,observation,2000,"other",3,1080,2400,"TARGET_APP_CHANGED");
  rejected(gate,observation,2000,"game",4,1080,2400,"TARGET_WINDOW_CHANGED");
  rejected(gate,observation,2000,"game",3,720,1280,"TARGET_BOUNDS_CHANGED");
  rejected(gate,action,2000,"game",3,1080,2400,"TICKET_EXPIRED");
  check(!gate.consume(action,2000,"game",3,1080,2400));
 }

 private static void accepted(ActionGate gate,ActionGate.Ticket ticket,long now,String app,int window,int width,int height){
  check(gate.rejection(ticket,now,app,window,width,height)==null);
  check(gate.valid(ticket,now,app,window,width,height));
 }

 private static void rejected(ActionGate gate,ActionGate.Ticket ticket,long now,String app,int window,int width,int height,String expected){
  String code=gate.rejection(ticket,now,app,window,width,height);
  check(expected.equals(code));
  check(code.matches("[A-Z][A-Z0-9_]{0,31}"));
  check(!gate.valid(ticket,now,app,window,width,height));
  check(!gate.consume(ticket,now,app,window,width,height));
 }

 private static void check(boolean condition){
  checks++;
  if(!condition)throw new AssertionError("check "+checks);
 }
}
