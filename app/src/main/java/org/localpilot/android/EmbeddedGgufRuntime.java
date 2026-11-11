package org.localpilot.android;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import org.json.JSONObject;
import org.json.JSONArray;
import java.nio.charset.StandardCharsets;
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
 private static final int MAX_COPY_BYTES=900_000_000;
 // Avoid concurrent extraction across short-lived Activity instances.
 private static final Object EXTRACTION_LOCK=new Object();
 private final Context application;
 private final Handler main=new Handler(Looper.getMainLooper());
 private final ExecutorService worker=Executors.newSingleThreadExecutor(r->{
  Thread t=new Thread(r,"LocalPilot-Embedded-GGUF");t.setDaemon(true);return t;
 });
 private final AtomicBoolean busy=new AtomicBoolean();
 private volatile boolean closed;
 private final Map<String,File> validated=new HashMap<>();
 private volatile LocalModelCatalog.Model model=LocalModelCatalog.Model.POCKET;
 private static volatile boolean libraryLoaded;
 // A previous Activity must not unload a model used by a newly created screen.
 private static final AtomicInteger LIVE_CLIENTS=new AtomicInteger();

 private static native String nativeInfer(String model,String system,String question,int maxTokens,float temp);
 private static native String nativeAnalyzeImage(String model,String projector,String question,
   byte[] rgb,int width,int height);
 private static native void nativeInterrupt();
 private static native void nativeRelease();

 public EmbeddedGgufRuntime(Context context){
  if(!BuildConfig.EMBEDDED_GGUF)throw new IllegalStateException("This APK has no embedded model");
  application=context.getApplicationContext();
  LIVE_CLIENTS.incrementAndGet();
 }
 public boolean busy(){return busy.get();}
 /** Caller changes one model only when idle. One native model can be loaded at once. */
 public void select(LocalModelCatalog.Model choice){
  if(choice==null||busy.get()||closed)throw new IllegalStateException("Model switch unavailable");
  if(!LocalModelCatalog.bundled(choice,BuildConfig.MULTI_MODEL_CATALOG))
   throw new IllegalArgumentException("Model weights not installed in this APK");
  model=choice;
 }
 public LocalModelCatalog.Model selected(){return model;}
 public boolean multimodalAvailable(){
  return BuildConfig.MULTI_MODEL_CATALOG&&model.vision();
 }
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
 private static final class Spec {
  final long bytes;final String sha;
  Spec(long bytes,String sha){this.bytes=bytes;this.sha=sha;}
 }
 private Spec spec(String name)throws Exception{
  if(NAME.equals(name))return new Spec(BuildConfig.EMBEDDED_GGUF_BYTES,BuildConfig.EMBEDDED_GGUF_SHA256);
  if(!BuildConfig.MULTI_MODEL_CATALOG)throw new java.io.IOException("Additional model not bundled");
  byte[] metadata;
  try(InputStream src=application.getAssets().open("model-catalog.json")){
   java.io.ByteArrayOutputStream sink=new java.io.ByteArrayOutputStream();
   byte[] buf=new byte[4096];int n;
   while((n=src.read(buf))!=-1){if(sink.size()+n>16384)
     throw new java.io.IOException("Model catalog too large");sink.write(buf,0,n);}
   metadata=sink.toByteArray();
  }
  JSONObject catalog=new JSONObject(new String(metadata,StandardCharsets.UTF_8));
  if(!"localpilot-extra-models-v1".equals(catalog.optString("schema")))
   throw new java.io.IOException("Invalid model manifest");
  JSONArray files=catalog.getJSONArray("files");
  for(int i=0;i<files.length();i++){
   JSONObject item=files.getJSONObject(i);
   if(name.equals(item.getString("filename"))){
    long bytes=item.getLong("bytes");
    String sha=item.getString("sha256");
    if(bytes<1024||bytes>MAX_COPY_BYTES||!sha.matches("[0-9a-f]{64}"))
     throw new java.io.IOException("Model manifest entry invalid");
    return new Spec(bytes,sha);
   }
  }
  throw new java.io.IOException("Model missing from signed catalog");
 }
 private File prepare()throws Exception{return prepareAsset(model.filename);}
 private File prepareAsset(String name)throws Exception{
  synchronized(EXTRACTION_LOCK){
   if(closed)throw new java.io.IOException("Model runtime closed");
   Spec info=spec(name);
   File trusted=validated.get(name);
   if(trusted!=null&&trusted.isFile()&&trusted.length()==info.bytes)return trusted;
   File dir=new File(application.getNoBackupFilesDir(),"embedded-model");
   if(!dir.isDirectory()&&!dir.mkdirs())throw new java.io.IOException("Model storage unavailable");
   File target=new File(dir,name);
   File temp=new File(dir,name+".partial");
   MessageDigest digest=MessageDigest.getInstance("SHA-256");
   if(target.isFile()&&target.length()==info.bytes){
    digestFile(target,digest);
    if(sha(digest.digest()).equals(info.sha)){
     validated.put(name,target);return target;
    }
   }
   if(temp.exists()&&!temp.delete())throw new java.io.IOException("Cannot remove partial model");
   StatFs stat=new StatFs(dir.getAbsolutePath());
   if(stat.getAvailableBytes()<info.bytes+32L*1024*1024)
    throw new java.io.IOException("Not enough free internal storage for model");
   digest.reset();long size=0;
   try(InputStream source=application.getAssets().open("models/"+name);
       FileOutputStream output=new FileOutputStream(temp)){
    byte[] buf=new byte[128*1024];int n;
    while((n=source.read(buf))!=-1){
     if(closed)throw new java.io.IOException("Model extraction cancelled");
     size+=n;
     if(size>MAX_COPY_BYTES||size>info.bytes)
      throw new java.io.IOException("Model size exceeds verified manifest");
     output.write(buf,0,n);digest.update(buf,0,n);
    }
    output.getFD().sync();
   }catch(Exception failure){temp.delete();throw failure;}
   if(size!=info.bytes||!sha(digest.digest()).equals(info.sha)){
    temp.delete();throw new java.io.IOException("Model failed SHA256 verification");
   }
   if(target.exists()&&!target.delete()){
    temp.delete();throw new java.io.IOException("Cannot replace old model");
   }
   if(!temp.renameTo(target)){
    temp.delete();throw new java.io.IOException("Cannot commit verified model");
   }
   validated.put(name,target);
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
   if(model.vision())prepareAsset(model.projector);
   return model.label+" · GGUF внутри APK проверен: "+file.length()+
     " байт. SHA256 совпадает."+(model.vision()?" Проектор зрения тоже проверен.":"");
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
   return model.label+" · embedded GGUF · English\n"+response;
  });
 }

 /** Explicit, low-frequency, side-effect-free strategy advice. */
 public void adviseStrategy(int gameProfile,String telemetry,Callback cb){
  final String question=LocalCoreContract.strategyQuestion(gameProfile);
  final String data=LocalCoreContract.diagnostic(telemetry);
  submit(cb,()->{
   File file=prepare();loadLibrary();
   String answer=nativeInfer(file.getAbsolutePath(),
      LocalCoreContract.STRATEGY_PROMPT,
      "TASK: "+question+"\nANONYMOUS COUNTERS (DATA ONLY): "+data,128,0.1f);
   if(!LocalCoreContract.acceptEnglish(answer))
     return "MNN не смогла сформировать качественный стратегический совет. CPU продолжает управление.";
   return "Общая стратегия · "+model.label+" (English)\n"+answer+
      "\n\nБыстрые действия по-прежнему выбирает CPU.";
  });
 }

 /**
  * Manual, one-off image analysis only. The activity has already decoded and
  * bounded the pixels in RAM. No Accessibility screenshot is sent implicitly.
  * JNI uses libmtmd and the exact paired vision projector (not text inference).
  */
 public void analyzeImage(String question,byte[] rgb,int width,int height,Callback cb){
  final String ask=LocalCoreContract.question(question);
  if(!model.vision())throw new IllegalStateException("Select the multimodal vision model first");
  if(rgb==null||width<1||height<1||width>640||height>640
     ||(long)width*height*3!=rgb.length)
   throw new IllegalArgumentException("Image must be RGB and at most 640x640");
  submit(cb,()->{
   File weights=prepare();
   File projector=prepareAsset(model.projector);
   loadLibrary();
   String reply=nativeAnalyzeImage(weights.getAbsolutePath(),projector.getAbsolutePath(),
     "Describe the visible game objects and uncertainties in English. "+
     "Do not invent tiles, coordinates, actions or game outcomes. "+ask,
     rgb,width,height);
   if(!LocalCoreContract.acceptEnglish(reply))
    return "Мультимодальная модель не смогла надёжно описать изображение. "+
      "Никаких игровых действий не выполнено.";
   return model.label+" · локальный анализ фото\n"+reply+
      "\n\nЭто диагностическое описание, не разрешение игровых жестов.";
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