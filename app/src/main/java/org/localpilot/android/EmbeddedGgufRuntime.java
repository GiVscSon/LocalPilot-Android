package org.localpilot.android;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fully in-APK offline GGUF runtime. No Termux, HTTP, API key or file-picker.
 * JNI model is loaded lazily. The only caller is the diagnostics screen;
 * model outputs have NO route into PilotService/gesture dispatch.
 */
public final class EmbeddedGgufRuntime {
 public interface Callback { void complete(String result); }
 private static final String NAME="MNN-Pocket-0.3B-Instruct-Q4_1.gguf";
 private static final String ASSET="models/"+NAME;
 private static final int MAX_COPY_BYTES=450_000_000;
 // Avoid concurrent extraction across short-lived Activity instances.
 private static final Object EXTRACTION_LOCK=new Object();
 private final Context application;
 private final Handler main=new Handler(Looper.getMainLooper());
 private final ExecutorService worker=Executors.newSingleThreadExecutor(r->{
  Thread t=new Thread(r,"LocalPilot-Embedded-GGUF");t.setDaemon(true);return t;
 });
 private final AtomicBoolean busy=new AtomicBoolean();
 private volatile boolean closed;
 private File validated;
 private static volatile boolean libraryLoaded;
 // A previous Activity must not unload a model used by a newly created screen.
 private static final AtomicInteger LIVE_CLIENTS=new AtomicInteger();

 private static native String nativeInfer(String model,String system,String question,int maxTokens,float temp);
 private static native String nativeInferDirection(String model,String system,String question);
 private static native void nativeInterrupt();
 private static native void nativeRelease();

 public EmbeddedGgufRuntime(Context context){
  if(!BuildConfig.EMBEDDED_GGUF)throw new IllegalStateException("This APK has no embedded model");
  application=context.getApplicationContext();
  LIVE_CLIENTS.incrementAndGet();
 }
 public boolean busy(){return busy.get();}
 private static synchronized void loadLibrary(){
  if(!libraryLoaded){
   System.loadLibrary("localpilot_embedded");
   libraryLoaded=true;
  }
 }
 private static String sha(byte[] bytes){
  StringBuilder s=new StringBuilder(bytes.length*2);
  for(byte b:bytes) s.append(Character.forDigit((b>>4)&15,16))
     .append(Character.forDigit(b&15,16));
  return s.toString();
 }
 private static void digestFile(File file,MessageDigest digest)throws Exception {
  try(InputStream input=new java.io.FileInputStream(file)){
   byte[] buffer=new byte[64*1024];int n;
   while((n=input.read(buffer))!=-1)digest.update(buffer,0,n);
  }
 }
 private File prepare()throws Exception{
  synchronized(EXTRACTION_LOCK){
   if(closed)throw new java.io.IOException("Model runtime closed");
  if(validated!=null&&validated.isFile()&&validated.length()==BuildConfig.EMBEDDED_GGUF_BYTES)
   return validated;
  File dir=new File(application.getNoBackupFilesDir(),"embedded-model");
  if(!dir.isDirectory()&&!dir.mkdirs())throw new java.io.IOException("Model storage unavailable");
  File target=new File(dir,NAME);
  File temp=new File(dir,NAME+".partial");
  MessageDigest digest=MessageDigest.getInstance("SHA-256");
  if(target.isFile()&&target.length()==BuildConfig.EMBEDDED_GGUF_BYTES){
   digestFile(target,digest);
   if(sha(digest.digest()).equals(BuildConfig.EMBEDDED_GGUF_SHA256)){
    validated=target;return target;
   }
  }
  // Only our private sandbox is used; never reuse untrusted model files.
  if(temp.exists()&&!temp.delete())throw new java.io.IOException("Cannot remove partial model");
  StatFs stat=new StatFs(dir.getAbsolutePath());
  long free=stat.getAvailableBytes();
  if(free<BuildConfig.EMBEDDED_GGUF_BYTES+32L*1024*1024)
   throw new java.io.IOException("Not enough free internal storage to extract model");
  digest.reset();
  long size=0;
  try(InputStream source=application.getAssets().open(ASSET);
      FileOutputStream output=new FileOutputStream(temp)){
   byte[] buf=new byte[128*1024];int n;
   while((n=source.read(buf))!=-1){
    if(closed)throw new java.io.IOException("Model extraction cancelled");
    size+=n;
    if(size>MAX_COPY_BYTES||size>BuildConfig.EMBEDDED_GGUF_BYTES)
     throw new java.io.IOException("Model size exceeds pinned metadata");
    output.write(buf,0,n);digest.update(buf,0,n);
   }
   output.getFD().sync();
  }catch(Exception failure){
   temp.delete();
   throw failure;
  }
  if(size!=BuildConfig.EMBEDDED_GGUF_BYTES ||
     !sha(digest.digest()).equals(BuildConfig.EMBEDDED_GGUF_SHA256)){
   temp.delete();
   throw new java.io.IOException("Bundled GGUF failed SHA256 verification");
  }
  if(target.exists()&&!target.delete()){
   temp.delete();throw new java.io.IOException("Cannot replace old model");
  }
  if(!temp.renameTo(target)){
   temp.delete();throw new java.io.IOException("Cannot commit verified model");
  }
  validated=target;
  return target;
  }
 }

