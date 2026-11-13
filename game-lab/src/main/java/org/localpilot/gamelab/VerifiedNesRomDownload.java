package org.localpilot.gamelab;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.Set;

/** Pinned, MIT-licensed real NES homebrew ROM. Never downloads unknown/commercial ROMs. */
public final class VerifiedNesRomDownload {
 private VerifiedNesRomDownload(){}
 public static final String TITLE="VOID LANCER v1.0.0";
 public static final String FILENAME="VOID-LANCER-v1.0.0.nes";
 public static final String SOURCE="https://github.com/Riyoway/void-lancer-nes/releases/download/v1.0.0/"+FILENAME;
 public static final String LICENSE="https://github.com/Riyoway/void-lancer-nes/blob/v1.0.0/LICENSE";
 public static final int BYTES=40976;
 public static final String SHA256="5eb801aca68131a3d17fa82d8901d46f6098b878d1e00cd1c47d00c001c2405a";
 private static final Set<String> CDN=Set.of("release-assets.githubusercontent.com",
  "objects.githubusercontent.com","github.com");
 static boolean allowed(String url,boolean first){
  try{
   URI u=new URI(url);
   String host=u.getHost();
   if(!"https".equalsIgnoreCase(u.getScheme())||host==null||
      !CDN.contains(host.toLowerCase(Locale.ROOT))||
      u.getUserInfo()!=null||u.getFragment()!=null||
      (u.getPort()!=-1&&u.getPort()!=443))return false;
   return !first||SOURCE.equals(url);
  }catch(Exception e){return false;}
 }
 public static byte[] download()throws Exception{
  String location=SOURCE;
  for(int i=0;i<=5;i++){
   if(!allowed(location,i==0))throw new SecurityException("Недоверенная ссылка NES ROM");
   HttpURLConnection c=(HttpURLConnection)new URI(location).toURL().openConnection(java.net.Proxy.NO_PROXY);
   c.setInstanceFollowRedirects(false);
   c.setConnectTimeout(12000);c.setReadTimeout(45000);
   c.setRequestProperty("User-Agent","LocalPilot-GameLab/1.0");
   try{
    int code=c.getResponseCode();
    if(code>=300&&code<=399){
     String next=c.getHeaderField("Location");
     if(next==null)throw new java.io.IOException("Нет адреса переадресации ROM");
     location=c.getURL().toURI().resolve(next).toString();
     continue;
    }
    if(code!=200)throw new java.io.IOException("Ошибка загрузки ROM HTTP "+code);
    if(c.getContentLengthLong()>BYTES)throw new SecurityException("Размер ROM превышает проверенный");
    ByteArrayOutputStream out=new ByteArrayOutputStream(BYTES);
    try(InputStream in=c.getInputStream()){
     byte[] block=new byte[8192];int n;
     while((n=in.read(block))!=-1){
      if(out.size()+n>BYTES)throw new SecurityException("ROM слишком большой");
      out.write(block,0,n);
     }
    }
    byte[] rom=out.toByteArray();
    validate(rom);
    return rom;
   }finally{c.disconnect();}
  }
  throw new SecurityException("Слишком много переадресаций ROM");
 }
 public static void validate(byte[] rom)throws Exception{
  if(rom==null||rom.length!=BYTES||rom[0]!='N'||rom[1]!='E'||rom[2]!='S'||(rom[3]&255)!=0x1a)
   throw new SecurityException("Неверный формат или размер NES ROM");
  MessageDigest sha=MessageDigest.getInstance("SHA-256");
  byte[] got=sha.digest(rom);
  StringBuilder hex=new StringBuilder();
  for(byte b:got)hex.append(String.format(Locale.ROOT,"%02x",b&255));
  if(!SHA256.equals(hex.toString()))throw new SecurityException("SHA256 ROM не совпадает");
 }
}