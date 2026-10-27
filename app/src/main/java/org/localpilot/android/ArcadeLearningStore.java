package org.localpilot.android;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Small atomic store for learned visual-game motion models. */
public final class ArcadeLearningStore {
 private static final java.util.concurrent.ConcurrentHashMap<String,Object> JVM_LOCKS=new java.util.concurrent.ConcurrentHashMap<>();
 private final File file,lockFile;
 private final Map<String,String> models=new HashMap<>();
 public ArcadeLearningStore(File file)throws IOException{this.file=file;this.lockFile=new File(file.getPath()+".lock");File p=file.getParentFile();if(p!=null&&!p.isDirectory()&&!p.mkdirs())throw new IOException("directory");load();}
 private Map<String,String> readModels(boolean strict)throws IOException{
  Map<String,String> result=new HashMap<>();if(!file.exists())return result;
  try(BufferedReader r=new BufferedReader(new FileReader(file))){String line;boolean first=true;
   while((line=r.readLine())!=null){
    if(first&&line.equals("LOCALPILOT-ARCADE-LEARNING-v1")){first=false;continue;}
    first=false;int i=line.indexOf('|');
    if(i<=0||i==line.length()-1||line.indexOf('|',i+1)>=0){if(strict)throw new IOException("invalid learning store; existing file preserved");continue;}
    String key=line.substring(0,i),value=line.substring(i+1);
    if(strict&&result.containsKey(key))throw new IOException("duplicate learning key; existing file preserved");
    result.put(key,value);
   }
  }return result;
 }
 private void load()throws IOException{models.clear();models.putAll(readModels(false));}
 public synchronized String get(ArcadeProfile profile){return models.get(profile.name());}
 public synchronized String get(String pkg,String slot,ArcadeProfile profile){String value=models.get(VisualGameTarget.key(pkg,slot,profile));return value==null&&VisualGameTarget.legacyMarket(pkg)&&"default".equals(slot)?get(profile):value;}
 public synchronized long losses(String pkg,String slot,ArcadeProfile profile){String value=models.get(VisualGameTarget.key(pkg,slot,profile)+".losses");try{return value==null&&VisualGameTarget.legacyMarket(pkg)&&"default".equals(slot)?losses(profile):value==null?0:Math.max(0,Long.parseLong(value));}catch(NumberFormatException ignored){return 0;}}
 public synchronized void save(String pkg,String slot,ArcadeProfile profile,String model)throws IOException{saveKey(VisualGameTarget.key(pkg,slot,profile),model);}
 public synchronized void recordResult(String pkg,String slot,ArcadeProfile profile,String model)throws IOException{String key=VisualGameTarget.key(pkg,slot,profile);saveKey(key,model,true,VisualGameTarget.legacyMarket(pkg)&&"default".equals(slot)?profile.name()+".losses":null);}
 public synchronized void recordResult(ArcadeProfile profile,String model)throws IOException{
  if(profile==null)throw new IOException("invalid profile");saveKey(profile.name(),model,true,null);
 }
 public synchronized long losses(ArcadeProfile profile){try{return Math.max(0,Long.parseLong(models.getOrDefault(profile.name()+".losses","0")));}catch(NumberFormatException ignored){return 0;}}
 public synchronized void save(ArcadeProfile profile,String model)throws IOException{if(profile==null)throw new IOException("invalid profile");saveKey(profile.name(),model);}
 private void saveKey(String key,String model)throws IOException{saveKey(key,model,false,null);}
 private void saveKey(String key,String model,boolean increment,String fallback)throws IOException{
  if(model==null||model.isEmpty()||model.indexOf('\n')>=0||model.indexOf('\r')>=0||model.indexOf('|')>=0)throw new IOException("invalid model");
  Object mutex=JVM_LOCKS.computeIfAbsent(lockFile.getCanonicalPath(),ignored->new Object());
  synchronized(mutex){
  try(RandomAccessFile handle=new RandomAccessFile(lockFile,"rw");java.nio.channels.FileLock ignored=handle.getChannel().lock()){
  Map<String,String> staged=new TreeMap<>(readModels(true));staged.put(key,model);
  if(increment){String raw=staged.get(key+".losses");if(raw==null&&fallback!=null)raw=staged.get(fallback);long losses=0;
   try{if(raw!=null)losses=Math.max(0,Long.parseLong(raw));}catch(NumberFormatException ignoredValue){}
   staged.put(key+".losses",Long.toString(losses>=1_000_000?1_000_000:losses+1));}
  StringBuilder b=new StringBuilder("LOCALPILOT-ARCADE-LEARNING-v1\n");
  for(Map.Entry<String,String> e:staged.entrySet())b.append(e.getKey()).append('|').append(e.getValue()).append('\n');
  File t=File.createTempFile("arcade-learning-", ".pending",file.getAbsoluteFile().getParentFile());
  try{
   try(FileOutputStream out=new FileOutputStream(t)){out.write(b.toString().getBytes(StandardCharsets.UTF_8));out.getFD().sync();}
   Files.move(t.toPath(),file.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
   // Readers must see only the last committed model, even when disk writes fail.
   models.clear();models.putAll(staged);
  }finally{Files.deleteIfExists(t.toPath());}
  }catch(java.nio.channels.OverlappingFileLockException busy){throw new IOException("store busy",busy);}
  }
 }
}
