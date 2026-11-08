package org.localpilot.android;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import org.localpilot.android.learning.*;

/** User-facing LAB-only model library. No Android input or real-policy activation. */
public final class ModelLibraryActivity extends Activity {
 private static final int IMPORT=81,EXPORT=82,NES_IMPORT=83;
 private static final java.util.concurrent.ExecutorService WORKER=Executors.newSingleThreadExecutor();
 private final AtomicBoolean cancel=new AtomicBoolean();
 private final List<View> controls=new ArrayList<>();
 private Spinner profiles,versions;private TextView status;
 private List<LabModelStore.Version> history=List.of();private LabModelStore store;
 private boolean busy,destroyed;private String requestedGame,requestedHash;
 private interface Work {String run(TaskSchema.GameId game)throws Exception;}
 @Override public void onCreate(Bundle state){
  super.onCreate(state);
  if(state!=null){requestedGame=state.getString("game");requestedHash=state.getString("hash");}
  LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(16,12,16,12);
  ScrollView page=new ScrollView(this);page.addView(box);setContentView(page);
  page.setOnApplyWindowInsetsListener((v,insets)->{
   android.graphics.Insets safe=insets.getInsets(android.view.WindowInsets.Type.systemBars()|android.view.WindowInsets.Type.displayCutout());
   v.setPadding(safe.left,safe.top,safe.right,safe.bottom);return insets;
  });page.requestApplyInsets();
  TextView title=new TextView(this);title.setText("Библиотека моделей · 16 профилей");title.setTextSize(22);box.addView(title);
  TextView help=new TextView(this);help.setText("Все версии здесь — лабораторные. Импорт, обучение и откат меняют только выбранную LAB-модель. Реальные жесты и игровые модели не активируются. DOS/SEGA — синтетические профили, эмуляторы не включены.");box.addView(help);
  profiles=new Spinner(this);String[] names=Arrays.stream(TaskSchema.GameId.values()).map(g->g.title).toArray(String[]::new);
  profiles.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,names));box.addView(profiles);controls.add(profiles);
  if(state!=null)profiles.setSelection(Math.max(0,Math.min(15,state.getInt("profile",0))));
  versions=new Spinner(this);box.addView(versions);controls.add(versions);
  button(box,"Импортировать совместимую версию (.zip)",()->{
   requestedGame=game().name();requestedHash=null;
   startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE),IMPORT);
  });
  button(box,"Экспортировать выбранную версию",()->{
   if(history.isEmpty()){toast("Версия недоступна");return;}
   requestedGame=game().name();requestedHash=history.get(versions.getSelectedItemPosition()).sha256();
   startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/zip").addCategory(Intent.CATEGORY_OPENABLE)
     .putExtra(Intent.EXTRA_TITLE,game().id+"-"+requestedHash.substring(0,12)+".zip"),EXPORT);
  });
  button(box,"Выбрать версию / откат LAB",()->{
   if(history.isEmpty())return;String hash=history.get(versions.getSelectedItemPosition()).sha256();
   runTask(g->{store.select(g,hash);return "Выбрана LAB-версия "+hash;});
  });
  button(box,"Оценить на отдельных seed",()->runTask(g->store.evaluate(g,987654321L)));
  button(box,"Обучить выбранный профиль",()->runTask(g->store.train(g,20261108L).report()));
  button(box,"Обучить все 16 профилей",()->runTask(g->{
   StringBuilder report=new StringBuilder("SIMULATED_GAME · по отдельности для каждого профиля\n");
   for(TaskSchema.GameId target:TaskSchema.GameId.values()){
    if(cancel.get())return report+"\nОчередь остановлена";
    report.append(target.title).append(": ").append(store.train(target,20261108L+target.ordinal()*31L).report()).append('\n');
   }return report.toString();
  }));
  button(box,"Обучить NES по эпизоду (.lpn)",()->{
   requestedGame=game().name();
   startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE),NES_IMPORT);
  });
  Button stop=new Button(this);stop.setText("Остановить очередь обучения");box.addView(stop);
  stop.setOnClickListener(v->{cancel.set(true);status.setText("Очередь остановится после текущего ограниченного задания.");});
  status=new TextView(this);status.setTextIsSelectable(true);status.setTextSize(13);box.addView(status);
  profiles.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
   public void onNothingSelected(AdapterView<?> p){}
   public void onItemSelected(AdapterView<?> p,View v,int at,long id){if(store!=null&&!busy)runTask(g->"Профиль: "+g.title);}
  });
  runTask(g->{
   store=new LabModelStore(getFilesDir());
   for(TaskSchema.GameId target:TaskSchema.GameId.values())store.ensureBundled(target,name->getAssets().open(name));
   return "16 профилей загружены. Контрольные суммы и схемы проверены.";
  });
 }
 private TaskSchema.GameId game(){return TaskSchema.GameId.values()[profiles.getSelectedItemPosition()];}
 private void button(LinearLayout box,String text,Runnable action){Button b=new Button(this);b.setText(text);b.setOnClickListener(v->action.run());box.addView(b);controls.add(b);}
 private void toast(String text){Toast.makeText(this,text,Toast.LENGTH_LONG).show();}
 private void runTask(Work work){
  if(busy){toast("Дождитесь текущего задания");return;}
  busy=true;cancel.set(false);controls.forEach(v->v.setEnabled(false));status.setText("Проверка лабораторных моделей…");
  TaskSchema.GameId target=game();
  WORKER.execute(()->{
   String message;List<LabModelStore.Version> loaded=List.of();
   try{message=work.run(target);loaded=store.versions(target);}
   catch(Exception error){message="Операция отклонена: "+error.getMessage();}
   final String result=message;final List<LabModelStore.Version> list=loaded;
   runOnUiThread(()->{
    if(destroyed)return;busy=false;history=list;controls.forEach(v->v.setEnabled(true));
    String[] labels=list.stream().map(v->(v.valid()?(v.selected()?"✓ ":""):"Повреждена · ")+v.sha256().substring(0,16)+" · "+v.bytes()+" байт").toArray(String[]::new);
    versions.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,labels));
    for(int i=0;i<list.size();i++)if(list.get(i).selected())versions.setSelection(i);
    status.setText(result+"\n\n"+target.mechanicsVersion+"\n"+target.observationSchema+"\n"+target.actionSchema+"\nВерсии: "+list.size()+"/"+LabModelStore.MAX_VERSIONS+
      "\nSIMULATED_GAME · перенос в настоящую игру не разрешён");
   });
  });
 }
 @Override protected void onActivityResult(int request,int result,Intent data){
  super.onActivityResult(request,result,data);
  if((request!=IMPORT&&request!=EXPORT&&request!=NES_IMPORT)||result!=RESULT_OK||data==null||data.getData()==null)return;
  if(requestedGame==null){toast("Контекст операции потерян; повторите выбор файла");return;}
  final TaskSchema.GameId target;
  try{target=TaskSchema.GameId.valueOf(requestedGame);}catch(IllegalArgumentException e){toast("Неверный профиль");return;}
  final android.net.Uri uri=data.getData();final String hash=requestedHash;
  runTask(ignored->{
   if(request==NES_IMPORT){
    try(InputStream in=getContentResolver().openInputStream(uri)){
     if(in==null)throw new IOException("Нет доступа к эпизоду");
     ByteArrayOutputStream memory=new ByteArrayOutputStream();byte[] chunk=new byte[8192];int amount;
     while((amount=in.read(chunk))!=-1){if(memory.size()+amount>org.localpilot.shared.NesExperience.MAX_BYTES)throw new IOException("Эпизод слишком велик");memory.write(chunk,0,amount);}
     byte[] bytes=memory.toByteArray();
     if(bytes.length>org.localpilot.shared.NesExperience.MAX_BYTES)throw new IOException("Эпизод слишком велик");
     var episode=org.localpilot.shared.NesExperience.decode(new String(bytes,java.nio.charset.StandardCharsets.UTF_8));
     var policy=new org.localpilot.shared.NesExperience.Policy(episode);
     java.nio.file.Path dir=new File(getFilesDir(),"nes-lab-policies-v1").toPath();java.nio.file.Files.createDirectories(dir);
     java.nio.file.Path pending=java.nio.file.Files.createTempFile(dir,"episode-",".pending");
     try{
      java.nio.file.Files.write(pending,bytes);
      String id=org.localpilot.shared.NesExperience.sha256(bytes);
      java.nio.file.Path version=dir.resolve(id+".lpn");
      if(!java.nio.file.Files.exists(version)){
       try(var existing=java.nio.file.Files.list(dir)){if(existing.filter(p->p.toString().endsWith(".lpn")).count()>=16)throw new IOException("Лимит NES-версий достигнут");}
       java.nio.file.Files.move(pending,version,java.nio.file.StandardCopyOption.ATOMIC_MOVE);
      }
      return policy.report();
     }finally{java.nio.file.Files.deleteIfExists(pending);}
    }
   }
   if(request==IMPORT){try(InputStream in=getContentResolver().openInputStream(uri)){
    if(in==null)throw new IOException("Нет доступа к файлу");return "Импорт LAB: "+store.importVersion(target,in);
   }}else{if(hash==null)throw new IOException("Версия не выбрана");
    try(OutputStream out=getContentResolver().openOutputStream(uri)){
     if(out==null)throw new IOException("Нет доступа к файлу");store.exportVersion(target,hash,out);return "Экспортирована версия "+hash;
    }
   }
  });
 }
 @Override protected void onSaveInstanceState(Bundle out){out.putInt("profile",profiles.getSelectedItemPosition());out.putString("game",requestedGame);out.putString("hash",requestedHash);super.onSaveInstanceState(out);}
 @Override protected void onStop(){cancel.set(true);super.onStop();}
 @Override protected void onDestroy(){destroyed=true;cancel.set(true);super.onDestroy();}
}
