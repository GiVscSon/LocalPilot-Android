package org.localpilot.gamelab;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

/**
 * Actual GameLab core capability and content preflight.
 *
 * NES / Dendy: packaged JSNES 2.1.0 JavaScript CPU+PPU (Apache-2.0),
 * with Dendy speed-compatibility at 50 fps (NOT exact Dendy video timing).
 * DOS: DOSBox Pure is NOT bundled or executable yet; preflight only.
 * Nothing here counts as real Market-game response or learning evidence.
 */
public final class RetroEmulatorCatalog {
 private RetroEmulatorCatalog(){}
 public enum System {
  NES("NES / Famicom","jsnes",60,"NTSC"),
  DENDY("Денди (NES-совместимый 50 Гц)","jsnes",50,"Dendy"),
  DOS("MS-DOS","dosbox_pure",0,"");
  public final String label,core,fceummRegion;
  public final int expectedFps;
  System(String label,String core,int fps,String region){
   this.label=label;this.core=core;expectedFps=fps;fceummRegion=region;
  }
  public String nativeLibraryName(){return this==DOS?"libdosbox_pure_libretro_android.so":"";}
  public boolean runnableInGameLab(){return this!=DOS;}
  public String coreOptionKey(){return this==DOS?"":"jsnes_compatibility_mode";}
 }
 public record Inspection(System system,String extension,boolean recognized,String note) {}
 private static String extension(String name){
  if(name==null||name.length()>255||name.indexOf('/')>=0||name.indexOf('\\')>=0
     ||name.contains(".."))throw new IllegalArgumentException("Unsafe filename");
  int dot=name.lastIndexOf('.');
  return dot<1?"":name.substring(dot).toLowerCase(Locale.ROOT);
 }
 public static boolean accepts(System system,String filename){
  if(system==null)return false;
  String ext=extension(filename);
  if(system==System.NES||system==System.DENDY)
   return ".nes".equals(ext);
  return ".zip".equals(ext)||".dosz".equals(ext)||".com".equals(ext)
    ||".exe".equals(ext)||".bat".equals(ext)||".img".equals(ext)
    ||".ima".equals(ext)||".iso".equals(ext)||".conf".equals(ext);
 }
 /** Header validation reduces accidental mismatch. It does not make files trusted. */
 public static Inspection inspect(System system,String filename,byte[] first){
  if(!accepts(system,filename)||first==null)
   return new Inspection(system,"",false,"Unsupported file type");
  String ext=extension(filename);
  boolean recognized;
  String note;
  switch(ext){
   case ".nes" -> {
    recognized=first.length>=16&&first[0]==0x4e&&first[1]==0x45
      &&first[2]==0x53&&first[3]==0x1a;
    note="iNES/NES2 header";
   }
   case ".zip",".dosz" -> {
    recognized=first.length>=4&&first[0]==0x50&&first[1]==0x4b&&
      (first[2]==3||first[2]==5||first[2]==7)&&
      (first[3]==4||first[3]==6||first[3]==8);
    note="ZIP container. Never extract unsanitized paths.";
   }
   case ".exe" -> {
    recognized=first.length>=2&&first[0]=='M'&&first[1]=='Z';
    note="DOS MZ executable (not executed by Android)";
   }
   case ".com",".bat",".conf",".img",".ima",".iso" -> {
    recognized=first.length>=4;
    note="Container/opaque DOS content; actual core must validate";
   }
   default -> {recognized=false;note="Unsupported media";}
  }
  return new Inspection(system,ext,recognized,note);
 }
 public static Inspection inspect(System system,String filename,InputStream in)throws IOException{
  if(in==null)throw new IllegalArgumentException("input");
  byte[] header=new byte[16];int offset=0;
  while(offset<header.length){
   int n=in.read(header,offset,header.length-offset);
   if(n<0)break;
   if(n==0){int b=in.read();if(b<0)break;header[offset++]=(byte)b;}
   else offset+=n;
  }
  return inspect(system,filename,java.util.Arrays.copyOf(header,offset));
 }
}
