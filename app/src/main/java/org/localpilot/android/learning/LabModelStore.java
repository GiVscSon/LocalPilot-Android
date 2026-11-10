package org.localpilot.android.learning;

import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** Content-addressed laboratory versions. Never writes production model storage. */
public final class LabModelStore {
 public static final int MAX_BYTES=8*1024*1024, MAX_VERSIONS=16;
 public record Version(String sha256,long bytes,boolean selected,boolean valid){}
 public record Training(boolean accepted,String report){}
 private final Path root;
 public LabModelStore(File privateRoot)throws IOException{
  root=new File(Objects.requireNonNull(privateRoot),"lab-model-versions-v1").toPath();
  Files.createDirectories(root);
 }
 private Path folder(TaskSchema.GameId game)throws IOException{
  Path dir=root.resolve(Objects.requireNonNull(game).id);Files.createDirectories(dir);return dir;
 }
 private static String digest(Path path)throws IOException{
  try(InputStream in=Files.newInputStream(path)){
   MessageDigest d=MessageDigest.getInstance("SHA-256");byte[] b=new byte[8192];int n;
   while((n=in.read(b))!=-1)d.update(b,0,n);
   StringBuilder s=new StringBuilder();for(byte v:d.digest())s.append(String.format(Locale.ROOT,"%02x",v&255));return s.toString();
  }catch(NoSuchAlgorithmException e){throw new IOException(e);}
 }
 private static void checkHash(String hash){
  if(hash==null||!hash.matches("[a-f0-9]{64}"))throw new IllegalArgumentException("invalid version hash");
 }
 public synchronized String selected(TaskSchema.GameId game)throws IOException{
  Path pointer=folder(game).resolve("selected");if(!Files.exists(pointer))return "";
  if(Files.size(pointer)!=64)throw new IOException("invalid selected version");
  String hash=new String(Files.readAllBytes(pointer),java.nio.charset.StandardCharsets.UTF_8);
  try{checkHash(hash);}catch(IllegalArgumentException invalid){throw new IOException("invalid selected version",invalid);}
  return hash;
 }
 private Path checked(TaskSchema.GameId game,String hash)throws IOException{
  checkHash(hash);Path path=folder(game).resolve(hash+".zip");
  if(!Files.isRegularFile(path)||Files.size(path)>MAX_BYTES||!digest(path).equals(hash))
   throw new IOException("model version missing or corrupted");
  ModelCheckpoint.load(path.toFile(),TaskSchema.forGame(game));return path;
 }
 public synchronized List<Version> versions(TaskSchema.GameId game)throws IOException{
  // A damaged pointer must not hide intact versions or prevent explicit rollback.
  String active;try{active=selected(game);}catch(IOException invalid){active="";}
  List<Version> out=new ArrayList<>();
  try(var paths=Files.list(folder(game))){
   for(Path p:paths.filter(p->p.getFileName().toString().matches("[a-f0-9]{64}\\.zip")).sorted().collect(java.util.stream.Collectors.toList())){
    String hash=p.getFileName().toString().substring(0,64);
    boolean valid;try{checked(game,hash);valid=true;}catch(IOException rejected){valid=false;}
    out.add(new Version(hash,Files.size(p),hash.equals(active),valid));
   }
  }
  return List.copyOf(out);
 }
 public synchronized String selectionProblem(TaskSchema.GameId game){
  try{String hash=selected(game);if(hash.isEmpty())return "LAB-версия не выбрана";checked(game,hash);return "";}
  catch(IOException invalid){return "Выбор LAB-версии повреждён. Выберите исправную версию для отката или импортируйте ZIP.";}
 }
 public synchronized String importVersion(TaskSchema.GameId game,InputStream input)throws IOException{
  Objects.requireNonNull(input);Path dir=folder(game),stage=Files.createTempFile(dir,"import-",".pending");
  try{
   try(OutputStream out=Files.newOutputStream(stage)){
    byte[] b=new byte[8192];int n,total=0;
    while((n=input.read(b))!=-1){total+=n;if(total>MAX_BYTES)throw new IOException("model exceeds 8 MiB");out.write(b,0,n);}
   }
   ModelCheckpoint.load(stage.toFile(),TaskSchema.forGame(game));
   String hash=digest(stage);Path target=dir.resolve(hash+".zip");
   if(!Files.exists(target)){
    if(versions(game).size()>=MAX_VERSIONS)throw new IOException("version limit reached; export existing history first");
    Files.move(stage,target,StandardCopyOption.ATOMIC_MOVE);
   }else{
    try{checked(game,hash);}
    catch(IOException corrupted){
     // Only the independently validated incoming bytes may repair this hash.
     // Preserve the damaged object for diagnosis; never overwrite user history.
     Path backup=dir.resolve(hash+".corrupt-"+Long.toHexString(System.nanoTime()));
     Files.move(target,backup,StandardCopyOption.ATOMIC_MOVE);
     Files.move(stage,target,StandardCopyOption.ATOMIC_MOVE);
    }
   }
   select(game,hash);return hash;
  }finally{Files.deleteIfExists(stage);}
 }
 public synchronized void select(TaskSchema.GameId game,String hash)throws IOException{
  checked(game,hash);Path stage=Files.createTempFile(folder(game),"selected-",".pending");
  try{
   Files.write(stage,hash.getBytes(java.nio.charset.StandardCharsets.UTF_8));
   Files.move(stage,folder(game).resolve("selected"),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
  }finally{Files.deleteIfExists(stage);}
 }
 public synchronized void exportVersion(TaskSchema.GameId game,String hash,OutputStream output)throws IOException{
  Path path=checked(game,hash);try(InputStream in=Files.newInputStream(path)){byte[] bytes=new byte[8192];int n;while((n=in.read(bytes))!=-1)output.write(bytes,0,n);}
 }
 public synchronized StudentPolicy loadSelected(TaskSchema.GameId game)throws IOException{
  return ModelCheckpoint.load(checked(game,selected(game)).toFile(),TaskSchema.forGame(game));
 }
 public synchronized void ensureBundled(TaskSchema.GameId game,BundledModelInstaller.AssetSource assets)throws IOException{
  if(!selected(game).isEmpty()){checked(game,selected(game));return;}
  try(InputStream in=assets.open("adaptive-engine-v1/"+game.id+"-"+game.mechanicsVersion+".zip")){
   importVersion(game,in);
  }
 }
 /** Bounded, cancellable between games. Publication changes only LAB selection. */
 public synchronized Training train(TaskSchema.GameId game,long seed)throws IOException{
  AdaptiveLearningEngine engine=new AdaptiveLearningEngine(null);
  engine.replaceActivePolicy(game,loadSelected(game));
  ModelEvaluator.PromotionDecision decision=engine.trainCurriculumAndPromote(game,8,24,seed);
  if(!decision.accepted)return new Training(false,"SIMULATED_GAME · "+decision.summary);
  Path temporary=Files.createTempFile(root,"trained-",".zip");Files.delete(temporary);
  try{
   ModelCheckpoint.save(temporary.toFile(),TaskSchema.forGame(game),engine.activePolicy(game));
   try(InputStream in=Files.newInputStream(temporary)){importVersion(game,in);}
   return new Training(true,"SIMULATED_GAME · "+decision.summary);
  }finally{Files.deleteIfExists(temporary);Files.deleteIfExists(temporary.resolveSibling(temporary.getFileName()+".bak"));}
 }
 public synchronized String evaluate(TaskSchema.GameId game,long seed)throws IOException{
  return "SIMULATED_GAME\n"+ModelEvaluator.evaluatePolicy("SELECTED_LAB",game,loadSelected(game),16,seed).format();
 }
}
