package org.localpilot.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.text.InputType;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Standalone native screen. Neither credential is stored or logged. */
public final class OnlineDiagnosticsActivity extends Activity {
 private final OpenRouterAdvisor advisor=new OpenRouterAdvisor();
 private TextView status,answer;
 private EditText url,telemetryToken,routerKey,models,question;
 @Override public void onCreate(Bundle saved){
  super.onCreate(saved);
  ScrollView scroll=new ScrollView(this);
  LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);
  content.setPadding(24,24,24,24);
  scroll.addView(content);setContentView(scroll);
  scroll.setOnApplyWindowInsetsListener((view,insets)->{
   android.graphics.Insets bars=insets.getInsets(android.view.WindowInsets.Type.systemBars()
     |android.view.WindowInsets.Type.displayCutout());
   view.setPadding(bars.left,bars.top,bars.right,bars.bottom);return insets;
  });scroll.requestApplyInsets();

  title(content,"Диагностика LocalPilot",24);
  paragraph(content,"Два независимых режима. Онлайн-диагностика отправляет обезличенные счётчики вашему HTTPS серверу каждые 10 секунд только после включения. OpenRouter отвечает на отдельный запрос о состоянии игры. Никаких автоматических жестов или отправки скриншотов.");
  title(content,"1. Онлайн-диагностика · свой сервер",18);
  url=field(content,"https://your-server.example/v1/telemetry",false);
  url.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
  telemetryToken=field(content,"Токен доступа к вашему серверу",true);
  status=paragraph(content,"Выключено");
  addButton(content,"Включить передачу",()->{
   PilotService service=PilotService.instance;
   if(service==null){status.setText("Сначала включите службу Accessibility LocalPilot");return;}
   try{
    service.startOnlineDiagnostics(url.getText().toString().trim(),telemetryToken.getText().toString());
    telemetryToken.getText().clear();updateStatus();
   }catch(IllegalArgumentException|IllegalStateException error){
    status.setText("Не удалось включить: "+error.getMessage());
   }
  });
  addButton(content,"Остановить передачу",()->{
   PilotService service=PilotService.instance;
   if(service!=null)service.stopOnlineDiagnostics();
   telemetryToken.getText().clear();updateStatus();
  });
  addButton(content,"Проверить соединение",this::updateStatus);

  title(content,"2. Помощник моделей · OpenRouter",18);
  paragraph(content,"Введите свой ключ OpenRouter и точное название модели. Для резервной маршрутизации можно указать до трёх моделей через запятую. Сводка отправляется только после вашего подтверждения; OpenRouter и выбранный провайдер получат её и вопрос. Запрос может расходовать платные токены. Ключ не сохраняется.");
  routerKey=field(content,"OpenRouter API key · только для текущего запроса",true);
  models=field(content,"Модель или несколько через запятую",false);
  models.setText("openai/gpt-4o-mini");
  addButton(content,"Проверить API-ключ OpenRouter",()->{
   if(advisor.busy()){answer.setText("Предыдущий запрос ещё выполняется");return;}
   String key=routerKey.getText().toString();
   try{
    advisor.checkKey(key,result->answer.setText(result));
    answer.setText("Проверяю ключ на сервере OpenRouter...");
   }catch(IllegalArgumentException|IllegalStateException error){
    answer.setText("Проверка ключа: "+error.getMessage());
   }
  });
  question=field(content,"Что мешает агенту играть и какие ошибки проверить?",false);
  question.setSingleLine(false);question.setMinLines(2);
  question.setText("Почему игра не делает подтверждённых ходов и какой следующий диагностический тест?");
  answer=paragraph(content,"Ответ модели появится здесь, без запуска игровых действий.");
  addButton(content,"Спросить OpenRouter",()->{
   PilotService service=PilotService.instance;
   if(service==null){answer.setText("Служба LocalPilot отключена");return;}
   if(advisor.busy()){answer.setText("Запрос уже выполняется");return;}
   String key=routerKey.getText().toString();
   String selected=models.getText().toString();
   String prompt=question.getText().toString().trim();
   try{
    OpenRouterAdvisor.validateKey(key);
    OpenRouterAdvisor.validateModels(selected);
    if(prompt.isBlank()||prompt.length()>600)
     throw new IllegalArgumentException("Вопрос должен содержать до 600 символов");
   }catch(IllegalArgumentException error){answer.setText(error.getMessage());return;}
   new AlertDialog.Builder(this).setTitle("Отправить обезличенную диагностику?")
     .setMessage("OpenRouter и выбранный провайдер получат вопрос и счётчики игры. Скриншоты, логи, имена окон и координаты не отправляются. Модель может ошибаться и не получает управление LocalPilot.")
     .setNegativeButton("Отмена",null)
     .setPositiveButton("Отправить", (dialog,which)->{
      PilotService current=PilotService.instance;
      if(current==null){answer.setText("Служба отключена");return;}
      // The same whitelist JSON is used for telemetry and model assistance.
      String snapshot=current.advisoryDiagnosticSnapshot();
      try{
       advisor.ask(key,selected,prompt,snapshot,result->answer.setText(result));
       routerKey.getText().clear();
       answer.setText("Запрос отправляется. Выбранный провайдер может взимать плату.");
      }catch(IllegalArgumentException|IllegalStateException error){
       answer.setText("Запрос отклонён: "+error.getMessage());
      }
     }).show();
  });
  updateStatus();
 }
 private void updateStatus(){
  if(status==null)return;
  PilotService service=PilotService.instance;
  status.setText(service==null?"Служба LocalPilot не подключена":
    (service.onlineDiagnosticsEnabled()?"Активно · ":"Выключено · ")+service.onlineDiagnosticsStatus());
 }
 private static void title(LinearLayout into,String text,int size){
  TextView view=new TextView(into.getContext());view.setText(text);view.setTextSize(size);into.addView(view);
 }
 private static TextView paragraph(LinearLayout into,String text){
  TextView view=new TextView(into.getContext());view.setText(text);view.setPadding(0,8,0,12);into.addView(view);
  return view;
 }
 private EditText field(LinearLayout into,String hint,boolean secret){
  EditText input=new EditText(this);input.setHint(hint);input.setSingleLine(true);
  input.setInputType(InputType.TYPE_CLASS_TEXT|(secret?InputType.TYPE_TEXT_VARIATION_PASSWORD:InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS));
  into.addView(input);return input;
 }
 private void addButton(LinearLayout into,String label,Runnable run){
  Button button=new Button(this);button.setText(label);button.setOnClickListener(v->run.run());
  into.addView(button,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));
 }
 @Override protected void onResume(){super.onResume();updateStatus();}
 @Override protected void onDestroy(){advisor.close();super.onDestroy();}
}
