package org.localpilot.android;

/** Session-scoped startup state. No screen content or projection token is retained. */
final class CaptureStartup {
 enum Stage { STOPPED, CONSENT, SERVICE, FIRST_FRAME, RUNNING }
 private static final java.util.concurrent.atomic.AtomicLong SERIAL=new java.util.concurrent.atomic.AtomicLong(System.currentTimeMillis());
 private long session, since;
 private Stage stage=Stage.STOPPED;
 private boolean recognized;
 private final java.util.ArrayDeque<String> events=new java.util.ArrayDeque<>();
 synchronized long begin(long now){session=SERIAL.incrementAndGet();recognized=false;set(Stage.CONSENT,now,"request");return session;}
 synchronized boolean active(long id){return id>0&&id==session&&stage!=Stage.STOPPED;}
 synchronized boolean move(long id,Stage expected,Stage next,long now){
  if(!active(id)||stage!=expected)return false;set(next,now,"ready");return true;
 }
 synchronized boolean consentMayClose(long id){return !active(id)||stage==Stage.FIRST_FRAME||stage==Stage.RUNNING;}
 synchronized boolean waiting(long id){return active(id)&&stage!=Stage.RUNNING;}
 synchronized String timeout(long id,long now){
  if(!active(id))return null;
  long limit=stage==Stage.CONSENT?120000:stage==Stage.SERVICE?15000:stage==Stage.FIRST_FRAME?15000:Long.MAX_VALUE;
  return now-since>=limit?stage.name()+"_TIMEOUT":null;
 }
 synchronized boolean recognized(long id,long now){
  if(!active(id)||stage!=Stage.RUNNING||recognized)return false;
  recognized=true;record(now+" session="+session+" RECOGNIZED");return true;
 }
 static String errorCode(String type){
  String clean=type==null?"UNKNOWN":type.toUpperCase(java.util.Locale.ROOT).replaceAll("[^A-Z0-9_]","_");
  return "STARTUP_"+clean.substring(0,Math.min(clean.length(),24));
 }
 synchronized void error(long id,String type){if(active(id))record("ERROR "+stage+" "+type);}
 synchronized void stop(long now){if(stage!=Stage.STOPPED)set(Stage.STOPPED,now,"stop");}
 synchronized String label(){return switch(stage){
  case CONSENT -> "Ожидание разрешения захвата…"; case SERVICE -> "2/5 служба";
  case FIRST_FRAME -> "3/5 кадр"; case RUNNING -> recognized?"5/5 игра":"4/5 кадры";
  case STOPPED -> "STOP";
 };}
 synchronized String report(){return String.join("\n",events);}
 private void set(Stage next,long now,String detail){stage=next;since=now;record(now+" session="+session+" "+next+" "+detail);}
 private void record(String s){if(events.size()==40)events.removeFirst();events.addLast(s);}
}
