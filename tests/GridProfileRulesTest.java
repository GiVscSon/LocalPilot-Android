package org.localpilot.android;

/** Regression for a previously miswired protected-skull SWAP profile. */
public final class GridProfileRulesTest {
 private static int checks;
 private static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
 public static void main(String[] args){
  check(GridProfileRules.mode(6)==GridPuzzlePolicy.Mode.SWAP_MATCH3,"Match World stays a SWAP_MATCH3 puzzle");
  check(GridProfileRules.mode(7)==GridPuzzlePolicy.Mode.TAP_GROUP,"groups stay TAP_GROUP");
  check(GridProfileRules.mode(10)==GridPuzzlePolicy.Mode.TRACE_LINE,"normal straight-line profile");
  check(GridProfileRules.mode(11)==GridPuzzlePolicy.Mode.TRACE_LINE,"Naщупай ход must be TRACE_LINE, not swap");
  check(GridProfileRules.protectSkulls(11),"skulls protected for named puzzle");
  check(!GridProfileRules.protectSkulls(10),"normal trace profile does not silently label skulls");
  check(GridProfileRules.minimum(11)==3&&GridProfileRules.minimum(10)==3,
     "line profile requires at least 3 identical objects");
  check(GridProfileRules.minimum(6)==2&&GridProfileRules.minimum(7)==2,
     "legacy match and group profiles retain original minimum defaults");
  for(int invalid:new int[]{-1,0,4,5,9,12}){
   try{GridProfileRules.mode(invalid);throw new AssertionError("unexpected profile accepted "+invalid);}
   catch(IllegalArgumentException expected){checks++;}
  }
  System.out.println("Grid game profile routing: "+checks+" assertions PASS");
 }
}
