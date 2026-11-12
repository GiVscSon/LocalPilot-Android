package org.localpilot.android;

import android.content.Context;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONArray;
import org.json.JSONObject;
import org.localpilot.shared.HarnessTelemetry;

/** App-private, bounded, append-only event journal.
 * Never fabricates gameplay reward or training provenance.
 * All disk IO occurs on a single worker; session controller remains one writer.
 */
public final class HarnessRunJournal implements HarnessTelemetry.Sink {
 private static final int MAX_ROWS=350;
 private static final long MAX_BYTES=3L*1024*1024;
 private static volatile HarnessRunJournal instance;
 private final ArrayDeque<JSONObject> recent=new ArrayDeque<>();
 private final ExecutorService io=Executors.newSingleThreadExecutor(r->{
  Thread t=new Thread(r,"LocalPilot-Harness-Audit");t.setDaemon(true);return t;
 });
 private final File file;
 private volatile boolean healthy=true, loaded;
 private volatile String error="";
 private HarnessRunJournal(Context context){
  file=new File(context.getNoBackupFilesDir(),"localpilot-harness-events-v1.jsonl");
  io.execute(this::load);
 }
 public static HarnessRunJournal get(Context context){
  HarnessRunJournal result=instance;
  if(result==null)synchronized(HarnessRunJournal.class){
   result=instance;
   if(result==null)instance=result=new HarnessRunJournal(context.getApplicationContext());
  }
  return result;
 }
 private void add(JSONObject event){
  synchronized(recent){
   while(recent.size()>=MAX_ROWS)recent.removeFirst();
   recent.addLast(event);
  }
 }
 private void load(){
  if(!file.isFile()){loaded=true;return;}
  try(BufferedReader in=new BufferedReader(new InputStreamReader(
       new FileInputStream(file),StandardCharsets.UTF_8))){
   String line;
   while((line=in.readLine())!=null){
    if(line.length()>0&&line.length()<2048){
     try{add(new JSONObject(line));}catch(Exception ignored){}
    }
   }
   if(file.length()>=MAX_BYTES){healthy=false;error="Журнал заполнен, экспортируйте данные"; }
  }catch(Exception failure){
   healthy=false;error="Ошибка чтения журнала: "+failure.getClass().getSimpleName();
  }finally{loaded=true;}
 }
 @Override public boolean healthy(){return healthy;}
 @Override public void accept(HarnessTelemetry.Event event){
  if(!healthy)return;
  JSONObject row=new JSONObject();
  try{
   row.put("schema","localpilot.harness.event.v1");
   row.put("timestamp",event.timeMs);
   row.put("episodeId",event.episodeId);
   row.put("modelId",event.modelId);
   row.put("game",event.game);
   row.put("stage",event.stage);
   row.put("status",event.status);
   row.put("detail",event.detail);
   row.put("latencyMs",event.latencyMs);
   row.put("provenance","REAL_JSNES");
   row.put("reward",JSONObject.NULL);
   row.put("goalConfirmed",false);
  }catch(Exception ignored){healthy=false;error="Ошибка сериализации аудита";return;}
  io.execute(()->{
   if(!healthy)return;
   try{
    byte[] content=(row.toString()+"\n").getBytes(StandardCharsets.UTF_8);
    if(file.length()+content.length>MAX_BYTES){
     healthy=false;error="Лимит журнала 3 МБ, экспортируйте данные";return;
    }
    try(FileOutputStream out=new FileOutputStream(file,true)){
     out.write(content);out.getFD().sync();
    }
    add(row);
   }catch(Exception failure){
    healthy=false;error="Не удалось сохранить аудит: "+failure.getClass().getSimpleName();
   }
  });
 }
 public JSONObject summary(){
  JSONObject data=new JSONObject();
  try{
   data.put("loaded",loaded);
   data.put("healthy",healthy);
   data.put("error",error);
   data.put("fileBytes",file.length());
   data.put("maxBytes",MAX_BYTES);
   data.put("events",recent().length());
  }catch(Exception ignored){}
  return data;
 }
 public JSONArray recent(){
  JSONArray data=new JSONArray();
  synchronized(recent){for(JSONObject row:recent)data.put(row);}
  return data;
 }
 public byte[] export(){
  // Do not trim or delete the recorded evidence during export.
  try(FileInputStream in=new FileInputStream(file)){
   if(file.length()>MAX_BYTES)throw new IllegalStateException("Journal exceeds quota");
   return in.readAllBytes();
  }catch(java.io.FileNotFoundException missing){return new byte[0];}
  catch(Exception e){throw new IllegalStateException("Cannot export audit: "+e.getClass().getSimpleName());}
 }
}