 private interface Job { String run()throws Exception; }
 private void submit(Callback callback,Job job){
  if(closed||!busy.compareAndSet(false,true))
   throw new IllegalStateException("Embedded model is busy or closed");
  worker.execute(()->{
   String answer;
   try{answer=job.run();}
   catch(Exception|LinkageError failed){
    // User-visible diagnostics must not silently hide the actual failure stage.
    String reason=failed.getMessage();
    if(reason==null||reason.isBlank())reason="unknown reason";
    reason=reason.replaceAll("[\\r\\n]+"," ").trim();
    if(reason.length()>160)reason=reason.substring(0,160)+"…";
    answer="Встроенная модель недоступна ["+failed.getClass().getSimpleName()+
      "]: "+reason+". Проверьте свободное место, ARM64 и целостность APK.";
   }finally{busy.set(false);}
   final String result=answer;
   if(!closed)main.post(()->{if(!closed)callback.complete(result);});
  });
 }

 public void health(Callback cb){
  submit(cb,()->{
   File file=prepare();
   return "Веса внутри APK проверены: "+file.length()+
     " байт, SHA256 совпадает. Проверить генерацию: BLUE / 27.";
  });
 }
 public void selfTest(Callback cb){
  submit(cb,()->{
   File file=prepare();loadLibrary();
   String system="Answer in English only. Follow the exact output format. No explanations.";
   String a=nativeInfer(file.getAbsolutePath(),system,"Reply with exactly one word: BLUE",16,0f);
   if(closed)return "Самопроверка отменена";
   String b=nativeInfer(file.getAbsolutePath(),system,"What is 12 + 15? Reply with the number only.",16,0f);
   if(closed)return "Самопроверка отменена";
   String cat=nativeInfer(file.getAbsolutePath(),system,
       "Write one short English sentence about a cat.",64,0f);
   boolean blueOk="BLUE".equals(a.trim());
   boolean mathOk=LocalCoreContract.acceptArithmetic(b);
   boolean catOk=LocalCoreContract.acceptEnglish(cat)&&
       cat.toLowerCase(java.util.Locale.ROOT).contains("cat");
   if(!blueOk||!mathOk||!catOk)
    return "FAIL: локальный ИИ отвечает некорректно. "+
      "BLUE="+(blueOk?"PASS":"FAIL")+"; 12+15="+(mathOk?"PASS":"FAIL")+
      "; English sentence="+(catOk?"PASS":"FAIL")+
      ". Ни одно игровое действие не выполняется.";
   return "PASS: BLUE, правильное равенство и английская фраза. Игровые навыки ещё не подтверждены.";
  });
 }
 public void ask(String question,String diagnostic,Callback cb){
  final String ask=LocalCoreContract.question(question);
  final String data=LocalCoreContract.diagnostic(diagnostic);
  submit(cb,()->{
   File file=prepare();loadLibrary();
   String response=nativeInfer(file.getAbsolutePath(),LocalCoreContract.SYSTEM_PROMPT,
       "QUESTION: "+ask+"\nANONYMOUS COUNTERS: "+data,128,0.2f);
   if(!LocalCoreContract.acceptEnglish(response))
    return "Отклонён некорректный ответ встроенной модели. Игровые действия не выполняются.";
   return "MNN-Pocket · embedded GGUF · English\n"+response;
  });
 }
 /**
  * Propose a direction for a fully observed 4x4 turn-based board. No gestures,
  * no coordinates, no OS permissions and no side effects on the real game.
  * A different component must independently verify this response on a
  * subsequently captured, stable board before any normal ActionGate dispatch.
  */
 public void propose2048(Board2048 board,int excludedMask,Callback callback){
  final String question=Neural2048Verifier.prompt(board,excludedMask);
  submit(callback,()->{
   File file=prepare();loadLibrary();
   String proposal=nativeInferDirection(file.getAbsolutePath(),
     "You are a 2048 puzzle solver. Reply with one legal direction word only. "+
     "No explanation, punctuation or extra tokens.",question);
   return proposal==null?"":proposal.trim();
  });
 }

 public synchronized void close(){
  if(closed)return;
  closed=true;
  boolean last=LIVE_CLIENTS.decrementAndGet()==0;
  if(last&&libraryLoaded)nativeInterrupt();
  worker.execute(()->{
   // A replacement Activity may already be using the same native engine.
   if(libraryLoaded&&LIVE_CLIENTS.get()==0)nativeRelease();
  });
  worker.shutdown();
 }
}