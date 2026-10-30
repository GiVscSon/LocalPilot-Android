package org.localpilot.android;

public final class WindowRejectionDiagnosticsTest {
 private static int checks;
 private static void check(boolean value){checks++;if(!value)throw new AssertionError("window rejection #"+checks);}
 public static void main(String[] args){
  WindowRejectionDiagnostics d=new WindowRejectionDiagnostics();
  check(d.report().contains("reason=NONE gate_epoch=-1 uptime_ms=-1 elapsed_ms=-1"));
  d.record("TARGET_BOUNDS_CHANGED",17,1234,1734,0,0,1220,2712,true,0,40,1220,2712);
  String first=d.report();
  check(first.contains("reason=TARGET_BOUNDS_CHANGED gate_epoch=17 uptime_ms=1234 elapsed_ms=1734"));
  check(first.contains("expected_bounds=[0,0][1220,2712] observed_bounds=[0,40][1220,2712]"));
  d.record(null,17,1235,1735,0,0,0,0,false,0,0,0,0);check(first.equals(d.report()));
  d.record("secret.pkg",17,1236,1736,0,0,0,0,false,0,0,0,0);check(first.equals(d.report()));
  d.record("TARGET_APP_CHANGED",18,2234,2734,0,0,1220,2712,false,100,100,300,300);
  check(d.report().contains("reason=TARGET_APP_CHANGED gate_epoch=18 uptime_ms=2234 elapsed_ms=2734"));
  check(d.report().endsWith("observed_bounds=unavailable"));
  check(!d.report().contains("100,100"));
  String second=d.report();d.record("TARGET_BOUNDS_CHANGED",-1,1,1,0,0,1,1,true,1,1,2,2);check(second.equals(d.report()));
  d.record("TARGET_BOUNDS_CHANGED",1,-1,1,0,0,1,1,true,1,1,2,2);check(second.equals(d.report()));
  d.record("TARGET_BOUNDS_CHANGED",1,1,-1,0,0,1,1,true,1,1,2,2);check(second.equals(d.report()));
  d.reset();check(d.report().contains("reason=NONE gate_epoch=-1 uptime_ms=-1 elapsed_ms=-1"));
  check(d.report().endsWith("expected_bounds=none observed_bounds=unavailable"));
  ActionGate gate=new ActionGate();gate.arm("game",4,1220,2712);long activeEpoch=gate.epoch();
  check("TARGET_BOUNDS_CHANGED".equals(gate.identityRejection("game",4,1220,2600)));
  d.record("TARGET_BOUNDS_CHANGED",activeEpoch,3000,4000,0,0,1220,2712,true,0,0,1220,2600);
  gate.stop();check(!gate.armed()&&gate.epoch()!=activeEpoch);
  check(d.report().contains("gate_epoch="+activeEpoch+" uptime_ms=3000 elapsed_ms=4000"));
  System.out.println("Window rejection diagnostics: "+checks+" assertions PASS");
 }
}
