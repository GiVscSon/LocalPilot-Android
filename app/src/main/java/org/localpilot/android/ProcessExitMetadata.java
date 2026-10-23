package org.localpilot.android;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.regex.Pattern;

/** Numeric Android exit metadata and a deliberately tiny, private-data-free process summary. */
public final class ProcessExitMetadata {
 public static final int MAX_SUMMARY_BYTES=128;
 private static final String MAGIC="LP_EXIT_V1";
 private static final Pattern BUILD=Pattern.compile("[0-9]{1,3}(?:\\.[0-9]{1,3}){1,3}(?:-[A-Za-z][A-Za-z0-9.-]{0,15})?/code[1-9][0-9]{0,9}");
 private static final Pattern SURFACE=Pattern.compile("CAPTURE_SURFACE_([1-9][0-9]{0,4})x([1-9][0-9]{0,4})");
 private static final Set<String> PHASES=Set.of("STARTUP","SERVICE_CONNECTED","SERVICE_INTERRUPTED",
  "SERVICE_DESTROYED","CAPTURE_CONSENT","CAPTURE_READY","CAPTURE_STOPPED");
 private static final Set<String> FAILURES=Set.of("RuntimeException","IllegalArgumentException",
  "IllegalStateException","NullPointerException","IndexOutOfBoundsException",
  "ArrayIndexOutOfBoundsException","ArithmeticException","SecurityException",
  "UnsupportedOperationException","OutOfMemoryError","ClassCastException",
  "ConcurrentModificationException","IllegalMonitorStateException","NumberFormatException",
  "BadTokenException","InvalidDisplayException","NotFoundException");
 private ProcessExitMetadata(){}

 /** Values verified against ApplicationExitInfo constants; OTHER does not explain a cause. */
 public static String reasonName(int reason){
  return switch(reason){
   case 0->"UNKNOWN";case 1->"EXIT_SELF";case 2->"SIGNALED";case 3->"LOW_MEMORY";
   case 4->"CRASH";case 5->"CRASH_NATIVE";case 6->"ANR";case 7->"INITIALIZATION_FAILURE";
   case 8->"PERMISSION_CHANGE";case 9->"EXCESSIVE_RESOURCE_USAGE";case 10->"USER_REQUESTED";
   case 11->"USER_STOPPED";case 12->"DEPENDENCY_DIED";case 13->"OTHER";case 14->"FREEZER";
   case 15->"PACKAGE_STATE_CHANGE";case 16->"PACKAGE_UPDATED";default->"UNKNOWN_CODE";
  };
 }
 public static String importanceName(int importance){
  return switch(importance){
   case 100->"foreground";case 125->"foreground_service";case 130->"perceptible_pre26";
   case 200->"visible";case 230->"perceptible";case 300->"service";case 325->"top_sleeping";
   case 350->"cant_save_state";case 400->"cached_or_background";case 500->"empty";
   case 1000->"gone";default->"unknown";
  };
 }
 public static boolean isAllowedPhase(String phase){
  if(phase==null)return false;
  if(PHASES.contains(phase))return true;
  if(phase.startsWith("FRAME_UI_"))return FAILURES.contains(phase.substring(9));
  var size=SURFACE.matcher(phase);
  if(!size.matches())return false;
  int w=Integer.parseInt(size.group(1)),h=Integer.parseInt(size.group(2));
  return w>=2&&h>=2&&w<=16384&&h<=16384&&(long)w*h<=8_000_000;
 }
 /** Unknown exception subclasses are reduced to a fixed marker; messages are never inspected. */
 public static String frameFailurePhase(Throwable failure){
  if(failure==null)throw new IllegalArgumentException("failure required");
  String type=failure.getClass().getSimpleName();
  return "FRAME_UI_"+(FAILURES.contains(type)?type:"RuntimeException");
 }
 private static boolean allowedBuild(String build){
  if(build==null||build.length()>64||!BUILD.matcher(build).matches())return false;
  try{return Long.parseLong(build.substring(build.lastIndexOf("/code")+5))<=Integer.MAX_VALUE;}
  catch(NumberFormatException invalid){return false;}
 }
 public static byte[] encode(String build,String phase){
  if(!allowedBuild(build)||!isAllowedPhase(phase))throw new IllegalArgumentException("unsupported process metadata");
  byte[] bytes=(MAGIC+"|"+build+"|"+phase).getBytes(StandardCharsets.US_ASCII);
  if(bytes.length>MAX_SUMMARY_BYTES)throw new IllegalArgumentException("process metadata too large");
  return bytes;
 }
 public static final class Summary {
  public final String build,phase;
  private Summary(String build,String phase){this.build=build;this.phase=phase;}
 }
 /** Null means unavailable/foreign/malformed. Never expose unknown system summary bytes. */
 public static Summary decode(byte[] bytes){
  if(bytes==null||bytes.length==0||bytes.length>MAX_SUMMARY_BYTES)return null;
  for(byte b:bytes)if(b<33||b>126)return null;
  String[] fields=new String(bytes,StandardCharsets.US_ASCII).split("\\|",-1);
  if(fields.length!=3||!MAGIC.equals(fields[0])||!allowedBuild(fields[1])||!isAllowedPhase(fields[2]))return null;
  return new Summary(fields[1],fields[2]);
 }
}
