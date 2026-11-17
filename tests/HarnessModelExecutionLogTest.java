package org.localpilot.android;

import java.nio.file.Files;
import java.nio.file.Path;

/** Synthetic evidence metadata fixtures; not proof of on-device inference. */
public final class HarnessModelExecutionLogTest {
 private static int checks;
 private static void check(boolean yes,String reason){checks++;if(!yes)throw new AssertionError(reason);}
 public static void main(String[] args)throws Exception{
  Path dir=Files.createTempDirectory("harness-model-infer-test");
  var accepted=new HarnessModelExecutionLog.Run("gguf:alpha","a".repeat(64),
    30,0x123,157,"RIGHT",8);
  HarnessModelExecutionLog.append(dir,accepted);
  var rejected=new HarnessModelExecutionLog.Run("gguf:alpha","a".repeat(64),
    46,0x1234,193,"REJECTED",0);
  HarnessModelExecutionLog.append(dir,rejected);
  var summary=HarnessModelExecutionLog.summary(dir);
  check(summary.inferenceCalls()==2,"two model calls recorded by producer");
  check(summary.validReplies()==1&&summary.rejectedReplies()==1,
    "invalid output never masquerades as model move");
  check(summary.lastLatencyMs()==193&&summary.lastGpuLayers()==0,
    "inference runtime metadata preserved");
  check(HarnessModelExecutionLog.read(dir).get(0).equals(accepted),"record decoded intact");
  boolean refused=false;
  try{HarnessModelExecutionLog.append(dir,new HarnessModelExecutionLog.Run(
    "gguf:alpha","bad",46,0x123,0,"A",0));}
  catch(IllegalArgumentException expected){refused=true;}
  check(refused,"reject invalid model SHA");
  var chord=new HarnessModelExecutionLog.Run("gguf:alpha","a".repeat(64),62,0x1235,211,"RIGHT+A",0);
  HarnessModelExecutionLog.append(dir,chord);
  check(HarnessModelExecutionLog.read(dir).get(2).equals(chord),"canonical chord retains exact inference metadata");
  refused=false;
  try{HarnessModelExecutionLog.append(dir,new HarnessModelExecutionLog.Run("gguf:alpha","a".repeat(64),78,0x1236,10,"UP+DOWN+A",0));}
  catch(IllegalArgumentException expected){refused=true;}
  check(refused&&HarnessModelExecutionLog.read(dir).size()==3,"opposing chord never enters inference evidence");
  var neutral=new HarnessModelExecutionLog.Run("gguf:alpha","a".repeat(64),78,0x1236,211,"WAIT",0);
  HarnessModelExecutionLog.append(dir,neutral);
  check(HarnessModelExecutionLog.read(dir).get(3).equals(neutral),"neutral choice retains actual inference identity and latency");
  Path log=dir.resolve(HarnessModelExecutionLog.FILE);
  byte[] damaged=Files.readAllBytes(log);damaged[damaged.length/3]^=1;
  Files.write(log,damaged);
  refused=false;
  try{HarnessModelExecutionLog.summary(dir);}
  catch(java.io.IOException expected){refused=true;}
  check(refused,"tampering fails closed");
  System.out.println("HARNESS_MODEL_EXECUTION_LOG_PASS checks="+checks+
    " modelActuallyRun=false syntheticFixturesOnly=true");
 }
}
