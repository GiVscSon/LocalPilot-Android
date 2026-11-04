package org.localpilot.android;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
/** Eight bounded v1 segments; legacy journal is copied once and never modified. */
public final class RotatingJournal {
 private final File directory;private File current;private long serial,request,sequence,knownLength,knownModified;private String previous="0";private final int segmentBytes;
 public RotatingJournal(File directory,File legacy)throws IOException{this(directory,legacy,SafeJournal.MAX);}
 public RotatingJournal(File directory,File legacy,int segmentBytes)throws IOException{
  if(segmentBytes<1024||segmentBytes>SafeJournal.MAX)throw new IllegalArgumentException("segment capacity");this.segmentBytes=segmentBytes;
  this.directory=directory;if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("journal directory");
  File preserved=new File(directory,"legacy-v1.log");if(legacy.exists()&&!preserved.exists()){new SafeJournal(legacy).snapshot();File tmp=new File(directory,"legacy.pending");Files.copy(legacy.toPath(),tmp.toPath(),StandardCopyOption.REPLACE_EXISTING);Files.move(tmp.toPath(),preserved.toPath(),StandardCopyOption.ATOMIC_MOVE);}
  List<File> files=segments();for(File f:files){byte[] bytes=new SafeJournal(f).snapshot();serial=Math.max(serial,id(f));for(String line:new String(bytes,java.nio.charset.StandardCharsets.UTF_8).split("\n"))if(!line.isEmpty())request=Math.max(request,Long.parseLong(line.split("\\|",-1)[3]));}
  current=files.isEmpty()?new File(directory,"segment-0.log"):files.get(files.size()-1);loadCurrent();
 }
 private void loadCurrent()throws IOException{byte[] bytes=new SafeJournal(current).snapshot();String raw=new String(bytes,java.nio.charset.StandardCharsets.UTF_8);sequence=0;previous="0";if(!raw.isEmpty()){String[] last=raw.trim().substring(raw.trim().lastIndexOf('\n')+1).split("\\|",-1);sequence=Long.parseLong(last[0]);previous=last[7];}knownLength=current.length();knownModified=current.lastModified();}
 private long id(File f){return Long.parseLong(f.getName().substring(8,f.getName().length()-4));}
 private List<File> segments()throws IOException{File[] list=directory.listFiles(f->f.getName().matches("segment-[0-9]{1,18}\\.log"));if(list==null)throw new IOException("journal listing");List<File> files=new ArrayList<>(Arrays.asList(list));files.sort(Comparator.comparingLong(this::id));return files;}
 public synchronized long nextRequest(){if(request==Long.MAX_VALUE)throw new IllegalStateException("request overflow");return ++request;}
 public synchronized void append(SafeJournal.Event event,long epoch,long request,long time,long metric)throws IOException{
  if(epoch<0||request<0||time<0||metric<0)throw new IOException("negative metadata");
  if(current.length()+512>segmentBytes){serial++;current=new File(directory,"segment-"+serial+".log");loadCurrent();}
  try(RandomAccessFile out=new RandomAccessFile(current,"rw");java.nio.channels.FileLock lock=out.getChannel().lock()){
   if(out.length()!=knownLength||current.lastModified()!=knownModified)loadCurrent();
   String body=(sequence+1)+"|"+event+"|"+epoch+"|"+request+"|"+time+"|"+metric+"|"+previous;String hash;
   try{byte[] digest=java.security.MessageDigest.getInstance("SHA-256").digest(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));StringBuilder h=new StringBuilder();for(byte b:digest)h.append(String.format(java.util.Locale.ROOT,"%02x",b&255));hash=h.toString();}catch(java.security.NoSuchAlgorithmException e){throw new IOException(e);}
   byte[] row=(body+"|"+hash+"\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);if(out.length()+row.length>segmentBytes)throw new IOException("journal segment full");out.seek(out.length());out.write(row);out.getFD().sync();sequence++;previous=hash;knownLength=out.length();knownModified=current.lastModified();
  }catch(java.nio.channels.OverlappingFileLockException e){throw new IOException("journal busy",e);}
  List<File> files=segments();while(files.size()>8)Files.delete(files.remove(0).toPath());
 }
 public synchronized byte[] snapshot()throws IOException{
  ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(ZipOutputStream zip=new ZipOutputStream(bytes)){
   List<File> files=segments();File legacy=new File(directory,"legacy-v1.log");if(legacy.exists())files.add(0,legacy);
   for(File f:files){byte[] data=new SafeJournal(f).snapshot();zip.putNextEntry(new ZipEntry(f.getName()));zip.write(data);zip.closeEntry();}
   zip.putNextEntry(new ZipEntry("README.txt"));zip.write("Independent checksum chains per segment. Eight newest runtime segments retained. Legacy file preserved separately.\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));zip.closeEntry();
  }return bytes.toByteArray();
 }
}
