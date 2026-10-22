package org.localpilot.android;
public final class PilotApplication extends android.app.Application {
 @Override public void onCreate(){super.onCreate();CrashDiagnostics.install(this);}
}
