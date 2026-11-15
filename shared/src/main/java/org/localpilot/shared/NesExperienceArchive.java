package org.localpilot.shared;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/** Private, explicit retention of verified NES experience. Never imports training
 * data, weights, ROMs or input authority. A sealed read-back-verified archive must
 * exist BEFORE retiring any active row. Interrupted retirement can leave duplicate
 * active rows, but every retired byte remains in the durable archive.
 */
public final class NesExperienceArchive {
 private NesExperienceArchive(){}
 public static final int MAX_ARCHIVES=8,MAX_BYTES=6*1024*1024;
 private static final String GOALS="console-ai-homebrew-goal-v1.log";
 private static final String NAME="experience-[0-9a-f]{32}\\.zip";
 public record Receipt(String name,String sha256,int goalRows,int replayBatches){}
 @FunctionalInterface public interface DirectorySync {void sync(Path directory)throws IOException;}
 private static String sha(byte[] bytes){
  try{
   StringBuilder s=new StringBuilder();
   for(byte b:MessageDigest.getInstance("SHA-256").digest(bytes))
    s.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
   return s.toString();
  }catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
 }
 private static Path child(Path parent,String name)throws IOException{
  if(Files.isSymbolicLink(parent)||!Files.isDirectory(parent,LinkOption.NOFOLLOW_LINKS))
   throw new IOException("Unsafe NES archive parent");
  Path p=parent.resolve(name);
  if(Files.isSymbolicLink(p))throw new IOException("Symlink NES archive path");
  Files.createDirectories(p);return p;
 }
 private static Path directory(Path files)throws IOException{
  return child(child(child(child(files,"localpilot_vault"),"exports"),"nes-experience-v1"),"sealed");
 }
 public static synchronized List<String> list(Path files)throws IOException{
  Path dir=directory(files);List<String> names=new ArrayList<>();
  try(DirectoryStream<Path> entries=Files.newDirectoryStream(dir)){
   for(Path p:entries){
    if(!p.getFileName().toString().matches(NAME)||Files.isSymbolicLink(p)||
       !Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)||Files.size(p)>MAX_BYTES)
     throw new IOException("Foreign or oversized NES archive; preserved for diagnosis");
    names.add(p.getFileName().toString());
    if(names.size()>MAX_ARCHIVES)throw new IOException("NES archive quota exceeded");
   }
  }
  java.util.Collections.sort(names);return List.copyOf(names);
 }
 private static byte[] bounded(Path p,int max)throws IOException{
  if(Files.isSymbolicLink(p)||!Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)||Files.size(p)>max)
   throw new IOException("Unsafe or oversized experience input");
  byte[] bytes=Files.readAllBytes(p);
  if(bytes.length>max)throw new IOException("Experience changed size during read");
  return bytes;
 }
 private static String manifest(Map<String,byte[]> input){
  StringBuilder m=new StringBuilder("LOCALPILOT_NES_EXPERIENCE_ARCHIVE_V1\nsha256,bytes,path\n");
  for(var e:input.entrySet())m.append(sha(e.getValue())).append(',').append(e.getValue().length)
    .append(',').append(e.getKey()).append('\n');
  return m.toString();
 }
 private static void verify(byte[] zip)throws IOException{
  if(zip.length>MAX_BYTES)throw new IOException("NES archive byte quota");
  Map<String,byte[]> input=new LinkedHashMap<>();String declared=null;int total=0;
  try(ZipInputStream in=new ZipInputStream(new java.io.ByteArrayInputStream(zip))){
   ZipEntry entry;
   while((entry=in.getNextEntry())!=null){
    String n=entry.getName();
    if(entry.isDirectory()||input.size()>65||!(n.equals("MANIFEST.csv")||n.equals(GOALS)||
      n.matches("replay/[0-9a-f]{64}\\.lpr")))throw new IOException("Foreign NES ZIP entry");
    ByteArrayOutputStream data=new ByteArrayOutputStream();byte[] buf=new byte[8192];int count;
    while((count=in.read(buf))!=-1){
     total+=count;if(total>MAX_BYTES)throw new IOException("Expanded NES archive quota");
     data.write(buf,0,count);
    }
    byte[] bytes=data.toByteArray();
    if(n.equals("MANIFEST.csv")){
     if(declared!=null||bytes.length>16384)throw new IOException("Duplicate/oversized archive manifest");
     declared=new String(bytes,StandardCharsets.UTF_8);
    }else if(input.put(n,bytes)!=null)throw new IOException("Duplicate NES archive entry");
   }
  }
  if(input.isEmpty()||!manifest(input).equals(declared))throw new IOException("NES archive checksums differ");
 }
 public static synchronized byte[] read(Path files,String name)throws IOException{
  if(name==null||!name.matches(NAME))throw new IOException("Invalid NES archive name");
  byte[] zip=bounded(directory(files).resolve(name),MAX_BYTES);verify(zip);return zip;
 }
 /** Caller must provide the SHA of bytes READ BACK from the external destination.
  * A cancelled picker/failed write must never call this method. */
 public static synchronized void retireExported(Path files,String name,String readBackSha)throws IOException{
  byte[] bytes=read(files,name);
  if(!sha(bytes).equals(readBackSha))throw new IOException("External export readback SHA mismatch");
  Files.delete(directory(files).resolve(name));
 }
 public static synchronized Receipt archiveAndRotate(Path files)throws IOException{
  return archiveAndRotate(files,dir->{
   try(java.nio.channels.FileChannel channel=java.nio.channels.FileChannel.open(dir,java.nio.file.StandardOpenOption.READ)){
    channel.force(true);
   }
  });
 }
 /** Android supplies Os.open/fstat/fsync; JVM uses FileChannel. */
 public static synchronized Receipt archiveAndRotate(Path files,DirectorySync sync)throws IOException{
  synchronized(ConsoleExperienceMemory.class){synchronized(VerifiedPpoReplayStore.class){
   if(sync==null)throw new IOException("Archive directory fsync is required");
   if(Thread.currentThread().isInterrupted())throw new IOException("Archive cancelled before commit");
   if(list(files).size()>=MAX_ARCHIVES)throw new IOException("Export a saved archive before making another");
   var goals=ConsoleExperienceMemory.readHomebrewGoals(files);
   Path vault=child(files,"localpilot_vault");
   var summary=VerifiedPpoReplayStore.summary(vault);
   Map<String,byte[]> input=new LinkedHashMap<>();Map<Path,byte[]> originals=new LinkedHashMap<>();
   Path journal=files.resolve(GOALS);
   if(Files.exists(journal,LinkOption.NOFOLLOW_LINKS)){
    byte[] bytes=bounded(journal,131072);input.put(GOALS,bytes);originals.put(journal,bytes);
   }
   Path replay=vault.resolve("episodes/onpolicy-v1");List<Path> paths=new ArrayList<>();
   try(DirectoryStream<Path> entries=Files.newDirectoryStream(replay)){for(Path p:entries)paths.add(p);}
   paths.sort(java.util.Comparator.comparing(p->p.getFileName().toString()));
   for(Path p:paths){
    VerifiedPpoReplayStore.read(p);byte[] bytes=bounded(p,65536);
    input.put("replay/"+p.getFileName(),bytes);originals.put(p,bytes);
   }
   if(goals.isEmpty()&&summary.batches()==0)throw new IOException("No verified NES experience to archive");
   ByteArrayOutputStream data=new ByteArrayOutputStream();
   try(ZipOutputStream zip=new ZipOutputStream(data)){
    for(var e:input.entrySet()){
     zip.putNextEntry(new ZipEntry(e.getKey()));zip.write(e.getValue());zip.closeEntry();
    }
    zip.putNextEntry(new ZipEntry("MANIFEST.csv"));
    zip.write(manifest(input).getBytes(StandardCharsets.UTF_8));zip.closeEntry();
   }
   byte[] bytes=data.toByteArray();verify(bytes);
   Path dir=directory(files),temp=Files.createTempFile(dir,"archive-",".pending");
   String name="experience-"+UUID.randomUUID().toString().replace("-","")+".zip";
   try{
    try(FileOutputStream out=new FileOutputStream(temp.toFile())){out.write(bytes);out.getFD().sync();}
    if(!MessageDigest.isEqual(bytes,bounded(temp,MAX_BYTES)))throw new IOException("Archive fsync readback mismatch");
    Files.move(temp,dir.resolve(name),StandardCopyOption.ATOMIC_MOVE);
   }finally{Files.deleteIfExists(temp);}
   // Seal and verify before the first deletion. On an I/O failure, never delete
   // the sealed archive: it is the recovery copy even if rotation was partial.
   read(files,name);
   // Persist the renamed archive and newly created parent directories BEFORE
   // deleting active records. If fsync fails, keep both copies for recovery.
   for(Path p=dir;;p=p.getParent()){
    sync.sync(p);if(p.equals(files))break;
   }
   if(Thread.currentThread().isInterrupted())throw new IOException("Archive saved; rotation cancelled");
   for(var e:originals.entrySet())if(!MessageDigest.isEqual(e.getValue(),bounded(e.getKey(),131072)))
    throw new IOException("Experience changed; archive saved, rotation refused");
   for(Path p:originals.keySet())Files.delete(p);
   return new Receipt(name,sha(bytes),goals.size(),summary.batches());
  }}
 }
}
