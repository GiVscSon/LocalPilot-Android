package org.localpilot.shared;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.Set;

/**
 * An untrusted, read-only JSNES observation accepted by ModelLab.
 * SHA-256 detects accidental corruption; it does NOT authenticate its origin.
 * A pixel fingerprint changing does not prove score, causality, or game success.
 */
public final class NesTrial {
 public static final String SCHEMA="MODELLAB-NES-TRIAL-v2";
 private static final String LEGACY_SCHEMA="MODELLAB-NES-TRIAL-v1";
 public static final String ACTION="org.localpilot.modellab.NES_TRIAL";
 public static final String EXTRA="org.localpilot.shared.NES_TRIAL";
 private static final Set<String> BUTTONS=Set.of("UP","DOWN","LEFT","RIGHT","A","B","START","SELECT");
 public final String scenario,button,status;
 private final String wireSchema;
 public final int frameBefore,frameAfter,hashBefore,hashAfter;

 private NesTrial(String scenario,String button,int start,int finish,int before,int after,String wireSchema){
  this.wireSchema=wireSchema;
  this.scenario=scenario;this.button=button;
  this.frameBefore=start;this.frameAfter=finish;
  this.hashBefore=before;this.hashAfter=after;
  this.status=before==after?"NO_VISUAL_CHANGE":"PIXELS_DIFFER";
 }
 public static NesTrial create(String scenario,String button,int start,int finish,
                               int before,int after){
  if(!("HOMEBREW".equals(scenario)||"USER_ROM".equals(scenario))||
     !BUTTONS.contains(button)||start<15||
     !((long)finish-start>=15&&(long)finish-start<=600||
       "HOMEBREW".equals(scenario)&&((long)finish-start==4||(long)finish-start==8))||before==0||after==0)
   throw new IllegalArgumentException("Incomplete or impossible NES frame observation");
  return new NesTrial(scenario,button,start,finish,before,after,SCHEMA);
 }
 private String body(){
  return wireSchema+"|JSNES_2_1_0|"+scenario+"|"+button+"|"+frameBefore+"|"+
    frameAfter+"|"+Integer.toUnsignedString(hashBefore)+"|"+
    Integer.toUnsignedString(hashAfter)+"|"+status;
 }
 private static String digest(String source){
  try{
   byte[] raw=MessageDigest.getInstance("SHA-256").digest(
     source.getBytes(StandardCharsets.UTF_8));
   StringBuilder hex=new StringBuilder(64);
   for(byte b:raw)hex.append(String.format(Locale.ROOT,"%02x",b&255));
   return hex.toString();
  }catch(Exception e){throw new IllegalStateException(e);}
 }
 public String serialize(){String body=body();return body+"|"+digest(body);}
 public static NesTrial parse(String text){
  if(text==null||text.length()<100||text.length()>450||
     text.indexOf('\n')!=-1||text.indexOf('\r')!=-1)
   throw new IllegalArgumentException("Invalid NES evidence length");
  String[] parts=text.split("\\|",-1);
  if(parts.length!=10||!(SCHEMA.equals(parts[0])||LEGACY_SCHEMA.equals(parts[0]))||!"JSNES_2_1_0".equals(parts[1])||
     !parts[9].matches("[0-9a-f]{64}"))
   throw new IllegalArgumentException("Unknown NES trial schema");
  int cutoff=text.lastIndexOf('|');
  String body=text.substring(0,cutoff);
  if(!MessageDigest.isEqual(digest(body).getBytes(StandardCharsets.US_ASCII),
     parts[9].getBytes(StandardCharsets.US_ASCII)))
   throw new IllegalArgumentException("Modified NES trial");
  try{
   long hb=Long.parseUnsignedLong(parts[6]),ha=Long.parseUnsignedLong(parts[7]);
   if(hb>0xffffffffL||ha>0xffffffffL)
    throw new IllegalArgumentException("NES fingerprint out of range");
   if(LEGACY_SCHEMA.equals(parts[0])&&
       (long)Integer.parseInt(parts[5])-Integer.parseInt(parts[4])<15)
    throw new IllegalArgumentException("Legacy evidence cannot declare short steps");
   NesTrial result=create(parts[2],parts[3],Integer.parseInt(parts[4]),
     Integer.parseInt(parts[5]),(int)hb,(int)ha);
   if(!result.status.equals(parts[8]))
    throw new IllegalArgumentException("Forged NES transition state");
   if(LEGACY_SCHEMA.equals(parts[0]))return new NesTrial(result.scenario,result.button,
      result.frameBefore,result.frameAfter,result.hashBefore,result.hashAfter,LEGACY_SCHEMA);
   return result;
  }catch(NumberFormatException e){
   throw new IllegalArgumentException("Invalid NES frame counter or hash",e);
  }
 }
 public String advisoryPrompt(){
  return "Analyze this UNTRUSTED JSNES observation, not a proven win. "+
    "ROM type: "+scenario+"; input: "+button+"; observed frames: "+
    frameBefore+" to "+frameAfter+" ("+(frameAfter-frameBefore)+" frame interval); "+
    "pixel fingerprint status: "+status+". "+
    "No ROM contents, score, player state, or screenshot pixels are available. "+
    "Explain what the observation supports, what it does not prove, "+
    "and which deterministic emulator checks should be used next. "+
    "Never issue button sequences or commands.";
 }
 public String summary(){
  return "JSNES 2.1.0 · "+scenario+" · "+button+" · кадры "+frameBefore+
    " → "+frameAfter+" · "+status+
    ". Контрольная сумма не подтверждает происхождение или победу.";
 }
}
