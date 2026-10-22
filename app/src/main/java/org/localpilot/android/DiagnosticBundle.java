package org.localpilot.android;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;

/** Explicit user export only. Fixed names, bounded data and per-entry checksums. */
public final class DiagnosticBundle {
 public static final int MAX_TEXT=131072,MAX_PNG=2*1024*1024,MAX_ZIP=3*1024*1024;
 private DiagnosticBundle(){}
 public static byte[] create(String session,String device,String crash,byte[] png)throws IOException{
  LinkedHashMap<String,byte[]> entries=new LinkedHashMap<>();
  entries.put("session.txt",text(session));entries.put("device.txt",text(device));entries.put("crash.txt",text(crash));
  if(png!=null){validatePng(png);entries.put("game-frame.png",png);}
  StringBuilder manifest=new StringBuilder("LocalPilot diagnostic-v1\nExplicit local export; no automatic upload.\nAndroid ACK is not proof of game motion.\nframe_included=").append(png!=null).append('\n');
  for(Map.Entry<String,byte[]> entry:entries.entrySet())manifest.append(entry.getKey()).append(" bytes=").append(entry.getValue().length).append(" sha256=").append(sha(entry.getValue())).append('\n');
  entries.put("manifest.txt",text(manifest.toString()));
  ByteArrayOutputStream bytes=new ByteArrayOutputStream();
  try(ZipOutputStream zip=new ZipOutputStream(bytes,StandardCharsets.UTF_8)){
   for(Map.Entry<String,byte[]> entry:entries.entrySet()){zip.putNextEntry(new ZipEntry(entry.getKey()));zip.write(entry.getValue());zip.closeEntry();}
  }
  byte[] result=bytes.toByteArray();if(result.length>MAX_ZIP)throw new IOException("Diagnostic ZIP limit");return result;
 }
 private static byte[] text(String value)throws IOException{
  if(value==null)throw new IOException("Missing report metadata");
  if(value.length()>MAX_TEXT)throw new IOException("Report text limit");
  byte[] result=value.getBytes(StandardCharsets.UTF_8);if(result.length>MAX_TEXT)throw new IOException("Report text bytes limit");return result;
 }
 private static void validatePng(byte[] p)throws IOException{
  byte[] signature={(byte)137,80,78,71,13,10,26,10};
  if(p.length<33||p.length>MAX_PNG)throw new IOException("Diagnostic PNG limit");
  for(int i=0;i<signature.length;i++)if(p[i]!=signature[i])throw new IOException("Diagnostic PNG signature");
  if(read(p,8)!=13||read(p,12)!=0x49484452)throw new IOException("Diagnostic PNG header");
  int w=read(p,16),h=read(p,20);
  if(w<1||h<1||w>720||h>720||(long)w*h>345600)throw new IOException("Diagnostic frame bounds");
 }
 private static int read(byte[] p,int at){return (p[at]&255)<<24|(p[at+1]&255)<<16|(p[at+2]&255)<<8|p[at+3]&255;}
 private static String sha(byte[] value)throws IOException{
  try{byte[] hash=MessageDigest.getInstance("SHA-256").digest(value);StringBuilder s=new StringBuilder();for(byte b:hash){s.append(Character.forDigit(b>>>4&15,16));s.append(Character.forDigit(b&15,16));}return s.toString();}
  catch(java.security.NoSuchAlgorithmException e){throw new IOException("SHA-256 unavailable",e);}
 }
}
