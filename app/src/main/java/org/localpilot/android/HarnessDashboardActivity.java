package org.localpilot.android;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.localpilot.shared.ConsoleExperienceMemory;
import org.localpilot.shared.StudentPolicyNet;
import org.localpilot.shared.VerifiedReplayFilter;
import org.localpilot.shared.VerifiedPpoReplayStore;
import org.localpilot.shared.HarnessDemoWorkflow;
import org.localpilot.shared.NesSaveStore;
import org.localpilot.shared.HarnessCoordinator;
import org.localpilot.shared.ConsoleAgentContract;
import org.localpilot.shared.NesExperienceArchive;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Single-app native view of existing HarnessCoordinator, NES and ModelLab.
 * No parallel neural backend, emulator, trainer or input owner is created.
 */
public final class HarnessDashboardActivity extends Activity {
 private static final int BG=0xff0a1422,CARD=0xff132237,BORDER=0xff304862;
 private static final int TEXT=0xffedf4ff,MUTED=0xffa7b8cf,ACCENT=0xff87b6ff;
 private static final String[] TABS={
   "Обзор","Модели","Консоли","Эпизоды","Обучение","GPU и STOP"};
 private LinearLayout content;
 private String tab="Обзор";
 private boolean destroyed;
 private volatile boolean trainingBusy;
 private volatile boolean archiveBusy;
 private String pendingExport;
 private static final int EXPORT_NES_EXPERIENCE=6035;
 private final ExecutorService trainingWorker=Executors.newSingleThreadExecutor(job->{
  Thread thread=new Thread(job,"LocalPilot-GoalStudent-Training");
  thread.setDaemon(true);return thread;
 });
 private TextView notice;

