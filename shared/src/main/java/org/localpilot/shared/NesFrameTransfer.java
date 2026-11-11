package org.localpilot.shared;

import java.util.zip.CRC32;

/**
 * Untrusted NES frame in the explicit GameLab -> ModelLab intent.
 * A small 128x120 PNG is useful for vision experiments without Binder-sized
 * screenshots, file URIs, network access, or any gameplay permissions.
 * This codec validates PNG structure, fixed dimensions, chunk CRC and size.
 * It does NOT authenticate the screenshot or associate it with a scored win.
 */
public final class NesFrameTransfer {
 private NesFrameTransfer(){}
 public static final String EXTRA_PNG="org.localpilot.shared.NES_FRAME_PNG";
 public static final int WIDTH=128, HEIGHT=120;
 public static final int MAX_BYTES=150_000;
 public static final int MAX_BASE64_CHARS=4*((MAX_BYTES+2)/3);
 private static final byte[] MAGIC={(byte)137,80,78,71,13,10,26,10};
 private static long u32(byte[] in,int at){
  return ((long)(in[at]&255)<<24)|((long)(in[at+1]&255)<<16)|
   ((long)(in[at+2]&255)<<8)|(in[at+3]&255);
 }
 private static boolean chunk(byte[] in,int at,String name){
  for(int i=0;i<4;i++)if(in[at+i]!=(byte)name.charAt(i))return false;
  return true;
 }
 public static void verify(byte[] png){
  if(png==null||png.length<68||png.length>MAX_BYTES)
   throw new IllegalArgumentException("NES PNG outside IPC size limit");
  for(int i=0;i<8;i++)if(png[i]!=MAGIC[i])
   throw new IllegalArgumentException("Not a PNG image");
  long offset=8;boolean header=false,pixels=false,ended=false;
  while(offset+12<=png.length){
   int at=(int)offset;
   long length=u32(png,at);
   if(length>MAX_BYTES||offset+12+length>png.length)
    throw new IllegalArgumentException("Truncated PNG chunk");
   int type=at+4,data=at+8,crcAt=(int)(offset+8+length);
   CRC32 crc=new CRC32();
   crc.update(png,type,(int)length+4);
   if(crc.getValue()!=u32(png,crcAt))
    throw new IllegalArgumentException("Corrupt NES PNG CRC");
   if(!header){
    if(!chunk(png,type,"IHDR")||length!=13||
       u32(png,data)!=WIDTH||u32(png,data+4)!=HEIGHT||
       png[data+8]!=8||
       !(png[data+9]==2||png[data+9]==6)||
       png[data+10]!=0||png[data+11]!=0||png[data+12]!=0)
     throw new IllegalArgumentException("NES screenshot must be 128x120 RGB/RGBA PNG");
    header=true;
   }else if(chunk(png,type,"IDAT")){
    if(length<1)throw new IllegalArgumentException("Empty NES PNG image");
    pixels=true;
   }else if(chunk(png,type,"IEND")){
    if(length!=0||!pixels||offset+12!=png.length)
     throw new IllegalArgumentException("NES PNG missing pixels or trailing data");
    ended=true;
    break;
   }else if(chunk(png,type,"IHDR")){
    throw new IllegalArgumentException("Duplicate PNG header");
   }
   offset+=length+12;
  }
  if(!header||!pixels||!ended)
   throw new IllegalArgumentException("Incomplete NES PNG");
 }
}
