package org.localpilot.android;
public class MetricsTest {
 public static void main(String[] args){StageMetrics m=new StageMetrics();check(m.report().contains("CAPTURE n=0"));for(int i=1;i<=200;i++)m.record(StageMetrics.Stage.CAPTURE,i*1000000L);String r=m.report();check(r.contains("CAPTURE n=128 p50_ms=136.000 p95_ms=194.000 max_ms=200.000"));check(r.contains("POLICY n=0"));m.record(StageMetrics.Stage.POLICY,1000);check(m.report().contains("POLICY n=1 p50_ms=0.001"));boolean rejected=false;try{m.record(StageMetrics.Stage.READ,-1);}catch(IllegalArgumentException e){rejected=true;}check(rejected);check(m.report().contains("READ n=0"));System.out.println("PASS 6 metrics assertions");}
 static void check(boolean ok){if(!ok)throw new AssertionError();}
}
