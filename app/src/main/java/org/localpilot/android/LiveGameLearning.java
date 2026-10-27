package org.localpilot.android;

/**
 * Small, bounded per-game online model for real verified gameplay.
 * Stores no pixels, board layouts, identifiers or speculative outcomes.
 * Android gesture ACK is never an observation of game success.
 * Scores change only ordering among moves already allowed by the rule policy.
 */
public final class LiveGameLearning {
 public enum Game { GAME_2048, BUBBLE }
 private static final int CAP=4000, MAX_QUALITY=12;
 private final Game game;
 private final int[] verified, unconfirmed, qualityTotal;
 public LiveGameLearning(Game kind){
  if(kind==null)throw new IllegalArgumentException("game");
  game=kind;int n=kind==Game.GAME_2048?4:6;
  verified=new int[n];unconfirmed=new int[n];qualityTotal=new int[n];
 }
 public Game game(){return game;}
 private void require(Game expected){if(game!=expected)throw new IllegalArgumentException("wrong game model");}
 private static int bubbleBucket(int bounces,int removed,int dropped){
  if(bounces<0||bounces>1||removed<0||dropped<0)throw new IllegalArgumentException("shot");
  return bounces*3+(removed==0?0:dropped>0?2:1);
 }
 private void record(int bucket,int quality,boolean success){
  if(bucket<0||bucket>=verified.length||quality<0||quality>MAX_QUALITY)throw new IllegalArgumentException("bucket/quality");
  if(verified[bucket]+unconfirmed[bucket]>=CAP){
   verified[bucket]=(verified[bucket]+1)/2;
   unconfirmed[bucket]=(unconfirmed[bucket]+1)/2;
   qualityTotal[bucket]=Math.min(verified[bucket]*MAX_QUALITY,(qualityTotal[bucket]+1)/2);
  }
  if(success){verified[bucket]++;qualityTotal[bucket]+=quality;}
  else unconfirmed[bucket]++;
 }
 /** Call exclusively after exact VERIFIED_2048 post-ACK board transition. */
 public void verified2048(int direction,int actualMergeReward){
  require(Game.GAME_2048);
  if(direction<0||direction>3||actualMergeReward<0)throw new IllegalArgumentException("move");
  int quality=actualMergeReward==0?0:Math.min(MAX_QUALITY,32-Integer.numberOfLeadingZeros(actualMergeReward));
  record(direction,quality,true);
 }
 /** Call exclusively after BubbleSession.VERIFIED (exact predicted board). */
 public void verifiedBubble(int bounces,int removed,int dropped){
  require(Game.BUBBLE);
  record(bubbleBucket(bounces,removed,dropped),Math.min(MAX_QUALITY,(long)removed*2+(long)dropped*3>MAX_QUALITY?MAX_QUALITY:removed*2+dropped*3),true);
 }
 /** Only an ACK with expired, unverified response; never label it a game defeat. */
 public void unconfirmedBubble(int bounces,int removed,int dropped){
  require(Game.BUBBLE);record(bubbleBucket(bounces,removed,dropped),0,false);
 }
 /** Small directional preference; existing 2048 legality, heuristic and reward remain authoritative. */
 public double bonus2048(int direction){
  require(Game.GAME_2048);
  if(direction<0||direction>3)throw new IllegalArgumentException("direction");
  int n=verified[direction];if(n<3)return 0;
  double mean=(double)qualityTotal[direction]/n, confidence=(double)n/(n+8);
  return Math.max(-1.5,Math.min(3.0,(mean-2.0)*0.65*confidence));
 }
 /** Maximum six score points versus a normal bubble clear worth >=100 points. */
 public double bonusBubble(int bounces,int removed,int dropped){
  require(Game.BUBBLE);int i=bubbleBucket(bounces,removed,dropped),n=verified[i]+unconfirmed[i];
  if(n<3)return 0;
  double quality=verified[i]==0?0:(double)qualityTotal[i]/verified[i];
  double reliability=(verified[i]+1.0)/(n+2.0);
  double confidence=(double)n/(n+8);
  // An ACK without a proven effect must reduce rather than conceal the
  // success signal, even when a few early verified shots had high reward.
  return Math.max(-6,Math.min(6,((quality-3)*.8*reliability+(reliability-.75)*3)*confidence));
 }
 public int verifiedCount(){int sum=0;for(int x:verified)sum+=x;return sum;}
 public int unconfirmedCount(){int sum=0;for(int x:unconfirmed)sum+=x;return sum;}
 public String encode(){
  StringBuilder s=new StringBuilder("v1");
  for(int i=0;i<verified.length;i++)s.append(';').append(verified[i]).append(',').append(unconfirmed[i]).append(',').append(qualityTotal[i]);
  return s.toString();
 }
 public static LiveGameLearning decode(Game kind,String saved){
  if(saved==null||saved.length()>256)throw new IllegalArgumentException("learning format");
  LiveGameLearning result=new LiveGameLearning(kind);
  String[] parts=saved.split(";",-1);
  if(parts.length!=result.verified.length+1||!"v1".equals(parts[0]))throw new IllegalArgumentException("learning version");
  try{
   for(int i=0;i<result.verified.length;i++){
    String[] v=parts[i+1].split(",",-1);
    if(v.length!=3)throw new IllegalArgumentException("learning row");
    int ok=Integer.parseInt(v[0]),unknown=Integer.parseInt(v[1]),total=Integer.parseInt(v[2]);
    if(ok<0||unknown<0||total<0||ok+unknown>CAP||total>ok*MAX_QUALITY)throw new IllegalArgumentException("learning bounds");
    result.verified[i]=ok;result.unconfirmed[i]=unknown;result.qualityTotal[i]=total;
   }
  }catch(NumberFormatException e){throw new IllegalArgumentException("learning counters",e);}
  return result;
 }
}
