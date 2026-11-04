package org.localpilot.android;

import java.util.ArrayList;
import java.util.List;

/** The primary engine is exercised through real production controller classes. */
public final class WeightSpaceGameEngineTest {
 private static int checks;
 private static void ok(boolean condition,String reason){
  checks++;if(!condition)throw new AssertionError(reason);
 }
 private static ArcadeObservation paddle(long at,float conf){
  return new ArcadeObservation(ArcadeProfile.PADDLE,at,
   new ArcadeObservation.Box(.59f,.48f,.61f,.50f),
   new ArcadeObservation.Box(.48f,.89f,.64f,.93f),
   List.of(new ArcadeObservation.Box(.05f,.10f,.15f,.15f)),
   .04f,.04f,.96f,.96f,0,0,.10f,.60f,conf,true,false);
 }
 private static JumperState jumper(boolean hazard,float conf){
  var p=new JumperState.ObjectBox(JumperState.Kind.PLAYER,420,596,450,644,.97f);
  var platforms=new ArrayList<JumperState.ObjectBox>();
  for(int i=0;i<5;i++)platforms.add(new JumperState.ObjectBox(
   JumperState.Kind.PLATFORM,30,220+i*55,130,232+i*55,.95f));
  List<JumperState.ObjectBox> hazards=hazard
   ?List.of(new JumperState.ObjectBox(JumperState.Kind.HAZARD,40,205,110,435,.96f)):List.of();
  return new JumperState(854,1280,0,0,854,1280,1000,p,platforms,hazards,conf,0,0,0);
 }
 public static void main(String[] args){
  ArcadeObservation observed=paddle(1000,.98f);
  ArcadePolicy.Decision base=ArcadePolicy.next(observed);
  String learned="LP-PADDLE-WEIGHTS-v1,100";
  ArcadePolicy.Decision weight=WeightSpaceGameEngine.arcade(observed,base,learned);
  ArcadePolicy.Decision expected=PaddleTransfer.decide(observed,learned);
  ok(weight.action==expected.action&&Math.abs(weight.x-expected.x)<.00001f,
   "primary engine uses identical trained weights on actual visual state");
  ok(Math.abs(weight.x-base.x)>.001f,"trained parameters change real PADDLE target");
  ok(WeightSpaceGameEngine.arcade(observed,base,null)==base,
   "exact rollback is old physics, not another learned policy");
  ok(WeightSpaceGameEngine.arcade(observed,base,"corrupt").x==base.x,
   "schema-incompatible checkpoint does not steer");
  ok(WeightSpaceGameEngine.arcade(paddle(1100,.7f),base,learned).x==base.x,
   "low-confidence visual input cannot promote new action");
  ok(WeightSpaceGameEngine.arcade(null,base,learned).action==ArcadePolicy.Action.WAIT,
   "missing real frame fails closed");
  var controller=new ArcadeController();
  controller.installPaddleTransfer(learned);
  ArcadePolicy.Decision live=controller.decide(observed,1000);
  ok(live.action==weight.action&&Math.abs(live.x-weight.x)<.00001f,
   "production ArcadeController calls WeightSpace first");
  ok(controller.decide(observed,1100).action==ArcadePolicy.Action.WAIT,
   "replayed frame cannot authorize repeated movement");

  var t=new ArcadeObservation(ArcadeProfile.TRAFFIC,2000,
   new ArcadeObservation.Box(.45f,.77f,.55f,.86f),
   new ArcadeObservation.Box(.45f,.77f,.55f,.86f),
   List.of(new ArcadeObservation.Box(.43f,.55f,.57f,.65f)),
   .05f,.05f,.95f,.95f,0,0,0,.05f,.98f,true,false);
  var traffic=new ArcadeController().decide(t,2000);
  ok(traffic.action==WeightSpaceGameEngine.arcade(t,traffic,null).action,
   "traffic still uses validated temporal physics, no arbitrary index mapping");
  var f=new ArcadeObservation(ArcadeProfile.FLAPPY,3000,
   new ArcadeObservation.Box(.32f,.40f,.39f,.47f),
   new ArcadeObservation.Box(.32f,.40f,.39f,.47f),
   List.of(new ArcadeObservation.Box(.65f,.1f,.72f,.25f)),
   .02f,.03f,.98f,.97f,.25f,.72f,0,.10f,.98f,true,false);
  var flight=new ArcadeController().decide(f,3000);
  ok(flight.action==WeightSpaceGameEngine.arcade(f,flight,null).action,
   "flappy cooldown and safe flight model unchanged");

  var scene=jumper(false,.97f);
  ok(WeightSpaceJumperEngine.decide(scene,0,JumperPolicy.Action.WAIT)==JumperPolicy.Action.WAIT,
   "do not force recovery without verified plateau");
  ok(WeightSpaceJumperEngine.decide(scene,3,JumperPolicy.Action.WAIT)==JumperPolicy.Action.TAP_LEFT,
   "WeightSpace recovery is now in the real Ari decision path");
  ok(WeightSpaceJumperEngine.decide(jumper(true,.97f),3,JumperPolicy.Action.WAIT)
    ==JumperPolicy.Action.WAIT,"never force retreat into observed hazard");
  ok(WeightSpaceJumperEngine.decide(jumper(false,.75f),3,JumperPolicy.Action.WAIT)
    ==JumperPolicy.Action.WAIT,"low-confidence Ari state uses old policy");
  ok(WeightSpaceJumperEngine.decide(scene,4,JumperPolicy.Action.WAIT)
    ==JumperPolicy.Action.WAIT,"4-steer visual-progress STOP cannot be bypassed");
  ok(WeightSpaceJumperEngine.decide(null,3,JumperPolicy.Action.TAP_RIGHT)
    ==JumperPolicy.Action.TAP_RIGHT,"no unverified model override on missing scene");
  System.out.println("WEIGHTSPACE_PRIMARY_ENGINE_PASS checks="+checks);
 }
}
