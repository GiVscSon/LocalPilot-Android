package org.localpilot.android;

import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;

/** Bounded metadata only; independent windows per stage, never a training reward. */
public final class StageMetrics {
 public enum Stage { CAPTURE, READ, POLICY, ANDROID_ACK }
 private static final int LIMIT=128;
 private static final int MAX_CODE_LENGTH=32;
 private final long[][] values=new long[Stage.values().length][LIMIT];
 private final int[] counts=new int[Stage.values().length], next=new int[counts.length];
 private final long[] attempts=new long[counts.length], errors=new long[counts.length], cancellations=new long[counts.length];
 private final String[] lastErrors=new String[counts.length];

 /** Explicit stage entry; successful duration samples are recorded separately. */
 public synchronized void attempt(Stage stage){int i=index(stage);attempts[i]=increment(attempts[i]);}

 /** Codes must be stable identifiers, never exception messages or captured content. */
 public synchronized void failure(Stage stage,String code){
  int i=index(stage);
  validateCode(code);
  errors[i]=increment(errors[i]);
  lastErrors[i]=code;
 }

 /** Cancellation is distinct from a processing error and carries no duration sample. */
 public synchronized void cancelled(Stage stage){int i=index(stage);cancellations[i]=increment(cancellations[i]);}

 /** Preserve the last 128 successful durations; this does not increment attempts. */
 public synchronized void record(Stage stage,long nanos){
  if(nanos<0)throw new IllegalArgumentException("negative duration");
  int i=index(stage);
  values[i][next[i]]=nanos;
  next[i]=(next[i]+1)%LIMIT;
  counts[i]=Math.min(LIMIT,counts[i]+1);
 }

 public synchronized String report(){
  StringBuilder s=new StringBuilder("LocalPilot stage-metrics-v1\nRAM only; n is the last 128 successful duration samples per stage.\nIndependent windows; not end-to-end latency or gameplay success.\nScheduled animation waits, failures and Stop are excluded from duration percentiles.\nAttempts, errors and cancelled are independent explicit counters for this service lifetime, saturating at Long.MAX_VALUE.\nlast_error is the last stable failure code, retained after success or cancellation; '-' means none.\n");
  for(Stage stage:Stage.values()){
   int i=stage.ordinal(),n=counts[i];
   s.append(stage).append(" n=").append(n);
   if(n>0){
    long[] a=Arrays.copyOf(values[i],n);
    Arrays.sort(a);
    s.append(" p50_ms=").append(ms(a[(n-1)/2])).append(" p95_ms=").append(ms(a[(int)Math.ceil(n*.95)-1])).append(" max_ms=").append(ms(a[n-1]));
   }
   s.append(" attempts=").append(attempts[i]).append(" errors=").append(errors[i]).append(" cancelled=").append(cancellations[i]).append(" last_error=").append(lastErrors[i]==null?"-":lastErrors[i]).append('\n');
  }
  return s.toString();
 }

 private static int index(Stage stage){return Objects.requireNonNull(stage,"stage").ordinal();}
 private static long increment(long count){return count==Long.MAX_VALUE?Long.MAX_VALUE:count+1;}
 private static void validateCode(String code){
  if(code==null||code.isEmpty()||code.length()>MAX_CODE_LENGTH)throw new IllegalArgumentException("invalid diagnostic code");
  for(int i=0;i<code.length();i++){
   char c=code.charAt(i);
   if(!(c>='A'&&c<='Z')&&!(i>0&&(c>='0'&&c<='9'||c=='_')))throw new IllegalArgumentException("invalid diagnostic code");
  }
 }
 private static String ms(long ns){return String.format(Locale.ROOT,"%.3f",ns/1000000.0);}
}
