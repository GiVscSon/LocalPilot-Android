package org.localpilot.android.web;
import java.net.*;
import javax.net.ssl.*;
import java.io.*;
import java.security.cert.Certificate;
import java.util.*;
public final class WebHttpTest {
 private static final Map<String,Fake> routes=new HashMap<>();private static final List<String> opened=new ArrayList<>();private static int checks;
 private static void check(boolean value){checks++;if(!value)throw new AssertionError("web HTTP check "+checks);}
 private static Fake route(String url,int status,String data,String location)throws Exception{Fake f=new Fake(new URL(url),status,data.getBytes(java.nio.charset.StandardCharsets.UTF_8),location);routes.put(url,f);return f;}
 private static void fails(Task task,String contains)throws Exception{try{task.run();throw new AssertionError("request unexpectedly succeeded");}catch(IOException|IllegalArgumentException expected){checks++;if(contains!=null)check(expected.getMessage().contains(contains));}}
 private interface Task{void run()throws Exception;}
 private static final class Fake extends HttpsURLConnection {
  final int status;final byte[] body;final String location;final ByteArrayOutputStream sent=new ByteArrayOutputStream();boolean closed,inputRead;long declared=-1;
  Fake(URL url,int status,byte[] body,String location){super(url);this.status=status;this.body=body;this.location=location;}
  @Override public int getResponseCode(){return status;}
  @Override public String getHeaderField(String name){return name.equals("Location")?location:null;}
  @Override public long getContentLengthLong(){return declared;}
  @Override public OutputStream getOutputStream(){return sent;}
  @Override public InputStream getInputStream(){inputRead=true;return new ByteArrayInputStream(body);}
  @Override public void disconnect(){closed=true;}
  @Override public boolean usingProxy(){return false;}
  @Override public void connect(){}
  @Override public String getCipherSuite(){return "TEST_ONLY";}
  @Override public Certificate[] getLocalCertificates(){return new Certificate[0];}
  @Override public Certificate[] getServerCertificates(){return new Certificate[0];}
 }
 public static void main(String[] args)throws Exception{
  URL.setURLStreamHandlerFactory(protocol->protocol.equals("https")?new URLStreamHandler(){protected URLConnection openConnection(URL url)throws IOException{opened.add(url.toString());Fake f=routes.get(url.toString());if(f==null)throw new IOException("No mock route");return f;}}:null);
  Fake f=route("https://unit.test/examples",200,"example",null);check(Arrays.equals(WebHttp.examples(f.getURL().toString()),"example".getBytes()));check(f.closed);check(f.getRequestProperty("Authorization")==null);
  f=route("https://unit.test/redirect",302,"","https://other.test/file");Fake target=route("https://other.test/file",200,"ok",null);check(WebHttp.examples("https://unit.test/redirect").length==2);check(f.closed&&target.closed);check(target.getRequestProperty("Authorization")==null);
  route("https://unit.test/insecure",302,"","http://other.test/file");fails(()->WebHttp.examples("https://unit.test/insecure"),null);
  Fake post=route("https://unit.test/api",200,"{}",null);byte[] body="request".getBytes();check(WebHttp.teacher("https://unit.test/api","unit-only-key",body).length==2);check(Arrays.equals(post.sent.toByteArray(),body));check("Bearer unit-only-key".equals(post.getRequestProperty("Authorization")));check(post.closed);
  route("https://unit.test/api-redirect",302,"","https://secret-target.test/api");int before=opened.size();fails(()->WebHttp.teacher("https://unit.test/api-redirect","unit-only-key",body),"Redirect refused");check(opened.size()==before+1);check(!opened.contains("https://secret-target.test/api"));
  route("https://unit.test/failure",401,"PRIVATE_PROVIDER_BODY",null);try{WebHttp.teacher("https://unit.test/failure","unit-only-key",body);throw new AssertionError();}catch(IOException e){check(e.getMessage().contains("HTTP 401"));check(!e.getMessage().contains("PRIVATE_PROVIDER_BODY")&&!e.getMessage().contains("unit-only-key"));}
  Fake large=route("https://unit.test/large",200,"",null);large.declared=WebLesson.MAX_BYTES+1;fails(()->WebHttp.examples("https://unit.test/large"),"too large");check(!large.inputRead&&large.closed);
  Fake big=new Fake(new URL("https://unit.test/big-api"),200,new byte[128*1024+1],null);routes.put(big.getURL().toString(),big);fails(()->WebHttp.teacher("https://unit.test/big-api","unit-only-key",body),"too large");check(big.closed);
  before=opened.size();fails(()->WebHttp.teacher("https://unit.test/api","bad\nkey",body),"API key");check(opened.size()==before);
  route("https://unit.test/loop",302,"","/loop");before=opened.size();fails(()->WebHttp.examples("https://unit.test/loop"),"Redirect refused");check(opened.size()==before+4);
  System.out.println("Web HTTP: "+checks+" assertions PASS (mock HTTPS, no provider/API call made)");
 }
}
