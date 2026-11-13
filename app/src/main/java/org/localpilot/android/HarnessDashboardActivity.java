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
import org.localpilot.shared.HarnessCoordinator;
import org.localpilot.shared.ConsoleAgentContract;
import java.io.IOException;
import java.util.List;

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
 private TextView notice;

 @Override public void onCreate(Bundle saved){
  super.onCreate(saved);
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
  row(system,"Ожидает модель: "+live.waiting()+
    " · действие выполняется: "+live.actionInFlight());
  row(system,"Последний STOP: "+live.lastStopReason()+
    " · ревизия "+live.stopRevision());
  LinearLayout model=card("Нейросеть и её фактическое использование",
    "Назначение ≠ запуск. Использование подтверждается только сохранённым переходом.");
  ModelLabStore.Entry bound=boundModel();
  row(model,bound==null?"Для NES не назначена доступная GGUF.":
    "Назначена: "+bound.label+" · "+bound.modelSha.substring(0,12));
  row(model,"Бэкенд: "+(BuildConfig.VULKAN_COMPILED?
    "Vulkan собран, работа GPU ещё должна быть доказана":"CPU · Vulkan не собран в этом APK"));
  if(bound!=null){
   try{
    int used=0;
    for(ConsoleExperienceMemory.Episode e:ConsoleExperienceMemory.read(getFilesDir().toPath()))
     if(e.modelId().equals(bound.id)&&e.modelSha().equals(bound.modelSha)&&
        "PIXELS_DIFFER".equals(e.status()))used++;
    row(model,"Действий этой модели с изменением кадра: "+used+
      " (не доказательство игровой цели)");
   }catch(IOException damaged){
    row(model,"STOP: журнал повреждён; количество действий не подтверждено.");
   }
  }
  LinearLayout experience=card("Общая память NES",
    "Одна история для эмулятора, ModelLab и панели; только реальные homebrew-переходы.");
  try{row(experience,ConsoleExperienceMemory.summary(getFilesDir().toPath()));}
  catch(IOException invalid){row(experience,"STOP: целостность опыта не пройдена.");}
  experience.addView(button("Открыть эпизоды",()->{tab="Эпизоды";render();}));
  LinearLayout launch=card("Играть и исследовать","ROM запускается в существующем GameLab.");
  launch.addView(button("Открыть NES / Денди",this::openNES));
  launch.addView(button("Открыть ModelLab",this::openModelLab));
 }
 private ModelLabStore.Entry boundModel(){
  if(!BuildConfig.UNIFIED_MODELLAB)return null;
  try{
   return HarnessModelBinding.resolve(this,ModelLabActivity.harnessStore(this).list());
  }catch(RuntimeException invalid){return null;}
 }
 private void models(){
  LinearLayout intro=card("Модели, подключённые к реальному runtime",
   "Назначение выбирает GGUF для следующего NES-наблюдения. Веса не входят в APK.");
  intro.addView(button("Загрузить / импортировать / проверить GGUF",this::openModelLab));
  List<ModelLabStore.Entry> list;
  try{list=ModelLabActivity.harnessStore(this).list();}
  catch(RuntimeException error){
   row(intro,"STOP: хранилище моделей не открылось: "+error.getClass().getSimpleName());
   return;
  }
  HarnessModelBinding.Assignment stored=HarnessModelBinding.read(this);
  ModelLabStore.Entry assigned=HarnessModelBinding.resolve(this,list);
  if(stored!=null&&assigned==null)
   row(intro,"STOP: назначенная модель отсутствует или её SHA изменился.");
  if(list.isEmpty()){row(intro,"Нет GGUF. Скачай или импортируй модель в ModelLab.");return;}
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
  intro.addView(button("Отвязать выбранную модель",()->{
   try{HarnessModelBinding.clear(this);render();}
   catch(RuntimeException failure){info("STOP: не удалось отозвать назначение");}
  }));
 }
 private void consoles(){
  LinearLayout nes=card("NES / Денди · JSNES",
    "Настоящее ядро. Homebrew: безопасный один ход GGUF, пошаговый режим, свежий кадр.");
  nes.addView(button("Библиотека ROM / NES",this::openNES));
  nes.addView(button("Выбор и проверка GGUF",this::openModelLab));
  card("Game Boy / SNES","НЕ ПОДКЛЮЧЕНО. Не выдаём список форматов за работающий эмулятор.");
  card("Другие консоли","Новое ядро появится только после интеграции ROM, кадров, ввода и тестов.");
 }
 private void episodes(){
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
  if(all.isEmpty()){row(summary,"Ещё нет подтверждённых игровых переходов.");return;}
  int start=Math.max(0,all.size()-15);
  for(int i=all.size()-1;i>=start;i--){
   ConsoleExperienceMemory.Episode e=all.get(i);
   LinearLayout entry=card("NES #"+(i+1)+" · "+e.button()+" · "+e.status(),
    "Модель: "+e.modelId()+" · SHA "+e.modelSha().substring(0,10));
   row(entry,"Кадры "+e.beforeFrame()+" → "+e.afterFrame()+
     " · CRC "+Integer.toUnsignedString(e.beforeHash())+" → "+
     Integer.toUnsignedString(e.afterHash()));
   row(entry,"Награда: НЕИЗВЕСТНА · веса не менялись");
  }
 }
 private void learning(){
  LinearLayout train=card("Обучение · проверка источника награды",
   "Память предыдущих ходов используется как контекст; это НЕ обучение весов GGUF.");
  try{
   List<ConsoleExperienceMemory.Episode> all=
     ConsoleExperienceMemory.read(getFilesDir().toPath());
   row(train,"Проверенных визуальных переходов: "+all.size());
  }catch(IOException error){row(train,"STOP: журнал повреждён, обучение невозможно.");}
  row(train,"Достоверных игровых наград NES: 0");
  row(train,"PPO / DQN для NES: НЕ ПОДКЛЮЧЕНЫ");
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
  row(device,"GPU-слоёв в действующей модели: "+ModelLabActivity.harnessGpuLayers());
  row(device,"Физический сравнительный GPU/CPU тест: НЕ ПОДТВЕРЖДЁН");
  LinearLayout stop=card("STOP · единый контур безопасности",
   "Не разрешает модельный ввод и не меняет Accessibility. Отзыв разрешений принадлежит HarnessCoordinator.");
  row(stop,"Владелец: "+s.owner()+" · ожидает решение: "+s.waiting());
  row(stop,"Последний отказ: "+s.lastStopReason());
  stop.addView(button("STOP · отозвать ввод NES",()->{
   HarnessCoordinator.Snapshot before=HarnessCoordinator.nes().snapshot();
   HarnessCoordinator.nes().stop("DASHBOARD_USER_STOP");
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
 private void openNES(){
  startActivity(new Intent(this,org.localpilot.gamelab.RetroLibraryActivity.class));
 }
 private void openModelLab(){startActivity(new Intent(this,ModelLabActivity.class));}
 @Override protected void onResume(){super.onResume();render();}
 @Override protected void onDestroy(){destroyed=true;super.onDestroy();}
}
