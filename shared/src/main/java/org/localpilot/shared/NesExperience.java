package org.localpilot.shared;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

/** NES framebuffer/button imitation. Scores and rewards remain UNKNOWN. */
public final class NesExperience {
 public static final String SCHEMA="nes-luma64-buttons8-v1",HEADER="LOCALPILOT-NES-EPISODE-v1";
 public static final int MAX_ROWS=1024,MAX_BYTES=512*1024;
 public record Frame(int frame,int pixelHash,int buttons,int[] luma){
  public Frame{
   if(frame<0||buttons<0||buttons>255||luma==null||luma.length!=64)throw new IllegalArgumentException("NES observation bounds");
   luma=luma.clone();for(int v:luma)if(v<0||v>255)throw new IllegalArgumentException("NES pixel range");
  }
  @Override public int[] luma(){return luma.clone();}
 }
 public record Episode(String romSha256,List<Frame> frames){
  public Episode{
   if(romSha256==null||!romSha256.matches("[a-f0-9]{64}")||frames==null||frames.isEmpty()||frames.size()>MAX_ROWS)
    throw new IllegalArgumentException("NES episode identity/size");
   frames=List.copyOf(frames);int last=-1;
   for(Frame f:frames){if(f.frame<=last)throw new IllegalArgumentException("NES replayed frame");last=f.frame;}
  }
 }
 public static String sha256(byte[] data){
  try{byte[] hash=MessageDigest.getInstance("SHA-256").digest(data);StringBuilder out=new StringBuilder();
   for(byte b:hash)out.append(String.format(Locale.ROOT,"%02x",b&255));return out.toString();
  }catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
 }
 public static String encode(Episode episode){
  StringBuilder out=new StringBuilder(HEADER+"|"+SCHEMA+"|"+episode.romSha256+"\n");
  for(Frame f:episode.frames){StringBuilder row=new StringBuilder(f.frame+"|"+f.pixelHash+"|"+f.buttons+"|");
   for(int i=0;i<64;i++){if(i>0)row.append(',');row.append(f.luma[i]);}
   String body=row.toString();out.append(body).append('|').append(sha256((episode.romSha256+"|"+SCHEMA+"|"+body).getBytes(StandardCharsets.UTF_8))).append('\n');
  }
  String text=out.toString();if(text.getBytes(StandardCharsets.UTF_8).length>MAX_BYTES)throw new IllegalArgumentException("NES episode exceeds bound");return text;
 }
 public static Episode decode(String text){
  if(text==null||text.getBytes(StandardCharsets.UTF_8).length>MAX_BYTES)throw new IllegalArgumentException("NES episode size");
  String[] lines=text.split("\n",-1);
  if(lines.length<3||lines.length>MAX_ROWS+2||!lines[lines.length-1].isEmpty())throw new IllegalArgumentException("NES episode lines");
  String[] header=lines[0].split("\\|",-1);
  if(header.length!=3||!HEADER.equals(header[0])||!SCHEMA.equals(header[1]))throw new IllegalArgumentException("NES episode schema");
  List<Frame> frames=new ArrayList<>();
  for(int i=1;i<lines.length-1;i++){
   String[] row=lines[i].split("\\|",-1);if(row.length!=5)throw new IllegalArgumentException("NES row");
   String body=lines[i].substring(0,lines[i].lastIndexOf('|'));
   if(!sha256((header[2]+"|"+SCHEMA+"|"+body).getBytes(StandardCharsets.UTF_8)).equals(row[4]))throw new IllegalArgumentException("NES checksum");
   int[] luma=Arrays.stream(row[3].split(",",-1)).mapToInt(Integer::parseInt).toArray();
   frames.add(new Frame(Integer.parseInt(row[0]),Integer.parseInt(row[1]),Integer.parseInt(row[2]),luma));
  }
  return new Episode(header[2],frames);
 }
 /** Verify the private snapshot before opening (and possibly truncating) an export destination. */
 public static Episode decodeSnapshot(String digest,byte[] bytes){
  if(digest==null||!digest.matches("[a-f0-9]{64}")||bytes==null||bytes.length>MAX_BYTES||!digest.equals(sha256(bytes)))
   throw new IllegalArgumentException("NES snapshot integrity");
  return decode(new String(bytes,StandardCharsets.UTF_8));
 }
 /** Nearest observed-state imitation, explicitly no reward or gesture permission. */
 public static final class Policy {
  private final Episode episode;
  public Policy(Episode episode){this.episode=Objects.requireNonNull(episode);}
  public OptionalInt suggest(String rom,Frame observation){
   if(!episode.romSha256.equals(rom))throw new IllegalArgumentException("NES ROM mismatch");
   long best=Long.MAX_VALUE;int buttons=0;boolean ambiguous=false;
   for(Frame f:episode.frames){long distance=0;for(int i=0;i<64;i++){long d=f.luma[i]-observation.luma[i];distance+=d*d;}
    if(distance<best){best=distance;buttons=f.buttons;ambiguous=false;}
    else if(distance==best&&buttons!=f.buttons)ambiguous=true;
   }
   return !ambiguous&&best<=64*12*12?OptionalInt.of(buttons):OptionalInt.empty();
  }
  public String report(){return "RETRO_EMULATED · "+SCHEMA+"\nROM SHA256="+episode.romSha256+
    "\nНаблюдений="+episode.frames.size()+"\nОбучение повторению наблюдённых кнопок; награда UNKNOWN. Реальные жесты и перенос весов не разрешены.";}
 }
 private NesExperience(){}
}
