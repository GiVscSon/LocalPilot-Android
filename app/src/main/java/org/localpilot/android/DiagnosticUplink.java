package org.localpilot.android;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import javax.net.ssl.HttpsURLConnection;

/**
 * Explicitly opt-in, memory-only HTTPS diagnostics transport.
 * Never sends pixels, screenshots, logs, window packages or game coordinates.
 * Neither the destination nor the access token are written to preferences.
 */
public final class DiagnosticUplink {
 private static final long INTERVAL_MS=10_000;
 private static final int MAX_BYTES=4_096;
 private final Handler main=new Handler(Looper.getMainLooper());
 private final ExecutorService worker=Executors.newSingleThreadExecutor(r->{
  Thread thread=new Thread(r,"LocalPilot-Online-Diagnostics");thread.setDaemon(true);return thread;
 });
 private final AtomicBoolean inFlight=new AtomicBoolean();
 private volatile String status="Выключено";
 private volatile boolean enabled,closed;
 private volatile HttpsURLConnection activeConnection;
 private volatile int generation;
 private String endpoint,secret;
 private Supplier<String> source;
 private final Runnable next=new Runnable(){
  @Override public void run(){
   if(!enabled||closed)return;
   final int version=generation;
   if(inFlight.compareAndSet(false,true)){
    String snapshot;
    try{snapshot=Objects.requireNonNull(source.get(),"snapshot");}
    catch(RuntimeException error){
     status="Нет диагностического состояния";inFlight.set(false);
     if(enabled&&!closed&&generation==version)main.postDelayed(this,INTERVAL_MS);
     return;
    }
    byte[] bytes=snapshot.getBytes(StandardCharsets.UTF_8);
    if(bytes.length>MAX_BYTES){
     status="Отчёт превышает 4 КиБ, отправка отменена";inFlight.set(false);
    }else{
     String destination=endpoint,token=secret;
     worker.execute(()->{
      try{
       if(enabled&&!closed&&generation==version)upload(destination,token,bytes,version);
      }finally{
       inFlight.set(false);
      }
     });
    }
   }
   if(enabled&&!closed&&generation==version)main.postDelayed(this,INTERVAL_MS);
  }
 };
 public String status(){return status;}
 public boolean enabled(){return enabled;}
 public static void validate(String endpoint,String token){
  if(endpoint==null||endpoint.length()>300||token==null||token.length()<16||token.length()>256
    ||token.indexOf('\n')>=0||token.indexOf('\r')>=0)
   throw new IllegalArgumentException("Требуется HTTPS адрес и длинный токен");
  try{
   URI uri=new URI(endpoint);
   if(!"https".equalsIgnoreCase(uri.getScheme())||uri.getHost()==null
     ||uri.getUserInfo()!=null||uri.getFragment()!=null||uri.getRawQuery()!=null
     ||uri.getRawPath()==null||!uri.getRawPath().endsWith("/v1/telemetry"))
    throw new IllegalArgumentException("Адрес должен оканчиваться на /v1/telemetry");
  }catch(java.net.URISyntaxException invalid){throw new IllegalArgumentException("Неверный HTTPS адрес");}
 }
 public void start(String url,String token,Supplier<String> producer){
  validate(url,token);Objects.requireNonNull(producer,"source");
  if(closed)throw new IllegalStateException("Служба диагностики завершена");
  stop();
  endpoint=url;secret=token;source=producer;enabled=true;
  status="Подключение · ожидаю HTTPS подтверждение";
  main.post(next);
 }
 public void stop(){
  enabled=false;generation++;
  main.removeCallbacks(next);
  if(activeConnection!=null)activeConnection.disconnect();
  endpoint=null;secret=null;source=null;
  status="Выключено";
 }
 public void close(){stop();closed=true;worker.shutdownNow();}
 private void upload(String endpoint,String token,byte[] bytes,int version){
  HttpsURLConnection connection=null;
  try{
   connection=(HttpsURLConnection)new URL(endpoint).openConnection();
   activeConnection=connection;
   if(!enabled||version!=generation)return;
   connection.setConnectTimeout(3500);connection.setReadTimeout(3500);
   connection.setRequestMethod("POST");
   connection.setRequestProperty("Content-Type","application/json; charset=utf-8");
   connection.setRequestProperty("Authorization","Bearer "+token);
   connection.setRequestProperty("Cache-Control","no-store");
   connection.setInstanceFollowRedirects(false);
   connection.setDoOutput(true);
   connection.setFixedLengthStreamingMode(bytes.length);
   try(OutputStream output=connection.getOutputStream()){output.write(bytes);}
   int response=connection.getResponseCode();
   if(enabled&&generation==version)status=response==204||response==200
     ?"Данные доставлены · "+SystemClock.elapsedRealtime()+" мс"
     :"Сервер отклонил данные: HTTP "+response;
  }catch(java.io.IOException|RuntimeException error){
   if(enabled&&generation==version)status="Нет соединения с сервером HTTPS";
  }finally{
   if(connection!=null){connection.disconnect();if(activeConnection==connection)activeConnection=null;}
  }
 }
}
