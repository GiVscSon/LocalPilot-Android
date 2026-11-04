package org.localpilot.android.web;
import javax.net.ssl.HttpsURLConnection;
import java.net.*;
import java.io.*;

/** HTTPS only, bounded responses. Model credentials are never redirected to another host. */
public final class WebHttp {
 public static byte[] examples(String address)throws IOException{return request(address,null,null,WebLesson.MAX_BYTES,3);}
 public static byte[] teacher(String address,String token,byte[] body)throws IOException{
  if(token==null||token.isEmpty()||token.contains("\r")||token.contains("\n"))throw new IOException("API key required");
  return request(address,body,token,128*1024,0);
 }
 private static byte[] request(String address,byte[] body,String token,int max,int redirects)throws IOException{
  URI uri=WebLesson.https(address);
  for(int attempt=0;;attempt++){
   HttpsURLConnection c=(HttpsURLConnection)uri.toURL().openConnection();
   try{
    c.setConnectTimeout(10000);c.setReadTimeout(20000);c.setInstanceFollowRedirects(false);
    c.setRequestProperty("Accept",body==null?"text/plain":"application/json");
    if(body!=null){c.setRequestMethod("POST");c.setRequestProperty("Content-Type","application/json");c.setRequestProperty("Authorization","Bearer "+token);c.setDoOutput(true);c.setFixedLengthStreamingMode(body.length);try(OutputStream out=c.getOutputStream()){out.write(body);}}
    int code=c.getResponseCode();
    if(code>=300&&code<=399){
     String location=c.getHeaderField("Location");if(attempt>=redirects||location==null)throw new IOException("Redirect refused; use the final HTTPS address");
     uri=WebLesson.https(uri.resolve(location).toString());continue;
    }
    if(code<200||code>=300)throw new IOException("HTTP "+code+"; check the endpoint, key, model and provider limits");
    if(c.getContentLengthLong()>max)throw new IOException("Response too large");
    try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
     byte[] block=new byte[8192];int n;while((n=in.read(block))!=-1){if(out.size()+n>max)throw new IOException("Response too large");out.write(block,0,n);}return out.toByteArray();
    }
   }finally{c.disconnect();}
  }
 }
}
