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
 private NesModelHost.State state=new NesModelHost.State("IDLE","","","",0,0);
 private static final class Call {
  final NesModelHost.Observation observation;final Consumer<NesModelHost.Reply> callback;
  volatile boolean cancelled,nativeStarted;ModelLabStore.Entry model;
  Call(NesModelHost.Observation o,Consumer<NesModelHost.Reply> c){observation=o;callback=c;}
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
  Call call=new Call(observation,callback);active=call;phase(call,"VERIFYING");
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
   boolean generic=call.observation.profile()==NesModelHost.TaskProfile.VISION_EXPLORATION;
   if(generic&&!model.vision()){finish(call,NesModelHost.Status.UNAVAILABLE,null,0);return;}
   File weights=store.modelFile(model);
   byte[] png=call.observation.png();byte[] pixels=null;
   File projector=null;
   if(model.vision()){
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
   String observation=(projector==null?"TEXT ONLY: no image understanding. ":"Verified NES RGB attached. ")+
    (generic?(call.observation.game()==null?"Play the visible NES game. Infer its controls and objective from the image. Choose NONE if uncertain. Game RAM, score and victory UNKNOWN. ":
      "Play the actual VOID LANCER shooter. Move to dodge threats and fire A when safe; B spends a limited bomb. The ROM-specific memory below is verified. ")+"ROM="+call.observation.romSha256()+". ":
      "Builtin NES rightward task. ")+"frame="+t.frame+" hash="+Integer.toUnsignedString(t.hash)+
    (generic?" ":call.observation.ramX()<0?" RAM UNKNOWN. ":" verified RAM x="+call.observation.ramX()+" y="+call.observation.ramY()+". ")+
    (generic?"Choose one allowed BUTTON for visible game progress. ":"Choose one allowed BUTTON for rightward progress without crossing a RAM edge. ")+
    "Input ACK is not a reward. "+history;
   if(call.observation.game()!=null)observation=call.observation.game().prompt()+observation;
   final String modelObservation=observation.substring(0,Math.min(900,observation.length()));
   final File mmproj=projector;final byte[] rgb=pixels;
   main.post(()->{
    try{
     if(!current(call)||!sameBinding(call)){finish(call,NesModelHost.Status.STALE,null,0);return;}
     EmbeddedGgufRuntime runtime=engine();
     if(runtime.busy()){finish(call,NesModelHost.Status.BUSY,null,0);return;}
     phase(call,"INFERENCING");
     runtime.proposeNesButton(weights,mmproj,modelObservation,rgb,NesFrameTransfer.WIDTH,NesFrameTransfer.HEIGHT,0,
       (answer,elapsed)->completed(call,answer,elapsed));
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
  files.execute(()->recordCompleted(call,answer,elapsed));
 }
 private void recordCompleted(Call call,String answer,long elapsed){
  if(call.cancelled){finish(call,NesModelHost.Status.CANCELLED,null,elapsed);return;}
  if(!current(call)||!sameBinding(call)){finish(call,NesModelHost.Status.STALE,null,elapsed);return;}
  String button=ConsoleAgentContract.parseButton(answer);
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
 private void finish(Call call,NesModelHost.Status status,String button,long elapsed){
  main.post(()->{
   synchronized(this){
    if(active!=call)return;
    active=null;state=new NesModelHost.State("IDLE","","","",0,0);
   }
   NesModelHost.Status effective=call.cancelled?NesModelHost.Status.CANCELLED:status;
   if(effective==NesModelHost.Status.PROPOSAL&&(!current(call)||!sameBinding(call)))effective=NesModelHost.Status.STALE;
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
