package org.localpilot.android.web;
import java.nio.file.*;
import java.io.*;
import java.util.*;
public final class WebLessonTest {
 private static int checks;
 private static void check(boolean value){checks++;if(!value)throw new AssertionError("web lesson check "+checks);}
 private static float[] state(float value){float[] f=new float[WebLesson.FEATURES];Arrays.fill(f,value);return f;}
 private static WebLesson.Action action(float x){return new WebLesson.Action(WebLesson.Kind.TAP,x,.5f,x,.5f,100);}
 private static WebLesson.Sample sample(String session,long time,float value,float x){return new WebLesson.Sample(session,time,.66f,action(x),state(value),state(value+.001f));}
 private static WebLesson lesson(){return new WebLesson(WebLesson.gameId("https://example.com/game?id=1"),"https://example.com","Игра A");}
 private static void bad(byte[] data)throws Exception{try{WebLesson.decode(data);throw new AssertionError("invalid input accepted");}catch(IOException expected){checks++;}}
 private static void invalid(Runnable r){try{r.run();throw new AssertionError("invalid argument accepted");}catch(IllegalArgumentException|IllegalStateException expected){checks++;}}
 public static void main(String[] args)throws Exception{
  WebLesson d=lesson();check(d.size()==0);check(WebLesson.decode(d.encode()).size()==0);
  float[] features=state(.1f);d.add(new WebLesson.Sample("a",1,.66f,action(.2f),features,state(.12f)));Arrays.fill(features,.9f);
  check(d.predict(state(.1f),.66f)!=null);check(d.predict(state(.9f),.66f)==null);check(d.predict(state(.1f),1.4f)==null);
  byte[] one=d.encode();WebLesson decoded=WebLesson.decode(one);check(decoded.size()==1);check(Arrays.equals(one,decoded.encode()));
  byte[] changed=one.clone();changed[changed.length-20]='x';bad(changed);bad(new byte[0]);bad("audit log\n".getBytes());bad(new byte[]{(byte)0xff});bad(Arrays.copyOf(one,one.length-1));bad(new byte[WebLesson.MAX_BYTES+1]);
  invalid(()->new WebLesson.Sample("x",0,.66f,action(.2f),new float[255],state(.1f)));
  for(float f:new float[]{Float.NaN,Float.POSITIVE_INFINITY,1.1f,-1.1f})invalid(()->new WebLesson.Sample("x",0,.66f,action(.2f),state(f),state(.1f)));
  invalid(()->new WebLesson.Action(WebLesson.Kind.TAP,-.1f,0,0,0,100));invalid(()->new WebLesson.Action(WebLesson.Kind.HOLD,0,0,0,0,3000));
  for(String s:new String[]{"http://example.com","file:///a","javascript:alert(1)","https://key@example.com"})invalid(()->WebLesson.https(s));
  check(WebLesson.gameId("https://example.com/game#one").equals(WebLesson.gameId("https://example.com/game#two")));
  check(!WebLesson.gameId("https://example.com/game?id=1").equals(WebLesson.gameId("https://example.com/game?id=2")));
  check(WebLesson.origin("https://example.com/a?secret=private").equals("https://example.com"));
  check(!new String(one,java.nio.charset.StandardCharsets.UTF_8).contains("?id=1"));
  WebLesson merged=d.merged(decoded);check(merged.size()==1);check(d.size()==1);
  WebLesson other=new WebLesson(WebLesson.gameId("https://example.com/other"),"https://example.com");invalid(()->d.merged(other));
  invalid(()->d.trainReport());
  WebLesson trained=lesson();for(int i=0;i<6;i++)trained.add(sample("first",i,.12f*i,i%2==0?.2f:.8f));
  for(int i=0;i<4;i++)trained.add(sample("second",100+i,.12f*i+.001f,i%2==0?.2f:.8f));
  String report=trained.trainReport();check(report.contains("совпало 4"));check(report.contains("Отдельная последняя сессия"));
  check(trained.predict(state(.001f),.66f).x==.2f);check(trained.predict(state(.121f),.66f).x==.8f);
  trained.add(sample("third",200,.001f,.8f));check(trained.predict(state(.001f),.66f)==null);
  Path dir=Files.createTempDirectory("localpilot-web-store-");File f=dir.resolve("game.lpweb").toFile();d.save(f);check(WebLesson.load(f).size()==1);
  d.add(sample("a",2,.2f,.8f));d.save(f);check(WebLesson.load(f).size()==2);check(WebLesson.load(new File(f+".bak")).size()==1);
  Files.writeString(f.toPath(),"truncated");check(WebLesson.loadRecovering(f).size()==1);
  try(var files=Files.list(dir)){check(files.anyMatch(p->p.getFileName().toString().contains(".corrupt-")));}
  WebLesson full=lesson();for(int i=0;i<1000;i++)full.add(sample("a",i,.1f,.2f));invalid(()->full.add(sample("a",1001,.1f,.2f)));check(full.size()==1000);
  check(WebLesson.decode(full.encode()).size()==1000);
  try{WebLesson.bounded(new InputStream(){int remaining=WebLesson.MAX_BYTES+1;public int read(){return remaining-->0?0:-1;}});throw new AssertionError();}catch(IOException expected){checks++;}
  System.out.println("Web lesson: "+checks+" assertions PASS; no real game demonstrations were fabricated");
 }
}
