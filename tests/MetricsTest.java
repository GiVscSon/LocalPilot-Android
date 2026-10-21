package org.localpilot.android;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.TreeMap;

public class MetricsTest {
 private static int checks;

 public static void main(String[] args)throws Exception{
  failureDiagnostics();
  mixedFailureCodes();
  boundedFailureCodes();
  percentileBoundaries();
  ringOverflow();
  rejectedInputs();
  counterSaturation();
  histogramSaturation();
  System.out.println("PASS "+checks+" metrics assertions");
 }

 private static void mixedFailureCodes(){
  StageMetrics m=new StageMetrics();
  check(histogram(m,StageMetrics.Stage.ANDROID_ACK).equals("ANDROID_ACK error_counts=- error_overflow=0"));
  m.failure(StageMetrics.Stage.ANDROID_ACK,"STALE_TICKET");
  m.failure(StageMetrics.Stage.ANDROID_ACK,"PERMIT_REQUIRED");
  m.failure(StageMetrics.Stage.ANDROID_ACK,"STALE_TICKET");
  check(line(m,StageMetrics.Stage.ANDROID_ACK).equals("ANDROID_ACK n=0 attempts=0 errors=3 cancelled=0 last_error=STALE_TICKET"));
  String expected="ANDROID_ACK error_counts=PERMIT_REQUIRED:1,STALE_TICKET:2 error_overflow=0";
  check(histogram(m,StageMetrics.Stage.ANDROID_ACK).equals(expected));
  m.failure(StageMetrics.Stage.READ,"STALE_TICKET");
  check(histogram(m,StageMetrics.Stage.READ).equals("READ error_counts=STALE_TICKET:1 error_overflow=0"));
  check(histogram(m,StageMetrics.Stage.CAPTURE).equals("CAPTURE error_counts=- error_overflow=0"));
  m.record(StageMetrics.Stage.ANDROID_ACK,1000);
  m.cancelled(StageMetrics.Stage.ANDROID_ACK);
  check(histogram(m,StageMetrics.Stage.ANDROID_ACK).equals(expected));
  check(line(m,StageMetrics.Stage.ANDROID_ACK).endsWith("errors=3 cancelled=1 last_error=STALE_TICKET"));
  check(m.report().contains("last_error is only the latest stable failure code, not a breakdown of all errors"));
 }

 private static void boundedFailureCodes(){
  StageMetrics m=new StageMetrics();
  // Arrival order differs from report order; exactly the first 32 codes are retained.
  for(int i=31;i>=0;i--)m.failure(StageMetrics.Stage.READ,String.format(java.util.Locale.ROOT,"ERR_%02d",i));
  StringBuilder expected=new StringBuilder("READ error_counts=");
  for(int i=0;i<32;i++){
   if(i>0)expected.append(',');
   expected.append(String.format(java.util.Locale.ROOT,"ERR_%02d:1",i));
  }
  check(histogram(m,StageMetrics.Stage.READ).equals(expected+" error_overflow=0"));
  m.failure(StageMetrics.Stage.READ,"ERR_32");
  m.failure(StageMetrics.Stage.READ,"ERR_32");
  m.failure(StageMetrics.Stage.READ,"OTHER");
  check(histogram(m,StageMetrics.Stage.READ).equals(expected+" error_overflow=3"));
  check(!histogram(m,StageMetrics.Stage.READ).contains("ERR_32"));
  check(!histogram(m,StageMetrics.Stage.READ).contains("OTHER"));
  check(line(m,StageMetrics.Stage.READ).endsWith("errors=35 cancelled=0 last_error=OTHER"));
  m.failure(StageMetrics.Stage.READ,"ERR_00");
  check(histogram(m,StageMetrics.Stage.READ).equals(expected.toString().replace("ERR_00:1","ERR_00:2")+" error_overflow=3"));
  check(line(m,StageMetrics.Stage.READ).endsWith("errors=36 cancelled=0 last_error=ERR_00"));
  m.failure(StageMetrics.Stage.POLICY,"OTHER");
  check(histogram(m,StageMetrics.Stage.POLICY).equals("POLICY error_counts=OTHER:1 error_overflow=0"));
  String before=m.report();
  boolean rejected=false;
  try{m.failure(StageMetrics.Stage.READ,"PRIVATE MESSAGE");}catch(IllegalArgumentException expectedError){rejected=true;}
  check(rejected);
  check(m.report().equals(before));
 }

