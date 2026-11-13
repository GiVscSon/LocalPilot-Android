package org.localpilot.gamelab;

/**
 * Simulator-only continuation policy. Never controls AccessibilityService.
 * Disabling the *automatic* no-change STOP is limited to a short homebrew
 * batch. Manual STOP, release of held keys, stale-frame rejection, and the
 * hard maximum remain unconditional.
 */
public final class NesExperimentPolicy {
 public static final int MAX_STEPS=5;
 public enum Decision {
  CONTINUE, STOP_NO_CHANGE, STOP_INTEGRITY, STOP_LIMIT, STOP_MANUAL
 }
 private int completed;
 private boolean running;
 private boolean continueOnNoChange;
 public void start(boolean homebrew,boolean exploratory){
  if(!homebrew)throw new IllegalArgumentException("Only built-in test ROM");
  completed=0;continueOnNoChange=exploratory;running=true;
 }
 public Decision observe(String outcome){
  if(!running)return Decision.STOP_MANUAL;
  if(!"PIXELS_DIFFER".equals(outcome)&&!"NO_VISUAL_CHANGE".equals(outcome)){
   running=false;return Decision.STOP_INTEGRITY;
  }
  completed++;
  if(completed>=MAX_STEPS){running=false;return Decision.STOP_LIMIT;}
  if("NO_VISUAL_CHANGE".equals(outcome)&&!continueOnNoChange){
   running=false;return Decision.STOP_NO_CHANGE;
  }
  return Decision.CONTINUE;
 }
 public void stop(){running=false;}
 public boolean running(){return running;}
 public boolean exploratory(){return running&&continueOnNoChange;}
 public int completed(){return completed;}
}
