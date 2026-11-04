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
import java.io.File;
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
 private RetroEmulatorCatalog.Inspection inspected;
 private Uri selectedUri;
 @Override public void onCreate(Bundle state){
  super.onCreate(state);
  LinearLayout content=new LinearLayout(this);
  content.setOrientation(LinearLayout.VERTICAL);
  content.setPadding(24,24,24,24);
  TextView title=new TextView(this);
  title.setText("GameLab · DOS / NES / Денди");
  title.setTextSize(22);
  content.addView(title);
  TextView note=new TextView(this);
  note.setText("Системы подключаются через Libretro. Для запуска требуются " +
    "скомпилированное ядро и фронтенд в самом APK. Этот экран проверяет игры, " +
    "но без ядра не выдаёт проверку за эмуляцию.");
  content.addView(note);
  platforms=new Spinner(this);
  String[] labels=new String[RetroEmulatorCatalog.System.values().length];
  for(int i=0;i<labels.length;i++)
   labels[i]=RetroEmulatorCatalog.System.values()[i].label;
  platforms.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,labels));
  content.addView(platforms);
  Button pick=new Button(this);
  pick.setText("Выбрать собственный ROM / DOS-файл");
  pick.setOnClickListener(v->{
   Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT)
      .setType("*/*").addCategory(Intent.CATEGORY_OPENABLE);
   startActivityForResult(intent,IMPORT);
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
  setContentView(content);report();
 }
 private RetroEmulatorCatalog.System selected(){
  return RetroEmulatorCatalog.System.values()[platforms.getSelectedItemPosition()<0?0:
    platforms.getSelectedItemPosition()];
 }
 private String coreStatus(){
  String required=selected().nativeLibraryName();
  File file=new File(getApplicationInfo().nativeLibraryDir,required);
  if(file.isFile())return "Обнаружено ядро "+required+"; требуется ещё запуск через LibretroDroid.";
  return "Ядро "+required+" не включено в этот APK. Эмуляция недоступна.";
 }
 private void report(){
  if(details==null)return;
  RetroEmulatorCatalog.System type=selected();
  String region=type.coreOptionKey().isEmpty()?"DOS":
    type.coreOptionKey()+"="+type.fceummRegion;
  details.setText("Платформа: "+type.label+"\nРежим: "+region+"\n"+coreStatus()
    +"\nROM: "+(selectedUri==null?"не выбран":inspected!=null&&inspected.recognized()
      ?"заголовок распознан":"ошибка проверки")
    +"\n\nИсточник ядра: FCEUmm / DOSBox Pure (GPL). ROM и программы в APK не входят."
    +"\n\nПолноценное тестирование появится только после сборки native core и " +
      "проверки кадров/команд на устройстве.");
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
