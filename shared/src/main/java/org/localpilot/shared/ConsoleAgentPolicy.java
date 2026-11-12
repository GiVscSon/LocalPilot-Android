package org.localpilot.shared;

/** Bounded observe->GGUF->button->fresh-frame policy, for REAL NES homebrew only.
 *  The AI may only propose a button. Emulator ACK never counts as success.
 */
public final class ConsoleAgentPolicy {
 public static final int MAX_STEPS=4;
 private boolean running;
 private int verified;
 private String pending;
 public void start(boolean builtInHomebrew,boolean modelReady){
  if(!builtInHomebrew||!modelReady)
   throw new IllegalStateException("Verified homebrew ROM and GGUF model required");
  stop();verified=0;running=true;
 }
 public boolean running(){return running;}
 public int verified(){return verified;}
 public String pending(){return pending;}
 public void stop(){running=false;pending=null;}
 public String choose(String raw){
  if(!running||pending!=null||verified>=MAX_STEPS){
   stop();throw new IllegalStateException("Agent action is not authorized");
  }
  if(raw==null || !raw.matches("ACTION=(?:A|B|UP|DOWN|LEFT|RIGHT|STOP)")){
   stop();throw new IllegalArgumentException("Model action rejected: strict single-line grammar");
  }
  String action=raw.substring(7);
  if("STOP".equals(action)){stop();return null;}
  pending=action;return action;
 }
 public boolean confirmed(String actualButton,String freshFrameStatus){
  if(!running||pending==null||!pending.equals(actualButton)){
   stop();throw new IllegalStateException("Unmatched emulated action");
  }
  pending=null;
  // Fresh-frame transition is the only accepted progress signal.
  if(!"PIXELS_DIFFER".equals(freshFrameStatus)){stop();return false;}
  verified++;
  if(verified>=MAX_STEPS){stop();return false;}
  return true;
 }
}
