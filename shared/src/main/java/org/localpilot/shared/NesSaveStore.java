package org.localpilot.shared;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.Arrays;

/** One bounded local save slot. It carries CPU/PPU state, never an input permit. */
public final class NesSaveStore {
 private NesSaveStore(){}
 public static final String CORE_SHA256="84946f0dae4bcf3b2dfebe84ad3bfeb1dbcb413bd9b1a69e1f45910a95fd7b41";
 private static final int MAGIC=0x4e535631,MAX_ROM=3*1024*1024,MAX_STATE=4*1024*1024;
 public static final int MAX_FILE=MAX_ROM+MAX_STATE+4096;
 public record Save(byte[] rom,String coreState,int frame,int frameHash,boolean dendy,
                    long verifiedActions,String policySha256) {
  public Save { rom=rom==null?null:rom.clone(); }
  @Override public byte[] rom(){return rom==null?null:rom.clone();}
 }
 private static byte[] digest(byte[] bytes){
  try{return MessageDigest.getInstance("SHA-256").digest(bytes);}
  catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
 }
 private static void valid(Save s)throws IOException{
  if(s==null||s.rom==null||s.rom.length<16||s.rom.length>MAX_ROM||
     s.rom[0]!=78||s.rom[1]!=69||s.rom[2]!=83||s.rom[3]!=26||
     s.coreState==null||!s.coreState.startsWith("{")||!s.coreState.endsWith("}")||
     s.coreState.length()>MAX_STATE||s.frame<15||s.frameHash==0||s.verifiedActions<0||
     s.policySha256==null||!s.policySha256.matches("(?:|[0-9a-f]{64})"))
   throw new IOException("Invalid NES save metadata");
 }
 private static byte[] encode(Save s)throws IOException{
  valid(s);byte[] state=s.coreState.getBytes(StandardCharsets.UTF_8);
  if(state.length>MAX_STATE)throw new IOException("NES state oversized");
  ByteArrayOutputStream bytes=new ByteArrayOutputStream();
  try(DataOutputStream out=new DataOutputStream(bytes)){
   out.writeInt(MAGIC);out.writeUTF(CORE_SHA256);out.writeInt(s.frame);
   out.writeInt(s.frameHash);out.writeBoolean(s.dendy);out.writeLong(s.verifiedActions);
   out.writeUTF(s.policySha256);out.write(digest(s.rom));
   out.writeInt(s.rom.length);out.write(s.rom);out.writeInt(state.length);out.write(state);
  }
  byte[] payload=bytes.toByteArray();bytes.write(digest(payload));return bytes.toByteArray();
 }
 private static byte[] bounded(DataInputStream in,int max)throws IOException{
  int n=in.readInt();if(n<1||n>max||n>in.available())throw new IOException("NES section length invalid");
  byte[] b=new byte[n];in.readFully(b);return b;
 }
 private static Save decode(byte[] bytes)throws IOException{
  if(bytes.length<160||bytes.length>MAX_FILE)throw new IOException("NES save size invalid");
  int n=bytes.length-32;byte[] payload=Arrays.copyOf(bytes,n);
  if(!MessageDigest.isEqual(digest(payload),Arrays.copyOfRange(bytes,n,bytes.length)))
   throw new IOException("NES save checksum mismatch");
  try(DataInputStream in=new DataInputStream(new ByteArrayInputStream(payload))){
   if(in.readInt()!=MAGIC||!CORE_SHA256.equals(in.readUTF()))throw new IOException("NES core/schema mismatch");
   int frame=in.readInt(),hash=in.readInt();boolean dendy=in.readBoolean();
   long actions=in.readLong();String policy=in.readUTF();byte[] romHash=new byte[32];in.readFully(romHash);
   byte[] rom=bounded(in,MAX_ROM),state=bounded(in,MAX_STATE);
   if(in.available()!=0||!MessageDigest.isEqual(romHash,digest(rom)))throw new IOException("NES ROM/trailing data invalid");
   Save s=new Save(rom,new String(state,StandardCharsets.UTF_8),frame,hash,dendy,actions,policy);valid(s);return s;
  }
 }
 private static Path file(Path vault)throws IOException{
  if(vault==null||Files.isSymbolicLink(vault))throw new IOException("Unsafe NES vault");
  Path dir=vault;
  for(String part:new String[]{"console","nes"}){
   dir=dir.resolve(part);if(Files.isSymbolicLink(dir))throw new IOException("Unsafe NES save directory");
   Files.createDirectories(dir);
  }
  Path f=dir.resolve("latest.nss");
  if(Files.isSymbolicLink(f))throw new IOException("Unsafe NES save file");return f;
 }
 public static synchronized Save load(Path vault)throws IOException{
  Path f=file(vault);
  if(!Files.exists(f,LinkOption.NOFOLLOW_LINKS))return null;
  if(!Files.isRegularFile(f,LinkOption.NOFOLLOW_LINKS)||Files.size(f)>MAX_FILE)
   throw new IOException("NES save file invalid/oversized");
  return decode(Files.readAllBytes(f));
 }
 public static synchronized void save(Path vault,Save state)throws IOException{
  byte[] bytes=encode(state);Path target=file(vault);
  if(Files.exists(target,LinkOption.NOFOLLOW_LINKS))load(vault); // Preserve corrupt evidence.
  Path tmp=Files.createTempFile(target.getParent(),"save-",".pending");
  try{
   try(FileOutputStream out=new FileOutputStream(tmp.toFile())){out.write(bytes);out.getFD().sync();}
   decode(Files.readAllBytes(tmp));
   Files.move(tmp,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
  }finally{Files.deleteIfExists(tmp);}
 }
}
