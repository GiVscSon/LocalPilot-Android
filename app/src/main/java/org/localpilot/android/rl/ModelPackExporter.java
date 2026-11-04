package org.localpilot.android.rl;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.zip.*;

/**
 * Explicit user-directed portable model archive. Does not contact a network,
 * change training state, install a policy or authorize Android gestures.
 *
 * The archive separates genuinely trained DQN checkpoints from programmed
 * expert matrices, an optional trained paddle candidate and live visual policy
 * settings. Missing on-device models are reported as missing, never invented.
 */
public final class ModelPackExporter {
 private ModelPackExporter(){}
 public static final String FORMAT="localpilot-modelpack-v1";
 private static final long MAX_CHECKPOINT_BYTES=32L*1024*1024;
 private static final long MAX_LIVE_MODEL_BYTES=1024L*1024;
 private static final int MAX_ENTRIES=32;
 private static String quote(String s){
  StringBuilder b=new StringBuilder("\"");
  for(int i=0;i<s.length();i++){
   char c=s.charAt(i);
   if(c=='\"'||c=='\\')b.append('\\').append(c);
   else if(c=='\n')b.append("\\n");
   else if(c=='\r')b.append("\\r");
   else if(c=='\t')b.append("\\t");
   else if(c<32)b.append(String.format(Locale.ROOT,"\\u%04x",(int)c));
   else b.append(c);
  }
  return b.append('\"').toString();
 }
 private static String sha(byte[] data)throws IOException{
  try{
   byte[] digest=MessageDigest.getInstance("SHA-256").digest(data);
   StringBuilder b=new StringBuilder();
   for(byte c:digest)b.append(String.format(Locale.ROOT,"%02x",c&255));
   return b.toString();
  }catch(NoSuchAlgorithmException e){throw new IOException(e);}
 }
 private static final class Archive {
  final ZipOutputStream zip;
  final Map<String,String> digests=new TreeMap<>();
  Archive(OutputStream out){zip=new ZipOutputStream(out,StandardCharsets.UTF_8);}
  void add(String name,byte[] bytes)throws IOException{
   if(digests.size()>=MAX_ENTRIES||!name.matches("[A-Za-z0-9_./-]{1,128}")
      ||name.startsWith("/")||name.contains("..")||name.startsWith("./")
      ||digests.containsKey(name)||bytes.length>MAX_CHECKPOINT_BYTES)
    throw new IOException("invalid model archive entry: "+name);
   zip.putNextEntry(new ZipEntry(name));
   zip.write(bytes);
   zip.closeEntry();
   digests.put(name,sha(bytes));
  }
  void addFile(String name,File file,long max)throws IOException{
   if(!file.isFile()||file.length()<1||file.length()>max)throw new IOException("invalid model file");
   ByteArrayOutputStream bytes=new ByteArrayOutputStream((int)Math.min(file.length(),1024*1024));
   try(InputStream in=new FileInputStream(file)){
    byte[] block=new byte[8192];int n;
    while((n=in.read(block))!=-1){
     if(bytes.size()+n>max)throw new IOException("model file changed/too large");
     bytes.write(block,0,n);
    }
   }
   add(name,bytes.toByteArray());
  }
  void finish()throws IOException{
   StringBuilder sums=new StringBuilder();
   for(Map.Entry<String,String> x:digests.entrySet())
    sums.append(x.getValue()).append("  ").append(x.getKey()).append('\n');
   zip.putNextEntry(new ZipEntry("SHA256SUMS"));
   zip.write(sums.toString().getBytes(StandardCharsets.UTF_8));
   zip.closeEntry();zip.finish();zip.flush();
  }
 }
 private static String matrixJson(WeightSpaceSkills.Network model,String genre,String kind){
  double[][] weights=model.matrix();
  StringBuilder b=new StringBuilder("{\"schema\":\"weightspace-3x4-v1\",\"genre\":")
   .append(quote(genre)).append(",\"origin\":").append(quote(kind))
   .append(",\"weights\":[");
  for(int j=0;j<weights.length;j++){
   if(j>0)b.append(',');
   b.append('[');
   for(int i=0;i<weights[j].length;i++){
    if(i>0)b.append(',');
    b.append(Double.toString(weights[j][i]));
   }
   b.append(']');
  }
  return b.append("]}").toString();
 }
 private static String paddleJson(PaddleWeightLab.Weights w,String state){
  double[] weights=w.snapshot();
  StringBuilder b=new StringBuilder("{\"schema\":\"paddle-2x4x1-v1\",\"origin\":")
   .append(quote(state)).append(",\"weights\":[");
  for(int i=0;i<weights.length;i++){if(i>0)b.append(',');b.append(Double.toString(weights[i]));}
  return b.append("]}").toString();
 }
 private static byte[] policy(DqnTrainer t){
  ByteBuffer out=ByteBuffer.allocate(t.online.w.length*4).order(ByteOrder.LITTLE_ENDIAN);
  for(float value:t.online.w)out.putFloat(value);
  return out.array();
 }
 /** Return a short export summary for the Android UI. */
 public static String export(File appFiles,
    PaddleWeightLab.Weights trainedPaddle,OutputStream destination)throws IOException {
  if(appFiles==null||destination==null||!appFiles.isDirectory())
   throw new IOException("model export location");
  File rlDirectory=new File(appFiles,"arcade-rl");
  Archive a=new Archive(destination);
  int found=0,trained=0,missing=0,invalid=0;
  StringBuilder models=new StringBuilder("[");
  for(ArcadeEnv.Genre genre:ArcadeEnv.Genre.values()){
   if(models.length()>1)models.append(',');
   File source=new File(rlDirectory,genre.name()+".zip");
   String id=genre.name().toLowerCase(Locale.ROOT);
   if(!source.isFile()){
    missing++;
    models.append("{\"genre\":").append(quote(genre.name()))
      .append(",\"status\":\"not_trained_on_device\"}");
    continue;
   }
   try{
    if(source.length()<1||source.length()>MAX_CHECKPOINT_BYTES)
     throw new IOException("invalid size");
    // DqnTrainer.load validates stored SHA256 and the entire observation/action schema.
    DqnTrainer trainer=DqnTrainer.load(source,genre);
    // A saved but never optimized network is preserved, explicitly marked untrained.
    a.addFile("dqn/"+id+"/checkpoint.zip",source,MAX_CHECKPOINT_BYTES);
    a.add("dqn/"+id+"/policy.f32le",policy(trainer));
    a.add("dqn/"+id+"/policy.json",
      ("{\"format\":\"localpilot-dqn-policy-v1\",\"genre\":"+quote(genre.name())
       +",\"steps\":"+trainer.steps+",\"updates\":"+trainer.updates
       +",\"weights\":"+trainer.online.w.length
       +",\"dtype\":\"float32-le\",\"checkpoint_manifest\":"
       +trainer.manifest()+"}").getBytes(StandardCharsets.UTF_8));
    found++;
    if(trainer.updates>0)trained++;
    models.append("{\"genre\":").append(quote(genre.name()))
      .append(",\"status\":").append(quote(trainer.updates>0
        ?"trained_checkpoint_exported":"untrained_checkpoint_exported"))
      .append(",\"steps\":")
      .append(trainer.steps).append(",\"updates\":").append(trainer.updates)
      .append(",\"checkpoint\":").append(quote("dqn/"+id+"/checkpoint.zip"))
      .append(",\"policy\":").append(quote("dqn/"+id+"/policy.f32le")).append('}');
   }catch(IOException|IllegalArgumentException e){
    invalid++;
    models.append("{\"genre\":").append(quote(genre.name()))
      .append(",\"status\":\"invalid_checkpoint_not_exported\"}");
   }
  }
  models.append(']');
  for(WeightSpaceSkills.Genre genre:WeightSpaceSkills.Genre.values()){
   String name=genre.name().toLowerCase(Locale.ROOT);
   a.add("weightspace/"+name+".json",
     matrixJson(WeightSpaceSkills.Network.program(genre),genre.name(),
       "programmed_not_device_trained").getBytes(StandardCharsets.UTF_8));
  }
  a.add("weightspace/platform_recovery_patched.json",
   matrixJson(WeightSpaceSkills.patchRecovery(WeightSpaceSkills.Network.program(
     WeightSpaceSkills.Genre.PLATFORM_RECOVERY)),"PLATFORM_RECOVERY",
     "single_weight_programmed_patch_not_device_trained").getBytes(StandardCharsets.UTF_8));
  PaddleWeightLab.Weights base=PaddleWeightLab.Weights.followBall();
  a.add("paddle/follow_ball.json",paddleJson(base,"programmed_baseline")
    .getBytes(StandardCharsets.UTF_8));
  a.add("paddle/impact.json",paddleJson(base.patchToImpact(),"programmed_physics")
    .getBytes(StandardCharsets.UTF_8));
  boolean hasPaddle=trainedPaddle!=null;
  if(hasPaddle){
   a.add("paddle/trained_current.json",
     paddleJson(trainedPaddle,"trained_on_device_and_heldout_validated")
     .getBytes(StandardCharsets.UTF_8));
  }
  File live=new File(appFiles,"arcade-learning-v1.log");
  boolean hasLive=live.isFile()&&live.length()>0&&live.length()<=MAX_LIVE_MODEL_BYTES;
  if(hasLive)a.addFile("live/arcade-learning-v1.log",live,MAX_LIVE_MODEL_BYTES);
  a.add("README.txt",("LocalPilot Android -> PC model pack.\n"
    +"DQN checkpoint.zip contains replay, optimizer, target and online weights.\n"
    +"policy.f32le contains ONLY online inference weights in Java Net memory order.\n"
    +"Programmed WeightSpace matrices are not learned checkpoints.\n"
    +"Game policy settings may contain names of game apps and local slots.\n"
    +"PC must provide matching observation and action encoders; this archive "
    +"does not control desktop software automatically.\n"
    +"Missing/corrupt DQN files were not replaced by random weights.\n")
    .getBytes(StandardCharsets.UTF_8));
  a.add("manifest.json",("{\"format\":\""+FORMAT+"\","
    +"\"origin\":\"user_initiated_local_android_export\","
    +"\"dqn\":"+models+","
    +"\"dqn_exported\":"+found+",\"dqn_trained\":"+trained+",\"dqn_missing\":"+missing+",\"dqn_invalid\":"+invalid+","
    +"\"paddle_current_exported\":"+hasPaddle+",\"live_policy_store_exported\":"+hasLive+","
    +"\"weightspace_origin\":\"explicitly_programmed_not_trained\","
    +"\"compatibility\":\"python_reader_requires_matching_observation_schema\"}"
   ).getBytes(StandardCharsets.UTF_8));
  a.finish();
  return "Модели сохранены: DQN "+found+"/4 (обучены "+trained+"); нет "+missing
    +"; повреждены "+invalid+"; Карканоид "+(hasPaddle?"обучен":"базовые веса")
    +"; игровые настройки "+(hasLive?"включены":"отсутствуют");
 }
}
