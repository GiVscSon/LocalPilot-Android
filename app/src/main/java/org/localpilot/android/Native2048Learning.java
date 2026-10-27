package org.localpilot.android;

/**
 * Verified on-device 2048 evidence, scoped by the caller to one target game.
 * Never learns from gesture ACK, unverified board changes or no-op moves.
 * Direction bonuses only rank independently legal actions.
 */
public final class Native2048Learning {
 private static final int DIRECTIONS=4, MAX=4000;
 private final int[] count=new int[DIRECTIONS], merges=new int[DIRECTIONS];
 public void record(Board2048 before,int direction,Board2048 after){
  if(before==null||after==null||direction<0||direction>=DIRECTIONS||!before.verifies(after,direction))
   throw new IllegalArgumentException("verified 2048 transition required");
  int reward=Verified2048.mergeReward(before,direction,after);
  if(count[direction]>=MAX){count[direction]=(count[direction]+1)/2;merges[direction]=(merges[direction]+1)/2;}
  count[direction]++;
  if(reward>0)merges[direction]++;
 }
 public int evidence(){int n=0;for(int c:count)n+=c;return n;}
 public double[] bonus(){
  double[] result=new double[DIRECTIONS];
  for(int d=0;d<DIRECTIONS;d++){
   if(count[d]<5)continue;
   double rate=(merges[d]+2.0)/(count[d]+4.0);
   result[d]=Math.max(-.2,Math.min(.2,(rate-.5)*.4));
  }
  return result;
 }
 public String encode(){
  StringBuilder s=new StringBuilder("v1");
  for(int d=0;d<DIRECTIONS;d++)s.append(';').append(count[d]).append(',').append(merges[d]);
  return s.toString();
 }
 public static Native2048Learning decode(String encoded){
  if(encoded==null||encoded.length()>100)throw new IllegalArgumentException("native 2048 format");
  String[] parts=encoded.split(";",-1);
  if(parts.length!=5||!"v1".equals(parts[0]))throw new IllegalArgumentException("native 2048 version");
  Native2048Learning result=new Native2048Learning();
  try{
   for(int d=0;d<DIRECTIONS;d++){
    String[] pair=parts[d+1].split(",",-1);
    if(pair.length!=2)throw new IllegalArgumentException("native 2048 pair");
    int c=Integer.parseInt(pair[0]),m=Integer.parseInt(pair[1]);
    if(c<0||c>MAX||m<0||m>c)throw new IllegalArgumentException("native 2048 counters");
    result.count[d]=c;result.merges[d]=m;
   }
  }catch(NumberFormatException e){throw new IllegalArgumentException("native 2048 counts",e);}
  return result;
 }
 /** Safe real-game feedback, bounded below the policy limit. */
 public static double[] combine(Native2048Learning nativeModel){
  return nativeModel==null?new double[4]:nativeModel.bonus();
 }
}
