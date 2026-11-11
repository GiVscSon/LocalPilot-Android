package org.localpilot.android;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.Locale;
import java.util.Set;

/**
 * Download only public Hugging Face GGUF files, with explicit user initiation.
 * Block local network SSRF, unencrypted HTTP and arbitrary redirect hosts.
 */
public final class ModelLabNetworkPolicy {
 private ModelLabNetworkPolicy(){}
 private static final Set<String> HOSTS=Set.of(
   "huggingface.co","hf.co",
   "cdn-lfs.huggingface.co","cdn-lfs-us-1.hf.co",
   "cas-bridge.xethub.hf.co","transfer.xethub.hf.co",
   "cas-server.xethub.hf.co");
 public static boolean allowed(String raw){
  if(raw==null||raw.length()>4096)return false;
  try{
   URI u=new URI(raw);
   String host=u.getHost();
   return "https".equalsIgnoreCase(u.getScheme())&&
      host!=null&&HOSTS.contains(host.toLowerCase(Locale.ROOT))&&
      (u.getPort()==-1||u.getPort()==443)&&
      u.getUserInfo()==null&&u.getFragment()==null;
  }catch(Exception rejected){return false;}
 }
 public static HttpURLConnection open(String raw)throws Exception{
  if(!allowed(raw))throw new SecurityException("Только HTTPS-ссылки Hugging Face");
  URI first=new URI(raw);
  if(!first.getPath().toLowerCase(Locale.ROOT).endsWith(".gguf"))
   throw new IllegalArgumentException("Ссылка должна указывать на конкретный GGUF-файл");
  String location=raw;
  for(int redirect=0;redirect<=5;redirect++){
   if(!allowed(location))throw new SecurityException("Небезопасная переадресация");
   URL url=new URI(location).toURL();
   HttpURLConnection c=(HttpURLConnection)url.openConnection(java.net.Proxy.NO_PROXY);
   c.setInstanceFollowRedirects(false);
   c.setConnectTimeout(10_000);
   c.setReadTimeout(45_000);
   c.setRequestProperty("User-Agent","LocalPilot-ModelLab/1.0");
   c.setRequestProperty("Accept","application/octet-stream");
   int status=c.getResponseCode();
   if(status==200)return c;
   if(status>=300&&status<=399){
    String next=c.getHeaderField("Location");
    c.disconnect();
    if(next==null)throw new java.io.IOException("Redirect without Location");
    location=url.toURI().resolve(next).toString();
    continue;
   }
   c.disconnect();
   throw new java.io.IOException("HF HTTP "+status+"; private/gated models are unsupported");
  }
  throw new SecurityException("Too many redirects");
 }
}
