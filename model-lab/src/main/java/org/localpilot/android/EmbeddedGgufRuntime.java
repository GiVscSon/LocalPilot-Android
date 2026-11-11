package org.localpilot.android;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Separate ModelLab-only JNI entrypoint. Same native ABI as the reviewed
 * llama.cpp bridge; no PilotService, AccessibilityService or game permissions.
 * Never call inference on the Android UI thread.
 */
public final class EmbeddedGgufRuntime {
 public interface Result { void complete(String answer,long elapsedMs); }
 private static native String nativeInfer(String model,String system,String question,int tokens,float temperature);
 private static native String nativeAnalyzeImage(String model,String projector,String question,
   byte[] rgb,int width,int height);
 private static native String nativeGpuDescription();
 private static native boolean nativeConfigureGpuLayers(int layers);
 private static native int nativeLoadedGpuLayers();
 private static native void nativeInterrupt();
 private static native void nativeRelease();
 private static volatile boolean loaded;
 private static synchronized void load(){
  if(!loaded){System.loadLibrary("localpilot_embedded");loaded=true;}
 }
 private final ExecutorService executor=Executors.newSingleThreadExecutor(task->{
  Thread thread=new Thread(()->{
   android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
   task.run();
  },"ModelLab-Inference");thread.setDaemon(true);return thread;
 });
 private final Handler ui=new Handler(Looper.getMainLooper());
 private final AtomicBoolean busy=new AtomicBoolean();
 private volatile int lastGpuLayers;
 public boolean busy(){return busy.get();}
 public int lastGpuLayers(){return lastGpuLayers;}
 /** Read-only GPU discovery, only on explicit user click. No offload yet. */
 public void probeGpu(Result callback){
  submit(callback,()->{
   if(!BuildConfig.VULKAN_COMPILED)
    return "Vulkan не собран в этом APK. CPU остаётся доступен.";
   return "Обнаружено через ggml: "+nativeGpuDescription()+
     ". Поддержка драйвера ещё не означает ускорение.";
  });
 }
 private int chooseLayers(int requested){
  if(requested!=0&&requested!=8&&requested!=16)
   throw new IllegalArgumentException("GPU layers must be 0, 8, or 16");
  int attempt=BuildConfig.VULKAN_COMPILED?requested:0;
  if(!nativeConfigureGpuLayers(attempt)){
   nativeConfigureGpuLayers(0);
   return 0;
  }
  return attempt;
 }
 public void text(File model,String prompt,Result callback){
  text(model,prompt,0,callback);
 }
 /** GPU offload is opt-in; 0 is the CPU default. */
 public void text(File model,String prompt,int requestedGpuLayers,Result callback){
  if(model==null||!model.isFile())throw new IllegalArgumentException("Model GGUF missing");
  if(prompt==null||prompt.isBlank()||prompt.length()>1000)
   throw new IllegalArgumentException("Prompt must be 1-1000 characters");
  submit(callback,()->{
   int requested=chooseLayers(requestedGpuLayers);
   String answer=nativeInfer(model.getAbsolutePath(),
    "You are an offline on-device model being evaluated. Answer briefly and in English. "+
    "If the question is uncertain, say so; do not provide executable commands.",
    prompt,160,0.2f);
   lastGpuLayers=nativeLoadedGpuLayers();
   return answer==null?"":answer+
    "\n\n[Вычисления: "+(lastGpuLayers>0?"GPU "+lastGpuLayers+" слоёв":
      requestedGpuLayers>0?"CPU, GPU не сработал":"CPU")+"]";
  });
 }
 public void image(File model,File projector,String prompt,byte[] rgb,int width,int height,
                   Result callback){
  image(model,projector,prompt,rgb,width,height,0,callback);
 }
 public void image(File model,File projector,String prompt,byte[] rgb,int width,int height,
                   int requestedGpuLayers,Result callback){
  if(model==null||projector==null||!model.isFile()||!projector.isFile())
   throw new IllegalArgumentException("A GGUF model and matching mmproj GGUF are required");
  if(prompt==null||prompt.isBlank()||prompt.length()>1000||
    rgb==null||width<1||height<1||width>640||height>640||
    rgb.length!=(long)width*height*3)
   throw new IllegalArgumentException("Invalid RGB screenshot/prompt");
  // Snapshot user data: a new picked image cannot modify an inflight inference.
  final byte[] copy=rgb.clone();
  submit(callback,()->{
   chooseLayers(requestedGpuLayers);
   String answer=nativeAnalyzeImage(model.getAbsolutePath(),
     projector.getAbsolutePath(),
     "Describe only what is visible in the attached image. Mention uncertainty. "+prompt,
     copy,width,height);
   lastGpuLayers=nativeLoadedGpuLayers();
   return answer==null?"":answer+
     "\n\n[Вычисления: "+(lastGpuLayers>0?"GPU "+lastGpuLayers+" слоёв":
       requestedGpuLayers>0?"CPU, GPU не сработал":"CPU")+"]";
  });
 }
 private interface Work { String run()throws Exception; }
 private void submit(Result callback,Work work){
  if(callback==null||!busy.compareAndSet(false,true))
   throw new IllegalStateException("The local model is already busy");
  try{
   executor.execute(()->{
    long start=SystemClock.elapsedRealtime();
    String result;
    try{
     load();
     result=work.run();
     if(result==null||result.isBlank())result="Нет ответа. Модель или формат не поддерживаются этим движком.";
     else result=ModelLabAnswer.visible(result);
    }catch(Exception|LinkageError error){
     result="Ошибка локальной модели: "+error.getClass().getSimpleName()+
       (error.getMessage()==null?"":": "+error.getMessage());
    }finally{busy.set(false);}
    final String done=result;
    long elapsed=SystemClock.elapsedRealtime()-start;
    ui.post(()->callback.complete(done,elapsed));
   });
  }catch(java.util.concurrent.RejectedExecutionException failure){
   busy.set(false);throw new IllegalStateException("Inference worker has been closed",failure);
  }
 }
 public void close(){
  if(loaded)nativeInterrupt();
  executor.execute(()->{if(loaded)nativeRelease();});
  executor.shutdown();
 }
}
