package org.localpilot.gamelab;

import android.graphics.BitmapFactory;
import android.os.SystemClock;
import android.util.Base64;
import android.webkit.WebView;
import org.json.JSONObject;
import org.localpilot.shared.HarnessCoordinator;
import org.localpilot.shared.harness.EnvironmentAdapter;
import org.localpilot.shared.harness.Policy;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.function.*;

/** Real JSNES adapter over the existing view, never a second emulator or input executor. */
public final class NesHarnessAdapter implements EnvironmentAdapter {
 private final WebView web;
 private final HarnessCoordinator owner;
 private final Path images;
 private final Supplier<String> newEpoch;
 private final Consumer<Observation> observed;
 private final byte[] cartridge=HomebrewNes.cartridge();
 private final String romSha;
 private String episode="",epoch="";
 private int seed;
 private long generation;
 private boolean busy;
 public NesHarnessAdapter(WebView web,HarnessCoordinator owner,Path images,Supplier<String> newEpoch,
                          Consumer<Observation> observed)throws Exception{
  this.web=web;this.owner=owner;this.images=images;this.newEpoch=newEpoch;this.observed=observed;
  Files.createDirectories(images);romSha=hash(cartridge);
 }
 private static String hash(byte[] bytes)throws Exception{return Policy.hex(MessageDigest.getInstance("SHA-256").digest(bytes));}
 @Override public void reset(String id,int nextSeed,Reply<Observation> reply){
  if(busy){reply.complete(null,"Adapter busy");return;}
  episode=id;seed=nextSeed;epoch=newEpoch.get();
  owner.reset(epoch,HarnessCoordinator.Mode.STEP_MODE);
  call("window.retroHarnessReset("+JSONObject.quote(Base64.encodeToString(cartridge,Base64.NO_WRAP))+","+
    JSONObject.quote(epoch)+","+seed+")",reply);
 }
 @Override public void observe(Reply<Observation> reply){call("window.retroHarnessObserve()",reply);}
 @Override public void mode(HarnessCoordinator.Mode mode,Reply<Observation> reply){
  if(mode!=HarnessCoordinator.Mode.REALTIME_MODE||!owner.changeMode(epoch,mode)){
   reply.complete(null,"Mode busy or unsupported");return;
  }
  call("window.retroHarnessRealtime()",reply);
 }
 @Override public void step(HarnessCoordinator.ActionPermit permit,Reply<Observation> reply){
  if(permit==null||permit.actor!=HarnessCoordinator.Actor.FAST_POLICY||
     permit.holdFrames!=16||!epoch.equals(permit.epoch)||permit.stopRevision!=owner.stopRevision()||!owner.actionInFlight()){
   reply.complete(null,"Invalid or revoked environment permit");return;
  }
  if(permit.mode==HarnessCoordinator.Mode.REALTIME_MODE){
   String token=Long.toString(SystemClock.elapsedRealtimeNanos());
   String begin="window.retroHarnessBegin("+JSONObject.quote(permit.button)+","+permit.beforeFrame+","+
     JSONObject.quote(permit.epoch)+","+JSONObject.quote(token)+")";
   call("window.retroHarnessPoll("+JSONObject.quote(token)+")",reply,begin);
  }else call("window.retroHarnessStep("+JSONObject.quote(permit.button)+")",reply);
 }
 private void call(String script,Reply<Observation> reply){
  call(script,reply,null);
 }
 private void call(String script,Reply<Observation> reply,String begin){
  if(busy){reply.complete(null,"Adapter busy");return;}
  busy=true;final long current=++generation;
  web.postDelayed(()->{
   if(current!=generation||!busy)return;
   stop();reply.complete(null,"WebView observation timeout");
  },2500);
  if(begin==null)receive(script,current,reply,false);
  else web.evaluateJavascript(begin,result->{
   if(current!=generation||!busy)return;
   if(!"true".equals(result)){busy=false;reply.complete(null,"Stale realtime frame or rejected action");return;}
   receive(script,current,reply,true);
  });
 }
 private void receive(String script,long current,Reply<Observation> reply,boolean poll){
  web.evaluateJavascript(script,json->{
   if(current!=generation||!busy)return;
   if("null".equals(json)&&poll){web.postDelayed(()->{
    if(current==generation&&busy)receive(script,current,reply,true);
   },8);return;}
   busy=false;
   try{
    JSONObject data=new JSONObject(json);
    if(!epoch.equals(data.getString("epoch"))||seed!=data.getInt("seed"))throw new IOException("Wrong task epoch");
    String encoded=data.getString("png");if(encoded.length()>350000)throw new IOException("Frame bounds");
    byte[] png=Base64.decode(encoded,Base64.NO_WRAP);
    BitmapFactory.Options dimensions=new BitmapFactory.Options();dimensions.inJustDecodeBounds=true;
    BitmapFactory.decodeByteArray(png,0,png.length,dimensions);
    if(dimensions.outWidth!=256||dimensions.outHeight!=240||!"image/png".equals(dimensions.outMimeType))
     throw new IOException("Invalid NES framebuffer image");
    String imageSha=hash(png);Path image=images.resolve(imageSha+".png");
    if(!Files.exists(image)){
     try(var paths=Files.list(images)){
      long bytes=0;int count=0;
      for(Path path:paths.collect(java.util.stream.Collectors.toList())){count++;bytes+=Files.size(path);}
      if(count>=5000||bytes+png.length>128L*1024*1024)
       throw new IOException("Frame quota full; export evidence first");
     }
     Path temp=Files.createTempFile(images,"frame-",".tmp");
     try{
      try(FileOutputStream out=new FileOutputStream(temp.toFile())){out.write(png);out.getFD().sync();}
      Files.move(temp,image,StandardCopyOption.ATOMIC_MOVE);
     }finally{Files.deleteIfExists(temp);}
    }
    Observation result=new Observation(episode,epoch,romSha,seed,data.getInt("frame"),data.getInt("hash"),
      data.getInt("x"),data.getInt("y"),data.getInt("target"),
      HarnessCoordinator.Mode.valueOf(data.getString("mode")),data.getBoolean("verified"),imageSha);
    observed.accept(result);reply.complete(result,null);
   }catch(Exception error){reply.complete(null,error.getClass().getSimpleName()+": "+error.getMessage());}
  });
 }
 @Override public void stop(){
  generation++;busy=false;owner.stop("ENVIRONMENT_STOP");
  web.evaluateJavascript("window.retroPause()",null);
 }
}
