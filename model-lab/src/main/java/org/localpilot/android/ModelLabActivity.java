package org.localpilot.android;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.localpilot.shared.NesTrial;
import org.localpilot.shared.NesFrameTransfer;
import org.localpilot.shared.ConsoleAgentBridge;

/**
 * Independent on-device AI laboratory. No LocalPilot service/gestures are present.
 * Only explicit user clicks import models, start evaluation or share an image.
 */
public final class ModelLabActivity extends Activity {
 // One process-local lab runtime shared by rotated unified-APK activities.
 // The standalone ModelLab still uses ModelLabApp's own app-scoped objects.
 private static EmbeddedGgufRuntime unifiedEngine;
 private static ModelLabStore unifiedStore;
 private boolean inUnifiedApk(){return getPackageName().startsWith("org.localpilot.android");}
 private static final int OPEN_MODEL=820,OPEN_PROJECTOR=821,OPEN_IMAGE=822;
 private ModelLabStore store;
 private EmbeddedGgufRuntime engine;
 private final ExecutorService io=Executors.newSingleThreadExecutor(task->{
  Thread t=new Thread(task,"ModelLab-Storage");t.setDaemon(true);return t;
 });
 private Spinner modelPicker,computePicker,presetPicker;
 private TextView presetStatus;
 private volatile String preferredModelId;
 private volatile boolean presetDownloadRunning,downloadCancelled;
 private TextView computeStatus;
 private final ModelLabComputePolicy compute=new ModelLabComputePolicy();
 private final List<ModelLabStore.Entry> displayed=new ArrayList<>();
 private TextView status,result,modelStatus,imageStatus,nesStatus;
 private NesTrial nesTrial;
 // A callback from an earlier NES episode or screenshot is never displayed
 // as the result of a later one. UI-thread confined revision tracking.
 private final ModelLabResultGate revisions=new ModelLabResultGate();
 private EditText prompt,url,sha256;
 private ImageView preview;
 private Bitmap previewBitmap;
 private byte[] rgb;
 private boolean nesFrameLoaded;
 private int width,height;
 private boolean closed;

