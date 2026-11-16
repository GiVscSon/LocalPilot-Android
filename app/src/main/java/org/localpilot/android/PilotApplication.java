package org.localpilot.android;
public final class PilotApplication extends android.app.Application implements HarnessModelOwner,org.localpilot.shared.NesModelHost {
 private volatile HarnessModelRuntime models;
 @Override public synchronized HarnessModelRuntime harnessModels(){
  if(models==null)models=new HarnessModelRuntime(this);return models;
 }
 @Override public void inferNes(org.localpilot.shared.NesModelHost.Observation observation,
   java.util.function.Consumer<org.localpilot.shared.NesModelHost.Reply> callback){
  if(!BuildConfig.UNIFIED_MODELLAB){
   callback.accept(new org.localpilot.shared.NesModelHost.Reply(observation,
     org.localpilot.shared.NesModelHost.Status.UNAVAILABLE,null,null,null,0));return;
  }
  harnessModels().infer(observation,callback);
 }
 @Override public boolean cancelNesInference(String nonce){return models!=null&&models.cancel(nonce);}
 @Override public org.localpilot.shared.NesModelHost.State nesModelState(){return harnessModels().state();}
 @Override public org.localpilot.shared.NesModelHost.ModelIdentity nesBoundModel(){return harnessModels().boundIdentity();}
 @Override public void onCreate(){super.onCreate();CrashDiagnostics.install(this);}
}
