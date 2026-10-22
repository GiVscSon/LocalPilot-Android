package org.localpilot.android;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;
import javax.imageio.ImageIO;
public final class DiagnosticBundleTest {
 private static int checks;
 private interface Work{void run()throws Exception;}
 private static void check(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
 private static void reject(Work w,String why)throws Exception{try{w.run();throw new AssertionError(why);}catch(IOException expected){checks++;}}
 public static void main(String[] args)throws Exception{
  BufferedImage pixel=new BufferedImage(2,3,BufferedImage.TYPE_INT_ARGB);pixel.setRGB(0,0,0xffaa0011);
  ByteArrayOutputStream image=new ByteArrayOutputStream();ImageIO.write(pixel,"png",image);byte[] png=image.toByteArray();
  Map<String,byte[]> entries=unzip(DiagnosticBundle.create("Игра: READ=10; input=0","sdk=35","No saved Java crash",png));
  check(entries.keySet().equals(new LinkedHashSet<>(List.of("session.txt","device.txt","crash.txt","game-frame.png","manifest.txt"))),"fixed archive paths");
  check(new String(entries.get("session.txt"),StandardCharsets.UTF_8).contains("Игра"),"UTF-8 metadata");
  check(Arrays.equals(png,entries.get("game-frame.png")),"PNG bytes preserved");
  check(ImageIO.read(new ByteArrayInputStream(entries.get("game-frame.png"))).getHeight()==3,"real PNG remains readable");
  String manifest=new String(entries.get("manifest.txt"),StandardCharsets.UTF_8);
  check(manifest.contains("frame_included=true")&&manifest.contains("Explicit local export"),"explicit frame presence");
  for(String name:List.of("session.txt","device.txt","crash.txt","game-frame.png")){
   byte[] value=entries.get(name);String sha=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
   check(manifest.contains(name+" bytes="+value.length+" sha256="+sha),"entry checksum "+name);
  }
  Map<String,byte[]> absent=unzip(DiagnosticBundle.create("No game session","device","crash",null));
  check(!absent.containsKey("game-frame.png")&&new String(absent.get("manifest.txt"),StandardCharsets.UTF_8).contains("frame_included=false"),"missing frame remains explicit");
  reject(()->DiagnosticBundle.create(null,"d","c",null),"missing session");
  reject(()->DiagnosticBundle.create("x".repeat(DiagnosticBundle.MAX_TEXT+1),"d","c",null),"metadata character cap");
  reject(()->DiagnosticBundle.create("я".repeat(DiagnosticBundle.MAX_TEXT),"d","c",null),"UTF-8 byte cap");
  reject(()->DiagnosticBundle.create("s","d","c",new byte[0]),"empty PNG");
  reject(()->DiagnosticBundle.create("s","d","c",new byte[DiagnosticBundle.MAX_PNG+1]),"oversized PNG");
  byte[] bad=png.clone();bad[0]=0;reject(()->DiagnosticBundle.create("s","d","c",bad),"PNG signature");
  byte[] huge=png.clone();put(huge,16,721);reject(()->DiagnosticBundle.create("s","d","c",huge),"dimension cap");
  byte[] area=png.clone();put(area,16,720);put(area,20,720);reject(()->DiagnosticBundle.create("s","d","c",area),"pixel area cap");
  byte[] negative=png.clone();put(negative,16,-1);reject(()->DiagnosticBundle.create("s","d","c",negative),"negative PNG dimension");
  byte[] header=png.clone();header[12]=0;reject(()->DiagnosticBundle.create("s","d","c",header),"IHDR required");
  byte[] zip=DiagnosticBundle.create("x".repeat(DiagnosticBundle.MAX_TEXT),"device","crash",png);
  check(zip.length<=DiagnosticBundle.MAX_ZIP,"bounded output");
  System.out.println("Diagnostic bundle: "+checks+" assertions PASS");
 }
 private static void put(byte[] b,int at,int value){b[at]=(byte)(value>>>24);b[at+1]=(byte)(value>>>16);b[at+2]=(byte)(value>>>8);b[at+3]=(byte)value;}
 private static Map<String,byte[]> unzip(byte[] bytes)throws Exception{
  Map<String,byte[]> result=new LinkedHashMap<>();try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(bytes),StandardCharsets.UTF_8)){
   ZipEntry entry;while((entry=zip.getNextEntry())!=null){ByteArrayOutputStream data=new ByteArrayOutputStream();zip.transferTo(data);result.put(entry.getName(),data.toByteArray());zip.closeEntry();}
  }return result;
 }
}