 @Override public void onCreate(Bundle saved){
  super.onCreate(saved);
  if(saved!=null){pendingExport=saved.getString("nesArchiveExport");archiveBusy=pendingExport!=null;}
  if(!BuildConfig.UNIFIED_MODELLAB){finish();return;}
  getWindow().setStatusBarColor(BG);
  getWindow().setNavigationBarColor(BG);
  LinearLayout screen=new LinearLayout(this);
  screen.setOrientation(LinearLayout.VERTICAL);
  screen.setBackgroundColor(BG);
  screen.setPadding(dp(12),dp(18),dp(12),dp(6));
  TextView title=label("LocalPilot · Harness Console",23,TEXT,true);
  screen.addView(title);
  screen.addView(label("Один APK · один NES-контроллер · проверенный опыт",12,MUTED,false));
  HorizontalScrollView tabs=new HorizontalScrollView(this);
  tabs.setHorizontalScrollBarEnabled(false);
  LinearLayout rail=new LinearLayout(this);
  rail.setOrientation(LinearLayout.HORIZONTAL);
  for(String name:TABS){
   Button button=button(name,()->{tab=name;render();});
   rail.addView(button);
  }
  tabs.addView(rail);
  screen.addView(tabs);
  notice=label("",12,MUTED,false);
  screen.addView(notice);
  ScrollView scroll=new ScrollView(this);
  content=new LinearLayout(this);
  content.setOrientation(LinearLayout.VERTICAL);
  content.setPadding(dp(2),dp(8),dp(2),dp(18));
  scroll.addView(content);
  screen.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
  setContentView(screen);
  screen.setOnApplyWindowInsetsListener((view,insets)->{
   android.graphics.Insets safe=insets.getInsets(
     android.view.WindowInsets.Type.systemBars()|
     android.view.WindowInsets.Type.displayCutout());
   view.setPadding(dp(12)+safe.left,dp(18)+safe.top,dp(12)+safe.right,dp(6)+safe.bottom);
   return insets;
  });
  screen.requestApplyInsets();
  render();
 }
 private int dp(int value){return (int)(value*getResources().getDisplayMetrics().density);}
 private GradientDrawable background(int color){
  GradientDrawable g=new GradientDrawable();g.setColor(color);
  g.setCornerRadius(dp(14));g.setStroke(dp(1),BORDER);return g;
 }
 private TextView label(String body,int size,int color,boolean bold){
  TextView t=new TextView(this);
  t.setText(body);t.setTextSize(size);t.setTextColor(color);
  if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
  t.setPadding(dp(5),dp(7),dp(5),dp(7));
  return t;
 }
 private Button button(String text,Runnable action){
  Button b=new Button(this);b.setText(text);b.setTextSize(12);
  b.setAllCaps(false);b.setTextColor(TEXT);
  b.setBackground(background(0xff203655));
  b.setOnClickListener(v->{if(!destroyed)action.run();});
  LinearLayout.LayoutParams param=new LinearLayout.LayoutParams(-2,dp(45));
  param.setMargins(dp(3),dp(4),dp(3),dp(4));
  b.setLayoutParams(param);return b;
 }
 private LinearLayout card(String title,String subtitle){
  LinearLayout group=new LinearLayout(this);
  group.setOrientation(LinearLayout.VERTICAL);
  group.setBackground(background(CARD));
  group.setPadding(dp(12),dp(11),dp(12),dp(11));
  LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);
  params.setMargins(0,dp(8),0,dp(6));
  content.addView(group,params);
  group.addView(label(title,17,TEXT,true));
  if(subtitle!=null&&!subtitle.isEmpty())
   group.addView(label(subtitle,13,MUTED,false));
  return group;
 }
 private void row(LinearLayout group,String description){group.addView(label(description,13,TEXT,false));}
 private void info(String text){notice.setText(text);}
 private void render(){
  if(destroyed||content==null)return;
  content.removeAllViews();
  info("Только локальные подтверждённые данные. Нет фиктивных наград или обучения GGUF.");
  try{
   switch(tab){
    case "Модели"->models();
    case "Консоли"->consoles();
    case "Эпизоды"->episodes();
    case "Обучение"->learning();
    case "GPU и STOP"->gpu();
    default->overview();
   }
  }catch(RuntimeException error){
   card("Данные панели недоступны","STOP: "+error.getClass().getSimpleName()+
     ". Никаких действий в NES не выполнено.");
  }
  content.addView(button("Обновить состояние",this::render));
 }
 private void overview(){
  HarnessCoordinator.Snapshot live=HarnessCoordinator.nes().snapshot();
  LinearLayout system=card("Единый контроллер NES",
    "Один владелец ввода: ручной, CPU-эвристика или GGUF. ACK ≠ награда.");
  row(system,"Сеанс: "+(live.epoch().isEmpty()?"ROM не запущена":live.epoch()));
  row(system,"Режим: "+live.mode());
  row(system,"Владелец: "+live.owner());
  row(system,"Ввод занят/зарезервирован: "+live.waiting()+
    " · действие выполняется: "+live.actionInFlight());
  row(system,"Последний STOP: "+live.lastStopReason()+
    " · ревизия "+live.stopRevision());
  LinearLayout model=card("Нейросеть и её фактическое использование",
    "Назначение ≠ запуск. Использование подтверждается только сохранённым переходом.");
  ModelLabStore.Entry bound=boundModel();
  HarnessModelBinding.Assignment binding=HarnessModelBinding.read(this);
  row(model,bound==null?
    (binding!=null&&binding.id().startsWith("student:")?
      "Экспериментальная student-сеть: "+binding.id()+" · только homebrew":
      "Для NES не назначена доступная GGUF или student-сеть.") :
    "Назначена: "+bound.label+" · "+bound.modelSha.substring(0,12));
  try{
   HarnessModelExecutionLog.Summary executions=HarnessModelExecutionLog.summary(getFilesDir().toPath());
   row(model,"Реальных вызовов GGUF: "+executions.inferenceCalls()+
     " · строгих ответов "+executions.validReplies()+
     " · отказов "+executions.rejectedReplies());
   if(executions.inferenceCalls()>0)row(model,
     "Последний инференс: "+executions.lastModelId()+" · "+
     executions.lastLatencyMs()+" мс · GPU-слоёв "+executions.lastGpuLayers());
  }catch(IOException damaged){row(model,
    "STOP: журнал вызовов моделей повреждён, MODEL_USED не подтверждён");}
  row(model,"Бэкенд: "+(BuildConfig.VULKAN_COMPILED?
    "Vulkan собран, работа GPU ещё должна быть доказана":"CPU · Vulkan не собран в этом APK"));
  if(getApplication() instanceof org.localpilot.shared.NesModelHost host){
   var shared=host.nesModelState();
   row(model,"Общий запрос модели: "+shared.phase()+
     (shared.frame()>0?" · NES кадр "+shared.frame()+" · эпоха "+shared.epoch():""));
  }
  if(bound!=null){
   try{
    int used=0;
    for(ConsoleExperienceMemory.Episode e:ConsoleExperienceMemory.read(getFilesDir().toPath()))
     if(e.modelId().equals(bound.id)&&e.modelSha().equals(bound.modelSha))used++;
    row(model,"Подтверждённых действий этой модели: "+used);
   }catch(IOException damaged){
    row(model,"STOP: журнал повреждён; количество действий не подтверждено.");
   }
  }
  LinearLayout experience=card("Общая память NES",
    "Одна история для эмулятора, ModelLab и панели; только реальные homebrew-переходы.");
  try{
   row(experience,ConsoleExperienceMemory.summary(getFilesDir().toPath()));
   row(experience,ConsoleExperienceMemory.goalSummary(getFilesDir().toPath()));
  }catch(IOException invalid){row(experience,"STOP: целостность опыта или игровой награды не пройдена.");}
  experience.addView(button("Открыть эпизоды",()->{tab="Эпизоды";render();}));
  // Installed Android games use the existing GamePicker → PilotService → capture /
  // ActionGate → verified-response pipeline, never another model or gesture owner.
  LinearLayout installed=card("Игры на телефоне · тот же LocalPilot",
    "Установленная игра не означает совместимое управление. Никаких фиктивных NFS-профилей.");
  String gamePackage=getSharedPreferences(MainActivity.PREF,MODE_PRIVATE).getString("selected_game_package","");
  String gameLabel=getSharedPreferences(MainActivity.PREF,MODE_PRIVATE).getString("selected_game_label",gamePackage);
  row(installed,gamePackage.isEmpty()?"Игра ещё не выбрана":"Выбрана: "+gameLabel+" · "+gamePackage);
  PilotService androidPilot=PilotService.instance;
  row(installed,androidPilot==null?"Accessibility-служба выключена. Ввод невозможен.":
    androidPilot.installedGameReadiness(gamePackage));
  row(installed,"MNN-Pocket сейчас проверена на NES/RAM. Для NFS зрительный ввод и моторная политика НЕ ПОДТВЕРЖДЕНЫ.");
  installed.addView(button("Выбрать установленную игру",()->startActivity(new Intent(this,GamePickerActivity.class))));
  installed.addView(button("Показать существующую панель Android",()->{
   PilotService pilot=PilotService.instance;
   if(pilot==null){info("STOP: сначала включите Accessibility-службу LocalPilot");return;}
   pilot.recoverPanel();
   info("Панель Android открыта · выберите механику и окно игры");
  }));
  LinearLayout launch=card("Играть и продолжать","Автоигра Student — встроенная NES-ROM. Свои ROM доступны для ручной игры.");
  launch.addView(button("▶ Автоигра Student · 256 ходов с сохранением",
    ()->startTrustedNesWorkflow(HarnessDemoWorkflow.STUDENT_AUTOPLAY)));
  launch.addView(button("▶ Продолжить сохранённую автоигру",
    ()->startTrustedNesWorkflow(HarnessDemoWorkflow.RESUME_AUTOPLAY)));
  launch.addView(button("Открыть NES / Денди",this::openNES));
  launch.addView(button("▶ Играть: назначенная GGUF, до 4 проверенных NES-ходов",
    ()->startTrustedNesWorkflow(HarnessDemoWorkflow.GGUF_PLAY_4)));
  launch.addView(button("▶ Играть и учиться: 16 NES-ходов Student → PPO-кандидат",
    ()->startTrustedNesWorkflow(HarnessDemoWorkflow.STUDENT_TRAIN_16)));
  try{
   NesSaveStore.Save saved=NesSaveStore.load(new java.io.File(getFilesDir(),"localpilot_vault").toPath());
   row(launch,saved==null?"Сохранённой игры пока нет":
     "Сохранено: NES кадр "+saved.frame()+" · проверенных действий "+saved.verifiedActions()+" · продолжение после перезапуска доступно");
  }catch(IOException invalid){row(launch,"STOP: сохранение повреждено; файл сохранён для диагностики");}
  launch.addView(button("Открыть ModelLab",this::openModelLab));
  content.removeView(launch);content.addView(launch,0);
 }
 private ModelLabStore.Entry boundModel(){
  if(!BuildConfig.UNIFIED_MODELLAB)return null;
  try{
   return HarnessModelBinding.resolve(this,HarnessModelRuntime.from(this).store().list());
  }catch(RuntimeException invalid){return null;}
 }
 private void models(){
  LinearLayout intro=card("Модели, подключённые к реальному runtime",
   "Назначение выбирает GGUF для следующего NES-наблюдения. Веса не входят в APK.");
  intro.addView(button("Загрузить / импортировать / проверить GGUF",this::openModelLab));
  List<ModelLabStore.Entry> list;
  try{list=HarnessModelRuntime.from(this).store().list();}
  catch(RuntimeException error){
   row(intro,"STOP: хранилище моделей не открылось: "+error.getClass().getSimpleName());
   return;
  }
  HarnessModelBinding.Assignment stored=HarnessModelBinding.read(this);
  ModelLabStore.Entry assigned=HarnessModelBinding.resolve(this,list);
  if(stored!=null&&assigned==null)
   row(intro,"STOP: назначенная модель отсутствует или её SHA изменился.");
  if(list.isEmpty())row(intro,"Нет GGUF. Можно загрузить GGUF или использовать обученный CPU student.");
  for(ModelLabStore.Entry e:list){
   LinearLayout model=card(e.label,e.vision()?"Мультимодальная · требуется совместимый mmproj":
     "Текстовая · без зрения кадра NES");
   row(model,"ID: "+e.id+" · "+(e.modelBytes/1000000)+" МБ");
   row(model,"SHA-256: "+e.modelSha.substring(0,16)+"…");
   boolean isAssigned=assigned!=null&&assigned.id.equals(e.id);
   row(model,isAssigned?"НАЗНАЧЕНА · ещё не значит MODEL_USED":
     "НЕ НАЗНАЧЕНА · каталог не доказывает работоспособность модели");
   model.addView(button(isAssigned?"Назначена · открыть ModelLab":"Назначить для NES",()->{
    if(!isAssigned){
     try{HarnessModelBinding.assign(this,e);info("Назначена "+e.label+
       ". Перед вводом NES проверит реальную генерацию и свежий кадр.");}
     catch(RuntimeException failure){info("STOP: не удалось сохранить назначение модели.");}
    }else openModelLab();
    render();
   }));
  }
  row(intro,HomebrewStudentStore.status(this));
  intro.addView(button("Открыть обучение CPU student",()->{tab="Обучение";render();}));
  intro.addView(button("Отвязать выбранную модель",()->{
   try{HarnessModelBinding.clear(this);render();}
   catch(RuntimeException failure){info("STOP: не удалось отозвать назначение");}
  }));
 }
 private void consoles(){
  LinearLayout nes=card("NES / Денди · JSNES",
    "Настоящее ядро. Homebrew: безопасный один ход GGUF, пошаговый режим, свежий кадр.");
  nes.addView(button("Библиотека ROM / NES",this::openNES));
  nes.addView(button("▶ 4 настоящих GGUF-действия с визуальной и RAM-проверкой",
    ()->startTrustedNesWorkflow(HarnessDemoWorkflow.GGUF_PLAY_4)));
  nes.addView(button("▶ 16 StudentPolicy-действий + запись replay + PPO update",
    ()->startTrustedNesWorkflow(HarnessDemoWorkflow.STUDENT_TRAIN_16)));
  nes.addView(button("Выбор и проверка GGUF",this::openModelLab));
  card("Game Boy / SNES","НЕ ПОДКЛЮЧЕНО. Не выдаём список форматов за работающий эмулятор.");
  card("Другие консоли","Новое ядро появится только после интеграции ROM, кадров, ввода и тестов.");
 }
 private void episodes(){
  LinearLayout retention=card("Архив опыта NES",
    "Архив сохраняет RAM-журнал и PPO replay. Игра и веса остаются на месте.");
  try{
   List<String> archives=NesExperienceArchive.list(getFilesDir().toPath());
   row(retention,"Локальных архивов: "+archives.size()+" / "+NesExperienceArchive.MAX_ARCHIVES);
   retention.addView(button("Архивировать опыт · освободить рабочий журнал",this::archiveExperience));
   if(!archives.isEmpty())retention.addView(button("Экспортировать архив в файл",()->exportArchive(archives.get(0))));
  }catch(IOException invalid){row(retention,"STOP: архивы недоступны; исходные файлы не удаляются");}
  LinearLayout summary=card("Проверенные эпизоды NES",
    "Исход: PIXELS_DIFFER или NO_VISUAL_CHANGE. Reward = UNKNOWN.");
  List<ConsoleExperienceMemory.Episode> all;
  try{
   all=ConsoleExperienceMemory.read(getFilesDir().toPath());
   row(summary,ConsoleExperienceMemory.summary(getFilesDir().toPath()));
  }catch(IOException damaged){
   row(summary,"STOP: повреждённая или недоступная история. Не используем в моделях.");
   return;
  }
  // Reward-bearing steps live in the same ConsoleExperienceMemory and have
  // an independent exact-6502 provenance, never inferred from pixel changes.
  try{
   List<ConsoleExperienceMemory.GoalEpisode> tasks=
     ConsoleExperienceMemory.readGoals(getFilesDir().toPath());
   row(summary,ConsoleExperienceMemory.goalSummary(getFilesDir().toPath()));
   int from=Math.max(0,tasks.size()-12);
   for(int i=tasks.size()-1;i>=from;i--){
    ConsoleExperienceMemory.GoalEpisode goal=tasks.get(i);
    LinearLayout detail=card("CPU task "+goal.button()+" · reward "+goal.reward(),
       goal.reached()?"ЦЕЛЬ ДОСТИГНУТА во встроенной ROM":"Встроенная ROM · цель (192,112)");
    row(detail,"Настоящая NES RAM: ("+goal.beforeX()+","+goal.beforeY()+
       ") → ("+goal.afterX()+","+goal.afterY()+") · модель "+goal.modelId());
   }
  }catch(IOException invalid){row(summary,"STOP: записи награды не прошли проверку.");return;}
  try{
   var replay=VerifiedPpoReplayStore.summary(new java.io.File(getFilesDir(),"localpilot_vault").toPath());
   row(summary,"PPO: "+replay.batches()+" проверенных серий / "+replay.steps()+
     " переходов · сумма RAM-наград "+replay.rewardSum()+
     ". Это отдельный журнал; ниже показаны только визуальные переходы.");
  }catch(IOException invalid){row(summary,"STOP: PPO replay не прошёл проверку.");}
  if(all.isEmpty()){row(summary,"Пока нет визуальных переходов. Новых оценок игры не предполагаем.");return;}
  int start=Math.max(0,all.size()-15);
  for(int i=all.size()-1;i>=start;i--){
   ConsoleExperienceMemory.Episode e=all.get(i);
   LinearLayout entry=card("NES #"+(i+1)+" · "+e.button()+" · "+e.status(),
    "Модель: "+e.modelId()+" · SHA "+e.modelSha().substring(0,10));
   row(entry,"Кадры "+e.beforeFrame()+" → "+e.afterFrame()+
     " · CRC "+Integer.toUnsignedString(e.beforeHash())+" → "+
     Integer.toUnsignedString(e.afterHash()));
   row(entry,"Награда: НЕИЗВЕСТНА для визуальной записи; по пикселям она не определяется. RAM-награды и PPO-кандидат — в разделе «Обучение».");
  }
 }
 private void learning(){
  LinearLayout train=card("Обучение · проверка источника награды",
   "Память предыдущих ходов используется как контекст; это НЕ обучение весов GGUF.");
  try{
   List<ConsoleExperienceMemory.Episode> all=
     ConsoleExperienceMemory.read(getFilesDir().toPath());
   row(train,"Проверенных визуальных переходов: "+all.size());
   VerifiedReplayFilter.Verdict legacy=VerifiedReplayFilter.inspectLegacyVisualMemory(all);
   row(train,"PPO-допуск старого журнала: "+(legacy.eligible()?all.size():0)
     +" · "+legacy.reason()+". Нет доверенных наград.");
  }catch(IOException error){row(train,"STOP: журнал повреждён, обучение невозможно.");}
  try{
   List<ConsoleExperienceMemory.GoalEpisode> goals=
     ConsoleExperienceMemory.readGoals(getFilesDir().toPath());
   row(train,"Награды встроенной ROM по проверенной памяти 6502: "+goals.size());
   int achieved=0;for(var g:goals)if(g.reached())achieved++;
   row(train,"Цель (192,112) достигнута: "+achieved+" раз");
  }catch(IOException corrupted){
   row(train,"STOP: журнал целевой награды повреждён");
  }
  row(train,"Остальные ROM: награда UNKNOWN. PPO / DQN для чужих игр не подключены.");
  try{
   VerifiedPpoReplayStore.Summary replay=VerifiedPpoReplayStore.summary(
     new java.io.File(getFilesDir(),"localpilot_vault").toPath());
   row(train,"Записано настоящих on-policy NES-серий: "+replay.batches()+
     " · переходов "+replay.steps()+" · сумма ROM-RAM наград "+replay.rewardSum());
   if(!replay.latestSha256().isEmpty())row(train,
     "Контрольная сумма локального replay: "+replay.latestSha256().substring(0,16));
  }catch(IOException badReplay){row(train,
   "STOP: replay-журнал повреждён / переполнен; обучение заблокировано");}
  train.addView(button("▶ NES · собрать 16 проверенных шагов и обновить PPO-кандидат",
    ()->startTrustedNesWorkflow(HarnessDemoWorkflow.STUDENT_TRAIN_16)));
  row(train,HomebrewStudentStore.status(this));
  row(train,"Сеть 7→12→6 имитирует направление к цели по проверенной памяти ROM. "+
    "Holdout проверяет ошибку классификации на другой ROM-сессии; это не приёмка устройства.");
  train.addView(button(trainingBusy?"Обучение выполняется...":
    "Обучить CPU student на проверенных NES-эпизодах",()->{
    if(archiveBusy){info("Сначала заверши сохранение архива опыта");return;}
    if(trainingBusy){info("Уже обучаю candidate; дождись завершения");return;}
    trainingBusy=true;info("Обучаю отдельные веса на CPU; другие NES-режимы не запускаю");
    trainingWorker.execute(()->{
     String outcome;
     try{outcome=HomebrewStudentStore.train(getApplicationContext());}
     catch(Exception invalid){outcome="STOP: нет допустимого набора для обучения: "+
       invalid.getClass().getSimpleName()+" · "+invalid.getMessage();}
     final String result=outcome;
     runOnUiThread(()->{trainingBusy=false;if(!destroyed){render();info(result);}});
    });
  }));
  train.addView(button("Назначить проверенного student для одного NES-шага",()->{
   try{
    String outcome=HomebrewStudentStore.bindSandbox(this);
    render();info(outcome);
   }catch(Exception failure){info("STOP: student не прошёл offline gate: "+
      failure.getClass().getSimpleName()+" · "+failure.getMessage());}
  }));
  row(train,"Физическая on-policy приёмка и автоматическое продвижение: НЕ ПОДТВЕРЖДЕНЫ.");
  try{
   List<ConsoleExperienceMemory.HomebrewGoal> goals=
     ConsoleExperienceMemory.readHomebrewGoals(getFilesDir().toPath());
   row(train,"ROM RAM-цель homebrew, сохранённых шагов: "+goals.size());
   row(train,"Допущено из RAM-журнала к PPO: 0 · нет obs64 и oldLogProb.");
   if(!goals.isEmpty()){
    ConsoleExperienceMemory.HomebrewGoal last=goals.get(goals.size()-1);
    row(train,"Последняя RAM-награда: "+last.reward()+
      " · модель SHA "+last.modelSha().substring(0,12)+
      " · кадры "+last.frameBefore()+"→"+last.frameAfter());
   }
  }catch(IOException damaged){
   row(train,"STOP: RAM-журнал повреждён или недоступен, обучение заблокировано.");
  }
  row(train,"Общих игровых побед/счётов NES: НЕ ПОДТВЕРЖДЕНО");
  try {
   java.nio.file.Path student=new java.io.File(getFilesDir(),
     "localpilot_vault/models/active/student-homebrew-v1.weights.bin").toPath();
   if(java.nio.file.Files.isRegularFile(student)){
    StudentPolicyNet policy=StudentPolicyNet.load(student);
    row(train,"StudentPolicy CPU: НАСТОЯЩИЙ forward · 64→64→64→"+
      policy.actionCount+" · SHA "+policy.sha256().substring(0,16));
    row(train,"Веса StudentPolicy есть, но PPO-обновление / holdout: НЕ ПОДТВЕРЖДЕНЫ");
   }else row(train,"StudentPolicy CPU: создаётся по первому реальному NES-ходу");
  }catch(IOException broken) {
   row(train,"STOP: StudentPolicy weights.bin повреждены; не используем модель.");
  }
  try{
   java.nio.file.Path candidate=new java.io.File(getFilesDir(),
     "localpilot_vault/models/candidates/student-homebrew-v1.weights.bin").toPath();
   if(java.nio.file.Files.isRegularFile(candidate)){
    StudentPolicyNet pending=StudentPolicyNet.load(candidate);
    row(train,"PPO-кандидат на диске: SHA "+pending.sha256().substring(0,16));
    row(train,"EvaluationGate: НЕ ПРОЙДЕН · активные веса не заменены.");
   }else row(train,"PPO-кандидат: ещё не создан из 16 реальных StudentPolicy шагов.");
  }catch(IOException invalid){
   row(train,"STOP: PPO-кандидат повреждён; активация невозможна.");
  }
  row(train,"PPO/GAE: доступен в NES после 16 on-policy ходов; автоматическая активация ЗАПРЕЩЕНА");
  row(train,"Обученная политика с независимой оценкой: НЕ ПОДТВЕРЖДЕНА");
  row(train,"Для настоящего обучения нужны наблюдения, достоверная цель/награда и проверка весов на отдельных эпизодах.");
  card("Старые симуляторы","Архивное обучение не применяется к NES. Нет автоматического переноса весов.");
 }
 private void gpu(){
  HarnessCoordinator.Snapshot s=HarnessCoordinator.nes().snapshot();
  LinearLayout device=card("Устройство и вычислитель",
   "Показываем возможности сборки, не выдавая компиляцию Vulkan за ускорение.");
  row(device,"Android API: "+android.os.Build.VERSION.SDK_INT+
    " · "+android.os.Build.MANUFACTURER+" "+android.os.Build.MODEL);
  row(device,"ABI: "+String.join(", ",android.os.Build.SUPPORTED_ABIS));
  row(device,"Доступно процессоров: "+Runtime.getRuntime().availableProcessors());
  row(device,"Vulkan в этом APK: "+(BuildConfig.VULKAN_COMPILED?"СОБРАН":"НЕТ"));
  row(device,"GPU-слоёв в действующей модели: "+HarnessModelRuntime.from(this).lastGpuLayers());
  row(device,"Физический сравнительный GPU/CPU тест: НЕ ПОДТВЕРЖДЁН");
  LinearLayout stop=card("STOP · единый контур безопасности",
   "Отзывает NES и Android ввод через их существующих владельцев, без нового исполнителя.");
  row(stop,"Владелец: "+s.owner()+" · ожидает решение: "+s.waiting());
  row(stop,"Последний отказ: "+s.lastStopReason());
  stop.addView(button("STOP · NES и установленные Android-игры",()->{
   // Stop the actual Accessibility/MediaProjection owner before returning to NES.
   PilotService androidPilot=PilotService.instance;
   if(androidPilot!=null)androidPilot.stopFromHarness();
   HarnessCoordinator.Snapshot before=HarnessCoordinator.nes().snapshot();
   if(before.waiting()||before.actionInFlight())
    HarnessCoordinator.nes().requestStop(before.epoch(),"DASHBOARD_USER_STOP");
   else HarnessCoordinator.nes().stop("DASHBOARD_USER_STOP");
   if(before.waiting()||before.actionInFlight()){
    // Bring back the existing activity to release its actual JSNES controls.
    // This is a STOP command, never a model-proposed button.
    Intent stopGame=new Intent(this,org.localpilot.gamelab.NesEmulatorActivity.class);
    stopGame.setAction(ConsoleAgentContract.DASHBOARD_STOP);
    stopGame.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
    try{startActivity(stopGame);}
    catch(RuntimeException failure){info("STOP зарегистрирован; NES недоступна для подтверждения отпускания.");}
   }
   render();
  }));
 }
 private void startTrustedNesWorkflow(String mode){
  if(archiveBusy){info("Сначала заверши сохранение архива опыта");return;}
  if(!HarnessDemoWorkflow.supported(mode)||!BuildConfig.UNIFIED_MODELLAB){
   info("STOP: этот режим не поддерживается текущим единым APK");return;
  }
  HarnessCoordinator.Snapshot running=HarnessCoordinator.nes().snapshot();
  if(running.waiting()||running.actionInFlight()){
   info("STOP: сначала заверши текущий NES-эпизод или останови старого агента");return;
  }
  if(HarnessDemoWorkflow.GGUF_PLAY_4.equals(mode)&&boundModel()==null){
   info("STOP: назначь GGUF в Модели и проверь её SHA, затем запускай");return;
  }
  Intent game=new Intent(this,org.localpilot.gamelab.NesEmulatorActivity.class)
    .putExtra(HarnessDemoWorkflow.EXTRA_MODE,mode)
    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
  try{startActivity(game);}
  catch(RuntimeException unavailable){info("STOP: встроенное ядро NES недоступно");}
 }
 private void openNES(){
  if(archiveBusy){info("Сначала заверши сохранение архива опыта");return;}
  startActivity(new Intent(this,org.localpilot.gamelab.RetroLibraryActivity.class));
 }
 private void openModelLab(){if(archiveBusy){info("Сначала заверши сохранение архива опыта");return;}startActivity(new Intent(this,ModelLabActivity.class));}
 private void archiveExperience(){
  if(archiveBusy||trainingBusy){info("Дождись завершения текущей записи/обучения");return;}
  archiveBusy=true;info("Проверяю и сохраняю архив; рабочие записи пока на месте");
  trainingWorker.execute(()->{
   String result;
   try{
    NesExperienceArchive.Receipt receipt;
    HarnessCoordinator coordinator=HarnessCoordinator.nes();
    HarnessCoordinator.MaintenanceLease lease=coordinator.beginMaintenance();
    if(lease==null)throw new IOException("Сначала останови NES-эпизод");
    try{receipt=NesExperienceArchive.archiveAndRotate(getFilesDir().toPath(),directory->{
     try{
      java.io.FileDescriptor fd=android.system.Os.open(directory.toString(),
        android.system.OsConstants.O_RDONLY|android.system.OsConstants.O_CLOEXEC,0);
      try{
       if(!android.system.OsConstants.S_ISDIR(android.system.Os.fstat(fd).st_mode))
        throw new IOException("Archive fsync target is not a directory");
       android.system.Os.fsync(fd);
      }finally{android.system.Os.close(fd);}
     }catch(android.system.ErrnoException failed){throw new IOException("Archive directory fsync failed",failed);}
    });}
    finally{coordinator.endMaintenance(lease);}
    result="Архив проверен: "+receipt.goalRows()+" RAM-записей, "+receipt.replayBatches()+
      " replay-серий. Рабочий журнал свободен; игра и веса сохранены.";
   }catch(Exception failed){result="STOP: архивирование: "+failed.getMessage()+". Сохранённые архивы не удалены.";}
   final String message=result;
   runOnUiThread(()->{archiveBusy=false;if(!destroyed){render();info(message);}});
  });
 }
 private void exportArchive(String name){
  if(archiveBusy||trainingBusy){info("Дождись завершения текущей записи/обучения");return;}
  pendingExport=name;archiveBusy=true;
  Intent create=new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/zip")
    .addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,"LocalPilot-"+name)
    .putExtra(android.provider.DocumentsContract.EXTRA_INITIAL_URI,
      android.net.Uri.parse("content://com.android.externalstorage.documents/document/primary%3ADownload"));
  try{startActivityForResult(create,EXPORT_NES_EXPERIENCE);}
  catch(RuntimeException unavailable){pendingExport=null;archiveBusy=false;info("Нет приложения для сохранения файла; архив оставлен локально");}
 }
 @Override protected void onActivityResult(int request,int result,Intent data){
  super.onActivityResult(request,result,data);
  if(request!=EXPORT_NES_EXPERIENCE)return;
  final String name=pendingExport;pendingExport=null;
  if(result!=RESULT_OK||data==null||data.getData()==null||name==null){
   archiveBusy=false;info("Экспорт отменён; локальный архив сохранён");return;
  }
  final android.net.Uri uri=data.getData();
  trainingWorker.execute(()->{
   String message;
   try{
    byte[] bytes=NesExperienceArchive.read(getFilesDir().toPath(),name);
    try(java.io.OutputStream out=getContentResolver().openOutputStream(uri,"wt")){
     if(out==null)throw new IOException("Не удалось открыть файл назначения");out.write(bytes);out.flush();
    }
    java.security.MessageDigest sha=java.security.MessageDigest.getInstance("SHA-256");int total=0;
    try(java.io.InputStream in=getContentResolver().openInputStream(uri)){
     if(in==null)throw new IOException("Файл нельзя перечитать для проверки");
     byte[] chunk=new byte[8192];int n;
     while((n=in.read(chunk))!=-1){total+=n;if(total>NesExperienceArchive.MAX_BYTES)throw new IOException("Экспорт превышает лимит");sha.update(chunk,0,n);}
    }
    if(total!=bytes.length)throw new IOException("Размер экспортированного файла отличается");
    StringBuilder digest=new StringBuilder();for(byte b:sha.digest())digest.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
    NesExperienceArchive.retireExported(getFilesDir().toPath(),name,digest.toString());
    message="Экспорт перечитан, SHA-256 совпал. Локальный архив освобождён; файл сохранён в выбранном месте.";
   }catch(Exception failed){message="STOP: экспорт не подтверждён: "+failed.getMessage()+". Локальный архив сохранён.";}
   final String outcome=message;
   runOnUiThread(()->{archiveBusy=false;if(!destroyed){render();info(outcome);}});
  });
 }
 @Override protected void onSaveInstanceState(Bundle out){
  super.onSaveInstanceState(out);if(pendingExport!=null)out.putString("nesArchiveExport",pendingExport);
 }
 @Override protected void onResume(){super.onResume();render();}
 @Override protected void onDestroy(){destroyed=true;trainingWorker.shutdown();super.onDestroy();}
}
