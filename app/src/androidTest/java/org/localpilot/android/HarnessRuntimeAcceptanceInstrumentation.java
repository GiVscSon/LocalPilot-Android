package org.localpilot.android;

import android.app.Activity;
import android.app.Application;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONObject;
import org.localpilot.shared.HarnessCoordinator;
import org.localpilot.shared.NesModelHost;

/** Real Android lifecycle + NEGATIVE inference admission. No GGUF model or
 * genuine NES observation is invented. Never reports INFERENCE/DEVICE_PASS. */
public final class HarnessRuntimeAcceptanceInstrumentation extends Instrumentation {
 private volatile Activity latest;
 private boolean preparePinnedModel;
 private volatile CountDownLatch resumed=new CountDownLatch(1);
 @Override public void onCreate(Bundle args){super.onCreate(args);preparePinnedModel=args!=null&&"import-pinned-pocket".equals(args.getString("mode"));start();}
 @Override public void onStart(){
  if(preparePinnedModel){prepareModel();return;}
  Bundle result=new Bundle();JSONObject evidence=new JSONObject();boolean pass=false;
  Application app=(Application)getTargetContext().getApplicationContext();
  Application.ActivityLifecycleCallbacks lifecycle=new Application.ActivityLifecycleCallbacks(){
   public void onActivityResumed(Activity a){if(a instanceof ModelLabActivity){latest=a;resumed.countDown();}}
   public void onActivityCreated(Activity a,Bundle b){} public void onActivityStarted(Activity a){}
   public void onActivityPaused(Activity a){} public void onActivityStopped(Activity a){}
   public void onActivitySaveInstanceState(Activity a,Bundle b){} public void onActivityDestroyed(Activity a){}
  };
  try{
   require(BuildConfig.UNIFIED_MODELLAB,"Unified APK required");
   require(app instanceof HarnessModelOwner&&app instanceof NesModelHost,"Application does not own shared model port");
   app.registerActivityLifecycleCallbacks(lifecycle);
   HarnessModelRuntime shared=((HarnessModelOwner)app).harnessModels();
   require(shared.store().list().isEmpty(),"Fresh isolated catalog required; refusing existing model data");
   getTargetContext().startActivity(new Intent(getTargetContext(),ModelLabActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
   require(resumed.await(15,TimeUnit.SECONDS),"ModelLab did not resume");
   Field engine=ModelLabActivity.class.getDeclaredField("engine");engine.setAccessible(true);
   Field store=ModelLabActivity.class.getDeclaredField("store");store.setAccessible(true);
   require(engine.get(latest)==shared.engine()&&store.get(latest)==shared.store(),"ModelLab uses a different engine/catalog");
   Activity old=latest;resumed=new CountDownLatch(1);runOnMainSync(old::recreate);
   require(resumed.await(15,TimeUnit.SECONDS)&&latest!=old,"ModelLab recreation did not produce a new Activity");
   require(engine.get(latest)==shared.engine()&&store.get(latest)==shared.store(),"Recreated screen replaced application runtime");
   require(ModelLabActivity.harnessStore(getTargetContext())==shared.store(),"Console catalog differs from ModelLab catalog");
   HarnessCoordinator h=HarnessCoordinator.nes();h.reset("7001",HarnessCoordinator.Mode.STEP_MODE);
   var ticket=h.begin("7001",15,123,SystemClock.elapsedRealtime());
   var observation=new NesModelHost.Observation(ticket,"a".repeat(64),
     new NesModelHost.ModelIdentity("fixture:missing","b".repeat(64)),128,112,null);
   CountDownLatch answered=new CountDownLatch(1);AtomicReference<NesModelHost.Reply> reply=new AtomicReference<>();
   runOnMainSync(()->((NesModelHost)app).inferNes(observation,r->{reply.set(r);answered.countDown();}));
   require(answered.await(10,TimeUnit.SECONDS),"Missing-model request did not complete");
   require(reply.get()!=null&&reply.get().status()==NesModelHost.Status.NO_MODEL,"Missing model became a valid proposal");
   require(h.awaiting(ticket)&&!h.actionInFlight(),"Model port consumed input authority");
   require(shared.state().phase().equals("IDLE")&&!shared.engine().busy(),"Unavailable inference left runtime busy");
   h.stop("FIXTURE_COMPLETE");
   h.reset("7002",HarnessCoordinator.Mode.STEP_MODE);
   var pending=h.begin("7002",15,123,SystemClock.elapsedRealtime());
   var next=new NesModelHost.Observation(pending,"a".repeat(64),
     new NesModelHost.ModelIdentity("fixture:missing","b".repeat(64)),128,112,null);
   CountDownLatch cancelled=new CountDownLatch(2);
   AtomicReference<NesModelHost.Reply> first=new AtomicReference<>(),duplicate=new AtomicReference<>();
   runOnMainSync(()->{
    NesModelHost host=(NesModelHost)app;
    host.inferNes(next,r->{first.set(r);cancelled.countDown();});
    host.inferNes(next,r->{duplicate.set(r);cancelled.countDown();});
    require(!host.cancelNesInference("wrong-token"),"Wrong request cancelled another owner");
    h.stop("FIXTURE_USER_STOP");
    require(host.cancelNesInference(pending.nonce),"Pending request did not acknowledge cancellation");
   });
   require(cancelled.await(10,TimeUnit.SECONDS),"Cancelled/duplicate request did not complete");
   require(first.get().status()==NesModelHost.Status.CANCELLED&&duplicate.get().status()==NesModelHost.Status.BUSY,
     "Duplicate inference replaced owner or STOP admitted a response");
   require(!h.hasPending()&&!h.actionInFlight(),"STOP restored input authority");
   evidence.put("schema","localpilot-application-runtime-contract-v1").put("result","ANDROID_CONTRACT_PASS")
    .put("sameEngineAcrossActivityRecreation",true).put("sameCatalogAcrossScreens",true)
    .put("missingModelFailsClosed",true).put("inputAuthorityConsumed",false)
    .put("duplicateRequestRejected",true).put("stopRevokesPendingReply",true)
    .put("syntheticObservationMetadata",true).put("nativeInferencePerformed",false)
    .put("physicalDevice",false).put("releaseReady",false);
   pass=true;
  }catch(Throwable error){
   try{evidence.put("result","FAIL").put("error",error.toString()).put("releaseReady",false);}catch(Exception ignored){}
  }finally{
   app.unregisterActivityLifecycleCallbacks(lifecycle);HarnessCoordinator.nes().stop("INSTRUMENTATION_DONE");
   Activity a=latest;if(a!=null)runOnMainSync(a::finish);
   result.putString("evidence_json",evidence.toString());finish(pass?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
  }
 }
 private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
 private void prepareModel(){
  Bundle result=new Bundle();JSONObject evidence=new JSONObject();boolean pass=false;
  try{
   Application app=(Application)getTargetContext().getApplicationContext();
   require(BuildConfig.UNIFIED_MODELLAB&&app instanceof HarnessModelOwner,"Unified Application required");
   HarnessModelRuntime runtime=((HarnessModelOwner)app).harnessModels();
   require(runtime.store().list().isEmpty(),"Refusing to modify an existing model catalog");
   ModelLabPresetCatalog.Preset preset=ModelLabPresetCatalog.ALL.stream().filter(p->p.id.equals("pocket")).findFirst().orElseThrow();
   ModelLabStore.Entry imported=runtime.store().downloadPreset(preset,null);
   require(imported.modelSha.equals(preset.sha256)&&imported.modelBytes==preset.bytes,"Public fixture model identity mismatch");
   runtime.store().modelFile(imported);HarnessModelBinding.assign(app,imported);
   evidence.put("result","MODEL_IMPORT_PASS_NOT_INFERENCE").put("modelSha256",imported.modelSha)
    .put("modelId",imported.id).put("modelBytes",imported.modelBytes).put("nativeInferencePerformed",false)
    .put("source","PUBLIC_PINNED_GGUF_CI_FIXTURE").put("physicalDevice",false).put("releaseReady",false);
   pass=true;
  }catch(Throwable error){try{evidence.put("result","FAIL").put("error",error.toString()).put("releaseReady",false);}catch(Exception ignored){}}
  result.putString("evidence_json",evidence.toString());finish(pass?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
 }
}
