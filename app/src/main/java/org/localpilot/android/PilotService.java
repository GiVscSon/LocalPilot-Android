package org.localpilot.android;
import android.accessibilityservice.*;import android.app.AlertDialog;import android.app.KeyguardManager;import android.content.*;import android.graphics.*;import android.os.*;import android.view.*;import android.view.accessibility.*;import android.widget.*;
public class PilotService extends AccessibilityService {
 public static PilotService instance;
 private ExperienceStore experience;private ArcadeLearningStore arcadeLearning;private boolean collecting;
 public void enableExamples(){stop("Сбор примеров включён; привяжите игру заново");if(experience==null){note="Хранилище недоступно; сбор выключен";update();return;}collecting=true;update();}
 public void disableExamples(){collecting=false;stop("Сбор примеров выключен");}
 public byte[] exportExamples()throws java.io.IOException{stop("Экспорт примеров: STOP");if(experience==null)throw new java.io.IOException("Хранилище недоступно");return experience.snapshot();}
 public int restoreExamples(byte[] data)throws java.io.IOException{collecting=false;stop("Восстановление: STOP");if(experience==null)throw new java.io.IOException("Хранилище недоступно; исходник сохранён");return experience.restoreEmpty(data);}
 private boolean saveExample(String profile,int[] before,int action,int[] after){if(!collecting)return true;try{if(experience==null)throw new java.io.IOException();experience.append(profile,before,action,after);return true;}catch(ExperienceStore.Full full){collecting=false;uiStats="Хранилище примеров заполнено; сбор выключен, игра продолжается";Toast.makeText(this,uiStats,Toast.LENGTH_LONG).show();update();return true;}catch(java.io.IOException|RuntimeException e){collecting=false;stop("Пример не записан: сбор и ввод остановлены. Экспортируйте данные");return false;}}
 private final StageMetrics metrics=new StageMetrics();
 public String exportMetrics(){String bubbleReport=bubbleSession.report();stop("Экспорт замеров: STOP");return metrics.report()+"\n"+captureCounters()+"\nLAST_STOP: "+lastStopReason+"\nCAPTURE STARTUP\n"+captureStartup.report()+"\n"+sceneRecovery.report()+"\n"+bubbleReport;}
 private final Handler handler=new Handler(Looper.getMainLooper());private final Handler gestureHandler=new Handler(Looper.getMainLooper());private final ActionGate gate=new ActionGate();
 private final CaptureRetry captureRetry=new CaptureRetry();
 private DisplayGeometry displayGeometry;
 private WindowManager wm;private LinearLayout panel,controls,miniBar;private TextView miniState;private TextView state;private Button collapseButton;private boolean panelCompact,discoveringBoard;private WindowManager.LayoutParams layout;
 private final java.util.List<Button> manualControls=new java.util.ArrayList<>();
 private final java.util.concurrent.ExecutorService visionWorker=java.util.concurrent.Executors.newSingleThreadExecutor();
 // Accessed only by visionWorker; reuse the bounded mirror copy between jobs.
 private int[] arcadePixelBuffer;
 private int[] visionPixels(Bitmap frame){int width=frame.getWidth(),height=frame.getHeight(),count=Math.multiplyExact(width,height);if(count>480*720)throw new IllegalArgumentException("capture pixel bound");if(arcadePixelBuffer==null||arcadePixelBuffer.length<count)arcadePixelBuffer=new int[count];frame.getPixels(arcadePixelBuffer,0,width,0,0,width,height);return arcadePixelBuffer;}
 private java.util.concurrent.Future<?> visionTask;private long visionRequest;private boolean visionPending;
 private boolean busy=false;private int autoLeft=0;private long deadline;private int demoVerified;private String target="";private Rect armedBounds=new Rect();private int nx=50,ny=65;
 private long lastPanelRender;
 private String lastGestureContext="none";
 private String note="STOP",uiStats="",lastStopReason="Нет остановок";private final InputPermit permit=new InputPermit();private RotatingJournal journal;private final GestureFlight gestureFlight=new GestureFlight();
 private final Color2048Reader.Session colorSession=new Color2048Reader.Session();
 private boolean playingMini;private int miniKind,miniAction;private long miniMoves;private int[] miniBefore;private Rect boardRoi;private View picker;private boolean playing2048=false,colored2048=false,captureBusy=false;private long moves2048;private long captureCounter,pendingCapture,lastShotTimestamp=-1;private long mergeTotal;private Board2048 before2048;private int direction2048;private AlertDialog preview,overlayDialog;
 private final CaptureStartup captureStartup=new CaptureStartup();
 private final SceneRecovery sceneRecovery=new SceneRecovery();
 private final PixelBoardLocator.Stability boardSearch=new PixelBoardLocator.Stability();
 private int boardReadMisses;private long boardReadGeneration;
 private final WindowFocus windowFocus=new WindowFocus();
 private volatile boolean focusPaused;private boolean focusWatch;private long focusRecoveredAt,focusWatchId;
 private volatile long captureSession;
 private volatile long captureAcceptedFrames,captureSkippedFrames,captureSkippedThrottle,captureSkippedUnwanted,captureSkippedClock;
 private long captureFrames,captureReads,captureDecisions,captureSent,captureCompleted,dispatchedInputs;
 private volatile boolean playingJumper,jumperCaptureReady;
 // The legacy Jumper capture session is shared by all explicit visual arcade profiles.
 private volatile ArcadeProfile captureProfile=ArcadeProfile.ARI;
 private ArcadeObservation arcadeState,arcadeBefore,arcadeLastUsable;
 private final ArcadeController arcadeController=new ArcadeController();
 private final ArcadeFeedback arcadeFeedback=new ArcadeFeedback();
 private final JumperFeedback jumperFeedback=new JumperFeedback();
 private final RoundOutcome roundOutcome=new RoundOutcome();
 private long arcadeSceneGeneration;
 private boolean consentPanelDetached,panelRegistered,destroying;
 private String learningStatus="Нет сохранённых изменений в этом сеансе";
 private String learningSlot="default";
 private String modelSlot="default";
 private boolean playingGrid,gridResultVisible,gridSawAction; // Shared screenshot loop for grid and bubble puzzles.
 private final BubbleSession bubbleSession=new BubbleSession();
 private BubbleBoard bubbleGeometry;
 private BubblePolicy.Shot bubblePendingShot;
 private LiveGameLearning bubbleLearning=new LiveGameLearning(LiveGameLearning.Game.BUBBLE);
 private LiveGameLearning learning2048=new LiveGameLearning(LiveGameLearning.Game.GAME_2048);
 private boolean bubbleLearningWritable=true,learning2048Writable=true;
 private GridPuzzleConfig gridConfig=new GridPuzzleConfig(6,6,2);
 private GridPuzzleVision.Palette gridPalette;
 private GridPuzzleGoal.Color gridGoal=GridPuzzleGoal.Color.ANY;
 private final GridPuzzleSession gridSession=new GridPuzzleSession(Build.VERSION.SDK_INT>=31?3000:4500);
 private long gridMoves,gridVerified;
 private GridOnlineLearning gridLearning=new GridOnlineLearning();
 private GridPuzzlePolicy.Decision gridPendingDecision;
 // Bounded symbolic counts from the current grid observation; no pixels retained.
 private String gridUnknownReasons="none";
 private CustomVisionConfig customVision;
 private boolean customCalibrationPending;
 private View customCalibrationOverlay;
 private int controlMode;
 private ArcadePolicy.Decision arcadePending;
 private long arcadeLastAction,arcadePendingAt,arcadeVerified,arcadeDispatchFrameAge=-1;
 private boolean arcadeAck,arcadeRoundSeen;
 private long arcadeCaptureReadyAt;
 private volatile boolean jumperVisionPending;
 private boolean jumperConsentPending,jumperAwaitingVerification;
 private boolean jumperCalibrationPending;private long jumperLastFrameMs,jumperLastActionMs,jumperPendingActionMs;private float jumperPendingStartX;
 private JumperPolicy.Action jumperPendingAction=JumperPolicy.Action.WAIT;private JumperState jumperState;
 private int jumperResponseFailures;private boolean jumperResponsePaused;
 private JumperVision.ColorProfile jumperColorProfile;private JumperCalibrationOverlay jumperCalibrationOverlay;
 private int jumperDisplayWidth,jumperDisplayHeight;private JumperFrameGeometry jumperFrameGeometry;
 private final Object jumperFrameLock=new Object();
 private final java.util.ArrayDeque<Bitmap> jumperQueuedFrames=new java.util.ArrayDeque<>();
 private Bitmap diagnosticFrame;private long diagnosticFrameAt=-1;
 private String diagnosticFrameGeometry="No game frame retained",lastSessionReport="No recorded game session";

 private void clearDiagnosticFrame(){if(diagnosticFrame!=null&&!diagnosticFrame.isRecycled())diagnosticFrame.recycle();diagnosticFrame=null;diagnosticFrameAt=-1;diagnosticFrameGeometry="No game frame retained";}
 /** A single RAM-only crop, sampled at most once a second while the bound game is focused. */
 private void rememberDiagnosticFrame(Bitmap frame,long timestamp){
  long now=SystemClock.elapsedRealtime();
  if(jumperCalibrationOverlay!=null||jumperCalibrationPending||now<timestamp||now-timestamp>650
    ||diagnosticFrameAt>=0&&timestamp-diagnosticFrameAt<1000||!valid(gate.offer(now)))return;
  Bitmap copy=null;
  try{
   JumperFrameGeometry g=visualGeometry(frame);
   copy=Bitmap.createBitmap(g.right-g.left,g.bottom-g.top,Bitmap.Config.ARGB_8888);
   new Canvas(copy).drawBitmap(frame,new Rect(g.left,g.top,g.right,g.bottom),new Rect(0,0,copy.getWidth(),copy.getHeight()),null);
   if(diagnosticFrame!=null&&!diagnosticFrame.isRecycled())diagnosticFrame.recycle();
   diagnosticFrame=copy;copy=null;diagnosticFrameAt=timestamp;
   diagnosticFrameGeometry="frame_elapsed_ms="+timestamp+" crop="+diagnosticFrame.getWidth()+"x"+diagnosticFrame.getHeight()
      +" screen_bounds="+armedBounds.toShortString()+" display="+jumperDisplayWidth+"x"+jumperDisplayHeight+" profile="+profileLabel();
  }catch(RuntimeException|OutOfMemoryError error){/* Reporting must not interrupt gameplay. */}
  finally{if(copy!=null&&!copy.isRecycled())copy.recycle();}
 }
 public Bitmap copyDiagnosticFrame(){try{return diagnosticFrame==null||diagnosticFrame.isRecycled()||SystemClock.elapsedRealtime()-diagnosticFrameAt>5000?null:diagnosticFrame.copy(Bitmap.Config.ARGB_8888,false);}catch(RuntimeException|OutOfMemoryError error){return null;}}
 private String diagnosticState(){
  long now=SystemClock.elapsedRealtime();
  StringBuilder out=new StringBuilder("LocalPilot game-session-v1\nelapsed_ms=").append(now)
    .append("\nprofile=").append(captureProfile).append(" selected_profile=").append(profileLabel()).append(" target=").append(target).append(" bound=").append(gate.armed())
    .append("\nvisual_roi=").append(visualBounds().toShortString()).append(" manual_roi=").append(manualVisualRoi)
    .append("\nbounds=").append(armedBounds.toShortString()).append(" display=").append(jumperDisplayWidth).append('x').append(jumperDisplayHeight)
    .append("\nplaying=").append(playingJumper||playingGrid).append(" capture_ready=").append(playingGrid||jumperCaptureReady).append(" busy=").append(busy)
    .append("\nstatus=").append(note).append("\nLAST_STOP=").append(lastStopReason).append("\nlast_gesture=").append(lastGestureContext).append('\n').append(captureCounters())
    .append("\nframe_age_ms=").append(diagnosticFrameAt<0?-1:Math.max(0,now-diagnosticFrameAt)).append('\n').append(diagnosticFrameGeometry)
    .append('\n').append(gestureFlight.report(now)).append('\n').append(sceneRecovery.report())
    .append('\n').append(arcadeFeedback.report()).append('\n').append(jumperFeedback.report()).append("\nMOTION MODEL: ").append(arcadeController.model())
    .append('\n').append(roundOutcome.report()).append("\nLEARNING: ").append(learningStatus)
    .append(" scope=").append(target).append('/').append(learningSlot)
    .append(" model_context=").append(modelSlot)
    .append(" losses=").append(arcadeLearning==null||target.isEmpty()?-1:arcadeLearning.losses(target,modelSlot,captureProfile))
    .append("\nSAVED_MODEL: ").append(arcadeLearning==null||target.isEmpty()?"unavailable":arcadeLearning.get(target,modelSlot,captureProfile))
    .append("\nCUSTOM_VISION: ").append(customVision!=null).append(" control_mode=").append(controlMode);
  out.append('\n').append(gridSession.report()).append(" unknown_reasons=").append(gridUnknownReasons).append(" size=").append(gridConfig.rows).append('x').append(gridConfig.cols).append(" goal_color=").append(gridGoal).append(" dispatched=").append(gridMoves).append(" visual_changes=").append(gridVerified);
  out.append('\n').append(bubbleSession.report());
  out.append("\nLIVE_LEARNING_2048: verified=").append(learning2048.verifiedCount())
   .append(" unconfirmed=").append(learning2048.unconfirmedCount())
   .append(" saved=").append(learning2048Writable)
   .append("\nLIVE_LEARNING_BUBBLE: verified=").append(bubbleLearning.verifiedCount())
   .append(" unconfirmed=").append(bubbleLearning.unconfirmedCount())
   .append(" saved=").append(bubbleLearningWritable);
  if(arcadeState!=null)out.append("\narcade_usable=").append(arcadeState.usable()).append(" confidence=").append(arcadeState.confidence)
    .append(" terminal=").append(arcadeState.terminal).append(" tracked=").append(arcadeState.tracked)
    .append("\nplayer=").append(diagnosticBox(arcadeState.player)).append(" control=").append(diagnosticBox(arcadeState.control))
    .append(" obstacles=").append(arcadeState.obstacles.size()).append(" vx=").append(arcadeState.vx).append(" vy=").append(arcadeState.vy);
  if(jumperState!=null)out.append("\nari_usable=").append(jumperState.usable()).append(" confidence=").append(jumperState.confidence)
    .append(" player=").append(jumperState.player!=null).append(" platforms=").append(jumperState.platforms.size()).append(" hazards=").append(jumperState.hazards.size());
  out.append("\npending_action=").append(arcadePending==null?"none":arcadePending.action).append(" action_age_ms=")
    .append(arcadePending==null?-1:Math.max(0,now-arcadePendingAt)).append(" android_ack=").append(arcadeAck)
    .append(" dispatch_frame_age_ms=").append(arcadeDispatchFrameAge).append(" verified_game_responses=").append(arcadeVerified).append(" ari_pending=").append(jumperAwaitingVerification).append(" demo_verified=").append(demoVerified).append(" demo_remaining=").append(autoLeft);
  return out.toString();
 }
 private String diagnosticBox(ArcadeObservation.Box b){return b==null?"none":b.left+","+b.top+","+b.right+","+b.bottom;}
 public String exportDiagnosticState(){
  String result="BEFORE EXPORT\n"+diagnosticState()+"\n\nLAST ACTIVE SESSION\n"+lastSessionReport+"\n\n"+metrics.report()+"\nCAPTURE STARTUP\n"+captureStartup.report();
  stop("Экспорт отчёта: STOP");return result;
 }

