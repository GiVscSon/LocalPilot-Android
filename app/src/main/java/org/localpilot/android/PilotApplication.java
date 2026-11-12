package org.localpilot.android;
public final class PilotApplication extends android.app.Application {
 @Override public void onCreate(){
  super.onCreate();CrashDiagnostics.install(this);
  if(BuildConfig.UNIFIED_MODELLAB){
   org.localpilot.shared.HarnessTelemetry.install(HarnessRunJournal.get(this));
   ModelLabActivity.restoreConsoleBrain(this);
  }
 }
}
