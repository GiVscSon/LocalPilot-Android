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
import androidx.webkit.WebViewAssetLoader;
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
import org.localpilot.shared.HomebrewGoalReward;
import org.localpilot.shared.ConsoleAgentContract;
import org.localpilot.shared.HarnessCoordinator;

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
 private static final String START_URL="https://appassets.androidplatform.net/assets/nes/index.html";
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
 // Process-wide permit owner shared by the one embedded NES instance.
 private static final class GoalSample {
  final String epoch,button;
  final int start,finish,x0,y0,x1,y1;
  GoalSample(String epoch,String button,int start,int finish,int x0,int y0,int x1,int y1){
   this.epoch=epoch;this.button=button;this.start=start;this.finish=finish;
   this.x0=x0;this.y0=y0;this.x1=x1;this.y1=y1;
  }
 }
 private GoalSample pendingGoal;
 private static final class TaskObservation {
  final String token,epoch;final int x,y;
  TaskObservation(String token,String epoch,int x,int y){
   this.token=token;this.epoch=epoch;this.x=x;this.y=y;
  }
 }
 private TaskObservation taskObservation;
 private final HarnessCoordinator harness=HarnessCoordinator.nes();
 private HarnessCoordinator.ActionPermit pendingPermit;
 private boolean aiSnapshot,aiRunning,aiFrozen;
 private String aiTicketNonce,queuedAiButton,aiModelId="",aiModelSha="",aiEpoch="";
 private int aiExpectedHash,aiHoldFrames=ConsoleAgentContract.HOLD_FRAMES;
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
  aiObserveButton.setText("🧠 ИИ-агент · STEP_MODE · GGUF → 16 кадров NES");
  aiObserveButton.setEnabled(false);
  aiObserveButton.setOnClickListener(v->requestAiObservation());
  root.addView(aiObserveButton);
  Button resumeRealtime=new Button(this);
  resumeRealtime.setText("▶ REALTIME_MODE · продолжить ручную NES");
  resumeRealtime.setOnClickListener(v->{
   if(!aiFrozen||!resumed||!pageLoaded||web==null){
    state.setText("NES уже выполняется в обычном режиме или не запущена");return;
   }
   stopCpuExperiment("STOP: переход STEP → REALTIME");
   String currentEpoch=frameEpoch.token();
   web.evaluateJavascript("window.retroExitStepMode()",reply->{
    if("true".equals(reply)&&frameEpoch.current(currentEpoch)&&
       harness.changeMode(currentEpoch,HarnessCoordinator.Mode.REALTIME_MODE)){
     aiFrozen=false;
     state.setText("REALTIME_MODE: автоматические шаги прекращены, ручное управление доступно");
    }else state.setText("STOP: не удалось возобновить реальное время NES");
   });
  });
  root.addView(resumeRealtime);
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
  web.getSettings().setAllowFileAccess(false);
  web.getSettings().setAllowContentAccess(false);
  web.getSettings().setAllowFileAccessFromFileURLs(false);
  web.getSettings().setAllowUniversalAccessFromFileURLs(false);
  web.getSettings().setBlockNetworkLoads(true);
  web.getSettings().setMediaPlaybackRequiresUserGesture(false);
  // Only the three bundled NES documents can be served, and only via HTTPS.
  final WebViewAssetLoader assetLoader=new WebViewAssetLoader.Builder()
    .addPathHandler("/assets/",new WebViewAssetLoader.AssetsPathHandler(this)).build();
  web.setWebViewClient(new WebViewClient(){
   @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){
    return true; // Never navigate away from the bundled NES application.
   }
   @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){
    Uri uri=request.getUrl();
    if(uri==null||!"https".equals(uri.getScheme())||
       !"appassets.androidplatform.net".equals(uri.getHost())||
       uri.getPort()!=-1||uri.getQuery()!=null||uri.getFragment()!=null||
       !("/assets/nes/index.html".equals(uri.getPath())||
         "/assets/nes/jsnes.min.js".equals(uri.getPath())||
         "/assets/nes/latched-buttons.js".equals(uri.getPath())))
     return new WebResourceResponse("text/plain","UTF-8",
       new java.io.ByteArrayInputStream(new byte[0]));
    WebResourceResponse result=assetLoader.shouldInterceptRequest(uri);
    return result!=null?result:new WebResourceResponse("text/plain","UTF-8",
       new java.io.ByteArrayInputStream(new byte[0]));
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
   @JavascriptInterface public void onHomebrewTask(String name,int start,int finish,
       int beforeX,int beforeY,int afterX,int afterY,String session,String nonce){
    runOnUiThread(()->{
     if(pendingPermit==null||!pendingPermit.nonce.equals(nonce)||!pendingPermit.epoch.equals(session))return;
     if(closed||!resumed||!homebrewRom||!aiFrozen||!aiRunning||
         !frameEpoch.current(session)||pendingTrial==null||
         !pendingTrial.button.equals(name)||pendingTrial.frame!=start||
         pendingPermit==null||(long)finish-start!=pendingPermit.holdFrames||
         !harness.authorizesStep(session,nonce,finish-start,SystemClock.elapsedRealtime())){
      if(aiRunning)stopCpuExperiment("STOP: не удалось привязать RAM-награду к NES-сеансу");
      return;
     }
     try{
      HomebrewGoalReward.verify(name,start,finish,beforeX,beforeY,afterX,afterY);
      pendingGoal=new GoalSample(session,name,start,finish,beforeX,beforeY,afterX,afterY);
     }catch(IllegalArgumentException invalid){
      stopCpuExperiment("STOP: память встроенной NES противоречит заявленному действию");
     }
    });
   }
   @JavascriptInterface public boolean allowStep(String session,String nonce,int requestedFrames){
    return harness.authorizesStep(session,nonce,requestedFrames,SystemClock.elapsedRealtime());
   }
   @JavascriptInterface public void onStepAction(String name,int started,int finished,String session,String nonce){
    runOnUiThread(()->{
     if(pendingPermit==null||!harness.authorizesStep(session,nonce,finished-started,SystemClock.elapsedRealtime())||
         started!=pendingPermit.beforeFrame||(long)finished-started!=pendingPermit.holdFrames)return;
     acceptAgentAck(name,started,finished,session);
    });
   }
   private void acceptAgentAck(String name,int started,int finished,String session){
    if(!resumed||!frameEpoch.current(session)||pendingTrial==null)return;
    TrialPending current=pendingTrial;
    if(current.button.equals(name)&&(current.downAck||(aiFrozen&&aiRunning))&&
        started==current.frame&&(long)finished-started>=4&&(long)finished-started<=60){
     if(aiFrozen&&aiRunning)current.downAck=true;
     current.upAck=true;
    }else if(experiment.running()||aiRunning)
     stopCpuExperiment("STOP: несогласованный ACK эмулятора");
   }
   @JavascriptInterface public void onAgentAction(String name,int started,int finished,String session){
    runOnUiThread(()->{if(!aiFrozen)acceptAgentAck(name,started,finished,session);});
   }
   @JavascriptInterface public void onStepReady(int count,int pixelsHash,String session){
    runOnUiThread(()->{
     if(closed||!resumed||!homebrewRom||!frameEpoch.current(session)||
        count<15||pixelsHash==0||harness.actionInFlight()||harness.hasPending()){
      stopCpuExperiment("STOP: неподтверждённый замороженный NES-кадр");
      return;
     }
     if(count<frames||(count==frames&&pixelsHash!=hash)||
        (count>frames&&!frameEpoch.accept(session,count))){
      stopCpuExperiment("STOP: STEP epoch/frame/hash не совпадают");
      return;
     }
     frames=count;hash=pixelsHash;
     if(!harness.changeMode(session,HarnessCoordinator.Mode.STEP_MODE)){
      stopCpuExperiment("STOP: другой владелец NES-ввода");return;
     }
     aiFrozen=true;
     requestFrozenAiObservation();
    });
   }
   @JavascriptInterface public void onHomebrewObservation(
      int x,int y,String token,String epoch){
    runOnUiThread(()->{
     if(closed||!resumed||!homebrewRom||!aiFrozen||
        !frameEpoch.current(epoch)||!snapshotGate.current(token))return;
     try{
      HomebrewGoalReward.distance(x,y); // Only real 8-bit RAM task coordinates
      taskObservation=new TaskObservation(token,epoch,x,y);
     }catch(IllegalArgumentException bad){
      harness.stopForEpoch(frameEpoch.token(),"INVALID_TASK_OBSERVATION");
      taskObservation=null;
     }
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
     if(aiFrozen){
      state.setText("STEP_MODE заморозил игру; нажми ▶ REALTIME_MODE для ручного управления");
      return true;
     }
     if(aiRunning||experiment.running()||harness.hasPending()){
      stopCpuExperiment("STOP: ручной ввод отозвал текущий агентский permit");return true;
     }
     if(type==MotionEvent.ACTION_DOWN){
      if(!beginTrial(name,HarnessCoordinator.Actor.MANUAL)){
       state.setText("STOP: контроллер занят другим вводом");return true;
      }
      gamepad(name,true,accepted->{
       if(pendingTrial!=null&&pendingTrial.button.equals(name)){
        pendingTrial.downAck=accepted;
        if(!accepted)stopCpuExperiment("STOP: NES отверг ручное нажатие");
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
        if(!accepted)stopCpuExperiment("STOP: NES отверг отпускание кнопки");
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
  if(aiFrozen){state.setText("Сначала выйди из STEP_MODE");return;}
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
  final String stopEpoch=frameEpoch.token();
  final long stopRevision=harness.requestStop(stopEpoch,"STOP_OR_RECOVERY");
  pendingPermit=null;pendingGoal=null;taskObservation=null;
  aiTicketNonce=null;aiSnapshot=false;queuedAiButton=null;aiRunning=false;
  snapshotGate.cancel();exportTrial=null;
  if(aiObserveButton!=null)aiObserveButton.setEnabled(false);
  ++experimentGeneration;
  experiment.stop();
  pendingTrial=null;
  if(web!=null&&pageLoaded)
   web.evaluateJavascript("window.retroCancelAgent()",ack->{
    if(stopRevision>=0&&frameEpoch.current(stopEpoch)&&"true".equals(ack)&&
        harness.confirmStop(stopEpoch,stopRevision)){
     boolean ready=resumed&&homebrewRom&&pageLoaded&&!closed;
     if(aiObserveButton!=null)aiObserveButton.setEnabled(ready);
     if(cpuBatchButton!=null)cpuBatchButton.setEnabled(ready&&!aiFrozen);
     if(cpuAgentButton!=null)cpuAgentButton.setEnabled(ready&&!aiFrozen);
     if(state!=null)state.setText(reason+" · освобождение JS-ввода подтверждено");
    }
   });
  if(cpuBatchButton!=null)cpuBatchButton.setEnabled(false);
  if(cpuAgentButton!=null)cpuAgentButton.setEnabled(false);
  if(state!=null)state.setText(reason+" · ожидаю подтверждение JS STOP");
  if(web!=null)web.postDelayed(()->{
   if(frameEpoch.current(stopEpoch)&&harness.stopRevision()==stopRevision&&
       harness.stopAwaitingConfirmation()&&state!=null)
    state.setText("STOP: освобождение ввода не подтверждено; новые permit запрещены, требуется перезагрузка ROM");
  },2500);
 }
 private void runHomebrewCpuAgent(){
  if(aiFrozen){state.setText("Сначала выйди из STEP_MODE");return;}
  if(!resumed||!homebrewRom||!pageLoaded||frames<15||hash==0||
     (pendingTrial!=null)){
   if(experiment.running())stopCpuExperiment("STOP: тестовая NES недоступна");
   else state.setText("CPU-агент доступен только в запущенном homebrew NES");
   return;
  }
  if(!beginTrial("A",HarnessCoordinator.Actor.HEURISTIC)){
   stopCpuExperiment("STOP: право на ввод NES занято");return;
  }
  web.evaluateJavascript("window.retroAgentTrial('A',16)",result->{
   TrialPending current=pendingTrial;
   if(current==null)return;
   current.downAck="true".equals(result);
   if(!current.downAck)
    stopCpuExperiment("STOP: NES не подтвердил начало CPU-ввода");
  });
 }
 private boolean beginTrial(String key,HarnessCoordinator.Actor actor){
  return beginTrial(key,actor,actor==HarnessCoordinator.Actor.MANUAL?0:ConsoleAgentContract.HOLD_FRAMES);
 }
 private boolean beginTrial(String key,HarnessCoordinator.Actor actor,int holdFrames){
  if(pendingTrial!=null||frames<15||hash==0)return false;
  HarnessCoordinator.ActionPermit permit=harness.start(
    actor,frameEpoch.token(),key,frames,hash,
    holdFrames,
    SystemClock.elapsedRealtime());
  if(permit==null)return false;
  readyTrial=null;
  if(shareTrial!=null)shareTrial.setEnabled(false);
  pendingPermit=permit;
  pendingTrial=new TrialPending(key,frames,hash);
  return true;
 }
 private void completeTrialOnFreshFrame(int count,int pixelHash){
  TrialPending current=pendingTrial;
  if(current==null)return;
  // A missing UP/ACK must not hold the unique permit forever.
  if(count-current.frame>600){
   stopCpuExperiment("STOP: превышено 600 NES-кадров без полного подтверждения");
   return;
  }
  if(!current.downAck||!current.upAck||pendingPermit==null||
      (long)count-current.frame<(pendingPermit.holdFrames>0?pendingPermit.holdFrames:15)||pixelHash==0)
   return;
  try{
   if(!harness.complete(pendingPermit,frameEpoch.token(),count,pixelHash,
       current.downAck,current.upAck,SystemClock.elapsedRealtime())){
    pendingPermit=null;
    stopCpuExperiment("STOP: Harness отверг несогласованный NES-переход");
    return;
   }
   pendingPermit=null;
   readyTrial=NesTrial.create(homebrewRom?"HOMEBREW":"USER_ROM",
     current.button,current.frame,count,current.hash,pixelHash);
   if(shareTrial!=null)shareTrial.setEnabled(true);
   state.setText(readyTrial.summary()+" · можно передать в ModelLab");
    if(aiRunning){
     aiRunning=false;
     GoalSample goal=pendingGoal;
     pendingGoal=null;
     boolean saved=recordAiEpisode(readyTrial);
     if(!saved)state.setText("STOP: общий журнал NES недоступен");
     else if(goal!=null&&goal.epoch.equals(frameEpoch.token())&&
          goal.button.equals(readyTrial.button)&&
          goal.start==readyTrial.frameBefore&&goal.finish==readyTrial.frameAfter){
      try{
       ConsoleExperienceMemory.appendGoal(getFilesDir().toPath(),goal.epoch,
         aiModelId,aiModelSha,readyTrial,goal.x0,goal.y0,goal.x1,goal.y1);
       HomebrewGoalReward.Result task=HomebrewGoalReward.verify(goal.button,
         goal.start,goal.finish,goal.x0,goal.y0,goal.x1,goal.y1);
       state.setText("NES 6502: "+goal.button+" · задача дойти до (192,112)"+
         " · reward "+task.reward()+" · "+(task.reached()?"ЦЕЛЬ ДОСТИГНУТА":"продолжается")+
         " · вес GGUF не изменился");
      }catch(IOException|IllegalArgumentException invalid){
       state.setText("STOP: подтверждён ход, но награда NES не записана: "+
         invalid.getClass().getSimpleName());
      }
     }else{
      state.setText("ИИ-ход "+readyTrial.button+": "+readyTrial.status+
        " · RAM-награда не подтверждена (UNKNOWN)");
     }
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
  if(!aiEligible()||snapshotGate.pending()||harness.hasPending()){
   state.setText("ИИ: необходима активная встроенная NES, без другого выполняющегося действия");
   return;
  }
  if(aiFrozen){requestFrozenAiObservation();return;}
  // JSNES confirms it has stopped the realtime timer and returns the
  // authoritative last frame/hash through onStepReady before a ticket exists.
  web.evaluateJavascript("window.retroEnterStepMode()",result->{
   if(!"true".equals(result)&&!aiFrozen){
    harness.stopForEpoch(frameEpoch.token(),"STEP_MODE_UNAVAILABLE");
    state.setText("STOP: эмулятор не подтвердил переход в STEP_MODE");
   }
  });
 }
 private void requestFrozenAiObservation(){
  if(!aiFrozen||!aiEligible()||snapshotGate.pending()||harness.hasPending()){
   state.setText("STOP: нет единственного стабильного кадра STEP_MODE");return;
  }
  try{
   HarnessCoordinator.Ticket ticket=harness.begin(
     frameEpoch.token(),frames,hash,SystemClock.elapsedRealtime());
   aiTicketNonce=ticket.nonce;
   aiExpectedHash=ticket.hash;
   String token=snapshotGate.begin();
   taskObservation=null;
   aiSnapshot=true;
   if(aiObserveButton!=null)aiObserveButton.setEnabled(false);
   web.evaluateJavascript("window.retroSnapshot("+org.json.JSONObject.quote(token)+")",ack->{
    if(snapshotGate.current(token)&&!"true".equals(ack))completeAiObservation(token,null);
   });
   web.postDelayed(()->{
    if(snapshotGate.current(token))completeAiObservation(token,null);
   },2500);
  }catch(RuntimeException failure){
   harness.stopForEpoch(frameEpoch.token(),"STOP");aiSnapshot=false;aiTicketNonce=null;
   state.setText("ИИ: не удалось запросить наблюдение: "+
     failure.getClass().getSimpleName());
  }
 }
 private void completeAiObservation(String token,byte[] png){
  if(!snapshotGate.finish(token))return;
  TaskObservation task=taskObservation;
  taskObservation=null;
  aiSnapshot=false;
  if(aiObserveButton!=null)aiObserveButton.setEnabled(aiEligible());
  if(png==null||!aiFrozen||!harness.hasPending()||!aiEligible()||
    hash!=aiExpectedHash){
   harness.stopForEpoch(frameEpoch.token(),"STOP");aiTicketNonce=null;
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
  if(task!=null&&task.token.equals(token)&&task.epoch.equals(frameEpoch.token())){
   intent.putExtra(ConsoleAgentContract.TASK_X,task.x);
   intent.putExtra(ConsoleAgentContract.TASK_Y,task.y);
  }
  intent.putExtra(NesFrameTransfer.EXTRA_PNG,png);
  try{startActivity(intent);}
  catch(RuntimeException failure){
   harness.stopForEpoch(frameEpoch.token(),"STOP");aiTicketNonce=null;state.setText("STOP: ModelLab не открылся");
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
  if(reply==null)return;
  if(ConsoleAgentContract.DASHBOARD_STOP.equals(reply.getAction())){
   stopCpuExperiment("STOP: команда с единой панели Harness");
   return;
  }
  if(ConsoleAgentContract.CANCEL.equals(reply.getAction())){
   // Cancellation can only revoke a current local one-time capability; it
   // never authorizes a button. No response from stale ModelLab is replayed.
   String cancelNonce=reply.getStringExtra(ConsoleAgentContract.TOKEN);
   if(ConsoleAgentContract.tokenValid(cancelNonce)&&
      cancelNonce.equals(aiTicketNonce)){
    harness.stopForEpoch(frameEpoch.token(),"STOP");aiTicketNonce=null;queuedAiButton=null;aiSnapshot=false;
    if(aiObserveButton!=null)aiObserveButton.setEnabled(homebrewRom&&pageLoaded);
    state.setText("STOP: ModelLab отменил запрос; NES-контроллер освобождён");
   }
   return;
  }
  if(!ConsoleAgentContract.MOVE.equals(reply.getAction()))return;
  String button=reply.getStringExtra(ConsoleAgentContract.BUTTON);
  String nonce=reply.getStringExtra(ConsoleAgentContract.TOKEN);
  if(!ConsoleAgentContract.tokenValid(nonce)||!nonce.equals(aiTicketNonce))return;
  String modelId=reply.getStringExtra(ConsoleAgentContract.MODEL_ID);
  String modelSha=reply.getStringExtra(ConsoleAgentContract.MODEL_SHA);
  int proposedFrames=reply.getIntExtra(ConsoleAgentContract.FRAMES,ConsoleAgentContract.HOLD_FRAMES);
  boolean student=modelId!=null&&modelId.startsWith("student:");
  boolean durationValid=student?ConsoleAgentContract.studentFramesValid(proposedFrames):
    proposedFrames==ConsoleAgentContract.HOLD_FRAMES;
  boolean admissible=durationValid&&button!=null&&
    (ConsoleAgentContract.playable(button)||"NONE".equals(button))&&
    modelId!=null&&modelId.matches("[A-Za-z0-9:_.-]{1,96}")&&
    modelSha!=null&&modelSha.matches("[a-f0-9]{64}")&&
    harness.consume(nonce,frameEpoch.token(),frames,hash,SystemClock.elapsedRealtime())&&
    getPackageName().startsWith("org.localpilot.android")&&homebrewRom&&
    pageLoaded&&web!=null&&pendingTrial==null&&!experiment.running()&&!aiRunning;
  aiTicketNonce=null;
  if(!admissible){
   harness.stopForEpoch(frameEpoch.token(),"STOP");state.setText("STOP: ИИ-команда устарела, повреждена или ROM изменился");
   return;
  }
  aiModelId=modelId;aiModelSha=modelSha;aiHoldFrames=proposedFrames;
  if("NONE".equals(button)){
   harness.stopForEpoch(frameEpoch.token(),"MODEL_SELECTED_NONE");
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
  if(!aiFrozen||!aiEligible()||!frameEpoch.current(aiEpoch)||hash!=aiExpectedHash||
    SystemClock.elapsedRealtime()-aiIssuedAt>ConsoleAgentContract.TTL_MS){
   harness.stopForEpoch(frameEpoch.token(),"STALE_EMULATOR_FRAME");
   state.setText("STOP: новый кадр не совпадает с наблюдением ИИ");
   return;
  }
  HarnessCoordinator.Actor owner=aiModelId.startsWith("student:")?
    HarnessCoordinator.Actor.STUDENT:HarnessCoordinator.Actor.GGUF;
  if(!beginTrial(button,owner,aiHoldFrames)){
   harness.stopForEpoch(frameEpoch.token(),"PERMIT_DENIED");
   state.setText("STOP: Harness не выдал право на ввод NES");return;
  }
  aiRunning=true;
  state.setText("ИИ "+aiModelId+" · STEP_MODE "+button+" на "+aiHoldFrames+" реальных кадров; проверяю");
  final String executingNonce=pendingPermit.nonce;
  web.evaluateJavascript("window.retroAiStep("+
    org.json.JSONObject.quote(button)+","+aiHoldFrames+","+
    org.json.JSONObject.quote(pendingPermit.nonce)+")",ack->{
     TrialPending pending=pendingTrial;
     if(pending==null||!pending.button.equals(button))return;
     if(!"true".equals(ack))stopCpuExperiment("STOP: JSNES отказал в точном шаге");
     // onStepAction supplies the release ACK before the final fresh frame.
     // Do not overwrite it with a delayed evaluateJavascript callback.
  });
  web.postDelayed(()->{
   if(pendingPermit!=null&&pendingPermit.nonce.equals(executingNonce)&&aiRunning)
    stopCpuExperiment("STOP: нет подтверждения полного NES-шага за 2500 мс");
  },2500);
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
  NesRomPreflight.Result preflight=RetroEmulatorCatalog.preflight(
    dendy?RetroEmulatorCatalog.System.DENDY:RetroEmulatorCatalog.System.NES,rom);
  if(!preflight.runnable()){
   harness.stopForEpoch(frameEpoch.token(),"NES_PREFLIGHT_"+preflight.code());
   state.setText("ROM не запущен: "+preflight.code()+
      (preflight.mapper()>=0?" · mapper "+preflight.mapper():""));
   return;
  }
  String encoded=Base64.encodeToString(rom,Base64.NO_WRAP);
  String session=frameEpoch.begin();
  harness.reset(session,HarnessCoordinator.Mode.REALTIME_MODE);
  pendingPermit=null;pendingGoal=null;taskObservation=null;aiFrozen=false;
  frames=hash=0;touches.set(0);
  ++experimentGeneration;experiment.stop();
  snapshotGate.cancel();exportTrial=null;
  harness.stopForEpoch(frameEpoch.token(),"STOP");aiTicketNonce=null;queuedAiButton=null;aiRunning=false;aiSnapshot=false;
  if(homebrew&&!java.util.Arrays.equals(rom,HomebrewNes.cartridge())){
   harness.stopForEpoch(frameEpoch.token(),"HOME_BREW_ROM_MISMATCH");
   state.setText("STOP: только точная встроенная ROM даёт RAM-награду");return;
  }
  homebrewRom=homebrew;pendingTrial=null;readyTrial=null;
  if(shareTrial!=null)shareTrial.setEnabled(false);
  if(cpuAgentButton!=null)cpuAgentButton.setEnabled(false);
  if(cpuBatchButton!=null)cpuBatchButton.setEnabled(false);
  web.evaluateJavascript("window.retroLoadRom("+org.json.JSONObject.quote(encoded)+","
    +(dendy?"true":"false")+","+(homebrew?"true":"false")+","
    +org.json.JSONObject.quote(session)+")",success->{
   if(closed||!frameEpoch.current(session))return;
   boolean launched="true".equals(success);
   if(!launched){frameEpoch.cancel();harness.stopForEpoch(frameEpoch.token(),"ROM_LOAD_FAILED");}
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
  if(pendingTrial!=null){harness.stopForEpoch(frameEpoch.token(),"NES_PAUSED_IN_FLIGHT");pendingPermit=null;}
  pendingTrial=null; // Keep a pending ModelLab ticket, but not held input.
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
  closed=true;harness.stopForEpoch(frameEpoch.token(),"NES_DESTROYED");pendingPermit=null;aiFrozen=false;
  queuedAiButton=null;aiRunning=false;
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
