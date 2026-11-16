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
 @Override public java.util.List<org.localpilot.shared.NesModelHost.ModelChoice> nesModels(){
  if(!BuildConfig.UNIFIED_MODELLAB)return java.util.List.of();
  var choices=new java.util.ArrayList<org.localpilot.shared.NesModelHost.ModelChoice>();
  for(var entry:harnessModels().store().list())choices.add(new org.localpilot.shared.NesModelHost.ModelChoice(
    new org.localpilot.shared.NesModelHost.ModelIdentity(entry.id,entry.modelSha),entry.label));
  return java.util.List.copyOf(choices);
 }
 @Override public boolean selectNesModel(org.localpilot.shared.NesModelHost.ModelIdentity identity){
  if(!BuildConfig.UNIFIED_MODELLAB||identity==null)return false;
  var state=org.localpilot.shared.HarnessCoordinator.nes().snapshot();
  if(state.waiting()||state.actionInFlight()||harnessModels().engine().busy())return false;
  for(var entry:harnessModels().store().list())if(entry.id.equals(identity.id())&&entry.modelSha.equals(identity.sha256())){
   HarnessModelBinding.assign(this,entry);return true;
  }
  return false;
 }
 @Override public void onCreate(){super.onCreate();CrashDiagnostics.install(this);}
}
