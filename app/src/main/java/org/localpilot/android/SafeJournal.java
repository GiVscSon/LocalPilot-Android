package org.localpilot.android;
import java.io.*;import java.nio.charset.StandardCharsets;import java.security.*;
/** Private bounded metadata receipts; checksum chain detects corruption, not malicious re-signing. */
public final class SafeJournal {
 public static final int MAX=524288;private final File file;private String previous="0";private long sequence;
 public enum Event {PROPOSED,ANDROID_COMPLETED,ANDROID_CANCELLED,REJECTED,STOP,VERIFIED_2048,VERIFIED_GRID,VERIFIED_BUBBLE,VERIFIED_ARCADE}
 public SafeJournal(File file)throws IOException {this.file=file;File parent=file.getParentFile();if(parent!=null&&!parent.isDirectory()&&!parent.mkdirs())throw new IOException("journal directory");if(file.exists())read(true);}
 private static String sha(String text)throws IOException{try{byte[] b=MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));StringBuilder out=new StringBuilder();for(byte x:b)out.append(String.format(java.util.Locale.ROOT,"%02x",x&255));return out.toString();}catch(NoSuchAlgorithmException e){throw new IOException(e);}}
 private byte[] read(boolean load)throws IOException{
  if(!file.exists())return new byte[0];
  if(file.length()>MAX)throw new IOException("journal size");
  byte[] data;
  try(InputStream in=new FileInputStream(file)){
   ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[4096];int size;
   while((size=in.read(buffer))!=-1){if(out.size()+size>MAX)throw new IOException("journal size");out.write(buffer,0,size);}
   data=out.toByteArray();
  }
  Tail tail=validate(data);if(load){sequence=tail.sequence;previous=tail.previous;}return data;
 }
 /** Validate an already captured immutable snapshot without touching disk or creating a writer. */
 public static void validateSnapshot(byte[] data)throws IOException{validate(data);}
 private static final class Tail {final long sequence;final String previous;Tail(long sequence,String previous){this.sequence=sequence;this.previous=previous;}}
 private static Tail validate(byte[] data)throws IOException{
  if(data==null||data.length>MAX)throw new IOException("journal size");
  String raw=new String(data,StandardCharsets.UTF_8),prev="0";long seq=0;
  if(!raw.isEmpty()&&!raw.endsWith("\n"))throw new IOException("truncated journal");
  for(String line:raw.split("\n")){
   if(line.isEmpty())continue;String[] p=line.split("\\|",-1);
   if(p.length!=8)throw new IOException("journal fields");
   try{
    long number=Long.parseLong(p[0]);Event.valueOf(p[1]);
    for(int i=2;i<=5;i++)if(Long.parseLong(p[i])<0)throw new IllegalArgumentException();
    if(number!=++seq||!p[6].equals(prev))throw new IllegalArgumentException();
   }catch(IllegalArgumentException e){throw new IOException("journal context",e);}
   String body=line.substring(0,line.lastIndexOf('|'));
   if(!sha(body).equals(p[7]))throw new IOException("journal checksum");prev=p[7];
  }
  return new Tail(seq,prev);
 }
 public synchronized void append(Event event,long epoch,long request,long time,long metric)throws IOException{if(epoch<0||request<0||time<0||metric<0)throw new IOException("negative metadata");read(true);String body=(sequence+1)+"|"+event+"|"+epoch+"|"+request+"|"+time+"|"+metric+"|"+previous;String hash=sha(body);byte[] row=(body+"|"+hash+"\n").getBytes(StandardCharsets.UTF_8);if(file.length()+row.length>MAX)throw new IOException("journal full: preserve and export");try(RandomAccessFile stream=new RandomAccessFile(file,"rw");java.nio.channels.FileLock lock=stream.getChannel().lock()){read(true);if(!body.startsWith((sequence+1)+"|"))throw new IOException("concurrent journal writer");if(stream.length()+row.length>MAX)throw new IOException("journal full");stream.seek(stream.length());stream.write(row);stream.getFD().sync();}catch(java.nio.channels.OverlappingFileLockException e){throw new IOException("journal busy",e);}sequence++;previous=hash;}
 public synchronized long nextRequest(){return sequence+1;}
 public synchronized byte[] snapshot()throws IOException{return read(false);}
}
