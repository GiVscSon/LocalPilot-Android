package org.localpilot.shared;

import java.util.UUID;

/** Process-local event bus for a real console-agent episode.
 * Events describe observed evidence, NOT model training or game success.
 * The standalone GameLab compiles this class but installs no sink.
 */
public final class HarnessTelemetry {
 private HarnessTelemetry(){}
 public interface Sink {
  void accept(Event event);
  default boolean healthy(){return true;}
 }
 public static final class Event {
  public final String episodeId,modelId,game,stage,status,detail;
  public final long timeMs,latencyMs;
  private Event(String ep,String model,String g,String step,String state,
                String description,long delay) {
   episodeId=ep;modelId=model;game=g;stage=step;status=state;
   detail=description;latencyMs=delay;timeMs=System.currentTimeMillis();
  }
 }
 private static volatile Sink sink;
 private static volatile String episode="";
 private static volatile String modelId="";
 private static volatile String game="";
 private static volatile boolean active;
 private static volatile boolean stopRequested;
 public static synchronized void install(Sink consumer){sink=consumer;}
 public static boolean healthy(){Sink current=sink;return current==null||current.healthy();}
 public static String episode(){return active?episode:"";}
 public static String modelId(){return active?modelId:"";}
 public static boolean active(){return active;}
 public static boolean stopRequested(){return stopRequested;}
 public static void requestStop(){
  stopRequested=true;
  emit("STOP","REQUESTED","user requested immediate cancel",0);
 }
 public static synchronized String begin(String gameName,String model) {
  if(active)throw new IllegalStateException("Harness already owns an episode");
  if(!healthy())throw new IllegalStateException("Harness audit is unavailable");
  if(gameName==null||gameName.isBlank()||model==null||model.isBlank())
   throw new IllegalArgumentException("Real environment and selected model required");
  episode=UUID.randomUUID().toString();
  game=gameName;modelId=model;stopRequested=false;active=true;
  emit("SESSION","STARTED","real emulator; goal reward UNKNOWN",-1);
  return episode;
 }
 public static synchronized void end(String reason) {
  if(!active)return;
  emit("STOP","STOPPED",reason,-1);
  active=false;stopRequested=false;episode="";modelId="";game="";
 }
 public static void emit(String stage,String status,String detail,long latencyMs){
  Sink listener=sink;
  if(listener==null)return;
  String p=episode(),m=modelId(),g=game;
  if(p.isEmpty())return; // no attribution outside an active real episode
  try{listener.accept(new Event(p,m,g,clean(stage,40),clean(status,40),
                         clean(detail,180),latencyMs));}
  catch(RuntimeException ignored){
   // Telemetry must never change STOP, neural decision or real input policy.
  }
 }
 private static String clean(String raw,int max) {
  if(raw==null)return "";
  String s=raw.replaceAll("[\\p{Cntrl}]+"," ").trim();
  return s.length()>max?s.substring(0,max):s;
 }
}
