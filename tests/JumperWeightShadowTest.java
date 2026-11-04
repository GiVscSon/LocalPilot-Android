package org.localpilot.android;

import java.util.List;
import org.localpilot.android.rl.WeightSpaceSkills;

/** Only shadow diagnostics from actual vision observations: zero gesture authority. */
public final class JumperWeightShadowTest {
 private static int checks;
 private static void ok(boolean x,String m){checks++;if(!x)throw new AssertionError(m);}
 private static JumperState sample(boolean dangerous,float confidence){
  var player=new JumperState.ObjectBox(JumperState.Kind.PLAYER,420,596,450,644,.95f);
  var platforms=new java.util.ArrayList<JumperState.ObjectBox>();
  for(int i=0;i<5;i++)platforms.add(new JumperState.ObjectBox(
    JumperState.Kind.PLATFORM,20,220+i*44,120,232+i*44,.95f));
  List<JumperState.ObjectBox> hazards=dangerous?
    List.of(new JumperState.ObjectBox(JumperState.Kind.HAZARD,40,205,110,435,.96f)):List.of();
  return new JumperState(854,1280,0,0,854,1280,1000,player,platforms,hazards,
    confidence,0,0,0);
 }
 public static void main(String[] args){
  var frame=sample(false,.97f);
  ok(frame.usable(),"recognized real-vision frame");
  var initial=JumperWeightShadow.review(frame,0);
  var plateau=JumperWeightShadow.review(frame,3);
  ok(initial.usable()&&plateau.usable(),"shadow understands actual vision geometry");
  ok(!initial.intervention(),"ordinary game state unchanged");
  ok(plateau.original()==WeightSpaceSkills.Action.STAY,"unpatched agent stuck");
  ok(plateau.patched()==WeightSpaceSkills.Action.LEFT,"single-weight shadow recommends retreat");
  ok(plateau.intervention(),"plateau hypothesis recorded");
  ok(!initial.inputAuthorized()&&!plateau.inputAuthorized(),"no input authority");
  var hazardous=JumperWeightShadow.review(sample(true,.97f),3);
  ok(!hazardous.intervention()&&hazardous.patched()==WeightSpaceSkills.Action.STAY,
    "do not suggest retreat onto observed hazard");
  ok(!JumperWeightShadow.review(sample(false,.75f),3).usable(),
    "unknown or low-confidence vision rejected");
  ok(!JumperWeightShadow.review(frame,22).usable(),
    "corrupt repeated-step counter rejected");
  ok(JumperWeightShadow.review(null,3).status().equals("weight-shadow=unknown"),
    "missing real screenshot state does not produce action");
  System.out.println("JUMPER_WEIGHT_SHADOW_PASS checks="+checks);
 }
}
