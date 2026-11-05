package org.localpilot.gamelab;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import java.io.IOException;
import java.io.InputStream;

/**
 * Honest ROM/core preflight for the future in-app libretro renderer.
 *
 * This activity deliberately does NOT pretend to emulate a ROM before a
 * tested GPL-compliant native core and frontend are present in the APK.
 */
public final class RetroLibraryActivity extends Activity {
 private static final int IMPORT=83;
 private Spinner platforms;
 private TextView details;
 private Button launchButton;
 private RetroEmulatorCatalog.Inspection inspected;
 private Uri selectedUri;
 @Override public void onCreate(Bundle state){
  super.onCreate(state);
  LinearLayout content=new LinearLayout(this);
  content.setOrientation(LinearLayout.VERTICAL);
  GameLabInsets.install(this,content,16);
  TextView title=new TextView(this);
  title.setText("GameLab · DOS / NES / Денди");
  title.setTextSize(22);
  content.addView(title);
  TextView note=new TextView(this);
  note.setText("NES и Денди: настоящий локальный JSNES-эмулятор, управление Android. " +
   "DOS пока в разработке: проверка файла не запускает DOSBox. " +
   "Картриджи и DOS-программы не входят в APK.");
  content.addView(note);
  platforms=new Spinner(this);
  String[] labels=new String[RetroEmulatorCatalog.System.values().length];
  for(int i=0;i<labels.length;i++)
   labels[i]=RetroEmulatorCatalog.System.values()[i].label;
  platforms.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,labels));
  content.addView(platforms);
  launchButton=new Button(this);Button pick=launchButton;
  pick.setText("Играть в NES / Денди или проверить DOS-файл");
  pick.setOnClickListener(v->{
   if(selected().runnableInGameLab()){
    startActivity(new Intent(this,NesEmulatorActivity.class)
      .putExtra("dendy",selected()==RetroEmulatorCatalog.System.DENDY));
   }else{
    Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT)
      .setType("*/*").addCategory(Intent.CATEGORY_OPENABLE);
    startActivityForResult(intent,IMPORT);
   }
  });
  content.addView(pick);
  Button inspect=new Button(this);
  inspect.setText("Проверить эмуляторное ядро");
  inspect.setOnClickListener(v->report());
  content.addView(inspect);
  details=new TextView(this);
  content.addView(details);
  platforms.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
   @Override public void onItemSelected(android.widget.AdapterView<?> view,View child,int position,long id){
    inspected=null;selectedUri=null;report();
   }
   @Override public void onNothingSelected(android.widget.AdapterView<?> view){}
  });
  setContentView(content);content.requestApplyInsets();report();
  content.post(this::publishEvidence);
 }
 private RetroEmulatorCatalog.System selected(){
  return RetroEmulatorCatalog.System.values()[platforms.getSelectedItemPosition()<0?0:
    platforms.getSelectedItemPosition()];
 }
 private String coreStatus(){
  if(selected().runnableInGameLab()){
   try(java.io.InputStream in=getAssets().open("nes/jsnes.min.js")){
    if(in.read()>=0)return "JSNES 2.1.0 включён: NES исполняется в Android WebView.";
   }catch(IOException ignored){}
   return "JSNES отсутствует: запуск NES невозможен.";
  }
  return "DOSBox Pure пока не подключён: DOS-файл можно проверить, запуск недоступен.";
 }
 private void report(){
  if(details==null)return;
  RetroEmulatorCatalog.System type=selected();
  String region=type==RetroEmulatorCatalog.System.DOS?"DOSBox ещё не установлен":
    type==RetroEmulatorCatalog.System.DENDY?"50 Гц, совместимость без точных таймингов Денди":
      "NES NTSC ~60 кадров/с";
  details.setText("Платформа: "+type.label+"\nРежим: "+region+"\n"+coreStatus()
    +"\nROM: "+(selectedUri==null?"не выбран":inspected!=null&&inspected.recognized()
      ?"заголовок распознан":"ошибка проверки")
    +"\n\nNES: JSNES 2.1.0 (Apache-2.0). DOS: запланирован DOSBox Pure (GPLv2)."
    +"\nROM и игры не входят в APK. Результаты эмуляторов не заменяют проверку игр Маркета.");
 }
 private void publishEvidence(){
  if(launchButton!=null)GameLabEvidenceProvider.retro(selected(),launchButton);
 }
 @Override protected void onResume(){
  super.onResume();
  if(launchButton!=null)launchButton.post(this::publishEvidence);
 }
 @Override protected void onPause(){
  GameLabEvidenceProvider.clear("retro");
  super.onPause();
 }
 private String fileName(Uri uri){
  try(Cursor cursor=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},
      null,null,null)){
   if(cursor!=null&&cursor.moveToFirst())return cursor.getString(0);
  }catch(RuntimeException ignored){}
  return "";
 }
 @Override protected void onActivityResult(int request,int result,Intent data){
  super.onActivityResult(request,result,data);
  if(request!=IMPORT||result!=RESULT_OK||data==null||data.getData()==null)return;
  Uri uri=data.getData();
  String name=fileName(uri);
  try(InputStream in=getContentResolver().openInputStream(uri)){
   inspected=RetroEmulatorCatalog.inspect(selected(),name,in);
   if(!inspected.recognized()){
    selectedUri=null;
    new AlertDialog.Builder(this).setTitle("Неподходящий образ")
       .setMessage("Не распознан формат для "+selected().label+".")
       .setPositiveButton("OK",null).show();
   }else selectedUri=uri;
  }catch(IOException|IllegalArgumentException e){
   selectedUri=null;inspected=null;
   new AlertDialog.Builder(this).setMessage("Ошибка безопасного чтения образа")
     .setPositiveButton("OK",null).show();
  }
  report();
 }
}
