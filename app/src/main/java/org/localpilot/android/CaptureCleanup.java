package org.localpilot.android;

/** Independent native teardown steps: one failed release must not strand the rest. */
final class CaptureCleanup {
 private CaptureCleanup(){}
 static boolean run(Runnable... steps){
  if(steps==null||steps.length>8)throw new IllegalArgumentException("capture cleanup steps");
  for(Runnable step:steps)if(step==null)throw new IllegalArgumentException("capture cleanup step");
  boolean complete=true;
  for(Runnable step:steps)try{step.run();}catch(RuntimeException|OutOfMemoryError error){complete=false;}
  return complete;
 }
}
