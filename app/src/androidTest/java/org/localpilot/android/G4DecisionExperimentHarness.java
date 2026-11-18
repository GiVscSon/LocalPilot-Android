package org.localpilot.android;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.webkit.WebView;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONArray;
import org.json.JSONObject;
import org.localpilot.gamelab.NesEmulatorActivity;
import org.localpilot.shared.*;

/** Isolated actual-Android experiment driver. All game actions use the existing
 * Activity -> model host -> coordinator -> JSNES executor. No scripted buttons. */
final class G4DecisionExperimentHarness {
 private final Instrumentation test;private final Bundle args;
 private NesEmulatorActivity activity;private HarnessModelRuntime runtime;private Path root,vault,fixtures;
 private NesModelHost.ModelIdentity model;
 G4DecisionExperimentHarness(Instrumentation test,Bundle args){this.test=test;this.args=args;}
 private <T>T main(Callable<T> work)throws Exception{
  AtomicReference<T> value=new AtomicReference<>();AtomicReference<Throwable> error=new AtomicReference<>();
  test.runOnMainSync(()->{try{value.set(work.call());}catch(Throwable e){error.set(e);}});
  if(error.get()!=null)throw new Exception("Main-thread experiment failure",error.get());return value.get();
 }
 private Object field(String name)throws Exception{Field f=NesEmulatorActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(activity);}
 private void set(String name,Object value)throws Exception{Field f=NesEmulatorActivity.class.getDeclaredField(name);f.setAccessible(true);f.set(activity,value);}
 private Object invoke(String name,Class<?>[] types,Object...values)throws Exception{
  Method m=NesEmulatorActivity.class.getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(activity,values);
 }
 private void call(String name)throws Exception{main(()->{invoke(name,new Class<?>[0]);return null;});}
 private void await(Callable<Boolean> predicate,long ms,String reason)throws Exception{
  long end=SystemClock.elapsedRealtime()+ms;
  while(SystemClock.elapsedRealtime()<end){if(predicate.call())return;Thread.sleep(30);}throw new IllegalStateException(reason);
 }
 private String js(String code)throws Exception{
  CountDownLatch done=new CountDownLatch(1);AtomicReference<String> value=new AtomicReference<>();
  main(()->{((WebView)field("web")).evaluateJavascript(code,s->{value.set(s);done.countDown();});return null;});
  if(!done.await(10,TimeUnit.SECONDS))throw new IllegalStateException("JS query timeout");return value.get();
 }
 private static String sha(byte[] data)throws Exception{
  StringBuilder result=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(data))result.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return result.toString();
 }
 private static String readText(Path path)throws Exception{return new String(Files.readAllBytes(path),java.nio.charset.StandardCharsets.UTF_8);}
 private static void writeText(Path path,String text)throws Exception{Files.write(path,text.getBytes(java.nio.charset.StandardCharsets.UTF_8));}
 private static void require(boolean ok,String reason){if(!ok)throw new IllegalStateException(reason);}
 private void stop()throws Exception{
  main(()->{invoke("stopCpuExperiment",new Class<?>[]{String.class},"STOP: bounded G4 diagnostic case");return null;});
  await(()->!runtime.engine().busy()&&runtime.state().phase().equals("IDLE")&&!HarnessCoordinator.nes().actionInFlight(),125000,"Native worker did not stop");
 }
 private void clearJournals()throws Exception{
  for(String file:new String[]{"console-ai-user-rom-v1.log","console-ai-user-rom-v1.previous.log",
     "harness-model-execution-v1.log","harness-model-execution-v1.previous.log"})Files.deleteIfExists(root.resolve(file));
 }
 private VoidLancerGameState game()throws Exception{
  // Profile authorization is read-only, ROM-bound and cannot mint an input permit.
  boolean prior=main(()->(Boolean)field("genericVisionEpisode"));
  main(()->{set("genericVisionEpisode",true);return null;});
  try{
   Object result=new org.json.JSONTokener(js("window.readActualGameState()" )).nextValue();
   require(result instanceof String,"Missing verified game RAM");return VoidLancerGameState.parse((String)result);
  }finally{main(()->{set("genericVisionEpisode",prior);return null;});}
 }
 private Path slot(String name){return fixtures.resolve(name);}
 private Path savedFile(String name){return slot(name).resolve("console/nes/latest.nss");}
 private JSONObject metadata(String name)throws Exception{return new JSONObject(readText(slot(name).resolve("metadata.json")));}
 private void capture(String name)throws Exception{
  call("freezeAndSave");
  await(()->main(()->!(Boolean)field("progressBusy")&&(Boolean)field("aiFrozen")),10000,"Snapshot not saved");
  var save=NesSaveStore.load(vault);require(save!=null,"Missing actual save");
  var state=game();NesSaveStore.save(slot(name),save);
  String history=ConsoleExperienceMemory.genericContext(root,VoidLancerGameState.ROM_SHA,model.id(),model.sha256());
  JSONObject meta=new JSONObject().put("schema","g4-real-core-snapshot-v1").put("name",name)
   .put("snapshotSha256",sha(Files.readAllBytes(savedFile(name)))).put("romSha256",sha(save.rom()))
   .put("coreSha256",NesSaveStore.CORE_SHA256).put("frame",save.frame()).put("frameHash",Integer.toUnsignedString(save.frameHash()))
   .put("game",state.wire()).put("history",history).put("historySource",model.id()+"/"+model.sha256());
  writeText(slot(name).resolve("metadata.json"),meta.toString());
 }
 private JSONObject restore(String name)throws Exception{
  stop();clearJournals();
  JSONObject meta=metadata(name);var saved=NesSaveStore.load(slot(name));
  require(saved!=null&&sha(Files.readAllBytes(savedFile(name))).equals(meta.getString("snapshotSha256")),"Snapshot SHA mismatch");
  require(sha(saved.rom()).equals(VoidLancerGameState.ROM_SHA)&&meta.getString("coreSha256").equals(NesSaveStore.CORE_SHA256),"Snapshot ROM/core mismatch");
  main(()->{invoke("launchRom",new Class<?>[]{byte[].class,boolean.class,NesSaveStore.Save.class,boolean.class},saved.rom(),false,saved,false);return null;});
  await(()->main(()->!(Boolean)field("progressBusy")&&(Boolean)field("aiFrozen")&&(Integer)field("frames")==saved.frame()),15000,"Restore not confirmed");
  require(main(()->(Integer)field("hash"))==saved.frameHash(),"Restored pixel hash differs");
  require(game().wire().equals(meta.getString("game")),"Restored RAM differs");return meta;
 }
 private void start(int budget)throws Exception{
  main(()->{
   invoke("requestCompactWorkflow",new Class<?>[]{String.class,NesModelHost.ModelIdentity.class},HarnessDemoWorkflow.ggufMode(16),model);
   require((Boolean)field("genericVisionEpisode")&&(Integer)field("autoGgufRemaining")>0,"Existing controller refused diagnostic start");
   // The diagnostic probe shortens the existing bounded episode, never the input gate.
   set("autoGgufRemaining",budget);return null;
  });
 }
 private void waitCase()throws Exception{
  long deadline=SystemClock.elapsedRealtime()+600000;
  while(SystemClock.elapsedRealtime()<deadline){
   if(main(()->(Integer)field("autoGgufRemaining")==0&&!(Boolean)field("aiRunning")&&!(Boolean)field("directModelInference"))&&!runtime.engine().busy())return;
   Thread.sleep(100);
  }
  stop();throw new IllegalStateException("CASE_TIME_BUDGET");
 }
 private void bootstrap()throws Exception{
  require(model.sha256().equals("57d1997790d1744fba5b40a7317df71ea5e2acee28c47e78f0cce39c0703f8cf"),"Bootstrap requires pinned actual Qwen");
  clearJournals();byte[] rom=Files.readAllBytes(root.resolve("g4-input-rom.nes"));
  require(sha(rom).equals(VoidLancerGameState.ROM_SHA),"Official ROM differs");
  main(()->{invoke("launchRom",new Class<?>[]{byte[].class,boolean.class},rom,false);return null;});
  await(()->main(()->(Integer)field("frames")>=96),15000,"Actual ROM did not run");capture("TITLE");
  JSONObject title=metadata("TITLE");
  // Freeze independent neutral TITLE offsets before any recipe is evaluated.
  // These are real core snapshots, not model wins or scripted game inputs.
  for(int i=0;i<20;i++){
   final int target=128+16*i;
   main(()->{invoke("launchRom",new Class<?>[]{byte[].class,boolean.class},rom,false);return null;});
   await(()->main(()->(Integer)field("frames")>=target),15000,"Holdout title did not advance");capture("HOLDOUT_"+i);
  }
  restore("TITLE");
  require(runtime.configureExperiment(new NesDecisionExperiment.Options(NesDecisionExperiment.Variant.A,42,"bootstrap",title.getString("snapshotSha256"),null,"actual Qwen bootstrap")),"Bootstrap config refused");
  boolean playing=false,threat=false;
  for(int i=0;i<64;i++){
   start(1);waitCase();var rows=ConsoleExperienceMemory.readGeneric(root);
   require(rows.size()==i+1,"Bootstrap made no new verified model transition");
   var last=rows.get(rows.size()-1);var state=last.after();
   require(state!=null&&state.state()!=2&&state.state()!=3,"Bootstrap did not retain a playable episode");
   if(state.state()==0)continue;
   if(!playing){capture("PLAYING");playing=true;}
   if(state.threatX()>=0){capture("THREAT");threat=true;break;}
  }
  require(playing&&threat,"BOOTSTRAP_INCOMPLETE");stop();
  Path proof=fixtures.resolve("provenance");Files.createDirectories(proof);
  for(String name:new String[]{"console-ai-user-rom-v1.log","harness-model-execution-v1.log"})
   Files.copy(root.resolve(name),proof.resolve(name),StandardCopyOption.REPLACE_EXISTING);
  for(String name:new String[]{"bootstrap.jsonl","bootstrap.transitions.jsonl"})
   Files.copy(root.resolve("g4-traces/"+name),proof.resolve(name),StandardCopyOption.REPLACE_EXISTING);
  restore("TITLE");
  var cancellation=new NesDecisionExperiment.Options(NesDecisionExperiment.Variant.A,1042,"negative_cancel",title.getString("snapshotSha256"),null,"negative STOP test");
  require(runtime.configureExperiment(cancellation),"Negative config refused");start(1);
  await(()->runtime.engine().busy(),10000,"No actual native inference to cancel");
  require(!runtime.configureExperiment(cancellation),"Configuration changed during native inference");
  stop();require(ConsoleExperienceMemory.readGeneric(root).isEmpty(),"Late completed transition after inference STOP");
  Files.copy(root.resolve("g4-traces/negative_cancel.jsonl"),proof.resolve("negative_cancel.jsonl"),StandardCopyOption.REPLACE_EXISTING);
  writeText(fixtures.resolve("manifest.json"),new JSONObject().put("schema","g4-fixtures-v1")
   .put("producerModelSha",model.sha256())
   .put("fixtureNames",new JSONArray(new String[]{"TITLE","PLAYING","THREAT"})).put("holdoutCount",20)
   .put("negativeStopNoTransition",true).put("busyConfigurationRejected",true).put("scriptedActions",false).toString());
 }
 private JSONObject runCase(String name,NesDecisionExperiment.Variant variant,int seed,boolean episode)throws Exception{
  String id=args.getString("modelProfile")+"_"+variant+"_"+name+"_"+seed+"_"+(episode?"episode":"probe");
  JSONObject report=new JSONObject().put("caseId",id).put("variant",variant.name()).put("baseSeed",seed)
   .put("snapshot",name).put("episode",episode).put("result","CASE_ERROR");
  try{
   JSONObject meta=restore(name);report.put("snapshotSha256",meta.getString("snapshotSha256")).put("gameAtRestore",meta.getString("game"));
   String history=episode?null:meta.getString("history");
   require(runtime.configureExperiment(new NesDecisionExperiment.Options(variant,seed,id,meta.getString("snapshotSha256"),history,episode?"current model episode":meta.getString("historySource"))),"Case config refused");
   start(episode?16:1);waitCase();
   require(Files.isRegularFile(root.resolve("g4-traces/"+id+".jsonl")),"No actual model invocation receipt");
   var rows=ConsoleExperienceMemory.readGeneric(root);report.put("verifiedTransitions",rows.size());
   JSONArray transitions=new JSONArray();
   for(var row:rows){
    require(row.romSha().equals(VoidLancerGameState.ROM_SHA)&&row.modelSha().equals(model.sha256()),"Mixed model/ROM journal");
    transitions.put(new JSONObject().put("epoch",row.epoch()).put("action",row.trial().button)
     .put("beforeFrame",row.trial().frameBefore).put("afterFrame",row.trial().frameAfter)
     .put("beforeHash",Integer.toUnsignedString(row.trial().hashBefore)).put("afterHash",Integer.toUnsignedString(row.trial().hashAfter))
     .put("beforeGame",row.before().wire()).put("afterGame",row.after().wire()));
   }
   report.put("transitions",transitions).put("result","OBSERVED");
   byte[] before=Files.exists(root.resolve("console-ai-user-rom-v1.log"))?Files.readAllBytes(root.resolve("console-ai-user-rom-v1.log")):new byte[0];
   stop();Thread.sleep(1000);
   byte[] after=Files.exists(root.resolve("console-ai-user-rom-v1.log"))?Files.readAllBytes(root.resolve("console-ai-user-rom-v1.log")):new byte[0];
   require(java.util.Arrays.equals(before,after),"Completed transition after STOP");report.put("stopNoLateTransition",true);
  }catch(Exception e){report.put("error",e.toString());stop();}
  Path out=root.resolve("g4-results");Files.createDirectories(out);writeText(out.resolve(id+".json"),report.toString());
  return report;
 }
 void run(){
  Bundle result=new Bundle();JSONObject summary=new JSONObject();boolean completed=false;
  try{
   require(BuildConfig.UNIFIED_MODELLAB&&BuildConfig.HARNESS_TEST_FIXTURES,"Isolated unified test APK required");
   require(android.os.Build.SUPPORTED_ABIS[0].equals("x86_64")&&android.os.Build.VERSION.SDK_INT==35,"API35 x86_64 required");
   root=test.getTargetContext().getFilesDir().toPath();vault=root.resolve("localpilot_vault");fixtures=root.resolve("g4-fixtures");
   runtime=HarnessModelRuntime.from(test.getTargetContext());model=runtime.boundIdentity();require(model!=null,"No genuinely imported bound model");
   activity=(NesEmulatorActivity)test.startActivitySync(new Intent(test.getTargetContext(),NesEmulatorActivity.class)
    .putExtra(NesModelHost.EXTRA_REAL_GAMES_ONLY,true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
   await(()->main(()->(Boolean)field("pageLoaded")&&(Boolean)field("resumed")),15000,"NES Activity not ready");
   if("g4-bootstrap".equals(args.getString("mode"))){bootstrap();summary.put("result","REAL_MODEL_FIXTURES_CAPTURED");}
   else if(args.containsKey("holdoutIndex")){
    int pair=Integer.parseInt(args.getString("holdoutIndex"));require(pair>=0&&pair<20,"Holdout index");
    var candidate=NesDecisionExperiment.Variant.valueOf(args.getString("variant"));JSONArray cases=new JSONArray();
    cases.put(runCase("HOLDOUT_"+pair,NesDecisionExperiment.Variant.A,10042+1000*pair,true));
    if(candidate!=NesDecisionExperiment.Variant.A)cases.put(runCase("HOLDOUT_"+pair,candidate,10042+1000*pair,true));
    summary.put("result","DIAGNOSTICS_COMPLETED").put("cases",cases);
   }else{
    var variant=NesDecisionExperiment.Variant.valueOf(args.getString("variant"));JSONArray cases=new JSONArray();
    for(int seed:new int[]{42,1042,2042}){
     for(String name:new String[]{"TITLE","PLAYING","THREAT"})cases.put(runCase(name,variant,seed,false));
     cases.put(runCase("TITLE",variant,seed,true));
    }
    summary.put("result","DIAGNOSTICS_COMPLETED").put("cases",cases);
   }
   completed=true;
  }catch(Throwable e){try{summary.put("result","FAIL").put("error",e.toString());}catch(Exception ignored){}}
  try{summary.put("modelSha256",model==null?JSONObject.NULL:model.sha256()).put("physicalDevice",false).put("weightsTrained",false).put("releaseReady",false);
   if(root!=null){Files.createDirectories(root.resolve("g4-results"));writeText(root.resolve("g4-results/summary.json"),summary.toString());}
  }catch(Exception ignored){}
  result.putString("evidence_json",summary.toString());test.finish(completed?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
 }
}
