package org.localpilot.android;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebSettings;
import org.json.JSONArray;
import org.json.JSONObject;
import org.localpilot.shared.ConsoleAgentBridge;
import org.localpilot.shared.HarnessTelemetry;
import java.io.File;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Embedded offline Harness Console. One native app + one existing ModelLab.
 * No network daemon, no external control socket and no Android gesture API.
 * The WebView is a local view. All actions are bounded native commands.
 */
public final class HarnessDashboardActivity extends Activity {
 private static final int IMPORT_GGUF=908, EXPORT_EVENTS=909;
 private static final String HOME="file:///android_asset/harness/dashboard.html";
 private final ExecutorService worker=Executors.newSingleThreadExecutor(job->{
  Thread t=new Thread(job,"LocalPilot-Harness-Console");t.setDaemon(true);return t;
 });
 private final AtomicBoolean busy=new AtomicBoolean();
 private final Map<String,String> probes=new HashMap<>();
 private volatile String operation="Готово", error="", gpuProbe="Не проверен";
 private WebView web;
 private HarnessRunJournal journal;

 @Override public void onCreate(Bundle state){
  super.onCreate(state);
  if(!BuildConfig.UNIFIED_MODELLAB){
   finish();return;
  }
  journal=HarnessRunJournal.get(this);
  web=new WebView(this);
  web.setBackgroundColor(0xff0b1220);
  WebSettings settings=web.getSettings();
  settings.setJavaScriptEnabled(true);
  settings.setDomStorageEnabled(false);
  settings.setBlockNetworkLoads(true);
  settings.setAllowContentAccess(false);
  settings.setAllowFileAccess(true);
  settings.setAllowFileAccessFromFileURLs(false);
  settings.setAllowUniversalAccessFromFileURLs(false);
  settings.setJavaScriptCanOpenWindowsAutomatically(false);
  settings.setSupportMultipleWindows(false);
  web.setWebViewClient(new WebViewClient(){
   @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest req){
    return !HOME.equals(req.getUrl().toString());
   }
   @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest req){
    if(HOME.equals(req.getUrl().toString()))
     return super.shouldInterceptRequest(view,req);
    return new WebResourceResponse("text/plain","UTF-8",
      new java.io.ByteArrayInputStream(new byte[0]));
   }
  });
  web.addJavascriptInterface(new Bridge(),"Harness");
  setContentView(web);
  getWindow().setDecorFitsSystemWindows(false);
  web.setOnApplyWindowInsetsListener((view,insets)->{
   android.graphics.Insets bars=insets.getInsets(android.view.WindowInsets.Type.systemBars()
     |android.view.WindowInsets.Type.displayCutout());
   view.setPadding(bars.left,bars.top,bars.right,bars.bottom);
   return insets;
  });
  web.requestApplyInsets();
  web.loadUrl(HOME);
 }
 private void async(String label,Job job){
  if(!busy.compareAndSet(false,true)){
   error="Занято: "+operation;return;
  }
  operation=label;error="";
  worker.execute(()->{
   try{operation=job.run();}
   catch(Exception|LinkageError ex){
    error=ex.getClass().getSimpleName()+": "+String.valueOf(ex.getMessage());
    operation="Операция остановлена";
   }finally{busy.set(false);}
  });
 }
 private interface Job {String run()throws Exception;}
 private String displayName(Uri uri){
  try(Cursor c=getContentResolver().query(uri,
     new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){
   if(c!=null&&c.moveToFirst())return c.getString(0);
  }catch(Exception ignored){}
  return "Imported GGUF";
 }
 private final class Bridge {
  @JavascriptInterface public String snapshot(){
   JSONObject root=new JSONObject();
   try{
    root.put("connected",true);
    root.put("onePackage",getPackageName());
    root.put("device",new JSONObject()
      .put("manufacturer",Build.MANUFACTURER).put("model",Build.MODEL)
      .put("sdk",Build.VERSION.SDK_INT).put("abi",String.join(", ",Build.SUPPORTED_ABIS))
      .put("cores",Runtime.getRuntime().availableProcessors())
      .put("maxHeapMb",Runtime.getRuntime().maxMemory()/1024/1024));
    root.put("gpu",new JSONObject()
      .put("vulkanCompiled",BuildConfig.VULKAN_COMPILED)
      .put("probe",gpuProbe)
      .put("actuallyLoadedLayers",ModelLabActivity.harnessEngine(
        HarnessDashboardActivity.this).lastGpuLayers())
      .put("physicalBenchmarkVerified",false));
    root.put("operation",operation);
    root.put("busy",busy.get());
    root.put("error",error);
    root.put("activeEpisode",HarnessTelemetry.episode());
    root.put("audit",journal.summary());
    JSONArray events=journal.recent();
    root.put("events",events);
    root.put("imitation",HarnessImitationPolicy.status(HarnessDashboardActivity.this));
    Set<String> used=new HashSet<>(),visual=new HashSet<>(),goal=new HashSet<>();
    int imitationExamples=0;
    for(int i=0;i<events.length();i++){
     JSONObject event=events.optJSONObject(i);
     if(event==null)continue;
     if("EXAMPLE".equals(event.optString("stage"))&&
        "VISUAL_ONLY".equals(event.optString("status")))imitationExamples++;
     if("MODEL".equals(event.optString("stage"))&&
        "MODEL_USED".equals(event.optString("status")))used.add(event.optString("modelId"));
     if("VISUAL".equals(event.optString("stage"))&&
        "PIXELS_DIFFER".equals(event.optString("status")))visual.add(event.optString("episodeId"));
     if("GOAL".equals(event.optString("stage"))&&
        "VERIFIED".equals(event.optString("status")))goal.add(event.optString("episodeId"));
    }
    String assigned=getSharedPreferences("localpilot-console-assignment",0)
       .getString("modelId","");
    JSONObject tested=new JSONObject();
    synchronized(probes){for(Map.Entry<String,String> p:probes.entrySet())tested.put(p.getKey(),p.getValue());}
    JSONArray models=new JSONArray();
    for(ModelLabStore.Entry item:ModelLabActivity.harnessStore(
           HarnessDashboardActivity.this).list()){
     models.put(new JSONObject().put("id",item.id).put("label",item.label)
      .put("bytes",item.modelBytes).put("sha256",item.modelSha)
      .put("vision",item.vision()).put("bundled",item.bundled)
      .put("projector",item.vision()?item.projectorName:"")
      .put("bound",item.id.equals(assigned)&&ConsoleAgentBridge.get()!=null)
      .put("used",used.contains(item.id)).put("test",tested.optString(item.id,"NOT_RUN")));
    }
    root.put("models",models);
    JSONArray presets=new JSONArray();
    for(int i=0;i<ModelLabPresetCatalog.ALL.size();i++){
     ModelLabPresetCatalog.Preset p=ModelLabPresetCatalog.ALL.get(i);
     presets.put(new JSONObject().put("index",i).put("label",p.label)
       .put("bytes",p.totalBytes()).put("vision",p.vision()));
    }
    root.put("presets",presets);
    root.put("assignedModelId",assigned);
    root.put("learning",new JSONObject().put("imitationExamples",imitationExamples)
      .put("eligibleTransitions",0)
      .put("trainedPolicies",0).put("rewardProvider","UNKNOWN")
      .put("reason","Нет проверенного источника награды для NES. Изменение пикселей не является наградой.")
      .put("successConfirmedEpisodes",goal.size()).put("visuallyChangedEpisodes",visual.size()));
    JSONArray cores=new JSONArray();
    cores.put(new JSONObject().put("id","nes").put("name","NES / Денди")
       .put("status","READY").put("realCore",true).put("agentHomebrewOnly",true));
    cores.put(new JSONObject().put("id","snes").put("name","SNES")
       .put("status","NOT_INTEGRATED").put("realCore",false));
    cores.put(new JSONObject().put("id","gb").put("name","Game Boy")
       .put("status","NOT_INTEGRATED").put("realCore",false));
    root.put("cores",cores);
   }catch(Exception ex){
    try{root.put("error","Snapshot: "+ex.getClass().getSimpleName());}
    catch(Exception ignored){}
   }
   return root.toString();
  }
  @JavascriptInterface public void command(String action,String argument){
   if(action==null)return;
   String arg=argument==null?"":argument;
   switch(action){
    case "openModelLab":
     runOnUiThread(()->startActivity(new Intent(HarnessDashboardActivity.this,
       ModelLabActivity.class)));break;
    case "openNES":
     if(HarnessTelemetry.active()) {error="STOP текущего эпизода перед запуском";break;}
     runOnUiThread(()->startActivity(new Intent(HarnessDashboardActivity.this,
       org.localpilot.gamelab.NesEmulatorActivity.class)));break;
    case "importModel":
     runOnUiThread(()->{
      Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT);
      pick.addCategory(Intent.CATEGORY_OPENABLE);pick.setType("*/*");
      startActivityForResult(pick,IMPORT_GGUF);
     });break;
    case "download":
     async("Загрузка проверенной модели",()->{
      int index=Integer.parseInt(arg);
      if(index<0||index>=ModelLabPresetCatalog.ALL.size())
       throw new IllegalArgumentException("Invalid preset");
      ModelLabPresetCatalog.Preset preset=ModelLabPresetCatalog.ALL.get(index);
      ModelLabStore.Entry item=ModelLabActivity.harnessStore(HarnessDashboardActivity.this)
        .downloadPreset(preset,(phase,done,total)->operation=phase+" "+
            (total>0?(100*done/total)+"%":done+" bytes"));
      return "Загружено и SHA проверен: "+item.label+" (не назначена автоматически)";
     });break;
    case "bind":
     async("Проверка GGUF перед назначением",()->ModelLabActivity.harnessBind(
       HarnessDashboardActivity.this,arg,0));break;
    case "unbind":
     async("Отключение модели",()->{
      ModelLabActivity.harnessUnbind(HarnessDashboardActivity.this);
      return "Модель отключена; никакой новой команды NES не будет";
     });break;
    case "delete":
     async("Удаление импортированной GGUF",()->{
      ModelLabActivity.harnessDelete(HarnessDashboardActivity.this,arg);
      return "Удалена импортированная модель";
     });break;
    case "probeModel":
     async("Проверяю локальную GGUF",()->{
      ModelLabStore store=ModelLabActivity.harnessStore(HarnessDashboardActivity.this);
      ModelLabStore.Entry selected=store.find(arg);
      if(selected==null)throw new IllegalArgumentException("Модель не найдена");
      if(selected.vision())throw new IllegalStateException(
        "Проверка vision требует реального NES кадра: открой ModelLab");
      File file=store.modelFile(selected);
      ModelLabActivity.harnessEngine(HarnessDashboardActivity.this).consoleAction(
       file,"No framebuffer is present. Return ACTION=STOP only.",0,(answer,ms)->{
        boolean ok="ACTION=STOP".equals(answer);
        synchronized(probes){probes.put(arg,ok?"PASS":"FAIL");}
        operation=(ok?"РЕАЛЬНЫЙ GGUF INFERENCE PASS":"GGUF RESPONSE FAIL")+
          " · "+selected.label+" · "+ms+" ms";
       });
      return "Запущен настоящий native inference; ожидается строгий ACTION=STOP";
     });break;
    case "probeGpu":
     async("Опрашиваю Vulkan через JNI",()->{
      ModelLabActivity.harnessEngine(HarnessDashboardActivity.this)
       .probeGpu((answer,ms)->{
        gpuProbe=answer+" · "+ms+" ms";
        operation="Опрошен реальный GPU backend";
       });
      return "GPU probe запущен; компиляция Vulkan не равна успешному offload";
     });break;
    case "stop":
     HarnessTelemetry.requestStop();
     ConsoleAgentBridge.Brain running=ConsoleAgentBridge.get();
     if(running!=null)running.cancel();
     operation="STOP запрошен; контроллер освободит кнопки";
     break;
    case "export":
     runOnUiThread(()->{
      Intent save=new Intent(Intent.ACTION_CREATE_DOCUMENT);
      save.addCategory(Intent.CATEGORY_OPENABLE);
      save.setType("application/x-ndjson");
      save.putExtra(Intent.EXTRA_TITLE,"localpilot-harness-events-v1.jsonl");
      startActivityForResult(save,EXPORT_EVENTS);
     });break;
    case "learn":
     async("Обучаю реальную CPU Fast Policy",()->HarnessImitationPolicy.train(
       HarnessDashboardActivity.this,journal.recent()));break;
    case "bindPolicy":
     async("Назначение обученной Fast Policy",()->HarnessImitationPolicy.bind(
       HarnessDashboardActivity.this));break;
    default:error="Недопустимая команда Harness";
   }
  }
 }
 @Override protected void onActivityResult(int request,int result,Intent data){
  super.onActivityResult(request,result,data);
  if(result!=RESULT_OK||data==null||data.getData()==null)return;
  Uri uri=data.getData();
  if(request==IMPORT_GGUF)async("Импортирую GGUF",()->{
   String name=displayName(uri);
   ModelLabStore.Entry entry=ModelLabActivity.harnessStore(this)
      .importFile(uri,name,"",null);
   return "SHA проверен, загружена: "+entry.label+". Для игры назначь её агенту.";
  });
  else if(request==EXPORT_EVENTS)async("Экспортирую журнал",()->{
   byte[] bytes=journal.export();
   try(OutputStream out=getContentResolver().openOutputStream(uri)){
    if(out==null)throw new java.io.IOException("No output stream");
    out.write(bytes);
   }
   return "Экспортированы реальные события: "+bytes.length+" байт";
  });
 }
 @Override protected void onDestroy(){
  worker.shutdown();
  if(web!=null){web.removeJavascriptInterface("Harness");web.destroy();web=null;}
  super.onDestroy();
 }
}
