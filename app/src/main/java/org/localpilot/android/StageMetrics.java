package org.localpilot.android;
import java.util.*;
/** Bounded metadata only; independent windows per stage, never a training reward. */
public final class StageMetrics {
 public enum Stage { CAPTURE, READ, POLICY, ANDROID_ACK }
 private static final int LIMIT=128;
 private final long[][] values=new long[Stage.values().length][LIMIT];
 private final int[] counts=new int[Stage.values().length], next=new int[counts.length];
 public synchronized void record(Stage stage,long nanos){if(nanos<0)throw new IllegalArgumentException("negative duration");int i=stage.ordinal();values[i][next[i]]=nanos;next[i]=(next[i]+1)%LIMIT;counts[i]=Math.min(LIMIT,counts[i]+1);}
 public synchronized String report(){StringBuilder s=new StringBuilder("LocalPilot stage-metrics-v1\nRAM only; last 128 successful samples per stage.\nIndependent windows; not end-to-end latency or gameplay success.\nScheduled animation waits, failures and Stop are not included.\n");for(Stage stage:Stage.values()){int i=stage.ordinal(),n=counts[i];s.append(stage).append(" n=").append(n);if(n>0){long[] a=Arrays.copyOf(values[i],n);Arrays.sort(a);s.append(" p50_ms=").append(ms(a[(n-1)/2])).append(" p95_ms=").append(ms(a[(int)Math.ceil(n*.95)-1])).append(" max_ms=").append(ms(a[n-1]));}s.append('\n');}return s.toString();}
 private static String ms(long ns){return String.format(Locale.ROOT,"%.3f",ns/1000000.0);}
}
