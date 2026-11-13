package org.localpilot.shared;

/**
 * Verified intrinsic goal for the exact bundled, original HomebrewNes cartridge.
 * JSNES emulated 6502 zero-page $01/$02 carry player X/Y. This is a narrow
 * ENGINE_STATE task reward, not pixels-differ, an external ROM score or RL success.
 */
public final class HomebrewGoalReward {
 public static final int TARGET_X=192,TARGET_Y=112,REQUIRED_FRAMES=16;
 public static final String TASK="HOME_BREW_REACH_192_112_V1";
 public static final String SOURCE="JSNES_6502_ZERO_PAGE_01_02";
 private HomebrewGoalReward(){}

 public record Result(int beforeX,int beforeY,int afterX,int afterY,
     int beforeDistance,int afterDistance,int reward,boolean reached) {}

 public static int distance(int x,int y){
  if(x<0||x>255||y<0||y>255)throw new IllegalArgumentException("NES state byte invalid");
  return Math.abs(x-TARGET_X)+Math.abs(y-TARGET_Y);
 }
 public static Result verify(String action,int frameBefore,int frameAfter,
     int beforeX,int beforeY,int afterX,int afterY){
  if(!ConsoleAgentContract.playable(action)||frameBefore<15||
     frameAfter-frameBefore!=REQUIRED_FRAMES||
     distance(beforeX,beforeY)<0||distance(afterX,afterY)<0)
   throw new IllegalArgumentException("Invalid verified ROM task input");
  int dx=afterX-beforeX,dy=afterY-beforeY;
  // The ROM moves at most once per emulated frame, and on ONE direction only.
  // Disallow wrapping or abnormal RAM jumps; these need independent proof.
  if(Math.abs(dx)>REQUIRED_FRAMES+1||Math.abs(dy)>REQUIRED_FRAMES+1)
   throw new IllegalArgumentException("NES coordinate jump");
  if(!switch(action){
   case "RIGHT"->dx>=0&&dy==0;
   case "LEFT"->dx<=0&&dy==0;
   case "UP"->dy<=0&&dx==0;
   case "DOWN"->dy>=0&&dx==0;
   case "A","B"->dx==0&&dy==0;
   default->false;
  })throw new IllegalArgumentException("RAM movement contradicts button");
  int d0=distance(beforeX,beforeY),d1=distance(afterX,afterY);
  boolean reached=d1<=2;
  // A terminal bonus is task-specific. It must never imply a real game's win.
  int shaped=d0-d1+(reached&&d0>2?20:0);
  return new Result(beforeX,beforeY,afterX,afterY,d0,d1,shaped,reached);
 }
}
