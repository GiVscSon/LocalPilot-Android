package org.localpilot.android;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Small atomic store for learned visual-game motion models. */
public final class ArcadeLearningStore {
 private final File file;
 private final Map<String,String> models=new HashMap<>();
 public ArcadeLearningStore(File file)throws IOException{this.file=file;File p=file.getParentFile();if(p!=null&&!p.isDirectory()&&!p.mkdirs())throw new IOException("directory");load();}
 private void load()throws IOException{if(!file.exists())return;try(BufferedReader r=new BufferedReader(new FileReader(file))){String line;while((line=r.readLine())!=null){int i=line.indexOf('|');if(i>0&&i<line.length()-1)models.put(line.substring(0,i),line.substring(i+1));}}}
 public synchronized String get(ArcadeProfile profile){return models.get(profile.name());}
 public synchronized String get(String pkg,String slot,ArcadeProfile profile){String value=models.get(VisualGameTarget.key(pkg,slot,profile));return value==null&&VisualGameTarget.legacyMarket(pkg)&&"default".equals(slot)?get(profile):value;}
 public synchronized long losses(String pkg,String slot,ArcadeProfile profile){String value=models.get(VisualGameTarget.key(pkg,slot,profile)+".losses");try{return value==null&&VisualGameTarget.legacyMarket(pkg)&&"default".equals(slot)?losses(profile):value==null?0:Math.max(0,Long.parseLong(value));}catch(NumberFormatException ignored){return 0;}}
 public synchronized void save(String pkg,String slot,ArcadeProfile profile,String model)throws IOException{saveKey(VisualGameTarget.key(pkg,slot,profile),model);}
 public synchronized void recordResult(String pkg,String slot,ArcadeProfile profile,String model)throws IOException{String key=VisualGameTarget.key(pkg,slot,profile);long count=losses(pkg,slot,profile);models.put(key+".losses",Long.toString(Math.min(1_000_000,count+1)));saveKey(key,model);}
 public synchronized void recordResult(ArcadeProfile profile,String model)throws IOException{
  String key=profile.name()+".losses";long count=losses(profile);models.put(key,Long.toString(Math.min(1_000_000,count+1)));save(profile,model);
 }
 public synchronized long losses(ArcadeProfile profile){try{return Math.max(0,Long.parseLong(models.getOrDefault(profile.name()+".losses","0")));}catch(NumberFormatException ignored){return 0;}}
 public synchronized void save(ArcadeProfile profile,String model)throws IOException{if(profile==null)throw new IOException("invalid profile");saveKey(profile.name(),model);}
 private void saveKey(String key,String model)throws IOException{if(model==null||model.indexOf('\n')>=0||model.indexOf('\r')>=0||model.indexOf('|')>=0)throw new IOException("invalid model");models.put(key,model);StringBuilder b=new StringBuilder("LOCALPILOT-ARCADE-LEARNING-v1\n");for(Map.Entry<String,String> e:models.entrySet())b.append(e.getKey()).append('|').append(e.getValue()).append('\n');File t=File.createTempFile("arcade-learning-", ".pending",file.getAbsoluteFile().getParentFile());try(FileOutputStream out=new FileOutputStream(t)){out.write(b.toString().getBytes(StandardCharsets.UTF_8));out.getFD().sync();}try{Files.move(t.toPath(),file.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}finally{Files.deleteIfExists(t.toPath());}}
}