 private static void failureDiagnostics(){
  StageMetrics m=new StageMetrics();
  for(StageMetrics.Stage stage:StageMetrics.Stage.values())check(line(m,stage).equals(stage+" n=0 attempts=0 errors=0 cancelled=0 last_error=-"));
  m.attempt(StageMetrics.Stage.READ);
  m.failure(StageMetrics.Stage.READ,"UNKNOWN_COLOR");
  check(line(m,StageMetrics.Stage.READ).equals("READ n=0 attempts=1 errors=1 cancelled=0 last_error=UNKNOWN_COLOR"));
  check(!line(m,StageMetrics.Stage.READ).contains("p50_ms="));
  check(line(m,StageMetrics.Stage.CAPTURE).equals("CAPTURE n=0 attempts=0 errors=0 cancelled=0 last_error=-"));
  m.attempt(StageMetrics.Stage.POLICY);
  m.cancelled(StageMetrics.Stage.POLICY);
  check(line(m,StageMetrics.Stage.POLICY).equals("POLICY n=0 attempts=1 errors=0 cancelled=1 last_error=-"));
  m.record(StageMetrics.Stage.READ,450000);
  m.cancelled(StageMetrics.Stage.READ);
  check(line(m,StageMetrics.Stage.READ).equals("READ n=1 p50_ms=0.450 p95_ms=0.450 max_ms=0.450 attempts=1 errors=1 cancelled=1 last_error=UNKNOWN_COLOR"));
  m.failure(StageMetrics.Stage.READ,"INVALID_ROI");
  check(line(m,StageMetrics.Stage.READ).endsWith("attempts=1 errors=2 cancelled=1 last_error=INVALID_ROI"));
  check(line(m,StageMetrics.Stage.ANDROID_ACK).equals("ANDROID_ACK n=0 attempts=0 errors=0 cancelled=0 last_error=-"));
  m.record(StageMetrics.Stage.CAPTURE,1000);
  check(line(m,StageMetrics.Stage.CAPTURE).equals("CAPTURE n=1 p50_ms=0.001 p95_ms=0.001 max_ms=0.001 attempts=0 errors=0 cancelled=0 last_error=-"));
 }

 private static void percentileBoundaries(){
  StageMetrics m=new StageMetrics();
  m.record(StageMetrics.Stage.READ,2000000);
  check(line(m,StageMetrics.Stage.READ).startsWith("READ n=1 p50_ms=2.000 p95_ms=2.000 max_ms=2.000 "));
  m.record(StageMetrics.Stage.READ,4000000);
  check(line(m,StageMetrics.Stage.READ).startsWith("READ n=2 p50_ms=2.000 p95_ms=4.000 max_ms=4.000 "));
  StageMetrics boundary=new StageMetrics();
  for(int i=1;i<=20;i++)boundary.record(StageMetrics.Stage.POLICY,i*1000000L);
  check(line(boundary,StageMetrics.Stage.POLICY).startsWith("POLICY n=20 p50_ms=10.000 p95_ms=19.000 max_ms=20.000 "));
  boundary.record(StageMetrics.Stage.POLICY,21000000);
  check(line(boundary,StageMetrics.Stage.POLICY).startsWith("POLICY n=21 p50_ms=11.000 p95_ms=20.000 max_ms=21.000 "));
  StageMetrics zero=new StageMetrics();
  zero.record(StageMetrics.Stage.ANDROID_ACK,0);
  check(line(zero,StageMetrics.Stage.ANDROID_ACK).startsWith("ANDROID_ACK n=1 p50_ms=0.000 p95_ms=0.000 max_ms=0.000 "));
 }

 private static void ringOverflow(){
  StageMetrics m=new StageMetrics();
  for(int i=1;i<=200;i++){
   m.attempt(StageMetrics.Stage.CAPTURE);
   m.record(StageMetrics.Stage.CAPTURE,i*1000000L);
  }
  check(line(m,StageMetrics.Stage.CAPTURE).equals("CAPTURE n=128 p50_ms=136.000 p95_ms=194.000 max_ms=200.000 attempts=200 errors=0 cancelled=0 last_error=-"));
  m.attempt(StageMetrics.Stage.CAPTURE);
  m.record(StageMetrics.Stage.CAPTURE,201000000L);
  check(line(m,StageMetrics.Stage.CAPTURE).equals("CAPTURE n=128 p50_ms=137.000 p95_ms=195.000 max_ms=201.000 attempts=201 errors=0 cancelled=0 last_error=-"));
  check(line(m,StageMetrics.Stage.READ).equals("READ n=0 attempts=0 errors=0 cancelled=0 last_error=-"));
 }

