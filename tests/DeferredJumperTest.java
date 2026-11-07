package org.localpilot.android;

import java.util.List;

/** Synthetic head-contact/bounce and edge-crossing evidence; no Android ACK is treated as a reward. */
public final class DeferredJumperTest {
 private static int assertions;
 private static void check(boolean ok,String why){assertions++;if(!ok)throw new AssertionError(why);}
 private static JumperState.ObjectBox box(JumperState.Kind kind,float l,float t,float r,float b){return new JumperState.ObjectBox(kind,l,t,r,b,.95f);}
 private static List<JumperState.ObjectBox> platforms(){return List.of(
  box(JumperState.Kind.PLATFORM,210,750,290,770),box(JumperState.Kind.PLATFORM,10,100,100,120),
  box(JumperState.Kind.PLATFORM,700,200,800,220),box(JumperState.Kind.PLATFORM,650,950,750,970),
  box(JumperState.Kind.PLATFORM,40,1100,150,1120));}
 private static JumperState state(long at,float x,float bottom,float vy,List<JumperState.ObjectBox> hazards){
  return new JumperState(854,1280,0,0,854,1280,at,box(JumperState.Kind.PLAYER,x-20,bottom-60,x+20,bottom),platforms(),hazards,.95f,0,vy,0);
 }
 private static JumperState withScroll(JumperState s,float scroll){return new JumperState(s.frameWidth,s.frameHeight,s.roiLeft,s.roiTop,s.roiRight,s.roiBottom,s.timestampMs,s.player,s.platforms,s.hazards,s.confidence,s.playerVx,s.playerVy,scroll);}
 private static List<JumperState.ObjectBox> enemy(){return List.of(box(JumperState.Kind.HAZARD,470,600,550,640));}
 private static void policy(){
  JumperState falling=state(1000,400,560,150,enemy());
  check(JumperPolicy.nextAction(falling)==JumperPolicy.Action.TAP_LEFT,"Legacy default still treats enemies as hazards");
  check(JumperPolicy.nextAction(falling,false,true,false)==JumperPolicy.Action.TAP_RIGHT,"Explicit falling-head approach receives stomp landing priority");
  check(JumperPolicy.selectedStompTarget(falling,false,false)==falling.hazards.get(0),"Selected target refers to the observed enemy");
  check(JumperPolicy.canStomp(falling,falling.hazards.get(0)),"A confident falling player above the head is eligible");
  check(!JumperPolicy.canStomp(state(1000,400,560,-150,enemy()),falling.hazards.get(0)),"Ascending player cannot stomp");
  check(!JumperPolicy.canStomp(state(1000,400,560,0,enemy()),falling.hazards.get(0)),"Stationary player cannot stomp");
  check(!JumperPolicy.canStomp(state(1000,400,630,150,enemy()),falling.hazards.get(0)),"Side contact cannot stomp");
  check(!JumperPolicy.canStomp(withScroll(falling,200),falling.hazards.get(0)),"Camera-only downward motion cannot stomp");
  JumperState enemyOnly=new JumperState(854,1280,0,0,854,1280,1000,falling.player,List.of(),enemy(),.9f,0,150,0);
  check(!enemyOnly.usable()&&enemyOnly.usableForMechanics(true),"Enemy-only scene needs explicit calibrated mechanic");
  check(JumperPolicy.nextAction(enemyOnly)==JumperPolicy.Action.WAIT,"Enemy-only scene cannot enter legacy Ari input");
  check(JumperPolicy.nextAction(enemyOnly,false,true,false)==JumperPolicy.Action.TAP_RIGHT,"Explicit enemy-only scene can aim at its head");
  var across=List.of(box(JumperState.Kind.PLATFORM,760,440,840,460),platforms().get(1),platforms().get(2),platforms().get(3),platforms().get(4));
  JumperState edge=new JumperState(854,1280,0,0,854,1280,1000,box(JumperState.Kind.PLAYER,0,500,40,560),across,List.of(),.95f,0,0,0);
  check(JumperPolicy.nextAction(edge,true,false,true)==JumperPolicy.Action.TAP_LEFT,"Reachable edge route can be preferred for wrap quest");
  check(JumperPolicy.nextAction(edge,false,false,true)!=JumperPolicy.Action.TAP_LEFT,"Disabling wrap never invents an across-edge path");
 }
 private static JumperFeedback prepared(boolean ack){
  JumperFeedback f=new JumperFeedback();f.configureMechanics(false,true);
  long id=f.dispatched(JumperPolicy.Action.TAP_RIGHT,state(1000,400,560,150,enemy()),1000);
  check(!f.acknowledge(id+1,1080),"Foreign ACK cannot confirm a stomp plan");
  if(ack)check(f.acknowledge(id,1080),"Own timestamped ACK accepted");
  check(f.verifiedStomps()==0,"ACK alone is not a stomp");return f;
 }
 private static void feedBounce(JumperFeedback f){
  f.observe(state(1100,510,600,150,enemy()),1100);
  check(f.verifiedStomps()==0,"Top contact alone is not a verified stomp");
  f.observe(state(1200,510,580,-150,enemy()),1200);
  check(f.verifiedStomps()==0,"One rising frame is insufficient");
  f.observe(state(1240,510,570,-150,enemy()),1240);
 }
 private static void feedback(){
  JumperFeedback f=prepared(true);feedBounce(f);
  check(f.verifiedStomps()==1,"Own ACK, top contact and two fresh upward frames verify one stomp");
  f.observe(state(1280,510,560,-150,enemy()),1280);check(f.verifiedStomps()==1,"One physical bounce cannot count twice");
  f.reset();check(f.verifiedStomps()==0&&!f.awaitingStompProof(),"Session reset revokes stomp evidence and counters");
  f=prepared(false);feedBounce(f);check(f.verifiedStomps()==0,"No Android ACK means no stomp verification");
  f=prepared(true);f.observe(state(1100,510,600,150,enemy()),1100);
  JumperState changed=state(1200,510,580,-150,enemy());
  changed=new JumperState(854,1280,0,0,853,1280,1200,changed.player,changed.platforms,changed.hazards,.95f,0,-150,0);
  f.observe(changed,1200);f.observe(state(1240,510,570,-150,enemy()),1240);
  check(f.verifiedStomps()==0,"Changed physical ROI revokes the contact proof");
  f=prepared(true);var duplicateEnemies=List.of(enemy().get(0),box(JumperState.Kind.HAZARD,472,600,552,640));
  f.observe(state(1100,510,600,150,duplicateEnemies),1100);f.observe(state(1200,510,580,-150,enemy()),1200);f.observe(state(1240,510,570,-150,enemy()),1240);
  check(f.verifiedStomps()==0,"Ambiguous enemy identity cannot establish contact");

  f=prepared(true);f.observe(state(1100,560,620,150,enemy()),1100);f.observe(state(1200,510,580,-150,enemy()),1200);f.observe(state(1240,510,570,-150,enemy()),1240);
  check(f.verifiedStomps()==0,"A side collision cannot be retroactively promoted by an upward frame");
  f=prepared(true);f.observe(state(1100,510,600,150,enemy()),1100);f.observe(state(1200,510,580,-150,enemy()),1200);f.observe(state(1240,510,570,-150,enemy()),1600);
  check(f.verifiedStomps()==0,"Stale bounce frame cannot finish a proof");
  f=prepared(true);f.observe(state(1100,510,600,150,enemy()),1100);f.observe(state(1200,510,580,-150,enemy()),1200);f.observe(state(1200,510,570,-150,enemy()),1200);
  check(f.verifiedStomps()==0,"Duplicate timestamp is not a second bounce observation");
  f=prepared(true);f.observe(state(1100,510,600,150,enemy()),1100);f.observe(null,1150);f.observe(state(1200,510,580,-150,enemy()),1200);f.observe(state(1240,510,570,-150,enemy()),1240);
  check(f.verifiedStomps()==0,"Lost identity revokes the physical-contact proof");
  f=prepared(true);f.observe(state(1100,510,600,150,enemy()),1100);f.observe(state(1800,510,580,-150,enemy()),1800);f.observe(state(1840,510,570,-150,enemy()),1840);
  check(f.verifiedStomps()==0,"Late bounce cannot extend the original bounded window");
  f=new JumperFeedback();f.configureMechanics(false,true);long id=f.dispatched(JumperPolicy.Action.TAP_RIGHT,state(1000,400,560,150,enemy()),1000);
  f.observe(state(1100,510,600,150,enemy()),1100);f.acknowledge(id,1150);f.observe(state(1100,510,600,150,enemy()),1150);f.observe(state(1200,510,580,-150,enemy()),1200);f.observe(state(1240,510,570,-150,enemy()),1240);
  check(f.verifiedStomps()==0,"Pre-ACK contact frame cannot be replayed after completion");
  f=new JumperFeedback();f.configureMechanics(false,true);id=f.dispatched(JumperPolicy.Action.TAP_RIGHT,state(1000,400,560,150,enemy()),1000);f.acknowledge(id);feedBounce(f);
  check(f.verifiedStomps()==0,"Legacy untimed ACK verifies steering only, not stomp evidence");
 }
 private static void tracking(){
  JumperTracking t=new JumperTracking();t.observe(state(1000,820,560,0,List.of()),5,1);t.observe(state(1100,30,560,0,List.of()),5,1);
  check(t.verifiedWraps()==0,"Opposite-edge observation requires a confirming identity frame");
  t.observe(state(1150,40,560,0,List.of()),5,1);check(t.verifiedWraps()==1,"Fresh continued motion on new edge confirms one wrap");
  t.observe(state(1150,40,560,0,List.of()),5,1);check(t.verifiedWraps()==1,"Duplicate frame cannot count twice");
  t.observe(state(1200,50,560,0,List.of()),4,99);check(t.verifiedWraps()==1,"Retired session callback cannot reset current progress");
  t.observe(state(1200,50,560,0,List.of()),5,0);check(t.verifiedWraps()==1,"Retired generation callback cannot reset current progress");
  t.observe(null,5,1);check(t.previous(1300)!=null&&t.missingFrames()==1,"Short object loss retains matching history only");
  check(t.previous(1900)==null,"Identity history expires instead of authorizing blind control");
  t.observe(state(1400,50,560,0,List.of()),6,1);check(t.verifiedWraps()==0,"Session change clears old quest proof");
  t.observe(state(1500,820,560,0,List.of()),6,1);t.observe(state(1550,30,560,0,List.of()),6,2);t.observe(state(1600,40,560,0,List.of()),6,2);
  check(t.verifiedWraps()==0,"Generation change prevents cross-session edge matching");
  t.reset();t.observe(state(1000,400,560,0,List.of()));t.observe(state(1100,30,560,0,List.of()));t.observe(state(1150,40,560,0,List.of()));
  check(t.verifiedWraps()==0,"Interior teleport is not an edge crossing");
  t.reset();t.configureMechanics(false,false);t.observe(state(1000,820,560,0,List.of()));t.observe(state(1100,30,560,0,List.of()));t.observe(state(1150,40,560,0,List.of()));
  check(t.verifiedWraps()==0,"Non-wrapping games cannot accumulate wrap quest progress");
 }
 public static void main(String[] args){policy();feedback();tracking();System.out.println("Deferred Jumper mechanics: "+assertions+" assertions PASS; synthetic top-contact and edge evidence, not physical-game acceptance");}
}
