package org.localpilot.android;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/** Android wiring for calibrated mechanics. Gesture authority remains exclusively in PilotService. */
final class DeferredGameBridge {
 interface FrameConsumer { void accept(Bitmap image,ActionGate.Ticket ticket,long timestamp); }
 interface Host {
  Context context();long epoch();long generation();int window();int profile();String scope();
  Rect bounds();Rect roi();Rect panel();boolean automatic();boolean active();boolean bind();
  AccessibilityNodeInfo root();void stop(String reason);void note(String message);void stats(String text,int verified);
  void menu(String title,String[] choices,IntConsumer callback);void dialog(AlertDialog dialog);
  void pick(String title,Rect bounds,Consumer<Rect> selected);void capture(FrameConsumer consumer);
  boolean dispatch(float x,float y,float toX,float toY,ActionGate.Ticket ticket,long frameAt,int duration,Runnable ack);
  void repeat(Runnable callback,int delay);void read();void decision();
 }
 private final Host host;
 private final AdaptiveGridReader adaptive=new AdaptiveGridReader();
 private final HexaSession hexa=new HexaSession();
 private final RoundRestartController restart=new RoundRestartController();
 private HexaConfig hexaConfig;private HexaVision.Calibration hexaColors;private HexaVision.Reader hexaReader;
 private boolean hexaRunning,hexaCalibrationAttempted;
 private final BubbleBoosterSession booster=new BubbleBoosterSession();private BubbleBoosterCalibration boosterCalibration;
 private BubbleVision.BombCalibration bomb;private String bombScope="";
 private Bitmap calibrationFrame;
 private long observedFrame,restartEpoch=-1,restartGeometry=-1;
 private int previousVerified;
 private Rect restartModal;
 private final Rect replayButton=new Rect();private boolean unsafeUi;private int scanBudget;
 DeferredGameBridge(Host host){this.host=host;}
 private SharedPreferences prefs(){return host.context().getSharedPreferences("deferred-mechanics-v1",Context.MODE_PRIVATE);}
 private String key(String suffix){return host.scope()+"."+suffix;}
 boolean farmWraps(){return prefs().getBoolean(key("wrap-quest"),false);}
 boolean stompEnabled(){return host.profile()==13;}
 boolean adaptiveEnabled(){return prefs().getBoolean(key("adaptive"),true);}
 void stop(){booster.reset();hexaRunning=false;hexa.reset();restart.stop();restartEpoch=restartGeometry=-1;previousVerified=0;adaptive.reset();
  if(calibrationFrame!=null){if(!calibrationFrame.isRecycled())calibrationFrame.recycle();calibrationFrame=null;}}
 void invalidate(){booster.invalidate();hexa.invalidateCandidate();restart.invalidate();adaptive.reset();}
 void options(){
  host.menu("Новые механики",new String[]{"Бомба в пушке · запомнить образец","Гексон · размер и режим","Гексон · калибровать пустую ячейку","Гексон · форма поля","Квест пролётов через края: "+(farmWraps()?"включён":"выключен"),"Адаптивные цвета: "+(adaptiveEnabled()?"включены":"выключены"),"Авто-перезапуск · настроить","Прозрачность панели","Кнопка бомбы · запомнить готовое состояние"},i->{
   if(i==0)calibrateBomb();else if(i==1)configureHexa();else if(i==2)calibrateHexa();else if(i==3)configureHexaMask();
   else if(i==4){prefs().edit().putBoolean(key("wrap-quest"),!farmWraps()).apply();host.note("Квест пролётов сохранён · нажмите Старт");}
   else if(i==5){prefs().edit().putBoolean(key("adaptive"),!adaptiveEnabled()).apply();adaptive.reset();host.note("Настройка адаптивных цветов сохранена");}
   else if(i==6)configureRestart();else if(i==8)calibrateBooster();else host.menu("Прозрачность",new String[]{"85% · контрастная","65% · полупрозрачная","45% · прозрачная"},n->{prefs().edit().putInt("panel-alpha",new int[]{217,166,115}[n]).apply();host.note("Прозрачность сохранена; скрытые панелью пиксели не используются");});
  });
 }
 int panelAlpha(){return prefs().getInt("panel-alpha",166);}
 BubbleVision.BombCalibration bombCalibration(){
  String scope=host.scope();if(!scope.equals(bombScope)){bombScope=scope;bomb=null;boosterCalibration=null;String saved=prefs().getString(key("bomb"),null);
   if(saved!=null)try{bomb=BubbleVision.BombCalibration.decode(saved);}catch(IllegalArgumentException ignored){bomb=null;}}
  return bomb;
 }
 private void calibrateBomb(){
  host.stop("Калибровка заряженной бомбы · STOP");if(host.profile()!=9||!host.bind()||host.roi()==null){host.note("Выберите Шипучку и область с пушкой");return;}
  final Rect roi=new Rect(host.roi());host.note("Покажите уже заряженную бомбу и обведите только шар в пушке");
  host.capture((image,ticket,at)->{calibrationFrame=image.copy(Bitmap.Config.ARGB_8888,false);
   host.pick("Обведите заряженную бомбу",roi,box->{
    try{Bitmap sample=calibrationFrame;if(sample==null||sample.isRecycled()||!roi.contains(box))throw new IllegalArgumentException();
     bomb=BubbleVision.BombCalibration.capture(sample::getPixel,sample.getWidth(),sample.getHeight(),roi.left,roi.top,roi.right,roi.bottom,
       box.exactCenterX()-roi.left,box.exactCenterY()-roi.top,Math.min(box.width(),box.height()));
     bombScope=host.scope();prefs().edit().putString(key("bomb"),bomb.encode()).apply();host.note("Образец бомбы сохранён · свежий кадр должен подтвердить его перед выстрелом");
    }catch(IllegalArgumentException error){host.note("Бомба не подтверждена: обведите один разноцветный заряженный шар");}
    finally{releaseCalibration();}
   });
  });
 }
 private void releaseCalibration(){if(calibrationFrame!=null){if(!calibrationFrame.isRecycled())calibrationFrame.recycle();calibrationFrame=null;}}
 private void calibrateBooster(){
  host.stop("Калибровка готовой кнопки бомбы · STOP");if(host.profile()!=9||!host.bind())return;
  Rect view=host.bounds();host.capture((image,ticket,at)->{calibrationFrame=image.copy(Bitmap.Config.ARGB_8888,false);host.pick("Обведите только готовую кнопку бомбы",view,box->{
   try{Bitmap f=calibrationFrame;if(f==null||f.isRecycled())return;boosterCalibration=BubbleBoosterCalibration.capture(f::getPixel,f.getWidth(),f.getHeight(),view.left,view.top,view.right,view.bottom,box.left,box.top,box.right,box.bottom);prefs().edit().putString(key("booster"),boosterCalibration.encode()).apply();host.note("Кнопка сохранена; активация требует отдельного образца заряженной бомбы");}catch(IllegalArgumentException error){host.note("Состояние кнопки не подтверждено");}finally{releaseCalibration();}
  });});
 }
 boolean booster(Bitmap image,BubbleBoard board,boolean stable,ActionGate.Ticket ticket,long at,Runnable repeat){
  if(board==null||bombCalibration()==null)return false;
  if(boosterCalibration==null){String saved=prefs().getString(key("booster"),null);if(saved!=null)try{boosterCalibration=BubbleBoosterCalibration.decode(saved);}catch(IllegalArgumentException ignored){return false;}}
  if(boosterCalibration==null)return false;Rect v=host.bounds(),p=host.panel();
  boolean ready=boosterCalibration.ready(image::getPixel,image.getWidth(),image.getHeight(),v.left,v.top,v.right,v.bottom,p.left,p.top,p.right,p.bottom);
  BubbleBoosterSession.Status status=booster.observe(ready,board.candidateSpecial(),at,SystemClock.elapsedRealtime());
  if(status==BubbleBoosterSession.Status.PAUSED){host.note("Бомба: активация не подтверждена · наблюдение без ввода");host.repeat(repeat,1000);return true;}
  if(booster.pending()){host.repeat(repeat,350);return true;}
  if(status!=BubbleBoosterSession.Status.READY||!stable||!host.automatic())return false;
  long token=booster.prepare(SystemClock.elapsedRealtime());float x=boosterCalibration.centerX(v.left,v.right),y=boosterCalibration.centerY(v.top,v.bottom);
  boolean sent=host.dispatch(x,y,x,y,ticket,at,50,()->{booster.acknowledge(token,SystemClock.elapsedRealtime());host.repeat(repeat,350);});
  if(sent)booster.sent(token,SystemClock.elapsedRealtime());else{booster.cancelUnsent(token);host.repeat(repeat,400);}return true;
 }
 private void configureHexa(){
  host.menu("Гексон · размер поля без трёх слотов снизу",new String[]{"3 × 4","3 × 5","4 × 4","4 × 5","5 × 5","6 × 6","Режим: "+(prefs().getBoolean(key("hexa-fill"),false)?"заполнять поле":"сортировать цвета")},i->{
   if(i==6){prefs().edit().putBoolean(key("hexa-fill"),!prefs().getBoolean(key("hexa-fill"),false)).apply();host.note("Режим Гексона сохранён");return;}
   int[] rows={3,3,4,4,5,6},cols={4,5,4,5,5,6};prefs().edit().putInt(key("hexa-rows"),rows[i]).putInt(key("hexa-cols"),cols[i]).remove(key("hexa-mask")).remove(key("hexa-colors")).apply();
   host.note("Размер Гексона сохранён · Область: обведите поле, оставив три слота видимыми ниже");
  });
 }
 void regionChanged(){hexaReader=null;hexaConfig=null;hexaColors=null;hexaCalibrationAttempted=false;adaptive.reset();restart.invalidate();}
 private HexaConfig config(){
  Rect r=host.roi();if(r==null||!host.bounds().contains(r))return null;
  int rows=prefs().getInt(key("hexa-rows"),4),cols=prefs().getInt(key("hexa-cols"),4);
  HexaConfig plain=new HexaConfig(rows,cols,r.left,r.top,r.right,r.bottom);
  long mask=prefs().getLong(key("hexa-mask"),plain.activeMask);
  return new HexaConfig(rows,cols,r.left,r.top,r.right,r.bottom,mask);
 }
 private void configureHexaMask(){
  if(!host.bind())return;HexaConfig c=config();if(c==null){host.note("Сначала выберите область Гексона");return;}
  String[] labels=new String[c.size()];boolean[] checked=new boolean[c.size()];
  for(int i=0;i<labels.length;i++){labels[i]="Ряд "+(i/c.cols+1)+", клетка "+(i%c.cols+1);checked[i]=c.active(i);}
  host.dialog(new AlertDialog.Builder(host.context()).setTitle("Существующие ячейки поля")
   .setMultiChoiceItems(labels,checked,(d,i,value)->checked[i]=value).setNegativeButton("Отмена",null)
   .setPositiveButton("Сохранить",(d,which)->{long mask=0;for(int i=0;i<checked.length;i++)if(checked[i])mask|=1L<<i;
    try{new HexaConfig(c.rows,c.cols,c.left,c.top,c.right,c.bottom,mask);prefs().edit().putLong(key("hexa-mask"),mask).remove(key("hexa-colors")).apply();regionChanged();host.note("Форма поля сохранена");}
    catch(IllegalArgumentException error){host.note("Нужна хотя бы одна существующая ячейка");}}).create());
 }
 private void calibrateHexa(){
  host.stop("Калибровка Гексона · STOP");if(host.profile()!=12||!host.bind()){host.note("Сначала выберите механику Гексон");return;}
  HexaConfig c=config();if(c==null){host.note("Обведите само поле Гексона через Область");return;}
  host.capture((image,ticket,at)->{calibrationFrame=image.copy(Bitmap.Config.ARGB_8888,false);host.pick("Обведите одну доказанно пустую ячейку",new Rect(c.left,c.top,c.right,c.bottom),box->{
   try{Bitmap sample=calibrationFrame;if(sample==null||sample.isRecycled())throw new IllegalArgumentException();
    int row=(int)((box.exactCenterY()-c.top)/c.cellHeight());int col=Math.round((box.exactCenterX()-c.left)/c.cellWidth()-.5f-(row&1)*.5f);
    hexaColors=HexaVision.calibrate(sample::getPixel,c,row,col);prefs().edit().putString(key("hexa-colors"),hexaColors.encode()).apply();hexaReader=new HexaVision.Reader(c,hexaColors);hexaConfig=c;host.note("Пустая ячейка и цвета стопок подтверждены · нажмите Старт");
   }catch(IllegalArgumentException|IndexOutOfBoundsException error){host.note("Не удалось подтвердить пустую ячейку и различимые цвета");}
   finally{releaseCalibration();}
  });});
 }
 boolean startHexa(){
  hexaConfig=config();if(hexaConfig==null){host.note("Выберите размер и область Гексона");return false;}
  hexa.reset();hexaReader=null;hexaColors=null;hexaCalibrationAttempted=false;
  String saved=prefs().getString(key("hexa-colors"),null);if(saved!=null)try{hexaColors=HexaVision.Calibration.decode(saved);hexaReader=new HexaVision.Reader(hexaConfig,hexaColors);}catch(IllegalArgumentException ignored){hexaColors=null;}
  hexaRunning=true;bindRestart();stepHexa(host.epoch());return true;
 }
 private void stepHexa(long epoch){
  if(!hexaRunning||epoch!=host.epoch()||host.profile()!=12||!host.active())return;
  host.capture((image,ticket,at)->{
   if(!hexaRunning||epoch!=host.epoch())return;
   Rect panel=host.panel();
   if(GameOverScreen.detect(image::getPixel,host.bounds().left,host.bounds().top,host.bounds().right,host.bounds().bottom,panel.left,panel.top,panel.right,panel.bottom)){
    result(image::getPixel,ticket,at);host.note("Гексон · экран результата · "+restart.reason());repeatHexa(epoch,1000);return;}
   if(hexaReader==null&&!hexaCalibrationAttempted){hexaCalibrationAttempted=true;try{hexaColors=HexaVision.calibrateFromScene(image::getPixel,hexaConfig,image.getWidth(),image.getHeight());hexaReader=new HexaVision.Reader(hexaConfig,hexaColors);}catch(IllegalArgumentException error){host.note("Гексон: Ещё → Новые механики → калибровать пустую ячейку");}}
   if(hexaReader==null){repeatHexa(epoch,1000);return;}
   HexaVision.Observation observation=hexaReader.read(image::getPixel,image.getWidth(),image.getHeight(),at,panel.left,panel.top,panel.right,panel.bottom);host.read();
   HexaSession.Status status=hexa.observe(observation.usable()?observation.board():null,at,SystemClock.elapsedRealtime());
   host.stats("Гексон · подтверждено "+hexa.verified()+" · "+hexa.reason(),hexa.verified());
   boolean resumed=gameplay(observation.usable(),observation.board().signature(),status==HexaSession.Status.VERIFIED,at);
   if(!resumed||status!=HexaSession.Status.READY&&status!=HexaSession.Status.VERIFIED){host.note(observation.reason()+" · "+hexa.reason());repeatHexa(epoch,status==HexaSession.Status.PAUSED?1000:350);return;}
   prefs().edit().putString(key("hexa-colors"),hexaColors.encode()).apply();
   HexaPolicy.Move move=HexaPolicy.choose(observation.board(),prefs().getBoolean(key("hexa-fill"),false));host.decision();
   if(move==null||!host.automatic()){host.note(move==null?"Гексон: безопасное размещение не найдено":"Гексон: размещение найдено · только наблюдение");repeatHexa(epoch,700);return;}
   hexa.sent(observation.board(),move,SystemClock.elapsedRealtime());
   boolean sent=host.dispatch(move.fromX,move.fromY,move.toX,move.toY,ticket,at,move.durationMs,()->{hexa.acknowledge(SystemClock.elapsedRealtime());repeatHexa(epoch,450);});
   if(!sent&&hexaRunning){hexa.cancelUnsent();repeatHexa(epoch,400);}
  });
 }
 private void repeatHexa(long epoch,int delay){host.repeat(()->stepHexa(epoch),delay);}
 GridPuzzleVision.Observation grid(Bitmap image,Rect roi,int rows,int cols,Rect panel,long at,GridPuzzleVision.Palette palette,boolean skulls,boolean matchWorld,boolean pending){
  if(!adaptiveEnabled()||palette==null||palette.size()<2)return GridPuzzleVision.detect(image::getPixel,roi.left,roi.top,roi.right,roi.bottom,rows,cols,panel.left,panel.top,panel.right,panel.bottom,at,palette,skulls,matchWorld);
  return adaptive.read(image::getPixel,roi.left,roi.top,roi.right,roi.bottom,rows,cols,panel.left,panel.top,panel.right,panel.bottom,at,SystemClock.elapsedRealtime(),host.epoch(),palette,skulls,matchWorld,pending).snapshot();
 }
 private void configureRestart(){
  host.menu("Авто-перезапуск раунда",new String[]{"Выключить","До 3 раундов · выбрать карточку результата","До 10 раундов · выбрать карточку результата"},i->{
   if(i==0){prefs().edit().putBoolean(key("restart"),false).apply();restart.stop();host.note("Авто-перезапуск выключен");return;}
   host.stop("Настройка перезапуска · STOP");if(!host.bind())return;
   host.note("Откройте результат и обведите только карточку с кнопкой Играть снова");
   host.pick("Карточка результата с Играть снова",host.bounds(),box->{prefs().edit().putBoolean(key("restart"),true).putInt(key("restart-limit"),i==1?3:10).putString(key("restart-modal"),box.flattenToString()).putString(key("restart-window"),host.bounds().flattenToString()).apply();restartModal=new Rect(box);host.note("Перезапуск разрешён после подтверждённой игры, двух кадров результата и точной кнопки");});
  });
 }
 void bindRestart(){
  restart.configure(prefs().getBoolean(key("restart"),false),prefs().getInt(key("restart-limit"),3));
  restartEpoch=host.epoch();restartGeometry=host.generation();previousVerified=0;
  restart.bind(restartEpoch,host.window(),restartGeometry,SystemClock.elapsedRealtime());
  restartModal=null;String saved=prefs().getString(key("restart-modal"),null);
  if(saved!=null&&host.bounds().flattenToString().equals(prefs().getString(key("restart-window"),""))){Rect r=Rect.unflattenFromString(saved);if(r!=null&&host.bounds().contains(r)){restartModal=r;restart.calibrateResultArea(r.left,r.top,r.right,r.bottom);}}
 }
 Rect restartBounds(){return restart.enabled()&&restart.calibrated()&&restartModal!=null?new Rect(restartModal):null;}
 boolean gameplay(boolean usable,long signature,boolean verified,long at){
  if(restartEpoch<0)bindRestart();
  if(!restart.enabled())return true;
  if(restartEpoch!=host.epoch()||restartGeometry!=host.generation())return false;
  RoundRestartController.State state=restart.observeGameplay(host.epoch(),host.window(),host.generation(),++observedFrame,at,SystemClock.elapsedRealtime(),usable,signature,verified);
  return state!=RoundRestartController.State.WAIT_ACK&&state!=RoundRestartController.State.WAIT_GAME_RETURN&&state!=RoundRestartController.State.PAUSED;
 }
 boolean gameplayCounter(boolean usable,long signature,int verified,long at){boolean changed=verified>previousVerified;previousVerified=verified;return gameplay(usable,signature,changed,at);}
 void result(GameOverScreen.Pixels pixels,ActionGate.Ticket ticket,long at){
  if(restartEpoch<0)bindRestart();
  if(!restart.enabled()||restartModal==null||restartEpoch!=host.epoch()||restartGeometry!=host.generation())return;
  replayButton.setEmpty();unsafeUi=false;scanBudget=400;AccessibilityNodeInfo root=host.root();
  try{scan(root,0);}finally{if(root!=null)root.recycle();}
  Rect panel=host.panel();boolean pixelProof=!replayButton.isEmpty()&&GameOverScreen.supportsReplayButton(pixels,restartModal.left,restartModal.top,restartModal.right,restartModal.bottom,replayButton.left,replayButton.top,replayButton.right,replayButton.bottom,panel.left,panel.top,panel.right,panel.bottom);
  RoundRestartController.State state=restart.observeResult(host.epoch(),host.window(),host.generation(),++observedFrame,at,SystemClock.elapsedRealtime(),pixelProof,!replayButton.isEmpty(),unsafeUi,replayButton.left,replayButton.top,replayButton.right,replayButton.bottom);
  if(state!=RoundRestartController.State.READY||!host.automatic())return;
  long token=restart.prepareDispatch(host.epoch(),host.window(),host.generation(),SystemClock.elapsedRealtime());if(token==0)return;
  boolean sent=host.dispatch(restart.buttonCenterX(),restart.buttonCenterY(),restart.buttonCenterX(),restart.buttonCenterY(),ticket,at,50,()->restart.acknowledge(token,SystemClock.elapsedRealtime()));
  if(!sent)restart.dispatchRejected(token);
 }
 private void scan(AccessibilityNodeInfo node,int depth){
  if(node==null||depth>24||scanBudget--<=0)return;
  if(node.isVisibleToUser()){
   String text=node.getText()==null?"":node.getText().toString();String description=node.getContentDescription()==null?"":node.getContentDescription().toString();
   if(RoundRestartController.dangerousLabel(text)||RoundRestartController.dangerousLabel(description))unsafeUi=true;
   if(node.isClickable()&&node.isEnabled()&&(RoundRestartController.replayLabel(text)||RoundRestartController.replayLabel(description))){Rect box=new Rect();node.getBoundsInScreen(box);if(restartModal.contains(box)){if(!replayButton.isEmpty())unsafeUi=true;else replayButton.set(box);}}
  }
  for(int i=0;i<node.getChildCount()&&scanBudget>0;i++){AccessibilityNodeInfo child=node.getChild(i);try{scan(child,depth+1);}finally{if(child!=null)child.recycle();}}
 }
 String report(){return hexa.report()+"\n"+restart.report();}
}
