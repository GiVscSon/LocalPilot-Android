package org.localpilot.android;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import org.localpilot.shared.ConsoleAgentContract;
import org.localpilot.shared.ConsoleExperienceMemory;
import org.localpilot.shared.HarnessCoordinator;
import org.localpilot.shared.HomebrewObs64;
import org.localpilot.shared.NesFrameTransfer;
import org.localpilot.shared.NesModelHost;
import org.localpilot.shared.NesAgentTools;
import org.localpilot.shared.NesDecisionExperiment;

/** Application composition: reuses the existing registry and JNI class.
 * No Activity, emulator, trainer or action executor is retained here.
 */
public final class HarnessModelRuntime {
 private final Context application;
 private final ModelLabStore store;
 private EmbeddedGgufRuntime engine;
 private final Handler main=new Handler(Looper.getMainLooper());
 private final ExecutorService files=Executors.newSingleThreadExecutor(r->{
  Thread t=new Thread(r,"Harness-Model-Validation");t.setDaemon(true);return t;
 });
 private Call active;
 private boolean libraryBusy;
 private NesDecisionExperiment.Options experiment;
 private int experimentIndex;
 private Call experimentProposal;
 private NesModelHost.State state=new NesModelHost.State("IDLE","","","",0,0);
 private static final class Call {
  final NesModelHost.Observation observation;final Consumer<NesModelHost.Reply> callback;
  volatile boolean cancelled,nativeStarted;ModelLabStore.Entry model;
  final NesDecisionExperiment.Options experiment;final int seed;
  String prompt,answer;Long prefillMs,decodeMs;
  Call(NesModelHost.Observation o,Consumer<NesModelHost.Reply> c,NesDecisionExperiment.Options options,int index){
   observation=o;callback=c;experiment=options;seed=options==null?-1:options.seed(index);
  }
 }
 public HarnessModelRuntime(Context context){
  application=context.getApplicationContext();store=new ModelLabStore(application);
 }
 public static HarnessModelRuntime from(Context context){
  Context app=context.getApplicationContext();
  if(!(app instanceof HarnessModelOwner owner))throw new IllegalStateException("Application model runtime unavailable");
  return owner.harnessModels();
 }
 public ModelLabStore store(){return store;}
 /** Test instrumentation only; production applications cannot enable this path. */
 public synchronized boolean configureExperiment(NesDecisionExperiment.Options options){
  if(!(application instanceof NesModelHost host)||!host.nesTestFixturesAllowed())return false;
  var s=HarnessCoordinator.nes().snapshot();
  if(active!=null||libraryBusy||s.waiting()||s.actionInFlight()||!s.owner().equals("NONE")||engine!=null&&engine.busy())return false;
  experiment=options;experimentIndex=0;experimentProposal=null;return true;
 }
 public synchronized boolean recordExperimentTransition(NesModelHost.ExperimentTransition t){
  if(experiment==null)return true;
  Call c=experimentProposal;
  if(c==null||t==null||!t.epoch().equals(c.observation.ticket().epoch)||
    !java.util.Objects.equals(t.observationNonce(),c.observation.ticket().nonce)||
    !ConsoleAgentContract.tokenValid(t.actionNonce())||!t.romSha().equals(c.observation.romSha256())||
    !t.modelId().equals(c.model.id)||!t.modelSha().equals(c.model.modelSha)||
    t.beforeFrame()!=c.observation.ticket().frame||t.beforeHash()!=c.observation.ticket().hash||
    t.afterFrame()-t.beforeFrame()!=16||!java.util.Objects.equals(t.beforeGame(),c.observation.game().wire())||
    !c.observation.game().phaseAllows(t.action()))return false;
  try{
   var call=NesAgentTools.parse(c.answer,NesAgentTools.scope(true,c.observation.game()));
   if(call==null||!call.button().equals(t.action()))return false;
   var row=new org.json.JSONObject().put("epoch",t.epoch()).put("observationNonce",t.observationNonce()).put("actionNonce",t.actionNonce())
    .put("romSha256",t.romSha()).put("modelSha256",t.modelSha()).put("modelId",t.modelId()).put("action",t.action())
    .put("beforeFrame",t.beforeFrame()).put("afterFrame",t.afterFrame()).put("beforeHash",Integer.toUnsignedString(t.beforeHash()))
    .put("afterHash",Integer.toUnsignedString(t.afterHash())).put("beforeGame",t.beforeGame()).put("afterGame",t.afterGame());
   if(!appendExperiment(c.experiment.caseId()+".transitions.jsonl",row))return false;
   experimentProposal=null;return true;
  }catch(Exception invalid){return false;}
 }
 public synchronized boolean libraryBusy(){return libraryBusy;}
 public synchronized boolean select(NesModelHost.ModelIdentity identity){
  var snapshot=HarnessCoordinator.nes().snapshot();
  if(identity==null||libraryBusy||active!=null||!snapshot.owner().equals("NONE")||snapshot.waiting()||snapshot.actionInFlight()||engine!=null&&engine.busy())return false;
  var entry=store.find(identity.id());
  if(entry==null||!entry.modelSha.equals(identity.sha256()))return false;
  HarnessModelBinding.assign(application,entry);return true;
 }
 public synchronized void manageLibrary(NesModelHost.LibraryRequest request,Consumer<NesModelHost.LibraryReply> completed){
  var snapshot=HarnessCoordinator.nes().snapshot();
  if(libraryBusy||active!=null||!snapshot.owner().equals("NONE")||snapshot.waiting()||snapshot.actionInFlight()||engine!=null&&engine.busy()){
   main.post(()->completed.accept(new NesModelHost.LibraryReply(false,null,"Сначала останови текущий сеанс или дождись завершения операции")));return;
  }
  libraryBusy=true;
  files.execute(()->{
   NesModelHost.LibraryReply reply;
   try{
    if(request==null||request.operation()==null)throw new IllegalArgumentException("Не выбрана операция");
    ModelLabStore.Entry target=null;
    if(request.target()!=null){
     target=store.find(request.target().id());
     if(target==null||!target.modelSha.equals(request.target().sha256()))throw new IllegalStateException("Модель изменилась или удалена");
    }
    ModelLabStore.Entry result=null;String message;
    switch(request.operation()){
     case IMPORT_MODEL,IMPORT_PROJECTOR -> {
      boolean projector=request.operation()==NesModelHost.LibraryOperation.IMPORT_PROJECTOR;
      if(projector&&(target==null||target.bundled))throw new IllegalArgumentException("Для mmproj выбери импортированный GGUF");
      var uri=android.net.Uri.parse(request.uri());String label="Imported GGUF";
      if(!"content".equals(uri.getScheme()))throw new SecurityException("Только Android Content Picker");
      try(var cursor=application.getContentResolver().query(uri,new String[]{android.provider.OpenableColumns.DISPLAY_NAME},null,null,null)){
       if(cursor!=null&&cursor.moveToFirst())label=cursor.getString(0);
      }catch(RuntimeException ignored){}
      result=store.importFile(uri,projector?target.label:label,request.sha256(),projector?target.id:null);
      boolean pinned=request.sha256()!=null&&!request.sha256().isBlank();
      message=(pinned?"HASH_VERIFIED":"SHA_COMPUTED · источник не проверен")+" · SHA-256 "+(projector?result.projectorSha:result.modelSha)+" · инференс ещё не проверен";
     }
     case DOWNLOAD_PRESET -> {
      ModelLabPresetCatalog.Preset preset=null;
      for(var candidate:ModelLabPresetCatalog.ALL)if(candidate.id.equals(request.presetId()))preset=candidate;
      if(preset==null)throw new IllegalArgumentException("Модель отсутствует в закреплённом каталоге");
      result=store.downloadPreset(preset,null);
      message="HASH_VERIFIED · модель и mmproj (если нужен) установлены · инференс ещё не проверен";
     }
     case VERIFY -> {
      if(target==null)throw new IllegalArgumentException("Выбери GGUF");
      store.modelFile(target);if(target.vision())store.projectorFile(target);result=target;
      message="HASH_VERIFIED · SHA-256 "+target.modelSha+" · это проверка файлов, инференс ещё не проверен";
     }
     case DELETE -> {
      if(target==null||target.bundled)throw new IllegalArgumentException("Встроенную модель нельзя удалить");
      var bound=boundIdentity();
      if(bound!=null&&bound.equals(request.target()))throw new IllegalStateException("Сначала отвяжи модель от NES");
      store.remove(target);message="Импортированная модель удалена";
     }
     case UNBIND -> {HarnessModelBinding.clear(application);message="Привязка GGUF снята; ввод не запущен";}
     default -> throw new IllegalArgumentException("Операция недоступна");
    }
    reply=new NesModelHost.LibraryReply(true,result==null?null:new NesModelHost.ModelIdentity(result.id,result.modelSha),message);
   }catch(Exception failure){
    String reason=failure instanceof SecurityException?"не прошла проверка SHA-256, размера или источника":
      failure instanceof IOException?"не удалось прочитать или сохранить файл":
      failure instanceof IllegalArgumentException?"неверный файл GGUF, контрольная сумма или целевая модель":
      "модель недоступна, привязана к NES или недостаточно места";
    reply=new NesModelHost.LibraryReply(false,null,"Операция отклонена: "+reason);
   }
   final var outcome=reply;
   main.post(()->{synchronized(this){libraryBusy=false;}completed.accept(outcome);});
  });
 }
 public NesModelHost.ModelIdentity boundIdentity(){
  try{
   ModelLabStore.Entry selected=HarnessModelBinding.resolve(application,store.list());
   return selected==null?null:new NesModelHost.ModelIdentity(selected.id,selected.modelSha);
  }catch(RuntimeException unavailable){return null;}
 }
 public synchronized EmbeddedGgufRuntime engine(){
  if(engine==null)engine=new EmbeddedGgufRuntime(application);return engine;
 }
 public synchronized int lastGpuLayers(){return engine==null?0:engine.lastGpuLayers();}
 public synchronized NesModelHost.State state(){
  return active==null&&engine!=null&&engine.busy()?
    new NesModelHost.State("NATIVE_BUSY","","","",0,0):state;
 }
 private synchronized void phase(Call call,String phase){
  if(active!=call)return;
  var ticket=call.observation.ticket();var model=call.model;
  state=new NesModelHost.State(phase,model==null?"":model.id,model==null?"":model.modelSha,
    ticket.epoch,ticket.frame,ticket.hash);
 }
 private boolean current(Call call){
  return !call.cancelled&&NesModelHost.current(HarnessCoordinator.nes(),call.observation,SystemClock.elapsedRealtime());
 }
 public synchronized void infer(NesModelHost.Observation observation,Consumer<NesModelHost.Reply> callback){
  if(observation==null||callback==null)throw new IllegalArgumentException("Observation and callback required");
  if(active!=null||libraryBusy){main.post(()->callback.accept(new NesModelHost.Reply(observation,NesModelHost.Status.BUSY,null,null,null,0)));return;}
  if(experiment!=null&&(observation.profile()!=NesModelHost.TaskProfile.GAME_RAM||experimentIndex>=64)){
   main.post(()->callback.accept(new NesModelHost.Reply(observation,NesModelHost.Status.UNAVAILABLE,null,null,null,0)));return;
  }
  Call call=new Call(observation,callback,experiment,experiment==null?0:experimentIndex++);active=call;phase(call,"VERIFYING");
  files.execute(()->prepare(call));
 }
 private void prepare(Call call){
  try{
   if(!current(call)){finish(call,NesModelHost.Status.STALE,null,0);return;}
   ModelLabStore.Entry model=HarnessModelBinding.resolve(application,store.list());
   if(model==null){finish(call,NesModelHost.Status.NO_MODEL,null,0);return;}
   if(!model.id.equals(call.observation.binding().id())||!model.modelSha.equals(call.observation.binding().sha256())){
    finish(call,NesModelHost.Status.STALE,null,0);return;
   }
   call.model=model;phase(call,"VERIFYING");
   boolean generic=call.observation.profile()!=NesModelHost.TaskProfile.HOMEBREW_RAM;
   boolean gameRam=call.observation.profile()==NesModelHost.TaskProfile.GAME_RAM;
   if(generic&&!gameRam&&!model.vision()){finish(call,NesModelHost.Status.UNAVAILABLE,null,0);return;}
   File weights=store.modelFile(model);
   byte[] png=call.observation.png();byte[] pixels=null;
   File projector=null;
   if(model.vision()&&!gameRam){
    if(png==null)throw new IOException("Vision model requires verified NES PNG");
    NesFrameTransfer.verify(png);projector=store.projectorFile(model);
    Bitmap bitmap=BitmapFactory.decodeByteArray(png,0,png.length);
    if(bitmap==null)throw new IOException("NES PNG decode failed");
    try{
     if(bitmap.getWidth()!=NesFrameTransfer.WIDTH||bitmap.getHeight()!=NesFrameTransfer.HEIGHT)
      throw new IOException("NES RGB dimensions changed");
     int[] rgba=new int[NesFrameTransfer.WIDTH*NesFrameTransfer.HEIGHT];
     bitmap.getPixels(rgba,0,NesFrameTransfer.WIDTH,0,0,NesFrameTransfer.WIDTH,NesFrameTransfer.HEIGHT);
     pixels=new byte[rgba.length*3];
     for(int i=0;i<rgba.length;i++){
      pixels[i*3]=(byte)(rgba[i]>>>16);pixels[i*3+1]=(byte)(rgba[i]>>>8);pixels[i*3+2]=(byte)rgba[i];
     }
    }finally{bitmap.recycle();}
   }
   String history=generic?ConsoleExperienceMemory.genericContext(application.getFilesDir().toPath(),call.observation.romSha256(),model.id,model.modelSha):
    ConsoleExperienceMemory.context(application.getFilesDir().toPath(),model.id,model.modelSha);
   var t=call.observation.ticket();
   String observation=(gameRam?"VERIFIED ROM GAME STATE: this request uses RAM text, not image inference. ":projector==null?"TEXT ONLY: no image understanding. ":"Verified NES RGB attached. ")+
    (generic?(call.observation.game()==null?"Play the visible NES game. Infer its controls and objective from the image. Use agent_stop if uncertain. Game RAM, score and victory UNKNOWN. ":
      "Play the actual VOID LANCER shooter. Move to dodge threats and fire A when safe; B spends a limited bomb. The ROM-specific memory below is verified. ")+"ROM="+call.observation.romSha256()+". ":
      "Builtin NES rightward task. ")+"frame="+t.frame+" hash="+Integer.toUnsignedString(t.hash)+
    (generic?" ":call.observation.ramX()<0?" RAM UNKNOWN. ":" verified RAM x="+call.observation.ramX()+" y="+call.observation.ramY()+". ")+
    (generic?"Choose one available NES tool for the visible game. ":"Choose one allowed BUTTON for rightward progress without crossing a RAM edge. ")+
    "Input ACK is not a reward. "+history;
   if(gameRam)observation=call.observation.game().modelPrompt(history);
   if(call.experiment!=null)observation=call.experiment.prompt(call.observation.game(),history);
   final String modelObservation=observation.substring(0,Math.min(900,observation.length()));
   call.prompt=modelObservation;
   final File mmproj=projector;final byte[] rgb=pixels;
   main.post(()->{
    try{
     if(!current(call)||!sameBinding(call)){finish(call,NesModelHost.Status.STALE,null,0);return;}
     EmbeddedGgufRuntime runtime=engine();
     if(runtime.busy()){finish(call,NesModelHost.Status.BUSY,null,0);return;}
     if(application instanceof NesModelHost host&&host.nesTestFixturesAllowed())
      android.util.Log.i("LocalPilotObservation","profile="+call.observation.profile()+" frame="+t.frame+
       " hash="+Integer.toUnsignedString(t.hash)+" gameControls="+t.gameControls+
       " game="+(call.observation.game()==null?"UNKNOWN":call.observation.game().wire()));
     phase(call,gameRam?"GAME_RAM_INFERENCE":"INFERENCING");
     if(call.experiment==null)runtime.proposeNesTools(weights,mmproj,modelObservation,rgb,NesFrameTransfer.WIDTH,NesFrameTransfer.HEIGHT,0,NesAgentTools.scope(generic&&t.gameControls,call.observation.game()),
       (answer,elapsed)->completed(call,answer,elapsed));
     else runtime.proposeNesExperiment(weights,modelObservation,NesAgentTools.scope(true,call.observation.game()),call.experiment.phaseGrammar(),call.seed,
       (answer,elapsed,prefill,decode)->{call.prefillMs=prefill;call.decodeMs=decode;completed(call,answer,elapsed);});
     call.nativeStarted=true;
    }catch(RuntimeException unavailable){finish(call,NesModelHost.Status.UNAVAILABLE,null,0);}
   });
  }catch(Exception invalid){finish(call,NesModelHost.Status.IO_FAILURE,null,0);}
 }
 private boolean sameBinding(Call call){
  try{
   ModelLabStore.Entry bound=HarnessModelBinding.resolve(application,store.list());
   return call.model!=null&&bound!=null&&call.model.id.equals(bound.id)&&
     call.model.modelSha.equals(bound.modelSha)&&java.util.Objects.equals(call.model.projectorSha,bound.projectorSha);
  }catch(RuntimeException unavailable){return false;}
 }
 private void completed(Call call,String answer,long elapsed){
  call.answer=answer;
  files.execute(()->recordCompleted(call,answer,elapsed));
 }
 private void recordCompleted(Call call,String answer,long elapsed){
  if(call.cancelled){trace(call,answer,elapsed,"CANCELLED");finish(call,NesModelHost.Status.CANCELLED,null,elapsed);return;}
  if(!current(call)||!sameBinding(call)){trace(call,answer,elapsed,"STALE");finish(call,NesModelHost.Status.STALE,null,elapsed);return;}
  int toolScope=NesAgentTools.scope(call.observation.profile()!=NesModelHost.TaskProfile.HOMEBREW_RAM&&call.observation.ticket().gameControls,call.observation.game());
  NesAgentTools.Call tool=toolScope>=NesAgentTools.GENERIC?NesAgentTools.parse(answer,toolScope):null;
  String button=toolScope==NesAgentTools.LEGACY?ConsoleAgentContract.parseButton(answer):tool==null?null:tool.button();
  String rejection=button==null&&(NesAgentTools.parse(answer,NesAgentTools.GENERIC)!=null||NesAgentTools.parse(answer,NesAgentTools.MENU)!=null)?"REJECTED_PHASE":"REJECTED_SCHEMA";
  if(!trace(call,answer,elapsed,button==null?rejection:call.observation.game()!=null&&!call.observation.game().phaseAllows(button)?"REJECTED_PHASE":button.equals("NONE")?"MODEL_STOP":"PROPOSAL_NOT_YET_EXECUTED")){
   finish(call,NesModelHost.Status.IO_FAILURE,null,elapsed);return;
  }
  if(tool!=null&&application instanceof NesModelHost host&&host.nesTestFixturesAllowed())
   android.util.Log.i("LocalPilotTools","chosen="+tool.name()+" button="+tool.button()+" frames="+tool.frames()+
     " frame="+call.observation.ticket().frame+" hash="+Integer.toUnsignedString(call.observation.ticket().hash)+" model="+call.model.modelSha);
  if(button!=null&&!"NONE".equals(button)&&call.observation.ramX()>=0){
   int id=HomebrewObs64.id(button);
   if(id<0||(HomebrewObs64.taskMask(call.observation.ramX(),call.observation.ramY(),16)&(1<<id))==0)button=null;
  }
  try{
   HarnessModelExecutionLog.append(application.getFilesDir().toPath(),
    new HarnessModelExecutionLog.Run(call.model.id,call.model.modelSha,
      call.observation.ticket().frame,call.observation.ticket().hash,Math.max(0,elapsed),
      button==null?"REJECTED":button,lastGpuLayers()));
  }catch(IOException|IllegalArgumentException failed){finish(call,NesModelHost.Status.IO_FAILURE,null,elapsed);return;}
  finish(call,button==null?NesModelHost.Status.INVALID_OUTPUT:NesModelHost.Status.PROPOSAL,button,elapsed);
 }
 private boolean trace(Call call,String answer,long elapsed,String reason){
  if(call.experiment==null)return true;
  try{
   var t=call.observation.ticket();var o=call.experiment;
   int scope=NesAgentTools.scope(true,call.observation.game());
   org.json.JSONObject row=new org.json.JSONObject().put("schema","nes-decision-experiment-v1")
    .put("caseId",o.caseId()).put("variant",o.variant().name()).put("seed",call.seed)
    .put("snapshotSha256",o.snapshotSha()).put("historySource",o.historySource())
    .put("romSha256",call.observation.romSha256()).put("modelId",call.model.id).put("modelSha256",call.model.modelSha)
    .put("epoch",t.epoch).put("nonce",t.nonce).put("frame",t.frame).put("frameHash",Integer.toUnsignedString(t.hash))
    .put("gameBefore",call.observation.game().wire()).put("grammarScope",o.phaseGrammar()?scope:5)
    .put("nativeLegalButtons",new org.json.JSONArray(NesAgentTools.legalButtons(o.phaseGrammar()?scope:5)))
    .put("systemPrompt",NesAgentTools.systemPrompt(scope)).put("userPrompt",call.prompt)
    .put("rawReply",answer==null?org.json.JSONObject.NULL:answer).put("elapsedMs",elapsed)
    .put("prefillMs",call.prefillMs==null?org.json.JSONObject.NULL:call.prefillMs)
    .put("decodeMs",call.decodeMs==null?org.json.JSONObject.NULL:call.decodeMs).put("reason",reason);
   return appendExperiment(o.caseId()+".jsonl",row);
  }catch(Exception invalid){return false;}
 }
 private boolean appendExperiment(String name,org.json.JSONObject row)throws java.io.IOException{
  File dir=new File(application.getFilesDir(),"g4-traces");if(!dir.isDirectory()&&!dir.mkdirs())return false;
  File file=new File(dir,name);byte[] data=(row.toString()+"\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);
  if(file.length()+data.length>4*1024*1024)return false;
  try(var out=new java.io.FileOutputStream(file,true)){out.write(data);out.getFD().sync();}return true;
 }
 private void finish(Call call,NesModelHost.Status status,String button,long elapsed){
  main.post(()->{
   synchronized(this){
    if(active!=call)return;
    active=null;state=new NesModelHost.State("IDLE","","","",0,0);
   }
   NesModelHost.Status effective=call.cancelled?NesModelHost.Status.CANCELLED:status;
   if(effective==NesModelHost.Status.PROPOSAL&&(!current(call)||!sameBinding(call)))effective=NesModelHost.Status.STALE;
   if(effective==NesModelHost.Status.PROPOSAL&&call.experiment!=null)experimentProposal=call;
   call.callback.accept(new NesModelHost.Reply(call.observation,effective,
    call.model==null?null:call.model.id,call.model==null?null:call.model.modelSha,
    effective==NesModelHost.Status.PROPOSAL?button:null,Math.max(0,elapsed)));
  });
 }
 public synchronized boolean cancel(String nonce){
  if(active==null||!active.observation.ticket().nonce.equals(nonce))return false;
  active.cancelled=true;phase(active,"CANCEL_PENDING");
  if(active.nativeStarted&&engine!=null)engine.requestCancel();
  return true; // Do not clear active/busy until verification/JNI actually completes.
 }
}