 @Override public void onCreate(Bundle saved){
  super.onCreate(saved);
  ScrollView scroll=new ScrollView(this);
  LinearLayout col=new LinearLayout(this);
  col.setOrientation(LinearLayout.VERTICAL);
  col.setPadding(24,24,24,24);
  scroll.addView(col);setContentView(scroll);
  scroll.setOnApplyWindowInsetsListener((view,insets)->{
   android.graphics.Insets bars=insets.getInsets(
     android.view.WindowInsets.Type.systemBars()|android.view.WindowInsets.Type.displayCutout());
   view.setPadding(bars.left,bars.top,bars.right,bars.bottom);
   return insets;
  });
  scroll.requestApplyInsets();
  title(col,"ModelLab · локальные нейросети");
  line(col,(inUnifiedApk()?"Встроенный ModelLab: один LocalPilot APK. ":"Независимый ModelLab. ")+
    "ИИ может выбирать кнопки только внутри NES homebrew через отдельный проверяемый контроллер; внешние Android-жесты и ActionGate не выдаются. "+
    "GGUF работает локально через llama.cpp, модели скачиваются по выбору.");
  status=line(col,"Готовлю библиотеку моделей...");
  try {
   if(getApplication() instanceof ModelLabApp){
    ModelLabApp app=(ModelLabApp)getApplication();
    store=app.store();engine=app.engine();
   }else if(inUnifiedApk()){
    synchronized(ModelLabActivity.class){
     if(unifiedStore==null)unifiedStore=new ModelLabStore(getApplicationContext());
     if(unifiedEngine==null)unifiedEngine=new EmbeddedGgufRuntime(getApplicationContext());
     store=unifiedStore;engine=unifiedEngine;
    }
   }else throw new IllegalStateException("Unknown ModelLab Application host");
  }catch(Exception failure){
   status.setText("Ошибка локального каталога: "+failure.getMessage());return;
  }
  title(col,"NES · единый эксперимент GameLab → ModelLab");
  button(col,"Вернуться в GameLab · настоящий NES",()->{
   try{
    Intent companion=new Intent(Intent.ACTION_MAIN)
      .setClassName(inUnifiedApk()?getPackageName():"org.localpilot.gamelab",
        "org.localpilot.gamelab.TestDeviceActivity")
      .addCategory(Intent.CATEGORY_LAUNCHER)
      .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|
        Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
    startActivity(companion);
   }catch(android.content.ActivityNotFoundException|SecurityException missing){
    status.setText(inUnifiedApk()?"Встроенный GameLab не найден: проверь целостность LocalPilot APK":
      "GameLab не установлен или недоступен.");
   }
  });
  nesStatus=line(col,"Передай эпизод из GameLab NES: кнопка → кадры → реальное изменение пикселей. "+
    "Это НЕ оценка победы, награда или доказательство причинности.");
  if(inUnifiedApk())button(col,"🧠 Назначить выбранную GGUF нейроагентом NES",()->{
   ModelLabStore.Entry choice=selected();
   if(choice==null){nesStatus.setText("Сначала загрузи или импортируй GGUF");return;}
   final int gpuLayers=selectedGpuLayers(choice);
   task("Проверяю GGUF для консольного агента",()->registerConsoleBrain(choice,gpuLayers),false);
  });
  // A verified GGUF file is NOT proof that its native backend can infer.
  // Call the real NES action runtime with deliberately absent game pixels.
  if(inUnifiedApk())button(col,"🧪 Проверить GGUF: настоящая генерация ACTION=STOP",()->{
   ModelLabStore.Entry choice=selected();
   if(choice==null){nesStatus.setText("Выбери скачанную или импортированную GGUF");return;}
   if(engine.busy()){nesStatus.setText("Движок уже занят");return;}
   final int offload=selectedGpuLayers(choice);
   task("Проверяю настоящий вывод NES-команды",()->{
    File weights=store.modelFile(choice);
    runOnUiThread(()->{
     if(closed||selected()==null||!choice.id.equals(selected().id))return;
     try{
      engine.consoleAction(weights,
       "No NES framebuffer has been supplied. This is a model smoke test, not a game. "+
       "The only safe exact answer is ACTION=STOP.",
       offload,(answer,elapsed)->{
        if(closed)return;
        nesStatus.setText("ACTION=STOP".equals(answer)?
         "GGUF-генерация PASS: "+choice.label+" · ACTION=STOP · "+elapsed+
         " мс. Это не подтверждение победы в NES или ускорения GPU.":
         "GGUF-генерация FAIL: "+choice.label+" · модель не вернула строгий STOP. "+
         "Несовместимая модель не будет действовать в NES.");
       });
      nesStatus.setText("Выполняется настоящий GGUF inference. Ожидаю ACTION=STOP...");
     }catch(Exception|LinkageError failure){
      nesStatus.setText("GGUF не запущена: "+failure.getClass().getSimpleName());
     }
    });
    return "Проверка настоящего inference запущена.";
   },false);
  });
  button(col,"Объяснить последний NES-эпизод выбранной нейросетью",()->{
   NesTrial evidence=nesTrial;
   ModelLabStore.Entry selection=selected();
   if(evidence==null){nesStatus.setText("Сначала передай NES-эпизод из GameLab");return;}
   if(selection==null){nesStatus.setText("Выбери или импортируй GGUF-модель");return;}
   if(engine.busy()){nesStatus.setText("Сейчас выполняется другой анализ");return;}
   final long episodeRevision=revisions.nes();
   task("Проверяю модель для NES-анализа",()->{
    File weights=store.modelFile(selection);
    runOnUiThread(()->{
     if(closed||!revisions.currentNes(episodeRevision))return;
     try{
      engine.text(weights,evidence.advisoryPrompt(),selectedGpuLayers(selection),(answer,elapsed)->{
       if(!closed&&revisions.currentNes(episodeRevision))
        nesStatus.setText(evidence.summary()+"\n"+
          selection.label+" · "+elapsed+" мс\n"+answer);
      });
      nesStatus.setText("Анализирую текущий NES-эпизод...");
     }catch(Exception failure){
      if(revisions.currentNes(episodeRevision))
       nesStatus.setText("NES-анализ: "+failure.getMessage());
     }
    });
    return "Только текстовая стратегия, без доступа к игровым кнопкам";
   },false);
  });
  button(col,"STOP · отменить локальную генерацию",()->{
   if(engine.requestCancel())
    status.setText("Отмена запрошена. Движок освободится после завершения нативного вызова.");
   else status.setText("Сейчас нет активного анализа модели.");
  });
  title(col,"Выбор модели");
  modelPicker=new Spinner(this);
  col.addView(modelPicker);
  modelStatus=line(col,"Одна активная модель. Остальные не загружены в RAM.");
  title(col,"Проверенный каталог · модели загружаются отдельно");
  line(col,"APK не содержит тяжёлых весов. Выбери модель: ссылка указывает на "+
    "закреплённую ревизию Hugging Face, размер и SHA-256 проверяются до установки. "+
    "Для зрения автоматически загружается соответствующий mmproj. "+
    "Загруженные веса остаются в приватной памяти приложения.");
  presetPicker=new Spinner(this);
  List<String> recommended=new ArrayList<>();
  for(ModelLabPresetCatalog.Preset p:ModelLabPresetCatalog.ALL)recommended.add(p.description());
  presetPicker.setAdapter(new ArrayAdapter<>(this,
    android.R.layout.simple_spinner_dropdown_item,recommended));
  presetPicker.setSelection(1); // Qwen text is the recommended default, no auto-download.
  col.addView(presetPicker);
  presetStatus=line(col,"Ничего не загружено автоматически. Подключение нужно только для скачивания.");
  button(col,"Скачать выбранную модель по проверенной ссылке",()->{
   int index=presetPicker.getSelectedItemPosition();
   if(index<0||index>=ModelLabPresetCatalog.ALL.size())return;
   ModelLabPresetCatalog.Preset preset=ModelLabPresetCatalog.ALL.get(index);
   if(engine.busy()){presetStatus.setText("Сначала заверши анализ");return;}
   if(presetDownloadRunning){presetStatus.setText("Уже идёт загрузка модели");return;}
   presetDownloadRunning=true;
   downloadCancelled=false;
   boolean queued=task("Загружаю "+preset.label,()->{
    try{
     ModelLabStore.Entry installed=store.downloadPreset(preset,(phase,done,total)->{
      if(downloadCancelled)throw new IllegalStateException("Загрузка отменена пользователем");
      runOnUiThread(()->{
      if(!closed)presetStatus.setText(preset.label+" · "+phase+" · "+
       (total>0?((done*100)/total)+"%":done+" байт"));
     });
    });
    preferredModelId=installed.id;
    if(inUnifiedApk())registerConsoleBrain(installed);
    runOnUiThread(()->{
     if(!closed)presetStatus.setText("Готово · "+installed.label+
      ". SHA-256 и точный размер подтверждены, модель доступна в списке.");
    });
     return "Установлена "+installed.label;
    }finally{presetDownloadRunning=false;}
   },true);
   if(!queued)presetDownloadRunning=false;
  });
  button(col,"Отменить загрузку GGUF",()->{
   if(presetDownloadRunning){
    downloadCancelled=true;
    presetStatus.setText("Отмена запрошена; проверка при следующем блоке данных...");
   }else presetStatus.setText("Сейчас нет активной загрузки");
  });
  title(col,"Ускорение нейросети · CPU / Vulkan GPU");
  line(col,"На POCO X7 Pro установлен Mali-G720. Vulkan экспериментален: "+
    "производительность и устойчивость нужно измерить. По умолчанию всегда CPU. "+
    "При тесте GPU держи ModelLab на экране: некоторые драйверы Mali "+
    "могут зависать при переходе приложения в фон.");
  computePicker=new Spinner(this);
  computePicker.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,
    new String[]{"CPU · надёжный режим","Vulkan GPU · 8 слоёв (эксперимент)",
      "Vulkan GPU · 16 слоёв (эксперимент)","Auto · GPU только после успешного сравнения"}));
  col.addView(computePicker);
  computePicker.setSelection(0); // Explicit opt-in after every app start.
  computeStatus=line(col,BuildConfig.VULKAN_COMPILED?
    "В этом APK собран Vulkan. GPU не используется, пока не выбран пользователем.":
    "Эта сборка только CPU. Нужен отдельный ModelLab Vulkan APK для теста GPU.");
  button(col,"Проверить наличие Vulkan GPU",()->{
   if(engine.busy()){computeStatus.setText("Движок занят");return;}
   engine.probeGpu((answer,elapsed)->{
    if(!closed)computeStatus.setText(answer+" · "+elapsed+" мс");
   });
  });
  button(col,"Сравнить CPU и GPU на одинаковом английском запросе",()->{
   ModelLabStore.Entry choice=selected();
   if(choice==null){computeStatus.setText("Сначала выбери GGUF");return;}
   if(!BuildConfig.VULKAN_COMPILED){
    computeStatus.setText("Vulkan отсутствует в этом APK. Для сравнения нужна GPU-сборка.");return;
   }
   if(engine.busy()){computeStatus.setText("Движок занят");return;}
   final String fixed="Explain why empty cells matter in 2048. Reply in one English sentence.";
   computeStatus.setText("Бенчмарк CPU → GPU8, один запрос, без игровых жестов...");
   task("Проверяю модель для бенчмарка",()->{
    File model=store.modelFile(choice);
    runOnUiThread(()->{
     if(closed)return;
     try{
      engine.text(model,fixed,0,(cpuAnswer,cpuMs)->{
       if(closed)return;
       boolean validCpu=validBenchmarkText(cpuAnswer);
       compute.benchmark(choice.id,false,cpuMs,validCpu);
       if(!validCpu){
        computeStatus.setText("CPU не выдал пригодный ответ; сравнение отменено.");
        return;
       }
       try{
        computeStatus.setText("CPU "+cpuMs+" мс. Тестирую Vulkan GPU8...");
        engine.text(model,fixed,8,(gpuAnswer,gpuMs)->{
         if(closed)return;
         boolean validGpu=validBenchmarkText(gpuAnswer)&&engine.lastGpuLayers()>0;
         compute.benchmark(choice.id,true,gpuMs,validGpu);
         computeStatus.setText(compute.summary(choice.id)+
           (validGpu?" · offload инициализирован, фактическую загрузку GPU проверь на устройстве":
             " · GPU не задействован или ошибка")+
           ". Автоматический режим выберет только измеренно быстрый backend.");
        });
       }catch(RuntimeException error){
        compute.benchmark(choice.id,true,1,false);
        computeStatus.setText("GPU тест не начат: "+error.getClass().getSimpleName());
       }
      });
     }catch(RuntimeException error){
      computeStatus.setText("CPU тест не начат: "+error.getClass().getSimpleName());
     }
    });
    return "Модель проверена";
   },false);
  });
  button(col,"Обновить список",this::refresh);
  button(col,"Удалить выбранную импортированную модель",()->{
   ModelLabStore.Entry e=selected();
   if(e==null||e.bundled){status.setText("Предустановленную модель нельзя удалить");return;}
   if(engine.busy()){status.setText("Заверши текущий тест");return;}
   task("Удаляю модель",()->{store.remove(e);return "Модель удалена";},true);
  });

  title(col,"Добавить другие модели");
  line(col,"Поддерживаются GGUF, включая Qwen, LFM, SmolLM и совместимые архитектуры. "+
    "ONNX, MNN и TFLite требуют отдельных движков и здесь пока не запускаются. "+
    "Для моделей со зрением импортируй отдельно GGUF и совместимый mmproj.gguf.");
  sha256=new EditText(this);
  sha256.setSingleLine(true);
  sha256.setHint("Ожидаемый SHA-256, необязательно");
  col.addView(sha256);
  button(col,"Импорт GGUF из файлов",()->openFile(OPEN_MODEL,"*/*"));
  button(col,"Привязать mmproj к выбранной импортированной модели",()->{
   ModelLabStore.Entry e=selected();
   if(e==null||e.bundled){status.setText("Выбери свою импортированную модель");return;}
   openFile(OPEN_PROJECTOR,"*/*");
  });
  url=new EditText(this);
  url.setSingleLine(false);url.setMaxLines(3);
  url.setHint("https://huggingface.co/.../resolve/main/model.gguf");
  col.addView(url);
  button(col,"Скачать GGUF с Hugging Face",()->{
   String link=url.getText().toString().trim();
   String digest=sha256.getText().toString().trim();
   task("Скачиваю GGUF по HTTPS",()->{
    ModelLabStore.Entry added=store.download(link,filenameFromUrl(link),digest,null);
    if(inUnifiedApk())registerConsoleBrain(added);
    return "Импортирован "+added.label+" · SHA256 "+added.modelSha;
   },true);
  });
  button(col,"Скачать mmproj по ссылке для выбранной модели",()->{
   ModelLabStore.Entry selected=selected();
   if(selected==null||selected.bundled){status.setText("Сначала выбери импортированный GGUF");return;}
   String link=url.getText().toString().trim();
   String digest=sha256.getText().toString().trim();
   task("Загружаю проектор",()->{
    ModelLabStore.Entry updated=store.download(link,"mmproj",digest,selected.id);
    if(inUnifiedApk())registerConsoleBrain(updated);
    return "Проектор привязан к "+updated.label;
   },true);
  });

  title(col,"Текстовый тест");
  prompt=new EditText(this);
  prompt.setText("Explain a useful strategy for keeping empty cells in the game 2048.");
  prompt.setMinLines(2);col.addView(prompt);
  result=line(col,"Ответ и задержка будут показаны здесь.");
  button(col,"Запустить локальную генерацию",()->{
   ModelLabStore.Entry e=selected();
   if(e==null||engine.busy()){result.setText("Нет модели или движок занят");return;}
   String text=prompt.getText().toString().trim();
   if(text.isBlank()||text.length()>1000){result.setText("Текст от 1 до 1000 символов");return;}
   task("Проверяю SHA-256 модели",()->{
    File model=store.modelFile(e);
    runOnUiThread(()->{
     if(closed)return;
     try{
      engine.text(model,text,selectedGpuLayers(e),(answer,elapsed)->{
       if(!closed)result.setText(e.label+" · "+elapsed+" мс\n"+answer);
      });
      result.setText("Генерация в фоне...");
     }catch(Exception error){result.setText(error.getMessage());}
    });
    return "Модель проверена, загружена одна GGUF";
   },false);
  });

  title(col,"Анализ фотографии / игрового скриншота");
  line(col,"Только вручную выбранные изображения. Для зрения нужен "+
   "мультимодальный GGUF с совместимым файлом mmproj. "+
   "Фотография не отправляется на сервер, ответ не запускает игровые действия.");
  preview=new ImageView(this);
  preview.setVisibility(View.GONE);
  preview.setAdjustViewBounds(true);
  col.addView(preview,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,360));
  imageStatus=line(col,"Изображение не выбрано");
  button(col,"Выбрать фото",()->openFile(OPEN_IMAGE,"image/*"));
  button(col,"Анализировать фото локально",()->{
   ModelLabStore.Entry e=selected();
   if(e==null||!e.vision()){
    imageStatus.setText("Выбери модель с mmproj");return;
   }
   if(rgb==null){imageStatus.setText("Сначала выбери изображение");return;}
   if(engine.busy()){imageStatus.setText("Модель занята");return;}
   final byte[] data=rgb.clone();
   final int w=width,h=height;
   final long imageRevision=revisions.image();
   final boolean sourceIsNes=nesFrameLoaded;
   task("Проверяю веса и проектор",()->{
    File model=store.modelFile(e);
    File projector=store.projectorFile(e);
    runOnUiThread(()->{
     if(closed||!revisions.currentImage(imageRevision))return;
     try{
      engine.image(model,projector,
        (sourceIsNes?
          "This is a downsized NES screenshot captured when the episode was shared. "+
          "Describe visible game objects and uncertainty. The screenshot is NOT "+
          "proof of scoring, action causality or a successful game move. ":
          "Describe the visible game objects, colors and uncertain elements."),
        data,w,h,selectedGpuLayers(e),(answer,elapsed)->{
         if(!closed&&revisions.currentImage(imageRevision))
          imageStatus.setText(e.label+" · "+elapsed+" мс\n"+answer);
        });
      imageStatus.setText("Обрабатываю выбранный RGB-кадр в фоне...");
     }catch(Exception failure){
      if(revisions.currentImage(imageRevision))
       imageStatus.setText(failure.getMessage());
     }
    });
    return "Проверка GGUF и mmproj пройдена";
   },false);
  });
  refresh();
  if(inUnifiedApk()&&selected()!=null){
   final ModelLabStore.Entry active=selected();
   task("Подключаю проверенную GGUF к NES",()->registerConsoleBrain(active),false);
  }
  // Image picker and preview must exist before a received NES screenshot can
  // be decoded. onNewIntent also handles repeated exports in singleTop mode.
  receiveNesIntent(getIntent());
 }

 private void receiveNesIntent(Intent intent){
  if(intent==null||!NesTrial.ACTION.equals(intent.getAction()))return;
  // Even a malformed incoming trial invalidates old asynchronous results.
  revisions.newNes();
  revisions.newImage();
  nesTrial=null;
  rgb=null;nesFrameLoaded=false;width=0;height=0;
  if(preview!=null){preview.setImageDrawable(null);preview.setVisibility(View.GONE);}
  if(previewBitmap!=null){previewBitmap.recycle();previewBitmap=null;}
  if(imageStatus!=null)
   imageStatus.setText("Новый NES-эпизод: ожидаю кадр или выбери фото вручную.");
  try{
   String serialized=intent.getStringExtra(NesTrial.EXTRA);
   nesTrial=NesTrial.parse(serialized);
   if(nesStatus!=null)nesStatus.setText("Принят эпизод: "+nesTrial.summary()+
     "\nМожно анализировать текстовый отчёт или полученный снимок через vision GGUF. "+
     "Происхождение данных не аутентифицировано.");
   byte[] png=intent.getByteArrayExtra(NesFrameTransfer.EXTRA_PNG);
   if(png!=null&&preview!=null&&imageStatus!=null){
    try{
     NesFrameTransfer.verify(png);
     Bitmap image=BitmapFactory.decodeByteArray(png,0,png.length);
     if(image==null||image.getWidth()!=NesFrameTransfer.WIDTH||
        image.getHeight()!=NesFrameTransfer.HEIGHT){
      if(image!=null)image.recycle();
      throw new IllegalArgumentException("PNG decoder rejected NES screenshot");
     }
     applyBitmap(image,true);
     imageStatus.setText("Кадр NES при отправке эпизода: "+
       width+"×"+height+". Выбери VL-модель с mmproj и нажми «Анализировать фото». "+
       "Кадр не доказывает результат хода.");
    }catch(RuntimeException|OutOfMemoryError invalid){
     nesFrameLoaded=false;
     imageStatus.setText("Скриншот NES отклонён; текстовый отчёт доступен. "+
       invalid.getClass().getSimpleName());
    }
   }
  }catch(RuntimeException error){
   nesTrial=null;rgb=null;nesFrameLoaded=false;
   if(nesStatus!=null)nesStatus.setText("Неправильный или повреждённый NES-эпизод");
  }
 }
 @Override protected void onNewIntent(Intent next){
  super.onNewIntent(next);
  setIntent(next);
  receiveNesIntent(next);
 }
 private static String filenameFromUrl(String link){
  try{
   String path=new java.net.URI(link).getPath();
   String file=path.substring(path.lastIndexOf('/')+1);
   return file.isBlank()?"Downloaded GGUF":file;
  }catch(Exception e){return "Downloaded GGUF";}
 }
 private void openFile(int code,String mime){
  Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);
  intent.addCategory(Intent.CATEGORY_OPENABLE);intent.setType(mime);
  startActivityForResult(intent,code);
 }
 private ModelLabStore.Entry selected(){
  if(modelPicker==null)return null;
  int pos=modelPicker.getSelectedItemPosition();
  return pos>=0&&pos<displayed.size()?displayed.get(pos):null;
 }
 private boolean validBenchmarkText(String answer){
  return answer!=null&&!answer.isBlank()&&
   !answer.startsWith("Ошибка локальной модели:")&&
   !answer.startsWith("Нет ответа.")&&
   !answer.startsWith("Ответ обрезан")&&
   !answer.startsWith("Ответ содержит служебные");
 }
 private int selectedGpuLayers(ModelLabStore.Entry entry){
  if(entry==null||computePicker==null)return 0;
  int position=computePicker.getSelectedItemPosition();
  if(position<0||position>=ModelLabComputePolicy.Mode.values().length)return 0;
  return compute.layers(ModelLabComputePolicy.Mode.values()[position],
    entry.id,BuildConfig.VULKAN_COMPILED);
 }
 /** Models are registered as decision providers, never as Android input owners.
  *  The runtime and checked GGUF files are app-scoped; no Activity is retained.
  */
 private String registerConsoleBrain(ModelLabStore.Entry choice)throws Exception{
  return registerConsoleBrain(choice,0); // auto-binding stays CPU by default
 }
 private String registerConsoleBrain(ModelLabStore.Entry choice,int requestedGpuLayers)throws Exception{
  if(!inUnifiedApk())return "Нейроагент NES доступен только в едином APK";
  if(requestedGpuLayers!=0&&requestedGpuLayers!=8&&requestedGpuLayers!=16)
   throw new IllegalArgumentException("Invalid agent GPU layer count");
  if(requestedGpuLayers>0&&!BuildConfig.VULKAN_COMPILED)
   throw new IllegalStateException("Vulkan backend not compiled");
  final int offload=requestedGpuLayers;
  final File weights=store.modelFile(choice);
  final File projector=choice.vision()?store.projectorFile(choice):null;
  final EmbeddedGgufRuntime runtime=engine;
  installConsoleBrain(choice,weights,projector,runtime,offload);
  // Persist the elected local role, not the GGUF bytes or any Android input rights.
  getApplicationContext().getSharedPreferences("localpilot-console-assignment",MODE_PRIVATE)
    .edit().putString("modelId",choice.id).apply();
  return "GGUF назначена NES (генерация ещё не проверена): "+choice.label+
    (offload==0?" · CPU":" · Vulkan GPU"+offload)+
    (projector==null?" · текстовое наблюдение":" · NES кадр + mmproj")+
    ". Открой NES, запусти встроенный homebrew и нажми «Нейроагент».";
 }
 /** Restore the last user-selected NES role after an Android process restart.
  * This validates actual local GGUF/mmproj bytes off-main-thread. CPU is chosen
  * after restart; GPU requires a fresh explicit opt-in.
  */
 public static void restoreConsoleBrain(android.content.Context context){
  if(context==null||!context.getPackageName().startsWith("org.localpilot.android"))return;
  final android.content.Context app=context.getApplicationContext();
  final String wanted=app.getSharedPreferences("localpilot-console-assignment",0)
   .getString("modelId",null);
  if(wanted==null||wanted.isBlank())return;
  Thread restore=new Thread(()->{
   try{
    final ModelLabStore catalog;
    final EmbeddedGgufRuntime runtime;
    synchronized(ModelLabActivity.class){
     if(unifiedStore==null)unifiedStore=new ModelLabStore(app);
     if(unifiedEngine==null)unifiedEngine=new EmbeddedGgufRuntime(app);
     catalog=unifiedStore;
     runtime=unifiedEngine;
    }
    ModelLabStore.Entry chosen=catalog.find(wanted);
    if(chosen==null)return;
    File weights=catalog.modelFile(chosen);
    File projector=chosen.vision()?catalog.projectorFile(chosen):null;
    if(!wanted.equals(app.getSharedPreferences("localpilot-console-assignment",0)
                         .getString("modelId",null)))return;
    installConsoleBrain(chosen,weights,projector,runtime,0);
   }catch(Exception|LinkageError invalid){
    // Broken or removed model must NEVER become a controller. No implicit
    // download and no synthetic action or success is performed here.
   }
  },"LocalPilot-Console-Model-Restore");
  restore.setDaemon(true);
  restore.start();
 }
 private static void installConsoleBrain(ModelLabStore.Entry choice,File weights,
                    File projector,EmbeddedGgufRuntime runtime,int offload){
  ConsoleAgentBridge.set(new ConsoleAgentBridge.Brain(){
   public String label(){return choice.label;}
   public boolean vision(){return projector!=null;}
   public void cancel(){runtime.requestCancel();}
   public void decide(String observation,byte[] png,ConsoleAgentBridge.Reply reply){
    try{
     if(runtime.busy())throw new IllegalStateException("GGUF engine busy");
     if(projector==null)runtime.consoleAction(weights,observation,offload,
       (answer,elapsed)->reply.complete(answer));
     else{
      NesFrameTransfer.verify(png);
      Bitmap frame=BitmapFactory.decodeByteArray(png,0,png.length);
      if(frame==null)throw new IllegalArgumentException("NES PNG decode rejected");
      int w=frame.getWidth(),h=frame.getHeight();
      if(w!=NesFrameTransfer.WIDTH||h!=NesFrameTransfer.HEIGHT){
       frame.recycle();throw new IllegalArgumentException("Unexpected NES frame size");
      }
      int[] pixels=new int[w*h];frame.getPixels(pixels,0,w,0,0,w,h);
      frame.recycle();
      byte[] rgb=new byte[w*h*3];
      for(int i=0;i<pixels.length;i++){
       rgb[3*i]=(byte)(pixels[i]>>>16);
       rgb[3*i+1]=(byte)(pixels[i]>>>8);
       rgb[3*i+2]=(byte)pixels[i];
      }
      runtime.consoleActionImage(weights,projector,observation,rgb,w,h,offload,
        (answer,elapsed)->reply.complete(answer));
     }
    }catch(Exception|LinkageError failure){reply.complete("");}
   }
  });
 }
 private void refresh(){
  if(closed||modelPicker==null)return;
  String selectedId=preferredModelId!=null?preferredModelId:
    selected()==null?getPreferences(MODE_PRIVATE)
    .getString("selected","builtin:qwen35"):selected().id;
  preferredModelId=null;
  List<ModelLabStore.Entry> current=store.list();
  displayed.clear();displayed.addAll(current);
  List<String> labels=new ArrayList<>();
  int selection=0;
  for(int i=0;i<current.size();i++){
   ModelLabStore.Entry e=current.get(i);
   labels.add(e.description());
   if(e.id.equals(selectedId))selection=i;
  }
  if(labels.isEmpty())labels.add("Добавь GGUF из памяти или Hugging Face");
  modelPicker.setAdapter(new ArrayAdapter<>(this,
    android.R.layout.simple_spinner_dropdown_item,labels));
  if(!displayed.isEmpty())modelPicker.setSelection(selection);
  modelStatus.setText("Каталог: "+displayed.size()+" моделей; "+
    (BuildConfig.PRELOADED_MODELS?"4 предзагружены в APK":"моделей в APK нет")+
    ". Память расходует только выбранная загружаемая модель.");
  if(!displayed.isEmpty())
   getPreferences(MODE_PRIVATE).edit().putString("selected",displayed.get(selection).id).apply();
 }
 private interface Task { String run()throws Exception; }
 private boolean task(String message,Task action,boolean refresh){
  if(closed)return false;
  if(engine.busy()){status.setText("Проводится генерация. Заверши текущий тест.");return false;}
  status.setText(message+"...");
  try{
   io.execute(()->{
    String outcome;
    try{outcome=action.run();}
    catch(Exception|LinkageError failure){
     outcome="Ошибка: "+failure.getClass().getSimpleName()+
       (failure.getMessage()==null?"":": "+failure.getMessage());
    }
    String finalOutcome=outcome;
    runOnUiThread(()->{
     if(closed)return;
     status.setText(finalOutcome);
     if(refresh)refresh();
    });
   });
   return true;
  }catch(java.util.concurrent.RejectedExecutionException e){
   status.setText("Операция завершена: экран закрыт");
   return false;
  }
 }
 private void title(LinearLayout parent,String value){
  TextView v=line(parent,value);v.setTextSize(20);
 }
 private TextView line(LinearLayout parent,String value){
  TextView text=new TextView(this);
  text.setText(value);text.setTextSize(15);
  text.setPadding(0,10,0,14);parent.addView(text);return text;
 }
 private void button(LinearLayout parent,String name,Runnable click){
  Button b=new Button(this);b.setText(name);b.setOnClickListener(v->click.run());
  parent.addView(b,new LinearLayout.LayoutParams(
    ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));
 }
 private String displayName(Uri uri){
  try(Cursor cursor=getContentResolver().query(uri,
     new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){
   if(cursor!=null&&cursor.moveToFirst())return cursor.getString(0);
  }catch(Exception ignored){}
  return "Imported GGUF";
 }
 @Override protected void onActivityResult(int code,int result,Intent data){
  super.onActivityResult(code,result,data);
  if(result!=RESULT_OK||data==null||data.getData()==null)return;
  Uri uri=data.getData();
  if(code==OPEN_IMAGE){loadImage(uri);return;}
  if(code==OPEN_MODEL||code==OPEN_PROJECTOR){
   ModelLabStore.Entry target=code==OPEN_PROJECTOR?selected():null;
   if(code==OPEN_PROJECTOR&&(target==null||target.bundled)){
    status.setText("Для mmproj выбери импортированную модель");return;
   }
   String name=displayName(uri),sha=sha256.getText().toString().trim();
   task("Импортирую GGUF из файлов",()->{
    ModelLabStore.Entry entry=store.importFile(uri,name,sha,
       target==null?null:target.id);
    if(inUnifiedApk())registerConsoleBrain(entry);
    return "Добавлено: "+entry.label+" · SHA256 "+entry.modelSha;
   },true);
  }
 }
 private void loadImage(Uri uri){
  revisions.newImage();
  rgb=null;nesFrameLoaded=false;
  try{
   BitmapFactory.Options size=new BitmapFactory.Options();size.inJustDecodeBounds=true;
   try(InputStream in=getContentResolver().openInputStream(uri)){
    BitmapFactory.decodeStream(in,null,size);
   }
   if(size.outWidth<=0||size.outHeight<=0||size.outWidth>20000||size.outHeight>20000)
    throw new IllegalArgumentException("Неподдерживаемые размеры фото");
   int sampling=1;
   while(Math.max(size.outWidth/sampling,size.outHeight/sampling)>640)sampling*=2;
   BitmapFactory.Options opts=new BitmapFactory.Options();opts.inSampleSize=sampling;
   Bitmap decoded;
   try(InputStream in=getContentResolver().openInputStream(uri)){
    decoded=BitmapFactory.decodeStream(in,null,opts);
   }
   if(decoded==null)throw new IllegalArgumentException("Фото не декодируется");
   applyBitmap(decoded,false);
  }catch(Exception|OutOfMemoryError failure){
   rgb=null;nesFrameLoaded=false;
   imageStatus.setText("Ошибка фото: "+failure.getClass().getSimpleName());
  }
 }
 private void applyBitmap(Bitmap decoded,boolean fromNes){
   int w=decoded.getWidth(),h=decoded.getHeight();
   if(w>640||h>640){
    float ratio=Math.min(640f/w,640f/h);
    Bitmap smaller=Bitmap.createScaledBitmap(decoded,Math.max(1,(int)(w*ratio)),
      Math.max(1,(int)(h*ratio)),true);
    decoded.recycle();decoded=smaller;w=decoded.getWidth();h=decoded.getHeight();
   }
   int[] rgba=new int[w*h];decoded.getPixels(rgba,0,w,0,0,w,h);
   byte[] bytes=new byte[w*h*3];
   for(int i=0;i<rgba.length;i++){
    bytes[3*i]=(byte)((rgba[i]>>>16)&255);
    bytes[3*i+1]=(byte)((rgba[i]>>>8)&255);
    bytes[3*i+2]=(byte)(rgba[i]&255);
   }
   rgb=bytes;width=w;height=h;nesFrameLoaded=fromNes;
   if(previewBitmap!=null){preview.setImageDrawable(null);previewBitmap.recycle();}
   previewBitmap=decoded;
   preview.setImageBitmap(decoded);preview.setVisibility(View.VISIBLE);
   imageStatus.setText("RGB "+w+"×"+h+" готово. EXIF не передаётся.");
 }
 @Override protected void onDestroy(){
  closed=true;
  downloadCancelled=true;
  rgb=null;
  if(preview!=null)preview.setImageDrawable(null);
  if(previewBitmap!=null)previewBitmap.recycle();
  // Interrupt pending downloads/imports instead of letting a discarded
  // Activity continue writing multi-gigabyte GGUF files after rotation.
  io.shutdownNow();
  super.onDestroy();
 }
}