 private String targetLabel="";
 private Rect visualRoi;private boolean manualVisualRoi;private int visualWindow=-1;private String visualOwner="";private Rect visualWindowBounds=new Rect();
 private int selectedProfile; //0..3 arcade,4/5 2048,6 swap,7 group,10 straight-chain grid,11 protected-skull swap.
 private boolean browserProfileConfirmed,unsupportedGame,targetIsBrowser;
 private String confirmedProfileScope="";
 private String profileChoiceKey(String pkg){String slot=getSharedPreferences("panel",MODE_PRIVATE).getString("game_slot."+pkg,"default");return VisualGameTarget.selectionKey(pkg,slot);}
 private String color2048PaletteKey(){if(target==null||target.isEmpty())throw new IllegalStateException("2048 game scope unavailable");return profileChoiceKey(target)+".learned_colors";}
 private String liveLearningKey(LiveGameLearning.Game game,boolean color2048){
  return profileChoiceKey(target)+(game==LiveGameLearning.Game.BUBBLE?".bubble":color2048?".color2048":".2048")+".verified-live-v1";
 }
 private LiveGameLearning loadLiveGameLearning(LiveGameLearning.Game game,boolean color2048){
  String saved=getSharedPreferences("live-game-learning",MODE_PRIVATE).getString(liveLearningKey(game,color2048),null);
  if(saved==null)return new LiveGameLearning(game);
  try{return LiveGameLearning.decode(game,saved);}
  catch(IllegalArgumentException broken){
   if(game==LiveGameLearning.Game.BUBBLE)bubbleLearningWritable=false;else learning2048Writable=false;
   note="Модель игровых ходов повреждена; исходные данные сохранены, обучение отключено";
   return new LiveGameLearning(game);
  }
 }
 private void saveLiveGameLearning(LiveGameLearning learned,boolean color2048){
  if(learned.game()==LiveGameLearning.Game.BUBBLE&&!bubbleLearningWritable
      ||learned.game()==LiveGameLearning.Game.GAME_2048&&!learning2048Writable)return;
  getSharedPreferences("live-game-learning",MODE_PRIVATE).edit()
    .putString(liveLearningKey(learned.game(),color2048),learned.encode()).apply();
 }
 private boolean browserTarget(){try{return getPackageManager().resolveActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse("https://localpilot.invalid")).setPackage(target),android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)!=null;}catch(RuntimeException ignored){return true;}}
 private boolean explicitWindow;
 private Rect visualBounds(){return visualRoi!=null&&armedBounds.contains(visualRoi)?new Rect(visualRoi):new Rect(armedBounds);}
 private Rect windowBounds(AccessibilityNodeInfo root){
  if(root!=null)for(AccessibilityWindowInfo w:getWindows())if(w.getType()==AccessibilityWindowInfo.TYPE_APPLICATION&&w.getId()==root.getWindowId()){Rect b=new Rect();w.getBoundsInScreen(b);return b;}
  return new Rect();
 }
 private void retainVisualScope(AccessibilityNodeInfo root){
  if(!target.equals(visualOwner)||root.getWindowId()!=visualWindow||!armedBounds.equals(visualWindowBounds)){
   visualRoi=null;manualVisualRoi=false;visualOwner=target;visualWindow=root.getWindowId();visualWindowBounds=new Rect(armedBounds);
   // Never carry a screenshot or scene geometry from the previous app into a
   // newly bound game.  The next capture must establish a fresh ROI/profile.
   boardRoi=null;clearDiagnosticFrame();sceneRecovery.reset();jumperState=null;
   arcadeLastUsable=null;arcadeState=null;arcadeBefore=null;arcadePending=null;arcadeAck=false;
   arcadeController.reset();arcadeFeedback.reset();arcadeRoundSeen=false;
  }
 }
 private JumperFrameGeometry visualGeometry(Bitmap frame){Rect b=visualBounds();return new JumperFrameGeometry(frame.getWidth(),frame.getHeight(),jumperDisplayWidth,jumperDisplayHeight,b.left,b.top,b.right,b.bottom,true);}
 private void resolveVisualRegion(Bitmap frame){
  if(manualVisualRoi||visualRoi!=null||arcadeRoundSeen)return;
  Rect b=armedBounds;
  // A portrait application window already defines the game: road verges are not letterboxing.
  if(b.width()<=b.height()){visualRoi=new Rect(b);return;}
  JumperFrameGeometry g=new JumperFrameGeometry(frame.getWidth(),frame.getHeight(),jumperDisplayWidth,jumperDisplayHeight,b.left,b.top,b.right,b.bottom,true);
  Rect p=panelBounds();GameViewport.Box original=new GameViewport.Box(g.left,g.top,g.right,g.bottom);
  GameViewport.Box found=GameViewport.detect(frame::getPixel,original,new GameViewport.Box(g.toFrameX(p.left),g.toFrameY(p.top),g.toFrameX(p.right),g.toFrameY(p.bottom)));
  if(!found.equals(original)){
   Rect next=new Rect(Math.max(b.left,(int)Math.ceil(g.toScreenX(found.left()))),b.top,Math.min(b.right,(int)Math.floor(g.toScreenX(found.right()))),b.bottom);
   if(!next.equals(visualRoi)){visualRoi=next;sceneRecovery.reset();jumperState=null;arcadeState=null;}
  }
 }
 private void overlayMenu(String title,String[] labels,java.util.function.IntConsumer select){
  long menuEpoch=gate.epoch();AlertDialog d=new AlertDialog.Builder(this).setTitle(title).setItems(labels,(dialog,index)->{dialog.dismiss();if(overlayDialog==dialog)overlayDialog=null;handler.post(()->{if(menuEpoch==gate.epoch())select.accept(index);});}).setNegativeButton("Отмена",null).create();
  // Remove the menu input window before a selection can start gestures.
  overlayDialog=d;d.setOnDismissListener(dialog->{if(overlayDialog==d)overlayDialog=null;});d.getWindow().setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);d.getWindow().addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);d.getWindow().setWindowAnimations(0);d.show();
 }
 private void chooseWindow(){
  stop("Выберите окно с игрой");if(busy){note="Дождитесь завершения касания";update();return;}
  chooseWindow(gate.epoch(),SystemClock.elapsedRealtime()+2000);
 }
 private void chooseWindow(long epoch,long until){
  if(epoch!=gate.epoch()||destroying)return;
  java.util.List<String> owners=new java.util.ArrayList<>(),labels=new java.util.ArrayList<>();java.util.List<Integer> ids=new java.util.ArrayList<>();java.util.List<Rect> bounds=new java.util.ArrayList<>();
  for(AccessibilityWindowInfo w:getWindows())if(w.getType()==AccessibilityWindowInfo.TYPE_APPLICATION){AccessibilityNodeInfo r=windowRoot(w);if(r==null)continue;try{
   String pkg=String.valueOf(r.getPackageName());Rect b=new Rect();w.getBoundsInScreen(b);if(b.isEmpty())continue;
   owners.add(pkg);ids.add(w.getId());bounds.add(b);labels.add(applicationLabel(pkg)+" · "+b.width()+"×"+b.height()+(w.isFocused()?" · активное":"")+"\n"+pkg);
  }finally{r.recycle();}}
  if(labels.isEmpty()){
   // Window metadata may lag a newly opened Activity, especially on Android 11.
   // Re-read only; a window still needs explicit selection and no input is armed.
   if(SystemClock.elapsedRealtime()<until){note="Ожидаю список окон Android…";update();handler.postDelayed(()->chooseWindow(epoch,until),200);return;}
   note="Нет доступных окон. Откройте игру и повторите выбор";update();return;
  }
  overlayMenu("Окно игры",labels.toArray(new String[0]),i->{explicitWindow=true;if(!owners.get(i).equals(visualOwner)||ids.get(i)!=visualWindow||!bounds.get(i).equals(visualWindowBounds)){visualRoi=null;manualVisualRoi=false;}visualOwner=owners.get(i);visualWindow=ids.get(i);visualWindowBounds=new Rect(bounds.get(i));awaitChosenWindow(owners.get(i),ids.get(i),bounds.get(i),epoch,SystemClock.elapsedRealtime()+12000);});
 }
 private void awaitChosenWindow(String pkg,int id,Rect expected,long epoch,long until){
  if(epoch!=gate.epoch())return;AccessibilityNodeInfo r=appRoot();boolean ready=false;
  try{ready=r!=null&&unlocked()&&pkg.equals(String.valueOf(r.getPackageName()))&&id==r.getWindowId()&&expected.equals(windowBounds(r));}finally{if(r!=null)r.recycle();}
  if(ready){arm();if(gate.armed()){note="Окно выбрано · "+targetLabel+". Выберите область или нажмите Старт";update();}return;}
  if(SystemClock.elapsedRealtime()>=until){note="Окно не стало активным. Коснитесь игры и выберите окно снова";update();return;}
  note="Коснитесь выбранного окна игры · ожидаю фокус";update();handler.postDelayed(()->awaitChosenWindow(pkg,id,expected,epoch,until),250);
 }
 private void chooseProfile(){stop("Выберите механику игры");overlayMenu("Механика · выбирайте по управлению, не по оформлению",new String[]{ArcadeProfile.ARI.label,ArcadeProfile.PADDLE.label,ArcadeProfile.TRAFFIC.label,ArcadeProfile.FLAPPY.label,"2048","Цветной2048","Мэтч Ворлд · обмен соседей","Фишки · удалить группу","Имя игры · отдельное обучение","Шипучка · обычные шары (эксперимент)","Фишки · провести прямую цепочку","Нащупай ход · обмен, избегать черепов"},i->{if(i==8){nameLearningGame();return;}unsupportedGame=false;if(selectedProfile!=i){visualRoi=null;manualVisualRoi=false;boardRoi=null;}selectedProfile=i;if(i<4)captureProfile=ArcadeProfile.values()[i];getSharedPreferences("panel",MODE_PRIVATE).edit().putInt("profile",i).apply();AccessibilityNodeInfo root=appRoot();try{if(root!=null){String pkg=String.valueOf(root.getPackageName());getSharedPreferences("game-profiles",MODE_PRIVATE).edit().putInt(profileChoiceKey(pkg),i).apply();browserProfileConfirmed=true;confirmedProfileScope=profileChoiceKey(pkg);}}finally{if(root!=null)root.recycle();}note="Выбрано: "+profileLabel()+". Другая графика: Ещё → Настроить распознавание. Затем Старт";update();if(i==9){if(bindSelectedWindow())selectVisualRegion();}else if(i>=6)configureGrid();});}
 private void nameLearningGame(){
  AccessibilityNodeInfo root=appRoot();if(root==null){note="Сначала откройте приложение с игрой";update();return;}
  final String pkg;try{pkg=String.valueOf(root.getPackageName());}finally{root.recycle();}
  EditText name=new EditText(this);name.setSingleLine(true);name.setHint("Например: игра на сайте. Пусто — основной профиль");
  String saved=getSharedPreferences("panel",MODE_PRIVATE).getString("game_slot."+pkg,"default");name.setText(saved.equals("default")?"":saved);
  AlertDialog d=new AlertDialog.Builder(this).setTitle("Отдельное обучение · "+applicationLabel(pkg)).setView(name)
   .setMessage("Для разных игр в одном браузере указывайте разные имена. Перед сменой игры выберите нужное имя.")
   .setNegativeButton("Отмена",null).setPositiveButton("Сохранить",(dialog,which)->{
    String slot=name.getText().toString().trim();if(slot.isEmpty())slot="default";
    if(slot.length()>80){note="Имя должно быть не длиннее 80 символов";update();return;}
    if(!slot.equals(saved)){visualRoi=null;manualVisualRoi=false;boardRoi=null;clearDiagnosticFrame();}
    getSharedPreferences("panel",MODE_PRIVATE).edit().putString("game_slot."+pkg,slot).apply();
    learningSlot=slot;browserProfileConfirmed=false;note="Имя игры сохранено · выберите профиль и нажмите Старт";update();
   }).create();
  overlayDialog=d;d.setOnDismissListener(dialog->{if(overlayDialog==d)overlayDialog=null;});d.getWindow().setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);d.show();
 }
 private String profileLabel(){if(unsupportedGame)return "Тип игры не поддерживается";return selectedProfile<4?ArcadeProfile.values()[selectedProfile].label:switch(selectedProfile){case 4->"2048";case 5->"Цветной2048";case 6->"Мэтч Ворлд · обмен";case 9->"Шипучка · обычные шары";case 10->"Фишки · прямая цепочка";case 11->"Нащупай ход · обмен";default->"Фишки · группы";};}
 private boolean bindSelectedWindow(){
  if(gate.armed()){String reason=windowRejection(null,false,true);if(reason==null)return true;stop("Выбранное окно недоступно ["+reason+"] · выберите Окно снова");return false;}
  if(explicitWindow){AccessibilityNodeInfo r=appRoot();boolean same=false;try{same=r!=null&&visualOwner.equals(String.valueOf(r.getPackageName()))&&visualWindow==r.getWindowId()&&visualWindowBounds.equals(windowBounds(r));}finally{if(r!=null)r.recycle();}
   if(!same){note="Выбранное окно изменилось. Нажмите Окно и выберите игру";update();return false;}}
  arm();return gate.armed();
 }
 private void startSelected(){
  if(!bindSelectedWindow())return;
  if(!playingJumper&&!playingGrid&&!playing2048&&!playingMini){
   int saved=getSharedPreferences("game-profiles",MODE_PRIVATE).getInt(profileChoiceKey(target),-1);
   if(unsupportedGame||VisualGameTarget.requiresProfileChoice(saved,targetIsBrowser,browserProfileConfirmed&&profileChoiceKey(target).equals(confirmedProfileScope))){chooseProfile();return;}
   if(selectedProfile!=saved){selectedProfile=saved;visualRoi=null;manualVisualRoi=false;boardRoi=null;}
  }
  if(selectedProfile==9){startBubble();return;}
  if(gridSelected()){startGrid();return;}
  if(playingJumper&&(arcadeFeedback.paused()||jumperResponsePaused)&&selectedProfile<4&&ArcadeProfile.values()[selectedProfile]==captureProfile){
   if(busy){note="Дождитесь завершения касания";update();return;}
   arcadeController.reset();if(arcadeLearning!=null)arcadeController.restore(arcadeLearning.get(target,modelSlot,captureProfile));arcadeFeedback.reset();jumperFeedback.reset();arcadePending=null;arcadeBefore=null;arcadeAck=false;arcadeLastUsable=null;sceneRecovery.reset();jumperResponsePaused=false;jumperResponseFailures=0;jumperAwaitingVerification=false;
   note="Продолжаю текущий захват · проверяю свежую сцену";update();return;
  }
  if(selectedProfile>=4&&manualVisualRoi&&visualRoi!=null)boardRoi=new Rect(visualRoi);
  if(selectedProfile<4){startVisualArcade(ArcadeProfile.values()[selectedProfile]);return;}
  if(!gate.armed())arm();if(!gate.armed())return;
  if(boardRoi==null){
   if(selectedProfile==5){note="Выделите цветное поле 4×4 · обычная палитра здесь не используется";update();selectVisualRegion();}
   else startBoundDiscovery(gate.epoch());
   return;
  }
  if(selectedProfile==5)startColored2048();else start2048();
 }
 private void chooseRegion(){stop("Выберите область игры");if(!bindSelectedWindow())return;
  if(selectedProfile==9){selectVisualRegion();return;}
  if(gridSelected()){loadGridConfig();selectVisualRegion();return;}
  if(selectedProfile>=4){selectVisualRegion();return;}
  overlayMenu("Область внутри выбранного окна",new String[]{"Автоматически · убрать боковые поля","Выделить прямоугольник","Всё выбранное окно","Показать последний кадр области"},i->{
   if(i==3){showRegionPreview();return;}
   visualRoi=null;manualVisualRoi=i==2;sceneRecovery.reset();if(i==2)visualRoi=new Rect(armedBounds);
   if(i==1)selectVisualRegion();else{note="Область: "+(i==2?"всё окно":"автоматически")+" · нажмите Старт";update();}
  });
 }
 private void showRegionPreview(){
  Bitmap copy=copyDiagnosticFrame();if(copy==null){note="Нет кадра. Нажмите Старт и разрешите захват, затем откройте Область снова";update();return;}
  ImageView image=new ImageView(this);image.setImageBitmap(copy);image.setAdjustViewBounds(true);image.setMaxHeight(dp(360));
  AlertDialog d=new AlertDialog.Builder(this).setTitle("Последний кадр · "+copy.getWidth()+"×"+copy.getHeight()).setView(image).setMessage("Здесь должна быть игра. Если видна панель или другое приложение — выберите Окно и выделите Область заново.").setPositiveButton("Закрыть",null).create();
  overlayDialog=d;d.setOnDismissListener(dialog->{if(overlayDialog==d)overlayDialog=null;image.setImageDrawable(null);if(!copy.isRecycled())copy.recycle();});d.getWindow().setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);d.getWindow().addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);d.show();
 }
 private void selectVisualRegion(){
  long epoch=gate.epoch();
  String initialRejection=windowRejection(null,false,true);if(epoch!=gate.epoch())return;
  if(initialRejection!=null){note="Сначала выберите активное окно игры";update();return;}
  Rect window=new Rect(armedBounds);compact(true);
  GameAreaOverlay overlay=new GameAreaOverlay(this,window,selectedProfile==9?1:gridSelected()?gridConfig.rows:4,selectedProfile==9?1:gridSelected()?gridConfig.cols:4,new GameAreaOverlay.Listener(){
   public void selected(Rect b){if(epoch!=gate.epoch())return;View old=picker;picker=null;removeOverlay(old);compact(false);
    // Choosing geometry observes the binding; it does not authorize an action.
    // Slow window metadata must not silently discard the user's confirmed area.
    String reason=windowRejection(null,false,true);if(epoch!=gate.epoch())return;if(reason!=null){stop("Область не сохранена: окно изменилось ["+reason+"] · выберите его заново");return;}
    if(b==null||b.isEmpty()||!window.contains(b)||!armedBounds.contains(b)){stop("Область не сохранена: прямоугольник вне выбранного окна");return;}
    visualRoi=new Rect(b);manualVisualRoi=true;boardRoi=selectedProfile>=4?new Rect(b):null;if(selectedProfile==9)saveBubbleRegion(b);else if(gridSelected())saveGridRegion(b);sceneRecovery.reset();clearDiagnosticFrame();note="Область выбрана: "+b.width()+"×"+b.height()+" · нажмите Старт";update();}
   public void cancelled(){if(epoch!=gate.epoch())return;stop("Выбор области отменён");compact(false);}
  });
  WindowManager.LayoutParams lp=new WindowManager.LayoutParams(-1,-1,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN|WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,PixelFormat.TRANSLUCENT);lp.gravity=Gravity.TOP|Gravity.LEFT;
  try{wm.addView(overlay,lp);picker=overlay;handler.postDelayed(()->{if(picker==overlay){stop("Время выбора области истекло");compact(false);}},30000);}catch(RuntimeException error){stop("Не удалось открыть выбор области");compact(false);}
 }
 private void moreOptions(){
  stop("Дополнительные действия · STOP");overlayMenu("Ещё",new String[]{"Диагностика сеанса","Калибровка Ари","Автополе2048","Выбрать установленную игру","Только чтение","Включить автоуправление","Демо ×10","Скрыть панель","Отключить службу","Настроить распознавание","Способ управления","Вернуть стандартное распознавание","Размер сетки фишек","Приоритетный цвет фишек","Помощь по панели и играм"},i->{switch(i){
   case 0->showServiceDialog(new AlertDialog.Builder(this).setTitle("Последний сеанс").setMessage(lastSessionReport+"\n"+captureStartup.report()).setPositiveButton("Закрыть",null).create());
   case 1->{selectedProfile=0;startVisualArcade(ArcadeProfile.ARI);if(playingJumper)jumperCalibrationPending=true;}
   case 2->startAutomaticGame();case 3->startActivity(new Intent(this,GamePickerActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));case 4->setAutomatic(false);case 5->setAutomatic(true);case 6->{arm();if(gate.armed())startDemo();}case 7->hidePanel();case 8->disableSelf();
   case 9->startCustomCalibration();case 10->chooseControlMode();case 11->resetCustomVision();
   case 12->configureGrid();
   case 13->chooseGridGoal();
   case 14->showPanelHelp();
  }});
 }


 private void showPanelHelp(){
  String message="Окно — выберите окно игры. Игра — выберите её механику. Область — отметьте игровое поле, если требуется. Старт — начать выбранный режим. Ещё — настройки и диагностика; открытие останавливает игру. Свернуть — уменьшить панель. STOP — немедленно остановить ввод.\n\n"
   +"Для сетки: откройте игру → Окно → Игра → выберите механику → Область → обведите поле → Старт. Размер сетки задаётся в «Ещё». Неизвестные клетки: только наблюдение. 2048: обведите плитки 4×4; Шипучка: обведите ряды шаров до пушки. Шипучка: экспериментально только обычные шары. Прыгун Ари, Карканоид, Маркет Раш и Гошан: отдельные аркадные профили.\n\n"
   +"Гексон и Котобашня пока не поддерживаются. Для другой игры выбирайте механику по управлению. Успех действий и работа на конкретном телефоне не гарантированы.";
  showServiceDialog(new AlertDialog.Builder(this).setTitle("Панель и игры").setMessage(message).setPositiveButton("Закрыть",null).create());
 }


 @Override protected void onServiceConnected(){CrashDiagnostics.phase("SERVICE_CONNECTED");instance=this;try{experience=new ExperienceStore(new java.io.File(getFilesDir(),"experience-v1.log"));}catch(java.io.IOException e){experience=null;}try{arcadeLearning=new ArcadeLearningStore(new java.io.File(getFilesDir(),"arcade-learning-v1.log"));}catch(java.io.IOException e){arcadeLearning=null;}try{journal=new RotatingJournal(new java.io.File(getFilesDir(),"audit-segments"),new java.io.File(getFilesDir(),"audit-v1.log"));}catch(java.io.IOException e){journal=null;}wm=(WindowManager)getSystemService(WINDOW_SERVICE);gate.stop();displayGeometry=readDisplayGeometry();showPanel();}
 public void showPanel(){if(destroying)return;if(panel!=null){restoreConsentPanel();compact(false);return;}selectedProfile=getSharedPreferences("panel",MODE_PRIVATE).getInt("profile",0);if(!VisualGameTarget.supportedProfile(selectedProfile))selectedProfile=0;gate.stop();consentPanelDetached=false;panelRegistered=false;panelCompact=false;controls=null;manualControls.clear();panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(8,4,8,4);panel.setBackgroundColor(0xEE202830);
  TextView header=new TextView(this);header.setText("LocalPilot · тянуть здесь");header.setTextColor(Color.WHITE);panel.addView(header);
  header.setOnTouchListener(new View.OnTouchListener(){float x,y;int ox,oy;public boolean onTouch(View v,android.view.MotionEvent e){if(layout==null)return true;if(e.getAction()==MotionEvent.ACTION_DOWN){x=e.getRawX();y=e.getRawY();ox=layout.x;oy=layout.y;return true;}if(e.getAction()==MotionEvent.ACTION_MOVE){layout.x=ox+(int)(e.getRawX()-x);layout.y=oy+(int)(e.getRawY()-y);updatePanelLayout();return true;}return true;}});
  state=new TextView(this);state.setTextColor(Color.WHITE);state.setTextSize(11);state.setMaxLines(8);panel.addView(state);
  controls=new LinearLayout(this);controls.setOrientation(LinearLayout.VERTICAL);panel.addView(controls);
  row("Окно",this::chooseWindow,"Игра",this::chooseProfile);
  row("Область",this::chooseRegion,"Старт",this::startSelected);
  row("Ещё",this::moreOptions,"Свернуть",()->compact(true));
  Button stopButton=new Button(this);stopButton.setText("STOP");stopButton.setOnClickListener(v->stop("STOP"));panel.addView(stopButton,new LinearLayout.LayoutParams(-1,dp(44)));
  miniBar=new LinearLayout(this);miniBar.setOrientation(LinearLayout.HORIZONTAL);miniBar.setVisibility(View.GONE);
  miniState=new TextView(this);miniState.setTextColor(Color.WHITE);miniState.setTextSize(9);miniState.setMaxLines(2);miniBar.addView(miniState,new LinearLayout.LayoutParams(0,dp(48),1));
  Button miniStop=new Button(this);miniStop.setText("STOP");miniStop.setTextSize(10);miniStop.setPadding(0,0,0,0);miniStop.setMinWidth(0);miniStop.setOnClickListener(v->stop("STOP"));miniBar.addView(miniStop,new LinearLayout.LayoutParams(dp(48),dp(48)));
  Button expand=new Button(this);expand.setText("Развернуть");expand.setTextSize(8);expand.setPadding(0,0,0,0);expand.setMinWidth(0);expand.setOnClickListener(v->compact(false));miniBar.addView(expand,new LinearLayout.LayoutParams(dp(56),dp(48)));
  miniState.setOnTouchListener(new View.OnTouchListener(){float sx,sy;int ox,oy;public boolean onTouch(View v,MotionEvent e){if(layout==null)return true;if(e.getAction()==MotionEvent.ACTION_DOWN){sx=e.getRawX();sy=e.getRawY();ox=layout.x;oy=layout.y;return true;}if(e.getAction()==MotionEvent.ACTION_MOVE){layout.x=ox+(int)(e.getRawX()-sx);layout.y=oy+(int)(e.getRawY()-sy);updatePanelLayout();return true;}return true;}});
  panel.addView(miniBar);
  layout=new WindowManager.LayoutParams(dp(240),WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,PixelFormat.TRANSLUCENT);layout.gravity=Gravity.TOP|Gravity.LEFT;layout.x=0;layout.y=80;
  panel.addOnLayoutChangeListener((view,l,t,r,b,ol,ot,or,ob)->{if(r-l!=or-ol||b-t!=ob-ot)handler.post(()->{if(panel==view)updatePanelLayout();});});
  updatePanelLayout();update();
 }
 private void removeOverlay(View view){if(view!=null&&wm!=null)CaptureCleanup.run(()->wm.removeViewImmediate(view));}
 private void clearPanel(){View old=panel;panel=null;controls=null;state=null;miniBar=null;miniState=null;collapseButton=null;layout=null;consentPanelDetached=false;panelRegistered=false;panelCompact=false;manualControls.clear();removeOverlay(old);}
 /** A missing/stale WindowManager attachment never leaves input authority live. */
 private boolean updatePanelLayout(){
  if(panel==null||layout==null||wm==null||consentPanelDetached||destroying)return false;
  try{
   Point size=new Point();wm.getDefaultDisplay().getRealSize(size);
   int width=layout.width>0?layout.width:panel.getWidth(),height=Math.max(dp(48),panel.getHeight());
   layout.x=Math.max(0,Math.min(layout.x,Math.max(0,size.x-width)));
   layout.y=Math.max(0,Math.min(layout.y,Math.max(0,size.y-height-dp(24))));
   // addView registers synchronously, while isAttachedToWindow becomes true at
   // the first traversal. Repeated show/resize before that traversal must update.
   if(panelRegistered)wm.updateViewLayout(panel,layout);else{wm.addView(panel,layout);panelRegistered=true;}
   return true;
  }catch(RuntimeException|OutOfMemoryError error){
   permit.revoke();gate.stop();clearPanel();
   if(!destroying)handler.post(()->stop("Панель недоступна · откройте LocalPilot и нажмите «Показать панель»"));
   return false;
  }
 }
 public void recoverPanel(){stop("Панель восстановлена · выберите окно игры");browserProfileConfirmed=false;confirmedProfileScope="";showPanel();if(layout!=null){layout.x=0;layout.y=dp(48);updatePanelLayout();}update();}
 private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density);}
 private void row(String a,Runnable ar,String b,Runnable br){row(a,ar,b,br,false);}
 private void manualRow(String a,Runnable ar,String b,Runnable br){row(a,ar,b,br,true);}
 private void single(String label,Runnable callback){Button v=new Button(this);v.setText(label);v.setTextSize(12);v.setOnClickListener(x->callback.run());controls.addView(v,new LinearLayout.LayoutParams(-1,dp(40)));}
 private void row(String a,Runnable ar,String b,Runnable br,boolean needsInput){LinearLayout line=new LinearLayout(this);for(int i=0;i<2;i++){Button v=new Button(this);v.setText(i==0?a:b);v.setTextSize(12);Runnable r=i==0?ar:br;v.setOnClickListener(x->r.run());if(needsInput)manualControls.add(v);line.addView(v,new LinearLayout.LayoutParams(0,dp(40),1));}(controls==null?panel:controls).addView(line);}
 public void hidePanel(){try{stop("STOP");}finally{clearPanel();}}
 private String captureCounters(){return "Захват: кадры="+captureFrames+" · READ="+captureReads+" · решения="+captureDecisions+" · ввод="+captureSent+" · ACK="+captureCompleted+(playingJumper?" · MediaProjection pacerAccepted="+captureAcceptedFrames+" · skippedFrames="+captureSkippedFrames+" (pacing="+captureSkippedThrottle+", idle="+captureSkippedUnwanted+", clock="+captureSkippedClock+")":" · Accessibility capture (MediaProjection counters N/A)");}
 private void update(){
  boolean inputReady=gate.armed()&&!gate.paused()&&permit.allows(gate.epoch(),SystemClock.elapsedRealtime());for(Button control:manualControls)control.setEnabled(inputReady&&!busy);
  long renderNow=SystemClock.elapsedRealtime();if(playingJumper&&renderNow-lastPanelRender<100)return;lastPanelRender=renderNow;
  if(miniState!=null){
   String miniLabel=playingGrid
     ?(selectedProfile==9?"Шипучка · READ"+captureReads+" · ввод"+captureSent:
       gridConfig.rows+"×"+gridConfig.cols+" · READ"+captureReads+" · ввод"+captureSent)
     :playingJumper?captureStartup.label()+" · READ"+captureReads+" · ввод"+captureSent
     :gate.armed()?"Окно выбрано":"STOP";
   miniState.setText(miniLabel+"\n"+note);
  }
  if(state!=null)state.setText(profileLabel()+" · "+(manualVisualRoi?"область вручную":visualRoi!=null?"боковые поля убраны":"область авто")+"\n"+note+"\n"+(gate.armed()?targetLabel+" · "+(inputReady?"ввод включён":"наблюдение"):"Нажмите Старт или выберите Окно")+(captureSession>0||playingGrid?"\n"+captureCounters():"")+(collecting?"\nСбор примеров включён":""));
 }
 private AccessibilityNodeInfo windowRoot(AccessibilityWindowInfo window){return Build.VERSION.SDK_INT>=33?window.getRoot(0):window.getRoot();}
 private AccessibilityNodeInfo appRoot(){for(AccessibilityWindowInfo w:getWindows())if(w.getType()==AccessibilityWindowInfo.TYPE_APPLICATION&&w.isFocused()){AccessibilityNodeInfo r=windowRoot(w);if(r!=null){if(r.getWindowId()==w.getId())return r;r.recycle();}}return null;}
 private boolean unlocked(){return ((PowerManager)getSystemService(POWER_SERVICE)).isInteractive()&&!((KeyguardManager)getSystemService(KEYGUARD_SERVICE)).isKeyguardLocked();}
 private void arm(){stop("Привязка");if(busy){note="Дождитесь завершения жеста";update();return;}AccessibilityNodeInfo r=appRoot();try{if(r==null||!unlocked()){note="Нет активного приложения";update();return;}target=String.valueOf(r.getPackageName());targetLabel=applicationLabel(target);targetIsBrowser=browserTarget();armedBounds=windowBounds(r);if(armedBounds.isEmpty()){note="Нет границ окна";update();return;}retainVisualScope(r);gate.arm(target,r.getWindowId(),armedBounds.width(),armedBounds.height());if(automatic())permit.grantSession(gate.epoch(),SystemClock.elapsedRealtime());note=automatic()?"Окно выбрано · нажмите Старт":"Только наблюдение";update();}finally{if(r!=null)r.recycle();}}
 private String applicationLabel(String packageName){try{return String.valueOf(getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(packageName,0)));}catch(Exception e){return packageName;}}
 private void stop(String reason){
  boolean stoppedGame=playingJumper||playingGrid||playing2048||playingMini;
  boolean wasArmed=gate.armed();permit.revoke();gate.stop();
  if(!reason.startsWith("Экспорт"))lastStopReason=reason;
  if(playingJumper||playingGrid||wasArmed||autoLeft>0)try{lastSessionReport=diagnosticState();}catch(RuntimeException|OutOfMemoryError ignored){lastSessionReport="Сеанс остановлен; метаданные недоступны";}
  if(playingJumper)CrashDiagnostics.captureStopped();
  // Save measured physics before reset, including measurements made while no
  // gesture was needed. Stopping a session is not a new round loss.
  if(playingJumper&&jumperCaptureReady)CaptureCleanup.run(()->persistArcadeModel(false));
  captureStartup.stop(SystemClock.elapsedRealtime());clearDiagnosticFrame();arcadeSceneGeneration++;
  playingGrid=false;gridSession.reset();gridPalette=null;gridPendingDecision=null;gridUnknownReasons="none";bubbleSession.reset();bubbleGeometry=null;bubblePendingShot=null;
  arcadeLastUsable=null;arcadeController.reset();arcadeFeedback.reset();jumperFeedback.reset();arcadeState=null;arcadeBefore=null;arcadePending=null;arcadeAck=false;
  boolean stopJumper=playingJumper||jumperCaptureReady||jumperConsentPending;
  playingJumper=false;jumperCaptureReady=false;jumperConsentPending=false;jumperVisionPending=false;jumperAwaitingVerification=false;jumperCalibrationPending=false;jumperState=null;
  if(stoppedGame)browserProfileConfirmed=false;
  customCalibrationPending=false;View custom=customCalibrationOverlay;customCalibrationOverlay=null;removeOverlay(custom);
  synchronized(jumperFrameLock){Bitmap queued;while((queued=jumperQueuedFrames.poll())!=null)if(!queued.isRecycled())queued.recycle();}
  View calibration=jumperCalibrationOverlay;jumperCalibrationOverlay=null;removeOverlay(calibration);
  if(stopJumper)CaptureCleanup.run(()->stopService(new Intent(this,JumperCaptureService.class)));
  audit(SafeJournal.Event.STOP,0,0);boardSearch.reset();boardReadMisses=0;boardReadGeneration++;windowFocus.reset();focusPaused=false;focusWatch=false;focusWatchId++;autoLeft=0;playing2048=false;colored2048=false;colorSession.reset();playingMini=false;discoveringBoard=false;boardRoi=null;
  if(visionPending){visionPending=false;visionRequest++;if(visionTask!=null)visionTask.cancel(true);visionTask=null;metrics.cancelled(StageMetrics.Stage.READ);}
  if(captureBusy){metrics.cancelled(StageMetrics.Stage.CAPTURE);captureBusy=false;pendingCapture=++captureCounter;}
  if(overlayDialog!=null){AlertDialog old=overlayDialog;overlayDialog=null;CaptureCleanup.run(old::dismiss);}
  if(preview!=null){AlertDialog old=preview;preview=null;CaptureCleanup.run(old::dismiss);}View oldPicker=picker;picker=null;removeOverlay(oldPicker);
  handler.removeCallbacksAndMessages(null);note=reason;restoreConsentPanel();update();
 }
 private void failStage(StageMetrics.Stage stage,String code,String message){metrics.failure(stage,code);stop(stage+" / "+code+": "+message);compact(false);}
 private String invalidReason(ActionGate.Ticket t){return invalidReason(t,false);}
 private String invalidReason(ActionGate.Ticket t,boolean completion){return windowRejection(t,completion,false);}
 /** Read-only notifications observe identity; real inputs always validate their original ticket. */
 private String windowRejection(ActionGate.Ticket ticket,boolean completion,boolean observationOnly){
  AccessibilityNodeInfo root=null;
  try{
   if(!gate.armed())return "NOT_BOUND";if(!unlocked())return "DEVICE_LOCKED";
   root=appRoot();if(root==null)return "TARGET_WINDOW_UNAVAILABLE";
   Rect bounds=windowBounds(root);String pkg=String.valueOf(root.getPackageName());int id=root.getWindowId();
   if(!unlocked())return "DEVICE_LOCKED";if(!target.equals(pkg))return "TARGET_APP_CHANGED";if(bounds.isEmpty())return gate.identityRejection(pkg,id,0,0);if(!bounds.equals(armedBounds))return "TARGET_BOUNDS_CHANGED";
   long now=SystemClock.elapsedRealtime();
   // A slow Binder query is not an expired action in this read-only path.
   if(observationOnly)return gate.identityRejection(pkg,id,bounds.width(),bounds.height());
   return completion?gate.completionRejection(ticket,now,pkg,id,bounds.width(),bounds.height()):gate.rejection(ticket,now,pkg,id,bounds.width(),bounds.height());
  }catch(RuntimeException error){return "WINDOW_STATE_UNAVAILABLE";}
  finally{if(root!=null)root.recycle();}
 }
 /** Pause retains capture/calibration, but invalidates every pre-pause action ticket. */
 private boolean deferFocus(String reason){
  if("FOCUS_CHANGED".equals(reason))return true; // stale callback/plan, never retry its action
  if(!(playingJumper||playing2048||playingGrid||discoveringBoard)||!WindowFocus.transientLoss(reason))return false;
  if(windowFocus.observe(gate.epoch(),reason,SystemClock.elapsedRealtime())!=WindowFocus.State.WAITING)return false;
  if(WindowFocus.interruptedArcade(playingJumper,arcadePending!=null,jumperAwaitingVerification)||WindowFocus.interrupted2048(playing2048,before2048!=null)){stop("Фокус потерян во время непроверенного жеста · STOP");return true;}
  if(!focusPaused){
   focusPaused=true;gate.pause();boardReadGeneration++;arcadeSceneGeneration++;
   arcadeFeedback.reset();jumperFeedback.reset();arcadeController.nextRound();
   jumperAwaitingVerification=false;jumperResponsePaused=false;jumperResponseFailures=0;
   arcadePending=null;arcadeBefore=null;arcadeLastUsable=null;arcadeState=null;jumperState=null;
   captureBusy=false;pendingCapture=++captureCounter;
   if(visionPending&&visionTask!=null){visionTask.cancel(true);visionTask=null;}visionPending=false;visionRequest++;
   gridSession.invalidateCandidate();bubbleSession.invalidateCandidate();
   note="Фокус временно потерян · ввод приостановлен, захват сохранён";update();
  }
  if(!focusWatch){focusWatch=true;long epoch=gate.epoch(),watch=++focusWatchId;handler.postDelayed(()->watchFocus(epoch,watch),75);}
  return true;
 }
 private void watchFocus(long epoch,long watch){
  if(epoch!=gate.epoch()||!focusPaused||watch!=focusWatchId)return;
  String reason=windowRejection(null,false,true);
  WindowFocus.State state=windowFocus.observe(epoch,reason,SystemClock.elapsedRealtime());
  if(state==WindowFocus.State.STOP){focusWatch=false;stop("Фокус не восстановлен за 600 мс — STOP ["+reason+"]");return;}
  if(state==WindowFocus.State.WAITING){handler.postDelayed(()->watchFocus(epoch,watch),75);return;}
  gate.resume();focusPaused=false;focusWatch=false;focusWatchId++;focusRecoveredAt=SystemClock.elapsedRealtime();
  note="Прежнее окно восстановлено · ожидаю свежий кадр";update();
  if(playingGrid)scheduleGrid(epoch);
  else if(playing2048)postBoardTask(epoch,captureDelay(),()->round2048(epoch));
  else if(discoveringBoard)postBoardTask(epoch,captureDelay(),()->findAutomaticBoard(epoch,SystemClock.elapsedRealtime()));
 }
 private boolean valid(ActionGate.Ticket t){return invalidReason(t)==null;}
 private void manual(int dx,int dy){if(autoLeft>0||playing2048||playingMini||playingJumper||playingGrid){stop("Авторежим остановлен; нажмите ещё раз");return;}if(!gate.armed()){note="Сначала нажмите «Привязать»";update();return;}if(!permit.allows(gate.epoch(),SystemClock.elapsedRealtime())){note="Нажмите «Разрешить ввод» для запуска";update();return;}float x=armedBounds.left+Math.min(.99f,nx/100f)*armedBounds.width(),y=armedBounds.top+Math.min(.99f,ny/100f)*armedBounds.height();send(x,y,x+dx*armedBounds.width()*.18f,y+dy*armedBounds.height()*.18f,gate.offer(SystemClock.elapsedRealtime()),null);}
 private boolean audit(SafeJournal.Event event,long request,long metric){return auditAt(event,request,metric,gate.epoch());}
 private boolean auditAt(SafeJournal.Event event,long request,long metric,long scope){try{if(journal==null)return false;journal.append(event,scope,request,SystemClock.elapsedRealtime(),metric);return true;}catch(java.io.IOException|RuntimeException e){journal=null;permit.revoke();note="Журнал недоступен: ввод заблокирован";return false;}}
 public byte[] exportAudit()throws java.io.IOException{stop("Экспорт: ввод остановлен");if(journal!=null)return journal.snapshot();throw new java.io.IOException("Журнал недоступен; исходник audit-v1.log сохранён");}
 private boolean automatic(){return getSharedPreferences(MainActivity.PREF,MODE_PRIVATE).getBoolean("auto_control",true);}
 public void setAutomatic(boolean enabled){getSharedPreferences(MainActivity.PREF,MODE_PRIVATE).edit().putBoolean("auto_control",enabled).apply();stop(enabled?"Автоуправление включено; выберите игру":"Только наблюдение");}
 private void allowManual(){if(busy||discoveringBoard||playingJumper)return;getSharedPreferences(MainActivity.PREF,MODE_PRIVATE).edit().putBoolean("auto_control",true).apply();if(!gate.armed())arm();if(gate.armed()){permit.grantSession(gate.epoch(),SystemClock.elapsedRealtime());note="Ввод включён до STOP";update();}}

 private void startJumper(){startVisualArcade(ArcadeProfile.ARI);}
 private void startVisualArcade(ArcadeProfile profile){
  if(busy||captureBusy||discoveringBoard||autoLeft>0||playing2048||playingMini||playingJumper||playingGrid){note="Сначала остановите другой режим";update();return;}
  if(explicitWindow&&(!gate.armed()||!valid(gate.offer(SystemClock.elapsedRealtime())))){note="Выбранное окно недоступно · выберите Окно снова";update();return;}
  if(!gate.armed()||!valid(gate.offer(SystemClock.elapsedRealtime())))arm();
  if(!gate.armed()||!unlocked()){note="Откройте игру, выберите её окно и подходящий профиль на панели";update();return;}
  if(!VisualGameTarget.allowed(target,getPackageName())){note="Выберите приложение с игрой вместо системного окна";update();return;}
  if(!automatic()&&!permit.allows(gate.epoch(),SystemClock.elapsedRealtime())){note="Сначала разрешите ввод для этой сессии";update();return;}
  if(automatic())permit.grantSession(gate.epoch(),SystemClock.elapsedRealtime());
  learningSlot=getSharedPreferences("panel",MODE_PRIVATE).getString("game_slot."+target,"default");JumperPolicy.reset();
  String scope=VisualGameTarget.key(target,learningSlot,profile);
  controlMode=Math.max(0,Math.min(2,getSharedPreferences("controls",MODE_PRIVATE).getInt(scope,0)));
  customVision=null;String savedVision=getSharedPreferences("custom-vision",MODE_PRIVATE).getString(scope,null);
  if(savedVision!=null)try{customVision=CustomVisionConfig.decode(savedVision);}catch(IllegalArgumentException ignored){note="Калибровка повреждена · восстановите стандартное распознавание через Ещё";update();return;}
  modelSlot=VisualGameTarget.modelSlot(learningSlot,controlMode,customVision==null?null:customVision.encode());
  captureProfile=profile;jumperResponseFailures=0;jumperResponsePaused=false;arcadeLastUsable=null;arcadeController.reset();if(arcadeLearning!=null){arcadeController.restore(arcadeLearning.get(target,modelSlot,profile));JumperPolicy.restore(arcadeLearning.get(target,modelSlot,ArcadeProfile.ARI));}arcadeFeedback.reset();clearDiagnosticFrame();sceneRecovery.reset();arcadeRoundSeen=false;arcadeState=null;arcadeBefore=null;arcadePending=null;arcadeAck=false;
  jumperFeedback.reset();
  arcadeVerified=0;arcadeLastAction=0;
  roundOutcome.reset();
  jumperColorProfile=profile==ArcadeProfile.ARI?loadJumperColors(target):null;
  playingJumper=true;jumperConsentPending=true;jumperCaptureReady=false;
  jumperState=null;jumperAwaitingVerification=false;jumperLastActionMs=0;compact(true);positionJumperStopPanel();
  CrashDiagnostics.phase("CAPTURE_CONSENT");
  captureFrames=captureReads=captureDecisions=captureSent=captureCompleted=0;
  captureAcceptedFrames=captureSkippedFrames=captureSkippedThrottle=captureSkippedUnwanted=captureSkippedClock=0;
  captureSession=captureStartup.begin(SystemClock.elapsedRealtime());
  watchCaptureStartup(captureSession);
  note="1/5 · Подтвердите захват всего экрана Android";update();
  // Android/OEM consent buttons may reject touches while another window overlays them.
  // Remove our window completely until the system consent Activity has closed.
  detachPanelForConsent();
  if(!captureSessionActive(captureSession))return;
  try{startActivity(new Intent(this,JumperCaptureConsentActivity.class).putExtra(JumperCaptureService.EXTRA_SESSION,captureSession).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_MULTIPLE_TASK));}
  catch(RuntimeException error){stop("Не удалось открыть разрешение захвата экрана");}
 }

 private void detachPanelForConsent(){if(panel!=null&&!consentPanelDetached){View old=panel;consentPanelDetached=true;if(CaptureCleanup.run(()->wm.removeViewImmediate(old)))panelRegistered=false;else{clearPanel();stop("Не удалось убрать панель перед разрешением захвата");}}}
 private void restoreConsentPanel(){if(panel!=null&&consentPanelDetached&&!destroying){consentPanelDetached=false;updatePanelLayout();update();}}
 public void captureConsentUiClosed(long id){if(id==captureSession)restoreConsentPanel();}
 private void persistArcadeModel(boolean result){
  if(arcadeLearning==null){learningStatus="Хранилище обучения недоступно";return;}
  String model=captureProfile==ArcadeProfile.ARI?JumperPolicy.model():arcadeController.model();
  try{if(result)arcadeLearning.recordResult(target,modelSlot,captureProfile,model);else arcadeLearning.save(target,modelSlot,captureProfile,model);learningStatus=result?"Проигрыш записан один раз, измеренная физика сохранена":"Модель сохранена";}
  catch(java.io.IOException error){learningStatus="Ошибка сохранения: "+error.getClass().getSimpleName();}
 }

 /** Only the consent handoff may refresh the window ID; the bound app and bounds must match. */
 private boolean waitForCaptureTarget(long now){
  if(!jumperConsentPending||!unlocked())return false;
  AccessibilityNodeInfo root=appRoot();
  try{
   if(root!=null){
    String packageName=String.valueOf(root.getPackageName());
    if(target.equals(packageName)){
     Rect bounds=windowBounds(root);
     if(!bounds.equals(armedBounds))return false;
     boolean allowed=automatic()||permit.allows(gate.epoch(),now);
     gate.arm(target,root.getWindowId(),bounds.width(),bounds.height());
     visualOwner=target;visualWindow=root.getWindowId();visualWindowBounds.set(bounds);
     if(allowed)permit.grantSession(gate.epoch(),now);
     watchArcadeCapture(gate.epoch());
     note="Окно игры восстановлено · проверяю свежий кадр";update();return true;
    }
    if(!getPackageName().equals(packageName)&&!packageName.equals("com.android.systemui")
      &&!packageName.equals("com.android.permissioncontroller")&&!packageName.equals("com.google.android.permissioncontroller"))return false;
   }
   if(now-arcadeCaptureReadyAt<10000){note="Захват включён · ожидаю возврата в "+captureProfile.label;update();return true;}
   return false;
  }finally{if(root!=null)root.recycle();}
 }

 private void positionJumperStopPanel(){
  if(panel==null||layout==null)return;Point size=new Point();wm.getDefaultDisplay().getRealSize(size);
  layout.x=Math.max(0,(size.x-dp(160))/2);
  layout.y=captureProfile==ArcadeProfile.FLAPPY?armedBounds.top+(int)(armedBounds.height()*.73f):Math.max(0,armedBounds.top+dp(4));
  if(!updatePanelLayout())stop("Не удалось разместить панель STOP");
 }

 private void showJumperDiagnostics(){
  if(captureProfile!=ArcadeProfile.ARI){showArcadeDiagnostics();return;}
  if(jumperState==null||jumperState.player==null){showServiceDialog(new AlertDialog.Builder(this).setTitle("Прыгун Ари · диагностика")
    .setMessage("Распознавание запускается после выбора игрового окна и разрешения захвата экрана. Графика игры должна соответствовать профилю.\n"+captureStartup.report())
    .setPositiveButton("Понятно",null).create());return;}
  JumperState s=jumperState;
  String message="Игровое поле: "+s.frameWidth+"×"+s.frameHeight
    +"\nУверенность: "+String.format(java.util.Locale.ROOT,"%.2f",s.confidence)
    +"\nАри: "+Math.round(s.player.centerX())+", "+Math.round(s.player.centerY())
    +" · vx="+Math.round(s.playerVx)+" px/s"
    +"\nПлатформы: "+s.platforms.size()+" · шипы: "+s.hazards.size()
    +"\nКадр: "+s.timestampMs+" мс"
    +"\nКасание подтверждено: "+(jumperAwaitingVerification?"ожидание движения":"да/ещё не выполнялось");
  showServiceDialog(new AlertDialog.Builder(this).setTitle("Прыгун Ари · диагностика").setMessage(message)
    .setPositiveButton("Понятно",null).create());
 }

 private void chooseControlMode(){
  if(selectedProfile==9){note="Шипучка: удержать шар в пушке, навести и отпустить";update();return;}
  if(gridSelected()){note=selectedProfile==11?"Нащупай ход: обмен соседей · черепа не трогаю":selectedProfile==6?"Мэтч Ворлд: обмен соседей · особые клетки пропускаю":selectedProfile==10?"Фишки: прямая цепочка · особые клетки пропускаю":"Фишки: связанная группа · особые клетки пропускаю";update();return;}
  if(selectedProfile>=4){note="Управление 2048: свайпы по выбранному полю";update();return;}
  if(!bindSelectedWindow())return;
  String slot=getSharedPreferences("panel",MODE_PRIVATE).getString("game_slot."+target,"default");
  String key=VisualGameTarget.key(target,slot,ArcadeProfile.values()[selectedProfile]);
  overlayMenu("Способ управления",new String[]{"Касания слева / справа; платформа — перетаскивание","Свайпы влево / вправо","Платформа следует за касанием"},i->{getSharedPreferences("controls",MODE_PRIVATE).edit().putInt(key,i).apply();controlMode=i;note="Управление сохранено для этой игры · нажмите Старт";update();});
 }
 private void resetCustomVision(){
  if(selectedProfile==9){stop("Геометрия Шипучки сброшена · нажмите Старт");return;}
  if(gridSelected()){if(!bindSelectedWindow())return;getSharedPreferences("grid-puzzle",MODE_PRIVATE).edit().remove(gridKey()+".palette").apply();gridPalette=null;note="Типы фишек сброшены · нажмите Старт для нового распознавания";update();return;}
  if(selectedProfile==5){if(!bindSelectedWindow())return;getSharedPreferences("color-2048",MODE_PRIVATE).edit().remove(color2048PaletteKey()).apply();colorSession.reset();note="Выученные цвета этой игры сброшены · начинаю с базовых плиток 2, 4, 8, 16";update();return;}
  if(selectedProfile>=4){note="Для 2048 настройте область поля";update();return;}
  if(!bindSelectedWindow())return;
  String slot=getSharedPreferences("panel",MODE_PRIVATE).getString("game_slot."+target,"default"),key=VisualGameTarget.key(target,slot,ArcadeProfile.values()[selectedProfile]);
  getSharedPreferences("custom-vision",MODE_PRIVATE).edit().remove(key).apply();customVision=null;
  if(selectedProfile==0){android.content.SharedPreferences.Editor prefs=getSharedPreferences("jumper-vision",MODE_PRIVATE).edit();for(String role:new String[]{"player","platform","hazard"}){prefs.remove(key+"."+role);if(VisualGameTarget.legacyMarket(target)&&slot.equals("default"))prefs.remove(target+"."+role);}prefs.apply();jumperColorProfile=null;}
  note="Стандартное распознавание восстановлено · нажмите Старт";update();
 }
 private void startCustomCalibration(){
  if(selectedProfile==9){note="Шипучка: выделите обычные шары и пушку через Область. Бонусы не распознаются";update();return;}
  if(gridSelected()){configureGrid();return;}
  if(selectedProfile>=4){note="Для 2048 используйте Область и выберите сетку 4×4";update();return;}
  if(selectedProfile==0){startVisualArcade(ArcadeProfile.ARI);if(playingJumper)jumperCalibrationPending=true;return;}
  if(!bindSelectedWindow())return;
  if(!manualVisualRoi){note="Сначала Область → Выделить прямоугольник: только активное игровое поле";update();return;}
  startVisualArcade(ArcadeProfile.values()[selectedProfile]);if(playingJumper)customCalibrationPending=true;
 }

 private boolean gridSelected(){return selectedProfile==6||selectedProfile==7||selectedProfile==10||selectedProfile==11;}
 private GridPuzzlePolicy.Mode gridMode(){return selectedProfile==6||selectedProfile==11?GridPuzzlePolicy.Mode.SWAP_MATCH3:selectedProfile==10?GridPuzzlePolicy.Mode.TRACE_LINE:GridPuzzlePolicy.Mode.TAP_GROUP;}
 private String gridKey(){String slot=getSharedPreferences("panel",MODE_PRIVATE).getString("game_slot."+target,"default");return GridPuzzleConfig.key(target,slot,gridMode())+(selectedProfile==11?".protected-skull":"");}
 private void loadGridConfig(){
  String value=getSharedPreferences("grid-puzzle",MODE_PRIVATE).getString(gridKey()+".config",null);
  try{gridConfig=value==null?new GridPuzzleConfig(6,6,selectedProfile==10?3:2):GridPuzzleConfig.decode(value);}
  catch(IllegalArgumentException error){gridConfig=new GridPuzzleConfig(6,6,selectedProfile==10?3:2);visualRoi=null;manualVisualRoi=false;}
  if(!manualVisualRoi&&gridConfig.fits(armedBounds.width(),armedBounds.height())){
   visualRoi=new Rect(armedBounds.left+Math.round(gridConfig.left*armedBounds.width()),armedBounds.top+Math.round(gridConfig.top*armedBounds.height()),armedBounds.left+Math.round(gridConfig.right*armedBounds.width()),armedBounds.top+Math.round(gridConfig.bottom*armedBounds.height()));manualVisualRoi=true;
  }
 }
 private void saveGridRegion(Rect region){
  gridConfig=gridConfig.region(region.left-armedBounds.left,region.top-armedBounds.top,region.width(),region.height(),armedBounds.width(),armedBounds.height());
  getSharedPreferences("grid-puzzle",MODE_PRIVATE).edit().putString(gridKey()+".config",gridConfig.encode()).apply();
 }
 private void configureGrid(){
  if(!gridSelected()){note="Сначала Игра → Фишки: обмен или группы";update();return;}
  if(!bindSelectedWindow())return;loadGridConfig();
  overlayMenu("Сетка фишек · "+gridConfig.rows+"×"+gridConfig.cols,new String[]{"6×6","7×7","Другой размер / минимум группы"},i->{if(i<2){setGridSize(i==0?6:7,i==0?6:7,selectedProfile==10?3:2);selectVisualRegion();}else showGridSizeForm();});
 }
 private void chooseGridGoal(){
  if(!gridSelected()){note="Сначала Игра → Фишки: обмен или группы";update();return;}if(!bindSelectedWindow())return;
  overlayMenu("Приоритет при выборе хода",new String[]{"Любые фишки","Красные","Жёлтые","Зелёные","Синие","Фиолетовые","Оранжевые"},i->{gridGoal=GridPuzzleGoal.Color.values()[i];getSharedPreferences("grid-puzzle",MODE_PRIVATE).edit().putString(gridKey()+".goal",gridGoal.name()).apply();note="Приоритет сохранён · нажмите Старт. При смене цели выберите цвет заново";update();});
 }
 private void showGridSizeForm(){
  LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(16),0,dp(16),0);
  EditText[] fields=new EditText[3];String[] labels={"Строки (3–10)","Столбцы (3–10)","Минимум фишек в группе (2–10)"};int[] values={gridConfig.rows,gridConfig.cols,gridConfig.minGroup};
  for(int i=0;i<3;i++){TextView label=new TextView(this);label.setText(labels[i]);box.addView(label);fields[i]=new EditText(this);fields[i].setSingleLine(true);fields[i].setInputType(android.text.InputType.TYPE_CLASS_NUMBER);fields[i].setText(String.valueOf(values[i]));box.addView(fields[i]);}
  AlertDialog d=new AlertDialog.Builder(this).setTitle("Размер поля").setView(box).setMessage("Ровная прямоугольная сетка. Минимум фишек применяется к удалению групп и прямым цепочкам.")
   .setNegativeButton("Отмена",null).setPositiveButton("Выделить поле",(dialog,which)->{try{setGridSize(Integer.parseInt(fields[0].getText().toString()),Integer.parseInt(fields[1].getText().toString()),Integer.parseInt(fields[2].getText().toString()));selectVisualRegion();}catch(IllegalArgumentException error){note="Строки/столбцы: 3–10; минимум группы: 2–10";update();}}).create();
  overlayDialog=d;d.setOnDismissListener(dialog->{if(overlayDialog==d)overlayDialog=null;});d.getWindow().setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);d.show();
 }
 private void setGridSize(int rows,int cols,int minimum){
  gridConfig=new GridPuzzleConfig(rows,cols,minimum);gridPalette=null;visualRoi=null;manualVisualRoi=false;boardRoi=null;clearDiagnosticFrame();
  getSharedPreferences("grid-puzzle",MODE_PRIVATE).edit().putString(gridKey()+".config",gridConfig.encode()).remove(gridKey()+".palette").apply();
 }
 private String bubbleKey(){return profileChoiceKey(target)+".area";}
 private void saveBubbleRegion(Rect area){
  if(area==null||!armedBounds.contains(area))throw new IllegalArgumentException("bubble area");
  String value="v1,"+armedBounds.width()+","+armedBounds.height()+","+(area.left-armedBounds.left)+","+(area.top-armedBounds.top)+","+(area.right-armedBounds.left)+","+(area.bottom-armedBounds.top);
  getSharedPreferences("bubble-puzzle",MODE_PRIVATE).edit().putString(bubbleKey(),value).apply();
 }
 private void loadBubbleRegion(){
  if(manualVisualRoi&&visualRoi!=null&&armedBounds.contains(visualRoi))return;
  String saved=getSharedPreferences("bubble-puzzle",MODE_PRIVATE).getString(bubbleKey(),null);
  if(saved==null||saved.length()>100)return;
  try{
   String[] parts=saved.split(",",-1);if(parts.length!=7||!parts[0].equals("v1"))return;
   int width=Integer.parseInt(parts[1]),height=Integer.parseInt(parts[2]),left=Integer.parseInt(parts[3]),top=Integer.parseInt(parts[4]),right=Integer.parseInt(parts[5]),bottom=Integer.parseInt(parts[6]);
   if(width!=armedBounds.width()||height!=armedBounds.height()||left<0||top<0||right>width||bottom>height||right-left<80||bottom-top<120)return;
   visualRoi=new Rect(armedBounds.left+left,armedBounds.top+top,armedBounds.left+right,armedBounds.top+bottom);manualVisualRoi=true;
  }catch(NumberFormatException malformed){/* Keep the saved value for diagnosis; never invent coordinates. */}
 }
 private void startBubble(){
  if(playingGrid&&bubbleSession.paused()){stop("Повторная проверка Шипучки");if(!bindSelectedWindow())return;}
  if(busy||captureBusy||discoveringBoard||autoLeft>0||playing2048||playingMini||playingJumper||playingGrid){note="Сначала остановите текущий режим";update();return;}
  if(!VisualGameTarget.allowed(target,getPackageName())){note="Выберите окно сторонней игры";update();return;}
  loadBubbleRegion();
  if(!manualVisualRoi||visualRoi==null||!armedBounds.contains(visualRoi)){note="Шипучка: выделите всю ширину игры от верхних шаров до пушки";update();selectVisualRegion();return;}
  boardRoi=new Rect(visualRoi);saveBubbleRegion(boardRoi);bubbleSession.reset();bubbleGeometry=null;bubblePendingShot=null;
  bubbleLearningWritable=true;bubbleLearning=loadLiveGameLearning(LiveGameLearning.Game.BUBBLE,false);
  gridMoves=gridVerified=0;gridResultVisible=gridSawAction=false;playingGrid=true;clearDiagnosticFrame();captureFrames=captureReads=captureDecisions=captureSent=captureCompleted=0;
  if(automatic())permit.grantSession(gate.epoch(),SystemClock.elapsedRealtime());
  compact(true);note="Шипучка · проверяю обычные шары и пушку";update();long epoch=gate.epoch(),generation=boardReadGeneration;
  afterPanelLayout(()->{if(epoch!=gate.epoch()||generation!=boardReadGeneration||!playingGrid||focusPaused)return;
   if(!movePanelAway(boardRoi)){stop("Панель STOP закрывает область: безопасного места нет");return;}
   afterPanelLayout(()->{if(epoch!=gate.epoch()||generation!=boardReadGeneration||!playingGrid||focusPaused)return;
    if(!panelOutsideRoi(boardRoi)){stop("PANEL_COVERS_ROI: панель после перемещения перекрывает Шипучку");return;}
    scheduleGrid(epoch);});});
 }
 private void bubbleStep(long epoch){
  if(epoch!=gate.epoch()||!playingGrid||selectedProfile!=9||bubbleSession.paused()||focusPaused)return;
  captureImage(epoch,(pixels,ticket,captureMs)->{
   if(epoch!=gate.epoch()||!playingGrid||selectedProfile!=9||focusPaused)return;
   captureFrames++;Rect r=new Rect(boardRoi),panel=panelBounds();
   if(GameOverScreen.detect(pixels::getPixel,armedBounds.left,armedBounds.top,armedBounds.right,armedBounds.bottom,panel.left,panel.top,panel.right,panel.bottom)){
    gridResultVisible=true;bubbleSession.reset();bubbleGeometry=null;bubblePendingShot=null;note="Экран результата · нажмите Играть снова";update();scheduleGrid(epoch);return;
   }
   gridResultVisible=false;metrics.attempt(StageMetrics.Stage.READ);long started=SystemClock.elapsedRealtimeNanos();
   BubbleVision.Read read=BubbleVision.read(pixels::getPixel,pixels.getWidth(),pixels.getHeight(),r.left,r.top,r.right,r.bottom,panel.left,panel.top,panel.right,panel.bottom,bubbleGeometry);
   bubbleSession.visionReason(read.reason());
   captureReads++;metrics.record(StageMetrics.Stage.READ,SystemClock.elapsedRealtimeNanos()-started);
   long frameAt=SystemClock.elapsedRealtime()-Math.max(0,SystemClock.uptimeMillis()-lastShotTimestamp);
   BubbleSession.Status status=bubbleSession.observe(read.board(),frameAt,SystemClock.elapsedRealtime());
   if(status==BubbleSession.Status.PAUSED){
    if(bubblePendingShot!=null&&bubbleSession.acknowledged()){
     bubbleLearning.unconfirmedBubble(bubblePendingShot.bounces,bubblePendingShot.removed,bubblePendingShot.dropped);saveLiveGameLearning(bubbleLearning,false);
    }
    bubblePendingShot=null;
    note="Шипучка: "+(bubbleSession.acknowledged()?"Android завершил жест; ":"нет подтверждения Android; ")+bubbleWaitMessage(bubbleSession.lastWait())+" · ввод приостановлен. Старт — повторить";update();return;}
   if(status!=BubbleSession.Status.READY&&status!=BubbleSession.Status.VERIFIED){note=read.usable()?(bubbleSession.pending()?"Шипучка: проверяю попадание":"Шипучка: ожидаю второй устойчивый кадр"):read.reason();update();scheduleGrid(epoch);return;}
   bubbleGeometry=read.board();gridVerified=bubbleSession.verified();
   if(status==BubbleSession.Status.VERIFIED&&bubblePendingShot!=null){
    bubbleLearning.verifiedBubble(bubblePendingShot.bounces,bubblePendingShot.removed,bubblePendingShot.dropped);saveLiveGameLearning(bubbleLearning,false);
    bubblePendingShot=null;
   }
   metrics.attempt(StageMetrics.Stage.POLICY);started=SystemClock.elapsedRealtimeNanos();BubblePolicy.Shot shot=BubblePolicy.choose(bubbleGeometry,plan->bubbleLearning.bonusBubble(plan.bounces,plan.removed,plan.dropped));
   captureDecisions++;metrics.record(StageMetrics.Stage.POLICY,SystemClock.elapsedRealtimeNanos()-started);
   uiStats="Шипучка · шаров "+bubbleGeometry.count()+" · выстрелов "+gridMoves+" · подтверждено "+gridVerified
      +" · опыт "+bubbleLearning.verifiedCount()+" / "+bubbleLearning.unconfirmedCount();
   if(shot==null||!automatic()){note=shot==null?"Безопасная траектория не найдена · ожидаю поле":(shot.bounces==0?"Наблюдение: найден прямой выстрел":"Наблюдение: найден один рикошет");update();scheduleGrid(epoch);return;}
   if(bubbleGeometry.shortRowParity==0){note="Шипучка: обратное чередование рядов · наблюдение без ввода";update();scheduleGrid(epoch);return;}
   if(busy||!valid(ticket)||SystemClock.elapsedRealtime()-frameAt>300){note="Шипучка: жду свежий кадр для выстрела";update();scheduleGrid(epoch);return;}
   long sentBefore=captureSent;bubbleSession.sent(bubbleGeometry,shot,SystemClock.elapsedRealtime());
   sendObserved(r.left+bubbleGeometry.launcherX,r.top+bubbleGeometry.launcherY,r.left+shot.aimX,r.top+shot.aimY,ticket,()->{
    if(epoch!=gate.epoch()||!playingGrid||selectedProfile!=9)return;
    bubbleSession.acknowledge(SystemClock.elapsedRealtime());note="Android завершил жест · проверяю поле";update();scheduleGrid(epoch);
   },frameAt,220);
   if(captureSent>sentBefore){bubblePendingShot=shot;gridMoves++;gridSawAction=true;note=(shot.bounces==0?"Прямой выстрел ":"Один рикошет ")+gridMoves+" · ожидается удалений "+shot.removed+", падений "+shot.dropped;update();}
   else if(playingGrid){bubblePendingShot=null;bubbleSession.reset();scheduleGrid(epoch);}
  });
 }
 private String bubbleWaitMessage(BubbleSession.Reason reason){
  switch(reason){
   case CELLS_MISMATCH:return "распознанное поле отличается от расчёта";
   case GEOMETRY_MISMATCH:return "геометрия поля изменилась";
   case NULL_SCENE:return "поле не распознано";
   case STALE:return "нет свежего кадра";
   case UNSTABLE:return "поле не стабилизировалось";
   default:return "игровой отклик не подтверждён";
  }
 }
 private void startGrid(){
  if(playingGrid&&gridSession.paused()){stop("Повторная проверка поля");if(!bindSelectedWindow())return;}
  if(busy||captureBusy||discoveringBoard||autoLeft>0||playing2048||playingMini||playingJumper||playingGrid){note="Сначала остановите текущий режим";update();return;}
  if(!VisualGameTarget.allowed(target,getPackageName())){note="Выберите окно сторонней игры";update();return;}
  loadGridConfig();if(!manualVisualRoi||visualRoi==null||!armedBounds.contains(visualRoi)){note="Область → обведите только клетки сетки "+gridConfig.rows+"×"+gridConfig.cols;update();return;}
  if(visualRoi.width()/gridConfig.cols<18||visualRoi.height()/gridConfig.rows<18){note="Клетки слишком малы: проверьте размер и выделение поля";update();return;}
  boardRoi=new Rect(visualRoi);saveGridRegion(boardRoi);gridPalette=null;
  String palette=getSharedPreferences("grid-puzzle",MODE_PRIVATE).getString(gridKey()+".palette",null);
  if(palette!=null)try{gridPalette=GridPuzzleVision.Palette.decode(palette);}catch(IllegalArgumentException error){note="Типы фишек повреждены · Ещё → Вернуть стандартное распознавание";update();return;}
  learningSlot=getSharedPreferences("panel",MODE_PRIVATE).getString("game_slot."+target,"default");
  try{gridGoal=GridPuzzleGoal.Color.valueOf(getSharedPreferences("grid-puzzle",MODE_PRIVATE).getString(gridKey()+".goal","ANY"));}catch(IllegalArgumentException error){gridGoal=GridPuzzleGoal.Color.ANY;}
  gridSession.reset();gridUnknownReasons="none";gridMoves=gridVerified=0;gridResultVisible=gridSawAction=false;playingGrid=true;clearDiagnosticFrame();captureFrames=captureReads=captureDecisions=captureSent=captureCompleted=0;
  gridPendingDecision=null;gridLearning=new GridOnlineLearning();
  String savedLearning=getSharedPreferences("grid-puzzle",MODE_PRIVATE).getString(gridKey()+".online-v1",null);
  if(savedLearning!=null)try{gridLearning=GridOnlineLearning.decode(savedLearning);}
  catch(IllegalArgumentException invalid){note="Статистика ходов повреждена: использую безопасную базовую стратегию";}
  if(automatic())permit.grantSession(gate.epoch(),SystemClock.elapsedRealtime());
  compact(true);note="Проверяю устойчивую сетку "+gridConfig.rows+"×"+gridConfig.cols;update();long epoch=gate.epoch(),generation=boardReadGeneration;
  afterPanelLayout(()->{if(epoch!=gate.epoch()||generation!=boardReadGeneration||!playingGrid||focusPaused)return;if(!movePanelAway(boardRoi)){stop("Панель STOP закрывает сетку: безопасного места нет");return;}
   afterPanelLayout(()->{if(epoch!=gate.epoch()||generation!=boardReadGeneration||!playingGrid||focusPaused)return;
    if(!panelOutsideRoi(boardRoi)){stop("PANEL_COVERS_ROI: панель после перемещения перекрывает сетку");return;}
    scheduleGrid(epoch);});});
 }
 /** A focus pause invalidates pending reads even when the bound session survives. */
 private void postBoardTask(long epoch,long delay,Runnable task){
  long generation=boardReadGeneration;
  handler.postDelayed(()->{if(epoch==gate.epoch()&&generation==boardReadGeneration&&!focusPaused)task.run();},delay);
 }
 private void scheduleGrid(long epoch){if(playingGrid&&epoch==gate.epoch()&&!(selectedProfile==9?bubbleSession.paused():gridSession.paused())&&!focusPaused)postBoardTask(epoch,captureDelay(),()->gridStep(epoch));}
 private void gridStep(long epoch){
  if(selectedProfile==9){bubbleStep(epoch);return;}
  if(epoch!=gate.epoch()||!playingGrid||gridSession.paused()||focusPaused)return;
  captureImage(epoch,(pixels,ticket,captureMs)->{
   if(epoch!=gate.epoch()||!playingGrid)return;captureFrames++;Rect r=new Rect(boardRoi),panel=panelBounds();long now=SystemClock.elapsedRealtime();
   if(GameOverScreen.detect(pixels::getPixel,armedBounds.left,armedBounds.top,armedBounds.right,armedBounds.bottom,panel.left,panel.top,panel.right,panel.bottom)){
    if(!gridResultVisible&&gridSawAction)incrementGridStat("result_screens");gridResultVisible=true;gridSession.reset();gridUnknownReasons="none";note="Экран результата · нажмите Играть снова. Настройки сохранены";update();scheduleGrid(epoch);return;
   }
   gridResultVisible=false;long readNs=SystemClock.elapsedRealtimeNanos();metrics.attempt(StageMetrics.Stage.READ);
   long frameAt=SystemClock.elapsedRealtime()-Math.max(0,SystemClock.uptimeMillis()-lastShotTimestamp);
   GridPuzzleVision.Observation observation=GridPuzzleVision.detect(pixels::getPixel,r.left,r.top,r.right,r.bottom,gridConfig.rows,gridConfig.cols,panel.left,panel.top,panel.right,panel.bottom,frameAt,gridPalette,selectedProfile==11);
   captureReads++;metrics.record(StageMetrics.Stage.READ,SystemClock.elapsedRealtimeNanos()-readNs);
   gridUnknownReasons=observation.unknownReasonCounts();if(gridUnknownReasons.isEmpty())gridUnknownReasons="none";
   boolean partialGrid=observation.partialUsable();
   int[] observedCells=observation.cells();
   // Keep individually recognized cells; the policy treats every unknown as
   // an obstacle.  Reject a wrong screen or overlay-covered ROI as a whole.
   if(!observation.usable()&&!partialGrid)java.util.Arrays.fill(observedCells,-1);
   GridPuzzleSession.Status status=partialGrid||gridSession.pending()
     ?gridSession.observePartial(gridConfig.rows,gridConfig.cols,observedCells,observation.specialCellsMask(),frameAt,SystemClock.elapsedRealtime())
     :gridSession.observe(gridConfig.rows,gridConfig.cols,observedCells,observation.specialCellsMask(),frameAt,SystemClock.elapsedRealtime());
   if(status==GridPuzzleSession.Status.PAUSED){
    incrementGridStat("unconfirmed");
    if(gridPendingDecision!=null&&gridSession.acknowledged()){
     gridLearning.recordNoResponse(gridPendingDecision);saveGridLearning();
    }
    gridPendingDecision=null;
    note=(gridSession.acknowledged()?"Android завершил жест, но игра не подтвердила ход":"Нет подтверждения Android для жеста")+" · ввод приостановлен. Старт — повторить";
    update();return;
   }
   if(status==GridPuzzleSession.Status.CHANGED){
    gridVerified++;incrementGridStat("visual_changes");
    if(gridPendingDecision!=null){
     gridLearning.recordVerified(gridPendingDecision);saveGridLearning();gridPendingDecision=null;
    }
   }
   if(status!=GridPuzzleSession.Status.READY&&status!=GridPuzzleSession.Status.CHANGED){
    note=observation.usable()||partialGrid
      ?(gridSession.pending()?"Проверяю подтверждённое изменение поля после жеста":"Ожидаю второй устойчивый кадр"+(partialGrid?" · частичная сетка":""))
      :"Не распознано "+unknownGridCells(observation)+" из "+(gridConfig.rows*gridConfig.cols)+" клеток · "+observation.unknownSummary(3)+" · продолжаю чтение без ввода";
    update();scheduleGrid(epoch);return;
   }
   gridPalette=observation.palette();
   // Only fully verified observations may overwrite a stored game palette.
   // Partial observations remain session-local until a complete board appears.
   if(observation.usable())getSharedPreferences("grid-puzzle",MODE_PRIVATE).edit()
     .putString(gridKey()+".palette",gridPalette.encode()).apply();
   metrics.attempt(StageMetrics.Stage.POLICY);long policyNs=SystemClock.elapsedRealtimeNanos();
   int targetType=GridPuzzleGoal.targetType(gridPalette,gridGoal);
   GridPuzzle board=new GridPuzzle(gridConfig.rows,gridConfig.cols,observation.cells());
   GridPuzzlePolicy.Decision action=GridPuzzlePolicy.chooseAdaptive(
     board,gridMode(),gridConfig.minGroup,targetType,gridConfig.rows*gridConfig.cols,
     observation.specialCellsMask(),partialGrid,gridLearning);
   captureDecisions++;metrics.record(StageMetrics.Stage.POLICY,SystemClock.elapsedRealtimeNanos()-policyNs);
   // Bent four-cell chains are suggestions only: live gestures currently support straight paths.
   boolean shadowChain=gridMode()==GridPuzzlePolicy.Mode.TRACE_LINE
     &&GridChainShadow.findFour(board,observation.specialCellsMask())!=null;
   if(!automatic()){note="Наблюдение: сетка прочитана · "+(shadowChain?"найдена цепочка из 4 (без ввода)":action==null?"совпадений не найдено":"найден ход, фишек "+action.expectedCleared);update();scheduleGrid(epoch);return;}
   // Incomplete boards remain observational until partial-grid safety is validated.
   if(partialGrid){note="Частичная сетка · наблюдение без ввода"+(shadowChain?" · найдена цепочка из 4":action==null?"":" · найден возможный ход");update();scheduleGrid(epoch);return;}
   if(action==null){note="Нет проверяемого хода · продолжаю наблюдение"+(partialGrid?" (неизвестные клетки не трогаю)":"");update();scheduleGrid(epoch);return;}
   if(busy||!valid(ticket)||SystemClock.elapsedRealtime()-frameAt>300){note="Жду свежий кадр для хода";update();scheduleGrid(epoch);return;}
   float x=r.left+(action.fromCol+.5f)*r.width()/gridConfig.cols,y=r.top+(action.fromRow+.5f)*r.height()/gridConfig.rows;
   float ex=r.left+(action.toCol+.5f)*r.width()/gridConfig.cols,ey=r.top+(action.toRow+.5f)*r.height()/gridConfig.rows;
   long sentBefore=captureSent;gridSession.sent(observation.cells(),SystemClock.elapsedRealtime(),action);
   sendObserved(x,y,ex,ey,ticket,()->{gridSession.acknowledge(SystemClock.elapsedRealtime());note="Жест доставлен · проверяю поле";update();scheduleGrid(epoch);},frameAt,action.mode==GridPuzzlePolicy.Mode.TAP_GROUP?55:160);
   if(captureSent>sentBefore){
    gridPendingDecision=action;gridMoves++;gridSawAction=true;incrementGridStat("dispatched");
    note="Ход "+gridMoves+" · ожидается фишек "+action.expectedCleared+" · подтверждено "+gridVerified
      +" · обучение "+gridLearning.verifiedCount()+" / "+gridLearning.notConfirmedCount();
    update();
   }
   else if(playingGrid){gridPendingDecision=null;gridSession.reset();scheduleGrid(epoch);}
  });
 }
 private int unknownGridCells(GridPuzzleVision.Observation observation){int count=0;for(int type:observation.cells())if(type<0)count++;return count;}
 private void incrementGridStat(String name){android.content.SharedPreferences prefs=getSharedPreferences("grid-puzzle",MODE_PRIVATE);String key=gridKey()+"."+name;long count=prefs.getLong(key,0);prefs.edit().putLong(key,count==Long.MAX_VALUE?count:count+1).apply();}
 /** Persist only verified response statistics, scoped by target + game slot + mode. */
 private void saveGridLearning(){
  getSharedPreferences("grid-puzzle",MODE_PRIVATE).edit().putString(gridKey()+".online-v1",gridLearning.encode()).apply();
 }

 private void showCustomCalibration(Bitmap frame){
  final JumperFrameGeometry geometry=visualGeometry(frame);final int fw=frame.getWidth(),fh=frame.getHeight();
  String[] prompts=switch(captureProfile){
   case PADDLE->new String[]{"1/3 · нажмите в центре шара","2/3 · нажмите на платформу","3/3 · нажмите на разрушаемый блок"};
   case TRAFFIC->new String[]{"1/3 · нажмите на кузов своей машины","2/3 · нажмите на другую машину","3/3 · ещё одна машина; канистру не выбирайте"};
   default->new String[]{"1/3 · нажмите на птенца","2/3 · верхнее препятствие","3/3 · нижнее препятствие (или то же)"};
  };
  JumperCalibrationOverlay overlay=new JumperCalibrationOverlay(this,frame,prompts,new JumperCalibrationOverlay.Samples(){
   public void completed(int[] colors,float[] x,float[] y){
    try{
     for(int i=0;i<3;i++){x[i]=(x[i]*fw-geometry.left)/(geometry.right-geometry.left);y[i]=(y[i]*fh-geometry.top)/(geometry.bottom-geometry.top);}
     CustomVisionConfig selected=new CustomVisionConfig(colors,x,y);
     getSharedPreferences("custom-vision",MODE_PRIVATE).edit().putString(VisualGameTarget.key(target,learningSlot,captureProfile),selected.encode()).apply();customVision=selected;
     modelSlot=VisualGameTarget.modelSlot(learningSlot,controlMode,selected.encode());
     arcadeSceneGeneration++;jumperVisionPending=false;arcadeState=null;arcadeLastUsable=null;arcadeBefore=null;arcadePending=null;arcadeAck=false;arcadeController.reset();arcadeFeedback.reset();sceneRecovery.reset();roundOutcome.reset();persistArcadeModel(false);
     note="Настройка объектов сохранена · проверяю новые кадры";
    }catch(IllegalArgumentException error){stop("Точки должны быть внутри выбранного игрового поля · повторите настройку");}
    View old=customCalibrationOverlay;customCalibrationOverlay=null;removeOverlay(old);update();
   }
   public void cancelled(){stop("Настройка распознавания отменена");compact(false);}
  });
  WindowManager.LayoutParams lp=new WindowManager.LayoutParams(-1,-1,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN|WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,PixelFormat.TRANSLUCENT);lp.gravity=Gravity.TOP|Gravity.LEFT;
  try{wm.addView(overlay,lp);customCalibrationOverlay=overlay;compact(true);note="Выберите 3 объекта на замороженном кадре";update();}
  catch(RuntimeException error){if(!frame.isRecycled())frame.recycle();stop("Не удалось открыть настройку объектов");}
 }

 private void showServiceDialog(AlertDialog dialog){
  boolean waitForGesture=busy;stop("Диагностика: STOP");
  dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);
  dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
  Runnable show=()->{try{dialog.show();}catch(RuntimeException error){note="Не удалось открыть диагностику";update();}};
  if(waitForGesture)gestureHandler.postDelayed(show,150);else show.run();
 }

 private JumperVision.ColorProfile loadJumperColors(String packageName){
  android.content.SharedPreferences prefs=getSharedPreferences("jumper-vision",MODE_PRIVATE);
  String prefix=VisualGameTarget.key(packageName,learningSlot,ArcadeProfile.ARI)+".";
  if(!prefs.contains(prefix+"player")&&VisualGameTarget.legacyMarket(packageName)&&learningSlot.equals("default"))prefix=packageName+".";
  int p=prefs.getInt(prefix+"player",-1),platform=prefs.getInt(prefix+"platform",-1),hazard=prefs.getInt(prefix+"hazard",-1);
  if(p<0||platform<0||hazard<0)return null;
  try{return new JumperVision.ColorProfile(p,platform,hazard);}catch(IllegalArgumentException error){return null;}
 }

 private void startJumperCalibration(){
  if(captureProfile!=ArcadeProfile.ARI){note="Трёхцветная калибровка предназначена для Прыгуна Ари";update();return;}
  if(!playingJumper||!jumperCaptureReady){note="Запустите Прыгун Ари, затем откройте калибровку";update();return;}
  if(busy){note="Дождитесь подтверждения текущего касания";update();return;}
  if(jumperCalibrationOverlay!=null||jumperCalibrationPending)return;
  jumperCalibrationPending=true;note="Ожидаю кадр для калибровки · нажмите Ари, платформу и шип";update();
 }

 private void showJumperCalibration(Bitmap frame){
  if(jumperCalibrationOverlay!=null){frame.recycle();return;}
  JumperCalibrationOverlay overlay=new JumperCalibrationOverlay(this,frame,new JumperCalibrationOverlay.Listener(){
   @Override public void completed(int player,int platform,int hazard){
    View old=jumperCalibrationOverlay;jumperCalibrationOverlay=null;removeOverlay(old);
    jumperColorProfile=new JumperVision.ColorProfile(player,platform,hazard);
    String prefix=VisualGameTarget.key(target,learningSlot,ArcadeProfile.ARI)+".";
    getSharedPreferences("jumper-vision",MODE_PRIVATE).edit().putInt(prefix+"player",player)
      .putInt(prefix+"platform",platform).putInt(prefix+"hazard",hazard).apply();
    sceneRecovery.reset();jumperState=null;jumperAwaitingVerification=false;
    jumperLastActionMs=SystemClock.elapsedRealtime();
    if(automatic())permit.grantSession(gate.epoch(),SystemClock.elapsedRealtime());
    note="Калибровка сохранена локально · проверяю распознавание";update();
   }
   @Override public void cancelled(){View old=jumperCalibrationOverlay;jumperCalibrationOverlay=null;removeOverlay(old);stop("STOP во время калибровки");}
  });
  WindowManager.LayoutParams lp=new WindowManager.LayoutParams(-1,-1,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN|WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,PixelFormat.TRANSLUCENT);
  lp.gravity=Gravity.TOP|Gravity.LEFT;
  try{wm.addView(overlay,lp);jumperCalibrationOverlay=overlay;compact(true);}
  catch(RuntimeException error){if(!frame.isRecycled())frame.recycle();stop("Не удалось открыть калибровку");}
 }

 public boolean captureConsentMayClose(long id){return captureStartup.consentMayClose(id);}
 public boolean captureSessionActive(long id){return playingJumper&&captureStartup.active(id);}
 public boolean onCaptureConsentGranted(long id){
  if(!captureStartup.move(id,CaptureStartup.Stage.CONSENT,CaptureStartup.Stage.SERVICE,SystemClock.elapsedRealtime()))return false;
  handler.post(()->{if(captureSessionActive(id)){note="2/5 · Разрешение получено, запускаю службу захвата";update();}});return true;
 }
 public void onCaptureStartupError(long id,String reason,String type){
  handler.post(()->{if(!captureSessionActive(id))return;captureStartup.error(id,type);failStage(StageMetrics.Stage.CAPTURE,CaptureStartup.errorCode(type),reason);});
 }
 private void watchCaptureStartup(long id){handler.postDelayed(()->{
  if(!captureSessionActive(id)||!captureStartup.waiting(id))return;String error=captureStartup.timeout(id,SystemClock.elapsedRealtime());
  if(error!=null){onCaptureStartupError(id,"Захват не запустился. Нажмите профиль игры для повторной попытки",error);return;}
  note="Ожидание разрешения захвата / готовности первого кадра…";update();watchCaptureStartup(id);
 },500);}
 public void onJumperCaptureReady(long id,int width,int height){Runnable ready=()->{
  if(!captureStartup.move(id,CaptureStartup.Stage.SERVICE,CaptureStartup.Stage.FIRST_FRAME,SystemClock.elapsedRealtime()))return;
  if(!playingJumper){stopService(new Intent(this,JumperCaptureService.class));return;}
  CrashDiagnostics.phase("CAPTURE_READY");
  jumperDisplayWidth=width;jumperDisplayHeight=height;
  jumperConsentPending=true;jumperCaptureReady=true;jumperLastFrameMs=SystemClock.elapsedRealtime();
  arcadeCaptureReadyAt=jumperLastFrameMs;

  note="3/5 · Служба запущена, ожидаю первый кадр · "+captureProfile.label;update();
  watchArcadeCapture(gate.epoch());
 };if(Looper.myLooper()==Looper.getMainLooper())ready.run();else handler.post(ready);}

 public void onJumperCaptureDenied(long id,String reason){onCaptureStartupError(id,reason,"CONSENT_DENIED");}
 public void onJumperCaptureLost(long id,String reason){onCaptureStartupError(id,reason,"CAPTURE_LOST");}
 public void stopJumperFromNotification(long id){handler.post(()->{if(captureSessionActive(id))stop("STOP из уведомления Android");});}

 /** Takes ownership of each frame; frames are discarded immediately when the analyzer is busy. */
 public void onCapturePacing(long id,long accepted,long skipped,long throttled,long unwanted,long invalidClock){if(id>0&&id==captureSession){captureAcceptedFrames=accepted;captureSkippedFrames=skipped;captureSkippedThrottle=throttled;captureSkippedUnwanted=unwanted;captureSkippedClock=invalidClock;}}
 public boolean wantsJumperFrame(long id){synchronized(jumperFrameLock){return id>0&&id==captureSession&&playingJumper&&jumperCaptureReady&&!focusPaused&&!jumperVisionPending&&jumperQueuedFrames.isEmpty();}}
 public void onJumperFrame(long id,Bitmap frame,long timestampMs){
  if(frame==null)return;
  synchronized(jumperFrameLock){
   if(!captureSessionActive(id)||!jumperCaptureReady){if(!frame.isRecycled())frame.recycle();return;}
   // Keep only the newest waiting frame. Removed callbacks check ownership before access.
   if(!jumperQueuedFrames.isEmpty()){Bitmap oldest=jumperQueuedFrames.poll();if(oldest!=null&&!oldest.isRecycled())oldest.recycle();}
   jumperQueuedFrames.add(frame);
  }
  boolean posted=handler.post(()->{
   synchronized(jumperFrameLock){if(!jumperQueuedFrames.remove(frame))return;}
   if(!captureSessionActive(id)||focusPaused||timestampMs<focusRecoveredAt){if(!frame.isRecycled())frame.recycle();return;}
   try{
   if(captureStartup.move(id,CaptureStartup.Stage.FIRST_FRAME,CaptureStartup.Stage.RUNNING,SystemClock.elapsedRealtime())){note="4/5 · Кадр получен, запускаю распознавание";update();}
   captureFrames++;
   if(customCalibrationOverlay!=null){frame.recycle();return;}
   try{resolveVisualRegion(frame);}catch(RuntimeException error){/* Preserve full window when optional crop is unavailable. */}
   rememberDiagnosticFrame(frame,timestampMs);
   jumperLastFrameMs=Math.max(jumperLastFrameMs,timestampMs);
   if(customCalibrationPending){if(!valid(gate.offer(SystemClock.elapsedRealtime()))){if(!waitForCaptureTarget(SystemClock.elapsedRealtime())&&!deferFocus(invalidReason(gate.offer(SystemClock.elapsedRealtime()))))stop("Вернитесь в выбранное окно для настройки");frame.recycle();return;}customCalibrationPending=false;showCustomCalibration(frame);return;}
   if(holdResultScreen(frame,timestampMs)){frame.recycle();return;}
   if(captureProfile!=ArcadeProfile.ARI){analyzeArcadeFrame(frame,timestampMs);return;}
   if(jumperCalibrationOverlay!=null){frame.recycle();return;}
   if(jumperCalibrationPending){jumperCalibrationPending=false;showJumperCalibration(frame);return;}
   if(!playingJumper||!jumperCaptureReady||jumperVisionPending){frame.recycle();return;}
   long epoch=gate.epoch(),scene=arcadeSceneGeneration;
   try{jumperFrameGeometry=visualGeometry(frame);}
   catch(IllegalArgumentException error){frame.recycle();stop("Размер кадра не соответствует окну игры");return;}
   JumperFrameGeometry geometry=jumperFrameGeometry;Rect excludedScreen=panelBounds();
   int excludedLeft=geometry.toFrameX(excludedScreen.left),excludedTop=geometry.toFrameY(excludedScreen.top);
   int excludedRight=geometry.toFrameX(excludedScreen.right),excludedBottom=geometry.toFrameY(excludedScreen.bottom);
   JumperState previous=jumperState!=null&&jumperState.usable()?jumperState:null;SceneRecovery.Pass recoveryPass=sceneRecovery.pass();JumperVision.ColorProfile colors=jumperColorProfile;jumperVisionPending=true;long started=SystemClock.elapsedRealtimeNanos();
   try{visionTask=visionWorker.submit(()->{
    JumperState detected=null;RuntimeException failure=null;
    try{int width=frame.getWidth();int[] pixels=visionPixels(frame);
     detected=JumperVision.detectRecovering((x,y)->pixels[y*width+x],width,frame.getHeight(),
      geometry.left,geometry.top,geometry.right,geometry.bottom,excludedLeft,excludedTop,excludedRight,excludedBottom,
      timestampMs,previous,colors,recoveryPass);}
    catch(RuntimeException|OutOfMemoryError error){failure=error instanceof RuntimeException?(RuntimeException)error:new IllegalStateException("VISION_OUT_OF_MEMORY");}
    finally{if(!frame.isRecycled())frame.recycle();}
    JumperState result=detected;RuntimeException error=failure;
    handler.post(()->{if(scene==arcadeSceneGeneration)finishJumperFrame(epoch,timestampMs,started,result,error);else if(epoch==gate.epoch())jumperVisionPending=false;});
   });}catch(RuntimeException error){jumperVisionPending=false;if(!frame.isRecycled())frame.recycle();failStage(StageMetrics.Stage.READ,"VISION_WORKER_UNAVAILABLE","Не удалось запустить распознавание");}
   }catch(RuntimeException|OutOfMemoryError error){if(!frame.isRecycled())frame.recycle();CrashDiagnostics.phase(ProcessExitMetadata.frameFailurePhase(error));stop("Ошибка обработки кадра · захват остановлен. Откройте диагностику для проверки");}
  });
  if(!posted){boolean removed;synchronized(jumperFrameLock){removed=jumperQueuedFrames.remove(frame);}if(removed&&!frame.isRecycled())frame.recycle();}
 }

 public String captureProfileLabel(){return captureProfile.label;}

 private boolean holdResultScreen(Bitmap frame,long timestamp){
  long now=SystemClock.elapsedRealtime();
  if(timestamp>now||now-timestamp>300||!valid(gate.offer(now)))return false;
  // Result dialogs can extend beyond a manually selected playfield.
  JumperFrameGeometry g=new JumperFrameGeometry(frame.getWidth(),frame.getHeight(),jumperDisplayWidth,jumperDisplayHeight,armedBounds.left,armedBounds.top,armedBounds.right,armedBounds.bottom,true);Rect p=panelBounds();
  if(!GameOverScreen.detect(frame::getPixel,g.left,g.top,g.right,g.bottom,g.toFrameX(p.left),g.toFrameY(p.top),g.toFrameX(p.right),g.toFrameY(p.bottom)))return false;
  jumperConsentPending=false;
  if(roundOutcome.result(timestamp))persistArcadeModel(true);
  arcadeSceneGeneration++;jumperVisionPending=false;
  arcadeController.nextRound();arcadeFeedback.reset();arcadeLastUsable=null;arcadeState=null;arcadePending=null;arcadeBefore=null;arcadeAck=false;
  jumperState=null;jumperFeedback.reset();jumperAwaitingVerification=false;jumperResponsePaused=false;jumperResponseFailures=0;sceneRecovery.reset();arcadeRoundSeen=false;
  note="Экран результата · нажмите Играть снова. Захват и модель сохранены";update();return true;
 }

 private void watchArcadeCapture(long epoch){
  handler.postDelayed(()->{
   if(epoch!=gate.epoch()||!playingJumper||!jumperCaptureReady)return;
   if(!unlocked()){stop("Устройство заблокировано — STOP");return;}
   if(captureStartup.waiting(captureSession)){watchArcadeCapture(epoch);return;}
   long now=SystemClock.elapsedRealtime();
   if(now-jumperLastFrameMs>700){if(captureProfile==ArcadeProfile.ARI)advanceJumperFeedback(null,now);else advanceArcadeFeedback(null,now);}
   if(now-jumperLastFrameMs>1800){
    // MediaProjection may emit no frames for an unchanged result screen.
    // Keep the projection; never synthesize frames or issue input on old pixels.
    note=roundOutcome.waitingForReplay()?"Раунд завершён · нажмите Играть снова. Захват сохранён":"Нет свежего кадра · ввод приостановлен, ожидаю обновления экрана";update();watchArcadeCapture(epoch);return;
   }
   watchArcadeCapture(epoch);
  },600);
 }

 private void showArcadeDiagnostics(){
  ArcadeObservation s=arcadeState;
  String text=s==null?"Запустите профиль на открытой игровой сцене.\n"+captureStartup.report():
    (captureProfile==ArcadeProfile.TRAFFIC?"Машины: "+s.obstacles.size()+" · канистры: "+s.collectibles.size():"Объекты: "+s.obstacles.size())
    +" · уверенность "+String.format(java.util.Locale.ROOT,"%.2f",s.confidence)
    +"\nИгрок: "+(s.player==null?"не найден":Math.round(s.player.x()*100)+"% × "+Math.round(s.player.y()*100)+"%")
    +"\nСкорость Y: "+String.format(java.util.Locale.ROOT,"%.2f",s.vy)
    +"\nПодтверждено ответов на ввод: "+arcadeVerified
    +"\nОжидается ответ: "+(arcadePending!=null)
    +"\nЭто зрительная стратегия; игровой рекорд требует отдельной проверки.";
  showServiceDialog(new AlertDialog.Builder(this).setTitle(captureProfile.label+" · диагностика")
    .setMessage(text).setPositiveButton("Понятно",null).create());
 }

 private void analyzeArcadeFrame(Bitmap frame,long timestampMs){
  if(!playingJumper||!jumperCaptureReady||jumperVisionPending){frame.recycle();return;}
  long epoch=gate.epoch(),scene=arcadeSceneGeneration;ArcadeProfile profile=captureProfile;CustomVisionConfig calibration=customVision;
  final JumperFrameGeometry geometry;
  try{geometry=visualGeometry(frame);}
  catch(IllegalArgumentException error){frame.recycle();stop("Размер кадра не соответствует окну игры");return;}
  // Traffic retains only its appearance hint across a missed scene. The detector
  // independently rejects old coordinates/velocity after 500 ms.
  Rect excluded=panelBounds();ArcadeObservation previous=arcadeLastUsable!=null&&arcadeLastUsable.profile==profile&&timestampMs>arcadeLastUsable.timestampMs&&(profile==ArcadeProfile.TRAFFIC||timestampMs-arcadeLastUsable.timestampMs<=500)?arcadeLastUsable:null;SceneRecovery.Pass recoveryPass=sceneRecovery.pass();
  int el=geometry.toFrameX(excluded.left),et=geometry.toFrameY(excluded.top);
  int er=geometry.toFrameX(excluded.right),eb=geometry.toFrameY(excluded.bottom);
  jumperVisionPending=true;long started=SystemClock.elapsedRealtimeNanos();
  try{visionTask=visionWorker.submit(()->{
   ArcadeObservation detected=null;RuntimeException failure=null;
   try{int width=frame.getWidth();int[] pixels=visionPixels(frame);
    detected=calibration==null?ArcadeVision.detectRecovering(profile,(x,y)->pixels[y*width+x],width,frame.getHeight(),
     geometry.left,geometry.top,geometry.right,geometry.bottom,el,et,er,eb,timestampMs,previous,recoveryPass)
     :CustomArcadeVision.detect(profile,(x,y)->pixels[y*width+x],geometry.left,geometry.top,geometry.right,geometry.bottom,el,et,er,eb,timestampMs,previous,calibration);}
   catch(RuntimeException|OutOfMemoryError error){failure=error instanceof RuntimeException?(RuntimeException)error:new IllegalStateException("VISION_OUT_OF_MEMORY");}
   finally{if(!frame.isRecycled())frame.recycle();}
   ArcadeObservation result=detected;RuntimeException error=failure;
   handler.post(()->{if(scene==arcadeSceneGeneration)finishArcadeFrame(epoch,timestampMs,started,geometry,result,error);else if(epoch==gate.epoch())jumperVisionPending=false;});
  });}catch(RuntimeException error){jumperVisionPending=false;frame.recycle();failStage(StageMetrics.Stage.READ,"ARCADE_WORKER_UNAVAILABLE","Распознавание не запущено");}
 }

 private void finishArcadeFrame(long epoch,long timestampMs,long started,JumperFrameGeometry geometry,
   ArcadeObservation observation,RuntimeException error){
  if(epoch!=gate.epoch()||!playingJumper)return;
  jumperVisionPending=false;captureReads++;metrics.attempt(StageMetrics.Stage.READ);
  if(error!=null){failStage(StageMetrics.Stage.READ,"ARCADE_FRAME_REJECTED","Кадр не соответствует профилю");return;}
  metrics.record(StageMetrics.Stage.READ,SystemClock.elapsedRealtimeNanos()-started);
  long now=SystemClock.elapsedRealtime();jumperLastFrameMs=timestampMs;
  if(timestampMs>now||now-timestampMs>300){advanceArcadeFeedback(null,now);note="Пропускаю устаревший кадр";update();return;}
  ActionGate.Ticket ticket=gate.offer(now);
  if(!valid(ticket)){
   if(waitForCaptureTarget(now)||deferFocus(invalidReason(ticket)))return;
   stop("Окно игры изменилось — STOP");return;
  }
  jumperConsentPending=false;
  arcadeState=observation;
  if(observation!=null&&observation.terminal){
   sceneRecovery.reset();arcadeController.nextRound();arcadeFeedback.reset();arcadeLastUsable=null;arcadeRoundSeen=false;arcadePending=null;arcadeBefore=null;arcadeAck=false;arcadeState=null;
   note="Раунд завершён · нажмите «Играть снова» в игре. Захват и настройка сохранены";update();return;
  }
  if(observation!=null&&observation.usable()){arcadeLastUsable=observation;roundOutcome.game(timestampMs);}
  // Always update temporal physics, including while a previous Android gesture is in flight.
  ArcadePolicy.Decision predictive=observation!=null&&observation.usable()?arcadeController.decide(observation,now):null;
  if(!sceneRecovery.observe(observation!=null&&observation.usable(),timestampMs)){
   advanceArcadeFeedback(null,now);
   if(sceneRecovery.selectionRequired()){stop("Сцена не соответствует профилю. Игра → выберите механику; для другой графики: Ещё → Настроить распознавание");compact(false);return;}
   String missing=observation==null?"кадр":observation.player==null?(captureProfile==ArcadeProfile.PADDLE?"красный шар":"персонаж"):observation.control==null?"корзина":"поле";
   note=captureProfile.label+" · "+sceneRecovery.status()+(observation!=null&&observation.usable()?"":" · проверяю: "+missing);update();return;
  }
  arcadeRoundSeen=true;captureStartup.recognized(captureSession,now);

  boolean temporal=captureProfile==ArcadeProfile.TRAFFIC||captureProfile==ArcadeProfile.FLAPPY||captureProfile==ArcadeProfile.PADDLE;
  if(temporal){
   ArcadeFeedback.State feedback=advanceArcadeFeedback(observation,now);
   if(feedback==ArcadeFeedback.State.PAUSED){note="Нет видимого ответа на 3 касания · захват сохранён. Нажмите Старт для повторной проверки";update();return;}
   if(feedback==ArcadeFeedback.State.WAITING){note="Измеряю ответ на касание · прогноз обновляется";update();return;}
   if(busy)return;
  }else{
   if(arcadePending!=null){
    if(!arcadeAck){update();return;}
    if(timestampMs<=arcadePendingAt||!ArcadePolicy.response(arcadePending,arcadeBefore,observation)){
     if(now-arcadePendingAt>ArcadePolicy.responseTimeout(captureProfile)){failStage(StageMetrics.Stage.POLICY,"ARCADE_NO_RESPONSE","Сцена найдена, но игра не подтвердила движение после касания");return;}
     note="Проверяю реакцию игры…";update();return;
    }
    arcadeVerified++;arcadePending=null;arcadeBefore=null;arcadeAck=false;
   }
   if(busy||now-arcadeLastAction<ArcadePolicy.interval(captureProfile))return;
  }
  metrics.attempt(StageMetrics.Stage.POLICY);long policyStarted=SystemClock.elapsedRealtimeNanos();
  captureDecisions++;
  ArcadePolicy.Decision action=predictive==null?ArcadePolicy.next(observation):predictive;
  metrics.record(StageMetrics.Stage.POLICY,SystemClock.elapsedRealtimeNanos()-policyStarted);
  uiStats=captureProfile.label+(captureProfile==ArcadeProfile.TRAFFIC
    ?" · машины "+observation.obstacles.size()+" · топливо "+observation.collectibles.size()
    :" · объекты "+observation.obstacles.size())+" · ответов "+arcadeVerified;
  note="5/5 · "+action.reason;update();if(!action.sendsInput())return;
  if(now-timestampMs>ActionGate.TTL_MS){failStage(StageMetrics.Stage.CAPTURE,"ARCADE_FRAME_STALE","Кадр устарел до касания");return;}
  ArcadeControls.Gesture gesture=ArcadeControls.map(action,observation,controlMode);
  float x=geometry.toScreenX(geometry.left+(geometry.right-geometry.left)*gesture.toX());
  float y=geometry.toScreenY(geometry.top+(geometry.bottom-geometry.top)*gesture.toY());
  arcadeBefore=observation;arcadePending=action;arcadePendingAt=now;arcadeLastAction=now;arcadeAck=false;
  float fromX=geometry.toScreenX(geometry.left+(geometry.right-geometry.left)*gesture.fromX());
  float fromY=geometry.toScreenY(geometry.top+(geometry.bottom-geometry.top)*gesture.fromY());
  final long feedbackToken=temporal?arcadeFeedback.dispatched(action,observation,now):0;
  long sentBefore=captureSent;
  sendObserved(fromX,fromY,x,y,ticket,()->{
   if(temporal&&!arcadeFeedback.acknowledge(feedbackToken))return;
   arcadeAck=true;note="Android завершил касание · проверяю игру";update();
  },timestampMs,gesture.durationMs());
  if(captureSent==sentBefore&&playingJumper){if(temporal)arcadeFeedback.cancel(feedbackToken);arcadePending=null;arcadeBefore=null;arcadeAck=false;}
  if(captureSent>sentBefore){long actual=SystemClock.elapsedRealtime();arcadePendingAt=actual;if(temporal)arcadeFeedback.started(feedbackToken,actual);arcadeController.actionDispatched(action,actual);}
 }

 private ArcadeFeedback.State advanceArcadeFeedback(ArcadeObservation observation,long now){
  long verifiedBefore=arcadeFeedback.verified();ArcadeFeedback.State feedback=arcadeFeedback.observe(observation,now);
  if(arcadeFeedback.verified()>verifiedBefore){arcadeController.observeGesture(arcadePending,arcadeBefore,observation);persistArcadeModel(false);arcadeVerified++;}
  if(arcadeFeedback.verified()>verifiedBefore||feedback==ArcadeFeedback.State.RETRY||feedback==ArcadeFeedback.State.PAUSED){
   if(arcadePending!=null&&feedback!=ArcadeFeedback.State.READY)metrics.failure(StageMetrics.Stage.POLICY,"VISUAL_RESPONSE_RETRY");
   arcadePending=null;arcadeBefore=null;arcadeAck=false;
  }
  return feedback;
 }
 private JumperFeedback.State advanceJumperFeedback(JumperState observation,long now){
  JumperFeedback.State feedback=jumperFeedback.observe(observation,now);
  if(feedback==JumperFeedback.State.VERIFIED){jumperAwaitingVerification=false;jumperResponseFailures=0;arcadeVerified++;JumperPolicy.learn(true);persistArcadeModel(false);note="Движение подтверждено · "+jumperPendingAction;}
  if(feedback==JumperFeedback.State.SCENE_PROGRESS){jumperAwaitingVerification=false;jumperResponseFailures=0;jumperResponsePaused=false;note="Камера движется у края · продолжаю наблюдение без оценки касания";}
  if(feedback==JumperFeedback.State.RETRY||feedback==JumperFeedback.State.PAUSED){
   if(jumperAwaitingVerification){
    jumperAwaitingVerification=false;jumperResponseFailures++;
    if(jumperFeedback.failedWithAck()){JumperPolicy.learn(false);persistArcadeModel(false);}
    metrics.failure(StageMetrics.Stage.POLICY,"ARI_VISUAL_RESPONSE_RETRY");
   }
   jumperResponsePaused=feedback==JumperFeedback.State.PAUSED;
  }
  return feedback;
 }

 private void finishJumperFrame(long epoch,long timestampMs,long started,JumperState state,RuntimeException error){
  if(epoch!=gate.epoch()||!playingJumper)return;
  jumperVisionPending=false;captureReads++;metrics.attempt(StageMetrics.Stage.READ);
  if(error!=null){metrics.failure(StageMetrics.Stage.READ,"JUMPER_FRAME_REJECTED");failStage(StageMetrics.Stage.READ,"JUMPER_FRAME_REJECTED","Кадр не соответствует игровому полю");return;}
  metrics.record(StageMetrics.Stage.READ,SystemClock.elapsedRealtimeNanos()-started);
  long now=SystemClock.elapsedRealtime();jumperLastFrameMs=timestampMs;
  if(timestampMs>now||now-timestampMs>300){advanceJumperFeedback(null,now);note="Пропускаю устаревший кадр";update();return;}
  ActionGate.Ticket ticket=gate.offer(now);
  if(!valid(ticket)){
   if(waitForCaptureTarget(now)||deferFocus(invalidReason(ticket)))return;
   stop("Окно игры изменилось — STOP");return;
  }
  jumperConsentPending=false;
  jumperState=state;
  if(!sceneRecovery.observe(state!=null&&state.usable(),timestampMs)){
   advanceJumperFeedback(null,now);
   if(sceneRecovery.selectionRequired()){stop("Прыгун не распознан. Игра → выберите механику; для другой графики: Ещё → Настроить распознавание");compact(false);return;}
   note="Ари · "+sceneRecovery.status()+"\nИгрок: "+(state!=null&&state.player!=null?"найден":"не найден")+" · платформы: "+(state==null?0:state.platforms.size())+"\nЕсли область неверна: Область → Выделить";update();return;
  }
  arcadeRoundSeen=true;captureStartup.recognized(captureSession,now);

  roundOutcome.game(timestampMs);
  if(jumperResponsePaused){note="Ари: нет ответа на 3 касания · захват сохранён. Нажмите Старт";update();return;}
  JumperFeedback.State feedback=advanceJumperFeedback(state,now);
  if(feedback==JumperFeedback.State.PAUSED){note="Ари: нет видимого ответа на 3 касания · нажмите Старт";update();return;}
  if(feedback==JumperFeedback.State.WAITING){note="Проверяю направление касания…";update();return;}
  if(busy||now-jumperLastActionMs<180){update();return;}
  captureDecisions++;
  JumperPolicy.Action action=JumperPolicy.nextAction(state);
  uiStats="Ари "+Math.round(state.player.centerX())+"×"+Math.round(state.player.centerY())
    +" · платформы "+state.platforms.size()+" · шипы "+state.hazards.size()+" · "+action;
  if(action==JumperPolicy.Action.WAIT){note="Наблюдаю за прыжком";update();return;}
  JumperFrameGeometry geometry=jumperFrameGeometry;
  if(geometry==null){failStage(StageMetrics.Stage.POLICY,"FRAME_GEOMETRY_MISSING","Нет соответствия координат кадра");return;}
  ArcadeControls.Gesture gesture=ArcadeControls.map(new ArcadePolicy.Decision(action==JumperPolicy.Action.TAP_LEFT?ArcadePolicy.Action.LEFT:ArcadePolicy.Action.RIGHT,action==JumperPolicy.Action.TAP_LEFT?.25f:.75f,.56f,"Прыгун",120),null,controlMode);
  float x=geometry.toScreenX(geometry.left+(geometry.right-geometry.left)*gesture.toX());
  float y=geometry.toScreenY(geometry.top+(geometry.bottom-geometry.top)*.56f);
  float fromX=geometry.toScreenX(geometry.left+(geometry.right-geometry.left)*gesture.fromX());
  if(panelBounds().contains((int)x,(int)y)){failStage(StageMetrics.Stage.POLICY,"JUMPER_TAP_OVERLAY","Переместите панель STOP от зоны управления");return;}
  if(timestampMs>now||now-timestampMs>ActionGate.TTL_MS){failStage(StageMetrics.Stage.CAPTURE,"JUMPER_FRAME_STALE","Кадр устарел до касания");return;}
  jumperPendingAction=action;jumperPendingStartX=state.player.centerX();jumperPendingActionMs=now;
  jumperLastActionMs=now;jumperAwaitingVerification=true;
  long policyStart=SystemClock.elapsedRealtimeNanos();metrics.attempt(StageMetrics.Stage.POLICY);
  metrics.record(StageMetrics.Stage.POLICY,SystemClock.elapsedRealtimeNanos()-policyStart);
  note="Касание "+action+" · проверю движение Ари";update();
  long sentBefore=captureSent;
  long feedbackToken=jumperFeedback.dispatched(action,state,now);
  sendObserved(fromX,y,x,y,ticket,()->{if(!jumperFeedback.acknowledge(feedbackToken))return;note="Android завершил касание · ожидаю новый кадр";update();},timestampMs,gesture.durationMs());
  if(captureSent==sentBefore){jumperFeedback.cancel(feedbackToken);jumperAwaitingVerification=false;}
  else{jumperPendingActionMs=SystemClock.elapsedRealtime();jumperFeedback.started(feedbackToken,jumperPendingActionMs);}
 }
 private void send(float x,float y,float ex,float ey,ActionGate.Ticket ticket,Runnable done){sendObserved(x,y,ex,ey,ticket,done,-1);}
 private void sendObserved(float x,float y,float ex,float ey,ActionGate.Ticket ticket,Runnable done,long frameAt){sendObserved(x,y,ex,ey,ticket,done,frameAt,50);}
 private void sendObserved(float x,float y,float ex,float ey,ActionGate.Ticket ticket,Runnable done,long frameAt,int holdMs){
  metrics.attempt(StageMetrics.Stage.ANDROID_ACK);
  if(busy){metrics.failure(StageMetrics.Stage.ANDROID_ACK,"EXECUTOR_BUSY");note="Исполнитель занят";update();return;}String rejection=invalidReason(ticket);if(rejection!=null){if(deferFocus(rejection))return;failStage(StageMetrics.Stage.ANDROID_ACK,rejection,"Привяжите текущее окно заново; ввод не отправлен");return;}
  if(!armedBounds.contains((int)x,(int)y)||!armedBounds.contains((int)ex,(int)ey)){failStage(StageMetrics.Stage.ANDROID_ACK,"OUTSIDE_WINDOW","Жест вне окна");return;}
  if(panel==null||!panel.isShown()){failStage(StageMetrics.Stage.ANDROID_ACK,"STOP_PANEL_MISSING","Нет панели Stop");return;}int[] panelXY=new int[2];panel.getLocationOnScreen(panelXY);Rect panelRect=new Rect(panelXY[0],panelXY[1],panelXY[0]+panel.getWidth(),panelXY[1]+panel.getHeight());
  Rect pathRect=new Rect((int)Math.min(x,ex)-2,(int)Math.min(y,ey)-2,(int)Math.max(x,ex)+3,(int)Math.max(y,ey)+3);
  if(Rect.intersects(panelRect,pathRect)){failStage(StageMetrics.Stage.ANDROID_ACK,"PANEL_OVERLAP","Панель перекрывает жест: передвиньте её");return;}
  if(!permit.allows(gate.epoch(),SystemClock.elapsedRealtime())){failStage(StageMetrics.Stage.ANDROID_ACK,"PERMIT_REQUIRED","Сначала явно разрешите ввод");return;}
  if(journal==null){failStage(StageMetrics.Stage.ANDROID_ACK,"JOURNAL_UNAVAILABLE","Журнал недоступен — ввода нет");return;}final long gestureId=journal.nextRequest();if(!audit(SafeJournal.Event.PROPOSED,gestureId,0)){failStage(StageMetrics.Stage.ANDROID_ACK,"JOURNAL_WRITE_FAILED","Ошибка журнала — ввода нет");return;}
  if(frameAt>=0&&(SystemClock.elapsedRealtime()<frameAt||SystemClock.elapsedRealtime()-frameAt>300)){metrics.failure(StageMetrics.Stage.CAPTURE,"STALE_BEFORE_DISPATCH");note="Кадр устарел при подготовке жеста · жду свежий";update();return;}
  String dispatchRejection=invalidReason(ticket);if(dispatchRejection!=null){if(deferFocus(dispatchRejection))return;failStage(StageMetrics.Stage.ANDROID_ACK,dispatchRejection,"Контекст изменился до отправки");return;}
  if(!gate.consume(ticket,SystemClock.elapsedRealtime(),target,ticket.window,armedBounds.width(),armedBounds.height())||!permit.consume(gate.epoch(),SystemClock.elapsedRealtime())){failStage(StageMetrics.Stage.ANDROID_ACK,"PERMIT_EXPIRED","Разрешение истекло/уже использовано");return;}
  Path path=new Path();path.moveTo(x,y);if(x!=ex||y!=ey)path.lineTo(ex,ey);
  int duration=frameAt<0&&(x!=ex||y!=ey||Build.VERSION.SDK_INT<=30)?120:Math.max(40,Math.min(240,holdMs));
  lastGestureContext="epoch="+gate.epoch()+" from="+Math.round(x)+","+Math.round(y)+" to="+Math.round(ex)+","+Math.round(ey)+" hold_ms="+duration;
  GestureDescription gesture=new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(path,0,duration)).build();
  if(frameAt>=0)arcadeDispatchFrameAge=SystemClock.elapsedRealtime()-frameAt;
  final long dispatchedNs=SystemClock.elapsedRealtimeNanos();long epoch=gate.epoch();
  final long flight=gestureFlight.begin(SystemClock.elapsedRealtime());busy=true;note="Отправка";update();
  try{boolean accepted=dispatchGesture(gesture,new GestureResultCallback(){
   @Override public void onCompleted(GestureDescription g){if(expireGesture(flight,epoch)||!gestureFlight.complete(flight))return;metrics.record(StageMetrics.Stage.ANDROID_ACK,SystemClock.elapsedRealtimeNanos()-dispatchedNs);busy=false;if((playingJumper||playingGrid)&&epoch==gate.epoch())captureCompleted++;if(!auditAt(SafeJournal.Event.ANDROID_COMPLETED,gestureId,0,epoch)){stop("Ошибка записи ACK");return;}if(epoch!=gate.epoch()){update();return;}String completionRejection=invalidReason(ticket,true);if(completionRejection!=null){if(deferFocus(completionRejection))return;failStage(StageMetrics.Stage.ANDROID_ACK,completionRejection,"Контекст изменился после жеста");return;}note="Android завершил жест; игровой исход не проверен";update();if(done!=null)done.run();}
   @Override public void onCancelled(GestureDescription g){if(expireGesture(flight,epoch)||!gestureFlight.complete(flight))return;metrics.cancelled(StageMetrics.Stage.ANDROID_ACK);busy=false;auditAt(SafeJournal.Event.ANDROID_CANCELLED,gestureId,0,epoch);if(epoch==gate.epoch())stop("Android отменил жест");else update();}
  // The default callback runs directly on the service main thread. A second
  // Handler hop could put an already delivered ACK behind its watchdog after
  // a slow framework window query. The watchdog still has its own Handler.
  },null);
  if(accepted){
   dispatchedInputs++;
   if((playingJumper||playingGrid)&&epoch==gate.epoch())captureSent++;
   // Separate handler survives STOP, so a missing callback cannot leave busy=true forever.
   gestureHandler.postDelayed(()->expireGesture(flight,epoch),GestureFlight.TIMEOUT_MS);
  }else{gestureFlight.complete(flight);busy=false;audit(SafeJournal.Event.REJECTED,gestureId,0);failStage(StageMetrics.Stage.ANDROID_ACK,"DISPATCH_REJECTED","Android отклонил жест");}
  }catch(RuntimeException e){gestureFlight.complete(flight);busy=false;failStage(StageMetrics.Stage.ANDROID_ACK,"DISPATCH_FAILED","Ошибка отправки жеста");}
 }
 private boolean expireGesture(long flight,long epoch){
  if(!gestureFlight.timeout(flight,SystemClock.elapsedRealtime()))return false;
  busy=false;if(epoch==gate.epoch())failStage(StageMetrics.Stage.ANDROID_ACK,"ACK_TIMEOUT","Android не сообщил результат жеста за 3 секунды");else update();return true;
 }
 /** Explicit test button authorizes only the marked target in our own DemoActivity. */
 public void startTouchCheck(){
  if(busy){note="Дождитесь завершения текущего жеста";update();return;}
  stop("Ожидаю окно теста касаний");showPanel();
  awaitTouchCheck(gate.epoch(),SystemClock.uptimeMillis()+2000);
 }
 private void awaitTouchCheck(long epoch,long until){
  if(epoch!=gate.epoch()||destroying)return;
  AccessibilityNodeInfo root=appRoot();int window=-1;Rect bounds=null;
  try{
   if(root!=null){
    if(!getPackageName().equals(String.valueOf(root.getPackageName()))){note="Откройте тест касаний LocalPilot";update();return;}
    if(demoScore(root)>=0){window=root.getWindowId();bounds=windowBounds(root);}
   }
  }finally{if(root!=null)root.recycle();}
  if(window>=0&&bounds!=null&&!bounds.isEmpty()){
   arm();
   if(gate.matches(getPackageName(),window,bounds.width(),bounds.height())&&bounds.equals(armedBounds)){
    permit.grantSession(gate.epoch(),SystemClock.elapsedRealtime());
    if(layout!=null){layout.x=Math.max(0,armedBounds.right-dp(160));layout.y=armedBounds.top+dp(4);}
    startDemo();return;
   }
   // A second Binder observation may already belong to a replacement window.
   stop("Ожидаю устойчивое окно теста касаний");epoch=gate.epoch();
  }
  if(SystemClock.uptimeMillis()>=until){note="Не дождался окна теста касаний · откройте его и повторите";update();return;}
  note="Ожидаю границы окна теста касаний · ввод выключен";update();
  final long waitingEpoch=epoch;
  handler.postDelayed(()->awaitTouchCheck(waitingEpoch,until),100);
 }
 private void startDemo(){if(busy||autoLeft>0||playing2048||playingMini||playingJumper||playingGrid||captureBusy)return;if(!permit.allows(gate.epoch(),SystemClock.elapsedRealtime())){note="Для демо сначала разрешите ввод";update();return;}if(!gate.armed()||!getPackageName().equals(target)){note="Сначала привяжите нашу тестовую игру";update();return;}autoLeft=10;demoVerified=0;deadline=SystemClock.elapsedRealtime()+60000;long epoch=gate.epoch();compact(true);handler.postDelayed(()->{if(epoch==gate.epoch()&&(autoLeft>0||busy)){stop("Демо: лимит 60 секунд; нет завершения серии");compact(false);}},60000);afterPanelLayout(()->demoStep(epoch));}
 private void afterPanelLayout(Runnable action){if(panel==null)return;View view=panel;view.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener(){public boolean onPreDraw(){if(view.getViewTreeObserver().isAlive())view.getViewTreeObserver().removeOnPreDrawListener(this);view.postOnAnimation(()->view.postOnAnimation(()->handler.postDelayed(action,80)));return true;}});view.requestLayout();view.invalidate();}
 private AccessibilityNodeInfo findTarget(AccessibilityNodeInfo n){if(n==null)return null;if("LOCALPILOT_DEMO_TARGET".equals(String.valueOf(n.getContentDescription()))&&n.isVisibleToUser()&&n.isClickable())return n;for(int i=0;i<n.getChildCount();i++){AccessibilityNodeInfo f=findTarget(n.getChild(i));if(f!=null)return f;}return null;}
 private int demoScore(AccessibilityNodeInfo node){
  if(node==null)return -1;
  String marker=String.valueOf(node.getContentDescription());
  if(marker.startsWith("LOCALPILOT_DEMO_SCORE:")){
   if(!node.refresh())return -1;
   marker=String.valueOf(node.getContentDescription());
   if(!marker.startsWith("LOCALPILOT_DEMO_SCORE:"))return -1;
   try{return Integer.parseInt(marker.substring("LOCALPILOT_DEMO_SCORE:".length()));}catch(NumberFormatException error){return -1;}
  }
  for(int i=0;i<node.getChildCount();i++){
   AccessibilityNodeInfo child=node.getChild(i);if(child==null)continue;
   try{int score=demoScore(child);if(score>=0)return score;}finally{child.recycle();}
  }
  return -1;
 }
 private void demoStep(long epoch){
  if(epoch!=gate.epoch()||autoLeft<=0)return;
  if(SystemClock.elapsedRealtime()>deadline){stop("Демо: лимит 60 секунд");compact(false);return;}
  AccessibilityNodeInfo root=appRoot(),node=null;
  try{
   if(root==null||!getPackageName().equals(String.valueOf(root.getPackageName()))){stop("Демо: другая программа");compact(false);return;}
   node=findTarget(root);
   if(node==null||!node.refresh()||!node.isVisibleToUser()||!node.isClickable()
      ||!"LOCALPILOT_DEMO_TARGET".equals(String.valueOf(node.getContentDescription()))){stop("Демо: нет свежей подтверждённой цели");compact(false);return;}
   int before=demoScore(root);
   if(before<0){stop("Демо: не читается счёт тестовой игры");compact(false);return;}
   Rect bounds=new Rect();node.getBoundsInScreen(bounds);
   send(bounds.centerX(),bounds.centerY(),bounds.centerX(),bounds.centerY(),gate.offer(SystemClock.elapsedRealtime()),
    ()->verifyDemoTouch(epoch,before,SystemClock.elapsedRealtime()+2000));
  }finally{if(node!=null&&node!=root)node.recycle();if(root!=null)root.recycle();}
 }
 private void verifyDemoTouch(long epoch,int before,long until){
  if(epoch!=gate.epoch()||autoLeft<=0)return;
  ActionGate.Ticket fresh=gate.offer(SystemClock.elapsedRealtime());
  if(!valid(fresh)){stop("Демо: окно изменилось при проверке касания");compact(false);return;}
  AccessibilityNodeInfo root=appRoot();int score;
  try{score=demoScore(root);}finally{if(root!=null)root.recycle();}
  if(score==before+1){
   autoLeft--;demoVerified=10-autoLeft;uiStats="Демо: подтверждено "+(10-autoLeft)+"/10 касаний";
   if(autoLeft==0){stop("Демо завершено: подтверждено 10 касаний");compact(false);}
   else{note="Касание подтверждено счётом · осталось "+autoLeft;update();handler.postDelayed(()->demoStep(epoch),300);}
   return;
  }
  if(score>before+1){stop("Демо: счёт изменился больше чем на одно касание");compact(false);return;}
  if(SystemClock.elapsedRealtime()>=until){failStage(StageMetrics.Stage.ANDROID_ACK,"DEMO_NO_TOUCH_RESPONSE","Android завершил жест, но счёт цели не вырос");return;}
  handler.postDelayed(()->verifyDemoTouch(epoch,before,until),100);
 }


 private void compact(boolean on){if(panel==null)return;panelCompact=on;for(int i=0;i<panel.getChildCount();i++){View child=panel.getChildAt(i);child.setVisibility(child==miniBar?(on?View.VISIBLE:View.GONE):(on?View.GONE:View.VISIBLE));}panel.setPadding(on?0:8,on?0:4,on?0:8,on?0:4);panel.setBackgroundColor(on?0x8C202830:0xEE202830);if(layout!=null){layout.width=dp(on?160:240);updatePanelLayout();}}
 private Rect panelBounds(){if(panel==null||consentPanelDetached)return new Rect();int[] p=new int[2];panel.getLocationOnScreen(p);return new Rect(p[0],p[1],p[0]+panel.getWidth(),p[1]+panel.getHeight());}
 /** Choosing an installed game prepares observation only; it never grants input. */
 public void selectInstalledTarget(String packageName,String label){
  stop("Открываю выбранную игру; ввод выключен");showPanel();
  browserProfileConfirmed=false;confirmedProfileScope="";
  if(packageName==null||packageName.isEmpty()){note="Выберите стороннюю игру";update();return;}
  targetLabel=label==null?applicationLabel(packageName):label;
  long epoch=gate.epoch();awaitInstalledTarget(packageName,label,epoch,SystemClock.elapsedRealtime()+10000,-1,null);
 }
 private void awaitInstalledTarget(String expected,String label,long epoch,long until,int seenWindow,Rect seenBounds){
  if(epoch!=gate.epoch())return;
  AccessibilityNodeInfo root=appRoot();int window=-1;Rect bounds=null;
  try{if(root!=null&&unlocked()&&expected.equals(String.valueOf(root.getPackageName()))){window=root.getWindowId();bounds=windowBounds(root);}}
  finally{if(root!=null)root.recycle();}
  if(bounds!=null&&window==seenWindow&&bounds.equals(seenBounds)){
   arm();if(gate.armed()){if(!gate.matches(expected,window,bounds.width(),bounds.height())||!bounds.equals(armedBounds)){stop("Окно изменилось во время выбора игры; привяжите нужное окно заново");return;}note="Игра выбрана: "+label;update();if(automatic())startPickedProfile();}return;
  }
  if(SystemClock.elapsedRealtime()>=until){stop("Не дождался окна игры. Откройте её и нажмите «Окно»");return;}
  note="Жду окно выбранной игры: "+label;update();final int nextWindow=window;final Rect nextBounds=bounds;
  handler.postDelayed(()->awaitInstalledTarget(expected,label,epoch,until,nextWindow,nextBounds),250);
 }
 private void startAutomaticGame(){
  if(busy||captureBusy||discoveringBoard||autoLeft>0||playing2048||playingMini||playingJumper||playingGrid){note="Сначала остановите текущий режим";update();return;}
  arm();if(!gate.armed())return;selectedProfile=4;startBoundDiscovery(gate.epoch());
 }
 private void startPickedProfile(){if(!getPackageName().equals(target))startSelected();else startBoundDiscovery(gate.epoch());}
 private void startBoundDiscovery(long epoch){permit.revoke();boardSearch.reset();discoveringBoard=true;compact(true);long generation=boardReadGeneration;afterPanelLayout(()->{if(generation==boardReadGeneration&&!focusPaused)findAutomaticBoard(epoch,SystemClock.elapsedRealtime()+1500);});}
 private void findAutomaticBoard(long epoch,long until){
  if(epoch!=gate.epoch()||!discoveringBoard||focusPaused)return;if(!valid(gate.offer(SystemClock.elapsedRealtime()))){if(deferFocus(invalidReason(gate.offer(SystemClock.elapsedRealtime()))))return;stop("Автопоиск: активное окно изменилось");return;}
  java.util.List<BoardSelection.Candidate> candidates=new java.util.ArrayList<>();AccessibilityNodeInfo root=appRoot();
  try{if(root!=null&&getPackageName().equals(target))collectBoardNodes(root,candidates,0,new int[]{512});}
  catch(RuntimeException error){stop("Автопоиск: разметка окна недоступна");return;}
  finally{if(root!=null)root.recycle();}
  if(!candidates.isEmpty()){
   try{prepareAutomaticBoard(BoardSelection.choose(candidates,armedBounds.left,armedBounds.top,armedBounds.right,armedBounds.bottom),epoch);}
   catch(BoardSelection.SelectionFailure error){stop(error.code+": "+error.getMessage());}return;
  }
  if(getPackageName().equals(target)&&SystemClock.elapsedRealtime()<until){postBoardTask(epoch,150,()->findAutomaticBoard(epoch,until));return;}
  // External games have no trusted virtual-board marker. Only the strict 2048 palette
  // is supported here; unsupported or ambiguous images must not start a controller.
  discoveringBoard=true;boardRoi=new Rect(armedBounds);note="Ищу поле стандартной 2048 по изображению…";update();
  captureImage(epoch,(pixels,t,ms)->locatePixelBoard(pixels,epoch));
 }
 /** Worker owns a bounded immutable pixel copy; screenshot buffers stay on the callback. */
 private void locatePixelBoard(Bitmap pixels,long epoch){
  metrics.attempt(StageMetrics.Stage.READ);int w=pixels.getWidth(),h=pixels.getHeight();
  if((long)w*h>8000000){failStage(StageMetrics.Stage.READ,"AUTO_FRAME_TOO_LARGE","Для этого разрешения выберите поле вручную");return;}
  final int[] frame;try{frame=new int[w*h];pixels.getPixels(frame,0,w,0,0,w,h);}catch(RuntimeException error){failStage(StageMetrics.Stage.READ,"AUTO_PIXEL_COPY_FAILED","Не удалось прочитать кадр");return;}
  final Rect window=new Rect(armedBounds),excluded=panelBounds();final long request=++visionRequest;visionPending=true;long started=SystemClock.elapsedRealtimeNanos();
  try{visionTask=visionWorker.submit(()->{
   try{
    BoardSelection.Candidate selected=PixelBoardLocator.find((x,y)->frame[y*w+x],w,h,window.left,window.top,window.right,window.bottom,excluded.left,excluded.top,excluded.right,excluded.bottom);
    long elapsed=SystemClock.elapsedRealtimeNanos()-started;
    handler.post(()->{if(!visionPending||request!=visionRequest||epoch!=gate.epoch())return;visionPending=false;visionTask=null;metrics.record(StageMetrics.Stage.READ,elapsed);
     long signature=PixelBoardLocator.sceneSignature((x,y)->frame[y*w+x],selected.left,selected.top,selected.right,selected.bottom,excluded.left,excluded.top,excluded.right,excluded.bottom);
     if(boardSearch.observe(selected,lastShotTimestamp,signature)){prepareAutomaticBoard(selected,epoch);return;}
     note="Автополе найдено · проверяю устойчивость следующим кадром";update();retryAutomaticBoard(epoch);});
   }catch(java.util.concurrent.CancellationException cancelled){/* Stop/timeout records cancellation on the main thread. */}
   catch(RuntimeException error){handler.post(()->{if(!visionPending||request!=visionRequest||epoch!=gate.epoch())return;visionPending=false;visionTask=null;String code=error instanceof BoardSelection.SelectionFailure?((BoardSelection.SelectionFailure)error).code:"AUTO_BOARD_FAILED";if("AUTO_BOARD_MISSING".equals(code)){
     long signature=PixelBoardLocator.sceneSignature((x,y)->frame[y*w+x],window.left,window.top,window.right,window.bottom,excluded.left,excluded.top,excluded.right,excluded.bottom);
     boardSearch.observe(null,lastShotTimestamp,signature);
     if(boardSearch.missingFrames()==3)metrics.failure(StageMetrics.Stage.READ,code);
     note=boardSearch.missingFrames()<3?"Проверяю анимацию/перекрытие поля…":"Searching · поле перекрыто или не найдено. Калибровка сохранена";update();retryAutomaticBoard(epoch);return;
    }failStage(StageMetrics.Stage.READ,code,"Поле не подтверждено. Выберите поле вручную; для другой игры нужен её профиль.");});}
  });}catch(RuntimeException error){visionPending=false;visionTask=null;failStage(StageMetrics.Stage.READ,"AUTO_WORKER_UNAVAILABLE","Автопоиск недоступен");return;}
  handler.postDelayed(()->{if(visionPending&&request==visionRequest&&epoch==gate.epoch()){visionPending=false;visionRequest++;if(visionTask!=null)visionTask.cancel(true);visionTask=null;failStage(StageMetrics.Stage.READ,"AUTO_BOARD_TIMEOUT","Автопоиск превысил 3 секунды; выберите поле вручную");}},3000);
 }
 private void retryAutomaticBoard(long epoch){
  postBoardTask(epoch,Math.max(captureDelay(),boardSearch.missingFrames()>=3?1000:0),()->{if(discoveringBoard&&!captureBusy&&!visionPending)captureImage(epoch,(pixels,t,ms)->locatePixelBoard(pixels,epoch));});
 }
 private void collectBoardNodes(AccessibilityNodeInfo node,java.util.List<BoardSelection.Candidate> out,int depth,int[] remaining){
  if(depth>32||--remaining[0]<0)throw new IllegalStateException("tree limit");
  String marker=String.valueOf(node.getContentDescription());
  if(node.isVisibleToUser()&&BoardSelection.knownMarker(marker)){Rect r=new Rect();node.getBoundsInScreen(r);out.add(new BoardSelection.Candidate(marker,r.left,r.top,r.right,r.bottom));}
  for(int i=0;i<node.getChildCount();i++){AccessibilityNodeInfo child=node.getChild(i);if(child==null)continue;try{collectBoardNodes(child,out,depth+1,remaining);}finally{child.recycle();}}
 }
 private void prepareAutomaticBoard(BoardSelection.Candidate selected,long epoch){
  final long generation=boardReadGeneration;
  if(epoch!=gate.epoch()||focusPaused)return;discoveringBoard=true;boardRoi=new Rect(selected.left,selected.top,selected.right,selected.bottom);compact(true);
  afterPanelLayout(()->{
   if(epoch!=gate.epoch()||generation!=boardReadGeneration||focusPaused)return;if(!movePanelAway(boardRoi)){stop("Панель не помещается вне поля: поверните телефон или передвиньте её");return;}
   afterPanelLayout(()->{if(epoch!=gate.epoch()||generation!=boardReadGeneration||focusPaused)return;
    if(!panelOutsideRoi(boardRoi)){stop("PANEL_COVERS_ROI: панель после перемещения закрыла найденное поле");return;}
    discoveringBoard=false;uiStats="Автополе: "+selected.profile+" · "+boardRoi.width()+"×"+boardRoi.height();if(selected.profile==BoardSelection.Profile.GAME_2048)start2048();else startMini(selected.profile==BoardSelection.Profile.TTT?0:1);});
  });
 }
 /** Dock a measured compact STOP panel outside the ROI; fail if no safe place exists. */
 private boolean movePanelAway(Rect roi){
  if(roi==null||roi.isEmpty()||panel==null||layout==null||wm==null||consentPanelDetached||armedBounds.isEmpty())return false;
  Rect current=panelBounds();if(current.isEmpty())return false;
  Point size=new Point();wm.getDefaultDisplay().getRealSize(size);
  Rect safe=new Rect(Math.max(0,armedBounds.left),Math.max(0,armedBounds.top),
    Math.min(size.x,armedBounds.right),Math.min(size.y-dp(24),armedBounds.bottom));
  if(safe.isEmpty())return false;
  PanelDocking.Area placement=PanelDocking.choose(
    new PanelDocking.Area(safe.left,safe.top,safe.right,safe.bottom),
    new PanelDocking.Area(current.left,current.top,current.right,current.bottom),
    new PanelDocking.Area(roi.left,roi.top,roi.right,roi.bottom),dp(8));
  if(placement==null)return false;
  int oldX=layout.x,oldY=layout.y;
  layout.x+=placement.left-current.left;
  layout.y+=placement.top-current.top;
  if(updatePanelLayout())return true;
  if(layout!=null){layout.x=oldX;layout.y=oldY;}
  return false;
 }
 /** WindowManager may clamp/resize panel after updateViewLayout: verify the actual layout. */
 private boolean panelOutsideRoi(Rect roi){
  Rect measured=panelBounds();
  return roi!=null&&!roi.isEmpty()&&!measured.isEmpty()&&!Rect.intersects(measured,roi);
 }
 private boolean captureEnabled(){return playing2048||playingMini||discoveringBoard||playingGrid;}
 private void selectBoard(){
  if(busy||captureBusy||discoveringBoard||playing2048||playingMini||playingJumper||playingGrid||autoLeft>0||!gate.armed()){note="Сначала привяжите неподвижное окно";update();return;}
  long epoch=gate.epoch();compact(true);final float[] start={0,0};final RectF box=new RectF();
  picker=new View(this){Paint p=new Paint(3);@Override protected void onDraw(Canvas c){c.drawColor(0x55102030);p.setColor(Color.WHITE);p.setTextSize(dp(15));c.drawText("Обведите квадрат игрового поля. Вверху — отмена",dp(8),dp(35),p);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(4);c.drawRect(box,p);p.setStyle(Paint.Style.FILL);}
   @Override public boolean onTouchEvent(MotionEvent e){if(e.getAction()==MotionEvent.ACTION_DOWN){start[0]=e.getRawX();start[1]=e.getRawY();return true;}if(e.getAction()==MotionEvent.ACTION_MOVE||e.getAction()==MotionEvent.ACTION_UP){box.set(Math.min(start[0],e.getRawX()),Math.min(start[1],e.getRawY()),Math.max(start[0],e.getRawX()),Math.max(start[1],e.getRawY()));invalidate();if(e.getAction()==MotionEvent.ACTION_UP){Rect selected=new Rect();box.round(selected);if(picker!=null){wm.removeView(picker);picker=null;}compact(false);if(epoch!=gate.epoch())return true;if(start[1]<dp(65)||!armedBounds.contains(selected)||selected.width()<160||Math.abs(selected.width()-selected.height())>selected.width()*.08){note="Выделение отменено/поле не квадратное";update();return true;}boardRoi=selected;note="Поле выбрано. Выберите авторежим игры";update();}return true;}return true;}};
  WindowManager.LayoutParams lp=new WindowManager.LayoutParams(-1,-1,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT);lp.gravity=Gravity.TOP|Gravity.LEFT;wm.addView(picker,lp);
  handler.postDelayed(()->{if(picker!=null&&epoch==gate.epoch())stop("Выделение: таймаут");},30000);
 }
 private interface BoardResult {void ready(Board2048 board,ActionGate.Ticket ticket,long captureMs);}
 private interface ImageResult {void ready(Bitmap bitmap,ActionGate.Ticket ticket,long captureMs);}
 private void retryBoardRead(long epoch,BoardResult result,String reason){
  if(epoch!=gate.epoch()||!playing2048)return;
  boardReadMisses=Math.min(1000000,boardReadMisses+1);
  if(boardReadMisses==3)metrics.failure(StageMetrics.Stage.READ,"BOARD_SEARCHING");
  note=(boardReadMisses<3?"Проверяю анимацию поля · ":"Searching · калибровка сохранена · ")+reason;update();
  long generation=boardReadGeneration;
  handler.postDelayed(()->{if(epoch==gate.epoch()&&generation==boardReadGeneration&&playing2048&&!focusPaused&&!captureBusy)screenshot(epoch,result);},Math.max(captureDelay(),boardReadMisses>=3?1000:0));
 }
 private void screenshot(long epoch,BoardResult result){captureImage(epoch,(pixels,t,ms)->{
  Rect r=new Rect(boardRoi),panel=panelBounds();
  if(playing2048&&GameOverScreen.detect(pixels::getPixel,armedBounds.left,armedBounds.top,armedBounds.right,armedBounds.bottom,panel.left,panel.top,panel.right,panel.bottom)){
   before2048=null;colorSession.cancelPending();retryBoardRead(epoch,result,"экран результата · нажмите Играть снова");return;
  }
  Board2048 board;
  try{board=PipelineRead.read(metrics,()->colored2048?colorSession.read(pixels::getPixel,pixels.getWidth(),pixels.getHeight(),r.left,r.top,r.right,r.bottom,before2048,direction2048,lastShotTimestamp):PixelBoardReader.read(pixels::getPixel,pixels.getWidth(),pixels.getHeight(),r.left,r.top,r.right,r.bottom));}
  catch(PipelineRead.Failure error){retryBoardRead(epoch,result,"ожидаю распознаваемое поле");return;}
  boardReadMisses=0;result.ready(board,t,ms);
 });}
 private boolean retryCapture(long epoch,ImageResult result,String reason){
  if(epoch!=gate.epoch()||!captureEnabled()||focusPaused||!captureRetry.allow(epoch,reason))return false;
  metrics.failure(StageMetrics.Stage.CAPTURE,reason);
  note="Снимок задержался · запрашиваю новый кадр без ввода";update();
  long generation=boardReadGeneration;
  handler.postDelayed(()->{if(epoch==gate.epoch()&&generation==boardReadGeneration&&captureEnabled()&&!focusPaused&&!captureBusy)captureImage(epoch,result);},captureDelay());
  return true;
 }
 private void captureImage(long epoch,ImageResult result){
  if(epoch!=gate.epoch()||!captureEnabled())return;
  metrics.attempt(StageMetrics.Stage.CAPTURE);
  ActionGate.Ticket ticket=gate.offer(SystemClock.elapsedRealtime());if(!valid(ticket)||boardRoi==null){if(deferFocus(invalidReason(ticket)))return;failStage(StageMetrics.Stage.CAPTURE,"BINDING_OR_ROI_MISSING","Нет привязки/поля");return;}
  if(captureBusy){failStage(StageMetrics.Stage.CAPTURE,"CAPTURE_INFLIGHT","Предыдущий захват ещё выполняется");return;}final Rect roi=new Rect(boardRoi);
  if(!discoveringBoard&&Rect.intersects(panelBounds(),roi)){failStage(StageMetrics.Stage.CAPTURE,"PANEL_COVERS_ROI","Панель закрывает поле: передвиньте её");return;}
  final long captureNs=SystemClock.elapsedRealtimeNanos();captureBusy=true;long request=SystemClock.elapsedRealtime();final long requestedUptime=SystemClock.uptimeMillis();final long requestId=++captureCounter;pendingCapture=requestId;
  TakeScreenshotCallback callback=new TakeScreenshotCallback(){
   @Override public void onSuccess(ScreenshotResult shot){android.hardware.HardwareBuffer buffer=shot.getHardwareBuffer();if(pendingCapture!=requestId){CaptureCleanup.run(buffer::close);return;}captureBusy=false;Bitmap hardware=null,bitmap=null;boolean captured=false;
    try{if(epoch!=gate.epoch()||!captureEnabled()){metrics.cancelled(StageMetrics.Stage.CAPTURE);return;}if(boardRoi==null||!roi.equals(boardRoi)){failStage(StageMetrics.Stage.CAPTURE,"ROI_CHANGED","Область снимка изменилась");return;}if(!CaptureFreshness.valid(shot.getTimestamp(),requestedUptime,SystemClock.uptimeMillis(),lastShotTimestamp)){if(!retryCapture(epoch,result,"STALE_FRAME"))failStage(StageMetrics.Stage.CAPTURE,"STALE_FRAME","Снимок устарел/повторён после повторов");return;}long previousTimestamp=lastShotTimestamp;lastShotTimestamp=shot.getTimestamp();
     hardware=Bitmap.wrapHardwareBuffer(buffer,shot.getColorSpace());if(hardware==null)throw new IllegalArgumentException("Нет пикселей");bitmap=hardware.copy(Bitmap.Config.ARGB_8888,false);
     Point size=new Point();wm.getDefaultDisplay().getRealSize(size);if(bitmap.getWidth()!=size.x||bitmap.getHeight()!=size.y)throw new IllegalArgumentException("Масштаб снимка не подтверждён");
     final Bitmap pixels=bitmap;
     // One final window query after pixel conversion is sufficient; querying the
     // same Binder tree twice per callback can consume the entire frame budget.
     String rejection=invalidReason(ticket);
     if(rejection!=null){if(deferFocus(rejection)||retryCapture(epoch,result,rejection))return;failStage(StageMetrics.Stage.CAPTURE,rejection,"Контекст/срок снимка изменился");return;}
     if(!CaptureFreshness.valid(shot.getTimestamp(),requestedUptime,SystemClock.uptimeMillis(),previousTimestamp)){if(!retryCapture(epoch,result,"FRAME_TTL_EXPIRED"))failStage(StageMetrics.Stage.CAPTURE,"FRAME_TTL_EXPIRED","Обработка кадра превысила TTL после повторов");return;}
     metrics.record(StageMetrics.Stage.CAPTURE,SystemClock.elapsedRealtimeNanos()-captureNs);captured=true;captureRetry.fresh(epoch);result.ready(pixels,ticket,SystemClock.elapsedRealtime()-request);
     // Board games use Accessibility screenshots rather than MediaProjection.
     // Refresh their preview too, so it cannot keep showing the last arcade.
     long previewAt=SystemClock.elapsedRealtime();
     if(diagnosticFrameAt<0||previewAt-diagnosticFrameAt>=1000){
      clearDiagnosticFrame();
      try{diagnosticFrame=Bitmap.createBitmap(roi.width(),roi.height(),Bitmap.Config.ARGB_8888);new Canvas(diagnosticFrame).drawBitmap(pixels,roi,new Rect(0,0,roi.width(),roi.height()),null);diagnosticFrameAt=previewAt;diagnosticFrameGeometry="profile="+profileLabel()+" roi="+roi.toShortString()+" frame_elapsed_ms="+previewAt;}
      catch(RuntimeException|OutOfMemoryError ignored){clearDiagnosticFrame();}
     }
    }catch(RuntimeException|OutOfMemoryError error){if(epoch==gate.epoch()){if(!captured)failStage(StageMetrics.Stage.CAPTURE,"BITMAP_FAILED","Не удалось подготовить пиксели");else stop("Ошибка обработки прочитанного кадра · захват остановлен");}}finally{Bitmap oldBitmap=bitmap,oldHardware=hardware;boolean released=CaptureCleanup.run(()->{if(oldBitmap!=null)oldBitmap.recycle();},()->{if(oldHardware!=null)oldHardware.recycle();},buffer::close);if(!released&&epoch==gate.epoch())stop("Ошибка освобождения кадра · захват остановлен");}
   }
   @Override public void onFailure(int error){if(pendingCapture!=requestId)return;captureBusy=false;if(epoch==gate.epoch()&&error==ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT&&retryCapture(epoch,result,"SCREENSHOT_INTERVAL"))return;if(epoch==gate.epoch())failStage(StageMetrics.Stage.CAPTURE,"ANDROID_SCREENSHOT_ERROR","Захват Android: ошибка "+error+". Проверьте доступ службы");}
  };
  try{takeScreenshot(Display.DEFAULT_DISPLAY,getMainExecutor(),callback);}catch(RuntimeException e){captureBusy=false;failStage(StageMetrics.Stage.CAPTURE,"SCREENSHOT_UNAVAILABLE","Захват недоступен");return;}
  handler.postDelayed(()->{if(captureBusy&&pendingCapture==requestId&&epoch==gate.epoch()){captureBusy=false;pendingCapture=++captureCounter;failStage(StageMetrics.Stage.CAPTURE,"SCREENSHOT_TIMEOUT","Захват: таймаут без ввода");}},4000);
 }
 private long captureDelay(){return Build.VERSION.SDK_INT>=31?400:1100;}
 private void stable(long epoch,BoardResult result){
  long generation=boardReadGeneration;
  screenshot(epoch,(a,t,ms)->{
   if(generation!=boardReadGeneration||epoch!=gate.epoch())return;
   handler.postDelayed(()->{
    if(generation!=boardReadGeneration||epoch!=gate.epoch()||focusPaused)return;
    screenshot(epoch,(b,fresh,captureMs)->{
     if(generation!=boardReadGeneration||epoch!=gate.epoch())return;
     if(!a.same(b)){note="Поле меняется · ожидаю два устойчивых кадра";update();handler.postDelayed(()->{if(epoch==gate.epoch()&&generation==boardReadGeneration&&playing2048&&!focusPaused)stable(epoch,result);},captureDelay());return;}
     if(colored2048)try{int knownBefore=colorSession.learnedCount();colorSession.confirm(b);if(colorSession.learnedCount()>knownBefore)getSharedPreferences("color-2048",MODE_PRIVATE).edit().putString(color2048PaletteKey(),colorSession.encodeLearned()).apply();}catch(IllegalStateException error){handler.postDelayed(()->{if(epoch==gate.epoch()&&generation==boardReadGeneration&&playing2048&&!focusPaused)stable(epoch,result);},captureDelay());return;}
     result.ready(b,fresh,captureMs);
    });
   },captureDelay());
  });
 }
 private void start2048(){start2048(false);}
 private void start2048(boolean colored){
  if(busy||captureBusy||discoveringBoard||autoLeft>0||playing2048||playingMini||playingJumper||playingGrid||!gate.armed()||boardRoi==null){note=colored?"Цветной 2048: привяжите игру и выберите поле 4×4":"Привязать → Поле 4×4 → Авто2048";update();return;}
  boardReadGeneration++;boardReadMisses=0;colorSession.reset();before2048=null;colored2048=colored;
  learning2048Writable=true;learning2048=loadLiveGameLearning(LiveGameLearning.Game.GAME_2048,colored);
  if(colored2048){String saved=getSharedPreferences("color-2048",MODE_PRIVATE).getString(color2048PaletteKey(),null);if(saved!=null)try{colorSession.restoreLearned(saved);}catch(IllegalArgumentException invalidPalette){note="Сохранённая цветовая палитра повреждена · начинаю с базовых плиток";}}
  long epoch=gate.epoch(),generation=boardReadGeneration;playing2048=true;compact(true);note=colored2048?"Читаю цветное поле 2048 · изучено цветов "+colorSession.learnedCount():"Читаю поле в памяти…";update();
  afterPanelLayout(()->{
   if(epoch!=gate.epoch()||generation!=boardReadGeneration||!playing2048||focusPaused)return;
   if(!movePanelAway(boardRoi)){stop("Панель STOP закрывает поле: передвиньте её и повторите");return;}
   afterPanelLayout(()->{if(epoch==gate.epoch()&&generation==boardReadGeneration&&playing2048&&!focusPaused&&!panelOutsideRoi(boardRoi)){
     stop("PANEL_COVERS_ROI: панель после перемещения закрыла 2048");return;
    }
    if(epoch==gate.epoch()&&generation==boardReadGeneration&&playing2048&&!focusPaused)postBoardTask(epoch,captureDelay(),()->stable(epoch,(board,t,ms)->{
   StringBuilder text=new StringBuilder("Сверьте все 16 клеток с игрой:\n");for(int i=0;i<16;i++){text.append(board.cells[i]==0?"·":board.cells[i]).append(i%4==3?"\n":"  ");}
   text.append("До STOP или конца партии. CPU-правила + опыт только проверенных ходов, без PPO.");
   if(automatic()){permit.grantSession(epoch,SystemClock.elapsedRealtime());moves2048=0;mergeTotal=0;before2048=null;uiStats=text.toString();postBoardTask(epoch,captureDelay(),()->round2048(epoch));return;}
   preview=new AlertDialog.Builder(this).setTitle("2048 — начать игру?").setMessage(text.toString()).setPositiveButton("Поле верное, начать",(dialog,which)->{preview=null;if(epoch!=gate.epoch()||generation!=boardReadGeneration||!playing2048||focusPaused)return;permit.grantSession(epoch,SystemClock.elapsedRealtime());moves2048=0;mergeTotal=0;before2048=null;postBoardTask(epoch,captureDelay(),()->round2048(epoch));}).setNegativeButton("Отмена",(d,w)->stop("2048 отменён")).setCancelable(false).create();preview.getWindow().setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);preview.getWindow().addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);preview.show();
   }));});
  });
 }
 private void startColored2048(){
  if(busy||captureBusy||discoveringBoard||autoLeft>0||playing2048||playingMini||playingJumper||playingGrid){note="Сначала остановите текущий режим";update();return;}
  if(!gate.armed())arm();
  if(!gate.armed()||boardRoi==null){note="Цветной 2048: Привязать → Выбрать поле 4×4 → Цветной 2048";update();return;}
  start2048(true);
 }
 private void round2048(long epoch){
  if(epoch!=gate.epoch()||!playing2048||focusPaused)return;
  stable(epoch,(board,ticket,captureMs)->{
   if(epoch!=gate.epoch()||!playing2048)return;
   if(before2048!=null&&!before2048.verifies(board,direction2048)){Board2048.Verification why=before2048.verification(board,direction2048);note="2048: Android завершил жест · "+(why==Board2048.Verification.UNCHANGED?"поле не изменилось":why==Board2048.Verification.MISSING_SPAWN?"нет ожидаемой новой плитки":"поле отличается от расчёта")+" · наблюдаю без ввода";update();long generation=boardReadGeneration;handler.postDelayed(()->{if(epoch==gate.epoch()&&generation==boardReadGeneration&&playing2048&&!focusPaused)round2048(epoch);},Math.max(1000,captureDelay()));return;}
   if(before2048!=null){
    if(!saveExample("2048",before2048.cells,direction2048,board.cells))return;
    int verifiedReward=Verified2048.mergeReward(before2048,direction2048,board);
    mergeTotal+=verifiedReward;
    if(!audit(SafeJournal.Event.VERIFIED_2048,0,mergeTotal)){stop("Ошибка журнала результатов");return;}
    learning2048.verified2048(direction2048,verifiedReward);
    saveLiveGameLearning(learning2048,colored2048);
    before2048=null;
   }
   if(board.max()>=2048){stop("2048: распознана плитка 2048; сверить визуально");return;}
   metrics.attempt(StageMetrics.Stage.POLICY);long policyNs=SystemClock.elapsedRealtimeNanos();long started=SystemClock.elapsedRealtime();int d;try{d=board.choose(learning2048::bonus2048);}catch(RuntimeException error){failStage(StageMetrics.Stage.POLICY,"POLICY_FAILED","Не удалось выбрать ход");return;}metrics.record(StageMetrics.Stage.POLICY,SystemClock.elapsedRealtimeNanos()-policyNs);long think=SystemClock.elapsedRealtime()-started;if(d<0){stop("2048: допустимых ходов нет");return;}if(!Verified2048.mask(board)[d]){stop("2048: ход вне маски");return;}if(!valid(ticket)){if(deferFocus(invalidReason(ticket)))return;stop("План устарел");return;}
   Rect r=new Rect(boardRoi);float x=r.exactCenterX(),y=r.exactCenterY(),dx=0,dy=0;switch(d){case Board2048.LEFT:dx=-r.width()*.30f;break;case Board2048.RIGHT:dx=r.width()*.30f;break;case Board2048.UP:dy=-r.height()*.30f;break;case Board2048.DOWN:dy=r.height()*.30f;break;}
   before2048=board;direction2048=d;moves2048++;uiStats="кадр "+captureMs+" мс · CPU "+think+" мс · проверено "+learning2048.verifiedCount()+" · опыт игры";note="Ход "+moves2048+" · max "+board.max()+" · кадр "+captureMs+"мс · CPU "+think+"мс";update();
   long sentBefore=dispatchedInputs;
   send(x-dx/2,y-dy/2,x+dx/2,y+dy/2,ticket,()->{note="Проверяю исход хода "+moves2048;update();long generation=boardReadGeneration;handler.postDelayed(()->{if(generation==boardReadGeneration)round2048(epoch);},captureDelay());});
   if(dispatchedInputs==sentBefore&&playing2048&&epoch==gate.epoch()){
    before2048=null;moves2048--;long generation=boardReadGeneration;
    if(!focusPaused)handler.postDelayed(()->{if(generation==boardReadGeneration)round2048(epoch);},captureDelay());
   }
  });
 }

 private boolean miniMarker(AccessibilityNodeInfo root,String marker,int depth){if(root==null||depth>30)return false;if(marker.equals(String.valueOf(root.getContentDescription())))return true;for(int i=0;i<Math.min(100,root.getChildCount());i++)if(miniMarker(root.getChild(i),marker,depth+1))return true;return false;}
 private interface MiniResult {void ready(int[] board,ActionGate.Ticket ticket);}
 private void miniCapture(long epoch,MiniResult result){captureImage(epoch,(pixels,t,ms)->{Rect r=new Rect(boardRoi);int[] b;try{b=PipelineRead.read(metrics,()->MiniReader.read(miniKind,pixels::getPixel,pixels.getWidth(),pixels.getHeight(),r.left,r.top,r.right,r.bottom));}catch(PipelineRead.Failure error){stop("READ / "+error.code+": "+error.getMessage());return;}result.ready(b,t);});}
 private void miniStable(long epoch,MiniResult result){miniCapture(epoch,(a,t)->handler.postDelayed(()->miniCapture(epoch,(b,fresh)->{if(!java.util.Arrays.equals(a,b)){stop("Поле мини-игры меняется");return;}result.ready(b,fresh);}),captureDelay()));}
 private void startMini(int kind){
  if(busy||captureBusy||discoveringBoard||autoLeft>0||playing2048||playingMini||playingJumper||playingGrid||!gate.armed()||boardRoi==null){note="Привязать → выбрать поле → авторежим";update();return;}
  if(!getPackageName().equals(target)||!miniMarker(appRoot(),"LOCALPILOT_MINI_"+kind,0)){stop("Профиль поддерживает только выбранную встроенную мини-игру");return;}
  miniKind=kind;playingMini=true;compact(true);long epoch=gate.epoch();handler.postDelayed(()->miniStable(epoch,(board,ticket)->{
   StringBuilder message=new StringBuilder("Сверьте клетки: \n");int n=MiniPolicy.size(kind);for(int i=0;i<board.length;i++)message.append(board[i]).append(i%n==n-1?"\n":" ");message.append(kind==0?"0 пусто, 1 X, 2 O. До конца партии.":"0 выключено, 1 включено. До решения поля.");
   if(automatic()){permit.grantSession(epoch,SystemClock.elapsedRealtime());miniBefore=null;miniMoves=0;uiStats=message.toString();handler.postDelayed(()->miniRound(epoch),captureDelay());return;}
   preview=new AlertDialog.Builder(this).setTitle(kind==0?"Крестики-нолики":"Погаси свет").setMessage(message).setPositiveButton("Верно, разрешить",(d,w)->{preview=null;if(epoch!=gate.epoch())return;miniBefore=null;miniMoves=0;permit.grantSession(epoch,SystemClock.elapsedRealtime());handler.postDelayed(()->miniRound(epoch),captureDelay());}).setNegativeButton("Отмена",(d,w)->stop("Мини-игра отменена")).setCancelable(false).create();preview.getWindow().setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);preview.getWindow().addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);preview.show();
  }),captureDelay());
 }
 private void miniRound(long epoch){if(epoch!=gate.epoch()||!playingMini)return;miniStable(epoch,(board,ticket)->{
  if(miniBefore!=null&&!MiniPolicy.verifies(miniKind,miniBefore,miniAction,board)){stop("Мини-игра: исход касания не подтверждён");return;}
  if(miniBefore!=null&&!saveExample(miniKind==0?"ttt":"lights",miniBefore,miniAction,board))return;
  metrics.attempt(StageMetrics.Stage.POLICY);long policyNs=SystemClock.elapsedRealtimeNanos();int action;try{action=MiniPolicy.choose(miniKind,board);}catch(RuntimeException error){failStage(StageMetrics.Stage.POLICY,"POLICY_FAILED","Не удалось выбрать ход мини-игры");return;}metrics.record(StageMetrics.Stage.POLICY,SystemClock.elapsedRealtimeNanos()-policyNs);if(action==-1){stop("Мини-игра: терминальное поле распознано — сверить результат");return;}if(action<0){stop("Мини-игра: состояние/решение UNKNOWN");return;}
  if(!miniMarker(appRoot(),"LOCALPILOT_MINI_"+miniKind,0)){stop("Мини-игра изменилась");return;}
  int n=MiniPolicy.size(miniKind);Rect r=new Rect(boardRoi);float x=r.left+(action%n+.5f)*r.width()/n,y=r.top+(action/n+.5f)*r.height()/n;miniBefore=board.clone();miniAction=action;miniMoves++;uiStats="Проверено касаний: "+(miniMoves-1);send(x,y,x,y,ticket,()->handler.postDelayed(()->miniRound(epoch),captureDelay()));
 });}
 @Override public void onAccessibilityEvent(AccessibilityEvent e){
  // Canvas/content and our own status-label updates do not change the binding.
  // Re-querying the window tree for each such event floods the main thread and
  // can block gesture callbacks on older Android. Every dispatch and completion
  // still validates the current app, window, bounds, lock state and ticket.
  if(e==null||e.getEventType()!=AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED&&e.getEventType()!=AccessibilityEvent.TYPE_WINDOWS_CHANGED)return;
  if(!gate.armed())return;if(playingJumper&&jumperConsentPending)return;
  String reason=windowRejection(null,false,true);
  if(reason!=null){if(!deferFocus(reason))stop("Смена окна/блокировка — STOP ["+reason+"]");return;}
  if(focusPaused){watchFocus(gate.epoch(),++focusWatchId);return;}
  if(targetIsBrowser&&(playingJumper&&arcadeRoundSeen||playingGrid||playing2048||playingMini)
    &&e.getEventType()==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED&&target.equals(String.valueOf(e.getPackageName()))){stop("Состояние браузера изменилось · выберите игру и нажмите Старт заново");return;}

 }
 @Override public void onInterrupt(){CrashDiagnostics.phase("SERVICE_INTERRUPTED");stop("Служба прервана");}
 private DisplayGeometry readDisplayGeometry(){
  if(wm==null)return null;
  try{Display display=wm.getDefaultDisplay();Point size=new Point();display.getRealSize(size);
   android.util.DisplayMetrics metrics=new android.util.DisplayMetrics();display.getRealMetrics(metrics);
   return new DisplayGeometry(size.x,size.y,display.getRotation(),metrics.densityDpi);
  }catch(RuntimeException error){return null;}
 }
 @Override public void onConfigurationChanged(android.content.res.Configuration c){super.onConfigurationChanged(c);
  DisplayGeometry next=readDisplayGeometry();boolean changed=displayGeometry==null||displayGeometry.requiresRebind(next);displayGeometry=next;
  // Insets, locale or font metadata may change while the physical display and
  // target window remain the same. The normal per-frame window gate still applies.
  if(!changed){update();return;}
  stop("Поворот/конфигурация — STOP. Выберите окно игры заново");explicitWindow=false;browserProfileConfirmed=false;visualRoi=null;manualVisualRoi=false;visualOwner="";visualWindow=-1;visualWindowBounds.setEmpty();if(layout!=null){layout.x=0;layout.y=dp(48);updatePanelLayout();}}
 @Override public void onDestroy(){destroying=true;CrashDiagnostics.phase("SERVICE_DESTROYED");permit.revoke();gate.stop();if(instance==this)instance=null;try{CaptureCleanup.run(this::hidePanel,this::clearDiagnosticFrame,gestureFlight::clear,()->gestureHandler.removeCallbacksAndMessages(null),()->handler.removeCallbacksAndMessages(null),()->visionWorker.shutdownNow());}finally{busy=false;super.onDestroy();}}
}
