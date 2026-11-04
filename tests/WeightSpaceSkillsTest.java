package org.localpilot.android.rl;

import java.nio.file.*;
import java.util.Arrays;

/** Reproducible proof that patches modify the intended weights, not a whole model. */
public final class WeightSpaceSkillsTest {
 private static int n=0;
 private static void check(boolean c,String why){n++;if(!c)throw new AssertionError(why);}
 public static void main(String[] args)throws Exception{
  var original=WeightSpaceSkills.Network.program(WeightSpaceSkills.Genre.PLATFORM_RECOVERY);
  double[][] old=original.matrix();
  var edited=WeightSpaceSkills.patchRecovery(original);
  check(original.changedWeights(edited)==1,"single targeted parameter");
  check(original.weight(0,3)==0&&edited.weight(0,3)==.8,"exact output direction edited");
  check(Arrays.deepEquals(original.matrix(),old),"original checkpoint immutable");
  double[][] copy=edited.matrix();copy[0][3]=-99;
  check(edited.weight(0,3)==.8,"export cannot mutate stored weights");
  var valid=new WeightSpaceSkills.Frame(.48,.65,.15,1,true,true);
  check(original.predict(WeightSpaceSkills.Genre.PLATFORM_RECOVERY,valid)==WeightSpaceSkills.Action.STAY,
    "original policy stuck at plateau");
  check(edited.predict(WeightSpaceSkills.Genre.PLATFORM_RECOVERY,valid)==WeightSpaceSkills.Action.LEFT,
    "targeted left retreat exits plateau");
  check(edited.predict(WeightSpaceSkills.Genre.PLATFORM_RECOVERY,
      new WeightSpaceSkills.Frame(.48,.65,.15,1,false,true))==WeightSpaceSkills.Action.STAY,
    "stale frame must not authorize action");
  check(edited.predict(WeightSpaceSkills.Genre.PLATFORM_RECOVERY,
      new WeightSpaceSkills.Frame(.48,.65,.15,1,true,false))==WeightSpaceSkills.Action.STAY,
    "unrecognized scene must wait");
  check(edited.predict(WeightSpaceSkills.Genre.PLATFORM_RECOVERY,
      new WeightSpaceSkills.Frame(.03,.80,.1,1,true,true))==WeightSpaceSkills.Action.STAY,
    "no retreat into a known unsafe lane");
  check(edited.predict(WeightSpaceSkills.Genre.PLATFORM_RECOVERY,
      new WeightSpaceSkills.Frame(.15,.85,.2,0,true,true))==WeightSpaceSkills.Action.STAY,
    "do not change healthy ascent");
  var traffic=WeightSpaceSkills.Network.program(WeightSpaceSkills.Genre.LANE_ESCAPE);
  check(traffic.predict(WeightSpaceSkills.Genre.LANE_ESCAPE,
      new WeightSpaceSkills.Frame(.85,.2,.35,0,true,true))==WeightSpaceSkills.Action.LEFT,
    "lane game expert must select safe lane");
  var flap=WeightSpaceSkills.Network.program(WeightSpaceSkills.Genre.GAP_FLIGHT);
  check(flap.predict(WeightSpaceSkills.Genre.GAP_FLIGHT,
      new WeightSpaceSkills.Frame(.2,.80,0,0,true,true))==WeightSpaceSkills.Action.FLAP,
    "flappy expert can trigger ascent from danger");
  check(flap.predict(WeightSpaceSkills.Genre.GAP_FLIGHT,
      new WeightSpaceSkills.Frame(.8,.2,0,0,true,true))==WeightSpaceSkills.Action.STAY,
    "flappy expert can wait through gap");
  for(var genre:WeightSpaceSkills.Genre.values())
   check(WeightSpaceSkills.Network.program(genre).changedWeights(
     WeightSpaceSkills.Network.program(genre))==0,"deterministic expert initial state");
  for(long seed:new long[]{20261104,99173,20261201}){
   var result=WeightSpaceSkills.evaluateRetreat(seed,400);
   check(result.passes(),"held-out offline patch must improve plateau");
   check(result.baselineCorrect()<result.patchedCorrect()
      &&result.plateauRecovered()>0&&result.regressions()==0,
      "recovery improvement cannot harm safe cases");
   check(!result.json().contains("\"eligible_for_android\":true"),
      "offline results never grant device input");
  }
  try{original.patch(0,3,Double.NaN);throw new AssertionError("accepted unsafe patch");}
  catch(IllegalArgumentException expected){n++;}
  Path file=Path.of("build/reports/weight-space-skills.json");
  Files.createDirectories(file.getParent());
  Files.writeString(file,WeightSpaceSkills.evaluateRetreat(20261104,400).json()+"\n");
  System.out.println("WEIGHT_SPACE_SKILLS_PASS checks="+n+" report="+file);
 }
}
