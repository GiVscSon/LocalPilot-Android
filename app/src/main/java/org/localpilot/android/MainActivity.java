package org.localpilot.android;
import android.app.*;import android.os.*;import android.content.*;import android.provider.Settings;import android.widget.*;
public class MainActivity extends Activity {
 public static final String PREF="pilot";
 private TextView status;private Switch automatic;private DiagnosticRequest diagnosticRequest;
 private static final int REPORT=14;
 private static final class DiagnosticRequest {
  final String session;final android.graphics.Bitmap frame;
  DiagnosticRequest(String session,android.graphics.Bitmap frame){this.session=session;this.frame=frame;}
  void close(){if(frame!=null&&!frame.isRecycled())frame.recycle();}
 }
 @Override public void onCreate(Bundle b){super.onCreate(b);Object retained=getLastNonConfigurationInstance();if(retained instanceof DiagnosticRequest)diagnosticRequest=(DiagnosticRequest)retained;LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(24,24,24,24);ScrollView scroll=new ScrollView(this);scroll.addView(box);setContentView(scroll);
  TextView title=new TextView(this);title.setText("LocalPilot Android"+(getPackageName().endsWith(".dev")?" Dev":"")+" · 0.7.18\nАркады и обучение на телефоне");title.setTextSize(24);box.addView(title);
  TextView help=new TextView(this);help.setText("1. Откройте игру и выберите её профиль: «Игра».\n2. Нажмите «Окно» и выберите приложение с игрой.\n3. Нажмите «Старт» и разрешите показ экрана.\n\nSTOP останавливает игру агента. В первый раз включите управление кнопкой ниже.");box.addView(help);
  button(box,"Включить управление",()->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
  TextView panelHelp=new TextView(this);panelHelp.setText("Панель можно свернуть. STOP всегда остаётся доступным.");box.addView(panelHelp);
  button(box,"Показать панель",()->{if(PilotService.instance!=null)PilotService.instance.showPanel();else toast("Сначала включите службу");});
  button(box,"Web · варианты обучения",()->startActivity(new Intent(this,WebLearningActivity.class)));
  button(box,"Аркады · обучать нейросеть",()->startActivity(new Intent(this,TrainingActivity.class)));
  automatic=new Switch(this);automatic.setText("Автоуправление выбранной игрой");automatic.setChecked(getSharedPreferences(PREF,MODE_PRIVATE).getBoolean("auto_control",true));automatic.setOnCheckedChangeListener((v,on)->{getSharedPreferences(PREF,MODE_PRIVATE).edit().putBoolean("auto_control",on).apply();if(PilotService.instance!=null)PilotService.instance.setAutomatic(on);});box.addView(automatic);
  button(box,"Выбрать игру или браузер",()->startActivity(new Intent(this,GamePickerActivity.class)));
  TextView autoHelp=new TextView(this);autoHelp.setText("Откройте установленную игру или игру в браузере → выберите окно и профиль → Старт. Профили: Прыгун, Арканоид, Машина, Птенец, 2048. Распознавание зависит от графики и управления игры; неизвестные игры могут требовать адаптации. Для Прыгуна доступна калибровка цветов. В одном браузере задавайте разные имена: Игра → Имя игры. Модели сохраняются отдельно для каждого приложения и имени.");box.addView(autoHelp);
  button(box,"Открыть 2048",()->openGame(new Intent(this,Game2048Activity.class),"2048"));
  button(box,"Крестики-нолики",()->openGame(new Intent(this,MiniGamesActivity.class).putExtra("kind",0),"Крестики-нолики"));
  button(box,"Погаси свет",()->openGame(new Intent(this,MiniGamesActivity.class).putExtra("kind",1),"Погаси свет"));
  button(box,"Проверить касания",()->startActivity(new Intent(this,DemoActivity.class)));
  button(box,"Включить сбор примеров",()->{if(PilotService.instance==null){toast("Сначала включите службу");return;}new AlertDialog.Builder(this).setTitle("Сохранять игровые примеры?").setMessage("В памяти приложения останутся только распознанные поля до/после и проверенное действие, без снимков. До 256 примеров / 256 КиБ. Это не обучение. При заполнении сбор выключается, игра продолжается. При ошибке записи управление остановится.").setPositiveButton("Включить",(d,w)->{if(PilotService.instance!=null)PilotService.instance.enableExamples();else toast("Служба отключена");}).setNegativeButton("Отмена",null).show();});
  button(box,"Выключить сбор примеров",()->{if(PilotService.instance!=null)PilotService.instance.disableExamples();});
  button(box,"Экспорт примеров",()->{if(PilotService.instance==null){toast("Сначала включите службу");return;}try{PilotService.instance.exportExamples();startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("text/plain").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,"localpilot-experience-v1.log"),11);}catch(java.io.IOException e){toast("Хранилище недоступно; исходник сохранён");}});
  button(box,"Восстановить в пустое хранилище",()->{if(PilotService.instance==null){toast("Сначала включите службу");return;}PilotService.instance.disableExamples();new AlertDialog.Builder(this).setTitle("Восстановление примеров").setMessage("Файл будет проверен полностью. Восстановление разрешено только в пустое исправное хранилище. Имеющиеся примеры не заменяются. Импорт не включает обучение или управление.").setPositiveButton("Выбрать файл",(d,w)->startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("text/*").addCategory(Intent.CATEGORY_OPENABLE),12)).setNegativeButton("Отмена",null).show();});
  button(box,"Отчёт с кадром игры",this::beginDiagnosticReport);
  button(box,"Экспорт задержек",()->{if(PilotService.instance==null){toast("Сначала включите службу");return;}PilotService.instance.exportMetrics();startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("text/plain").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,"localpilot-latency.txt"),10);});
  button(box,"Экспорт диагностики сбоя",()->startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("text/plain").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,"localpilot-crash.txt"),13));
  button(box,"Экспорт журнала",()->startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/zip").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,"localpilot-audit.zip"),9));
  button(box,"STOP и убрать панель",()->{if(PilotService.instance!=null)PilotService.instance.hidePanel();});
  status=new TextView(this);box.addView(status);
 }
 private void openGame(Intent intent,String label){startActivity(intent);if(PilotService.instance!=null)PilotService.instance.selectInstalledTarget(getPackageName(),label);}
 @Override public void onResume(){super.onResume();if(automatic!=null)automatic.setChecked(getSharedPreferences(PREF,MODE_PRIVATE).getBoolean("auto_control",true));if(status!=null)status.setText(PilotService.instance==null?"Служба не подключена; обучение в симуляторах доступно":"Служба подключена · выберите игру или обучение");}
 private void beginDiagnosticReport(){
  if(diagnosticRequest!=null)diagnosticRequest.close();
  PilotService pilot=PilotService.instance;android.graphics.Bitmap frame=pilot==null?null:pilot.copyDiagnosticFrame();
  String session=pilot==null?"Accessibility service disconnected; no RAM game snapshot":"";
  if(pilot!=null)session=pilot.exportDiagnosticState();
  diagnosticRequest=new DiagnosticRequest(session,frame);
  try{startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/zip").addCategory(Intent.CATEGORY_OPENABLE)
    .putExtra(Intent.EXTRA_TITLE,"localpilot-diagnostic.zip"),REPORT);}
  catch(RuntimeException error){diagnosticRequest.close();diagnosticRequest=null;toast("Не удалось открыть сохранение отчёта");}
 }
 private String deviceDiagnostic(){
  StringBuilder out=new StringBuilder("LocalPilot device-v1\n");
  try{android.content.pm.PackageInfo p=getPackageManager().getPackageInfo(getPackageName(),0);out.append("Build=").append(p.versionName).append("/code").append(p.getLongVersionCode()).append(" package=").append(getPackageName());}catch(android.content.pm.PackageManager.NameNotFoundException ignored){}
  out.append("\nmanufacturer=").append(Build.MANUFACTURER).append(" model=").append(Build.MODEL).append(" sdk=").append(Build.VERSION.SDK_INT)
    .append(" android=").append(Build.VERSION.RELEASE).append("\nabis=").append(String.join(",",Build.SUPPORTED_ABIS))
    .append(" cores=").append(Runtime.getRuntime().availableProcessors()).append(" max_heap=").append(Runtime.getRuntime().maxMemory())
    .append(" used_heap=").append(Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory())
    .append("\naccessibility_connected=").append(PilotService.instance!=null).append(" automatic=").append(getSharedPreferences(PREF,MODE_PRIVATE).getBoolean("auto_control",true));
  try{ActivityManager manager=(ActivityManager)getSystemService(ACTIVITY_SERVICE);ActivityManager.MemoryInfo memory=new ActivityManager.MemoryInfo();manager.getMemoryInfo(memory);
   out.append("\nram_total=").append(memory.totalMem).append(" ram_available=").append(memory.availMem).append(" low_memory=").append(memory.lowMemory).append(" memory_class_mb=").append(manager.getMemoryClass());
  }catch(RuntimeException e){out.append("\nRAM metadata unavailable");}
  return out.append("\nNo serial, account, API key or network identifier is collected.\n").toString();
 }
 private void finishDiagnosticReport(int result,Intent data){
  DiagnosticRequest saved=diagnosticRequest;diagnosticRequest=null;
  if(result!=RESULT_OK||data==null||data.getData()==null){if(saved!=null)saved.close();return;}
  final DiagnosticRequest snapshot=saved==null?new DiagnosticRequest("RAM snapshot unavailable after Activity/process restart",null):saved;
  final android.net.Uri uri=data.getData();final String device=deviceDiagnostic();
  new Thread(()->{
   try{
    byte[] png=null;
    if(snapshot.frame!=null){java.io.ByteArrayOutputStream image=new java.io.ByteArrayOutputStream();if(!snapshot.frame.compress(android.graphics.Bitmap.CompressFormat.PNG,100,image))throw new java.io.IOException();png=image.toByteArray();}
    byte[] report=DiagnosticBundle.create(snapshot.session,device,CrashDiagnostics.report(getApplicationContext()),png);
    try(java.io.OutputStream out=getContentResolver().openOutputStream(uri)){if(out==null)throw new java.io.IOException();out.write(report);}
    final boolean included=png!=null;runOnUiThread(()->toast(included?"Отчёт и кадр игры сохранены. Можно прикрепить ZIP к чату":"Отчёт сохранён без кадра: снимка игры в памяти нет"));
   }catch(java.io.IOException|RuntimeException|OutOfMemoryError error){runOnUiThread(()->toast("Отчёт не сохранён полностью. Исходные данные игры сохранены"));}
   finally{snapshot.close();}
  },"LocalPilot-Game-Report").start();
 }
 @Override public Object onRetainNonConfigurationInstance(){return diagnosticRequest;}
 @Override protected void onDestroy(){if(!isChangingConfigurations()&&diagnosticRequest!=null){diagnosticRequest.close();diagnosticRequest=null;}super.onDestroy();}
 @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);
  if(request==REPORT){finishDiagnosticReport(result,data);return;}
  if(request==13){if(result==RESULT_OK&&data!=null&&data.getData()!=null){final android.net.Uri uri=data.getData();new Thread(()->{try{byte[] report=CrashDiagnostics.report(getApplicationContext()).getBytes(java.nio.charset.StandardCharsets.UTF_8);try(java.io.OutputStream out=getContentResolver().openOutputStream(uri)){if(out==null)throw new java.io.IOException();out.write(report);}runOnUiThread(()->toast("Диагностика сбоя сохранена"));}catch(java.io.IOException|RuntimeException e){runOnUiThread(()->toast("Не удалось сохранить диагностику сбоя"));}},"LocalPilot-Crash-Export").start();}return;}
  if((request!=9&&request!=10&&request!=11&&request!=12)||result!=RESULT_OK||data==null)return;try{if(request==11||request==12){if(PilotService.instance==null){toast("Сначала включите службу");return;}if(request==11){byte[] samples=PilotService.instance.exportExamples();try(java.io.OutputStream out=getContentResolver().openOutputStream(data.getData())){if(out==null)throw new java.io.IOException();out.write(samples);}toast("Примеры экспортированы, ввод остановлен");}else{try(java.io.InputStream in=getContentResolver().openInputStream(data.getData())){if(in==null)throw new java.io.IOException();int count=PilotService.instance.restoreExamples(ExperienceStore.bounded(in));toast("Восстановлено примеров: "+count+". Сбор и ввод выключены");}}return;}if(request==10){if(PilotService.instance==null){toast("Служба отключена: замеры RAM недоступны");return;}byte[] metrics=PilotService.instance.exportMetrics().getBytes(java.nio.charset.StandardCharsets.UTF_8);try(java.io.OutputStream out=getContentResolver().openOutputStream(data.getData())){if(out==null)throw new java.io.IOException("Нет файла");out.write(metrics);}toast("Замеры экспортированы, ввод остановлен");return;}byte[] bytes=PilotService.instance!=null?PilotService.instance.exportAudit():new SafeJournal(new java.io.File(getFilesDir(),"audit-v1.log")).snapshot();try(java.io.OutputStream out=getContentResolver().openOutputStream(data.getData())){if(out==null)throw new java.io.IOException("Нет файла");out.write(bytes);}toast("Экспортирован журнал метаданных без снимков");}catch(java.io.IOException e){toast("Операция не выполнена; существующие данные сохранены");}}
 private void button(LinearLayout b,String t,Runnable r){Button x=new Button(this);x.setText(t);x.setOnClickListener(v->r.run());b.addView(x);}
 private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
}
