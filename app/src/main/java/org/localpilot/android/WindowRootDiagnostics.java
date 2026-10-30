package org.localpilot.android;

/** Bounded window lookup metadata; never stores packages, IDs, titles or pixels. */
public final class WindowRootDiagnostics {
 public enum Outcome { NOT_OBSERVED, AVAILABLE, NO_APPLICATION, NO_FOCUSED_APPLICATION, ROOT_UNAVAILABLE, ROOT_ID_MISMATCH }
 private Outcome latest=Outcome.NOT_OBSERVED,lastFailure=Outcome.NOT_OBSERVED;
 private int applications,focused,missing,mismatched;
 private int failedApplications,failedFocused,failedMissing,failedMismatched;
 private long observations,failures;
 public void reset(){latest=lastFailure=Outcome.NOT_OBSERVED;applications=focused=missing=mismatched=0;failedApplications=failedFocused=failedMissing=failedMismatched=0;observations=failures=0;}
 /** Values describe only windows actually examined, which may stop at the first usable root. */
 public void record(int applications,int focused,int missing,int mismatched,boolean matched){
  if(applications<0||focused<0||missing<0||mismatched<0||focused>applications||missing+mismatched+(matched?1:0)!=focused)throw new IllegalArgumentException("invalid window observation");
  this.applications=applications;this.focused=focused;this.missing=missing;this.mismatched=mismatched;
  latest=matched?Outcome.AVAILABLE:applications==0?Outcome.NO_APPLICATION:focused==0?Outcome.NO_FOCUSED_APPLICATION:mismatched>0?Outcome.ROOT_ID_MISMATCH:Outcome.ROOT_UNAVAILABLE;
  if(observations<Long.MAX_VALUE)observations++;if(!matched){lastFailure=latest;failedApplications=applications;failedFocused=focused;failedMissing=missing;failedMismatched=mismatched;if(failures<Long.MAX_VALUE)failures++;}
 }
 public Outcome latest(){return latest;}
 public String report(){return "WINDOW_ROOT latest="+latest+" app_windows="+applications+" focused_app_windows="+focused+" null_roots="+missing+" mismatched_roots="+mismatched+" last_failure="+lastFailure+" failed_app_windows="+failedApplications+" failed_focused_app_windows="+failedFocused+" failed_null_roots="+failedMissing+" failed_mismatched_roots="+failedMismatched+" observations="+observations+" failures="+failures;}
}
