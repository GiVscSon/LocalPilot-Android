package org.localpilot.shared;
import java.util.*;
import java.nio.charset.StandardCharsets;
public final class NesExperienceTest {
 private static int checks;
 private static void check(boolean yes){checks++;if(!yes)throw new AssertionError("NES experience contract");}
 private static void rejects(Runnable r){try{r.run();throw new AssertionError("expected rejection");}catch(IllegalArgumentException expected){checks++;}}
 public static void main(String[] args){
  String rom="a".repeat(64);int[] pixels=new int[64];Arrays.fill(pixels,100);
  NesExperience.Frame first=new NesExperience.Frame(15,42,128,pixels);pixels[0]=0;check(first.luma()[0]==100);
  NesExperience.Episode episode=new NesExperience.Episode(rom,List.of(first,new NesExperience.Frame(30,43,128,first.luma())));
  String text=NesExperience.encode(episode);var restored=NesExperience.decode(text);check(restored.frames().size()==2);
  byte[] snapshot=text.getBytes(StandardCharsets.UTF_8);String digest=NesExperience.sha256(snapshot);
  check(NesExperience.decodeSnapshot(digest,snapshot).frames().size()==2);
  rejects(()->NesExperience.decodeSnapshot("../"+digest,snapshot));
  rejects(()->NesExperience.decodeSnapshot("0".repeat(64),snapshot));
  rejects(()->NesExperience.decodeSnapshot(digest,null));
  byte[] corrupt=snapshot.clone();corrupt[0]^=1;
  rejects(()->NesExperience.decodeSnapshot(digest,corrupt));
  rejects(()->NesExperience.decodeSnapshot(NesExperience.sha256(corrupt),corrupt));
  byte[] oversized=new byte[NesExperience.MAX_BYTES+1];
  rejects(()->NesExperience.decodeSnapshot(NesExperience.sha256(oversized),oversized));
  check(new NesExperience.Policy(restored).suggest(rom,first).orElse(-1)==128);
  rejects(()->new NesExperience.Policy(restored).suggest("b".repeat(64),first));
  rejects(()->NesExperience.decode(text.replace("15|42|","15|41|")));
  rejects(()->NesExperience.decode(text.replace(rom,"b".repeat(64))));
  rejects(()->new NesExperience.Episode(rom,List.of(first,first)));
  rejects(()->new NesExperience.Frame(1,0,256,new int[64]));
  rejects(()->NesExperience.decode(text.replace(NesExperience.SCHEMA,"obs64-retro-nes-scroll-v1")));
  var conflict=new NesExperience.Episode(rom,List.of(first,new NesExperience.Frame(30,42,64,first.luma())));
  check(new NesExperience.Policy(conflict).suggest(rom,first).isEmpty());
  check(new NesExperience.Policy(restored).report().contains("UNKNOWN"));
  System.out.println("NES_EXPERIENCE_PASS checks="+checks+" ROM-bound/lab-only/unknown-reward");
 }
}
