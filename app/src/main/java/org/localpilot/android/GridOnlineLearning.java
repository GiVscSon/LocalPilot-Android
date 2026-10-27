package org.localpilot.android;

/**
 * Bounded, per-game online evidence about observable responses to grid moves.
 * Only a verified post-ACK board transition is a positive observation.
 * A timeout with a confirmed Android ACK is recorded separately as
 * unconfirmed, not misrepresented as a game defeat or incorrect move.
 * This model changes ranking by at most six points and never makes an illegal
 * action eligible. It does not train a neural network.
 */
public final class GridOnlineLearning {
 private static final int BUCKETS=9, MAX_COUNT=4000;
 private final int[] confirmed=new int[BUCKETS],unconfirmed=new int[BUCKETS];
 private static int bucket(GridPuzzlePolicy.Decision d){
  if(d==null)throw new IllegalArgumentException("missing move");
  int mode=switch(d.mode){
   case SWAP_MATCH3->0;case TAP_GROUP->1;case TRACE_LINE->2;
  };
  int size=d.expectedCleared>=5?2:d.expectedCleared>=4?1:0;
  return 3*mode+size;
 }
 public void recordVerified(GridPuzzlePolicy.Decision d){update(bucket(d),true);}
 public void recordNoResponse(GridPuzzlePolicy.Decision d){update(bucket(d),false);}
 private void update(int i,boolean success){
  if(confirmed[i]+unconfirmed[i]>=MAX_COUNT){
   confirmed[i]=(confirmed[i]+1)/2;
   unconfirmed[i]=(unconfirmed[i]+1)/2;
  }
  if(success)confirmed[i]++;else unconfirmed[i]++;
 }
 /** Smoothed reliability bonus. Only influences ordering of ALREADY safe moves. */
 public double bonus(GridPuzzlePolicy.Decision d){
  int i=bucket(d),n=confirmed[i]+unconfirmed[i];
  if(n<3)return 0;
  double posterior=(confirmed[i]+2.0)/(n+4.0);
  return Math.max(-6,Math.min(6,(posterior-.65)*18.0));
 }
 public int verifiedCount(){int n=0;for(int v:confirmed)n+=v;return n;}
 public int notConfirmedCount(){int n=0;for(int v:unconfirmed)n+=v;return n;}
 public String encode(){
  StringBuilder b=new StringBuilder("v1");
  for(int i=0;i<BUCKETS;i++)b.append(';').append(confirmed[i]).append(',').append(unconfirmed[i]);
  return b.toString();
 }
 public static GridOnlineLearning decode(String saved){
  if(saved==null||saved.length()>256)throw new IllegalArgumentException("grid learning format");
  String[] parts=saved.split(";",-1);
  if(parts.length!=BUCKETS+1||!parts[0].equals("v1"))throw new IllegalArgumentException("grid learning version");
  GridOnlineLearning result=new GridOnlineLearning();
  try{
   for(int i=0;i<BUCKETS;i++){
    String[] counts=parts[i+1].split(",",-1);
    if(counts.length!=2)throw new IllegalArgumentException();
    int good=Integer.parseInt(counts[0]),unknown=Integer.parseInt(counts[1]);
    if(good<0||unknown<0||good>MAX_COUNT||unknown>MAX_COUNT||good+unknown>MAX_COUNT)
     throw new IllegalArgumentException();
    result.confirmed[i]=good;result.unconfirmed[i]=unknown;
   }
  }catch(NumberFormatException malformed){throw new IllegalArgumentException("grid learning counters",malformed);}
  return result;
 }
}
