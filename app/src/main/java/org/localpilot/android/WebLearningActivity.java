package org.localpilot.android;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.os.*;
import android.text.InputType;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import org.json.*;
import org.localpilot.android.web.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.*;

/** Web demonstrations stay inside this browser. No Accessibility or MediaProjection is used. */
public final class WebLearningActivity extends Activity {
 private final Handler main=new Handler(Looper.getMainLooper());
 private final ExecutorService io=Executors.newSingleThreadExecutor();
 private WebView web;private TextView status;private EditText address;private LinearLayout modeBox;
 private WebLessonStore lessonStore;private String pageGame="",pageOrigin="",selectedProfile="";
 private String importPage="",importProfile="";
 private WebLesson lesson,model;private boolean recording,playing,gesture,multi,synthetic,capturing,resumed,dead;
 private int generation,activeRows,waitRows;private long outcomeRevision;
 private boolean injecting;private long injectedDown;private float injectedX,injectedY;private byte[] exportSnapshot;
 private Runnable queuedSave;private long downAt,lastInput,lastWait,lastAuto;
 private float downX,downY;private float[] downFeatures;private String recordSession="";
 private Frame latest;private Pending pending;private WebLesson.Action suggestion;private float[] suggestedFrom;private long suggestionTime;
 private String endpoint="",modelName="",apiKey="";
 private String metricSelector="";private GoalReward.Source metricMode=GoalReward.Source.PROGRESS;private double metricScale=100,metricValue=Double.NaN,downMetric=Double.NaN;private long metricTime;private boolean metricBusy;private long metricRequestId;
 private static final int IMPORT=91,EXPORT=92;
 private static final class Frame {
  final Bitmap bitmap;final float[] features;final long time;final float aspect;
  Frame(Bitmap bitmap,float[] features,long time,float aspect){this.bitmap=bitmap;this.features=features;this.time=time;this.aspect=aspect;}
 }
 private static final class Pending {
  final float[] before;final WebLesson.Action action;final long up;final float aspect;double metric=Double.NaN;boolean interrupted,explicitOutcome;WebLesson.Sample finalized;GoalReward outcome=GoalReward.unknown();
  Pending(float[] before,WebLesson.Action action,long up,float aspect){this.before=before;this.action=action;this.up=up;this.aspect=aspect;}
 }
 private final Runnable poll=new Runnable(){public void run(){if(!resumed||dead)return;readMetric();capture();main.postDelayed(this,200);}};
 @Override public void onCreate(Bundle saved){
  super.onCreate(saved);lessonStore=new WebLessonStore(getFilesDir());if(PilotService.instance!=null)PilotService.instance.hidePanel();
  LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);setContentView(root);
  TextView heading=new TextView(this);heading.setText("Web · реальные демонстрации и подсказки");root.addView(heading);
  LinearLayout bar=new LinearLayout(this);address=new EditText(this);address.setSingleLine();address.setHint("https://адрес-игры");address.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);bar.addView(address,new LinearLayout.LayoutParams(0,-2,1));button(bar,"Открыть",this::navigate);button(bar,"STOP",()->stop("STOP: запись и управление выключены"));root.addView(bar);
  LinearLayout selectors=new LinearLayout(this);Spinner mode=new Spinner(this);mode.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Мои действия → примеры","Примеры: файл или HTTPS-ссылка","Интернет-модель → подсказки"}));selectors.addView(mode,new LinearLayout.LayoutParams(0,-2,2));button(selectors,"Игра ▾",this::chooseProfile);root.addView(selectors);
  modeBox=new LinearLayout(this);modeBox.setOrientation(LinearLayout.VERTICAL);root.addView(modeBox);
  status=new TextView(this);status.setTextSize(12);root.addView(status);
  web=new WebView(this);root.addView(web,new LinearLayout.LayoutParams(-1,0,1));
  WebSettings settings=web.getSettings();settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(true);settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);settings.setSupportMultipleWindows(false);settings.setJavaScriptCanOpenWindowsAutomatically(false);
  web.setWebChromeClient(new WebChromeClient());
  web.setWebViewClient(new WebViewClient(){
   @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){try{WebLesson.https(request.getUrl().toString());return false;}catch(IllegalArgumentException e){stop("Поддерживаются только HTTPS-страницы");return true;}}
   @Override public void onPageStarted(WebView view,String url,Bitmap icon){stop("Страница загружается");lesson=null;model=null;selectedProfile="";pageGame=pageOrigin="";clearFrame();}
   @Override public void onPageFinished(WebView view,String url){loadPage(url);}
   @Override public void onReceivedError(WebView view,WebResourceRequest request,WebResourceError error){if(request.isForMainFrame())stop("Страница не открылась: проверьте адрес и доступность сайта");}
  });
  web.setOnTouchListener((view,event)->{touch(event);return false;});
  mode.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onItemSelected(android.widget.AdapterView<?> parent,View view,int position,long id){stop("Выбран режим. Откройте HTTPS-игру");showMode(position);}public void onNothingSelected(android.widget.AdapterView<?> parent){}});
  showMode(0);
  android.content.SharedPreferences prefs=getSharedPreferences("web-learning",0);endpoint=prefs.getString("endpoint","https://openrouter.ai/api/v1/chat/completions");modelName=prefs.getString("model","");
  io.execute(()->{try{String key=WebVault.load(getApplicationContext());ui(()->apiKey=key);}catch(Exception ignored){}});
 }
 private Button button(LinearLayout row,String name,Runnable action){Button b=new Button(this);b.setText(name);b.setTextSize(11);b.setOnClickListener(v->action.run());row.addView(b,row.getOrientation()==LinearLayout.HORIZONTAL?new LinearLayout.LayoutParams(0,-2,1):new LinearLayout.LayoutParams(-1,-2));return b;}
 private void showMode(int mode){
  modeBox.removeAllViews();TextView help=new TextView(this);help.setTextSize(12);modeBox.addView(help);
  LinearLayout controls=new LinearLayout(this);modeBox.addView(controls);
  if(mode==0){
   help.setText("1. Откройте игру и выберите её через «Игра ▾».\n2. Нажмите «Запись» и играйте вручную.\n3. Оценивайте через «Оценка / цель»: удачно +1, ошибка −1 или автоматический показатель. Затем STOP → Обучить → По примерам. Модель экспериментальная.");
   button(controls,"Запись",this::startRecord);button(controls,"Обучить",this::train);button(controls,"По примерам",this::autoplay);button(controls,"Экспорт",this::exportData);
  }else if(mode==1){
   help.setText("1. Откройте игру, выберите профиль «Игра ▾».\n2. Выберите файл .lpweb или HTTPS-ссылку.\n3. «Обучить» → «По примерам». Видео и audit.zip не являются обучающими примерами.");
   button(controls,"Из файла",()->pickImport());button(controls,"По ссылке",this::importUrl);button(controls,"Обучить",this::train);button(controls,"По примерам",this::autoplay);
  }else{
   help.setText("1. «Настроить API»: HTTPS, ключ и vision-модель.\n2. «Подсказка» отправит снимок игровой области провайдеру.\n3. Проверьте ответ и нажмите «Применить». Быстрая аркада может измениться до ответа.");
   button(controls,"Настроить API",this::configure);button(controls,"Подсказка",this::teacher);button(controls,"Применить",this::applySuggestion);
  }
  if(mode<2){LinearLayout outcomes=new LinearLayout(this);modeBox.addView(outcomes);button(outcomes,"Оценка / цель ▾",this::outcomeMenu);button(outcomes,"В реальный 2048",this::transfer2048);}
 }
 private void say(String text){status.setText(text+"\nИгра: "+(selectedProfile.isEmpty()?"не выбрана":selectedProfile)+(lesson==null?"":" · примеры "+lesson.size()+"/"+WebLesson.MAX_ROWS+"\n"+lesson.outcomeSummary()));}
 private void stop(String reason){
  if(injecting&&web!=null){try{sendTouch(injectedDown,SystemClock.uptimeMillis(),MotionEvent.ACTION_CANCEL,injectedX,injectedY);}catch(RuntimeException ignored){}}
  if(queuedSave!=null){main.removeCallbacks(queuedSave);queuedSave=null;}
  if(recording&&lesson!=null)persist(lesson.copy());
  generation++;metricBusy=false;metricValue=Double.NaN;metricTime=0;recording=playing=gesture=multi=false;pending=null;suggestion=null;suggestedFrom=null;downFeatures=null;say(reason);}
 private void clearFrame(){if(latest!=null){latest.bitmap.recycle();latest=null;}}
 private boolean ready(){return resumed&&lesson!=null&&lesson.identified()&&latest!=null&&SystemClock.elapsedRealtime()-latest.time<=700&&web.hasWindowFocus()&&Math.abs(latest.aspect-web.getWidth()/(float)web.getHeight())<latest.aspect*.02f;}
 private void ui(Runnable task){main.post(()->{if(!dead)task.run();});}
 private void navigate(){try{String url=address.getText().toString().trim();WebLesson.https(url);stop("Открываю игру");((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(address.getWindowToken(),0);web.requestFocus();web.loadUrl(url);}catch(IllegalArgumentException e){say("Укажите HTTPS-адрес игры");}}
 private void loadPage(String url){
  try{
   String game=WebLesson.gameId(url);if(web.getUrl()==null||!WebLesson.gameId(web.getUrl()).equals(game))return;
   if(pageGame.equals(game)&&lesson!=null)return;
   pageGame=game;pageOrigin=WebLesson.origin(url);selectedProfile="";lesson=model=null;clearFrame();
   say("Страница готова. Выберите игру через «Игра ▾». Для другой мини-игры меняйте профиль");
  }catch(IllegalArgumentException e){stop("Откройте HTTPS-игру");}
 }
 private void chooseProfile(){
  stop("Выбор игры: запись и управление выключены");
  if(pageGame.isEmpty()){say("Сначала откройте HTTPS-страницу игры");return;}
  java.util.LinkedHashMap<String,String> names=new java.util.LinkedHashMap<>();
  for(String name:new String[]{"Прыгун Ари","Карканоид","Маркет Раш","Гошан, вперёд!","2048","Мэтч Ворлд","Гексон"})names.put(WebLesson.profileKey(name),name);
  for(String name:new java.util.HashSet<>(getSharedPreferences("web-learning",0).getStringSet("profiles-"+pageGame,java.util.Collections.emptySet())))try{names.put(WebLesson.profileKey(name),WebLesson.normalizeProfile(name));}catch(IllegalArgumentException ignored){}
  java.util.ArrayList<String> options=new java.util.ArrayList<>(names.values());int create=options.size(),legacy=create+1;options.add("Добавить свою игру…");options.add("Старый набор этой страницы…");
  new AlertDialog.Builder(this).setTitle("Для какой игры записывать и применять примеры?").setItems(options.toArray(new String[0]),(d,which)->{
   if(which==create){EditText input=new EditText(this);input.setHint("Название мини-игры");new AlertDialog.Builder(this).setTitle("Отдельный профиль игры").setView(input).setNegativeButton("Отмена",null).setPositiveButton("Выбрать",(dialog,w)->selectProfile(input.getText().toString())).show();}
   else if(which==legacy)openLegacy();else selectProfile(options.get(which));
  }).setNegativeButton("Отмена",null).show();
 }
 private boolean registerProfile(String name){
  java.util.Set<String> names=new java.util.HashSet<>(getSharedPreferences("web-learning",0).getStringSet("profiles-"+pageGame,java.util.Collections.emptySet()));
  String key=WebLesson.profileKey(name);boolean exists=false;for(String old:names)try{if(WebLesson.profileKey(old).equals(key)){exists=true;break;}}catch(IllegalArgumentException ignored){}
  if(!exists&&names.size()>=32){say("Достигнут лимит 32 профилей этой страницы");return false;}
  if(!exists)names.add(name);getSharedPreferences("web-learning",0).edit().putStringSet("profiles-"+pageGame,names).apply();return true;
 }
 private void selectProfile(String value){
  final String name;try{name=WebLesson.normalizeProfile(value);}catch(IllegalArgumentException e){say("Введите название игры: 1–64 символа без переносов строк");return;}
  if(pageGame.isEmpty()||!registerProfile(name))return;
  stop("Загружаю отдельный профиль «"+name+"»");selectedProfile=name;lesson=model=null;clearFrame();
  final String game=pageGame,origin=pageOrigin;int token=generation;
  io.execute(()->{try{WebLesson data=lessonStore.loadNamed(game,origin,name);ui(()->{if(generation==token&&pageGame.equals(game)){lesson=data;loadGoal();say("Профиль выбран. Записывайте только эту игру; перед другой выберите её профиль");}});}
   catch(IOException|RuntimeException e){ui(()->{if(generation==token)say("Профиль не загружен. Существующие данные сохранены");});}});
 }
 private void openLegacy(){
  final String game=pageGame;int token=generation;
  io.execute(()->{
   java.util.ArrayList<File> files=new java.util.ArrayList<>();java.util.ArrayList<String> names=new java.util.ArrayList<>();
   File original=lessonStore.legacyFile(game);if(original.isFile()||new File(original+".bak").isFile()){files.add(null);names.add("Старый набор этой страницы");}
   for(File f:lessonStore.archiveFiles()){if(files.size()>=128)break;files.add(f);names.add("Архив "+f.getName().substring(0,12)+" · "+Math.max(1,f.length()/1024)+" КиБ");}
   ui(()->{
    if(generation!=token)return;
    if(files.isEmpty()){say("Старых наборов нет. Можно импортировать сохранённый .lpweb");return;}
    new AlertDialog.Builder(this).setTitle("Архивы без названия игры").setItems(names.toArray(new String[0]),(d,which)->{
     io.execute(()->{try{WebLesson old=files.get(which)==null?lessonStore.loadLegacy(game):WebLesson.load(files.get(which));ui(()->{if(generation==token)receiveIncoming(old);});}
      catch(IOException e){ui(()->{if(generation==token)say("Архив не прочитан; исходные файлы сохранены");});}});
    }).setNegativeButton("Отмена",null).show();
   });
  });
 }
 private void capture(){
  if(capturing||lesson==null||web.getWidth()<32||web.getHeight()<32||!web.hasWindowFocus())return;
  int token=generation;capturing=true;int[] location=new int[2];web.getLocationInWindow(location);Rect rect=new Rect(location[0],location[1],location[0]+web.getWidth(),location[1]+web.getHeight());
  float aspect=web.getWidth()/(float)web.getHeight();int h=Math.max(32,Math.min(640,Math.round(384/aspect))),w=Math.max(32,Math.round(h*aspect));
  Bitmap bitmap;
  try{bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);}catch(RuntimeException|OutOfMemoryError error){capturing=false;stop("Недостаточно памяти для кадра web-игры");return;}
  try{PixelCopy.request(getWindow(),rect,bitmap,result->{
   capturing=false;if(dead||!resumed||token!=generation||result!=PixelCopy.SUCCESS){bitmap.recycle();return;}
   try{
    Frame previous=latest;float[] features=WebFrames.features(bitmap,previous==null?null:previous.features);long now=SystemClock.elapsedRealtime();
    Frame current=new Frame(bitmap,features,now,aspect);latest=current;
    if(recording){
     if(pending!=null&&now>=pending.up+(metricSelector.isEmpty()?120:400)){
      Pending p=pending;pending=null;GoalReward reward=p.outcome;
      if(!p.explicitOutcome&&!reward.known())reward=GoalReward.observed(metricMode,p.metric,metricValue,metricScale,p.interrupted,p.up,metricTime,now);
      p.finalized=add(p.before,p.action,features,p.aspect,p.up,reward);activeRows++;
     }
     else if(!gesture&&pending==null&&previous!=null&&activeRows>=3&&waitRows<activeRows/2&&previous.time>lastInput&&now-lastInput>500&&now-lastWait>1000){lastWait=now;waitRows++;add(previous.features,new WebLesson.Action(WebLesson.Kind.WAIT,0,0,0,0,100),features,aspect,now,GoalReward.unknown());}
    }
    if(previous!=null)previous.bitmap.recycle();
    if(playing&&now-lastAuto>400){lastAuto=now;WebLesson.Action action=model.predict(features,aspect);if(action!=null&&action.kind!=WebLesson.Kind.WAIT)inject(action);else say("Модель ждёт: нет однозначного похожего примера");}
   }catch(RuntimeException|OutOfMemoryError error){stop("Кадр web-игры не обработан");}
  },main);}catch(RuntimeException error){capturing=false;bitmap.recycle();stop("Снимок web-области недоступен");}
 }
 private void startRecord(){
  stop("Подготовка записи");if(lesson==null||!lesson.identified()){say("Сначала выберите игру через «Игра ▾»");return;}if(lesson.size()>=WebLesson.MAX_ROWS){say("Профиль заполнен: 1000 примеров. Экспортируйте его; данные других игр хранятся отдельно");return;}if(!ready()){say("Дождитесь загрузки игры и первого кадра");return;}
  model=null;recordSession=UUID.randomUUID().toString();recording=true;activeRows=waitRows=0;lastInput=SystemClock.elapsedRealtime();say("Запись включена: играйте вручную. STOP завершает сессию");
 }
 private void touch(MotionEvent event){
  if(synthetic)return;
  if(injecting)stop("Ручное касание: подсказка остановлена");
  if(playing)stop("Ручное касание: автоматический режим выключен");
  if(!recording)return;
  long now=SystemClock.elapsedRealtime();lastInput=now;
  if(event.getActionMasked()==MotionEvent.ACTION_POINTER_DOWN){multi=true;return;}
  if(event.getActionMasked()==MotionEvent.ACTION_DOWN){if(pending!=null)pending.interrupted=true;downMetric=now-metricTime<=450?metricValue:Double.NaN;gesture=true;multi=false;downAt=now;downX=event.getX()/web.getWidth();downY=event.getY()/web.getHeight();downFeatures=ready()&&pending==null?latest.features.clone():null;}
  if(event.getActionMasked()==MotionEvent.ACTION_CANCEL){gesture=false;downFeatures=null;}
  if(event.getActionMasked()==MotionEvent.ACTION_UP){
   gesture=false;float x=event.getX()/web.getWidth(),y=event.getY()/web.getHeight();long duration=now-downAt;
   if(!multi&&downFeatures!=null&&x>=0&&x<=1&&y>=0&&y<=1&&duration<=2000){
    float distance=Math.abs(x-downX)+Math.abs(y-downY);WebLesson.Kind kind=distance>.04f?WebLesson.Kind.DRAG:duration>450?WebLesson.Kind.HOLD:WebLesson.Kind.TAP;
    pending=new Pending(downFeatures,new WebLesson.Action(kind,downX,downY,x,y,(int)Math.max(30,duration)),now,latest.aspect);pending.metric=downMetric;
   }
   downFeatures=null;
  }
 }
 private WebLesson.Sample add(float[] before,WebLesson.Action action,float[] after,float aspect,long time,GoalReward reward){
  try{lesson.add(new WebLesson.Sample(recordSession,time,aspect,action,before,after,reward));say("Записано: "+action.label()+" · награда "+(reward.known()?reward.value:"неизвестна"));if(queuedSave!=null)main.removeCallbacks(queuedSave);final WebLesson recorded=lesson;queuedSave=()->{queuedSave=null;persist(recorded.copy());};main.postDelayed(queuedSave,700);return lesson.lastSample();}
  catch(IllegalArgumentException|IllegalStateException error){stop("Запись остановлена: предел данных или неверный пример. Экспортируйте данные");return null;}
 }
 private String goalKey(){return lesson.game+"."+lesson.profileId;}
 private void loadGoal(){
  metricSelector="";metricValue=Double.NaN;metricTime=0;if(lesson==null)return;
  android.content.SharedPreferences p=getSharedPreferences("web-goals",0);String k=goalKey();metricSelector=p.getString(k+".selector","");try{metricScale=Double.parseDouble(p.getString(k+".scale","100"));if(!Double.isFinite(metricScale)||metricScale<=0||metricScale>1e12)throw new IllegalArgumentException();}catch(RuntimeException error){metricSelector="";metricScale=100;}metricMode=p.getBoolean(k+".distance",false)?GoalReward.Source.DISTANCE:GoalReward.Source.PROGRESS;
 }
 private void readMetric(){
  if(metricBusy||metricSelector.isEmpty()||lesson==null||!resumed||dead)return;
  metricBusy=true;int token=generation;long requested=SystemClock.elapsedRealtime();
  long requestId=++metricRequestId;
  String script="(function(){try{var e=document.querySelector("+JSONObject.quote(metricSelector)+");return e?e.textContent.slice(0,129):null;}catch(e){return null;}})()";
  main.postDelayed(()->{if(token==generation&&requestId==metricRequestId&&metricBusy){metricBusy=false;metricValue=Double.NaN;metricTime=0;}},600);
  try{web.evaluateJavascript(script,json->{if(token!=generation||requestId!=metricRequestId)return;metricBusy=false;long now=SystemClock.elapsedRealtime();if(now-requested>400)return;
   try{Object raw=new JSONTokener(json).nextValue();if(!(raw instanceof String))throw new IllegalArgumentException();metricValue=GoalReward.parseMetricText((String)raw);metricTime=now;}catch(Exception error){metricValue=Double.NaN;metricTime=0;}
  });}catch(RuntimeException error){metricBusy=false;metricValue=Double.NaN;}

 }
 private void outcomeMenu(){
  if(lesson==null||!lesson.identified()){say("Сначала выберите профиль игры");return;}
  int token=generation;WebLesson selected=lesson;Pending selectedPending=pending;WebLesson.Sample selectedSample=lesson.lastSample();
  new AlertDialog.Builder(this).setTitle("Оценка последнего действия").setItems(new String[]{"Удачно +1","Ошибка −1","Без изменения0","Убрать оценку","Посчитать награду по двум значениям","Настроить автоматический показатель","Выключить автоматическую оценку"},(d,i)->{
   if(token!=generation||lesson!=selected)return;
   if(i<=3){markSelectedOutcome(i==3?GoalReward.unknown():GoalReward.manual(i==0?1:i==1?-1:0),selectedPending,selectedSample);return;}
   if(i==4)measureOutcome();else if(i==5)configureGoal();else{stop("Автооценка выключена");metricSelector="";getSharedPreferences("web-goals",0).edit().remove(goalKey()+".selector").apply();}
  }).setNegativeButton("Отмена",null).show();
 }
 private void markOutcome(GoalReward reward){markSelectedOutcome(reward,pending,lesson==null?null:lesson.lastSample());}
 private void markSelectedOutcome(GoalReward reward,Pending selectedPending,WebLesson.Sample selectedSample){
  outcomeRevision++;model=null;if(playing)stop("Оценка: автоматическое управление остановлено");
  if(selectedPending!=null){if(selectedPending==pending){pending.outcome=reward;pending.explicitOutcome=true;say("Оценка будет записана после следующего кадра");return;}selectedSample=selectedPending.finalized;}
  if(lesson==null||selectedSample==null){say("Нет завершённого примера для оценки");return;}
  try{lesson.rate(selectedSample,reward);persist(lesson.copy());say("Оценка действия: "+(reward.known()?"награда "+reward.value:"оценка снята")+". Чтобы применить оценки, нажмите Обучить");}catch(IllegalStateException error){say("Пример уже изменился; откройте оценку снова");}
 }
 private EditText numberField(LinearLayout form,String hint,String value){EditText e=new EditText(this);e.setHint(hint);e.setText(value);e.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL|InputType.TYPE_NUMBER_FLAG_SIGNED);form.addView(e);return e;}
 private void measureOutcome(){
  if(pending!=null){say("Дождитесь записи текущего действия");return;}WebLesson selected=lesson;WebLesson.Sample last=lesson.lastSample();if(last==null){say("Сначала запишите действие");return;}
  LinearLayout form=new LinearLayout(this);form.setOrientation(LinearLayout.VERTICAL);Spinner mode=new Spinner(this);mode.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Показатель растёт — лучше","Расстояние уменьшается — лучше"}));form.addView(mode);
  EditText before=numberField(form,"До действия",""),after=numberField(form,"После действия",""),scale=numberField(form,"Изменение для награды1","100");int token=generation;
  new AlertDialog.Builder(this).setTitle("Цель последнего записанного действия").setView(form).setNegativeButton("Отмена",null).setPositiveButton("Оценить",(d,w)->{
   if(token!=generation||lesson!=selected||lesson.lastSample()!=last){say("Пример изменился; оценка не применена");return;}
   try{markOutcome(GoalReward.metric(mode.getSelectedItemPosition()==0?GoalReward.Source.PROGRESS:GoalReward.Source.DISTANCE,Double.parseDouble(before.getText().toString().replace(',','.')),Double.parseDouble(after.getText().toString().replace(',','.')),Double.parseDouble(scale.getText().toString().replace(',','.'))));}catch(IllegalArgumentException error){say("Укажите конечные числа и положительный масштаб");}
  }).show();
 }
 private void configureGoal(){
  stop("Настройка цели: запись остановлена");final WebLesson selected=lesson;if(selected==null)return;int token=generation;
  LinearLayout form=new LinearLayout(this);form.setOrientation(LinearLayout.VERTICAL);TextView help=new TextView(this);help.setText("Для HTML-игры: CSS-селектор элемента, содержащего только число. Для Canvas без такого элемента используйте ручную оценку. Смена цели требует отдельного профиля.");form.addView(help);
  EditText selector=new EditText(this);selector.setHint("Например #score или .distance");selector.setText(metricSelector);form.addView(selector);
  Spinner direction=new Spinner(this);direction.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Больше — лучше","Меньше — лучше"}));direction.setSelection(metricMode==GoalReward.Source.DISTANCE?1:0);form.addView(direction);EditText scale=numberField(form,"Изменение для награды1",Double.toString(metricScale));
  new AlertDialog.Builder(this).setTitle("Автоматическая награда").setView(form).setNegativeButton("Отмена",null).setPositiveButton("Сохранить",(d,w)->{
   if(token!=generation||lesson!=selected)return;try{String css=selector.getText().toString().trim();double n=Double.parseDouble(scale.getText().toString().replace(',','.'));if(css.isEmpty()||css.length()>200||!Double.isFinite(n)||n<=0||n>1e12)throw new IllegalArgumentException();
    if(selected.hasMetricRewards()&&(!css.equals(metricSelector)||n!=metricScale||(direction.getSelectedItemPosition()==1)!=(metricMode==GoalReward.Source.DISTANCE))){say("Для другой цели создайте новый профиль игры");return;}
    model=null;metricSelector=css;metricScale=n;metricMode=direction.getSelectedItemPosition()==1?GoalReward.Source.DISTANCE:GoalReward.Source.PROGRESS;
    getSharedPreferences("web-goals",0).edit().putString(goalKey()+".selector",css).putString(goalKey()+".scale",Double.toString(n)).putBoolean(goalKey()+".distance",direction.getSelectedItemPosition()==1).apply();say("Цель сохранена. Если число недоступно или действия перекрылись, награда останется неизвестной");
   }catch(IllegalArgumentException error){say("Проверьте селектор и положительный масштаб");}
  }).show();
 }
 private void persist(WebLesson snapshot){io.execute(()->{try{lessonStore.saveNamed(snapshot);}catch(IOException|RuntimeException e){ui(()->{
  if(lesson!=null&&lesson.game.equals(snapshot.game)&&lesson.profileId.equals(snapshot.profileId))stop("Ошибка сохранения профиля: запись и управление выключены");
  else say("Не удалось сохранить профиль «"+snapshot.profileName+"»; его прежние файлы сохранены");
 });}});}
 private void train(){
  if(pending!=null){say("Дождитесь кадра результата текущего действия, затем нажмите Обучить");return;}
  stop("Подготавливаю модель по примерам");if(lesson==null||!lesson.identified()){say("Сначала выберите профиль через «Игра ▾»");return;}WebLesson data=lesson.copy();int token=generation;long revision=outcomeRevision;
  io.execute(()->{try{String report=data.trainReport();ui(()->{if(token==generation&&revision==outcomeRevision){model=data;say(report);}});}catch(RuntimeException e){ui(()->{if(token==generation)say("Нужно минимум 10 примеров. Для оценки нужна отдельная сессия записи");});}});
 }
 /** Explicit opt-in. Full Web imitation never drives external Android apps. */
 private void transfer2048(){
  if(model==null||lesson==null||!model.identified()
    ||!model.game.equals(lesson.game)||!model.profileId.equals(lesson.profileId)){
   new AlertDialog.Builder(this).setTitle("Обучение для обычного 2048")
     .setMessage("Для переноса сначала выберите профиль 2048, оцените действия и нажмите «Обучить». Существующий перенос можно отключить без очистки данных.")
     .setNegativeButton("Закрыть",null)
     .setNeutralButton("Отключить перенос",(d,w)->disable2048Transfer()).show();
   return;
  }
  final Web2048Transfer transfer;
  try{transfer=Web2048Transfer.fromLesson(model);}
  catch(IllegalArgumentException error){
   new AlertDialog.Builder(this).setTitle("Недостаточно совместимого опыта")
     .setMessage("Нужен именованный профиль «2048» и минимум 10 оценённых направленных свайпов, среди них 5 успешных. Чужие игры и неоценённые жесты не используются.")
     .setNegativeButton("Закрыть",null)
     .setNeutralButton("Отключить перенос",(d,w)->disable2048Transfer()).show();
   return;
  }
  new AlertDialog.Builder(this).setTitle("Передать опыт обычному 2048?")
    .setMessage("Перенести "+transfer.evidence()+" оценённых свайпов? Они дадут небольшой бонус выбора среди допустимых направлений. Снимки и касания не копируются. Реальные ходы по-прежнему подтверждаются по экрану.")
    .setNegativeButton("Отмена",null)
    .setNeutralButton("Отключить перенос",(d,w)->disable2048Transfer())
    .setPositiveButton("Использовать",(d,w)->{
     getSharedPreferences("real-2048-transfer",MODE_PRIVATE).edit().putString("model",transfer.encode()).apply();
     say("Опыт 2048 подключён к настоящему CPU-планировщику: "+transfer.evidence()+" оценённых направлений, бонус только среди допустимых ходов.");
    }).show();
 }
 private void disable2048Transfer(){
  getSharedPreferences("real-2048-transfer",MODE_PRIVATE).edit().remove("model").apply();
  say("Перенос опыта в настоящий 2048 отключён. Исходные уроки не удалены. Текущий запущенный игровой сеанс надо перезапустить.");
 }
 private void autoplay(){stop("Подготовка игры по примерам");if(!ready()||model==null||!model.game.equals(lesson.game)||!model.profileId.equals(lesson.profileId)){say("Откройте игру, запишите/импортируйте примеры и нажмите «Обучить»");return;}playing=true;lastAuto=0;say("Локальная модель включена. STOP или ручное касание выключает управление");}
 private void inject(WebLesson.Action action){
  if(!ready()||gesture||action.kind==WebLesson.Kind.WAIT)return;
  long at=SystemClock.uptimeMillis();int token=generation;gesture=true;
  sendTouch(at,at,MotionEvent.ACTION_DOWN,action.x,action.y);
  for(int i=1;i<=4;i++){final int step=i;main.postDelayed(()->{
   if(token!=generation||!resumed)return;
   float fraction=step/4f,x=action.x+(action.toX-action.x)*fraction,y=action.y+(action.toY-action.y)*fraction;
   sendTouch(at,SystemClock.uptimeMillis(),step==4?MotionEvent.ACTION_UP:MotionEvent.ACTION_MOVE,x,y);
   if(step==4)gesture=false;
  },(long)action.duration*i/4);}
 }
 private void sendTouch(long down,long at,int type,float x,float y){MotionEvent e=MotionEvent.obtain(down,at,type,x*web.getWidth(),y*web.getHeight(),0);synthetic=true;
  if(type==MotionEvent.ACTION_DOWN){injecting=true;injectedDown=down;}
  injectedX=x;injectedY=y;
  if(type==MotionEvent.ACTION_UP||type==MotionEvent.ACTION_CANCEL){injecting=false;gesture=false;}
  try{web.dispatchTouchEvent(e);}finally{synthetic=false;e.recycle();}}
 private void pickImport(){stop("Импорт: запись и управление выключены");importPage=pageGame;importProfile=selectedProfile;startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE),IMPORT);}
 private void beginExport(WebLesson data,String name){
  stop("Подготавливаю экспорт: запись и управление выключены");WebLesson snapshot=data.copy();int token=generation;
  io.execute(()->{try{byte[] encoded=snapshot.encode();ui(()->{if(generation!=token)return;exportSnapshot=encoded;
   try{startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("text/plain").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,name),EXPORT);}
   catch(RuntimeException e){exportSnapshot=null;say("Не удалось открыть сохранение. Исходные примеры сохранены");}
  });}catch(RuntimeException e){ui(()->{if(generation==token)say("Экспорт не подготовлен: предел данных или неверный набор");});}});
 }
 private void exportData(){if(lesson==null){say("Сначала выберите профиль игры");return;}beginExport(lesson,"localpilot-web-"+lesson.game.substring(0,12)+"-"+lesson.profileId.substring(0,8)+".lpweb");}
 private void importUrl(){EditText input=new EditText(this);input.setHint("https://.../examples.lpweb");new AlertDialog.Builder(this).setTitle("Примеры по HTTPS-ссылке").setView(input).setNegativeButton("Отмена",null).setPositiveButton("Загрузить",(d,w)->{
  stop("Загружаю примеры");final String url=input.getText().toString().trim();int token=generation;
  io.execute(()->{try{WebLesson loaded=WebLesson.decode(WebHttp.examples(url));ui(()->{if(generation==token)receiveIncoming(loaded);});}catch(IOException|RuntimeException e){ui(()->{if(generation==token)say("Проверьте HTTPS-ссылку и формат LOCALPILOT-WEB-v1/v2");});}});
 }).show();}
 private void receiveIncoming(WebLesson incoming){
  if(incoming.identified()){merge(incoming);return;}
  final int token=generation;
  say("Сохраняю старые записи отдельно: в них нет названия игры");
  io.execute(()->{try{lessonStore.archiveLegacy(incoming);ui(()->{if(generation==token)offerLegacy(incoming);});}
   catch(IOException|RuntimeException e){ui(()->{if(generation==token)say("Не удалось сохранить архив старых записей. Исходный файл не изменён");});}});
 }
 private void offerLegacy(WebLesson incoming){
  new AlertDialog.Builder(this).setTitle("Старый набор без профиля · "+incoming.size()+" примеров")
   .setMessage("Он сохранён отдельным архивом. Внутри могут быть разные игры. Для обучения выбирайте только проверенный сеанс одной игры.")
   .setNegativeButton("Закрыть",null).setNeutralButton("Экспорт архива",(d,w)->beginExport(incoming,"localpilot-web-legacy-"+incoming.game.substring(0,12)+".lpweb"))
   .setPositiveButton("Выбрать сеанс",(d,w)->legacySession(incoming)).show();
 }
 private void legacySession(WebLesson incoming){
  if(lesson==null||!lesson.identified()){say("Сначала выберите целевую игру через «Игра ▾», затем откройте старый набор снова");return;}
  if(!incoming.origin.equals(pageOrigin)){say("Этот старый набор записан на другом сайте");return;}
  java.util.ArrayList<String> ids=new java.util.ArrayList<>(incoming.sessions().keySet()),labels=new java.util.ArrayList<>();
  for(int i=0;i<ids.size();i++)labels.add("Сеанс "+(i+1)+" · "+incoming.sessions().get(ids.get(i))+" примеров");
  if(ids.isEmpty()){say("Старый набор пуст");return;}
  final String game=pageGame,origin=pageOrigin,name=lesson.profileName,id=lesson.profileId;int token=generation;
  new AlertDialog.Builder(this).setTitle("Какой сеанс целиком относится к «"+name+"»?").setItems(labels.toArray(new String[0]),(d,which)->{
   CheckBox confirmed=new CheckBox(this);confirmed.setText("Подтверждаю: весь выбранный сеанс записан в «"+name+"»");
   AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Назначить сеанс игре?").setMessage("Если внутри сеанса разные игры, оставьте его в архиве. Исходные записи сохранятся.").setView(confirmed).setNegativeButton("Отмена",null)
    .setPositiveButton("Перенести копию",(box,w)->{if(confirmed.isChecked()&&generation==token&&pageGame.equals(game)&&lesson!=null&&lesson.profileId.equals(id))merge(incoming.session(ids.get(which)).assignedTo(game,origin,name));}).create();
   dialog.setOnShowListener(v->{dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);confirmed.setOnCheckedChangeListener((button,checked)->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(checked));});dialog.show();
  }).setNegativeButton("Отмена",null).show();
 }
 private void merge(WebLesson incoming){
  if(lesson==null||!lesson.identified()){say("Сначала выберите совпадающий профиль «"+incoming.profileName+"»");return;}
  WebLesson base=lesson.copy();int token=generation;say("Проверяю и объединяю примеры профиля «"+base.profileName+"»");
  io.execute(()->{try{WebLesson combined=base.merged(incoming);ui(()->{
   if(generation!=token||lesson==null||!lesson.game.equals(base.game)||!lesson.profileId.equals(base.profileId))return;
   lesson=combined;model=null;persist(combined.copy());say("Добавлены примеры только профиля «"+lesson.profileName+"». Нажмите «Обучить»");
  });}catch(RuntimeException e){ui(()->{if(generation==token)say("Импорт не выполнен: выберите совпадающий профиль «"+incoming.profileName+"» и страницу; предел1000 примеров на игру. Исходные данные сохранены");});}});
 }
 @Override protected void onActivityResult(int request,int result,Intent data){
  super.onActivityResult(request,result,data);if(result!=RESULT_OK||data==null||data.getData()==null){if(request==EXPORT)exportSnapshot=null;return;}final Uri uri=data.getData();
  if(request==IMPORT){final int token=generation;final String game=importPage,name=importProfile;
   io.execute(()->{try(InputStream in=getContentResolver().openInputStream(uri)){if(in==null)throw new IOException();WebLesson loaded=WebLesson.decode(WebLesson.bounded(in));ui(()->{if(generation==token&&pageGame.equals(game)&&selectedProfile.equals(name))receiveIncoming(loaded);});}
    catch(IOException|RuntimeException e){ui(()->{if(generation==token)say("Файл не импортирован: пустой, повреждённый или несовместимый формат");});}});
  }
  if(request==EXPORT&&exportSnapshot!=null){byte[] bytes=exportSnapshot;exportSnapshot=null;io.execute(()->{try(OutputStream out=getContentResolver().openOutputStream(uri)){if(out==null)throw new IOException();out.write(bytes);ui(()->say("Примеры экспортированы"));}catch(IOException e){ui(()->say("Не удалось записать экспорт; исходные примеры сохранены"));}});}
 }
 private void configure(){
  stop("Настройки API");LinearLayout form=new LinearLayout(this);form.setOrientation(LinearLayout.VERTICAL);
  EditText url=new EditText(this);url.setText(endpoint);url.setHint("HTTPS URL /v1/chat/completions");form.addView(url);
  EditText name=new EditText(this);name.setText(modelName);name.setHint("ID модели с поддержкой изображений");form.addView(name);
  EditText secret=new EditText(this);secret.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);secret.setText(apiKey);secret.setHint("API key");form.addView(secret);
  CheckBox remember=new CheckBox(this);remember.setText("Сохранить ключ через Android Keystore");form.addView(remember);
  new AlertDialog.Builder(this).setTitle("Интернет-модель · OpenAI-compatible API").setView(form).setNegativeButton("Отмена",null).setNeutralButton("Удалить ключ",(d,w)->{apiKey="";io.execute(()->WebVault.clear(getApplicationContext()));say("Сохранённый ключ удалён");}).setPositiveButton("Сохранить",(d,w)->{
   try{String ep=url.getText().toString().trim();java.net.URI u=WebLesson.https(ep);if(u.getRawQuery()!=null||u.getRawFragment()!=null)throw new IllegalArgumentException();String model=name.getText().toString().trim(),key=secret.getText().toString().trim();if(model.isEmpty()||key.isEmpty()||key.contains("\n")||key.contains("\r"))throw new IllegalArgumentException();
    final boolean persistKey=remember.isChecked();endpoint=ep;modelName=model;apiKey=key;getSharedPreferences("web-learning",0).edit().putString("endpoint",ep).putString("model",model).apply();
    io.execute(()->{try{if(persistKey)WebVault.save(getApplicationContext(),key);else WebVault.clear(getApplicationContext());ui(()->say("API настроен. Снимок отправляется только по «Подсказка»"));}catch(Exception e){ui(()->say("Ключ действует до закрытия приложения; безопасное сохранение не удалось"));}});
   }catch(IllegalArgumentException e){say("Укажите HTTPS endpoint без query, ID vision-модели и ключ");}
  }).show();
 }
 private void teacher(){
  stop("Подготовка подсказки");if(!ready()||apiKey.isEmpty()||modelName.isEmpty()){say("Дождитесь кадра и настройте API с поддержкой изображений");return;}
  int token=generation;float[] features=latest.features.clone();long time=latest.time;ByteArrayOutputStream jpeg=new ByteArrayOutputStream();latest.bitmap.compress(Bitmap.CompressFormat.JPEG,75,jpeg);
  final byte[] request;
  try{
   String image="data:image/jpeg;base64,"+android.util.Base64.encodeToString(jpeg.toByteArray(),android.util.Base64.NO_WRAP);
   JSONObject text=new JSONObject().put("type","text").put("text","Suggest one game gesture. Do not click ads, purchases or account controls. WAIT if uncertain. Return only JSON: {kind: WAIT|TAP|DRAG|HOLD, x:0..1, y:0..1, toX:0..1, toY:0..1, duration:30..1500}. Coordinates refer to the image.");
   JSONObject picture=new JSONObject().put("type","image_url").put("image_url",new JSONObject().put("url",image));
   JSONObject body=new JSONObject().put("model",modelName).put("max_tokens",200).put("messages",new JSONArray().put(new JSONObject().put("role","user").put("content",new JSONArray().put(text).put(picture))));request=body.toString().getBytes(StandardCharsets.UTF_8);
  }catch(JSONException e){say("Не удалось подготовить запрос");return;}
  String ep=endpoint,key=apiKey;say("Отправляю снимок игровой области выбранному API…");
  io.execute(()->{try{
   JSONObject response=new JSONObject(new String(WebHttp.teacher(ep,key,request),StandardCharsets.UTF_8));String content=response.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content").trim();
   if(content.startsWith("```")){int line=content.indexOf('\n');int end=content.lastIndexOf("```");if(line<0||end<=line)throw new JSONException("format");content=content.substring(line+1,end).trim();}
   JSONObject a=new JSONObject(content);WebLesson.Action action=new WebLesson.Action(WebLesson.Kind.valueOf(a.getString("kind")),(float)a.optDouble("x",0),(float)a.optDouble("y",0),(float)a.optDouble("toX",a.optDouble("x",0)),(float)a.optDouble("toY",a.optDouble("y",0)),a.optInt("duration",100));
   ui(()->{if(generation==token){suggestion=action;suggestedFrom=features;suggestionTime=time;say("Подсказка: "+action.label()+". Применение — отдельной кнопкой; движущаяся сцена может устареть");}});
  }catch(Exception e){ui(()->{if(generation==token)say("API не вернул допустимую подсказку. Проверьте endpoint, ключ, vision-модель и лимиты провайдера");});}});
 }
 private void applySuggestion(){
  if(!ready()||suggestion==null||SystemClock.elapsedRealtime()-suggestionTime>30000){say("Подсказка отсутствует или устарела. Запросите новую");return;}
  float distance=0;for(int i=0;i<192;i++)distance+=Math.abs(suggestedFrom[i]-latest.features[i]);
  if(distance/192>.04f){say("Игра изменилась после снимка. Подсказка не применяется; запросите новую");suggestion=null;return;}
  WebLesson.Action action=suggestion;suggestion=null;if(action.kind==WebLesson.Kind.WAIT){say("Модель рекомендует ждать; касание не отправлено");return;}inject(action);say("Подсказка применена. Это не подтверждение успешного игрового хода");
 }
 @Override protected void onResume(){super.onResume();resumed=true;if(web!=null)web.onResume();main.post(poll);}
 @Override protected void onPause(){resumed=false;stop("Пауза: запись и управление выключены");main.removeCallbacks(poll);web.onPause();super.onPause();}
 @Override protected void onDestroy(){dead=true;main.removeCallbacksAndMessages(null);clearFrame();web.destroy();io.shutdown();super.onDestroy();}
}
