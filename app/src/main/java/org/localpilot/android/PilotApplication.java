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
  return harnessModels().select(identity);
 }
 @Override public boolean nesLibraryAvailable(){return BuildConfig.UNIFIED_MODELLAB;}
 @Override public boolean nesTestFixturesAllowed(){return BuildConfig.HARNESS_TEST_FIXTURES;}
 @Override public boolean nesVisionModel(org.localpilot.shared.NesModelHost.ModelIdentity identity){
  if(!BuildConfig.UNIFIED_MODELLAB||identity==null)return false;
  var entry=harnessModels().store().find(identity.id());
  return entry!=null&&entry.vision()&&entry.modelSha.equals(identity.sha256());
 }
 @Override public boolean supportsNesGame(org.localpilot.shared.NesModelHost.ModelIdentity identity,String romSha){
  if(!BuildConfig.UNIFIED_MODELLAB||identity==null)return false;
  var entry=harnessModels().store().find(identity.id());
  return entry!=null&&entry.modelSha.equals(identity.sha256())&&
    (entry.vision()||org.localpilot.shared.VoidLancerGameState.ROM_SHA.equals(romSha));
 }
 @Override public boolean nesLibraryBusy(){return models!=null&&models.libraryBusy();}
 @Override public boolean nesModelRemovable(org.localpilot.shared.NesModelHost.ModelIdentity identity){
  if(!BuildConfig.UNIFIED_MODELLAB||identity==null)return false;
  var entry=harnessModels().store().find(identity.id());
  return entry!=null&&!entry.bundled&&entry.modelSha.equals(identity.sha256());
 }
 @Override public java.util.List<org.localpilot.shared.NesModelHost.DownloadChoice> nesDownloads(){
  if(!BuildConfig.UNIFIED_MODELLAB)return java.util.List.of();
  var choices=new java.util.ArrayList<org.localpilot.shared.NesModelHost.DownloadChoice>();
  for(var preset:ModelLabPresetCatalog.ALL)choices.add(new org.localpilot.shared.NesModelHost.DownloadChoice(preset.id,preset.description()));
  return java.util.List.copyOf(choices);
 }
 @Override public void manageNesLibrary(org.localpilot.shared.NesModelHost.LibraryRequest request,
   java.util.function.Consumer<org.localpilot.shared.NesModelHost.LibraryReply> completed){
  if(!BuildConfig.UNIFIED_MODELLAB){org.localpilot.shared.NesModelHost.super.manageNesLibrary(request,completed);return;}
  harnessModels().manageLibrary(request,completed);
 }
 @Override public void onCreate(){super.onCreate();CrashDiagnostics.install(this);}
}
