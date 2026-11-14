package org.localpilot.android;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

/** Verified local model-inference attempts. This does NOT claim the NES moved.
 * Independent GAME result is stored in ConsoleExperienceMemory only after ACK.
 */
public final class HarnessModelExecutionLog {
 private HarnessModelExecutionLog(){}
 public static final String FILE="harness-model-execution-v1.log";
 private static final String OLD="harness-model-execution-v1.previous.log";
 private static final String SCHEMA="NES_MODEL_INFERENCE_V1";
 private static final int MAX_BYTES=128*1024,MAX_ROW=450;
 public record Run(String modelId,String modelSha,int frame,int frameHash,
   long latencyMs,String proposedButton,int gpuLayers){}
 public record Summary(int inferenceCalls,int validReplies,int rejectedReplies,
    long lastLatencyMs,String lastModelId,int lastGpuLayers){}
 private static boolean identity(String id,String sha){
  return id!=null&&id.matches("[A-Za-z0-9:_.-]{1,96}")&&
    sha!=null&&sha.matches("[0-9a-f]{64}");
 }
 private static String hash(String body){
  try{
   byte[] d=MessageDigest.getInstance("SHA-256").digest(body.getBytes(StandardCharsets.UTF_8));
   StringBuilder s=new StringBuilder(64);
   for(byte b:d)s.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
   return s.toString();
  }catch(NoSuchAlgorithmException missing){throw new IllegalStateException(missing);}
 }
 private static boolean proposal(String s){
  return s!=null&&(s.equals("A")||s.equals("B")||s.equals("UP")||
    s.equals("DOWN")||s.equals("LEFT")||s.equals("RIGHT")||s.equals("NONE")||
    s.equals("REJECTED"));
 }
 private static String payload(Run r){
  return SCHEMA+"|"+r.modelId()+"|"+r.modelSha()+"|"+r.frame()+"|"+
   Integer.toUnsignedString(r.frameHash())+"|"+r.latencyMs()+"|"+
   r.proposedButton()+"|"+r.gpuLayers();
 }
 private static void validate(Run r){
  if(r==null||!identity(r.modelId(),r.modelSha())||r.frame()<15||r.frameHash()==0||
     r.latencyMs()<0||r.latencyMs()>3600000||r.gpuLayers()<0||r.gpuLayers()>512||
     !proposal(r.proposedButton()))
   throw new IllegalArgumentException("Invalid observed inference metadata");
 }
 private static void load(Path p,List<Run> all)throws IOException{
  if(!Files.exists(p,LinkOption.NOFOLLOW_LINKS))return;
  if(Files.isSymbolicLink(p)||!Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)||
      Files.size(p)>MAX_BYTES)throw new IOException("Unsafe model inference journal");
  for(String line:Files.readAllLines(p,StandardCharsets.UTF_8)){
   try{
    if(line.length()>MAX_ROW||line.isBlank())throw new IllegalArgumentException();
    String[] parts=line.split("\\|",-1);
    if(parts.length!=9||!SCHEMA.equals(parts[0])||
       !MessageDigest.isEqual(hash(line.substring(0,line.lastIndexOf('|')))
         .getBytes(StandardCharsets.US_ASCII),
         parts[8].getBytes(StandardCharsets.US_ASCII)))throw new IllegalArgumentException();
    long parsed=Long.parseUnsignedLong(parts[4]);
    if(parsed>0xffffffffL)throw new IllegalArgumentException();
    Run r=new Run(parts[1],parts[2],Integer.parseInt(parts[3]),(int)parsed,
       Long.parseLong(parts[5]),parts[6],Integer.parseInt(parts[7]));
    validate(r);all.add(r);
   }catch(RuntimeException bad){throw new IOException("Model inference journal checksum/schema failure",bad);}
  }
 }
 public static synchronized List<Run> read(Path directory)throws IOException{
  List<Run> rows=new ArrayList<>();
  load(directory.resolve(OLD),rows);load(directory.resolve(FILE),rows);
  return List.copyOf(rows);
 }
 public static synchronized void append(Path directory,Run data)throws IOException{
  validate(data);
  Files.createDirectories(directory);
  read(directory); // No append to corrupt previous generations.
  String body=payload(data);
  byte[] entry=(body+"|"+hash(body)+"\n").getBytes(StandardCharsets.UTF_8);
  if(entry.length>MAX_ROW)throw new IOException("Oversized model run");
  Path target=directory.resolve(FILE);
  if(Files.isSymbolicLink(target))throw new IOException("Model run symlink");
  long used=Files.exists(target)?Files.size(target):0;
  if(used+entry.length>MAX_BYTES){
   Path backup=directory.resolve(OLD);
   if(Files.isSymbolicLink(backup))throw new IOException("Model run backup symlink");
   Files.move(target,backup,StandardCopyOption.REPLACE_EXISTING);
  }
  try(FileOutputStream output=new FileOutputStream(target.toFile(),true)){
   output.write(entry);output.getFD().sync();
  }
 }
 public static Summary summary(Path dir)throws IOException{
  List<Run> runs=read(dir);
  int valid=0,invalid=0;
  for(Run r:runs){if("REJECTED".equals(r.proposedButton()))invalid++;else valid++;}
  Run last=runs.isEmpty()?null:runs.get(runs.size()-1);
  return new Summary(runs.size(),valid,invalid,last==null?0:last.latencyMs(),
    last==null?"":last.modelId(),last==null?0:last.gpuLayers());
 }
}
