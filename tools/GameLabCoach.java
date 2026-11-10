import java.util.List;
import org.localpilot.gamelab.GameLabEngine;
import org.localpilot.gamelab.GameLab2048Rules;
import org.localpilot.gamelab.GameLabBubbleRules;

/**
 * Deterministic, non-neural SCRIPTED coach for the simplified GameLab engine.
 * It sees only simulator fields. No Android gestures, real game observations,
 * promotion of weights, or claim of Market skill is possible.
 *
 * The coach is a stronger lab baseline, NOT a source of verified real reward.
 */
public final class GameLabCoach {
 private GameLabCoach(){}
 private static float clamp(float x,float lo,float hi){
  return Math.max(lo,Math.min(hi,x));
 }
 private static int cell(GameLabEngine env,float x,float y){
  return env.cellFrom(x,y);
 }
 /** Return the index of a move in a safe candidate list. */
 public static int choose(GameLabEngine env,List<float[]> moves){
  if(env==null||moves==null||moves.isEmpty())
   throw new IllegalArgumentException("no simulator actions");
  int best=-1;
  double bestValue=-Double.MAX_VALUE;
  for(int i=0;i<moves.size();i++){
   float[] m=moves.get(i);
   if(m==null||m.length!=4)
    throw new IllegalArgumentException("invalid simulated candidate");
   for(float x:m)if(!Float.isFinite(x)||x<0||x>1)
    throw new IllegalArgumentException("coordinate outside simulator");
   double utility=switch(env.game){
    case BOARD2048 -> boardUtility(env.cells,m);
    case MATCH,PROTECTED_SWAP -> swapUtility(env,m);
    case PADDLE -> paddleUtility(env,m);
    case BUBBLE -> bubbleUtility(env,m);
    case HEXON -> hexonUtility(env,m);
    case TRAFFIC -> trafficUtility(env,m);
    case ARI,CAT_TOWER -> jumperUtility(env,m);
    case FLAPPY -> 0;
   };
   if(utility>bestValue){bestValue=utility;best=i;}
  }
  return best;
 }
 /** Pure merge/compression only. Spawn belongs to the seeded environment,
  * not to planning; looking ahead must not consume its RNG or invent a tile. */
 public static int[] swipe2048(int[] cells,int direction){
  return GameLab2048Rules.move(cells,direction).tiles();
 }
 private static double boardValue(int[] board){
  int empty=0,highest=0;for(int v:board){if(v==0)empty++;highest=Math.max(highest,v);}
  int corner=Math.max(Math.max(board[0],board[3]),Math.max(board[12],board[15]));
  return empty*3.5+corner*.03+(board[0]==highest?2:0);
 }
 private static double boardUtility(int[] cells,float[] m){
  float dx=m[2]-m[0],dy=m[3]-m[1];
  if(Math.max(Math.abs(dx),Math.abs(dy))<.045f)return -1_000_000;
  int dir=Math.abs(dx)>=Math.abs(dy)?(dx<0?0:1):(dy<0?2:3);
  GameLab2048Rules.Move move=GameLab2048Rules.move(cells,dir);
  if(!move.changed())return -1_000_000;
  int[] next=move.tiles();int free=GameLab2048Rules.empty(next);
  if(free==0)return move.mergeScore()*8d+boardValue(next);
  double expected=0;
  for(int i=0;i<next.length;i++)if(next[i]==0){
   next[i]=2;double two=boardValue(next);next[i]=4;double four=boardValue(next);next[i]=0;
   expected+=(.9*two+.1*four)/free;
  }
  return move.mergeScore()*8d+expected;
 }
 private static int matchingCells(int[] data,int n){
  boolean[] hit=new boolean[n*n];
  for(int r=0;r<n;r++)for(int c=0;c<n;c++){
   int v=data[r*n+c];if(v<=0||v>=8)continue;
   if(c+2<n&&data[r*n+c+1]==v&&data[r*n+c+2]==v){
    for(int k=c;k<n&&data[r*n+k]==v;k++)hit[r*n+k]=true;
   }
   if(r+2<n&&data[(r+1)*n+c]==v&&data[(r+2)*n+c]==v){
    for(int k=r;k<n&&data[k*n+c]==v;k++)hit[k*n+c]=true;
   }
  }
  int count=0;for(boolean h:hit)if(h)count++;
  return count;
 }
 private static double swapUtility(GameLabEngine env,float[] m){
  int a=cell(env,m[0],m[1]),b=cell(env,m[2],m[3]);
  if(a<0||b<0||env.protectedCell(a)||env.protectedCell(b)
    ||Math.abs(a/env.n-b/env.n)+Math.abs(a%env.n-b%env.n)!=1)
   return -1_000_000;
  int[] board=env.cells.clone();
  board[a]=env.cells[b];board[b]=env.cells[a];
  int count=matchingCells(board,env.n);
  // Do not regard a null swap as a successful action.
  return count>0?count*100:0;
 }
 private static double paddleUtility(GameLabEngine env,float[] m){
  // Use the actual fixture's walls, brick collisions and paddle plane, rather
  // than a second trajectory implementation with old .80/.055 constants.
  GameLabEngine future=new GameLabEngine(GameLabEngine.Game.PADDLE,0);
  future.ballX=env.ballX;future.ballY=env.ballY;future.vx=env.vx;future.vy=env.vy;
  future.paddleX=env.paddleX;future.score=env.score;future.paddleHits=env.paddleHits;
  System.arraycopy(env.paddleBricks,0,future.paddleBricks,0,env.paddleBricks.length);
  future.gesture(m[0],m[1],m[2],m[3],140);
  final float plane=(530f-11f)/640f;
  for(int i=0;i<200;i++){
   float y=future.ballY,vy=future.vy;future.tick();
   if(vy>0&&y<=plane&&(future.ballY>=plane||future.vy<0&&y+vy>=plane))
    return -Math.abs(future.ballX-future.paddleX)*100;
  }
  return -Math.abs(future.ballX-future.paddleX)*100;
 }
 private static double bubbleUtility(GameLabEngine env,float[] m){
  int[] preview=env.cells.clone();
  GameLabBubbleRules.Shot shot=GameLabBubbleRules.fire(preview,env.n,1+env.launches%4,m[2]*360f,m[3]*640f);
  if(!shot.placed())return -1_000_000;
  // Actual collision/snap/connected-match/drop outcomes; zero for an ordinary
  // attachment, not a made-up column score. A pure preview never mutates env.
  return (shot.cleared()+shot.dropped())*100d;
 }
 private static double hexonUtility(GameLabEngine env,float[] m){
  int slot=Math.min(2,(int)(m[2]*3));
  return env.slots[slot]<10?-env.slots[slot]:-1_000_000;
 }
 private static double trafficUtility(GameLabEngine env,float[] m){
  float dx=m[2]-m[0];
  int target=env.lane+(dx<-.045f?-1:dx>.045f?1:0);
  return -Math.abs(Math.max(0,Math.min(2,target))-1);
 }
 private static double jumperUtility(GameLabEngine env,float[] m){
  float dx=m[2]-m[0];
  // Cat Tower moves in proportion to the drag; its TAP has no lateral shift.
  // Ari has discrete left/right moves and a side-dependent tap impulse.
  float shift=env.game==GameLabEngine.Game.CAT_TOWER?clamp(dx*1.3f,-.20f,.20f):
    dx<-.018f?-.14f:dx>.018f?.14f:(m[0]<.5f?-.09f:.09f);
  float next=env.playerX+shift;if(next<0)next+=1;else if(next>1)next-=1;
  return -Math.abs(.5f-next);
 }
}
