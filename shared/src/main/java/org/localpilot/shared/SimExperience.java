package org.localpilot.shared;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * GameLab-only replay exchange. This file is NOT an authenticated real-game
 * witness or an Android input permit. A SHA-256 checksum detects corruption,
 * not provenance forgery. Never count it as Market acceptance.
 */
public final class SimExperience {
 private SimExperience(){}
 public static final String HEADER="LOCALPILOT-LAB-EXPERIENCE-v1";
 public static final int MAX_ROWS=1024;
 public static final int MAX_BYTES=512*1024;
 private static final String[] GAMES={
   "BUBBLE","ARI","MATCH","PADDLE","TRAFFIC","FLAPPY",
   "BOARD2048","HEXON","PROTECTED_SWAP","CAT_TOWER"
 };
 private static final String[] FAULTS={
   "NONE","INPUT_IGNORED","FROZEN_VIEW","PANEL_OVERLAP"
 };
 private static final String[] RESULTS={"CHANGED","BLOCKED","NO_CHANGE"};

 public static final class Step {
  public final int sequence,fromX,fromY,toX,toY,durationMs;
  public final int beforeHash,afterHash,scoreBefore,scoreAfter;
  public final String game,fault,result;
  public Step(int seq,String game,String fault,int fromX,int fromY,int toX,int toY,
              int durationMs,int beforeHash,int afterHash,int scoreBefore,int scoreAfter,
              String result) {
   this.sequence=seq;this.game=game;this.fault=fault;
   this.fromX=fromX;this.fromY=fromY;this.toX=toX;this.toY=toY;
   this.durationMs=durationMs;this.beforeHash=beforeHash;this.afterHash=afterHash;
   this.scoreBefore=scoreBefore;this.scoreAfter=scoreAfter;this.result=result;
   if(seq<1||seq>MAX_ROWS||!oneOf(game,GAMES)||!oneOf(fault,FAULTS)
     ||!oneOf(result,RESULTS)||!coord(fromX)||!coord(fromY)
     ||!coord(toX)||!coord(toY)||durationMs<0||durationMs>20000
     ||scoreBefore<0||scoreAfter<0||scoreBefore>10000000||scoreAfter>10000000
     ||("CHANGED".equals(result))!=(beforeHash!=afterHash))
    throw new IllegalArgumentException("invalid simulator transition");
  }
  private String body(){
   return sequence+"|"+game+"|"+fault+"|"+fromX+"|"+fromY+"|"+
     toX+"|"+toY+"|"+durationMs+"|"+beforeHash+"|"+afterHash+"|"+
     scoreBefore+"|"+scoreAfter+"|"+result;
  }
 }

 private static boolean coord(int n){return n>=0&&n<=1000;}
 private static boolean oneOf(String s,String[] allowed){
  if(s==null)return false;
  for(String a:allowed)if(a.equals(s))return true;
  return false;
 }
 public static int quantize(float value){
  if(!Float.isFinite(value))throw new IllegalArgumentException("nonfinite coordinate");
  return Math.max(0,Math.min(1000,Math.round(value*1000f)));
 }
 private static String sha256(String s){
  try{
   byte[] bytes=MessageDigest.getInstance("SHA-256")
      .digest(s.getBytes(StandardCharsets.UTF_8));
   StringBuilder out=new StringBuilder(64);
   for(byte b:bytes)out.append(String.format(Locale.ROOT,"%02x",b&255));
   return out.toString();
  }catch(NoSuchAlgorithmException exception){throw new IllegalStateException(exception);}
 }
 public static String encode(List<Step> steps){
  if(steps==null||steps.isEmpty()||steps.size()>MAX_ROWS)
   throw new IllegalArgumentException("bounded simulator replay required");
  StringBuilder out=new StringBuilder(HEADER).append('\n');
  int seq=0;
  for(Step step:steps){
   if(step==null||step.sequence!=++seq)
    throw new IllegalArgumentException("unordered simulator steps");
   String body=step.body();
   out.append(body).append('|').append(sha256(body)).append('\n');
  }
  String text=out.toString();
  if(text.getBytes(StandardCharsets.UTF_8).length>MAX_BYTES)
   throw new IllegalArgumentException("replay exceeds size limit");
  return text;
 }
 public static List<Step> decode(String text){
  if(text==null||text.getBytes(StandardCharsets.UTF_8).length>MAX_BYTES)
   throw new IllegalArgumentException("replay exceeds size limit");
  String[] lines=text.split("\\n",-1);
  if(lines.length<3||lines.length>MAX_ROWS+2||!HEADER.equals(lines[0])
    ||!lines[lines.length-1].isEmpty())
   throw new IllegalArgumentException("unsupported simulator replay header");
  List<Step> result=new ArrayList<>();
  for(int i=1;i<lines.length-1;i++){
   String[] c=lines[i].split("\\|",-1);
   if(c.length!=14)throw new IllegalArgumentException("invalid simulator row");
   String body=lines[i].substring(0,lines[i].lastIndexOf('|'));
   if(!MessageDigest.isEqual(sha256(body).getBytes(StandardCharsets.US_ASCII),
       c[13].getBytes(StandardCharsets.US_ASCII)))
    throw new IllegalArgumentException("simulator row checksum mismatch");
   try{
    Step s=new Step(Integer.parseInt(c[0]),c[1],c[2],
      Integer.parseInt(c[3]),Integer.parseInt(c[4]),
      Integer.parseInt(c[5]),Integer.parseInt(c[6]),
      Integer.parseInt(c[7]),Integer.parseInt(c[8]),Integer.parseInt(c[9]),
      Integer.parseInt(c[10]),Integer.parseInt(c[11]),c[12]);
    if(s.sequence!=i)throw new IllegalArgumentException("nonconsecutive simulator row");
    result.add(s);
   }catch(NumberFormatException malformed){
    throw new IllegalArgumentException("invalid numeric simulator value",malformed);
   }
  }
  return Collections.unmodifiableList(result);
 }
}
