package org.localpilot.android;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.Spinner;
import java.io.InputStream;
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
 private static final int SELECT_PHOTO=5121;
 private TextView result,connection,photoStatus;
 private EditText question;
 private ImageView photoPreview;
 private byte[] photoRgb;
 private int photoWidth,photoHeight;
 private Bitmap previewBitmap;

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
  title(layout,bundled()?"Встроенное ядро · выбор моделей":"Локальное ядро · MNN-Pocket",24);
  label(layout,bundled()
   ?"Веса GGUF и нативный движок входят в APK. Termux, интернет, API-ключ и загрузка модели не нужны. При первом запуске веса проверяются и распаковываются во внутреннюю память."
   :"Главный локальный языковой backend: Termux / llama-server / 127.0.0.1:8080. Запросы на английском; облако не нужно.");
  label(layout,"MNN-Pocket объясняет общую стратегию по обезличенным показателям игры. "+
   "CPU выполняет все быстрые решения, жесты и проверку результата. "+
   "Текст MNN не может управлять вводом. STOP, ActionGate, InputPermit, TTL и ROI сохранены.");
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
  title(layout,"Стратегия для текущей игры",18);
  TextView strategy=label(layout,
    "Модель объясняет общую тактику на английском. Не выдаёт команды для следующего хода.");
  button(layout,"Объяснить стратегию · MNN",()->{
   if(busy()){strategy.setText("Нейросеть занята; CPU работает независимо.");return;}
   PilotService service=PilotService.instance;
   String snapshot=service==null
     ?"{\"schema\":\"localpilot-telemetry-v1\",\"status\":\"SERVICE_NOT_CONNECTED\"}"
     :service.advisoryDiagnosticSnapshot();
   // User-selected profile is already persisted by PilotService.chooseProfile().
   // Avoid parsing telemetry on the UI thread or unhandled JSONException.
   int profile=getSharedPreferences("panel",MODE_PRIVATE).getInt("profile",-1);
   try{
    if(bundled())embedded.adviseStrategy(profile,snapshot,strategy::setText);
    else core.adviseStrategy(profile,snapshot,strategy::setText);
    strategy.setText("Формирую стратегию в фоне. CPU не ожидает ответа.");
   }catch(IllegalArgumentException|IllegalStateException error){
    strategy.setText(error.getMessage());
   }
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
  title(layout,"Выбор локальной нейросети",18);
  boolean allModels=bundled()&&BuildConfig.MULTI_MODEL_CATALOG;
  if(allModels)label(layout,"Модели уже входят в этот APK. Переключение не скачивает веса и не влияет на CPU-игру.");
  else label(layout,"В обычном APK встроена только MNN-Pocket. Для других моделей нужна сборка Offline Catalog.");
  Spinner picker=new Spinner(this);
  picker.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,
    LocalModelCatalog.labels(allModels)));
  layout.addView(picker);
  LocalModelCatalog.Model defaultModel=LocalModelCatalog.defaultModel(allModels);
  LocalModelCatalog.Model previous=LocalModelCatalog.find(
    getSharedPreferences("models",MODE_PRIVATE).getString("selected",defaultModel.id));
  if(!LocalModelCatalog.bundled(previous,allModels))previous=LocalModelCatalog.Model.POCKET;
  picker.setSelection(previous.ordinal());
  if(bundled())embedded.select(previous);
  TextView selected=label(layout,"Активна: "+previous.label+" · одновременно загружается одна модель.");
  button(layout,"Выбрать модель",()->{
   LocalModelCatalog.Model next=LocalModelCatalog.Model.values()[picker.getSelectedItemPosition()];
   if(!bundled()||!LocalModelCatalog.bundled(next,allModels)){
    selected.setText("Эта модель отсутствует в установленном APK.");return;
   }
   try{
    embedded.select(next);
    getSharedPreferences("models",MODE_PRIVATE).edit().putString("selected",next.id).apply();
    selected.setText("Выбрана: "+next.label+" · веса проверятся при первом запросе.");
   }catch(IllegalStateException error){
    selected.setText("Нейросеть занята. Выбор не изменён.");
   }
  });

  title(layout,"Ручной анализ фотографии",18);
  label(layout,"Только LFM2.5-VL-450M с модулем зрения. Выбери фотографию "+
    "или скриншот, затем нажми «Анализировать». Изображение не отправляется в интернет "+
    "и не управляет игрой.");
  photoPreview=new ImageView(this);
  photoPreview.setAdjustViewBounds(true);
  photoPreview.setVisibility(View.GONE);
  layout.addView(photoPreview,new LinearLayout.LayoutParams(
    ViewGroup.LayoutParams.MATCH_PARENT,420));
  photoStatus=label(layout,"Изображение не выбрано");
  button(layout,"Выбрать фото / скриншот",()->{
   Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);
   intent.addCategory(Intent.CATEGORY_OPENABLE);
   intent.setType("image/*");
   startActivityForResult(intent,SELECT_PHOTO);
  });
  button(layout,"Анализировать выбранное изображение",()->{
   if(!bundled()||!embedded.multimodalAvailable()){
    photoStatus.setText("Для анализа фото выбери LFM2.5-VL-450M в Offline Catalog APK.");return;
   }
   if(photoRgb==null){photoStatus.setText("Сначала выбери фото");return;}
   if(embedded.busy()){photoStatus.setText("Нейросеть занята, CPU продолжает игру.");return;}
   try{
    embedded.analyzeImage("Identify visible game objects, screen elements and uncertainties.",
      photoRgb,photoWidth,photoHeight,photoStatus::setText);
    photoStatus.setText("Анализирую локально в фоне. Игровые жесты не выполняются.");
   }catch(IllegalStateException|IllegalArgumentException ex){
    photoStatus.setText(ex.getMessage());
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
 @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
  super.onActivityResult(requestCode,resultCode,data);
  if(requestCode!=SELECT_PHOTO||resultCode!=RESULT_OK||data==null||data.getData()==null)return;
  photoRgb=null;
  Uri uri=data.getData();
  try{
   BitmapFactory.Options bounds=new BitmapFactory.Options();
   bounds.inJustDecodeBounds=true;
   try(InputStream stream=getContentResolver().openInputStream(uri)){
    BitmapFactory.decodeStream(stream,null,bounds);
   }
   if(bounds.outWidth<1||bounds.outHeight<1||
      bounds.outWidth>20000||bounds.outHeight>20000)
    throw new IllegalArgumentException("Неподдерживаемые размеры фотографии");
   int sample=1;
   while(Math.max(bounds.outWidth/sample,bounds.outHeight/sample)>640)sample*=2;
   BitmapFactory.Options options=new BitmapFactory.Options();
   options.inSampleSize=sample;
   Bitmap decoded;
   try(InputStream stream=getContentResolver().openInputStream(uri)){
    decoded=BitmapFactory.decodeStream(stream,null,options);
   }
   if(decoded==null)throw new IllegalArgumentException("Не удалось декодировать фото");
   int w=decoded.getWidth(),h=decoded.getHeight();
   if(w>640||h>640){
    float scale=Math.min(640f/w,640f/h);
    Bitmap scaled=Bitmap.createScaledBitmap(decoded,
      Math.max(1,Math.round(w*scale)),Math.max(1,Math.round(h*scale)),true);
    decoded.recycle();decoded=scaled;w=decoded.getWidth();h=decoded.getHeight();
   }
   int[] argb=new int[w*h];
   decoded.getPixels(argb,0,w,0,0,w,h);
   byte[] rgb=new byte[w*h*3];
   for(int i=0;i<argb.length;i++){
    int v=argb[i];
    rgb[i*3]=(byte)((v>>16)&255);
    rgb[i*3+1]=(byte)((v>>8)&255);
    rgb[i*3+2]=(byte)(v&255);
   }
   photoRgb=rgb;photoWidth=w;photoHeight=h;
   if(previewBitmap!=null){photoPreview.setImageDrawable(null);previewBitmap.recycle();}
   previewBitmap=decoded;
   photoPreview.setImageBitmap(previewBitmap);
   photoPreview.setVisibility(View.VISIBLE);
   photoStatus.setText("Фото "+w+"×"+h+" подготовлено. "+
     "Пиксели остаются в оперативной памяти, EXIF не передаётся.");
  }catch(Exception|OutOfMemoryError failed){
   photoRgb=null;photoWidth=photoHeight=0;
   photoStatus.setText("Фото не загружено: "+failed.getClass().getSimpleName());
  }
 }
 @Override protected void onDestroy(){
  photoRgb=null;
  if(photoPreview!=null)photoPreview.setImageDrawable(null);
  if(previewBitmap!=null){previewBitmap.recycle();previewBitmap=null;}
  core.close();
  if(embedded!=null)embedded.close();
  super.onDestroy();
 }
}