 private static void rejectedInputs(){
  StageMetrics m=new StageMetrics();
  String before=m.report();
  boolean rejected=false;
  try{m.record(StageMetrics.Stage.READ,-1);}catch(IllegalArgumentException expected){rejected=true;}
  check(rejected);
  check(m.report().equals(before));
  String[] invalid={null,""," ","bad_message","Exception: private text","CODE\nSECRET","A".repeat(33),"_BAD","1BAD","ERR-ONE","Ё"};
  for(String code:invalid){
   rejected=false;
   try{m.failure(StageMetrics.Stage.READ,code);}catch(IllegalArgumentException expected){rejected=true;check(expected.getMessage().equals("invalid diagnostic code"));}
   check(rejected);
   check(m.report().equals(before));
  }
  m.failure(StageMetrics.Stage.READ,"A");
  check(line(m,StageMetrics.Stage.READ).endsWith("errors=1 cancelled=0 last_error=A"));
  String longest="A".repeat(32);
  m.failure(StageMetrics.Stage.READ,longest);
  check(line(m,StageMetrics.Stage.READ).endsWith("errors=2 cancelled=0 last_error="+longest));
 }

 private static void counterSaturation()throws Exception{
  StageMetrics m=new StageMetrics();
  // Arrange the unreachable-in-practice lifetime boundary without billions of calls.
  for(String name:new String[]{"attempts","errors","cancellations"}){
   Field field=StageMetrics.class.getDeclaredField(name);
   field.setAccessible(true);
   ((long[])field.get(m))[StageMetrics.Stage.READ.ordinal()]=Long.MAX_VALUE-1;
  }
  for(int i=0;i<2;i++){
   m.attempt(StageMetrics.Stage.READ);
   m.failure(StageMetrics.Stage.READ,"READ_FAILED");
   m.cancelled(StageMetrics.Stage.READ);
  }
  check(line(m,StageMetrics.Stage.READ).equals("READ n=0 attempts="+Long.MAX_VALUE+" errors="+Long.MAX_VALUE+" cancelled="+Long.MAX_VALUE+" last_error=READ_FAILED"));
  check(line(m,StageMetrics.Stage.POLICY).equals("POLICY n=0 attempts=0 errors=0 cancelled=0 last_error=-"));
 }

 @SuppressWarnings("unchecked")
 private static void histogramSaturation()throws Exception{
  StageMetrics m=new StageMetrics();
  m.failure(StageMetrics.Stage.READ,"READ_FAILED");
  Field countsField=StageMetrics.class.getDeclaredField("errorCounts");
  countsField.setAccessible(true);
  Map<StageMetrics.Stage,TreeMap<String,Long>> counts=(Map<StageMetrics.Stage,TreeMap<String,Long>>)countsField.get(m);
  counts.get(StageMetrics.Stage.READ).put("READ_FAILED",Long.MAX_VALUE-1);
  m.failure(StageMetrics.Stage.READ,"READ_FAILED");
  m.failure(StageMetrics.Stage.READ,"READ_FAILED");
  check(histogram(m,StageMetrics.Stage.READ).equals("READ error_counts=READ_FAILED:"+Long.MAX_VALUE+" error_overflow=0"));
  // Fill the other stage, then arrange its unreachable-in-practice overflow boundary.
  for(int i=0;i<32;i++)m.failure(StageMetrics.Stage.ANDROID_ACK,"ERR_"+i);
  Field overflowField=StageMetrics.class.getDeclaredField("errorOverflow");
  overflowField.setAccessible(true);
  ((long[])overflowField.get(m))[StageMetrics.Stage.ANDROID_ACK.ordinal()]=Long.MAX_VALUE-1;
  m.failure(StageMetrics.Stage.ANDROID_ACK,"UNRETAINED_A");
  m.failure(StageMetrics.Stage.ANDROID_ACK,"UNRETAINED_B");
  check(histogram(m,StageMetrics.Stage.ANDROID_ACK).endsWith(" error_overflow="+Long.MAX_VALUE));
  check(counts.get(StageMetrics.Stage.ANDROID_ACK).size()==32);
  check(!histogram(m,StageMetrics.Stage.ANDROID_ACK).contains("UNRETAINED_"));
  check(line(m,StageMetrics.Stage.ANDROID_ACK).endsWith("errors=34 cancelled=0 last_error=UNRETAINED_B"));
  check(histogram(m,StageMetrics.Stage.POLICY).equals("POLICY error_counts=- error_overflow=0"));
 }

 private static String histogram(StageMetrics metrics,StageMetrics.Stage stage){
  for(String line:metrics.report().split("\n"))if(line.startsWith(stage+" error_counts="))return line;
  throw new AssertionError("missing error histogram "+stage);
 }

 private static String line(StageMetrics metrics,StageMetrics.Stage stage){
  for(String line:metrics.report().split("\n"))if(line.startsWith(stage+" n="))return line;
  throw new AssertionError("missing stage "+stage);
 }
 private static void check(boolean ok){checks++;if(!ok)throw new AssertionError("metrics check "+checks);}
}
