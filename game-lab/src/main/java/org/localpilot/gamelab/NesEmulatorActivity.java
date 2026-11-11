package org.localpilot.gamelab;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.OpenableColumns;
import android.util.Base64;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.localpilot.shared.HubContract;
import org.localpilot.shared.NesTrial;

/**
 * NES CPU/PPU emulation: JSNES 2.1.0 execution inside a
 * separate local Android WebView. User-selected ROMs never leave this device.
 * All inputs are OS-delivered touch events, not artificial ACK or rewards.
 */
public final class NesEmulatorActivity extends Activity {
 private static final int CHOOSE_ROM=101;
 private final ExecutorService romDownloads=Executors.newSingleThreadExecutor();
 private boolean closed;
 private static final int MAX_ROM=3*1024*1024;
 private static final String START_URL="file:///android_asset/nes/index.html";
 private static final String[] GAMEPAD={"UP","LEFT","DOWN","RIGHT","SELECT","START","B","A"};
 private WebView web;
 private TextView state;
 private Button demoButton,aButton,bButton,shareTrial,cpuAgentButton,cpuBatchButton,stopCpuButton;
 private CheckBox exploratoryStopSwitch;
 private final NesExperimentPolicy experiment=new NesExperimentPolicy();
 private long experimentGeneration;
 private boolean homebrewRom;
 private NesTrial readyTrial;
 private TrialPending pendingTrial;
 private static final class TrialPending{
  final String button;
  final int frame,hash;
  boolean downAck,upAck;
  TrialPending(String button,int frame,int hash){
   this.button=button;this.frame=frame;this.hash=hash;
  }
 }
 private boolean resumed;
 private boolean pageLoaded;
 private int frames,hash;
 private long lastInputAt;
 private boolean dendy;
 private final AtomicInteger touches=new AtomicInteger();
 @Override public void onCreate(Bundle bundle){
  super.onCreate(bundle);
  dendy=getIntent().getBooleanExtra("dendy",false);
  LinearLayout root=new LinearLayout(this);
  root.setOrientation(LinearLayout.VERTICAL);GameLabInsets.install(this,root,8);
  root.setBackgroundColor(HubContract.BACKGROUND);
  TextView label=new TextView(this);
  label.setText(dendy?"LocalPilot · Денди (50 Гц)":"LocalPilot · NES");
  label.setTextColor(HubContract.TEXT);label.setTextSize(21);root.addView(label);
  state=new TextView(this);state.setTextColor(HubContract.WARNING);
  state.setText("Загрузи свой .nes или скачай проверенный homebrew. Эмуляция работает офлайн, сеть нужна только для загрузки файла.");
  root.addView(state);
  Button open=new Button(this);open.setText("Выбрать картридж (.nes)");
  root.addView(open);
  open.setOnClickListener(v->{
   Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*")
     .addCategory(Intent.CATEGORY_OPENABLE);
   startActivityForResult(pick,CHOOSE_ROM);
  });
  Button curated=new Button(this);
  curated.setText("Каталог бесплатных homebrew NES-ROM");
  root.addView(curated);
  curated.setOnClickListener(v->{
   try{
    Intent browser=new Intent(Intent.ACTION_VIEW,
      Uri.parse("https://itch.io/games/free/tag-homebrew/tag-nes-rom"));
    startActivity(browser);
    state.setText("Скачай авторский .nes в браузере и выбери его кнопкой «Выбрать картридж».");
   }catch(android.content.ActivityNotFoundException missing){
    state.setText("Браузер недоступен. Импортируй NES ROM из файлов.");
   }
  });
  Button download=new Button(this);
  download.setText("Скачать и запустить VOID LANCER · MIT · 41 КБ");
  root.addView(download);
  download.setOnClickListener(v->{
   if(closed)return;
   download.setEnabled(false);
   state.setText("Проверяю локальную копию или скачиваю подлинный VOID LANCER...");
   romDownloads.execute(()->{
    byte[] rom=null;
    String error=null;
    try{
     rom=loadOrFetchVerifiedRom();
     if(!RetroEmulatorCatalog.inspect(RetroEmulatorCatalog.System.NES,
          VerifiedNesRomDownload.FILENAME,rom).recognized())
      throw new SecurityException("Эмулятор не распознал этот проверенный ROM");
    }catch(Exception failure){
     error="Не удалось загрузить ROM: "+failure.getClass().getSimpleName()+
       (failure.getMessage()==null?"":" · "+failure.getMessage());
    }
    final byte[] accepted=rom;final String problem=error;
    runOnUiThread(()->{
     if(closed)return;
     download.setEnabled(true);
     if(problem!=null){state.setText(problem+" · можно скачать ROM через браузер");return;}
     if(!resumed||!pageLoaded){
      state.setText("ROM сохранён. Вернись в NES и повторно нажми «Скачать и запустить».");return;
     }
     launchRom(accepted,false);
     state.setText("VOID LANCER · реальный NES ROM · SHA-256 OK · ручное управление.");
    });
   });
  });
  demoButton=new Button(this);Button demo=demoButton;
  demo.setText("Запустить тестовую NES-игру");
  root.addView(demo);
  demo.setOnClickListener(v->launchRom(HomebrewNes.cartridge(),true));
  shareTrial=new Button(this);
  shareTrial.setText("NES → ModelLab · передать проверенный эпизод");
  shareTrial.setEnabled(false);
  shareTrial.setOnClickListener(v->sendTrialToModelLab());
  root.addView(shareTrial);
  cpuAgentButton=new Button(this);
  cpuAgentButton.setText("CPU NES-агент · тестовый ход A (16 кадров)");
  cpuAgentButton.setEnabled(false);
  cpuAgentButton.setOnClickListener(v->{
   if(experiment.running())return;
   runHomebrewCpuAgent();
  });
  root.addView(cpuAgentButton);
  exploratoryStopSwitch=new CheckBox(this);
  exploratoryStopSwitch.setText(
    "Лаборатория NES: игнорировать авто-STOP при неизменном кадре (только тестовый ROM)");
  exploratoryStopSwitch.setChecked(false);
  exploratoryStopSwitch.setOnCheckedChangeListener((view,checked)->{
   if(experiment.running())stopCpuExperiment("Режим изменён: серия остановлена");
  });
  root.addView(exploratoryStopSwitch);
  cpuBatchButton=new Button(this);
  cpuBatchButton.setText("Серия CPU-агента · до "+NesExperimentPolicy.MAX_STEPS+" ходов");
  cpuBatchButton.setEnabled(false);
  cpuBatchButton.setOnClickListener(v->startCpuExperiment());
  root.addView(cpuBatchButton);
  stopCpuButton=new Button(this);
  stopCpuButton.setText("STOP · немедленно остановить CPU-агента");
  stopCpuButton.setOnClickListener(v->stopCpuExperiment("STOP: агент остановлен пользователем"));
  root.addView(stopCpuButton);
  web=new WebView(this);
  web.setBackgroundColor(HubContract.BACKGROUND);
  web.getSettings().setJavaScriptEnabled(true);
  web.getSettings().setDomStorageEnabled(false);
  web.getSettings().setAllowFileAccess(true); // only packaged asset file page
  web.getSettings().setAllowContentAccess(false);
  web.getSettings().setAllowFileAccessFromFileURLs(false);
  web.getSettings().setAllowUniversalAccessFromFileURLs(false);
  web.getSettings().setBlockNetworkLoads(true);
  web.getSettings().setMediaPlaybackRequiresUserGesture(false);
  web.setWebViewClient(new WebViewClient(){
   @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){
    return true; // Never navigate away from the bundled NES application.
   }
   @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){
    Uri uri=request.getUrl();
    if(uri==null||!"file".equals(uri.getScheme())||
      !START_URL.equals(uri.toString())&&
      !"file:///android_asset/nes/jsnes.min.js".equals(uri.toString())&&
      !"file:///android_asset/nes/latched-buttons.js".equals(uri.toString()))return
       new WebResourceResponse("text/plain","UTF-8",new java.io.ByteArrayInputStream(new byte[0]));
    return super.shouldInterceptRequest(view,request);
   }
   @Override public void onPageFinished(WebView view,String url){
    pageLoaded=START_URL.equals(url);
    if(!pageLoaded)state.setText("ROM остановлен: ненадёжный источник страницы");
   }
  });
  web.addJavascriptInterface(new Object(){
   @JavascriptInterface public void onFrame(int count,int pixelsHash){
    runOnUiThread(()->{
     if(!resumed)return;
     frames=count;hash=pixelsHash;
     if(experiment.running()&&pendingTrial!=null&&
        count-pendingTrial.frame>180){
      stopCpuExperiment("STOP: не получено подтверждение хода в пределах 180 кадров");
     }
     completeTrialOnFreshFrame(count,pixelsHash);
     publishEvidence();
     if(count%60==0)
      state.setText("Кадров "+frames+" · экран CRC="+Integer.toHexString(hash)
       +" · касаний "+touches.get()+" · только эмуляция (не результат Маркета)");
    });
   }
   @JavascriptInterface public void onAgentAction(String name,int started,int finished){
    runOnUiThread(()->{
     TrialPending current=pendingTrial;
     if(current!=null&&current.button.equals(name)&&current.downAck&&
        started>=current.frame&&finished>=started+4&&finished<=started+60)
      current.upAck=true;
     else if(experiment.running())
      stopCpuExperiment("STOP: несогласованный ACK эмулятора");
     else pendingTrial=null;
    });
   }
   @JavascriptInterface public void onStatus(String message){
    runOnUiThread(()->state.setText(message));
   }
  },"GameLabBridge");
  root.addView(web,new LinearLayout.LayoutParams(-1,0,1));
  for(int row=0;row<2;row++){
   LinearLayout controls=new LinearLayout(this);root.addView(controls);
   for(int i=row*4;i<row*4+4;i++){
    String name=GAMEPAD[i];
    Button btn=new Button(this);btn.setText(name);btn.setTextSize(11);
    if("A".equals(name))aButton=btn;
    if("B".equals(name))bButton=btn;
    btn.setContentDescription("NES "+name+" button");
    btn.setOnTouchListener((view,event)->{
     int type=event.getActionMasked();
     if(type==MotionEvent.ACTION_DOWN){
      beginTrial(name);
      gamepad(name,true,accepted->{
       if(pendingTrial!=null&&pendingTrial.button.equals(name)){
        pendingTrial.downAck=accepted;
        if(!accepted)pendingTrial=null;
       }
      });
      touches.incrementAndGet();lastInputAt=SystemClock.elapsedRealtime();
      publishEvidence();
      return true;
     }
     if(type==MotionEvent.ACTION_UP||type==MotionEvent.ACTION_CANCEL){
      gamepad(name,false,accepted->{
       if(pendingTrial!=null&&pendingTrial.button.equals(name)){
        pendingTrial.upAck=accepted;
        if(!accepted)pendingTrial=null;
       }
      });
      return true;
     }
     return true;
    });
    controls.addView(btn,new LinearLayout.LayoutParams(0,-2,1));
   }
  }
  TextView note=new TextView(this);root.addView(note);
  note.setTextColor(HubContract.SECONDARY);note.setTextSize(11);
  note.setText(dendy?"50 Гц: программная совместимость, не точное железо Денди.":
   "JSNES 2.1.0 · Apache-2.0 · настоящее выполнение кода NES");
  setContentView(root);
  root.requestApplyInsets();
  root.post(this::publishEvidence);
  web.loadUrl(START_URL);
 }
 private void publishEvidence(){
  if(resumed&&demoButton!=null)
   GameLabEvidenceProvider.nes(dendy,frames,hash,touches.get(),demoButton,aButton,bButton);
 }
 private void startCpuExperiment(){
  if(experiment.running())return;
  if(!resumed||!homebrewRom||!pageLoaded||frames<15||hash==0){
   state.setText("Серия доступна только внутри запущенной тестовой NES-игры");
   return;
  }
  experiment.start(true,exploratoryStopSwitch!=null&&exploratoryStopSwitch.isChecked());
  ++experimentGeneration;
  if(cpuBatchButton!=null)cpuBatchButton.setEnabled(false);
  if(cpuAgentButton!=null)cpuAgentButton.setEnabled(false);
  runHomebrewCpuAgent();
 }
 private void stopCpuExperiment(String reason){
  ++experimentGeneration;
  experiment.stop();
  pendingTrial=null;
  if(web!=null&&pageLoaded)
   web.evaluateJavascript("window.retroCancelAgent()",null);
  if(cpuBatchButton!=null)cpuBatchButton.setEnabled(homebrewRom&&pageLoaded&&resumed);
  if(cpuAgentButton!=null)cpuAgentButton.setEnabled(homebrewRom&&pageLoaded&&resumed);
  if(state!=null)state.setText(reason);
 }
 private void runHomebrewCpuAgent(){
  if(!resumed||!homebrewRom||!pageLoaded||frames<15||hash==0||
     (pendingTrial!=null)){
   if(experiment.running())stopCpuExperiment("STOP: тестовая NES недоступна");
   else state.setText("CPU-агент доступен только в запущенном homebrew NES");
   return;
  }
  beginTrial("A");
  web.evaluateJavascript("window.retroAgentTrial('A',16)",result->{
   TrialPending current=pendingTrial;
   if(current==null)return;
   current.downAck="true".equals(result);
   if(!current.downAck){
    if(experiment.running())stopCpuExperiment("STOP: не подтверждён ввод NES");
    else {
     pendingTrial=null;
     state.setText("CPU-агент не подтвердил начало эмулированного ввода");
    }
   }
  });
 }
 private void beginTrial(String key){
  readyTrial=null;
  if(shareTrial!=null)shareTrial.setEnabled(false);
  pendingTrial=(frames>=15&&hash!=0)?new TrialPending(key,frames,hash):null;
 }
 private void completeTrialOnFreshFrame(int count,int pixelHash){
  TrialPending current=pendingTrial;
  if(current==null||!current.downAck||!current.upAck)return;
  if(count-current.frame>600){pendingTrial=null;return;}
  if(count-current.frame<15||pixelHash==0)return;
  try{
   readyTrial=NesTrial.create(homebrewRom?"HOMEBREW":"USER_ROM",
     current.button,current.frame,count,current.hash,pixelHash);
   if(shareTrial!=null)shareTrial.setEnabled(true);
   state.setText(readyTrial.summary()+" · можно передать в ModelLab");
   if(experiment.running()){
    NesExperimentPolicy.Decision result=experiment.observe(readyTrial.status);
    if(result==NesExperimentPolicy.Decision.CONTINUE){
     long serial=experimentGeneration;
     String diagnostic=readyTrial.status.equals("NO_VISUAL_CHANGE")
       ?" · ОШИБКА: кадр не изменился, лабораторное продолжение"
       :" · кадр изменился";
     state.setText("CPU NES: шаг "+experiment.completed()+"/"+
       NesExperimentPolicy.MAX_STEPS+diagnostic);
     web.post(()->{
      if(serial==experimentGeneration&&experiment.running()&&resumed&&
         homebrewRom&&pageLoaded)runHomebrewCpuAgent();
     });
    }else{
     String reason=switch(result){
      case STOP_NO_CHANGE->"STOP: после хода кадр не изменился";
      case STOP_LIMIT->"STOP: достигнут обязательный предел "+
        NesExperimentPolicy.MAX_STEPS+" шагов";
      case STOP_INTEGRITY->"STOP: недействительный отчёт";
      default->"STOP: эксперимент завершён";
     };
     stopCpuExperiment(reason+" · "+readyTrial.status);
    }
   }
  }catch(IllegalArgumentException invalid){
   readyTrial=null;
   if(experiment.running())stopCpuExperiment("STOP: несогласованные кадры NES");
  }
  pendingTrial=null;
 }
 private void sendTrialToModelLab(){
  if(readyTrial==null){state.setText("Для отчёта запусти NES и нажми кнопку");return;}
  Intent intent=new Intent(NesTrial.ACTION);
  intent.setClassName("org.localpilot.modellab","org.localpilot.android.ModelLabActivity");
  intent.putExtra(NesTrial.EXTRA,readyTrial.serialize());
  try{startActivity(intent);}
  catch(android.content.ActivityNotFoundException missing){
   state.setText("Установи отдельный ModelLab, затем отправь NES-эпизод повторно");
  }catch(SecurityException denied){
   state.setText("ModelLab не разрешил приём: проверь установленную версию");
  }
 }
 private byte[] loadOrFetchVerifiedRom()throws Exception{
  File target=new File(getFilesDir(),VerifiedNesRomDownload.FILENAME);
  if(target.isFile()){
   try{
    if(target.length()!=VerifiedNesRomDownload.BYTES)
     throw new SecurityException("Повреждён локальный ROM");
    byte[] saved=Files.readAllBytes(target.toPath());
    VerifiedNesRomDownload.validate(saved);
    return saved;
   }catch(Exception damaged){target.delete();}
  }
  byte[] rom=VerifiedNesRomDownload.download();
  File temp=File.createTempFile("verified-nes-",".partial",getFilesDir());
  try{
   try(FileOutputStream out=new FileOutputStream(temp)){
    out.write(rom);out.getFD().sync();
   }
   if(!temp.renameTo(target))throw new java.io.IOException("Не удалось сохранить ROM");
  }finally{temp.delete();}
  return rom;
 }
 private void launchRom(byte[] rom,boolean homebrew){
  if(!pageLoaded||web==null||rom==null||rom.length>MAX_ROM){
   state.setText("NES-ядро ещё не загружено или ROM недоступен");return;
  }
  String encoded=Base64.encodeToString(rom,Base64.NO_WRAP);
  frames=hash=0;touches.set(0);
  ++experimentGeneration;experiment.stop();
  homebrewRom=homebrew;pendingTrial=null;readyTrial=null;
  if(shareTrial!=null)shareTrial.setEnabled(false);
  if(cpuAgentButton!=null)cpuAgentButton.setEnabled(false);
  if(cpuBatchButton!=null)cpuBatchButton.setEnabled(false);
  web.evaluateJavascript("window.retroLoadRom("+org.json.JSONObject.quote(encoded)+","
    +(dendy?"true":"false")+","+(homebrew?"true":"false")+")",success->{
   boolean launched="true".equals(success);
   if(cpuAgentButton!=null)cpuAgentButton.setEnabled(launched&&homebrew);
   if(cpuBatchButton!=null)cpuBatchButton.setEnabled(launched&&homebrew);
   if(!launched)state.setText("NES ROM не запущен. Проверьте mapper и ядро.");
  });
 }
 private interface InputAck{void result(boolean accepted);}
 private void gamepad(String key,boolean down,InputAck ack){
  if(web==null||!pageLoaded){ack.result(false);return;}
  web.evaluateJavascript("window.retroButton("+org.json.JSONObject.quote(key)+","
   +(down?"true":"false")+")",response->ack.result("true".equals(response)));
 }
 private String filename(Uri uri){
  try(Cursor cursor=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){
   if(cursor!=null&&cursor.moveToFirst())return cursor.getString(0);
  }catch(RuntimeException ignored){}
  return "";
 }
 @Override protected void onActivityResult(int req,int result,Intent data){
  super.onActivityResult(req,result,data);
  if(req!=CHOOSE_ROM||result!=RESULT_OK||data==null||data.getData()==null)return;
  Uri uri=data.getData();
  String name=filename(uri);
  try(InputStream in=getContentResolver().openInputStream(uri)){
   if(in==null)throw new IOException("no cartridge reader");
   ByteArrayOutputStream memory=new ByteArrayOutputStream();
   byte[] part=new byte[8192];int amount,total=0;
   while((amount=in.read(part))!=-1){
    total+=amount;
    if(total>MAX_ROM)throw new IOException("cartridge exceeds bound");
    memory.write(part,0,amount);
   }
   byte[] rom=memory.toByteArray();
   if(!RetroEmulatorCatalog.inspect(dendy?RetroEmulatorCatalog.System.DENDY:
      RetroEmulatorCatalog.System.NES,name,rom).recognized())
    throw new IOException("iNES/NES2 cartridge header not recognized");
   if(!pageLoaded)throw new IOException("emulator page not yet loaded");
   launchRom(rom,false);
  }catch(IOException|RuntimeException exception){
   state.setText("ROM не запущен: "+exception.getClass().getSimpleName());
   Toast.makeText(this,"Неподдерживаемый или слишком большой файл игры",Toast.LENGTH_LONG).show();
  }
 }
 @Override protected void onPause(){
  resumed=false;GameLabEvidenceProvider.clear("nes");
  if(experiment.running())stopCpuExperiment("STOP: NES ушла в фон");
  pendingTrial=null; // Do not link a paused/resumed frame to an old input.
  if(web!=null&&pageLoaded)web.evaluateJavascript("window.retroPause()",null);
  if(web!=null)web.onPause();
  super.onPause();
 }
 @Override protected void onResume(){
  super.onResume();resumed=true;
  if(demoButton!=null)demoButton.post(this::publishEvidence);
  if(web!=null){web.onResume();
   if(pageLoaded)web.evaluateJavascript("window.retroResume()",null);
  }
 }
 @Override protected void onDestroy(){
  closed=true;
  romDownloads.shutdownNow();
  ++experimentGeneration;experiment.stop();
  if(web!=null){web.evaluateJavascript("window.retroStop()",null);
   web.removeJavascriptInterface("GameLabBridge");
   web.destroy();web=null;
  }
  super.onDestroy();
 }
}
