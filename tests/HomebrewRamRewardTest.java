package org.localpilot.gamelab;

/** Synthetic scalar assertions; actual JSNES 6502 effects checked in Node test. */
public final class HomebrewRamRewardTest {
 private static int count;
 private static void check(boolean ok,String message){
  count++;if(!ok)throw new AssertionError(message);
 }
 private static void blocked(boolean rom,boolean core,String button,int start,int end,
                             int xBefore,int xAfter,HomebrewRamReward.Reason why){
  var evidence=HomebrewRamReward.evaluate(rom,core,button,start,end,xBefore,xAfter);
  check(!evidence.verified()&&evidence.reward()==null&&evidence.reason()==why,
    "expected "+why+" got "+evidence);
 }
 public static void main(String[] args){
  var right=HomebrewRamReward.evaluate(true,true,"RIGHT",45,61,128,144);
  check(right.verified(),"valid ROM task goal");
  check(right.reason()==HomebrewRamReward.Reason.VERIFIED_ROM_RAM,"source only ROM RAM");
  check(right.reward()==1.0f,"rightward progress normalized by 16 emulated frames");
  check(right.xBefore()==128&&right.xAfter()==144,"actual X bytes retained");
  check(HomebrewRamReward.evaluate(true,true,"RIGHT",45,61,128,130).reward()==.125f,
    "partial movement has proportional value");
  check(HomebrewRamReward.evaluate(true,true,"LEFT",45,61,128,112).reward()==-1.0f,
    "leftward movement is negative progress");
  check(HomebrewRamReward.evaluate(true,true,"A",45,61,128,128).reward()==0.0f,
    "palette button cannot receive fake motion reward");
  check(HomebrewRamReward.evaluate(true,true,"UP",45,61,128,128).reward()==0.0f,
    "vertical button cannot receive fake horizontal reward");
  blocked(false,true,"RIGHT",45,61,128,144,HomebrewRamReward.Reason.ROM_NOT_PINNED);
  blocked(true,false,"RIGHT",45,61,128,144,HomebrewRamReward.Reason.CORE_NOT_VERIFIED);
  blocked(true,true,"RIGHT",10,26,128,144,HomebrewRamReward.Reason.INVALID_FRAMES);
  blocked(true,true,"RIGHT",45,60,128,144,HomebrewRamReward.Reason.INVALID_FRAMES);
  blocked(true,true,"RIGHT",45,62,128,144,HomebrewRamReward.Reason.INVALID_FRAMES);
  blocked(true,true,"START",45,61,128,144,HomebrewRamReward.Reason.INVALID_ACTION);
  blocked(true,true,"RIGHT",45,61,-1,144,HomebrewRamReward.Reason.MISSING_RAM_STATE);
  blocked(true,true,"RIGHT",45,61,128,300,HomebrewRamReward.Reason.MISSING_RAM_STATE);
  blocked(true,true,"RIGHT",45,61,128,160,HomebrewRamReward.Reason.RAM_JUMP_OR_WRAP);
  blocked(true,true,"RIGHT",45,61,250,4,HomebrewRamReward.Reason.RAM_JUMP_OR_WRAP);
  blocked(true,true,"RIGHT",45,61,128,120,HomebrewRamReward.Reason.WRONG_DIRECTION);
  blocked(true,true,"LEFT",45,61,128,132,HomebrewRamReward.Reason.WRONG_DIRECTION);
  blocked(true,true,"DOWN",45,61,128,130,HomebrewRamReward.Reason.WRONG_DIRECTION);
  check(!HomebrewRamReward.evaluate(true,false,"A",45,61,128,128).verified(),
    "input transport without verified core is not success");
  System.out.println("HOMEBREW_RAM_REWARD_CONTRACT_PASS checks="+count+
    " fixture=synthetic trainingWeightsChanged=false");
 }
}
