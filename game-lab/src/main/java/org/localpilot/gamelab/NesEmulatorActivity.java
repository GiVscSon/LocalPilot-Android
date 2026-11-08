package org.localpilot.gamelab;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.OpenableColumns;
import android.util.Base64;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicInteger;
import org.localpilot.shared.HubContract;
import org.localpilot.shared.NesExperience;
import java.util.ArrayList;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;

/**
 * NES CPU/PPU emulation: JSNES 2.1.0 execution inside a
 * separate local Android WebView. User-selected ROMs never leave this device.
 * All inputs are OS-delivered touch events, not artificial ACK or rewards.
 */
public final class NesEmulatorActivity extends Activity {
 private static final int CHOOSE_ROM=101;
 private static final int MAX_ROM=3*1024*1024;
 private static final String START_URL="file:///android_asset/nes/index.html";
 private static final String[] GAMEPAD={"UP","LEFT","DOWN","RIGHT","SELECT","START","B","A"};
 private WebView web;
 private TextView state;
 private Button demoButton,aButton,bButton;
 private boolean resumed;
 private boolean pageLoaded;
 private int frames,hash;
 private long lastInputAt;
 private boolean dendy;
 private String episodeRom="",episodeSession="";
 private final ArrayList<NesExperience.Frame> episodeFrames=new ArrayList<>();
 private String pendingEpisodeExport;
 private static final int EXPORT_EPISODE=102;
 private static final java.util.concurrent.ExecutorService EPISODES=java.util.concurrent.Executors.newSingleThreadExecutor();
 private final AtomicInteger touches=new AtomicInteger();
 @Override public void onCreate(Bundle bundle){
  super.onCreate(bundle);
  if(bundle!=null)pendingEpisodeExport=bundle.getString("pending_nes_export");
  dendy=getIntent().getBooleanExtra("dendy",false);
  LinearLayout root=new LinearLayout(this);
  root.setOrientation(LinearLayout.VERTICAL);GameLabInsets.install(this,root,8);
  root.setBackgroundColor(HubContract.BACKGROUND);
  TextView label=new TextView(this);
  label.setText(dendy?"LocalPilot · Денди (50 Гц)":"LocalPilot · NES");
  label.setTextColor(HubContract.TEXT);label.setTextSize(21);root.addView(label);
  state=new TextView(this);state.setTextColor(HubContract.WARNING);
  state.setText("Выберите свой NES ROM. Операции исполняются внутри Android, без сети.");
  root.addView(state);
  Button exportEpisode=new Button(this);exportEpisode.setText("Экспорт опыта NES (.lpn)");root.addView(exportEpisode);
  exportEpisode.setOnClickListener(v->{
   if(episodeFrames.isEmpty()){state.setText("Сначала запустите ROM и выполните несколько действий");return;}
   String text=NesExperience.encode(new NesExperience.Episode(episodeRom,new ArrayList<>(episodeFrames)));
   try{
    pendingEpisodeExport=NesExperience.sha256(text.getBytes(StandardCharsets.UTF_8))+".pending-export";
    Files.write(new java.io.File(getFilesDir(),pendingEpisodeExport).toPath(),text.getBytes(StandardCharsets.UTF_8));
   }catch(IOException error){pendingEpisodeExport=null;state.setText("Не удалось подготовить экспорт NES");return;}
   startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("text/plain").addCategory(Intent.CATEGORY_OPENABLE)
     .putExtra(Intent.EXTRA_TITLE,"localpilot-nes-"+episodeRom.substring(0,12)+".lpn"),EXPORT_EPISODE);
  });
  Button open=new Button(this);open.setText("Выбрать картридж (.nes)");
  root.addView(open);
  open.setOnClickListener(v->{
   Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*")
     .addCategory(Intent.CATEGORY_OPENABLE);
   startActivityForResult(pick,CHOOSE_ROM);
  });
  demoButton=new Button(this);Button demo=demoButton;
  demo.setText("Запустить тестовую NES-игру");
  root.addView(demo);
  demo.setOnClickListener(v->launchRom(HomebrewNes.cartridge()));
  web=new WebView(this);
  web.setBackgroundColor(HubContract.BACKGROUND);
  web.getSettings().setJavaScriptEnabled(true);
  web.getSettings().setDomStorageEnabled(false);
  web.getSettings().setAllowFileAccess(true); // only packaged asset file page
  web.getSettings().setAllowContentAccess(false);
  web.getSettings().setAllowFileAccessFromFileURLs(false);
  web.getSettings().setAllowUniversalAccessFromFileURLs(false);
  web.getSettings().setBlockNetworkLoads(true);
  web.getSettings().setMediaPlaybackRequiresUserGesture(false);
  web.setWebViewClient(new WebViewClient(){
   @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){
    return true; // Never navigate away from the bundled NES application.
   }
   @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){
    Uri uri=request.getUrl();
    if(uri==null||!"file".equals(uri.getScheme())||
      !START_URL.equals(uri.toString())&&
      !"file:///android_asset/nes/jsnes.min.js".equals(uri.toString())&&
      !"file:///android_asset/nes/latched-buttons.js".equals(uri.toString())&&
      !"file:///android_asset/nes/nes-observation.js".equals(uri.toString()))return
       new WebResourceResponse("text/plain","UTF-8",new java.io.ByteArrayInputStream(new byte[0]));
    return super.shouldInterceptRequest(view,request);
   }
   @Override public void onPageFinished(WebView view,String url){
    pageLoaded=START_URL.equals(url);
    if(!pageLoaded)state.setText("ROM остановлен: ненадёжный источник страницы");
   }
  });
  web.addJavascriptInterface(new Object(){
   @JavascriptInterface public void onFrame(int count,int pixelsHash){
    runOnUiThread(()->{
     if(!resumed)return;
     frames=count;hash=pixelsHash;
     publishEvidence();
     if(count%60==0)
      state.setText("Кадров "+frames+" · экран CRC="+Integer.toHexString(hash)
       +" · касаний "+touches.get()+" · только эмуляция (не результат Маркета)");
    });
   }
   @JavascriptInterface public void onObservation(String rom,int frame,int pixelsHash,int buttons,String luma){
    if(luma==null||luma.length()>512)return;
    runOnUiThread(()->{
     if(!resumed||!episodeSession.equals(rom)||episodeFrames.size()>=NesExperience.MAX_ROWS)return;
     if(!episodeFrames.isEmpty()&&frame<=episodeFrames.get(episodeFrames.size()-1).frame())return;
     try{episodeFrames.add(new NesExperience.Frame(frame,pixelsHash,buttons,
       java.util.Arrays.stream(luma.split(",",-1)).mapToInt(Integer::parseInt).toArray()));}
     catch(IllegalArgumentException invalid){state.setText("Наблюдение NES отклонено: неверная схема");}
    });
   }
   @JavascriptInterface public void onStatus(String message){
    runOnUiThread(()->state.setText(message));
   }
  },"GameLabBridge");
  root.addView(web,new LinearLayout.LayoutParams(-1,0,1));
  for(int row=0;row<2;row++){
   LinearLayout controls=new LinearLayout(this);root.addView(controls);
   for(int i=row*4;i<row*4+4;i++){
    String name=GAMEPAD[i];
    Button btn=new Button(this);btn.setText(name);btn.setTextSize(11);
    if("A".equals(name))aButton=btn;
    if("B".equals(name))bButton=btn;
    btn.setContentDescription("NES "+name+" button");
    btn.setOnTouchListener((view,event)->{
     int type=event.getActionMasked();
     if(type==MotionEvent.ACTION_DOWN){
      gamepad(name,true);
      touches.incrementAndGet();lastInputAt=SystemClock.elapsedRealtime();
      publishEvidence();
      return true;
     }
     if(type==MotionEvent.ACTION_UP||type==MotionEvent.ACTION_CANCEL){
      gamepad(name,false);return true;
     }
     return true;
    });
    controls.addView(btn,new LinearLayout.LayoutParams(0,-2,1));
   }
  }
  TextView note=new TextView(this);root.addView(note);
  note.setTextColor(HubContract.SECONDARY);note.setTextSize(11);
  note.setText(dendy?"50 Гц: программная совместимость, не точное железо Денди.":
   "JSNES 2.1.0 · Apache-2.0 · настоящее выполнение кода NES");
  setContentView(root);
  root.requestApplyInsets();
  root.post(this::publishEvidence);
  web.loadUrl(START_URL);
 }
 private void publishEvidence(){
  if(resumed&&demoButton!=null)
   GameLabEvidenceProvider.nes(dendy,frames,hash,touches.get(),demoButton,aButton,bButton);
 }
 private void launchRom(byte[] rom){
  if(!pageLoaded||web==null||rom==null||rom.length>MAX_ROM){
   state.setText("NES-ядро ещё не загружено или ROM недоступен");return;
  }
  String encoded=Base64.encodeToString(rom,Base64.NO_WRAP);
  saveEpisode();episodeFrames.clear();episodeRom=NesExperience.sha256(rom);episodeSession=episodeRom+"-"+Long.toHexString(SystemClock.elapsedRealtimeNanos());
  frames=hash=0;touches.set(0);
  web.evaluateJavascript("window.retroLoadRom("+org.json.JSONObject.quote(encoded)+","
    +(dendy?"true":"false")+","+org.json.JSONObject.quote(episodeSession)+")",success->{
   if(!"true".equals(success))state.setText("NES ROM не запущен. Проверьте mapper и ядро.");
  });
 }
 private void gamepad(String key,boolean down){
  if(web==null||!pageLoaded)return;
  web.evaluateJavascript("window.retroButton("+org.json.JSONObject.quote(key)+","
   +(down?"true":"false")+")",null);
 }
 private String filename(Uri uri){
  try(Cursor cursor=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){
   if(cursor!=null&&cursor.moveToFirst())return cursor.getString(0);
  }catch(RuntimeException ignored){}
  return "";
 }
 @Override protected void onActivityResult(int req,int result,Intent data){
  super.onActivityResult(req,result,data);
  if(req==EXPORT_EPISODE){
   if(result==RESULT_OK&&data!=null&&data.getData()!=null&&pendingEpisodeExport!=null){
    final String id=pendingEpisodeExport;final Uri uri=data.getData();
    EPISODES.execute(()->{
     if(!id.matches("[a-f0-9]{64}\\.pending-export"))return;
     java.nio.file.Path snapshot=new java.io.File(getFilesDir(),id).toPath();
     try(java.io.OutputStream out=getContentResolver().openOutputStream(uri)){
      if(out==null||Files.size(snapshot)>NesExperience.MAX_BYTES)throw new IOException("invalid export destination/snapshot");
      byte[] bytes=Files.readAllBytes(snapshot);NesExperience.decode(new String(bytes,StandardCharsets.UTF_8));out.write(bytes);
      Files.deleteIfExists(snapshot);
     }catch(IOException|IllegalArgumentException error){runOnUiThread(()->state.setText("Не удалось экспортировать эпизод NES; приватная копия сохранена"));}
    });
   }
   pendingEpisodeExport=null;return;
  }
  if(req!=CHOOSE_ROM||result!=RESULT_OK||data==null||data.getData()==null)return;
  Uri uri=data.getData();
  String name=filename(uri);
  try(InputStream in=getContentResolver().openInputStream(uri)){
   if(in==null)throw new IOException("no cartridge reader");
   ByteArrayOutputStream memory=new ByteArrayOutputStream();
   byte[] part=new byte[8192];int amount,total=0;
   while((amount=in.read(part))!=-1){
    total+=amount;
    if(total>MAX_ROM)throw new IOException("cartridge exceeds bound");
    memory.write(part,0,amount);
   }
   byte[] rom=memory.toByteArray();
   if(!RetroEmulatorCatalog.inspect(dendy?RetroEmulatorCatalog.System.DENDY:
      RetroEmulatorCatalog.System.NES,name,rom).recognized())
    throw new IOException("iNES/NES2 cartridge header not recognized");
   if(!pageLoaded)throw new IOException("emulator page not yet loaded");
   launchRom(rom);
  }catch(IOException|RuntimeException exception){
   state.setText("ROM не запущен: "+exception.getClass().getSimpleName());
   Toast.makeText(this,"Неподдерживаемый или слишком большой файл игры",Toast.LENGTH_LONG).show();
  }
 }
 private void saveEpisode(){
  if(episodeFrames.isEmpty()||episodeRom.isEmpty())return;
  final String text=NesExperience.encode(new NesExperience.Episode(episodeRom,new ArrayList<>(episodeFrames)));
  final String id=NesExperience.sha256(text.getBytes(StandardCharsets.UTF_8));final java.io.File root=getFilesDir();
  EPISODES.execute(()->{try{
   java.nio.file.Path folder=new java.io.File(root,"nes-lab-episodes-v1").toPath();Files.createDirectories(folder);
   java.nio.file.Path target=folder.resolve(id+".lpn");if(Files.exists(target))return;
   try(var files=Files.list(folder)){if(files.filter(p->p.toString().endsWith(".lpn")).count()>=8)return;}
   java.nio.file.Path temporary=Files.createTempFile(folder,"nes-",".pending");
   try{Files.write(temporary,text.getBytes(StandardCharsets.UTF_8));Files.move(temporary,target,java.nio.file.StandardCopyOption.ATOMIC_MOVE);}
   finally{Files.deleteIfExists(temporary);}
  }catch(IOException ignored){/* Manual export remains available; no game reward is minted. */}});
 }
 @Override protected void onPause(){
  saveEpisode();resumed=false;GameLabEvidenceProvider.clear("nes");
  if(web!=null&&pageLoaded)web.evaluateJavascript("window.retroPause()",null);
  if(web!=null)web.onPause();
  super.onPause();
 }
 @Override protected void onResume(){
  super.onResume();resumed=true;
  if(demoButton!=null)demoButton.post(this::publishEvidence);
  if(web!=null){web.onResume();
   if(pageLoaded)web.evaluateJavascript("window.retroResume()",null);
  }
 }
 @Override protected void onSaveInstanceState(Bundle out){out.putString("pending_nes_export",pendingEpisodeExport);super.onSaveInstanceState(out);}
 @Override protected void onDestroy(){
  if(web!=null){web.evaluateJavascript("window.retroStop()",null);
   web.removeJavascriptInterface("GameLabBridge");
   web.destroy();web=null;
  }
  super.onDestroy();
 }
}
