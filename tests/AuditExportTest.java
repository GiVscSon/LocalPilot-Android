package org.localpilot.android;

import java.io.*;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.*;

/** Existing log exports, including service-disconnected/no-log cases. No Android required. */
public final class AuditExportTest {
 private static int checks;
 private interface Work {void run()throws Exception;}
 private static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
 private static void reject(Work work,String why)throws Exception{try{work.run();throw new AssertionError(why);}catch(IOException expected){checks++;}}
 private static Map<String,byte[]> unzip(byte[] bytes)throws Exception{
  check(bytes.length>0&&bytes.length<=AuditExport.MAX_ZIP,"nonempty bounded ZIP bytes");
  LinkedHashMap<String,byte[]> files=new LinkedHashMap<>();
  try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(bytes),StandardCharsets.UTF_8)){
   ZipEntry entry;while((entry=zip.getNextEntry())!=null){check(!entry.getName().contains("/"),"fixed flat ZIP names");files.put(entry.getName(),zip.readAllBytes());zip.closeEntry();}
  }
  check(files.containsKey("README.txt")&&files.containsKey("metadata.txt"),"export metadata always present");
  for(Map.Entry<String,byte[]> e:files.entrySet())if(e.getKey().endsWith(".log")){SafeJournal.validateSnapshot(e.getValue());checks++;}
  return files;
 }
 private static String metadata(Map<String,byte[]> files){return new String(files.get("metadata.txt"),StandardCharsets.UTF_8);}
 private static File journal(Path root,String relative,int events)throws Exception{
  File file=root.resolve(relative).toFile();SafeJournal writer=new SafeJournal(file);
  for(int i=0;i<events;i++)writer.append(SafeJournal.Event.STOP,1,i,100+i,0);
  return file;
 }
 public static void main(String[]args)throws Exception{
  noLogs();legacyOnly();rotating();invalidSources();liveWriter();busyFailure();
  System.out.println("Audit export: "+checks+" assertions PASS; real temporary files and locks, no Android UI test");
 }
 private static void noLogs()throws Exception{
  Path parent=Files.createTempDirectory("pilot-audit-empty-"),missing=parent.resolve("not-created");
  Map<String,byte[]> zip=unzip(AuditExport.snapshot(missing.toFile()));
  check(metadata(zip).contains("status=no_journal_events")&&metadata(zip).contains("event_count=0"),"empty source is explicit, not a successful gameplay claim");
  check(!Files.exists(missing),"read-only export creates no source directories");
 }
 private static void legacyOnly()throws Exception{
  Path root=Files.createTempDirectory("pilot-audit-legacy-");File file=journal(root,"audit-v1.log",2);byte[] original=Files.readAllBytes(file.toPath());
  Map<String,byte[]> zip=unzip(AuditExport.snapshot(root.toFile()));
  check(Arrays.equals(original,zip.get("legacy-root-v1.log")),"legacy-only data exported as log inside real ZIP");
  check(metadata(zip).contains("event_count=2"),"legacy event count");
  check(Arrays.equals(original,Files.readAllBytes(file.toPath())),"legacy source unchanged");
  check(!Files.exists(root.resolve("audit-segments")),"export does not migrate legacy source");
 }
 private static void rotating()throws Exception{
  Path root=Files.createTempDirectory("pilot-audit-segments-");File old=journal(root,"audit-v1.log",1);
  RotatingJournal writer=new RotatingJournal(root.resolve("audit-segments").toFile(),old,1024);
  for(int i=0;i<35;i++){long req=writer.nextRequest();writer.append(SafeJournal.Event.PROPOSED,2,req,200+i,0);}
  Map<String,byte[]> before=new TreeMap<>();try(var paths=Files.walk(root)){for(Path p:paths.filter(Files::isRegularFile).toList())before.put(root.relativize(p).toString(),Files.readAllBytes(p));}
  Map<String,byte[]> zip=unzip(AuditExport.snapshot(root.toFile()));
  check(zip.keySet().stream().filter(n->n.startsWith("segment-")).count()==8,"newest runtime segments exported when service absent");
  check(zip.containsKey("legacy-v1.log")&&!zip.containsKey("legacy-root-v1.log"),"identical preserved/root legacy deduplicated");
  check(metadata(zip).contains("status=verified_journal_events"),"available verified data explicit");
  for(Map.Entry<String,byte[]> e:before.entrySet())check(Arrays.equals(e.getValue(),Files.readAllBytes(root.resolve(e.getKey()))),"existing journal preserved: "+e.getKey());
  long after;try(var paths=Files.walk(root)){after=paths.filter(Files::isRegularFile).count();}check(after==before.size(),"no new journal or writer file created");
  Path many=Files.createTempDirectory("pilot-audit-many-");
  for(int i=0;i<9;i++)journal(many,"audit-segments/segment-"+i+".log",1);
  zip=unzip(AuditExport.snapshot(many.toFile()));
  check(!zip.containsKey("segment-0.log")&&zip.containsKey("segment-8.log"),"latest-eight selection is numeric and bounded");
  check(Files.exists(many.resolve("audit-segments/segment-0.log")),"export does not prune old files");
 }
 private static void invalidSources()throws Exception{
  Path root=Files.createTempDirectory("pilot-audit-invalid-");File file=journal(root,"audit-segments/segment-0.log",1);byte[] good=Files.readAllBytes(file.toPath());
  byte[] truncated=Arrays.copyOf(good,good.length-1);Files.write(file.toPath(),truncated);
  reject(()->AuditExport.snapshot(root.toFile()),"truncated chain must fail");
  check(Arrays.equals(truncated,Files.readAllBytes(file.toPath())),"bad source preserved for diagnosis");
  byte[] corrupted=good.clone();corrupted[corrupted.length-2]=corrupted[corrupted.length-2]=='a'?(byte)'b':(byte)'a';Files.write(file.toPath(),corrupted);
  reject(()->AuditExport.snapshot(root.toFile()),"checksum mismatch must fail");
  Files.write(file.toPath(),new byte[SafeJournal.MAX+1]);reject(()->AuditExport.snapshot(root.toFile()),"oversized journal must fail before allocation");
  Files.delete(file.toPath());Files.createSymbolicLink(file.toPath(),root.resolve("outside"));reject(()->AuditExport.snapshot(root.toFile()),"journal symlink rejected");
  reject(()->SafeJournal.validateSnapshot(null),"null snapshot rejected");
 }
 private static void liveWriter()throws Exception{
  Path root=Files.createTempDirectory("pilot-audit-live-");File file=journal(root,"audit-segments/segment-0.log",1);
  byte[] first=Files.readAllBytes(file.toPath());Path prepared=root.resolve("draft.log");Files.write(prepared,first);
  new SafeJournal(prepared.toFile()).append(SafeJournal.Event.ANDROID_COMPLETED,1,2,300,0);byte[] full=Files.readAllBytes(prepared);
  ExecutorService worker=Executors.newSingleThreadExecutor();
  try(RandomAccessFile writer=new RandomAccessFile(file,"rw");FileLock lock=writer.getChannel().lock()){
   writer.seek(first.length);writer.write(full,first.length,(full.length-first.length)/2);writer.getFD().sync();
   Future<byte[]> future=worker.submit(()->AuditExport.snapshot(root.toFile()));
   Thread.sleep(60);check(!future.isDone(),"reader waits for exclusive writer, not partial append");
   writer.seek(0);writer.write(full);writer.setLength(full.length);writer.getFD().sync();lock.release();
   Map<String,byte[]> zip=unzip(future.get(3,TimeUnit.SECONDS));
   check(Arrays.equals(full,zip.get("segment-0.log")),"export includes fully committed live append");
  }finally{worker.shutdownNow();}
 }
 private static void busyFailure()throws Exception{
  Path root=Files.createTempDirectory("pilot-audit-busy-");File file=journal(root,"audit-segments/segment-0.log",1);
  ExecutorService worker=Executors.newSingleThreadExecutor();
  try(RandomAccessFile writer=new RandomAccessFile(file,"rw");FileLock lock=writer.getChannel().lock()){
   Future<Boolean> future=worker.submit(()->{try{AuditExport.snapshot(root.toFile());return false;}catch(IOException expected){return true;}});
   check(future.get(3,TimeUnit.SECONDS),"busy writer fails within bounded retries, no hanging UI/export");
  }finally{worker.shutdownNow();}
 }
}
