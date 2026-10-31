package org.localpilot.android;

import android.os.Handler;
import android.os.Looper;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.net.ssl.HttpsURLConnection;

/**
 * Optional, human-initiated, read-only OpenRouter advisor.
 * Never receives images, Accessibility contents, target packages or action tickets.
 * Never returns an action to ActionGate/Policy/dispatchGesture.
 */
public final class OpenRouterAdvisor {
 public interface Callback { void complete(String message); }
 private static final String ENDPOINT="https://openrouter.ai/api/v1/chat/completions";
 private static final int MAX_RESPONSE_BYTES=48*1024;
 private final Handler main=new Handler(Looper.getMainLooper());
 private final ExecutorService worker=Executors.newSingleThreadExecutor(r->{
  Thread t=new Thread(r,"LocalPilot-OpenRouter-Advisor");t.setDaemon(true);return t;
 });
 private final AtomicBoolean busy=new AtomicBoolean();
 private volatile HttpsURLConnection active;
 private volatile boolean closed;
 public boolean busy(){return busy.get();}
 public static String[] validateModels(String raw){
  if(raw==null||raw.length()>400)throw new IllegalArgumentException("Введите название модели OpenRouter");
  String[] models=raw.trim().split(",",-1);
  if(models.length<1||models.length>3)throw new IllegalArgumentException("Допускается от 1 до 3 моделей");
  for(int i=0;i<models.length;i++){
   models[i]=models[i].trim();
   if(!models[i].matches("[A-Za-z0-9~._:/+\\-]{3,128}")
     ||models[i].contains(".."))
    throw new IllegalArgumentException("Проверьте slug модели OpenRouter");
  }
  return models;
 }
 public static void validateKey(String key){
  if(key==null||key.length()<16||key.length()>256||key.indexOf('\n')>=0||key.indexOf('\r')>=0)
   throw new IllegalArgumentException("Нужен действующий ключ OpenRouter");
 }
 /** A free credential check, independent of a billed model completion. */
 public void checkKey(String key,Callback callback){
  validateKey(key);
  if(closed||!busy.compareAndSet(false,true))
   throw new IllegalStateException("Запрос уже выполняется");
  worker.execute(()->{
   String message;
   HttpsURLConnection connection=null;
   try{
    connection=(HttpsURLConnection)new URL("https://openrouter.ai/api/v1/key").openConnection();
    active=connection;
    connection.setConnectTimeout(5000);connection.setReadTimeout(8000);
    connection.setRequestMethod("GET");
    connection.setInstanceFollowRedirects(false);
    connection.setRequestProperty("Authorization","Bearer "+key);
    connection.setRequestProperty("Cache-Control","no-store");
    int response=connection.getResponseCode();
    if(response!=200)throw new HttpStatus(response);
    JSONObject data;
    try(InputStream in=connection.getInputStream()){
     data=new JSONObject(new String(bounded(in),StandardCharsets.UTF_8)).getJSONObject("data");
    }
    message="Ключ подтверждён OpenRouter";
    if(data.has("limit_remaining")&&!data.isNull("limit_remaining"))
     message+=". Остаток лимита: "+data.opt("limit_remaining");
    if(data.optBoolean("is_free_tier",false))
     message+=". Бесплатный тариф: возможны лимиты частоты.";
   }catch(Exception e){message="Проверка OpenRouter не прошла: "+
     (e instanceof HttpStatus status?"HTTP "+status.code:"нет сети или некорректный ответ");}
   finally{
    if(connection!=null){connection.disconnect();if(active==connection)active=null;}
    busy.set(false);
   }
   final String result=message;
   if(!closed)main.post(()->{if(!closed)callback.complete(result);});
  });
 }
 public void ask(String key,String models,String question,String diagnostic,Callback callback){
  validateKey(key);
  String[] selected=validateModels(models);
  if(question==null||question.isBlank()||question.length()>600)
   throw new IllegalArgumentException("Напишите вопрос до 600 символов");
  if(diagnostic==null||diagnostic.length()>4096)
   throw new IllegalArgumentException("Сводка диагностики слишком большая");
  if(closed||!busy.compareAndSet(false,true))
   throw new IllegalStateException("Запрос уже выполняется или модуль закрыт");
  try{
   JSONObject request=new JSONObject();
   if(selected.length==1)request.put("model",selected[0]);
   else{JSONArray fallback=new JSONArray();for(String model:selected)fallback.put(model);request.put("models",fallback);}
   request.put("stream",false);
   request.put("temperature",0.2);
   request.put("max_tokens",450);
   request.put("provider",new JSONObject().put("data_collection","deny"));
   JSONArray messages=new JSONArray();
   messages.put(new JSONObject().put("role","system").put("content",
     "Ты диагностический помощник Android LocalPilot. Отвечай на русском кратко и предметно. "+
     "Ввод только агрегированная диагностика без кадров. Это НЕ показания реального изображения. "+
     "Разделяй подтвержденные факты и гипотезы, давай осторожные рекомендации. "+
     "Ты не управляешь игрой; никогда не предлагай обходить STOP, ROI, ActionGate, InputPermit, TTL "+
     "или считать Android ACK подтверждением игрового успеха."));
   messages.put(new JSONObject().put("role","user").put("content",
     "Вопрос пользователя: "+question+"\nОбезличенные игровые счётчики JSON: "+diagnostic));
   request.put("messages",messages);
   byte[] body=request.toString().getBytes(StandardCharsets.UTF_8);
   worker.execute(()->{
    String answer;
    try{answer=submit(key,body);}
    catch(Exception e){answer="OpenRouter недоступен. Проверьте ключ, модель, баланс и сеть. "+
      (e instanceof HttpStatus status?"HTTP "+status.code:"Ошибка соединения или формата ответа");}
    finally{busy.set(false);}
    final String result=answer;
    if(!closed)main.post(()->{if(!closed)callback.complete(result);});
   });
  }catch(JSONException|RuntimeException problem){
   busy.set(false);throw problem;
  }
 }
 private static final class HttpStatus extends Exception{
  final int code;HttpStatus(int value){code=value;}
 }
 private String submit(String key,byte[] request)throws Exception{
  HttpsURLConnection connection=(HttpsURLConnection)new URL(ENDPOINT).openConnection();
  active=connection;
  try{
   if(closed)throw new IllegalStateException("cancelled");
   connection.setConnectTimeout(5000);connection.setReadTimeout(18000);
   connection.setRequestMethod("POST");
   connection.setDoOutput(true);
   connection.setInstanceFollowRedirects(false);
   connection.setRequestProperty("Authorization","Bearer "+key);
   connection.setRequestProperty("Content-Type","application/json; charset=utf-8");
   connection.setRequestProperty("Cache-Control","no-store");
   connection.setFixedLengthStreamingMode(request.length);
   try(OutputStream out=connection.getOutputStream()){out.write(request);}
   int status=connection.getResponseCode();
   if(status!=200)throw new HttpStatus(status);
   byte[] response;
   try(InputStream stream=connection.getInputStream()){response=bounded(stream);}
   JSONObject root=new JSONObject(new String(response,StandardCharsets.UTF_8));
   JSONArray choices=root.getJSONArray("choices");
   String content=choices.getJSONObject(0).getJSONObject("message").optString("content","");
   if(content.isBlank())throw new JSONException("empty response");
   String selectedModel=root.optString("model","не указан");
   return "Модель: "+selectedModel+"\n"+content.substring(0,Math.min(content.length(),3000));
  }finally{
   connection.disconnect();
   if(active==connection)active=null;
  }
 }
 private static byte[] bounded(InputStream stream)throws java.io.IOException{
  ByteArrayOutputStream out=new ByteArrayOutputStream();
  byte[] part=new byte[2048];int n;
  while((n=stream.read(part))!=-1){
   if(out.size()+n>MAX_RESPONSE_BYTES)throw new java.io.IOException("response too large");
   out.write(part,0,n);
  }
  return out.toByteArray();
 }
 public void close(){closed=true;HttpsURLConnection connection=active;
  if(connection!=null)connection.disconnect();worker.shutdownNow();}
}
