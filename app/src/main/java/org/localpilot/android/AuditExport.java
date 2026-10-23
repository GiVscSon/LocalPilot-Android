package org.localpilot.android;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.zip.*;

/** Explicit local export of existing logs. Never creates a journal, migrates or rotates it. */
public final class AuditExport {
 public static final int MAX_SEGMENTS=8,MAX_ZIP=6*1024*1024;
 private AuditExport(){}
 public static byte[] snapshot(File filesDirectory)throws IOException{
  if(filesDirectory==null)throw new IllegalArgumentException("files directory");
  Path root=filesDirectory.toPath(),directory=root.resolve("audit-segments");
  LinkedHashMap<String,byte[]> entries=new LinkedHashMap<>();List<Path> segments=new ArrayList<>();
  if(Files.exists(directory)){
   if(!Files.isDirectory(directory)||Files.isSymbolicLink(directory))throw new IOException("invalid journal directory");
   try(DirectoryStream<Path> paths=Files.newDirectoryStream(directory,"segment-*.log")){
    for(Path p:paths)if(p.getFileName().toString().matches("segment-[0-9]{1,18}\\.log")){
     if(segments.size()>=32)throw new IOException("too many journal segments");segments.add(p);
    }
   }catch(DirectoryIteratorException e){throw new IOException("journal listing",e);}
   segments.sort(Comparator.comparingLong(AuditExport::id));
   if(segments.size()>MAX_SEGMENTS)segments=new ArrayList<>(segments.subList(segments.size()-MAX_SEGMENTS,segments.size()));
   for(Path p:segments)entries.put(p.getFileName().toString(),readLocked(p));
  }
  Path preserved=directory.resolve("legacy-v1.log"),legacy=root.resolve("audit-v1.log");
  if(Files.exists(preserved))entries.put("legacy-v1.log",readLocked(preserved));
  if(Files.exists(legacy)){
   byte[] bytes=readLocked(legacy);
   if(!Arrays.equals(bytes,entries.get("legacy-v1.log")))entries.put("legacy-root-v1.log",bytes);
  }
  long events=0;
  for(byte[] bytes:entries.values()){boolean content=false;for(byte b:bytes)if(b=='\n'){if(content)events++;content=false;}else content=true;}
  StringBuilder metadata=new StringBuilder("LocalPilot audit export-v2\nExplicit local export; no upload.\n")
   .append("status=").append(events==0?"no_journal_events":"verified_journal_events").append('\n')
   .append("segment_count=").append(segments.size()).append("\nevent_count=").append(events).append('\n');
  for(Map.Entry<String,byte[]> e:entries.entrySet())metadata.append(e.getKey()).append(" bytes=").append(e.getValue().length).append(" sha256=").append(sha(e.getValue())).append('\n');
  entries.put("metadata.txt",metadata.toString().getBytes(StandardCharsets.UTF_8));
  entries.put("README.txt",("Existing newest runtime segments are preferred; legacy sources are preserved separately when different.\n"
   +"Each log has an independent SHA256 chain validated before export.\n"
   +"No log is created, modified, migrated or rotated during this export.\n"
   +"Live files are read under bounded shared locks; entries may represent different committed instants.\n"
   +"status=no_journal_events means no events were available, not a successful gameplay session.\n"
   +"Android ACK is not proof of game motion. No images, URLs or credentials are added.\n").getBytes(StandardCharsets.UTF_8));
  ByteArrayOutputStream bytes=new ByteArrayOutputStream();
  try(ZipOutputStream zip=new ZipOutputStream(bytes,StandardCharsets.UTF_8)){
   for(Map.Entry<String,byte[]> entry:entries.entrySet()){
    zip.putNextEntry(new ZipEntry(entry.getKey()));zip.write(entry.getValue());zip.closeEntry();
   }
  }
  byte[] result=bytes.toByteArray();if(result.length==0||result.length>MAX_ZIP)throw new IOException("audit ZIP bound");return result;
 }
 private static long id(Path p){String name=p.getFileName().toString();return Long.parseLong(name.substring(8,name.length()-4));}
 private static byte[] readLocked(Path path)throws IOException{
  if(Files.isSymbolicLink(path)||!Files.isRegularFile(path))throw new IOException("invalid journal source");
  try(FileChannel channel=FileChannel.open(path,StandardOpenOption.READ)){
   for(int attempt=0;attempt<10;attempt++){
    FileLock lock=null;
    try{lock=channel.tryLock(0,Long.MAX_VALUE,true);}catch(OverlappingFileLockException busy){/* Existing local writer; bounded retry on export thread. */}
    if(lock!=null)try{
     long length=channel.size();if(length<0||length>SafeJournal.MAX)throw new IOException("journal size");
     ByteBuffer bytes=ByteBuffer.allocate((int)length);channel.position(0);
     while(bytes.hasRemaining())if(channel.read(bytes)<=0)throw new IOException("journal changed while reading");
     if(channel.size()!=length)throw new IOException("journal changed while reading");
     byte[] result=bytes.array();SafeJournal.validateSnapshot(result);return result;
    }finally{lock.release();}
    if(attempt<9)try{Thread.sleep(25);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException("audit export interrupted",e);}
   }
  }
  throw new IOException("journal busy; retry export");
 }
 private static String sha(byte[] bytes)throws IOException{
  try{byte[] hash=MessageDigest.getInstance("SHA-256").digest(bytes);StringBuilder s=new StringBuilder();for(byte b:hash){s.append(Character.forDigit(b>>>4&15,16));s.append(Character.forDigit(b&15,16));}return s.toString();}
  catch(NoSuchAlgorithmException e){throw new IOException("SHA256 unavailable",e);}
 }
}
