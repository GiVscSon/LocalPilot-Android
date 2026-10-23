package org.localpilot.android;

import java.nio.charset.StandardCharsets;

public final class ProcessExitMetadataTest {
 private static int checks;
 private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
 private static void rejects(String build,String phase){
  try{ProcessExitMetadata.encode(build,phase);throw new AssertionError("unsafe metadata accepted");}
  catch(IllegalArgumentException expected){checks++;}
 }
 public static void main(String[]args){
  String[] names={"UNKNOWN","EXIT_SELF","SIGNALED","LOW_MEMORY","CRASH","CRASH_NATIVE","ANR",
   "INITIALIZATION_FAILURE","PERMISSION_CHANGE","EXCESSIVE_RESOURCE_USAGE","USER_REQUESTED",
   "USER_STOPPED","DEPENDENCY_DIED","OTHER","FREEZER","PACKAGE_STATE_CHANGE","PACKAGE_UPDATED"};
  for(int i=0;i<names.length;i++)check(names[i].equals(ProcessExitMetadata.reasonName(i)),"SDK reason constant "+i);
  check("OTHER".equals(ProcessExitMetadata.reasonName(13)),"reason13 does not establish user stop or crash");
  check("UNKNOWN_CODE".equals(ProcessExitMetadata.reasonName(-1)),"negative vendor code stays unknown");
  check("UNKNOWN_CODE".equals(ProcessExitMetadata.reasonName(Integer.MAX_VALUE)),"future reason stays unknown");
  check("foreground_service".equals(ProcessExitMetadata.importanceName(125)),"foreground-service importance");
  check("cached_or_background".equals(ProcessExitMetadata.importanceName(400)),"cached/background share Android value");
  check("unknown".equals(ProcessExitMetadata.importanceName(999)),"unknown importance is not guessed");
  String build="0.7.23-dev/code39";
  for(String phase:new String[]{"STARTUP","SERVICE_CONNECTED","SERVICE_INTERRUPTED","SERVICE_DESTROYED",
      "CAPTURE_CONSENT","CAPTURE_READY","CAPTURE_STOPPED","CAPTURE_SURFACE_480x720",
      "FRAME_UI_NullPointerException","FRAME_UI_OutOfMemoryError"}){
   byte[] encoded=ProcessExitMetadata.encode(build,phase);
   check(encoded.length<=128,"Android process summary byte bound");
   var summary=ProcessExitMetadata.decode(encoded);
   check(summary!=null&&summary.build.equals(build)&&summary.phase.equals(phase),"own summary round trip "+phase);
  }
  for(String phase:new String[]{"https://example.invalid/private?token=secret","STARTUP\nsecret",
      "CAPTURE_SURFACE_0x720","CAPTURE_SURFACE_99999x1","CAPTURE_SURFACE_10000x10000",
      "FRAME_UI_some_private_message","FRAME_UI_NullPointerException|secret","","capturE_READY"})rejects(build,phase);
  for(String bad:new String[]{"https://secret.invalid/code39","0.7.23-dev/code0","0.7.23-dev/code2147483648",
      "0.7.23-dev/code39\nsecret","0.7.23-dev|token/code39","x".repeat(200),""})rejects(bad,"STARTUP");
  rejects(null,"STARTUP");rejects(build,null);
  check(ProcessExitMetadata.decode(null)==null,"missing system summary");
  check(ProcessExitMetadata.decode(new byte[0])==null,"empty system summary");
  check(ProcessExitMetadata.decode(new byte[129])==null,"oversized system summary");
  for(String foreign:new String[]{"foreign private process state","LP_EXIT_V2|"+build+"|STARTUP",
      "LP_EXIT_V1|"+build+"|STARTUP|extra","LP_EXIT_V1|"+build+"|FRAME_UI_token",
      "LP_EXIT_V1|"+build+"|STARTUP\n","LP_EXIT_V1||STARTUP"})
   check(ProcessExitMetadata.decode(foreign.getBytes(StandardCharsets.UTF_8))==null,"unsafe bytes never become exported metadata");
  byte[] nonAscii=ProcessExitMetadata.encode(build,"STARTUP");nonAscii[4]=(byte)0xff;
  check(ProcessExitMetadata.decode(nonAscii)==null,"invalid ASCII rejected rather than replacement-decoded");
  check(ProcessExitMetadata.frameFailurePhase(new NullPointerException("private url and token")).equals("FRAME_UI_NullPointerException"),"messages never inspected or exported");
  class PrivateSubclass extends RuntimeException{}
  check(ProcessExitMetadata.frameFailurePhase(new PrivateSubclass()).equals("FRAME_UI_RuntimeException"),"unknown exception type becomes fixed marker");
  System.out.println("Process exit metadata: "+checks+" assertions PASS; no Android lifecycle test implied");
 }
}
