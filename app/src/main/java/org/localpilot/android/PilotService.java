package org.localpilot.android;
import android.accessibilityservice.*;import android.app.AlertDialog;import android.app.KeyguardManager;import android.content.*;import android.graphics.*;import android.os.*;import android.view.*;import android.view.accessibility.*;import android.widget.*;
public class PilotService extends AccessibilityService {
 public static PilotService instance;
 private ExperienceStore experience;private boolean collecting;
 public void enableExamples(){stop("Сбор примеров включён; привяжите игру заново");if(experience==null){note="Хранилище недоступно; сбор выключен";update();return;}collecting=true;update();}
 public void disableExamples(){collecting=false;stop("Сбор примеров выключен");}
 public byte[] exportExamples()throws java.io.IOException{stop("Экспорт примеров: STOP");if(experience==null)throw new java.io.IOException("Хранилище недоступно");return experience.snapshot();}
 public int restoreExamples(byte[] data)throws java.io.IOException{collecting=false;stop("Восстановление: STOP");if(experience==null)throw new java.io.IOException("Хранилище недоступно; исходник сохранён");return experience.restoreEmpty(data);}
 private boolean saveExample(String profile,int[] before,int action,int[] after){if(!collecting)return true;try{if(experience==null)throw new java.io.IOException();experience.append(profile,before,action,after);return true;}catch(java.io.IOException|RuntimeException e){collecting=false;stop("Пример не записан: сбор и ввод остановлены. Экспортируйте данные");return false;}}
 private final StageMetrics metrics=new StageMetrics();
 public String exportMetrics(){stop("Экспорт замеров: STOP");return metrics.report();}
 private final Handler handler=new Handler(Looper.getMainLooper());private final Handler gestureHandler=new Handler(Looper.getMainLooper());private final ActionGate gate=new ActionGate();
 private WindowManager wm;private LinearLayout panel;private TextView state;private WindowManager.LayoutParams layout;
 private boolean busy=false;private int autoLeft=0;private long deadline;private String target="";private Rect armedBounds=new Rect();private int nx=50,ny=65;
 private String note="STOP",uiStats="";private final InputPermit permit=new InputPermit();private SafeJournal journal;private long gestureSequence;
 private boolean playingMini;private int miniKind,miniMoves,miniAction;private int[] miniBefore;private long miniDeadline;private Rect boardRoi;private View picker;private boolean playing2048=false,captureBusy=false;private int moves2048;private long budget2048;private long captureCounter,pendingCapture,lastShotTimestamp=-1;private int mergeTotal;private Board2048 before2048;private int direction2048;private AlertDialog preview;

