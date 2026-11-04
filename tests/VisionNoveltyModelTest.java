package org.localpilot.android;

public final class VisionNoveltyModelTest {
 private static int checks;
 private static void check(boolean condition,String message){
  checks++;if(!condition)throw new AssertionError(message);
 }
 public static void main(String[] args){
  VisionNoveltyMemory m=new VisionNoveltyMemory();
  check(m.size()==0&&m.stableCount()==0,"new profile has no observations");
  m.observe(0xe43715,85,1000);
  m.observe(0xe43715,85,1000);
  check(m.size()==1&&m.identify(0xe43715,85)==0,"duplicate frame does not create confidence");
  m.observe(0xde3a16,83,1200);
  check(m.identify(0xe43715,85)==0,"two frames not enough");
  VisionNoveltyMemory resumed=VisionNoveltyMemory.decode(m.encode());
  resumed.observe(0xe63611,80,1400);
  check(resumed.size()==1&&resumed.stableCount()==1,"third distinct frame confirms one recurring appearance");
  check(resumed.identify(0xe43715,85)==1,"retrieved appearance resolves to known cluster identifier");
  check(resumed.identify(0x00dfff,250)==0,"unrelated appearance is not identified");
  String saved=resumed.encode();
  VisionNoveltyMemory later=VisionNoveltyMemory.decode(saved);
  later.observe(0xe23414,82,1700);
  check(later.size()==1&&later.identify(0xe43715,85)==1,"subsequent session adds evidence to same cluster");
  int[] signature=VisionNoveltyMemory.appearance(0xff0000,0x000000,0x000000);
  check(signature[0]==0xff0000&&signature[1]>0,"spatial contrast is a visual feature");
  for(int i=0;i<80;i++)later.observe((i*163<<8)&0xffffff,i%256,2000+i);
  check(later.size()<=24,"unknown appearance memory has hard capacity");
  try{VisionNoveltyMemory.decode("v1;00ffee,256,3");throw new AssertionError("invalid shape allowed");}
  catch(IllegalArgumentException expected){checks++;}
  try{later.observe(0xfffffff,5,3500);throw new AssertionError("invalid color allowed");}
  catch(IllegalArgumentException expected){checks++;}
  System.out.println("Vision novelty cumulative heuristic: "+checks+" assertions PASS");
 }
}
