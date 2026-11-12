package org.localpilot.shared;
import java.util.Arrays;
public final class ConsoleObservationTest {
 static void ok(boolean value){if(!value)throw new AssertionError("Bad NES luminance grid");}
 public static void main(String[] args){
  int[] pixels=new int[128*120];
  Arrays.fill(pixels,0xff000000);
  ok(ConsoleObservation.luminance8x8(pixels,128,120).matches("0{8}(/0{8}){7}"));
  Arrays.fill(pixels,0xffffffff);
  String white=ConsoleObservation.luminance8x8(pixels,128,120);
  ok(white.matches("F{8}(/F{8}){7}"));
  for(int i=0;i<32;i++)pixels[i]=0xff000000;
  ok(!white.equals(ConsoleObservation.luminance8x8(pixels,128,120)));
  boolean rejected=false;
  try{ConsoleObservation.luminance8x8(pixels,64,120);}
  catch(IllegalArgumentException expected){rejected=true;}
  ok(rejected);
  System.out.println("CONSOLE_FRAME_OBSERVATION_PASS");
 }
}
