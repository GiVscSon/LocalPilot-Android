package org.localpilot.android;

import android.app.Activity;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Main model backend screen. Display is Russian; model-facing text is English. */
public final class LocalCoreActivity extends Activity {
 private final LocalModelCore core=new LocalModelCore();
 private EmbeddedGgufRuntime embedded;
 private boolean bundled(){return BuildConfig.EMBEDDED_GGUF;}
 private boolean busy(){return bundled()?embedded.busy():core.busy();}
 private TextView result,connection;
 private EditText question;

 @Override public void onCreate(Bundle saved){
  super.onCreate(saved);
  if(bundled())embedded=new EmbeddedGgufRuntime(this);
  ScrollView scroll=new ScrollView(this);
  LinearLayout layout=new LinearLayout(this);
  layout.setOrientation(LinearLayout.VERTICAL);
  layout.setPadding(24,24,24,24);
  scroll.addView(layout);
  setContentView(scroll);
  scroll.setOnApplyWindowInsetsListener((view,insets)->{
   android.graphics.Insets bars=insets.getInsets(
     android.view.WindowInsets.Type.systemBars()|android.view.WindowInsets.Type.displayCutout());
   view.setPadding(bars.left,bars.top,bars.right,bars.bottom);
   return insets;
  });
  scroll.requestApplyInsets();
  title(layout,bundled()?"Встроенное ядро · MNN-Pocket":"Локальное ядро · MNN-Pocket",24);
  label(layout,bundled()
   ?"Веса GGUF и нативный движок входят в APK. Termux, интернет, API-ключ и загрузка модели не нужны. При первом запуске веса проверяются и распаковываются во внутреннюю память."
   :"Главный локальный языковой backend: Termux / llama-server / 127.0.0.1:8080. Запросы на английском; облако не нужно.");
  label(layout,"MNN-Pocket может анализировать диагностику и, после явного включения, "+
   "предлагать ход 2048. Нейросеть сама не выполняет жесты: каждый ход отдельно "+
   "проверяется на свежем поле и проходит STOP, ActionGate, InputPermit и TTL.");
  if(bundled()){
   title(layout,"Проверяемое управление 2048",18);
   label(layout,"Экспериментальный режим: только 2048. Модель предлагает LEFT/UP/RIGHT/DOWN. "+
     "Правила игры и новый стабильный кадр проверяются независимо. "+
     "Неправильный ход заменяется проверенным CPU-планом. "+
     "Переключение режима вызывает STOP и требует новой привязки окна.");
   TextView mode=label(layout,"Режим MNN 2048: выключен");
   PilotService connected=PilotService.instance;
   if(connected!=null&&connected.mnn2048Enabled())
    mode.setText("Режим MNN 2048: включён (до завершения службы)");
   button(layout,"Включить / выключить MNN 2048",()->{
    PilotService service=PilotService.instance;
    if(service==null){mode.setText("Сначала включите службу специальных возможностей LocalPilot");return;}
    service.setMnn2048Enabled(!service.mnn2048Enabled());
    mode.setText(service.mnn2048Enabled()
       ?"MNN 2048 включена. Вернитесь в игру и заново привяжите окно."
       :"MNN 2048 выключена. Используется проверенный CPU-контроллер.");
   });
  }
  connection=label(layout,"Нажмите «Проверить подключение».");
  button(layout,bundled()?"Проверить встроенные веса":"Проверить подключение",()->{
   try{
    if(bundled())embedded.health(value->connection.setText(value));
    else core.health(value->connection.setText(value));
    connection.setText("Проверяем локальное ядро...");
   }catch(IllegalStateException e){connection.setText("Локальный движок занят");}
  });
  button(layout,"Самопроверка английского · BLUE / 27",()->{
   if(busy()){connection.setText("Ядро уже занято");return;}
   try{
    if(bundled())embedded.selfTest(value->connection.setText(value));
    else core.selfTest(value->connection.setText(value));
    connection.setText("Тестирую два независимых английских запроса...");
   }catch(IllegalStateException failure){connection.setText("Ядро занято или закрыто");}
  });
  title(layout,"Вопрос ядру · English",18);
  question=new EditText(this);
  question.setSingleLine(false);
  question.setMinLines(3);
  question.setText("Which diagnostic stage is failing? What is one safe next test?");
  layout.addView(question);
  result=label(layout,"Ответ будет здесь. Только локальный запрос, без выполнения команд.");
  button(layout,"Спросить локальную модель",()->{
   if(busy()){result.setText("Ядро выполняет предыдущий запрос");return;}
   PilotService service=PilotService.instance;
   String snapshot=service==null
     ?"{\"schema\":\"localpilot-telemetry-v1\",\"status\":\"SERVICE_NOT_CONNECTED\"}"
     :service.advisoryDiagnosticSnapshot();
   try{
    if(bundled())embedded.ask(question.getText().toString(),snapshot,text->result.setText(text));
    else core.ask(question.getText().toString(),snapshot,text->result.setText(text));
    result.setText("Локальная модель думает... Ввод в игру не выполняется.");
   }catch(IllegalArgumentException|IllegalStateException error){
    result.setText(error.getMessage());
   }
  });
 }

 private static TextView label(LinearLayout parent,String value){
  TextView label=new TextView(parent.getContext());
  label.setText(value);
  label.setTextSize(16);
  label.setPadding(0,8,0,16);
  parent.addView(label);
  return label;
 }
 private static void title(LinearLayout parent,String value,int size){
  TextView label=label(parent,value);
  label.setTextSize(size);
 }
 private void button(LinearLayout parent,String label,Runnable onClick){
  Button button=new Button(this);
  button.setText(label);
  button.setOnClickListener(view->onClick.run());
  parent.addView(button,new LinearLayout.LayoutParams(
    ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));
 }
 @Override protected void onDestroy(){
  core.close();
  if(embedded!=null)embedded.close();
  super.onDestroy();
 }
}
