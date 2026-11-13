package org.localpilot.android;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.Random;
import javax.imageio.ImageIO;

/** Real PNG export regression plus synthetic allocation-budget properties. */
public final class DiagnosticFrameSizeTest {
 private static int checks;
 private static void ok(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
 private static byte[] png(BufferedImage image)throws IOException{
  ByteArrayOutputStream out=new ByteArrayOutputStream();
  if(!ImageIO.write(image,"png",out))throw new IOException("PNG encoder unavailable");
  return out.toByteArray();
 }
 private static void budget(int w,int h){
  DiagnosticFrameSize fit=new DiagnosticFrameSize(w,h);
  ok(fit.width>=1&&fit.height>=1,"nonempty preview");
  ok(fit.width<=720&&fit.height<=720,"PNG edge acceptance bound");
  ok((long)fit.width*fit.height<=345600,"PNG pixel acceptance bound");
  ok(fit.argbBytes()<=1_382_400,"one preview has a fixed ARGB budget");
  ok(fit.width<=w&&fit.height<=h,"never enlarge a source");
  // Integer rounding can lose at most one output pixel on each axis.
  ok(Math.abs((long)fit.width*h-(long)fit.height*w)<=(long)w+h,
    "preserve source aspect within integer-pixel rounding");
  DiagnosticFrameSize twice=new DiagnosticFrameSize(fit.width,fit.height);
  ok(twice.width==fit.width&&twice.height==fit.height,"already bounded preview is stable");
 }
 public static void main(String[] args)throws Exception{
  for(int[] source:new int[][]{{1,1},{1,Integer.MAX_VALUE},{Integer.MAX_VALUE,1},
    {Integer.MAX_VALUE,Integer.MAX_VALUE},{720,480},{480,720},{720,720},
    {1080,2400},{1440,3200},{1600,2560},{3840,2160},{7680,4320}})
   budget(source[0],source[1]);
  Random random=new Random(20261113);
  for(int i=0;i<2000;i++)budget(1+random.nextInt(16384),1+random.nextInt(16384));
  for(int[] invalid:new int[][]{{0,1},{1,0},{-1,720},{720,-1}}){
   try{new DiagnosticFrameSize(invalid[0],invalid[1]);throw new AssertionError("invalid source accepted");}
   catch(IllegalArgumentException expected){checks++;}
  }
  DiagnosticFrameSize small=new DiagnosticFrameSize(321,123);
  ok(small.width==321&&small.height==123,"small diagnostic keeps exact dimensions");
  DiagnosticFrameSize phone=new DiagnosticFrameSize(1080,2400);
  ok(phone.width==324&&phone.height==720,"portrait screenshot budget");
  BufferedImage source=new BufferedImage(1080,2400,BufferedImage.TYPE_INT_ARGB);
  source.setRGB(540,1200,0xff33aa77);
  boolean oldRejected=false;
  try{DiagnosticBundle.create("session","device","crash",png(source));}
  catch(IOException expected){oldRejected=true;}
  ok(oldRejected,"old full-resolution diagnostic is rejected by actual exporter");
  BufferedImage preview=new BufferedImage(phone.width,phone.height,BufferedImage.TYPE_INT_ARGB);
  Graphics2D canvas=preview.createGraphics();
  try{canvas.drawImage(source,0,0,phone.width,phone.height,null);}finally{canvas.dispose();}
  byte[] encoded=png(preview);
  ok(ImageIO.read(new ByteArrayInputStream(encoded)).getWidth()==324,"actual resized PNG is readable");
  ok(DiagnosticBundle.create("source_roi=1080x2400 preview=324x720","device","crash",encoded).length>0,
    "bounded real PNG passes unchanged DiagnosticBundle validation");
  long oldBytes=4L*1080*2400;
  ok(oldBytes>10*phone.argbBytes(),"portrait retained allocation improves by more than ten times");
  System.out.println("DIAGNOSTIC_FRAME_SIZE_PASS checks="+checks+
    " old_phone_argb_bytes="+oldBytes+" bounded_phone_argb_bytes="+phone.argbBytes()+
    " max_preview_argb_bytes="+DiagnosticFrameSize.MAX_ARGB_BYTES+" synthetic_geometry=true real_png_export=true");
 }
}
