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
import org.localpilot.shared.NesModelHost;
import org.localpilot.shared.HarnessCoordinator;
import org.localpilot.shared.HarnessDemoWorkflow;
import org.localpilot.shared.NesSaveStore;
import org.localpilot.shared.HomebrewObs64;
import org.localpilot.shared.StudentPolicyNet;
import org.localpilot.shared.LearningCoordinator;
import org.localpilot.shared.VerifiedReplayFilter;
import org.localpilot.shared.VerifiedPpoReplayStore;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.nio.file.Path;
import java.security.SecureRandom;

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
 private TextView learningResult;
 private android.widget.Spinner compactModels;
 private boolean compactModelsInitialized;
 private android.widget.ScrollView detailsPanel;
 private List<NesModelHost.ModelChoice> compactChoices=List.of();

 private Button demoButton,aButton,bButton,shareTrial,cpuAgentButton,cpuBatchButton,stopCpuButton,aiObserveButton,studentStepButton,studentBatchButton;
 private CheckBox exploratoryStopSwitch;
 private final NesExperimentPolicy experiment=new NesExperimentPolicy();
 private long experimentGeneration;
 private boolean homebrewRom;
 private String pinnedHomebrewRomSha="";
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
 private boolean aiSnapshot,aiRunning,aiFrozen,studentStepRequested;
 private StudentPolicyNet studentNet;
 private HarnessCoordinator.EpisodeLease studentLease;
 private final SecureRandom studentRandom=new SecureRandom();
 private final ExecutorService studentTrainer=Executors.newSingleThreadExecutor();
 private Future<?> studentTraining;
 private final ArrayList<VerifiedReplayFilter.Step> studentRollout=new ArrayList<>();
 private int studentRemaining;
 private String studentBatchEpoch="";
 private long studentBatchStopRevision;
 private String aiTicketNonce,queuedAiButton,aiModelId="",aiModelSha="",aiEpoch="";
 private int aiExpectedHash,aiHoldFrames=ConsoleAgentContract.HOLD_FRAMES;
 private HarnessCoordinator.Ticket aiObservationTicket;
 private boolean directModelInference;
 private NesModelHost.ModelIdentity directModelBinding;
 private long aiIssuedAt;
 // Unlike snapshot tokens, frame epochs also fence delayed onFrame/onAgentAction
 // after a ROM switch, failed load, or Android WebView replay.
 private final NesFrameEpoch frameEpoch=new NesFrameEpoch();
 private TrialPending pendingTrial;
 private static final class TrialPending{
  final String button;
  final int frame,hash;
  boolean downAck,upAck,ramProbeSeen;
  int xBefore=-1,xAfter=-1,yBefore=-1,yAfter=-1;
  float studentOldLogProb,studentValue;
  float[] studentState64;
  int studentActionId=-1,studentLegalMask;
  String studentPolicySha;
  TrialPending(String button,int frame,int hash){
   this.button=button;this.frame=frame;this.hash=hash;
  }
 }
 private String requestedAutoWorkflow;
 private boolean autoLaunchArmed,autoStartArmed;
 private int autoGgufRemaining;
 private long lastStatusUiAt;
 private boolean progressBusy;
 private volatile long progressGeneration;
 private int autoplayBatchesRemaining;
 private long progressVerifiedActions;
 private byte[] activeRomBytes;
 private final ExecutorService progressIo=Executors.newSingleThreadExecutor();
 private boolean resumed;
 private boolean pageLoaded;
 private int frames,hash;
 private long lastInputAt;
 private boolean dendy;
 private final AtomicInteger touches=new AtomicInteger();
 @Override public void onCreate(Bundle bundle){
  super.onCreate(bundle);
  dendy=getIntent().getBooleanExtra("dendy",false);
  if(getPackageName().startsWith("org.localpilot.android")){
   String mode=getIntent().getStringExtra(HarnessDemoWorkflow.EXTRA_MODE);
   if(HarnessDemoWorkflow.supported(mode))requestedAutoWorkflow=mode;
  }
  LinearLayout root=new LinearLayout(this);
  root.setOrientation(LinearLayout.VERTICAL);GameLabInsets.install(this,root,8);
  root.setBackgroundColor(HubContract.BACKGROUND);
  TextView label=new TextView(this);
  label.setText(dendy?"LocalPilot · Денди (50 Гц)":"LocalPilot · NES");
  label.setTextColor(HubContract.TEXT);label.setTextSize(21);root.addView(label);
  state=new TextView(this);state.setTextColor(HubContract.WARNING);
  showState("Загрузи свой .nes или скачай проверенный homebrew. Эмуляция работает офлайн, сеть нужна только для загрузки файла.");
  state.setMaxLines(2);state.setEllipsize(android.text.TextUtils.TruncateAt.END);
  root.addView(state,new LinearLayout.LayoutParams(-1,(int)(40*getResources().getDisplayMetrics().density)));
  learningResult=new TextView(this);learningResult.setTextColor(HubContract.TEXT);
  learningResult.setText("Учебный ROM: квадрат — управляемый объект. PPO учится движению вправо по RAM; импортированные игры требуют своих адаптеров награды.");

  // Fixed-height scrolling controls preserve the real NES viewport on API
  // 30/35/36, while leaving the STOP button and physical controller visible.
  android.widget.ScrollView actionScroll=new android.widget.ScrollView(this);
  detailsPanel=actionScroll;actionScroll.setVisibility(getPackageName().startsWith("org.localpilot.android")?View.GONE:View.VISIBLE);
  actionScroll.setFillViewport(false);
  LinearLayout actionPanel=new LinearLayout(this);
  actionPanel.setOrientation(LinearLayout.VERTICAL);
  actionScroll.addView(actionPanel);
  actionPanel.addView(learningResult);
  root.addView(actionScroll,new LinearLayout.LayoutParams(-1,
    (int)(160*getResources().getDisplayMetrics().density)));
  Button open=new Button(this);open.setText("Выбрать картридж (.nes)");
  actionPanel.addView(open);
  open.setOnClickListener(v->{
   Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*")
     .addCategory(Intent.CATEGORY_OPENABLE);
   startActivityForResult(pick,CHOOSE_ROM);
  });
  Button curated=new Button(this);
  curated.setText("Каталог бесплатных homebrew NES-ROM");
  actionPanel.addView(curated);
  curated.setOnClickListener(v->{
   try{
    Intent browser=new Intent(Intent.ACTION_VIEW,
      Uri.parse("https://itch.io/games/free/tag-homebrew/tag-nes-rom"));
    startActivity(browser);
    showState("Скачай авторский .nes в браузере и выбери его кнопкой «Выбрать картридж».");
   }catch(android.content.ActivityNotFoundException missing){
    showState("Браузер недоступен. Импортируй NES ROM из файлов.");
   }
  });
  Button download=new Button(this);
  download.setText("Скачать и запустить VOID LANCER · MIT · 41 КБ");
  actionPanel.addView(download);
  download.setOnClickListener(v->{
   if(closed)return;
   download.setEnabled(false);
   final String previousRom=frameEpoch.token();
   showState("Проверяю локальную копию или скачиваю подлинный VOID LANCER...");
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
     if(problem!=null){showState(problem+" · можно скачать ROM через браузер");return;}
     // A manual/demo ROM selected while the download was running wins.
     if(!previousRom.equals(frameEpoch.token())){
      showState("ROM загружен и сохранён. Текущую игру не заменяю.");return;
     }
     if(!resumed||!pageLoaded){
      showState("ROM сохранён. Вернись в NES и повторно нажми «Скачать и запустить».");return;
     }
     launchRom(accepted,false);
     showState("VOID LANCER · реальный NES ROM · SHA-256 OK · ручное управление.");
    });
   });
  });
  demoButton=new Button(this);Button demo=demoButton;
  demo.setText("Запустить тестовую NES-игру");
  actionPanel.addView(demo);
  demo.setOnClickListener(v->launchRom(HomebrewNes.cartridge(),true));
  shareTrial=new Button(this);
  shareTrial.setText("NES → ModelLab · передать проверенный эпизод");
  shareTrial.setEnabled(false);
  shareTrial.setOnClickListener(v->sendTrialToModelLab());
  actionPanel.addView(shareTrial);
  aiObserveButton=new Button(this);
  aiObserveButton.setText("🧠 ИИ-агент · STEP_MODE · GGUF → 16 кадров NES");
  aiObserveButton.setEnabled(false);
  aiObserveButton.setOnClickListener(v->requestAiObservation());
  actionPanel.addView(aiObserveButton);
  studentStepButton=new Button(this);
  studentStepButton.setText("🧠 StudentPolicy · 1 ход NES (64→64→64→кнопка)");
  studentStepButton.setEnabled(false);
  studentStepButton.setOnClickListener(v->requestStudentAction());
  actionPanel.addView(studentStepButton);
  studentBatchButton=new Button(this);
  studentBatchButton.setText("🧪 StudentPolicy · 16 реальных NES-шагов → PPO-кандидат");
  studentBatchButton.setEnabled(false);
  studentBatchButton.setOnClickListener(v->startStudentBatch());
  actionPanel.addView(studentBatchButton);
  Button autoplay=new Button(this);
  autoplay.setText("▶ Автоигра Student · 256 ходов с сохранением (встроенная ROM)");
  autoplay.setOnClickListener(v->{
   if(progressBusy||studentRemaining>0||aiRunning||harness.hasPending()||harness.actionInFlight()||
      studentTraining!=null&&!studentTraining.isDone())return;
   if(homebrewRom&&frames>=15){if(armAutoplay())startStudentBatch();}
   else {requestedAutoWorkflow=HarnessDemoWorkflow.STUDENT_AUTOPLAY;tryAutoWorkflowLaunch();}
  });
  actionPanel.addView(autoplay);
  Button saveGame=new Button(this);saveGame.setText("Сохранить игру и приостановить");
  saveGame.setOnClickListener(v->freezeAndSave());actionPanel.addView(saveGame);
  Button resumeGame=new Button(this);resumeGame.setText("Продолжить сохранённую автоигру");
  resumeGame.setOnClickListener(v->restoreProgress(true));actionPanel.addView(resumeGame);
  Button viewSave=new Button(this);viewSave.setText("Загрузить сохранение без автоигры");
  viewSave.setOnClickListener(v->restoreProgress(false));actionPanel.addView(viewSave);
  Button resumeRealtime=new Button(this);
  resumeRealtime.setText("▶ REALTIME_MODE · продолжить ручную NES");
  resumeRealtime.setOnClickListener(v->{
   if(!aiFrozen||!resumed||!pageLoaded||web==null){
    showState("NES уже выполняется в обычном режиме или не запущена");return;
   }
   stopCpuExperiment("STOP: переход STEP → REALTIME");
   String currentEpoch=frameEpoch.token();
   web.evaluateJavascript("window.retroExitStepMode()",reply->{
    if("true".equals(reply)&&frameEpoch.current(currentEpoch)&&
       harness.changeMode(currentEpoch,HarnessCoordinator.Mode.REALTIME_MODE)){
     aiFrozen=false;
     showState("REALTIME_MODE: автоматические шаги прекращены, ручное управление доступно");
    }else showState("STOP: не удалось возобновить реальное время NES");
   });
  });
  actionPanel.addView(resumeRealtime);
  cpuAgentButton=new Button(this);
  cpuAgentButton.setText("CPU NES-агент · тестовый ход A (16 кадров)");
  cpuAgentButton.setEnabled(false);
  cpuAgentButton.setOnClickListener(v->{
   if(experiment.running())return;
   runHomebrewCpuAgent();
  });
  actionPanel.addView(cpuAgentButton);
  exploratoryStopSwitch=new CheckBox(this);
  exploratoryStopSwitch.setText(
    "Лаборатория NES: игнорировать авто-STOP при неизменном кадре (только тестовый ROM)");
  exploratoryStopSwitch.setChecked(false);
  exploratoryStopSwitch.setOnCheckedChangeListener((view,checked)->{
   if(experiment.running())stopCpuExperiment("Режим изменён: серия остановлена");
  });
  actionPanel.addView(exploratoryStopSwitch);
  cpuBatchButton=new Button(this);
  cpuBatchButton.setText("Серия CPU-агента · до "+NesExperimentPolicy.MAX_STEPS+" ходов");
  cpuBatchButton.setEnabled(false);
  cpuBatchButton.setOnClickListener(v->startCpuExperiment());
  actionPanel.addView(cpuBatchButton);
  if(getPackageName().startsWith("org.localpilot.android")){
   Button modelTools=new Button(this);modelTools.setText("Импорт и проверка моделей");
   modelTools.setOnClickListener(v->startActivity(new Intent().setClassName(getPackageName(),"org.localpilot.android.ModelLabActivity")));
   actionPanel.addView(modelTools);
   Button journal=new Button(this);journal.setText("Журнал и диагностика");
   journal.setOnClickListener(v->startActivity(new Intent().setClassName(getPackageName(),"org.localpilot.android.HarnessDashboardActivity")));
   actionPanel.addView(journal);
  }
  LinearLayout compact=new LinearLayout(this);compact.setOrientation(LinearLayout.VERTICAL);
  LinearLayout selectionRow=new LinearLayout(this);
  compactModels=new android.widget.Spinner(this);compactModels.setContentDescription("Модель агента NES");
  selectionRow.addView(compactModels,new LinearLayout.LayoutParams(0,-2,1));
  Button more=new Button(this);more.setText("Ещё");more.setContentDescription("Открыть дополнительные настройки");
  more.setOnClickListener(v->{boolean expanded=actionScroll.getVisibility()!=View.VISIBLE;
   actionScroll.setVisibility(expanded?View.VISIBLE:View.GONE);
   more.setText(expanded?"Свернуть":"Ещё");more.setContentDescription(expanded?"Скрыть дополнительные настройки":"Открыть дополнительные настройки");
  });selectionRow.addView(more);compact.addView(selectionRow);
  LinearLayout actions=new LinearLayout(this);
  Button gameMenu=new Button(this);gameMenu.setText("Игра");
  gameMenu.setOnClickListener(v->{android.widget.PopupMenu menu=new android.widget.PopupMenu(this,gameMenu);
   menu.getMenu().add("VOID LANCER").setOnMenuItemClickListener(item->{if(download.isEnabled())download.performClick();return true;});
   menu.getMenu().add("Продолжить сохранение").setOnMenuItemClickListener(item->{viewSave.performClick();return true;});
   menu.getMenu().add("Открыть .nes").setOnMenuItemClickListener(item->{open.performClick();return true;});
   menu.getMenu().add("Учебный ROM").setOnMenuItemClickListener(item->{demo.performClick();return true;});menu.show();
  });actions.addView(gameMenu,new LinearLayout.LayoutParams(0,-2,1));
  Button run=new Button(this);run.setText("Агент");run.setContentDescription("Запустить выбранного агента NES");
  run.setOnClickListener(v->{
   int pos=compactModels.getSelectedItemPosition();
   if(pos==0)requestCompactWorkflow(HarnessDemoWorkflow.STUDENT_AUTOPLAY,null);
   else if(pos>0&&pos<=compactChoices.size())requestCompactWorkflow(HarnessDemoWorkflow.GGUF_PLAY_4,compactChoices.get(pos-1).identity());
  });actions.addView(run,new LinearLayout.LayoutParams(0,-2,1));
  Button train=new Button(this);train.setText("Учить Student");train.setTextSize(11);
  train.setOnClickListener(v->requestCompactWorkflow(HarnessDemoWorkflow.STUDENT_TRAIN_16,null));
  actions.addView(train,new LinearLayout.LayoutParams(0,-2,1));
  Button save=new Button(this);save.setText("Сохранить");save.setTextSize(11);save.setOnClickListener(v->freezeAndSave());
  actions.addView(save,new LinearLayout.LayoutParams(0,-2,1));compact.addView(actions);
  TextView scope=new TextView(this);scope.setText("Агент и обучение: только учебный ROM · CPU");scope.setTextColor(HubContract.SECONDARY);scope.setTextSize(11);compact.addView(scope);
  if(getPackageName().startsWith("org.localpilot.android"))root.addView(compact,2);
  stopCpuButton=new Button(this);
  stopCpuButton.setText(getPackageName().startsWith("org.localpilot.android")?"STOP":"STOP · немедленно остановить CPU-агента");
  stopCpuButton.setOnClickListener(v->stopCpuExperiment("STOP: агент остановлен пользователем"));
  root.addView(stopCpuButton); // Always visible, never hidden by scrolling
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
    if(!pageLoaded)showState("ROM остановлен: ненадёжный источник страницы");
    else tryAutoWorkflowLaunch();
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
     if(autoStartArmed&&count>=15&&homebrewRom&&!harness.hasPending()){
      autoStartArmed=false;
      if(autoGgufRemaining>0)requestAiObservation();
      else startStudentBatch();
     }
     if(count%60==0&&!aiRunning&&!experiment.running()&&studentRemaining==0&&!autoStartArmed)
      showState("Кадров "+frames+" · экран CRC="+Integer.toHexString(hash)
       +" · касаний "+touches.get()+" · только эмуляция (не результат Маркета)");
    });
   }
   @JavascriptInterface public void onHomebrewRamProgress(String name,int started,
        int finished,int xBefore,int xAfter,String session,int yBefore,int yAfter,String nonce){
    runOnUiThread(()->{
     // The WebView bridge is not self-authenticating. Stage RAM only while
     // the exact built-in ROM and a single in-flight GGUF permit are active.
     if(pendingPermit==null||!pendingPermit.nonce.equals(nonce)||!pendingPermit.epoch.equals(session))return;
     if(closed||!resumed||!frameEpoch.current(session)||!homebrewRom||
        !aiFrozen||!aiRunning)return;
     if(pendingPermit.actor!=HarnessCoordinator.Actor.PPO_STUDENT)return;
     TrialPending current=pendingTrial;
     if(current==null||current.ramProbeSeen||!current.button.equals(name)||
        current.frame!=started||finished-started!=HomebrewRamReward.HOLD_FRAMES||
        xBefore<0||xBefore>255||xAfter<0||xAfter>255||
        yBefore<0||yBefore>255||yAfter<0||yAfter>255){
      stopCpuExperiment("STOP: NES RAM evidence mismatch");return;
     }
     current.ramProbeSeen=true;
     current.xBefore=xBefore;current.xAfter=xAfter;
     current.yBefore=yBefore;current.yAfter=yAfter;
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
     if(pendingPermit.actor==HarnessCoordinator.Actor.PPO_STUDENT)return;
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
        count<15||pixelsHash==0||harness.actionInFlight()||
        harness.hasPending()&&!harness.ownsEpisode(studentLease)){
      stopCpuExperiment("STOP: неподтверждённый замороженный NES-кадр");
      return;
     }
     if(count<frames||(count==frames&&pixelsHash!=hash)||
        (count>frames&&!frameEpoch.accept(session,count))){
      stopCpuExperiment("STOP: STEP epoch/frame/hash не совпадают");
      return;
     }
     frames=count;hash=pixelsHash;
     if(!(harness.ownsEpisode(studentLease)?harness.changeEpisodeMode(studentLease):
          harness.changeMode(session,HarnessCoordinator.Mode.STEP_MODE))){
      stopCpuExperiment("STOP: другой владелец NES-ввода");return;
     }
     aiFrozen=true;
     if(studentStepRequested){
      studentStepRequested=false;
      requestStudentFromFrozenRam(session,count,pixelsHash);
     }else requestFrozenAiObservation();
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
    runOnUiThread(()->showState(message));
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
      showState("STEP_MODE заморозил игру; нажми ▶ REALTIME_MODE для ручного управления");
      return true;
     }
     if(aiRunning||experiment.running()||harness.hasPending()){
      stopCpuExperiment("STOP: ручной ввод отозвал текущий агентский permit");return true;
     }
     if(type==MotionEvent.ACTION_DOWN){
      if(!beginTrial(name,HarnessCoordinator.Actor.MANUAL)){
       showState("STOP: контроллер занят другим вводом");return true;
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
 /** Avoid a content-change storm while preserving immediate STOP/error feedback. */
 private void showState(String message){
  if(state==null)return;
  long now=SystemClock.elapsedRealtime();
  if(autoplayBatchesRemaining>0&&!message.startsWith("STOP")&&now-lastStatusUiAt<2000)return;
  lastStatusUiAt=now;state.setText(message);
 }
 private boolean armAutoplay(){
  try{
   int goals=ConsoleExperienceMemory.readHomebrewGoals(getFilesDir().toPath()).size();
   int replay=VerifiedPpoReplayStore.summary(progressVault()).batches();
   autoplayBatchesRemaining=Math.min(HarnessDemoWorkflow.AUTOPLAY_BATCHES,
     Math.min((ConsoleExperienceMemory.HOMEBREW_GOAL_MAX_ROWS-goals)/VerifiedReplayFilter.MIN_STEPS,
       VerifiedPpoReplayStore.MAX_BATCHES-replay));
   if(autoplayBatchesRemaining<=0){
    autoplayBatchesRemaining=0;
    showState("Опыт сохранён, журнал заполнен: перед новой автоигрой требуется экспорт. Ручная игра и загрузка сохранения доступны.");
    return false;
   }
   return true;
  }catch(IOException|RuntimeException invalid){stopCpuExperiment("STOP: свободное место журнала не подтверждено");return false;}
 }
 private void queueAutoplayBatch(String epoch,long revision){
  final long serial=progressGeneration;
  web.postDelayed(()->{
   if(!closed&&resumed&&autoplayBatchesRemaining>0&&serial==progressGeneration&&
      frameEpoch.current(epoch)&&harness.stopRevision()==revision)startStudentBatch();
  },80);
 }
 private Path progressVault(){return new File(getFilesDir(),"localpilot_vault").toPath();}
 private boolean progressIdle(){
  return !closed&&resumed&&pageLoaded&&web!=null&&!progressBusy&&
   pendingTrial==null&&!aiRunning&&!studentStepRequested&&studentRemaining==0&&
   !experiment.running()&&!harness.hasPending()&&!harness.actionInFlight()&&
   (studentTraining==null||studentTraining.isDone());
 }
 private void freezeAndSave(){
  if(studentRemaining>0||progressBusy||studentTraining!=null&&!studentTraining.isDone()){
   autoplayBatchesRemaining=0;showState("Приостановлю после записи текущей серии; STOP доступен немедленно");return;
  }
  if(!progressIdle()||activeRomBytes==null){showState("Сохранение ожидает завершения действия/серии");return;}
  autoplayBatchesRemaining=0;
  String epoch=frameEpoch.token();long revision=harness.stopRevision(),serial=++progressGeneration;
  progressBusy=true;
  web.evaluateJavascript("window.retroFreezeForSave()",reply->{
   if(closed||!resumed||serial!=progressGeneration||!frameEpoch.current(epoch)||harness.stopRevision()!=revision)return;
   progressBusy=false;
   try{
    org.json.JSONObject result=new org.json.JSONObject(reply);
    int f=result.getInt("frame"),h=result.getInt("hash");
    if(!epoch.equals(result.getString("session"))||f<frames||f<15||h==0||
       (f>frames&&!frameEpoch.accept(epoch,f))||
       !harness.changeMode(epoch,HarnessCoordinator.Mode.STEP_MODE))throw new IOException("Freeze invalid");
    frames=f;hash=h;aiFrozen=true;persistProgress(()->{});
   }catch(IOException|org.json.JSONException|RuntimeException invalid){stopCpuExperiment("STOP: не удалось заморозить сохранение");}
  });
  web.postDelayed(()->{
   if(progressBusy&&serial==progressGeneration)stopCpuExperiment("STOP: таймаут заморозки сохранения");
  },5000);
 }
 private void readProgressChunks(String token,int offset,int length,StringBuilder text,
     long serial,String epoch,long revision,java.util.function.Consumer<String> complete){
  if(closed||!resumed||serial!=progressGeneration||!frameEpoch.current(epoch)||harness.stopRevision()!=revision)return;
  if(offset==length){web.evaluateJavascript("window.retroReleaseSave("+org.json.JSONObject.quote(token)+")",null);complete.accept(text.toString());return;}
  web.evaluateJavascript("window.retroReadSaveChunk("+org.json.JSONObject.quote(token)+","+offset+")",reply->{
   if(closed||!resumed||serial!=progressGeneration||!frameEpoch.current(epoch)||harness.stopRevision()!=revision)return;
   try{
    Object decoded=new org.json.JSONTokener(reply==null?"null":reply).nextValue();
    if(!(decoded instanceof String chunk)||chunk.length()!=Math.min(65536,length-offset))throw new IOException("CHUNK_LENGTH");
    text.append(chunk);readProgressChunks(token,offset+chunk.length(),length,text,serial,epoch,revision,complete);
   }catch(IOException|org.json.JSONException|RuntimeException invalid){stopCpuExperiment("STOP: неверный фрагмент сохранения NES");}
  });
 }
 private void persistProgress(Runnable afterSave){
  if(!progressIdle()||!aiFrozen||activeRomBytes==null){stopCpuExperiment("STOP: неустойчивая граница сохранения");return;}
  final String epoch=frameEpoch.token();final long revision=harness.stopRevision(),serial=++progressGeneration;
  final String token="save-"+epoch+"-"+serial;
  final int expectedFrame=frames,expectedHash=hash;
  final byte[] rom=activeRomBytes.clone();final long actions=progressVerifiedActions;
  final String policy=studentNet==null?"":studentNet.sha256();final boolean savedDendy=dendy;
  progressBusy=true;
  web.evaluateJavascript("window.retroPrepareSave("+org.json.JSONObject.quote(token)+")",reply->{
   if(closed||!resumed||serial!=progressGeneration||!frameEpoch.current(epoch)||harness.stopRevision()!=revision)return;
   try{
    org.json.JSONObject meta=new org.json.JSONObject(reply);
    if(!meta.getBoolean("ready"))throw new IOException("NOT_QUIESCENT");
    int length=meta.getInt("length");
    if(length<1||length>4*1024*1024||meta.getInt("frame")!=expectedFrame||meta.getInt("hash")!=expectedHash)
     throw new IOException("SAVE_METADATA_MISMATCH");
    readProgressChunks(token,0,length,new StringBuilder(length),serial,epoch,revision,snapshot->{
     try{
      org.json.JSONObject result=new org.json.JSONObject(snapshot);
      if(result.getInt("frame")!=expectedFrame||result.getInt("hash")!=expectedHash||
         frames!=expectedFrame||hash!=expectedHash)throw new IOException("NES changed during save");
      NesSaveStore.Save save=new NesSaveStore.Save(rom,snapshot,expectedFrame,expectedHash,savedDendy,actions,policy);
      progressIo.execute(()->{
       try{
        if(serial!=progressGeneration)return;
        NesSaveStore.save(progressVault(),save);
        runOnUiThread(()->{
         if(closed||!resumed||serial!=progressGeneration||!frameEpoch.current(epoch)||harness.stopRevision()!=revision)return;
         progressBusy=false;
         if(shareTrial!=null&&autoplayBatchesRemaining==0)shareTrial.setEnabled(readyTrial!=null);
         showState("Прогресс сохранён · NES кадр "+expectedFrame+" · подтверждённых действий "+actions+
          " · авто-серий осталось "+autoplayBatchesRemaining+" · PPO-кандидат отдельно от активных весов");
         afterSave.run();
        });
       }catch(IOException|RuntimeException invalid){runOnUiThread(()->{
        if(serial==progressGeneration)stopCpuExperiment("STOP: сохранение не записано: "+invalid.getClass().getSimpleName());
       });}
      });
     }catch(IOException|org.json.JSONException|RuntimeException invalid){stopCpuExperiment("STOP: собранный снимок NES недостоверен");}
    });
   }catch(IOException|org.json.JSONException|RuntimeException invalid){
    String code=invalid instanceof IOException?invalid.getMessage():invalid.getClass().getSimpleName();
    stopCpuExperiment("STOP: метаданные сохранения NES: "+code+" · ответ "+(reply==null?-1:reply.length())+" символов");
   }
  });
  web.postDelayed(()->{
   if(progressBusy&&serial==progressGeneration)stopCpuExperiment("STOP: таймаут сохранения NES");
  },5000);
 }
 private void restoreProgress(boolean auto){
  if(!progressIdle()){showState("Сначала завершите действие/обучение или нажмите STOP");return;}
  final long serial=++progressGeneration;final String priorEpoch=frameEpoch.token();progressBusy=true;
  progressIo.execute(()->{
   try{
    NesSaveStore.Save saved=NesSaveStore.load(progressVault());
    if(saved==null)throw new IOException("Нет сохранённой игры");
    runOnUiThread(()->{
     if(closed||!resumed||serial!=progressGeneration||!priorEpoch.equals(frameEpoch.token()))return;
     progressBusy=false;dendy=saved.dendy();
     boolean homebrew=java.util.Arrays.equals(saved.rom(),HomebrewNes.cartridge());
     if(auto&&!homebrew){showState("Сохранён внешний ROM: загрузите без автоигры; доверенной политики нет");return;}
     launchRom(saved.rom(),homebrew,saved,auto);
    });
   }catch(IOException|RuntimeException invalid){runOnUiThread(()->{
    if(serial==progressGeneration){progressBusy=false;showState("Сохранение не загружено: "+invalid.getClass().getSimpleName());}
   });}
  });
 }
 private void writeRestoreChunks(String token,String text,int offset,long serial,String epoch,
     long revision,java.util.function.Consumer<String> complete){
  if(closed||!resumed||serial!=progressGeneration||!frameEpoch.current(epoch)||harness.stopRevision()!=revision)return;
  if(offset==text.length()){
   web.evaluateJavascript("window.retroCommitRestore("+org.json.JSONObject.quote(token)+")",complete::accept);return;
  }
  int end=Math.min(offset+65536,text.length());
  web.evaluateJavascript("window.retroWriteRestoreChunk("+org.json.JSONObject.quote(token)+","+offset+","+
   org.json.JSONObject.quote(text.substring(offset,end))+")",reply->{
   if(closed||!resumed||serial!=progressGeneration||!frameEpoch.current(epoch)||harness.stopRevision()!=revision)return;
   if(!"true".equals(reply)){stopCpuExperiment("STOP: NES отверг фрагмент восстановления");return;}
   writeRestoreChunks(token,text,end,serial,epoch,revision,complete);
  });
 }
 private void applyRestoredProgress(NesSaveStore.Save saved,String loadEpoch,boolean auto){
  final long serial=++progressGeneration;progressBusy=true;
  final String epoch=frameEpoch.begin();
  harness.reset(epoch,HarnessCoordinator.Mode.STEP_MODE);
  final long revision=harness.stopRevision();
  final String token="restore-"+epoch+"-"+serial;
  web.evaluateJavascript("window.retroBeginRestore("+org.json.JSONObject.quote(token)+","+
   org.json.JSONObject.quote(loadEpoch)+","+org.json.JSONObject.quote(epoch)+","+saved.coreState().length()+")",begin->{
   if(closed||!resumed||serial!=progressGeneration||!frameEpoch.current(epoch)||harness.stopRevision()!=revision)return;
   if(!"true".equals(begin)){stopCpuExperiment("STOP: NES не подготовил восстановление");return;}
   writeRestoreChunks(token,saved.coreState(),0,serial,epoch,revision,reply->{
   if(closed||!resumed||serial!=progressGeneration||!frameEpoch.current(epoch))return;
   try{
    org.json.JSONObject restored=new org.json.JSONObject(reply);
    if(!epoch.equals(restored.getString("session"))||restored.getInt("frame")!=saved.frame()||
       restored.getInt("savedHash")!=saved.frameHash()||restored.getInt("hash")==0||!frameEpoch.accept(epoch,saved.frame()))throw new IOException("Restored frame mismatch");
    frames=saved.frame();hash=restored.getInt("hash");aiFrozen=true;
    progressVerifiedActions=saved.verifiedActions();progressBusy=false;
    showState("Сохранение загружено · кадр "+frames+" · действия "+progressVerifiedActions+" · новая эпоха");
    if(auto){
     if(saved.policySha256().isEmpty()||!activeStudent().sha256().equals(saved.policySha256()))throw new IOException("Active policy differs from saved session");
     if(armAutoplay())queueAutoplayBatch(epoch,harness.stopRevision());
    }
   }catch(IOException|org.json.JSONException|RuntimeException invalid){
    progressBusy=false;stopCpuExperiment("STOP: восстановление NES не подтверждено");
   }
   });
  });
  web.postDelayed(()->{
   if(progressBusy&&serial==progressGeneration)stopCpuExperiment("STOP: таймаут восстановления NES");
  },5000);
 }
 private void publishEvidence(){
  if(resumed&&demoButton!=null)
   GameLabEvidenceProvider.nes(dendy,frames,hash,touches.get(),demoButton,aButton,bButton);
 }
 /** Launch the actual bundled NES cartridge and existing actor, not a demo counter. */
 private void tryAutoWorkflowLaunch(){
  if(closed||!resumed||!pageLoaded||requestedAutoWorkflow==null||web==null)return;
  String mode=requestedAutoWorkflow;requestedAutoWorkflow=null;
  if(!HarnessDemoWorkflow.supported(mode))return;
  if(harness.hasPending()||aiRunning||experiment.running()||pendingTrial!=null){
   showState("STOP: NES занят. Автостарт отклонён");return;
  }
  if(HarnessDemoWorkflow.GGUF_PLAY_4.equals(mode)){
   if(!getPackageName().startsWith("org.localpilot.android")){
    showState("STOP: GGUF-цикл доступен только внутри единого APK");return;
   }
   autoGgufRemaining=HarnessDemoWorkflow.GGUF_MAX_STEPS;
  }else autoGgufRemaining=0;
  if(HarnessDemoWorkflow.RESUME_AUTOPLAY.equals(mode)){restoreProgress(true);return;}
  autoplayBatchesRemaining=0;
  if(HarnessDemoWorkflow.STUDENT_AUTOPLAY.equals(mode)&&!armAutoplay())return;
  autoLaunchArmed=true;
  showState("Harness: загружаю настоящий встроенный NES-ROM; проверю кадр перед вводом");
  launchRom(HomebrewNes.cartridge(),true);
 }
 private void startCpuExperiment(){
  if(progressBusy||aiFrozen){showState("Сначала выйди из STEP_MODE/сохранения");return;}
  if(experiment.running())return;
  if(progressBusy||!resumed||!homebrewRom||!pageLoaded||frames<15||hash==0){
   showState("Серия доступна только внутри запущенной тестовой NES-игры");
   return;
  }
  experiment.start(true,exploratoryStopSwitch!=null&&exploratoryStopSwitch.isChecked());
  ++experimentGeneration;
  if(cpuBatchButton!=null)cpuBatchButton.setEnabled(false);
  if(cpuAgentButton!=null)cpuAgentButton.setEnabled(false);
  runHomebrewCpuAgent();
 }
 private void cancelDirectInference(){
  if(directModelInference&&getApplication() instanceof NesModelHost host&&aiTicketNonce!=null)
   host.cancelNesInference(aiTicketNonce);
  directModelInference=false;aiObservationTicket=null;
  directModelBinding=null;
 }
 private void stopCpuExperiment(String reason){
  autoplayBatchesRemaining=0;progressBusy=false;++progressGeneration;
  final String stopEpoch=frameEpoch.token();
  final long stopRevision=harness.requestStop(stopEpoch,"STOP_OR_RECOVERY");
  cancelDirectInference();
  pendingPermit=null;pendingGoal=null;taskObservation=null;
  aiTicketNonce=null;aiSnapshot=false;queuedAiButton=null;aiRunning=false;
  studentStepRequested=false;studentRemaining=0;studentRollout.clear();studentLease=null;
  requestedAutoWorkflow=null;autoLaunchArmed=false;autoStartArmed=false;autoGgufRemaining=0;
  if(studentTraining!=null)studentTraining.cancel(true);
  snapshotGate.cancel();exportTrial=null;
  if(aiObserveButton!=null)aiObserveButton.setEnabled(false);
  if(studentStepButton!=null)studentStepButton.setEnabled(false);
  if(studentBatchButton!=null)studentBatchButton.setEnabled(false);
  ++experimentGeneration;
  experiment.stop();
  pendingTrial=null;
  if(web!=null&&pageLoaded)
   web.evaluateJavascript("window.retroCancelAgent()",ack->{
    if(stopRevision>=0&&frameEpoch.current(stopEpoch)&&"true".equals(ack)&&
        harness.confirmStop(stopEpoch,stopRevision)){
     boolean ready=resumed&&homebrewRom&&pageLoaded&&!closed;
     if(aiObserveButton!=null)aiObserveButton.setEnabled(ready);
     if(studentStepButton!=null)studentStepButton.setEnabled(ready);
     if(studentBatchButton!=null)studentBatchButton.setEnabled(ready);
     if(shareTrial!=null)shareTrial.setEnabled(readyTrial!=null);
     if(cpuBatchButton!=null)cpuBatchButton.setEnabled(ready&&!aiFrozen);
     if(cpuAgentButton!=null)cpuAgentButton.setEnabled(ready&&!aiFrozen);
     if(state!=null)showState(reason+" · освобождение JS-ввода подтверждено");
    }
   });
  if(cpuBatchButton!=null)cpuBatchButton.setEnabled(false);
  if(cpuAgentButton!=null)cpuAgentButton.setEnabled(false);
  if(state!=null)showState(reason+" · ожидаю подтверждение JS STOP");
  if(web!=null)web.postDelayed(()->{
   if(frameEpoch.current(stopEpoch)&&harness.stopRevision()==stopRevision&&
       harness.stopAwaitingConfirmation()&&state!=null)
    showState("STOP: освобождение ввода не подтверждено; новые permit запрещены, требуется перезагрузка ROM");
  },2500);
 }

 /**
  * There is ONE local StudentPolicy file; corrupt weights always fail closed.
  * No separate emulation input owner or duplicate JNI backend is created.
  */
 private StudentPolicyNet activeStudent()throws IOException{
  if(studentNet!=null)return studentNet;
  Path weights=new File(getFilesDir(),
    "localpilot_vault/models/active/student-homebrew-v1.weights.bin").toPath();
  if(Files.exists(weights))studentNet=StudentPolicyNet.load(weights);
  else{
   StudentPolicyNet initial=new StudentPolicyNet(HomebrewObs64.ACTIONS,
      studentRandom.nextLong());
   initial.saveAtomically(weights);
   studentNet=StudentPolicyNet.load(weights);
  }
  if(studentNet.actionCount!=HomebrewObs64.ACTIONS)
   throw new IOException("StudentPolicy action vocabulary mismatch");
  return studentNet;
 }
 private void startStudentBatch(){
  if(progressBusy||studentRemaining>0||studentTraining!=null&&!studentTraining.isDone()||
     studentStepRequested||aiRunning||pendingTrial!=null||harness.hasPending()||
     harness.actionInFlight()||experiment.running()||
     !resumed||!homebrewRom||!pageLoaded||frames<15||hash==0){
   showState("STOP: обучение ожидает свободную NES и неподвижный контроллер");return;
  }
  try{
   activeStudent(); // Fail closed if active Student weights are corrupted.
   studentRollout.clear();
   studentRemaining=VerifiedReplayFilter.MIN_STEPS;
   studentBatchEpoch=frameEpoch.token();
   studentBatchStopRevision=harness.stopRevision();
   requestStudentAction(true);
  }catch(IOException|RuntimeException invalid){
   stopCpuExperiment("STOP: нельзя начать Student batch: "+invalid.getClass().getSimpleName());
  }
 }
 private void requestStudentAction(){requestStudentAction(false);}
 private void requestStudentAction(boolean fromBatch){
  if(fromBatch&&(studentRemaining<=0||!studentBatchEpoch.equals(frameEpoch.token())||
     studentBatchStopRevision!=harness.stopRevision())){
   stopCpuExperiment("STOP: Student batch потерял эпоху или разрешение");return;
  }
  if(!fromBatch&&studentRemaining>0){
   showState("StudentPolicy: серия из 16 шагов уже идёт");return;
  }
  if(studentTraining!=null&&!studentTraining.isDone()){
   showState("STOP: PPO сохраняет кандидат, новый ввод запрещён");return;
  }
  if(progressBusy||closed||!resumed||!homebrewRom||!pageLoaded||web==null||
     frames<15||hash==0||pendingTrial!=null||aiRunning||
     experiment.running()||harness.hasPending()&&!harness.ownsEpisode(studentLease)||harness.actionInFlight()||
     studentStepRequested){
   showState("STOP: StudentPolicy не получила свободный NES-контроллер");
   return;
  }
  try{
   if(studentLease==null)studentLease=harness.reserveEpisode(frameEpoch.token(),
     HarnessCoordinator.PPO_TASK,activeStudent().sha256(),fromBatch?studentRemaining:1);
   if(!harness.ownsEpisode(studentLease))throw new IllegalStateException("PPO_EPISODE_BUSY");
  }catch(IOException|RuntimeException invalid){stopCpuExperiment("STOP: PPO reservation rejected");return;}
  if(aiFrozen)requestStudentFromFrozenRam(frameEpoch.token(),frames,hash);
  else{
   studentStepRequested=true;
   String session=frameEpoch.token();
   final HarnessCoordinator.EpisodeLease freezing=studentLease;
   web.postDelayed(()->{
    if(studentStepRequested&&studentLease==freezing&&harness.ownsEpisode(freezing)&&frameEpoch.current(session))stopCpuExperiment("STOP: PPO freeze timeout");
   },2500);
   web.evaluateJavascript("window.retroEnterStepMode()",accepted->{
    if(studentStepRequested&&frameEpoch.current(session)&&!"true".equals(accepted))
     stopCpuExperiment("STOP: StudentPolicy не смогла заморозить NES");
   });
  }
 }
 private void requestStudentFromFrozenRam(String epoch,int expectedFrame,int expectedHash){
  if(!resumed||!homebrewRom||!aiFrozen||!frameEpoch.current(epoch)||
     frames!=expectedFrame||hash!=expectedHash||pendingTrial!=null||
     (!harness.ownsEpisode(studentLease))||harness.actionInFlight()||aiRunning){
   stopCpuExperiment("STOP: StudentPolicy увидела устаревший кадр");return;
  }
  final HarnessCoordinator.EpisodeLease lease=studentLease;
  final HarnessCoordinator.Ticket observation;
  try{observation=harness.beginEpisodeObservation(lease,expectedFrame,expectedHash,SystemClock.elapsedRealtime());}
  catch(RuntimeException stale){stopCpuExperiment("STOP: PPO observation denied");return;}
  web.postDelayed(()->{
   if(studentLease==lease&&harness.ownsEpisode(lease)&&harness.awaiting(observation))
    stopCpuExperiment("STOP: PPO observation timeout");
  },2500);
  web.evaluateJavascript("window.retroHomebrewState()",result->{
   if(!harness.awaiting(observation))return; // Late reply never revokes another step.
   if(closed||!resumed||!homebrewRom||!aiFrozen||
      !frameEpoch.current(epoch)||frames!=expectedFrame||hash!=expectedHash||
      !harness.ownsEpisode(lease)||harness.actionInFlight()||pendingTrial!=null||aiRunning){
    stopCpuExperiment("STOP: StudentPolicy RAM state устарело");return;
   }
   try{
    org.json.JSONObject observed=new org.json.JSONObject(result);
    if(!epoch.equals(observed.getString("session"))||
       observed.getInt("frame")!=expectedFrame)
     throw new IllegalArgumentException("Stale NES RAM snapshot");
    float[] obs=HomebrewObs64.encode(observed.getInt("x"),observed.getInt("y"));
    int taskMask=HomebrewObs64.taskMask(observed.getInt("x"),observed.getInt("y"),ConsoleAgentContract.HOLD_FRAMES);
    StudentPolicyNet.Decision choice=activeStudent().choose(obs,taskMask,studentRandom);
    if(choice.action()<0||choice.action()>=HomebrewObs64.BUTTONS.length)
     throw new IllegalArgumentException("Illegal StudentPolicy action");
    String button=HomebrewObs64.BUTTONS[choice.action()];
    if(!harness.consume(observation.nonce,epoch,frames,hash,SystemClock.elapsedRealtime()))
     throw new IllegalStateException("PPO stale prepared observation");
    HarnessCoordinator.ActionPermit permit=harness.startEpisodeStep(lease,choice.policySha(),button,
      frames,hash,SystemClock.elapsedRealtime());
    if(permit==null)throw new IllegalStateException("PPO step denied");
    pendingPermit=permit;readyTrial=null;
    if(shareTrial!=null)shareTrial.setEnabled(false);
    pendingTrial=new TrialPending(button,frames,hash);
    final HarnessCoordinator.ActionPermit issued=permit;
    web.postDelayed(()->{
     if(pendingPermit==issued&&frameEpoch.current(epoch))stopCpuExperiment("STOP: PPO step timeout");
    },2500);
    TrialPending trial=pendingTrial;
    trial.studentOldLogProb=choice.oldLogProb();
    trial.studentValue=choice.value();
    trial.studentState64=obs.clone();
    trial.studentActionId=choice.action();
    trial.studentLegalMask=taskMask;
    trial.studentPolicySha=choice.policySha();
    aiModelId="student-homebrew-v1";
    aiModelSha=choice.policySha();
    aiRunning=true;
    showState("StudentPolicy "+button+" · 16 NES-кадров · SHA "+
      choice.policySha().substring(0,12));
    web.evaluateJavascript("window.retroAiStep("+
      org.json.JSONObject.quote(button)+","+ConsoleAgentContract.HOLD_FRAMES+","+
      org.json.JSONObject.quote(pendingPermit.nonce)+")",reply->{
       TrialPending current=pendingTrial;
       if(pendingPermit==issued&&current!=null&&!"true".equals(reply))
        stopCpuExperiment("STOP: JSNES отказал StudentPolicy");
    });
   }catch(IOException|org.json.JSONException|RuntimeException invalid){
    stopCpuExperiment("STOP: StudentPolicy: "+invalid.getClass().getSimpleName());
   }
  });
 }

 private void runHomebrewCpuAgent(){
  if(progressBusy||aiFrozen){showState("Сначала выйди из STEP_MODE/сохранения");return;}
  if(!resumed||!homebrewRom||!pageLoaded||frames<15||hash==0||
     (pendingTrial!=null)){
   if(experiment.running())stopCpuExperiment("STOP: тестовая NES недоступна");
   else showState("CPU-агент доступен только в запущенном homebrew NES");
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
  if(progressBusy||pendingTrial!=null||frames<15||hash==0)return false;
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
   // This goal has a verifiable ROM-RAM signal, unlike visual hash changes.
   // Only after core input ACK and a fresh final frame does it become evidence.
   // It is NOT yet a PPO rollout: no obs64/oldLogProb was sampled by StudentPolicy.
   HomebrewRamReward.Evidence rightProgress=HomebrewRamReward.evaluate(
     homebrewRom,aiFrozen&&aiRunning&&current.ramProbeSeen,
     current.button,current.frame,count,current.xBefore,current.xAfter);
   readyTrial=NesTrial.create(homebrewRom?"HOMEBREW":"USER_ROM",
     current.button,current.frame,count,current.hash,pixelHash);
   if(shareTrial!=null)shareTrial.setEnabled(autoplayBatchesRemaining==0&&studentRemaining==0&&autoGgufRemaining==0);
   showState(readyTrial.summary()+" · можно передать в ModelLab");
    if(aiRunning){
     aiRunning=false;
     GoalSample goal=pendingGoal;
     pendingGoal=null;
     boolean saved=recordAiEpisode(readyTrial);
     boolean taskGoalSaved=false; // GGUF uses onHomebrewTask; student uses RAM progress.
     if(!saved)showState("STOP: общий журнал NES недоступен");
     else if(goal!=null&&goal.epoch.equals(frameEpoch.token())&&
          goal.button.equals(readyTrial.button)&&
          goal.start==readyTrial.frameBefore&&goal.finish==readyTrial.frameAfter){
      try{
       ConsoleExperienceMemory.appendGoal(getFilesDir().toPath(),goal.epoch,
         aiModelId,aiModelSha,readyTrial,goal.x0,goal.y0,goal.x1,goal.y1);
       taskGoalSaved=true;
       HomebrewGoalReward.Result task=HomebrewGoalReward.verify(goal.button,
         goal.start,goal.finish,goal.x0,goal.y0,goal.x1,goal.y1);
       showState("NES 6502: "+goal.button+" · задача дойти до (192,112)"+
         " · reward "+task.reward()+" · "+(task.reached()?"ЦЕЛЬ ДОСТИГНУТА":"продолжается")+
         " · вес GGUF не изменился");
      }catch(IOException|IllegalArgumentException invalid){
       showState("STOP: подтверждён ход, но награда NES не записана: "+
         invalid.getClass().getSimpleName());
      }
     }else{
      showState("ИИ-ход "+readyTrial.button+": "+readyTrial.status+
        " · RAM-награда не подтверждена (UNKNOWN)");
     }
     boolean goalSaved=true;
     if(saved&&rightProgress.verified()) {
      try{
       // The same shared experience system owns both the legacy UNKNOWN
       // history and this separate, non-PPO homebrew RAM-goal evidence.
       ConsoleExperienceMemory.appendHomebrewGoal(getFilesDir().toPath(),
         new ConsoleExperienceMemory.HomebrewGoal(
           frameEpoch.token(),pinnedHomebrewRomSha,aiModelId,aiModelSha,
           current.button,current.frame,count,current.xBefore,current.xAfter,
           rightProgress.reward()));
      }catch(IOException|IllegalArgumentException failed){
       goalSaved=false;
       harness.stop("HOME_BREW_GOAL_JOURNAL_FAILURE");
      }
     }
     String ramGoal=rightProgress.verified()?
       " · ROM RAM-цель "+HomebrewRamReward.GOAL+
         " = "+rightProgress.reward()+" · свидетельство "+(goalSaved?"сохранено":"НЕ СОХРАНЕНО")+
         "; PPO BLOCKED (нет obs64 / oldLogProb)":
       " · ROM RAM-награда UNKNOWN ["+rightProgress.reason()+"]";
     showState(saved&&goalSaved?
       "ИИ-ход "+readyTrial.button+": "+readyTrial.status+
         " · визуальный журнал reward=UNKNOWN"+ramGoal:
       "STOP: проверенный кадр / RAM-цель не удалось сохранить; ввод отозван");
     if(autoGgufRemaining>0){
      if(!saved||!taskGoalSaved||
         !"PIXELS_DIFFER".equals(readyTrial.status)){
       stopCpuExperiment("STOP: нет независимого подтверждения GGUF-действия");
      }else{
       --autoGgufRemaining;
       if(autoGgufRemaining==0){
        showState("GGUF: до 4 реальных NES-действий записаны; RAM-цель подтверждена. "+
         "Веса GGUF не обучались; проверь журнал в Harness.");
       }else{
        final String nextEpoch=frameEpoch.token();
        web.postDelayed(()->{
         if(!closed&&resumed&&frameEpoch.current(nextEpoch)&&autoGgufRemaining>0&&
            !harness.hasPending()&&!aiRunning&&!harness.actionInFlight())
          requestFrozenAiObservation();
        },120);
       }
      }
     }
     if(studentRemaining>0)appendStudentBatchStep(current,rightProgress,count,saved&&goalSaved);
     else if(current.studentState64!=null){
      if(!harness.finishEpisode(studentLease))throw new IllegalStateException("PPO_EPISODE_END_DENIED");
      studentLease=null;
     }
    }
   if(experiment.running()){
    NesExperimentPolicy.Decision result=experiment.observe(readyTrial.status);
    if(result==NesExperimentPolicy.Decision.CONTINUE){
     long serial=experimentGeneration;
     String diagnostic=readyTrial.status.equals("NO_VISUAL_CHANGE")
       ?" · ОШИБКА: кадр не изменился, лабораторное продолжение"
       :" · кадр изменился";
     showState("CPU NES: шаг "+experiment.completed()+"/"+
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

 /**
  * This is the only producer of PPO-eligible on-policy NES data.
  * The actor's sampled oldLogProb/value/weight hash are captured BEFORE action.
  * Real JSNES CPU RAM and the completed ActionPermit are checked AFTER it.
  */
 private void appendStudentBatchStep(TrialPending t,
    HomebrewRamReward.Evidence goal,int afterFrame,boolean journalSaved){
  if(studentRemaining<=0)return;
  try{
   if(!journalSaved)throw new IllegalStateException("PPO_JOURNAL_NOT_SAVED");
   if(!goal.verified())throw new IllegalStateException("PPO_RAM_"+goal.reason());
   if(t.studentLegalMask!=HomebrewObs64.taskMask(t.xBefore,t.yBefore,ConsoleAgentContract.HOLD_FRAMES))
    throw new IllegalStateException("PPO_TASK_MASK_MISMATCH");
   if(!frameEpoch.current(studentBatchEpoch)||
      harness.stopRevision()!=studentBatchStopRevision||
      t.studentState64==null||
      t.studentPolicySha==null||t.studentActionId!=HomebrewObs64.id(t.button)||
      !java.util.Arrays.equals(t.studentState64,
         HomebrewObs64.encode(t.xBefore,t.yBefore))||
      !Float.isFinite(t.studentValue)||!Float.isFinite(t.studentOldLogProb)||
      afterFrame-t.frame!=ConsoleAgentContract.HOLD_FRAMES)
    throw new IllegalStateException("NES_STUDENT_ROLLOUT_NOT_VERIFIED");
   float[] next=HomebrewObs64.encode(t.xAfter,t.yAfter);
   int seq=studentRollout.size();
   if(seq>=VerifiedReplayFilter.MIN_STEPS)
    throw new IllegalStateException("NES_STUDENT_ROLLOUT_OVERFLOW");
   boolean batchEnd=studentRemaining==1;
   VerifiedReplayFilter.Step step=new VerifiedReplayFilter.Step(
     "student-homebrew-"+studentBatchEpoch,studentBatchEpoch,
     VerifiedReplayFilter.Provenance.RETRO_EMULATED,
     pinnedHomebrewRomSha,t.studentPolicySha,HomebrewObs64.SCHEMA,
     seq,t.frame,afterFrame,ConsoleAgentContract.HOLD_FRAMES,
     t.studentState64,next,t.studentActionId,HomebrewObs64.ACTIONS,
     t.studentLegalMask,t.studentOldLogProb,t.studentValue,
     goal.reward(),VerifiedReplayFilter.RewardSource.VERIFIED_ROM_RAM,
     t.downAck&&t.upAck,true,t.ramProbeSeen,false,batchEnd,false);
   studentRollout.add(step);
   progressVerifiedActions++;
   studentRemaining--;
   if(studentRemaining>0){
    final String epoch=studentBatchEpoch;
    final long revision=studentBatchStopRevision;
    showState("StudentPolicy: реальных NES-шагов "+(seq+1)+
      "/"+VerifiedReplayFilter.MIN_STEPS+" · текущая награда "+goal.reward());
    web.postDelayed(()->{
     if(closed||!resumed||!frameEpoch.current(epoch)||
        harness.stopRevision()!=revision||studentRemaining<=0){
      stopCpuExperiment("STOP: студенческая серия прервана");return;
     }
     requestStudentAction(true);
    },40);
   }else {
    VerifiedReplayFilter.Verdict verdict=VerifiedReplayFilter.inspect(studentRollout);
    if(!verdict.eligible())throw new IllegalStateException("REPLAY_BLOCKED_"+verdict.reason());
    if(!harness.finishEpisode(studentLease))throw new IllegalStateException("PPO_EPISODE_END_DENIED");
    studentLease=null;
    submitStudentTraining(List.copyOf(studentRollout));
   }
  }catch(RuntimeException failed){
   String code=failed.getMessage();
   if(code==null||!code.matches("[A-Z0-9_]{1,100}"))code=failed.getClass().getSimpleName();
   stopCpuExperiment("STOP: Student PPO-опыт недостоверен: "+code);
  }
 }
 private void submitStudentTraining(List<VerifiedReplayFilter.Step> records){
  try{
   if(!frameEpoch.current(studentBatchEpoch)||
      harness.stopRevision()!=studentBatchStopRevision||
      records.size()!=VerifiedReplayFilter.MIN_STEPS||
      studentTraining!=null&&!studentTraining.isDone())
    throw new IllegalStateException("NO_VERIFIED_STUDENT_BATCH");
   StudentPolicyNet active=activeStudent().candidateCopy();
   final String epoch=studentBatchEpoch;
   final long revision=studentBatchStopRevision;
   final Path vault=new File(getFilesDir(),"localpilot_vault").toPath();
   studentTraining=studentTrainer.submit(()->{
    try{
     if(Thread.currentThread().isInterrupted()||harness.stopRevision()!=revision)
      throw new IllegalStateException("PPO_STOP_BEFORE_TRAIN");
     // Store actual sampled states, old probabilities and RAM rewards before training.
     VerifiedPpoReplayStore.Receipt receipt=VerifiedPpoReplayStore.append(vault,records);
     if(Thread.currentThread().isInterrupted()||harness.stopRevision()!=revision)
      throw new IllegalStateException("PPO_STOP_AFTER_REPLAY_PERSIST");
     Path candidateWeights=vault.resolve("models/candidates/student-homebrew-v1.weights.bin");
     Path candidateManifest=vault.resolve("models/candidates/student-homebrew-v1.manifest.json");
     final String trainingNote;
     if(Files.exists(candidateWeights)||Files.exists(candidateManifest)){
      // Never overwrite or silently activate a pending candidate to keep autoplay moving.
      if(!Files.isRegularFile(candidateManifest)||Files.isSymbolicLink(candidateManifest)||
         Files.isSymbolicLink(candidateWeights)||Files.size(candidateManifest)>4096)
       throw new IOException("PPO_PENDING_CANDIDATE_INVALID");
      org.json.JSONObject stored=new org.json.JSONObject(new String(Files.readAllBytes(candidateManifest),java.nio.charset.StandardCharsets.UTF_8));
      String digest=StudentPolicyNet.load(candidateWeights).sha256();
      if(!"localpilot-student-candidate-v1".equals(stored.getString("schema"))||
         !HarnessCoordinator.PPO_TASK.equals(stored.getString("task"))||
         !records.get(0).observationSchema().equals(stored.getString("observationSchema"))||
         !digest.equals(stored.getString("policyAfterSha256"))||
         !active.sha256().equals(stored.getString("policyBeforeSha256"))||
         !records.get(0).romSha256().equals(stored.getString("romSha256"))||
         !"NOT_RUN".equals(stored.getString("evaluation"))||
         !"BLOCKED_UNTIL_INDEPENDENT_EVALUATION".equals(stored.getString("promotion")))
       throw new IOException("PPO_PENDING_CANDIDATE_IDENTITY_MISMATCH");
      trainingNote="Кандидат "+digest.substring(0,16)+" сохранён без перезаписи; новые данные записаны, независимая оценка ожидается";
     }else{
      LearningCoordinator.Candidate candidate=LearningCoordinator.prepare(active,records,vault);
      trainingNote="PPO изменил "+candidate.update().changedParameters()+" параметров КАНДИДАТА · SHA "+candidate.update().shaAfter().substring(0,16);
     }
     runOnUiThread(()->{
      if(closed||!resumed||!frameEpoch.current(epoch)||
         harness.stopRevision()!=revision)return;
      studentRollout.clear();
      studentTraining=null;
      learningResult.setText("NES: 16 проверенных шагов сохранены · replay SHA "+
        receipt.sha256().substring(0,12)+" · ROM-RAM reward "+receipt.totalReward()+
        ". "+trainingNote+
        " · ОЦЕНКА НЕ ПРОЙДЕНА · активные веса НЕ ЗАМЕНЕНЫ");
      if(autoplayBatchesRemaining>0)--autoplayBatchesRemaining;
      persistProgress(()->{
       if(autoplayBatchesRemaining>0)queueAutoplayBatch(epoch,revision);
      });
     });
    }catch(IOException|org.json.JSONException|RuntimeException failed){
     runOnUiThread(()->{
      if(closed||!resumed||!frameEpoch.current(epoch)||
         harness.stopRevision()!=revision)return;
      stopCpuExperiment("STOP: PPO-кандидат отклонён: "+failed.getClass().getSimpleName());
     });
    }
   });
   showState("StudentPolicy: 16 реальных шагов подтверждены; PPO обучает только кандидата");
  }catch(IOException|RuntimeException failed){
   stopCpuExperiment("STOP: PPO не запущено: "+failed.getClass().getSimpleName());
  }
 }

 private boolean aiEligible(){
  return getPackageName().startsWith("org.localpilot.android")&&
    !progressBusy&&resumed&&homebrewRom&&pageLoaded&&web!=null&&frames>=15&&hash!=0&&
    !experiment.running()&&!aiRunning&&pendingTrial==null&&
    studentRemaining==0&&(studentTraining==null||studentTraining.isDone());
 }
 private void requestAiObservation(){
  if(!aiEligible()||snapshotGate.pending()||harness.hasPending()){
   showState("ИИ: необходима активная встроенная NES, без другого выполняющегося действия");
   return;
  }
  if(aiFrozen){requestFrozenAiObservation();return;}
  // JSNES confirms it has stopped the realtime timer and returns the
  // authoritative last frame/hash through onStepReady before a ticket exists.
  web.evaluateJavascript("window.retroEnterStepMode()",result->{
   if(!"true".equals(result)&&!aiFrozen){
    harness.stopForEpoch(frameEpoch.token(),"STEP_MODE_UNAVAILABLE");
    showState("STOP: эмулятор не подтвердил переход в STEP_MODE");
   }
  });
 }
 private void requestFrozenAiObservation(){
  if(!aiFrozen||!aiEligible()||snapshotGate.pending()||harness.hasPending()){
   showState("STOP: нет единственного стабильного кадра STEP_MODE");return;
  }
  try{
   HarnessCoordinator.Ticket ticket=harness.begin(
     frameEpoch.token(),frames,hash,SystemClock.elapsedRealtime());
   aiObservationTicket=ticket;
   aiIssuedAt=ticket.issuedAt; // Keep the original frame age through inference AND the queued gesture.
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
   showState("ИИ: не удалось запросить наблюдение: "+
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
   showState("STOP: ИИ не получил достоверный свежий NES PNG-кадр");
   return;
  }
  if(autoGgufRemaining>0){
   if(!(getApplication() instanceof NesModelHost host)||aiObservationTicket==null){
    stopCpuExperiment("STOP: общий runtime модели недоступен");return;
   }
   final HarnessCoordinator.Ticket ticket=aiObservationTicket;
   int x=-1,y=-1;
   if(task!=null&&task.token.equals(token)&&task.epoch.equals(frameEpoch.token())){x=task.x;y=task.y;}
   final NesModelHost.Observation observation;
   try{observation=new NesModelHost.Observation(ticket,pinnedHomebrewRomSha,host.nesBoundModel(),x,y,png);}
   catch(IllegalArgumentException invalid){stopCpuExperiment("STOP: контракт наблюдения модели недопустим");return;}
   directModelInference=true;
   directModelBinding=observation.binding();
   if(aiObserveButton!=null)aiObserveButton.setEnabled(false);
   showState("Общая модель анализирует кадр NES без переключения экрана; STOP доступен");
   try{host.inferNes(observation,reply->runOnUiThread(()->{
    if(closed||!directModelInference||!ticket.nonce.equals(aiTicketNonce))return;
    directModelInference=false;aiObservationTicket=null;
    if(reply==null||reply.observation()!=observation||!resumed||
       !NesModelHost.current(harness,observation,SystemClock.elapsedRealtime())||
       reply.status()!=NesModelHost.Status.PROPOSAL){
     stopCpuExperiment("STOP: ответ общей модели отклонён: "+(reply==null?"UNKNOWN":reply.status()));return;
    }
    acceptAiProposal(ticket.nonce,reply.modelId(),reply.modelSha256(),reply.button(),ConsoleAgentContract.HOLD_FRAMES);
   }));}
   catch(RuntimeException unavailable){stopCpuExperiment("STOP: общий runtime не принял наблюдение");return;}
   web.postDelayed(()->{
    if(directModelInference&&ticket.nonce.equals(aiTicketNonce))
     stopCpuExperiment("STOP: TTL общего запроса модели истёк");
   },Math.max(0,ConsoleAgentContract.TTL_MS-(SystemClock.elapsedRealtime()-ticket.issuedAt)));
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
  if(autoGgufRemaining>0)intent.putExtra(HarnessDemoWorkflow.EXTRA_AUTO_MODEL,true);
  try{startActivity(intent);}
  catch(RuntimeException failure){
   harness.stopForEpoch(frameEpoch.token(),"STOP");aiTicketNonce=null;showState("STOP: ModelLab не открылся");
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
    showState("STOP: ModelLab отменил запрос; NES-контроллер освобождён");
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
  acceptAiProposal(nonce,modelId,modelSha,button,proposedFrames);
 }
 private void acceptAiProposal(String nonce,String modelId,String modelSha,String button,int proposedFrames){
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
  aiObservationTicket=null;
  if(!admissible){
   stopCpuExperiment("STOP: ИИ-команда устарела, повреждена или ROM изменился");
   return;
  }
  aiModelId=modelId;aiModelSha=modelSha;aiHoldFrames=proposedFrames;
  if("NONE".equals(button)){
   if(autoGgufRemaining>0){stopCpuExperiment("STOP: модель выбрала NONE");return;}
   harness.stopForEpoch(frameEpoch.token(),"MODEL_SELECTED_NONE");
   showState("ИИ выбрал NONE: ввод не выполнялся");return;
  }
  queuedAiButton=button;aiEpoch=frameEpoch.token();aiExpectedHash=hash;
  if(resumed&&web!=null)web.postDelayed(this::executeAiMove,350);
 }
 private void executeAiMove(){
  String button=queuedAiButton;
  if(button==null||!resumed||progressBusy)return;
  queuedAiButton=null;
  if(!aiFrozen||!aiEligible()||!frameEpoch.current(aiEpoch)||hash!=aiExpectedHash||
    SystemClock.elapsedRealtime()-aiIssuedAt>ConsoleAgentContract.TTL_MS){
   harness.stopForEpoch(frameEpoch.token(),"STALE_EMULATOR_FRAME");
   showState("STOP: новый кадр не совпадает с наблюдением ИИ");
   return;
  }
  if(directModelBinding!=null&&(!(getApplication() instanceof NesModelHost host)||
     !directModelBinding.equals(host.nesBoundModel()))){
   stopCpuExperiment("STOP: назначенная модель изменилась после наблюдения");return;
  }
  HarnessCoordinator.Actor owner=aiModelId.startsWith("student:")?
    HarnessCoordinator.Actor.STUDENT:HarnessCoordinator.Actor.GGUF;
  if(!beginTrial(button,owner,aiHoldFrames)){
   harness.stopForEpoch(frameEpoch.token(),"PERMIT_DENIED");
   showState("STOP: Harness не выдал право на ввод NES");return;
  }
  aiRunning=true;
  directModelBinding=null;
  showState("ИИ "+aiModelId+" · STEP_MODE "+button+" на "+aiHoldFrames+" реальных кадров; проверяю");
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
  if(next!=null&&next.getAction()==null&&
     getPackageName().startsWith("org.localpilot.android")){
   String mode=next.getStringExtra(HarnessDemoWorkflow.EXTRA_MODE);
   if(HarnessDemoWorkflow.supported(mode)){
    if(harness.hasPending()||aiRunning||experiment.running()||pendingTrial!=null)
     showState("STOP: прежняя NES-сессия ещё выполняет ввод");
    else {requestedAutoWorkflow=mode;tryAutoWorkflowLaunch();}
   }
  }
 }
 private void sendTrialToModelLab(){
  if(readyTrial==null){showState("Для отчёта запусти NES и нажми кнопку");return;}
  if(snapshotGate.pending()||!resumed||!pageLoaded||web==null){
   showState("Сначала открой работающий NES-эмулятор");return;
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
   showState("Установи отдельный ModelLab, затем отправь NES-эпизод повторно");
  }catch(SecurityException denied){
   showState("ModelLab не разрешил приём: проверь установленную версию");
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
 private void launchRom(byte[] rom,boolean homebrew){launchRom(rom,homebrew,null,false);}
 private void launchRom(byte[] rom,boolean homebrew,NesSaveStore.Save restore,boolean resumeAuto){
  if(!pageLoaded||web==null||rom==null||rom.length>MAX_ROM){
   showState("NES-ядро ещё не загружено или ROM недоступен");return;
  }
  NesRomPreflight.Result preflight=RetroEmulatorCatalog.preflight(
    dendy?RetroEmulatorCatalog.System.DENDY:RetroEmulatorCatalog.System.NES,rom);
  if(!preflight.runnable()){
   harness.stopForEpoch(frameEpoch.token(),"NES_PREFLIGHT_"+preflight.code());
   showState("ROM не запущен: "+preflight.code()+
      (preflight.mapper()>=0?" · mapper "+preflight.mapper():""));
   return;
  }
  ++progressGeneration;progressBusy=false;progressVerifiedActions=0;
  if(!autoLaunchArmed)autoplayBatchesRemaining=0;
  activeRomBytes=rom.clone();
  String encoded=Base64.encodeToString(rom,Base64.NO_WRAP);
  String session=frameEpoch.begin();
  harness.reset(session,HarnessCoordinator.Mode.REALTIME_MODE);
  pendingPermit=null;pendingGoal=null;taskObservation=null;aiFrozen=false;
  frames=hash=0;touches.set(0);
  ++experimentGeneration;experiment.stop();
  snapshotGate.cancel();exportTrial=null;
  harness.stopForEpoch(frameEpoch.token(),"STOP");aiTicketNonce=null;queuedAiButton=null;aiRunning=false;aiSnapshot=false;
  studentStepRequested=false;studentRemaining=0;studentRollout.clear();studentLease=null;
  if(studentTraining!=null)studentTraining.cancel(true);
  // A caller-provided boolean is not ROM provenance: require byte-for-byte
  // identity with the audited built-in 6502 cartridge before enabling RAM reward.
  if(homebrew&&!java.util.Arrays.equals(rom,HomebrewNes.cartridge())){
   frameEpoch.cancel();harness.stopForEpoch(session,"HOME_BREW_ROM_IDENTITY_MISMATCH");
   showState("STOP: тестовый ROM не совпадает с эталоном");return;
  }
  homebrewRom=false;pinnedHomebrewRomSha="";
  if(homebrew) {
   try{
    byte[] fingerprint=java.security.MessageDigest.getInstance("SHA-256").digest(rom);
    StringBuilder hex=new StringBuilder(64);
    for(byte v:fingerprint)hex.append(String.format(java.util.Locale.ROOT,"%02x",v&255));
    pinnedHomebrewRomSha=hex.toString();
   }catch(java.security.NoSuchAlgorithmException impossible){
    frameEpoch.cancel();harness.stopForEpoch(session,"ROM_SHA256_UNAVAILABLE");
    showState("STOP: не удалось проверить SHA ROM");return;
   }
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
   if(studentStepButton!=null)studentStepButton.setEnabled(launched&&homebrew);
   if(studentBatchButton!=null)studentBatchButton.setEnabled(launched&&homebrew);
   if(aiObserveButton!=null)aiObserveButton.setEnabled(launched&&homebrew&&
     getPackageName().startsWith("org.localpilot.android"));
   if(launched&&restore!=null){applyRestoredProgress(restore,session,resumeAuto);return;}
   if(launched&&homebrew&&autoLaunchArmed){
    autoLaunchArmed=false;
    autoStartArmed=true;
    showState("Harness: ROM подтверждён; ожидаю минимум 15 настоящих NES-кадров");
   }else if(!launched){
    autoLaunchArmed=false;autoGgufRemaining=0;
    showState("NES ROM не запущен. Проверьте mapper и ядро.");
   }
  });
 }
 private void refreshCompactModels(){
  if(compactModels==null)return;
  String selected=compactModels.getSelectedItemPosition()>0&&compactModels.getSelectedItemPosition()<=compactChoices.size()?
    compactChoices.get(compactModels.getSelectedItemPosition()-1).identity().id():null;
  try{
   if(!compactModelsInitialized&&getApplication() instanceof NesModelHost host){
    var bound=host.nesBoundModel();if(bound!=null)selected=bound.id();
   }
   compactChoices=getApplication() instanceof NesModelHost host?List.copyOf(host.nesModels()):List.of();
   compactModelsInitialized=true;
   List<String> labels=new ArrayList<>();labels.add("Student · CPU");
   for(var choice:compactChoices)labels.add(choice.label());
   compactModels.setAdapter(new android.widget.ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,labels));
   int index=0;for(int i=0;i<compactChoices.size();i++)if(compactChoices.get(i).identity().id().equals(selected))index=i+1;
   compactModels.setSelection(index);
  }catch(RuntimeException invalid){showState("STOP: каталог моделей недоступен");}
 }
 private boolean compactWorkflowBusy(){
  return progressBusy||studentRemaining>0||aiRunning||experiment.running()||harness.hasPending()||harness.actionInFlight()||
    autoStartArmed||autoLaunchArmed||autoGgufRemaining>0||autoplayBatchesRemaining>0||studentTraining!=null&&!studentTraining.isDone();
 }
 private void requestCompactWorkflow(String mode,NesModelHost.ModelIdentity model){
  if(compactWorkflowBusy()){
   showState("Сначала останови или заверши текущий сеанс");return;
  }
  Runnable launch=()->{
   if(!resumed||closed||!pageLoaded){showState("Подожди загрузки NES");return;}
   if(compactWorkflowBusy()){showState("STOP: другой сеанс уже выполняется");return;}
   if(model!=null&&(!(getApplication() instanceof NesModelHost host)||!host.selectNesModel(model))){
    showState("STOP: выбранная модель недоступна или движок занят");return;
   }
   requestedAutoWorkflow=mode;tryAutoWorkflowLaunch();
  };
  if(activeRomBytes!=null&&!homebrewRom)new AlertDialog.Builder(this).setTitle("Перейти в учебный ROM?")
   .setMessage("Агент и PPO пока работают только в учебном ROM. Текущая игра будет заменена; при необходимости сначала сохрани её.")
   .setPositiveButton("Запустить",(dialog,which)->launch.run()).setNegativeButton("Отмена",null).show();
  else launch.run();
 }
 private interface InputAck{void result(boolean accepted);}
 private void gamepad(String key,boolean down,InputAck ack){
  if(web==null||!pageLoaded||(down&&progressBusy)){ack.result(false);return;}
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
   showState("ROM не запущен: "+exception.getClass().getSimpleName());
   Toast.makeText(this,"Неподдерживаемый или слишком большой файл игры",Toast.LENGTH_LONG).show();
  }
 }
 @Override protected void onPause(){
  resumed=false;autoplayBatchesRemaining=0;progressBusy=false;++progressGeneration;snapshotGate.cancel();exportTrial=null;
  if(shareTrial!=null)shareTrial.setEnabled(readyTrial!=null);
  GameLabEvidenceProvider.clear("nes");
  if(directModelInference||directModelBinding!=null||experiment.running()||aiRunning||studentStepRequested||studentRemaining>0||
     studentTraining!=null&&!studentTraining.isDone())
   stopCpuExperiment("STOP: NES ушла в фон");
  if(pendingTrial!=null){harness.stopForEpoch(frameEpoch.token(),"NES_PAUSED_IN_FLIGHT");pendingPermit=null;}
  pendingTrial=null; // Keep a pending ModelLab ticket, but not held input.
  if(web!=null&&pageLoaded)web.evaluateJavascript("window.retroPause()",null);
  if(web!=null)web.onPause();
  super.onPause();
 }
 @Override protected void onResume(){
  super.onResume();resumed=true;
  refreshCompactModels();
  tryAutoWorkflowLaunch();
  if(demoButton!=null)demoButton.post(this::publishEvidence);
  if(web!=null){web.onResume();
   if(pageLoaded)web.evaluateJavascript("window.retroResume()",null);
  }
  if(queuedAiButton!=null&&web!=null)web.postDelayed(this::executeAiMove,350);
 }
 @Override protected void onDestroy(){
  cancelDirectInference();
  closed=true;progressBusy=false;++progressGeneration;progressIo.shutdownNow();harness.stopForEpoch(frameEpoch.token(),"NES_DESTROYED");pendingPermit=null;aiFrozen=false;
  queuedAiButton=null;aiRunning=false;studentStepRequested=false;
  frameEpoch.cancel();snapshotGate.cancel();exportTrial=null;
  romDownloads.shutdownNow();studentTrainer.shutdownNow();
  ++experimentGeneration;experiment.stop();
  if(web!=null){web.evaluateJavascript("window.retroStop()",null);
   web.removeJavascriptInterface("GameLabBridge");
   web.destroy();web=null;
  }
  super.onDestroy();
 }
}
