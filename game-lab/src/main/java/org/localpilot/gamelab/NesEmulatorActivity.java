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
import org.localpilot.shared.NesFrameTransfer;
import org.localpilot.shared.ConsoleExperienceMemory;
import org.localpilot.shared.ConsoleAgentContract;

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
 private Button demoButton,aButton,bButton,shareTrial,cpuAgentButton,cpuBatchButton,stopCpuButton,aiObserveButton;
 private CheckBox exploratoryStopSwitch;
 private final NesExperimentPolicy experiment=new NesExperimentPolicy();
 private long experimentGeneration;
 private boolean homebrewRom;
 private NesTrial readyTrial;
 private NesTrial exportTrial;
 private final NesSnapshotGate snapshotGate=new NesSnapshotGate();
 private final ConsoleAgentGate aiGate=new ConsoleAgentGate();
 private boolean aiSnapshot,aiRunning;
 private String aiTicketNonce,queuedAiButton,aiModelId="",aiModelSha="",aiEpoch="";
 private int aiExpectedHash;
 private long aiIssuedAt;
 // Unlike snapshot tokens, frame epochs also fence delayed onFrame/onAgentAction
 // after a ROM switch, failed load, or Android WebView replay.
 private final NesFrameEpoch frameEpoch=new NesFrameEpoch();
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
   final String previousRom=frameEpoch.token();
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
     // A manual/demo ROM selected while the download was running wins.
     if(!previousRom.equals(frameEpoch.token())){
      state.setText("ROM загружен и сохранён. Текущую игру не заменяю.");return;
     }
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
  aiObserveButton=new Button(this);
  aiObserveButton.setText("🧠 ИИ-агент · получить кадр и выполнить один ход NES");
  aiObserveButton.setEnabled(false);
  aiObserveButton.setOnClickListener(v->requestAiObservation());
  root.addView(aiObserveButton);
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
   @JavascriptInterface public void onFrame(int count,int pixelsHash,String session){
    runOnUiThread(()->{
     if(!resumed||!frameEpoch.accept(session,count))return;
     frames=count;hash=pixelsHash;
     if(experiment.running()&&pendingTrial!=null&&
        count-pendingTrial.frame>180){
      stopCpuExperiment("STOP: не получено подтверждение хода в пределах 180 кадров");
     }
     if(aiRunning&&pendingTrial!=null&&count-pendingTrial.frame>180)
      stopCpuExperiment("STOP: ИИ-ход не подтверждён новым NES-кадром");
     completeTrialOnFreshFrame(count,pixelsHash);
     publishEvidence();
     if(count%60==0)
      state.setText("Кадров "+frames+" · экран CRC="+Integer.toHexString(hash)
       +" · касаний "+touches.get()+" · только эмуляция (не результат Маркета)");
    });
   }
   @JavascriptInterface public void onAgentAction(String name,int started,int finished,String session){
    runOnUiThread(()->{
     if(!resumed||!frameEpoch.current(session))return;
     TrialPending current=pendingTrial;
     if(current!=null&&current.button.equals(name)&&current.downAck&&
        started>=current.frame&&finished>=started+4&&finished<=started+60)
      current.upAck=true;
     else if(experiment.running())
      stopCpuExperiment("STOP: несогласованный ACK эмулятора");
     else pendingTrial=null;
    });
   }
   @JavascriptInterface public void onSnapshot(String encoded,String token){
    // JavascriptInterface runs on a different WebView thread. Reject huge
    // responses before allocating or decoding any PNG bytes.
    byte[] png=null;
    if(encoded!=null&&encoded.length()<=NesFrameTransfer.MAX_BASE64_CHARS){
     try{
      byte[] candidate=Base64.decode(encoded,Base64.NO_WRAP);
      NesFrameTransfer.verify(candidate);
      png=candidate;
     }catch(RuntimeException ignored){ /* Text evidence still transfers. */ }
    }
    final byte[] accepted=png;
    runOnUiThread(()->{
     if(closed||!resumed||!snapshotGate.current(token))return;
     if(aiSnapshot)completeAiObservation(token,accepted);
     else completeExport(token,accepted);
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
     if(aiRunning){stopCpuExperiment("STOP: ручное управление прервало ИИ-ход");return true;}
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
  aiGate.stop();aiTicketNonce=null;aiSnapshot=false;queuedAiButton=null;aiRunning=false;
  snapshotGate.cancel();exportTrial=null;
  if(aiObserveButton!=null)aiObserveButton.setEnabled(
    resumed&&homebrewRom&&pageLoaded);
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
    if(aiRunning){
     aiRunning=false;
     boolean saved=recordAiEpisode(readyTrial);
     state.setText(saved?
       "ИИ-ход "+readyTrial.button+": "+readyTrial.status+
         " · сохранён в общей памяти, награда неизвестна":
       "STOP: кадр проверен, но общая память эпизодов недоступна; ход не записан");
    }
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
 private boolean aiEligible(){
  return getPackageName().startsWith("org.localpilot.android")&&
    resumed&&homebrewRom&&pageLoaded&&web!=null&&frames>=15&&hash!=0&&
    !experiment.running()&&!aiRunning&&pendingTrial==null;
 }
 private void requestAiObservation(){
  if(!aiEligible()||snapshotGate.pending()||aiGate.hasPending()){
   state.setText("ИИ: необходима активная встроенная NES, без другого выполняющегося действия");
   return;
  }
  try{
   ConsoleAgentGate.Ticket ticket=aiGate.begin(
     frameEpoch.token(),frames,hash,SystemClock.elapsedRealtime());
   aiTicketNonce=ticket.nonce;
   aiExpectedHash=ticket.hash;
   String token=snapshotGate.begin();
   aiSnapshot=true;
   if(aiObserveButton!=null)aiObserveButton.setEnabled(false);
   web.evaluateJavascript("window.retroSnapshot("+org.json.JSONObject.quote(token)+")",ack->{
    if(snapshotGate.current(token)&&!"true".equals(ack))completeAiObservation(token,null);
   });
   web.postDelayed(()->{
    if(snapshotGate.current(token))completeAiObservation(token,null);
   },2500);
  }catch(RuntimeException failure){
   aiGate.stop();aiSnapshot=false;aiTicketNonce=null;
   state.setText("ИИ: не удалось запросить наблюдение: "+
     failure.getClass().getSimpleName());
  }
 }
 private void completeAiObservation(String token,byte[] png){
  if(!snapshotGate.finish(token))return;
  aiSnapshot=false;
  if(aiObserveButton!=null)aiObserveButton.setEnabled(aiEligible());
  if(png==null||!aiGate.hasPending()||!aiEligible()||
    hash!=aiExpectedHash){
   aiGate.stop();aiTicketNonce=null;
   state.setText("STOP: ИИ не получил достоверный свежий NES PNG-кадр");
   return;
  }
  Intent intent=new Intent(ConsoleAgentContract.OBSERVE)
    .setClassName(getPackageName(),"org.localpilot.android.ModelLabActivity")
    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|
      Intent.FLAG_ACTIVITY_SINGLE_TOP);
  // A nonce identifies only this one observation, not an Android gesture permit.
  intent.putExtra(ConsoleAgentContract.TOKEN,aiTicketNonce);
  intent.putExtra(ConsoleAgentContract.FRAME,frames);
  intent.putExtra(ConsoleAgentContract.HASH,hash);
  intent.putExtra(NesFrameTransfer.EXTRA_PNG,png);
  try{startActivity(intent);}
  catch(RuntimeException failure){
   aiGate.stop();aiTicketNonce=null;state.setText("STOP: ModelLab не открылся");
  }
 }
 private boolean recordAiEpisode(NesTrial episode){
  try{
   // The SAME bounded, verified source is consumed by ModelLab and the main
   // console status. Old JSONL is retained read-only as legacy diagnostics.
   ConsoleExperienceMemory.append(getFilesDir().toPath(),aiModelId,aiModelSha,episode);
   return true;
  }catch(IOException|IllegalArgumentException invalid){
   return false; // never claim that experience has been saved
  }
 }
 private void handleAiMove(Intent reply){
  if(reply==null||!ConsoleAgentContract.MOVE.equals(reply.getAction()))return;
  String button=reply.getStringExtra(ConsoleAgentContract.BUTTON);
  String nonce=reply.getStringExtra(ConsoleAgentContract.TOKEN);
  String modelId=reply.getStringExtra(ConsoleAgentContract.MODEL_ID);
  String modelSha=reply.getStringExtra(ConsoleAgentContract.MODEL_SHA);
  boolean admissible=button!=null&&
    (ConsoleAgentContract.playable(button)||"NONE".equals(button))&&
    modelId!=null&&modelId.matches("[A-Za-z0-9:_.-]{1,96}")&&
    modelSha!=null&&modelSha.matches("[a-f0-9]{64}")&&
    aiGate.consume(nonce,frameEpoch.token(),hash,SystemClock.elapsedRealtime())&&
    getPackageName().startsWith("org.localpilot.android")&&homebrewRom&&
    pageLoaded&&web!=null&&pendingTrial==null&&!experiment.running()&&!aiRunning;
  aiTicketNonce=null;
  if(!admissible){
   aiGate.stop();state.setText("STOP: ИИ-команда устарела, повреждена или ROM изменился");
   return;
  }
  aiModelId=modelId;aiModelSha=modelSha;
  if("NONE".equals(button)){
   state.setText("ИИ выбрал NONE: ввод не выполнялся");return;
  }
  queuedAiButton=button;aiEpoch=frameEpoch.token();aiExpectedHash=hash;
  aiIssuedAt=SystemClock.elapsedRealtime();
  if(resumed&&web!=null)web.postDelayed(this::executeAiMove,350);
 }
 private void executeAiMove(){
  String button=queuedAiButton;
  if(button==null||!resumed)return;
  queuedAiButton=null;
  if(!aiEligible()||!frameEpoch.current(aiEpoch)||hash!=aiExpectedHash||
    SystemClock.elapsedRealtime()-aiIssuedAt>ConsoleAgentContract.TTL_MS){
   state.setText("STOP: новый кадр не совпадает с наблюдением ИИ");
   return;
  }
  beginTrial(button);
  if(pendingTrial==null){
   state.setText("STOP: исходное состояние NES не подтверждено");return;
  }
  aiRunning=true;
  state.setText("ИИ "+aiModelId+" · кнопка "+button+" на 16 кадров; ожидаю проверку");
  web.evaluateJavascript("window.retroAiTrial("+
    org.json.JSONObject.quote(button)+","+ConsoleAgentContract.HOLD_FRAMES+")",ack->{
     TrialPending pending=pendingTrial;
     if(pending==null||!pending.button.equals(button))return;
     pending.downAck="true".equals(ack);
     if(!pending.downAck)stopCpuExperiment("STOP: NES отверг ИИ-действие");
  });
 }
 @Override protected void onNewIntent(Intent next){
  super.onNewIntent(next);
  setIntent(next);
  handleAiMove(next);
 }
 private void sendTrialToModelLab(){
  if(readyTrial==null){state.setText("Для отчёта запусти NES и нажми кнопку");return;}
  if(snapshotGate.pending()||!resumed||!pageLoaded||web==null){
   state.setText("Сначала открой работающий NES-эмулятор");return;
  }
  String token=snapshotGate.begin();
  exportTrial=readyTrial;
  shareTrial.setEnabled(false);
  // Snapshot callback is optional: old WebViews can still send the trusted
  // structured observation alone. Never wait indefinitely for the bridge.
  web.evaluateJavascript("window.retroSnapshot("+org.json.JSONObject.quote(token)+")",value->{
   if(snapshotGate.current(token)&&!"true".equals(value))completeExport(token,null);
  });
  shareTrial.postDelayed(()->{
   if(snapshotGate.current(token)&&resumed&&!closed)completeExport(token,null);
  },2500);
 }
 private void completeExport(String token,byte[] png){
  // Delayed JavaScriptInterface deliveries and old timeout callbacks can never
  // satisfy a newer transfer, even if the user switched ROMs in between.
  if(!snapshotGate.finish(token))return;
  NesTrial evidence=exportTrial;
  exportTrial=null;
  if(shareTrial!=null)shareTrial.setEnabled(readyTrial!=null);
  if(evidence==null||closed||!resumed)return;
  Intent intent=new Intent(NesTrial.ACTION);
  // Same APK if GameLab is hosted within LocalPilot; separate standalone lab otherwise.
  final String modelPackage=getPackageName().startsWith("org.localpilot.android")
    ?getPackageName():"org.localpilot.modellab";
  intent.setClassName(modelPackage,"org.localpilot.android.ModelLabActivity");
  // Reuse the existing ModelLab task/session (and its in-RAM inference engine)
  // rather than creating a second separate, competing model instance.
  intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|
    Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
  intent.putExtra(NesTrial.EXTRA,evidence.serialize());
  if(png!=null)intent.putExtra(NesFrameTransfer.EXTRA_PNG,png);
  try{
   startActivity(intent);
  }catch(android.content.ActivityNotFoundException missing){
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
  String session=frameEpoch.begin();
  frames=hash=0;touches.set(0);
  ++experimentGeneration;experiment.stop();
  snapshotGate.cancel();exportTrial=null;
  aiGate.stop();aiTicketNonce=null;queuedAiButton=null;aiRunning=false;aiSnapshot=false;
  homebrewRom=homebrew;pendingTrial=null;readyTrial=null;
  if(shareTrial!=null)shareTrial.setEnabled(false);
  if(cpuAgentButton!=null)cpuAgentButton.setEnabled(false);
  if(cpuBatchButton!=null)cpuBatchButton.setEnabled(false);
  web.evaluateJavascript("window.retroLoadRom("+org.json.JSONObject.quote(encoded)+","
    +(dendy?"true":"false")+","+(homebrew?"true":"false")+","
    +org.json.JSONObject.quote(session)+")",success->{
   if(closed||!frameEpoch.current(session))return;
   boolean launched="true".equals(success);
   if(!launched)frameEpoch.cancel();
   if(cpuAgentButton!=null)cpuAgentButton.setEnabled(launched&&homebrew);
   if(cpuBatchButton!=null)cpuBatchButton.setEnabled(launched&&homebrew);
   if(aiObserveButton!=null)aiObserveButton.setEnabled(launched&&homebrew&&
     getPackageName().startsWith("org.localpilot.android"));
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
  resumed=false;snapshotGate.cancel();exportTrial=null;
  if(shareTrial!=null)shareTrial.setEnabled(readyTrial!=null);
  GameLabEvidenceProvider.clear("nes");
  if(experiment.running()||aiRunning)stopCpuExperiment("STOP: NES ушла в фон");
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
  if(queuedAiButton!=null&&web!=null)web.postDelayed(this::executeAiMove,350);
 }
 @Override protected void onDestroy(){
  closed=true;aiGate.stop();queuedAiButton=null;aiRunning=false;
  frameEpoch.cancel();snapshotGate.cancel();exportTrial=null;
  romDownloads.shutdownNow();
  ++experimentGeneration;experiment.stop();
  if(web!=null){web.evaluateJavascript("window.retroStop()",null);
   web.removeJavascriptInterface("GameLabBridge");
   web.destroy();web=null;
  }
  super.onDestroy();
 }
}
