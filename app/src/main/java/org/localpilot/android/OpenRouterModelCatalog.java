package org.localpilot.android;

import android.os.Handler;
import android.os.Looper;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.net.ssl.HttpsURLConnection;
import org.json.JSONArray;
import org.json.JSONObject;

/** Live, public OpenRouter catalog. No API key required for GET /api/v1/models. */
public final class OpenRouterModelCatalog {
 public interface Callback {void complete(List<Model> models,String error);}
 public record Model(String id,String name,boolean free,boolean suitable,
                     long context,double inputPerMillion,double outputPerMillion) {
  public String label(){
   return name+" · "+(free?"Бесплатно":"$"+price(inputPerMillion)+"/$"+
     price(outputPerMillion)+" за 1М ток. ввод/вывод")+
     " · "+(context>=1000?context/1000+"k":context)+" контекст";
  }
  private static String price(double n){return String.format(Locale.ROOT,"%.3f",n);}
 }
 private static final String ADDRESS="https://openrouter.ai/api/v1/models";
 private static final int MAX_BYTES=5*1024*1024;
 private static final int MAX_ITEMS=2_500;
 private final ExecutorService worker=Executors.newSingleThreadExecutor(r->{
  Thread t=new Thread(r,"LocalPilot-Model-Catalog");t.setDaemon(true);return t;
 });
 private final Handler main=new Handler(Looper.getMainLooper());
 private volatile boolean closed,busy;
 private volatile HttpsURLConnection active;
 public boolean busy(){return busy;}
 public void load(Callback callback){
  if(closed||busy)throw new IllegalStateException("Каталог уже обновляется");
  busy=true;
  worker.execute(()->{
   List<Model> result=List.of();String error=null;
   try{result=fetch();}
   catch(Exception failure){error="Каталог OpenRouter недоступен: проверьте сеть и повторите.";}
   finally{busy=false;}
   final List<Model> models=result;final String message=error;
   if(!closed)main.post(()->{if(!closed)callback.complete(models,message);});
  });
 }
 private List<Model> fetch()throws Exception{
  HttpsURLConnection conn=(HttpsURLConnection)new URL(ADDRESS).openConnection();
  active=conn;
  try{
   if(closed)throw new IllegalStateException("closed");
   conn.setConnectTimeout(6000);conn.setReadTimeout(12000);
   conn.setRequestMethod("GET");conn.setInstanceFollowRedirects(false);
   conn.setRequestProperty("Accept","application/json");
   conn.setRequestProperty("Cache-Control","no-store");
   if(conn.getResponseCode()!=200)throw new java.io.IOException("catalog status");
   byte[] raw;
   try(InputStream in=conn.getInputStream()){raw=bounded(in);}
   JSONArray items=new JSONObject(new String(raw,StandardCharsets.UTF_8)).getJSONArray("data");
   if(items.length()>MAX_ITEMS)throw new java.io.IOException("oversized model catalog");
   List<Model> result=new ArrayList<>();
   for(int i=0;i<items.length();i++){
    JSONObject item=items.optJSONObject(i);
    if(item==null)continue;
    Model m=parseModel(item);
    if(m!=null)result.add(m);
   }
   if(result.isEmpty())throw new java.io.IOException("no text models");
   result.sort(Comparator.comparing(Model::id));
   return Collections.unmodifiableList(result);
  }finally{
   conn.disconnect();if(active==conn)active=null;
  }
 }
 static Model parseModel(JSONObject item){
  String id=item.optString("id",""),name=item.optString("name",id);
  if(!id.matches("[A-Za-z0-9~._:/+\\-]{3,128}")||name.isBlank())return null;
  JSONObject arch=item.optJSONObject("architecture");
  // The assistant sends plain text diagnostics. Never offer image-only, embedding
  // or audio-only endpoints through the chat-completions advisor.
  if(arch!=null){
   JSONArray outputs=arch.optJSONArray("output_modalities");
   if(outputs!=null){boolean hasText=false;for(int i=0;i<outputs.length();i++)
     if("text".equals(outputs.optString(i)))hasText=true;
    if(!hasText)return null;
   }
  }
  JSONObject price=item.optJSONObject("pricing");
  if(price==null)return null;
  // OpenRouter prices are USD per token, not per million tokens.
  double input=number(price.opt("prompt")),output=number(price.opt("completion"));
  if(!Double.isFinite(input)||!Double.isFinite(output)||input<0||output<0)return null;
  boolean free=input==0&&output==0;
  long context=item.optLong("context_length",0);
  if(context<2048)return null;
  // Heuristic suitability: adequate context plus reasonably affordable text
  // inference. This is NOT a quality benchmark nor a health/uptime claim.
  boolean suitable=context>=16_384&&(free||(input*1_000_000<=1.5&&output*1_000_000<=6));
  String shown=name.length()>85?name.substring(0,85):name;
  return new Model(id,shown,free,suitable,context,input*1_000_000,output*1_000_000);
 }
 private static double number(Object value){
  if(value==null||value==JSONObject.NULL)return Double.NaN;
  try{return Double.parseDouble(String.valueOf(value));}
  catch(NumberFormatException invalid){return Double.NaN;}
 }
 private static byte[] bounded(InputStream stream)throws java.io.IOException{
  ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] block=new byte[8192];int count;
  while((count=stream.read(block))!=-1){
   if(out.size()+count>MAX_BYTES)throw new java.io.IOException("catalog too large");
   out.write(block,0,count);
  }
  return out.toByteArray();
 }
 public void close(){
  closed=true;HttpsURLConnection conn=active;if(conn!=null)conn.disconnect();
  worker.shutdownNow();
 }
}
