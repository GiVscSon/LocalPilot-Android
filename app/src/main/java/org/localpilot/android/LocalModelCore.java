package org.localpilot.android;

import android.os.Handler;
import android.os.Looper;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Primary LOCAL language-model backend.
 *
 * Bound to same-device 127.0.0.1 ONLY. Never sends images, Accessibility
 * contents, app/window names, target coordinates or action tickets.
 * Neither callback nor this class can dispatch game input.
 */
public final class LocalModelCore {
 public interface Callback { void complete(String message); }
 private final Handler main=new Handler(Looper.getMainLooper());
 private final ExecutorService worker=Executors.newSingleThreadExecutor(r->{
  Thread thread=new Thread(r,"LocalPilot-Local-English-Core");
  thread.setDaemon(true);
  return thread;
 });
 private final AtomicBoolean busy=new AtomicBoolean();
 private volatile HttpURLConnection active;
 private volatile boolean closed;
 private static final int MAX_HTTP_RESPONSE=16*1024;

 public boolean busy(){return busy.get();}

 public void health(Callback callback){
  execute(callback,()->{
   HttpURLConnection connection=connect(LocalCoreContract.HEALTH,"GET");
   try{
    connection.setReadTimeout(4000);
    int status=connection.getResponseCode();
    if(status!=200)return "Local model not ready (HTTP "+status+"). Keep Termux running.";
    String response=readBounded(connection.getInputStream());
    String state=new JSONObject(response).optString("status","");
    return "ok".equals(state)
      ?"Local model connected: 127.0.0.1:8080 (on-device)."
      :"Local model is not ready (status: "+(state.isBlank()?"unknown":state)+").";
   }finally{release(connection);}
  });
 }

 /**
  * End-to-end smoke check. Connection OK is not proof of intelligible inference.
  * Probes have no history, diagnostics, coordinates, or action permission.
  */
 public void selfTest(Callback callback){
  execute(callback,()->{
   String word=probe("Reply with exactly one word: BLUE");
   String arithmetic=probe("What is 12 + 15? Reply with the number only.");
   if(!"BLUE".equals(word.trim())||!"27".equals(arithmetic.trim()))
    return "Проверка качества НЕ пройдена. Сервер отвечает, но английские инструкции "+
      "выполняются некорректно. Модель не допускается к управлению игрой.";
   return "Проверка пройдена: BLUE и 27. Это подтверждает только простую "+
     "англоязычную генерацию, не игровую компетентность.";
  });
 }

 private String probe(String prompt)throws Exception{
  JSONObject request=new JSONObject();
  request.put("stream",false);
  request.put("temperature",0.0);
  request.put("max_tokens",16);
  request.put("messages",new JSONArray()
    .put(new JSONObject().put("role","system")
      .put("content","Answer in English. Obey the exact output format. No explanations."))
    .put(new JSONObject().put("role","user").put("content",prompt)));
  byte[] data=request.toString().getBytes(StandardCharsets.UTF_8);
  HttpURLConnection connection=connect(LocalCoreContract.COMPLETIONS,"POST");
  try{
   connection.setDoOutput(true);
   connection.setRequestProperty("Content-Type","application/json; charset=utf-8");
   connection.setFixedLengthStreamingMode(data.length);
   try(OutputStream out=connection.getOutputStream()){out.write(data);}
   if(connection.getResponseCode()!=200)
    throw new java.io.IOException("Probe HTTP error");
   JSONObject choice=new JSONObject(readBounded(connection.getInputStream()))
     .getJSONArray("choices").getJSONObject(0);
   if("length".equals(choice.optString("finish_reason","")))
    return "";
   return choice.getJSONObject("message").optString("content","");
  }finally{release(connection);}
 }

 public void ask(String question,String snapshot,Callback callback){
  final String validatedQuestion=LocalCoreContract.question(question);
  final String diagnostic=LocalCoreContract.diagnostic(snapshot);
  execute(callback,()->{
   JSONObject request=new JSONObject();
   request.put("stream",false);
   request.put("temperature",0.2);
   request.put("top_k",20);
   request.put("top_p",0.8);
   request.put("repeat_penalty",1.18);
   request.put("max_tokens",128);
   request.put("messages",new JSONArray()
     .put(new JSONObject().put("role","system").put("content",LocalCoreContract.SYSTEM_PROMPT))
     .put(new JSONObject().put("role","user").put("content",
       "QUESTION: "+validatedQuestion+"\nANONYMOUS COUNTERS: "+diagnostic)));
   byte[] bytes=request.toString().getBytes(StandardCharsets.UTF_8);
   HttpURLConnection connection=connect(LocalCoreContract.COMPLETIONS,"POST");
   try{
    connection.setDoOutput(true);
    connection.setRequestProperty("Content-Type","application/json; charset=utf-8");
    connection.setFixedLengthStreamingMode(bytes.length);
    try(OutputStream out=connection.getOutputStream()){out.write(bytes);}
    int status=connection.getResponseCode();
    if(status!=200)return "Local inference unavailable: HTTP "+status;
    JSONObject body=new JSONObject(readBounded(connection.getInputStream()));
    JSONObject choice=body.getJSONArray("choices").getJSONObject(0);
    if("length".equals(choice.optString("finish_reason","")))
     return "Local response cut off at the token limit. No game action taken.";
    String response=choice.getJSONObject("message").optString("content","");
    if(!LocalCoreContract.acceptEnglish(response))
     return "Local response rejected: looping, non-English, missing or too long. No game action taken.";
    return "MNN-Pocket local · English\n"+response;
   }finally{release(connection);}
  });
 }

 private interface Job { String run() throws Exception; }

 private void execute(Callback callback,Job job){
  if(closed || !busy.compareAndSet(false,true))
   throw new IllegalStateException("Local model is busy or closed");
  worker.execute(()->{
   String result;
   try{result=job.run();}
   catch(Exception failure){result="Cannot reach local model. Check Termux llama-server and port 8080.";}
   finally{busy.set(false);}
   final String text=result;
   if(!closed)main.post(()->{if(!closed)callback.complete(text);});
  });
 }

 private HttpURLConnection connect(String url,String method)throws Exception{
  if(closed)throw new IllegalStateException("closed");
  // Proxy.NO_PROXY prevents a conventional HTTP proxy intercepting loopback.
  HttpURLConnection connection=(HttpURLConnection)new URL(url).openConnection(Proxy.NO_PROXY);
  active=connection;
  connection.setConnectTimeout(2500);
  connection.setReadTimeout(30000);
  connection.setRequestMethod(method);
  connection.setInstanceFollowRedirects(false);
  connection.setUseCaches(false);
  connection.setRequestProperty("Cache-Control","no-store");
  return connection;
 }

 private static String readBounded(InputStream stream)throws Exception{
  try(InputStream in=stream;ByteArrayOutputStream out=new ByteArrayOutputStream()){
   byte[] buffer=new byte[1024];int read;
   while((read=in.read(buffer))!=-1){
    if(out.size()+read>MAX_HTTP_RESPONSE)throw new java.io.IOException("response too large");
    out.write(buffer,0,read);
   }
   return new String(out.toByteArray(),StandardCharsets.UTF_8);
  }
 }

 private void release(HttpURLConnection connection){
  connection.disconnect();
  if(active==connection)active=null;
 }

 public void close(){
  closed=true;
  HttpURLConnection connection=active;
  if(connection!=null)connection.disconnect();
  worker.shutdownNow();
 }
}
