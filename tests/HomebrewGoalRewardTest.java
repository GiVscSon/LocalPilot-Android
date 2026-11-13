package org.localpilot.shared;
public final class HomebrewGoalRewardTest {
 static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
 static void invalid(String button,int start,int end,int bx,int by,int ax,int ay){
  try{HomebrewGoalReward.verify(button,start,end,bx,by,ax,ay);}
  catch(IllegalArgumentException good){return;}
  throw new AssertionError("Invalid fake 6502 state accepted");
 }
 public static void main(String[] argv){
  HomebrewGoalReward.Result toward=HomebrewGoalReward.verify("RIGHT",90,106,128,112,144,112);
  check(toward.reward()==16&&toward.afterDistance()==48,"real RIGHT state approaches target");
  HomebrewGoalReward.Result away=HomebrewGoalReward.verify("LEFT",90,106,128,112,112,112);
  check(away.reward()==-16,"wrong direction has negative task reward");
  HomebrewGoalReward.Result idle=HomebrewGoalReward.verify("B",90,106,128,112,128,112);
  check(idle.reward()==0&&!idle.reached(),"B does not invent goal");
  HomebrewGoalReward.Result at=HomebrewGoalReward.verify("RIGHT",90,106,180,112,192,112);
  check(at.reached()&&at.reward()==32,"terminal bonus only on exact target transition");
  invalid("RIGHT",90,106,128,112,112,112);
  invalid("UP",90,106,128,112,144,112);
  invalid("A",90,106,128,112,144,112);
  invalid("RIGHT",90,105,128,112,144,112);
  invalid("RIGHT",90,106,128,112,210,112);
  invalid("START",90,106,128,112,128,112);
  invalid("RIGHT",90,106,128,112,250,999);
  System.out.println("NES_HOME_BREW_GOAL_REWARD_PASS");
 }
}
