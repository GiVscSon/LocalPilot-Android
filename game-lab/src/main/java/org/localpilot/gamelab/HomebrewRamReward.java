package org.localpilot.gamelab;

/**
 * A goal-specific reward for the exact built-in 6502 homebrew cartridge.
 *
 * The ROM stores the player X coordinate at NES zero-page $0001 and polls
 * RIGHT/LEFT once per vblank. Goal: move toward increasing X without wrapping.
 * Rewards are derived from CPU RAM, NEVER pixels, an ACK or a GUI event.
 *
 * The caller MUST first check exact ROM byte identity against HomebrewNes.
 * Reward evidence is distinct from on-policy eligibility (obs64, oldLogProb).
 */
public final class HomebrewRamReward {
 private HomebrewRamReward() {}
 public static final String GOAL="builtin-homebrew-right-progress-v1";
 public static final int HOLD_FRAMES=16;
 public enum Reason {
  VERIFIED_ROM_RAM, ROM_NOT_PINNED, CORE_NOT_VERIFIED, INVALID_FRAMES,
  INVALID_ACTION, MISSING_RAM_STATE, RAM_JUMP_OR_WRAP, WRONG_DIRECTION
 }
 public record Evidence(Float reward,Reason reason,int xBefore,int xAfter,
                         int frameBefore,int frameAfter) {
  public boolean verified(){return reason==Reason.VERIFIED_ROM_RAM&&reward!=null;}
  public Evidence {
   if(reason==null)throw new IllegalArgumentException("Missing evidence reason");
  }
 }
 public static Evidence evaluate(boolean exactHomebrewRom,boolean completedVerifiedCoreStep,
                                 String button,int frameBefore,int frameAfter,
                                 int xBefore,int xAfter) {
  if(!exactHomebrewRom)
   return no(Reason.ROM_NOT_PINNED,xBefore,xAfter,frameBefore,frameAfter);
  if(!completedVerifiedCoreStep)
   return no(Reason.CORE_NOT_VERIFIED,xBefore,xAfter,frameBefore,frameAfter);
  if(frameBefore<15||frameAfter-frameBefore!=HOLD_FRAMES)
   return no(Reason.INVALID_FRAMES,xBefore,xAfter,frameBefore,frameAfter);
  if(!("RIGHT".equals(button)||"LEFT".equals(button)||"UP".equals(button)||
       "DOWN".equals(button)||"A".equals(button)||"B".equals(button)))
   return no(Reason.INVALID_ACTION,xBefore,xAfter,frameBefore,frameAfter);
  if(xBefore<0||xBefore>255||xAfter<0||xAfter>255)
   return no(Reason.MISSING_RAM_STATE,xBefore,xAfter,frameBefore,frameAfter);
  int delta=xAfter-xBefore;
  // One horizontal movement step per emulated vblank; wrap is excluded.
  if(Math.abs(delta)>HOLD_FRAMES)
   return no(Reason.RAM_JUMP_OR_WRAP,xBefore,xAfter,frameBefore,frameAfter);
  if("RIGHT".equals(button)&&delta<0||"LEFT".equals(button)&&delta>0||
     !("RIGHT".equals(button)||"LEFT".equals(button))&&delta!=0)
   return no(Reason.WRONG_DIRECTION,xBefore,xAfter,frameBefore,frameAfter);
  return new Evidence(delta/(float)HOLD_FRAMES,Reason.VERIFIED_ROM_RAM,
                      xBefore,xAfter,frameBefore,frameAfter);
 }
 private static Evidence no(Reason reason,int xBefore,int xAfter,int frameBefore,int frameAfter){
  return new Evidence(null,reason,xBefore,xAfter,frameBefore,frameAfter);
 }
}
