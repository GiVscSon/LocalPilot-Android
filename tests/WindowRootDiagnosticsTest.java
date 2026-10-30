package org.localpilot.android;
public final class WindowRootDiagnosticsTest {
 private static int checks;
 private static void check(boolean ok){checks++;if(!ok)throw new AssertionError("window root diagnostics #"+checks);}
 public static void main(String[] args){
  WindowRootDiagnostics d=new WindowRootDiagnostics();check(d.latest()==WindowRootDiagnostics.Outcome.NOT_OBSERVED);
  d.record(0,0,0,0,false);check(d.latest()==WindowRootDiagnostics.Outcome.NO_APPLICATION);
  d.record(2,0,0,0,false);check(d.latest()==WindowRootDiagnostics.Outcome.NO_FOCUSED_APPLICATION);
  d.record(2,1,1,0,false);check(d.latest()==WindowRootDiagnostics.Outcome.ROOT_UNAVAILABLE);
  d.record(2,1,0,1,false);check(d.latest()==WindowRootDiagnostics.Outcome.ROOT_ID_MISMATCH);
  d.record(3,2,1,0,true);check(d.latest()==WindowRootDiagnostics.Outcome.AVAILABLE);
  check(d.report().contains("last_failure=ROOT_ID_MISMATCH"));check(d.report().contains("observations=5 failures=4"));
  check(d.report().contains("failed_app_windows=2 failed_focused_app_windows=1 failed_null_roots=0 failed_mismatched_roots=1"));
  try{d.record(1,1,0,0,false);throw new AssertionError("invalid observation accepted");}catch(IllegalArgumentException expected){checks++;}
  d.reset();check(d.latest()==WindowRootDiagnostics.Outcome.NOT_OBSERVED);check(d.report().contains("observations=0 failures=0"));
  System.out.println("Window root diagnostics: "+checks+" assertions PASS");
 }
}
