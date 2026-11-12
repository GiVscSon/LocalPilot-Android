package org.localpilot.android;

import android.content.Context;
import android.os.SystemClock;
import android.util.AtomicFile;
import org.json.JSONArray;
import org.json.JSONObject;
import org.localpilot.shared.ConsoleAgentBridge;
import org.localpilot.shared.HarnessImitationMath;
import org.localpilot.shared.HarnessTelemetry;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.TreeMap;

/** Real lightweight on-device imitation learning from REAL_JSNES visual examples.
 * NOT reinforcement learning; reward remains UNKNOWN, GGUF weights unchanged.
 */
public final class HarnessImitationPolicy {
 private static final String FILE="harness-imitation-active-v1.json";
 private static final String CANDIDATE="harness-imitation-candidate-v1.json";
 private HarnessImitationPolicy(){}
 private static File policyFile(Context ctx,String name){
  return new File(ctx.getNoBackupFilesDir(),name);
 }
 private static String digest(double[] weights)throws Exception{
  MessageDigest sha=MessageDigest.getInstance("SHA-256");
  for(double weight:weights){
   long bits=Double.doubleToLongBits(weight);
   for(int i=0;i<8;i++)sha.update((byte)(bits>>>(8*i)));
  }
  StringBuilder hex=new StringBuilder(64);
  for(byte b:sha.digest())hex.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
  return hex.toString();
 }
 private static JSONObject read(Context ctx,String name)throws Exception{
  File f=policyFile(ctx,name);
  if(!f.isFile()||f.length()>180000||f.length()<100)return null;
  try(FileInputStream in=new FileInputStream(f)){
   byte[] buf=new byte[(int)f.length()],block=new byte[8192];
   int total=0,n;
   while((n=in.read(block))!=-1){
    if(total+n>buf.length)throw new SecurityException("Unexpected policy file length");
    System.arraycopy(block,0,buf,total,n);total+=n;
   }
   if(total!=buf.length)throw new SecurityException("Partial policy");
   return new JSONObject(new String(buf,StandardCharsets.UTF_8));
  }
 }
 private static void atomicSave(Context ctx,String name,JSONObject data)throws Exception{
  AtomicFile target=new AtomicFile(policyFile(ctx,name));
  FileOutputStream out=null;
  try{
   out=target.startWrite();
   byte[] bytes=data.toString().getBytes(StandardCharsets.UTF_8);
   if(bytes.length>180000)throw new IllegalArgumentException("Policy exceeds bound");
   out.write(bytes);out.getFD().sync();
   target.finishWrite(out);
  }catch(Exception failure){if(out!=null)target.failWrite(out);throw failure;}
 }
 private static double[] validatedWeights(JSONObject object)throws Exception{
  if(object==null||!"localpilot.imitation.v1".equals(object.optString("schema")))
   throw new IllegalArgumentException("Policy missing or unknown schema");
  JSONArray array=object.getJSONArray("weights");
  if(array.length()!=HarnessImitationMath.WEIGHTS)
   throw new IllegalArgumentException("Policy tensor shape mismatch");
  double[] weights=new double[array.length()];
  for(int i=0;i<weights.length;i++){
   weights[i]=array.getDouble(i);
   if(!Double.isFinite(weights[i])||Math.abs(weights[i])>1000)
    throw new IllegalArgumentException("Invalid learned weight");
  }
  if(!digest(weights).equals(object.getString("sha256")))
   throw new SecurityException("Stored policy checksum mismatch");
  return weights;
 }
 public static JSONObject status(Context ctx){
  JSONObject result=new JSONObject();
  try{
   JSONObject active=read(ctx,FILE);
   JSONObject candidate=read(ctx,CANDIDATE);
   result.put("active",active!=null);
   if(active!=null){
    validatedWeights(active);
    result.put("modelId","fast-policy:"+active.getString("sha256").substring(0,16));
    result.put("sha256",active.getString("sha256"));
    result.put("examples",active.getInt("trainingSamples"));
    result.put("accuracy",active.getDouble("holdoutAccuracy"));
    result.put("baseline",active.getDouble("baselineAccuracy"));
    result.put("sourceEpisodes",active.getJSONArray("trainingEpisodes"));
    result.put("kind","IMITATION_ONLY_NO_REWARD");
   }
   result.put("candidate",candidate!=null);
   if(candidate!=null){
    validatedWeights(candidate);
    result.put("candidateSha256",candidate.getString("sha256"));
   }
  }catch(Exception e){
   try{result.put("active",false).put("error",e.getClass().getSimpleName());}
   catch(Exception ignored){}
  }
  return result;
 }
 /** Explicit training. Each example must come from an actual NES frame
  * AND a fresh visual transition; no reward is manufactured.
  * Split by whole episode, never by individual frame from same episode.
  */
 public static String train(Context ctx,JSONArray events)throws Exception{
  if(HarnessTelemetry.active())throw new IllegalStateException("STOP game before training");
  Map<String,List<HarnessImitationMath.Example>> groups=new TreeMap<>();
  Set<String> teacherIds=new HashSet<>();
  for(int i=0;i<events.length();i++){
   JSONObject row=events.optJSONObject(i);
   if(row==null||!"REAL_JSNES".equals(row.optString("provenance"))||
      !"EXAMPLE".equals(row.optString("stage"))||
      !"VISUAL_ONLY".equals(row.optString("status"))||
      row.optString("modelId").startsWith("fast-policy:"))continue;
   String detail=row.optString("detail");
   int at=detail.indexOf(";action=");
   if(!detail.startsWith("grid=")||at<5)continue;
   String grid=detail.substring(5,at),action=detail.substring(at+8);
   try{
    HarnessImitationMath.Example example=new HarnessImitationMath.Example(
      row.getString("episodeId"),grid,action);
    groups.computeIfAbsent(example.episode,k->new ArrayList<>()).add(example);
    teacherIds.add(row.optString("modelId","UNKNOWN"));
   }catch(IllegalArgumentException ignored){}
  }
  if(groups.size()<4)throw new IllegalStateException(
    "Нужно >=4 независимых реальных NES-эпизода с проверенными визуальными переходами");
  List<HarnessImitationMath.Example> training=new ArrayList<>(),holdout=new ArrayList<>();
  JSONArray trainIds=new JSONArray(),testIds=new JSONArray();
  int idx=0;
  for(Map.Entry<String,List<HarnessImitationMath.Example>> group:groups.entrySet()){
   if(idx++%4==0){holdout.addAll(group.getValue());testIds.put(group.getKey());}
   else{training.addAll(group.getValue());trainIds.put(group.getKey());}
  }
  if(training.size()<8||holdout.size()<2)
   throw new IllegalStateException("Не хватает независимых тренировочных и тестовых действий");
  double[] weights=HarnessImitationMath.train(training);
  double accuracy=HarnessImitationMath.accuracy(weights,holdout);
  double baseline=HarnessImitationMath.majorityBaseline(training,holdout);
  double abs=0;for(double w:weights)abs+=Math.abs(w);
  if(abs<0.01)throw new IllegalStateException("Weights did not change; no training");
  JSONArray encoded=new JSONArray();
  for(double w:weights)encoded.put(w);
  String sha=digest(weights);
  JSONObject record=new JSONObject();
  record.put("schema","localpilot.imitation.v1")
   .put("kind","IMITATION_ONLY_NO_REWARD")
   .put("sha256",sha).put("weights",encoded)
   .put("trainingSamples",training.size())
   .put("holdoutSamples",holdout.size())
   .put("holdoutAccuracy",accuracy)
   .put("baselineAccuracy",baseline)
   .put("trainingEpisodes",trainIds)
   .put("holdoutEpisodes",testIds)
   .put("teacherModels",new JSONArray(teacherIds))
   .put("reward",JSONObject.NULL)
   .put("trainedAt",System.currentTimeMillis());
  atomicSave(ctx,CANDIDATE,record);
  boolean promoted=accuracy>=baseline+0.05&&accuracy>=0.50;
  if(promoted)atomicSave(ctx,FILE,record);
  return "Настоящее имитационное обучение: "+training.size()+
    " реальных примеров, holdout "+holdout.size()+
    ", точность "+Math.round(accuracy*100)+"%, baseline "+
    Math.round(baseline*100)+"%. Изменён policy SHA "+sha.substring(0,16)+
    (promoted?". Кандидат принят, доступен для NES.":". Кандидат сохранён, но НЕ продвинут. Награда UNKNOWN.");
 }
 public static String bind(Context ctx)throws Exception{
  if(HarnessTelemetry.active())throw new IllegalStateException("STOP NES episode first");
  JSONObject active=read(ctx,FILE);
  double[] weights=validatedWeights(active);
  String id="fast-policy:"+active.getString("sha256").substring(0,16);
  synchronized(ModelLabActivity.class){
   if(HarnessTelemetry.active())throw new IllegalStateException("STOP NES before policy change");
   if(!ctx.getSharedPreferences("localpilot-console-assignment",0).edit()
          .putString("modelId",id).commit())throw new java.io.IOException("Policy binding not persisted");
   install(id,weights);
  }
  return "FAST POLICY BOUND: "+id+" · supervised imitation (NOT reward RL)";
 }
 private static void install(String id,double[] weights){
  ConsoleAgentBridge.set(new ConsoleAgentBridge.Brain(){
   public String label(){return id;}
   public boolean vision(){return false;}
   public void cancel(){/* synchronous small CPU inference has no background job */}
   public void decide(String observation,byte[] frame,ConsoleAgentBridge.Reply reply){
    long started=SystemClock.elapsedRealtime();
    String action="STOP";
    try{
     final String key="real 8x8 luminance framebuffer=";
     int at=observation.indexOf(key);
     if(at<0||at+key.length()+71>observation.length())
      throw new IllegalArgumentException("Missing real NES grid");
     String grid=observation.substring(at+key.length(),at+key.length()+71);
     HarnessImitationMath.Example e=new HarnessImitationMath.Example(
       "active-nes-session",grid,"A");
     double[] scores=HarnessImitationMath.scores(weights,e.x);
     int best=HarnessImitationMath.predict(weights,e);
     double next=-1e12;
     for(int i=0;i<scores.length;i++)if(i!=best)next=Math.max(next,scores[i]);
     if(scores[best]-next>=0.22)action=HarnessImitationMath.ACTIONS[best];
    }catch(RuntimeException ignored){action="STOP";}
    String replyText="ACTION="+action;
    HarnessTelemetry.emit("MODEL","MODEL_USED",replyText,
      SystemClock.elapsedRealtime()-started);
    reply.complete(replyText);
   }
  });
 }
 public static void restoreIfAssigned(Context ctx){
  String stored=ctx.getSharedPreferences("localpilot-console-assignment",0)
    .getString("modelId","");
  if(!stored.startsWith("fast-policy:"))return;
  try{
   JSONObject active=read(ctx,FILE);
   double[] weights=validatedWeights(active);
   String actual="fast-policy:"+active.getString("sha256").substring(0,16);
   if(actual.equals(stored))install(actual,weights);
  }catch(Exception ignored){/* invalid policy never becomes an agent */}
 }
}
