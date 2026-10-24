package org.localpilot.android;
public final class DisplayGeometryTest {
 private static int checks;
 private static void check(boolean value){checks++;if(!value)throw new AssertionError("geometry "+checks);}
 public static void main(String[] args){
  DisplayGeometry g=new DisplayGeometry(1080,2400,0,420);
  check(!g.requiresRebind(new DisplayGeometry(1080,2400,0,420)));
  check(g.requiresRebind(null));
  check(g.requiresRebind(new DisplayGeometry(1081,2400,0,420)));
  check(g.requiresRebind(new DisplayGeometry(1080,2399,0,420)));
  check(g.requiresRebind(new DisplayGeometry(1080,2400,2,420)));
  check(g.requiresRebind(new DisplayGeometry(2400,1080,1,420)));
  check(g.requiresRebind(new DisplayGeometry(1080,2400,0,421)));
  for(int[] bad:new int[][]{{0,1,0,1},{1,-1,0,1},{1,1,-1,1},{1,1,4,1},{1,1,0,0}}){
   try{new DisplayGeometry(bad[0],bad[1],bad[2],bad[3]);throw new AssertionError("invalid accepted");}catch(IllegalArgumentException expected){checks++;}
  }
  System.out.println("DisplayGeometry: "+checks+" assertions PASS");
 }
}