 @Override protected void onServiceConnected(){instance=this;try{experience=new ExperienceStore(new java.io.File(getFilesDir(),"experience-v1.log"));}catch(java.io.IOException e){experience=null;}try{journal=new SafeJournal(new java.io.File(getFilesDir(),"audit-v1.log"));}catch(java.io.IOException e){journal=null;}wm=(WindowManager)getSystemService(WINDOW_SERVICE);gate.stop();showPanel();}
 public void showPanel(){if(panel!=null)return;gate.stop();panel=new LinearLayout(this);panel.setOrientation(1);panel.setPadding(8,4,8,4);panel.setBackgroundColor(0xEE202830);
  TextView header=new TextView(this);header.setText("LocalPilot · тянуть здесь");header.setTextColor(Color.WHITE);panel.addView(header);
  header.setOnTouchListener(new View.OnTouchListener(){float x,y;int ox,oy;public boolean onTouch(View v,android.view.MotionEvent e){if(e.getAction()==MotionEvent.ACTION_DOWN){x=e.getRawX();y=e.getRawY();ox=layout.x;oy=layout.y;return true;}if(e.getAction()==MotionEvent.ACTION_MOVE){layout.x=Math.max(0,ox+(int)(e.getRawX()-x));layout.y=Math.max(0,oy+(int)(e.getRawY()-y));wm.updateViewLayout(panel,layout);return true;}return true;}});
  state=new TextView(this);state.setTextColor(Color.WHITE);state.setTextSize(12);panel.addView(state);
  row("Привязать",this::arm,"STOP",()->stop("STOP"));
  row("Разрешить ввод",this::allowManual,"Только чтение",()->stop("Ввод отозван"));
  row("X −10",()->{nx=Math.max(0,nx-10);update();},"X +10",()->{nx=Math.min(100,nx+10);update();});
  row("Y −10",()->{ny=Math.max(0,ny-10);update();},"Y +10",()->{ny=Math.min(100,ny+10);update();});
  row("ТАП",()->manual(0,0),"↑",()->manual(0,-1));row("←",()->manual(-1,0),"→",()->manual(1,0));
  row("↓",()->manual(0,1),"Демо ×10",this::startDemo);
  row("Выбрать поле",this::selectBoard,"Авто2048",this::start2048);
  row("Крестики авто",()->startMini(0),"Свет авто",()->startMini(1));
  row("Скрыть",this::hidePanel,"Отключить",()->{stop("Отключение");disableSelf();});
  layout=new WindowManager.LayoutParams(dp(240),WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,PixelFormat.TRANSLUCENT);layout.gravity=Gravity.TOP|Gravity.LEFT;layout.x=0;layout.y=80;
  try{wm.addView(panel,layout);update();}catch(RuntimeException e){panel=null;gate.stop();}
 }
 private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density);}
 private void row(String a,Runnable ar,String b,Runnable br){LinearLayout line=new LinearLayout(this);for(int i=0;i<2;i++){Button v=new Button(this);v.setText(i==0?a:b);v.setTextSize(12);Runnable r=i==0?ar:br;v.setOnClickListener(x->r.run());line.addView(v,new LinearLayout.LayoutParams(0,dp(40),1));}panel.addView(line);}
 public void hidePanel(){stop("STOP");if(panel!=null){wm.removeView(panel);panel=null;state=null;}}
 private void update(){if(state!=null)state.setText(note+"\n"+(target.isEmpty()?"Нет цели":target)+"\nX="+nx+"% Y="+ny+"% · "+(busy?"жест отправлен":"ожидание")+"\n"+uiStats+(collecting?"\nСбор примеров включён":""));}
 private AccessibilityNodeInfo appRoot(){for(AccessibilityWindowInfo w:getWindows())if(w.getType()==AccessibilityWindowInfo.TYPE_APPLICATION&&w.isActive()&&w.isFocused()){AccessibilityNodeInfo r=w.getRoot();if(r!=null)return r;}return null;}
 private boolean unlocked(){return ((PowerManager)getSystemService(POWER_SERVICE)).isInteractive()&&!((KeyguardManager)getSystemService(KEYGUARD_SERVICE)).isKeyguardLocked();}
 private void arm(){stop("Привязка");if(busy){note="Дождитесь завершения жеста";update();return;}AccessibilityNodeInfo r=appRoot();if(r==null||!unlocked()){note="Нет активного приложения";update();return;}target=String.valueOf(r.getPackageName());r.getBoundsInScreen(armedBounds);gate.arm(target,r.getWindowId(),armedBounds.width(),armedBounds.height());note="Только наблюдение; ввод требует разрешения";update();}
 private void stop(String reason){permit.revoke();gate.stop();audit(SafeJournal.Event.STOP,0,0);autoLeft=0;playing2048=false;playingMini=false;boardRoi=null;if(preview!=null){preview.dismiss();preview=null;}if(picker!=null){wm.removeView(picker);picker=null;}compact(false);handler.removeCallbacksAndMessages(null);note=reason;update();}
 private boolean valid(ActionGate.Ticket t){AccessibilityNodeInfo r=appRoot();if(r==null||!unlocked())return false;Rect b=new Rect();r.getBoundsInScreen(b);return b.equals(armedBounds)&&gate.valid(t,SystemClock.elapsedRealtime(),String.valueOf(r.getPackageName()),r.getWindowId(),b.width(),b.height());}
 private void manual(int dx,int dy){if(autoLeft>0||playing2048||playingMini){stop("Авторежим остановлен; нажмите ещё раз");return;}float x=armedBounds.left+Math.min(.99f,nx/100f)*armedBounds.width(),y=armedBounds.top+Math.min(.99f,ny/100f)*armedBounds.height();send(x,y,x+dx*armedBounds.width()*.18f,y+dy*armedBounds.height()*.18f,gate.offer(SystemClock.elapsedRealtime()),null);}
 private boolean audit(SafeJournal.Event event,long request,long metric){return auditAt(event,request,metric,gate.epoch());}
 private boolean auditAt(SafeJournal.Event event,long request,long metric,long scope){try{if(journal==null)return false;journal.append(event,scope,request,SystemClock.elapsedRealtime(),metric);return true;}catch(java.io.IOException|RuntimeException e){journal=null;permit.revoke();note="Журнал недоступен: ввод заблокирован";return false;}}
 public byte[] exportAudit()throws java.io.IOException{stop("Экспорт: ввод остановлен");if(journal!=null)return journal.snapshot();java.io.File raw=new java.io.File(getFilesDir(),"audit-v1.log");if(raw.length()>SafeJournal.MAX)throw new java.io.IOException("Слишком большой журнал");try(java.io.InputStream in=new java.io.FileInputStream(raw);java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()){byte[] buffer=new byte[4096];int n;while((n=in.read(buffer))!=-1){if(out.size()+n>SafeJournal.MAX)throw new java.io.IOException("Лимит экспорта");out.write(buffer,0,n);}return out.toByteArray();}}
 private void allowManual(){if(!gate.armed()||busy)return;long epoch=gate.epoch();AlertDialog d=new AlertDialog.Builder(this).setTitle("Разрешить касания?").setMessage("Только выбранное окно. До 40 жестов за 2 минуты. Stop отзывает разрешение. Сейчас программа ничего не нажимает.").setPositiveButton("Разрешить",(a,b)->{if(epoch==gate.epoch()){permit.grant(epoch,SystemClock.elapsedRealtime(),40,120000);note="Ввод разрешён на 2 минуты";update();}}).setNegativeButton("Отмена",null).create();d.getWindow().setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);d.getWindow().addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);d.show();}
 private void send(float x,float y,float ex,float ey,ActionGate.Ticket ticket,Runnable done){
  if(busy){note="Исполнитель занят";update();return;}if(!valid(ticket)){stop("Нет свежего разрешения — привяжите окно");return;}
  if(!armedBounds.contains((int)x,(int)y)||!armedBounds.contains((int)ex,(int)ey)){stop("Жест вне окна");return;}
  if(panel==null||!panel.isShown()){stop("Нет панели Stop");return;}int[] panelXY=new int[2];panel.getLocationOnScreen(panelXY);Rect panelRect=new Rect(panelXY[0],panelXY[1],panelXY[0]+panel.getWidth(),panelXY[1]+panel.getHeight());
  Rect pathRect=new Rect((int)Math.min(x,ex)-2,(int)Math.min(y,ey)-2,(int)Math.max(x,ex)+3,(int)Math.max(y,ey)+3);
  if(Rect.intersects(panelRect,pathRect)){stop("Панель перекрывает жест: передвиньте её");return;}
  if(!permit.allows(gate.epoch(),SystemClock.elapsedRealtime())){stop("Сначала явно разрешите ввод");return;}
  if(journal==null){stop("Журнал недоступен — ввода нет");return;}final long gestureId=journal.nextRequest();if(!audit(SafeJournal.Event.PROPOSED,gestureId,0)){stop("Ошибка журнала — ввода нет");return;}
  if(!valid(ticket)||!gate.consume(ticket,SystemClock.elapsedRealtime(),target,ticket.window,armedBounds.width(),armedBounds.height())||!permit.consume(gate.epoch(),SystemClock.elapsedRealtime())){stop("Разрешение истекло/уже использовано");return;}
  Path path=new Path();path.moveTo(x,y);if(x!=ex||y!=ey)path.lineTo(ex,ey);
  GestureDescription gesture=new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(path,0,x==ex&&y==ey?50:120)).build();
  final long dispatchedNs=SystemClock.elapsedRealtimeNanos();long epoch=gate.epoch();busy=true;note="Отправка";update();
  try{boolean accepted=dispatchGesture(gesture,new GestureResultCallback(){
   @Override public void onCompleted(GestureDescription g){metrics.record(StageMetrics.Stage.ANDROID_ACK,SystemClock.elapsedRealtimeNanos()-dispatchedNs);busy=false;if(!auditAt(SafeJournal.Event.ANDROID_COMPLETED,gestureId,0,epoch)){stop("Ошибка записи ACK");return;}if(epoch!=gate.epoch()){update();return;}if(!valid(ticket)){stop("Окно изменилось после жеста");return;}note="Android завершил жест; игровой исход не проверен";update();if(done!=null)done.run();}
   @Override public void onCancelled(GestureDescription g){busy=false;auditAt(SafeJournal.Event.ANDROID_CANCELLED,gestureId,0,epoch);if(epoch==gate.epoch())stop("Android отменил жест");else update();}
  },gestureHandler);if(!accepted){busy=false;audit(SafeJournal.Event.REJECTED,gestureId,0);stop("Android отклонил жест");}}catch(RuntimeException e){busy=false;stop("Ошибка отправки жеста");}
 }
 private void startDemo(){if(busy||autoLeft>0||playing2048||playingMini||captureBusy)return;if(!permit.allows(gate.epoch(),SystemClock.elapsedRealtime())){note="Для демо сначала разрешите ввод";update();return;}if(!gate.armed()||!getPackageName().equals(target)){note="Сначала привяжите нашу тестовую игру";update();return;}autoLeft=10;deadline=SystemClock.elapsedRealtime()+10000;demoStep(gate.epoch());}
 private AccessibilityNodeInfo findTarget(AccessibilityNodeInfo n){if(n==null)return null;if("LOCALPILOT_DEMO_TARGET".equals(String.valueOf(n.getContentDescription()))&&n.isVisibleToUser()&&n.isClickable())return n;for(int i=0;i<n.getChildCount();i++){AccessibilityNodeInfo f=findTarget(n.getChild(i));if(f!=null)return f;}return null;}
 private void demoStep(long epoch){if(epoch!=gate.epoch()||autoLeft<=0)return;if(SystemClock.elapsedRealtime()>deadline){stop("Демо: лимит 10 секунд");return;}AccessibilityNodeInfo root=appRoot();if(root==null||!getPackageName().equals(String.valueOf(root.getPackageName()))){stop("Демо: другая программа");return;}AccessibilityNodeInfo n=findTarget(root);if(n==null){stop("Демо: нет подтверждённой цели");return;}Rect b=new Rect();n.getBoundsInScreen(b);autoLeft--;send(b.centerX(),b.centerY(),b.centerX(),b.centerY(),gate.offer(SystemClock.elapsedRealtime()),()->{if(autoLeft==0)stop("Демо завершено: проверьте счёт");else handler.postDelayed(()->demoStep(epoch),300);});}

 private void compact(boolean on){if(panel==null)return;for(int i=3;i<panel.getChildCount();i++)panel.getChildAt(i).setVisibility(on?View.GONE:View.VISIBLE);}
 private Rect panelBounds(){if(panel==null)return new Rect();int[] p=new int[2];panel.getLocationOnScreen(p);return new Rect(p[0],p[1],p[0]+panel.getWidth(),p[1]+panel.getHeight());}
 private void selectBoard(){
  if(busy||captureBusy||playing2048||playingMini||autoLeft>0||!gate.armed()){note="Сначала привяжите неподвижное окно";update();return;}
  long epoch=gate.epoch();compact(true);final float[] start={0,0};final RectF box=new RectF();
  picker=new View(this){Paint p=new Paint(3);@Override protected void onDraw(Canvas c){c.drawColor(0x55102030);p.setColor(Color.WHITE);p.setTextSize(dp(15));c.drawText("Обведите квадрат игрового поля. Вверху — отмена",dp(8),dp(35),p);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(4);c.drawRect(box,p);p.setStyle(Paint.Style.FILL);}
   @Override public boolean onTouchEvent(MotionEvent e){if(e.getAction()==MotionEvent.ACTION_DOWN){start[0]=e.getRawX();start[1]=e.getRawY();return true;}if(e.getAction()==MotionEvent.ACTION_MOVE||e.getAction()==MotionEvent.ACTION_UP){box.set(Math.min(start[0],e.getRawX()),Math.min(start[1],e.getRawY()),Math.max(start[0],e.getRawX()),Math.max(start[1],e.getRawY()));invalidate();if(e.getAction()==MotionEvent.ACTION_UP){Rect selected=new Rect();box.round(selected);if(picker!=null){wm.removeView(picker);picker=null;}compact(false);if(epoch!=gate.epoch())return true;if(start[1]<dp(65)||!armedBounds.contains(selected)||selected.width()<160||Math.abs(selected.width()-selected.height())>selected.width()*.08){note="Выделение отменено/поле не квадратное";update();return true;}boardRoi=selected;note="Поле выбрано. Выберите авторежим игры";update();}return true;}return true;}};
  WindowManager.LayoutParams lp=new WindowManager.LayoutParams(-1,-1,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT);lp.gravity=Gravity.TOP|Gravity.LEFT;wm.addView(picker,lp);
  handler.postDelayed(()->{if(picker!=null&&epoch==gate.epoch())stop("Выделение: таймаут");},30000);
 }
 private interface BoardResult {void ready(Board2048 board,ActionGate.Ticket ticket,long captureMs);}
 private interface ImageResult {void ready(Bitmap bitmap,ActionGate.Ticket ticket,long captureMs);}
 private void screenshot(long epoch,BoardResult result){captureImage(epoch,(pixels,t,ms)->{long readNs=SystemClock.elapsedRealtimeNanos();Rect r=new Rect(boardRoi);Board2048 board=PixelBoardReader.read(pixels::getPixel,pixels.getWidth(),pixels.getHeight(),r.left,r.top,r.right,r.bottom);metrics.record(StageMetrics.Stage.READ,SystemClock.elapsedRealtimeNanos()-readNs);result.ready(board,t,ms);});}
 private void captureImage(long epoch,ImageResult result){
  if(epoch!=gate.epoch()||(!playing2048&&!playingMini))return;
  ActionGate.Ticket ticket=gate.offer(SystemClock.elapsedRealtime());if(!valid(ticket)||boardRoi==null){stop("Нет привязки/поля");return;}
  if(captureBusy){stop("Предыдущий захват ещё выполняется");return;}final Rect roi=new Rect(boardRoi);
  if(Rect.intersects(panelBounds(),roi)){stop("Панель закрывает поле: передвиньте и привяжите заново");return;}
  final long captureNs=SystemClock.elapsedRealtimeNanos();captureBusy=true;long request=SystemClock.elapsedRealtime();final long requestedUptime=SystemClock.uptimeMillis();final long requestId=++captureCounter;pendingCapture=requestId;
  TakeScreenshotCallback callback=new TakeScreenshotCallback(){
   @Override public void onSuccess(ScreenshotResult shot){if(pendingCapture==requestId)captureBusy=false;android.hardware.HardwareBuffer buffer=shot.getHardwareBuffer();Bitmap hardware=null,bitmap=null;
    try{if(epoch!=gate.epoch()||(!playing2048&&!playingMini))return;if(!valid(ticket)||boardRoi==null||!roi.equals(boardRoi)||!CaptureFreshness.valid(shot.getTimestamp(),requestedUptime,SystemClock.uptimeMillis(),lastShotTimestamp)){stop("Снимок устарел/повторён");return;}lastShotTimestamp=shot.getTimestamp();
     hardware=Bitmap.wrapHardwareBuffer(buffer,shot.getColorSpace());if(hardware==null)throw new IllegalArgumentException("Нет пикселей");bitmap=hardware.copy(Bitmap.Config.ARGB_8888,false);
     Point size=new Point();wm.getDefaultDisplay().getRealSize(size);if(bitmap.getWidth()!=size.x||bitmap.getHeight()!=size.y)throw new IllegalArgumentException("Масштаб снимка не подтверждён");
     final Bitmap pixels=bitmap;
     if(!valid(ticket)){stop("Обработка кадра превысила TTL");return;}metrics.record(StageMetrics.Stage.CAPTURE,SystemClock.elapsedRealtimeNanos()-captureNs);result.ready(pixels,ticket,SystemClock.elapsedRealtime()-request);
    }catch(RuntimeException error){if(epoch==gate.epoch())stop("2048: "+error.getMessage());}finally{if(bitmap!=null)bitmap.recycle();if(hardware!=null)hardware.recycle();buffer.close();}
   }
   @Override public void onFailure(int error){if(pendingCapture==requestId)captureBusy=false;if(epoch==gate.epoch())stop("Захват Android: ошибка "+error+". Проверьте доступ службы");}
  };
  try{takeScreenshot(Display.DEFAULT_DISPLAY,getMainExecutor(),callback);}catch(RuntimeException e){captureBusy=false;stop("Захват недоступен");}
  handler.postDelayed(()->{if(captureBusy&&pendingCapture==requestId&&epoch==gate.epoch())stop("Захват: таймаут без ввода");},4000);
 }
 private long captureDelay(){return Build.VERSION.SDK_INT>=31?400:1100;}
 private void stable(long epoch,BoardResult result){screenshot(epoch,(a,t,ms)->handler.postDelayed(()->screenshot(epoch,(b,fresh,captureMs)->{if(!a.same(b)){stop("Поле меняется: дождитесь остановки анимации");return;}result.ready(b,fresh,captureMs);}),captureDelay()));}
 private void start2048(){
  if(busy||captureBusy||autoLeft>0||playing2048||playingMini||!gate.armed()||boardRoi==null){note="Привязать → Поле 4×4 → Авто2048";update();return;}
  long epoch=gate.epoch();playing2048=true;compact(true);note="Читаю поле в памяти…";update();
  handler.postDelayed(()->stable(epoch,(board,t,ms)->{
   StringBuilder text=new StringBuilder("Сверьте все 16 клеток с игрой:\n");for(int i=0;i<16;i++){text.append(board.cells[i]==0?"·":board.cells[i]).append(i%4==3?"\n":"  ");}
   text.append("До 20 ходов / 60 секунд. Кадры только в RAM. Остановится при неизвестном результате. Это CPU-алгоритм, не обучение.");
   preview=new AlertDialog.Builder(this).setTitle("2048 — разрешить авторежим?").setMessage(text.toString()).setPositiveButton("Поле верное, начать",(dialog,which)->{preview=null;if(epoch!=gate.epoch()||!playing2048)return;permit.grant(epoch,SystemClock.elapsedRealtime(),20,60000);moves2048=0;mergeTotal=0;before2048=null;budget2048=SystemClock.elapsedRealtime()+60000;handler.postDelayed(()->round2048(epoch),captureDelay());}).setNegativeButton("Отмена",(d,w)->stop("2048 отменён")).setCancelable(false).create();preview.getWindow().setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);preview.getWindow().addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);preview.show();
  }),captureDelay());
 }
 private void round2048(long epoch){
  if(epoch!=gate.epoch()||!playing2048)return;if(SystemClock.elapsedRealtime()>budget2048){stop("2048: лимит 60 секунд");return;}
  stable(epoch,(board,ticket,captureMs)->{
   if(epoch!=gate.epoch()||!playing2048)return;
   if(before2048!=null&&!before2048.verifies(board,direction2048)){stop("2048: результат свайпа не подтверждён");return;}
   if(before2048!=null){if(!saveExample("2048",before2048.cells,direction2048,board.cells))return;mergeTotal+=Verified2048.mergeReward(before2048,direction2048,board);if(!audit(SafeJournal.Event.VERIFIED_2048,0,mergeTotal)){stop("Ошибка журнала результатов");return;}}
   if(board.max()>=2048){stop("2048: распознана плитка 2048; сверить визуально");return;}if(moves2048>=20){stop("2048: 20 ходов проверено, максимум "+board.max());return;}
   long policyNs=SystemClock.elapsedRealtimeNanos();long started=SystemClock.elapsedRealtime();int d=board.choose();metrics.record(StageMetrics.Stage.POLICY,SystemClock.elapsedRealtimeNanos()-policyNs);long think=SystemClock.elapsedRealtime()-started;if(d<0){stop("2048: допустимых ходов нет");return;}if(!Verified2048.mask(board)[d]){stop("2048: ход вне маски");return;}if(!valid(ticket)||SystemClock.elapsedRealtime()>budget2048){stop("План устарел");return;}
   Rect r=new Rect(boardRoi);float x=r.exactCenterX(),y=r.exactCenterY(),dx=0,dy=0;switch(d){case Board2048.LEFT:dx=-r.width()*.30f;break;case Board2048.RIGHT:dx=r.width()*.30f;break;case Board2048.UP:dy=-r.height()*.30f;break;case Board2048.DOWN:dy=r.height()*.30f;break;}
   before2048=board;direction2048=d;moves2048++;uiStats="кадр "+captureMs+" мс · CPU "+think+" мс · проверено "+(moves2048-1);note="Ход "+moves2048+" · max "+board.max()+" · кадр "+captureMs+"мс · CPU "+think+"мс";update();
   send(x-dx/2,y-dy/2,x+dx/2,y+dy/2,ticket,()->{note="Проверяю исход хода "+moves2048;update();handler.postDelayed(()->round2048(epoch),captureDelay());});
  });
 }

 private boolean miniMarker(AccessibilityNodeInfo root,String marker,int depth){if(root==null||depth>30)return false;if(marker.equals(String.valueOf(root.getContentDescription())))return true;for(int i=0;i<Math.min(100,root.getChildCount());i++)if(miniMarker(root.getChild(i),marker,depth+1))return true;return false;}
 private interface MiniResult {void ready(int[] board,ActionGate.Ticket ticket);}
 private void miniCapture(long epoch,MiniResult result){captureImage(epoch,(pixels,t,ms)->{long readNs=SystemClock.elapsedRealtimeNanos();Rect r=new Rect(boardRoi);int[] b=MiniReader.read(miniKind,pixels::getPixel,pixels.getWidth(),pixels.getHeight(),r.left,r.top,r.right,r.bottom);metrics.record(StageMetrics.Stage.READ,SystemClock.elapsedRealtimeNanos()-readNs);result.ready(b,t);});}
 private void miniStable(long epoch,MiniResult result){miniCapture(epoch,(a,t)->handler.postDelayed(()->miniCapture(epoch,(b,fresh)->{if(!java.util.Arrays.equals(a,b)){stop("Поле мини-игры меняется");return;}result.ready(b,fresh);}),captureDelay()));}
 private void startMini(int kind){
  if(busy||captureBusy||autoLeft>0||playing2048||playingMini||!gate.armed()||boardRoi==null){note="Привязать → выбрать поле → авторежим";update();return;}
  if(!getPackageName().equals(target)||!miniMarker(appRoot(),"LOCALPILOT_MINI_"+kind,0)){stop("Профиль поддерживает только выбранную встроенную мини-игру");return;}
  miniKind=kind;playingMini=true;compact(true);long epoch=gate.epoch();handler.postDelayed(()->miniStable(epoch,(board,ticket)->{
   StringBuilder message=new StringBuilder("Сверьте клетки: \n");int n=MiniPolicy.size(kind);for(int i=0;i<board.length;i++)message.append(board[i]).append(i%n==n-1?"\n":" ");message.append(kind==0?"0 пусто, 1 X, 2 O. До 9 касаний.":"0 выключено, 1 включено. До 25 касаний.");
   preview=new AlertDialog.Builder(this).setTitle(kind==0?"Крестики-нолики":"Погаси свет").setMessage(message).setPositiveButton("Верно, разрешить",(d,w)->{preview=null;if(epoch!=gate.epoch())return;miniBefore=null;miniMoves=0;miniDeadline=SystemClock.elapsedRealtime()+60000;permit.grant(epoch,SystemClock.elapsedRealtime(),kind==0?9:25,60000);handler.postDelayed(()->miniRound(epoch),captureDelay());}).setNegativeButton("Отмена",(d,w)->stop("Мини-игра отменена")).setCancelable(false).create();preview.getWindow().setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);preview.getWindow().addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);preview.show();
  }),captureDelay());
 }
 private void miniRound(long epoch){if(epoch!=gate.epoch()||!playingMini)return;if(SystemClock.elapsedRealtime()>miniDeadline){stop("Мини-игра: лимит времени");return;}miniStable(epoch,(board,ticket)->{
  if(miniBefore!=null&&!MiniPolicy.verifies(miniKind,miniBefore,miniAction,board)){stop("Мини-игра: исход касания не подтверждён");return;}
  if(miniBefore!=null&&!saveExample(miniKind==0?"ttt":"lights",miniBefore,miniAction,board))return;
  long policyNs=SystemClock.elapsedRealtimeNanos();int action=MiniPolicy.choose(miniKind,board);metrics.record(StageMetrics.Stage.POLICY,SystemClock.elapsedRealtimeNanos()-policyNs);if(action==-1){stop("Мини-игра: терминальное поле распознано — сверить результат");return;}if(action<0){stop("Мини-игра: состояние/решение UNKNOWN");return;}
  if(miniMoves>=(miniKind==0?9:25)||SystemClock.elapsedRealtime()>miniDeadline){stop("Мини-игра: бюджет исчерпан");return;}if(!miniMarker(appRoot(),"LOCALPILOT_MINI_"+miniKind,0)){stop("Мини-игра изменилась");return;}
  int n=MiniPolicy.size(miniKind);Rect r=new Rect(boardRoi);float x=r.left+(action%n+.5f)*r.width()/n,y=r.top+(action/n+.5f)*r.height()/n;miniBefore=board.clone();miniAction=action;miniMoves++;uiStats="Проверено касаний: "+(miniMoves-1);send(x,y,x,y,ticket,()->handler.postDelayed(()->miniRound(epoch),captureDelay()));
 });}
 @Override public void onAccessibilityEvent(AccessibilityEvent e){if(!gate.armed())return;ActionGate.Ticket t=gate.offer(SystemClock.elapsedRealtime());if(!valid(t))stop("Смена окна/блокировка — STOP");}
 @Override public void onInterrupt(){stop("Служба прервана");}
 @Override public void onConfigurationChanged(android.content.res.Configuration c){super.onConfigurationChanged(c);stop("Поворот/конфигурация — STOP");}
 @Override public void onDestroy(){hidePanel();instance=null;super.onDestroy();}
}
