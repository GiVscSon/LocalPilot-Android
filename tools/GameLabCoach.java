import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.localpilot.gamelab.GameLabEngine;

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
 private static double reflected(double position){
  // Ping-pong reflection between laboratory walls.
  final double lo=.055,hi=.945,len=hi-lo;
  double phase=((position-lo)%(2*len)+2*len)%(2*len);
  return lo+(phase<=len?phase:2*len-phase);
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
 /** A pure 2048 move simulator, using GameLabEngine's line/merge rules. */
 public static int[] swipe2048(int[] cells,int direction){
  if(cells==null||cells.length!=16||direction<0||direction>3)
   throw new IllegalArgumentException("invalid 2048 snapshot");
  int[] copy=cells.clone();
  boolean horizontal=direction<2;
  boolean forward=direction==1||direction==3;
  for(int line=0;line<4;line++){
   int[] packed=new int[4];int size=0;
   for(int i=0;i<4;i++){
    int row=horizontal?line:(forward?3-i:i);
    int col=horizontal?(forward?3-i:i):line;
    int value=cells[row*4+col];
    if(value<0||value>2048)throw new IllegalArgumentException("invalid tile");
    if(value!=0)packed[size++]=value;
   }
   int[] merged=new int[4];int write=0;
   for(int i=0;i<size;i++){
    if(i+1<size&&packed[i]==packed[i+1]){
     merged[write++]=packed[i]*2;i++;
    }else merged[write++]=packed[i];
   }
   for(int i=0;i<4;i++){
    int row=horizontal?line:(forward?3-i:i);
    int col=horizontal?(forward?3-i:i):line;
    copy[row*4+col]=merged[i];
   }
  }
  // Exactly one deterministic tile is spawned by the simplified GameLab.
  if(!Arrays.equals(cells,copy)){
   for(int i=0;i<16;i++)if(copy[i]==0){copy[i]=2;break;}
  }
  return copy;
 }
 private static double boardUtility(int[] cells,float[] m){
  float dx=m[2]-m[0],dy=m[3]-m[1];
  int dir=Math.abs(dx)>=Math.abs(dy)?(dx<0?0:1):(dy<0?2:3);
  int[] next=swipe2048(cells,dir);
  if(Arrays.equals(cells,next))return -1_000_000;
  // Need to reward merges without pretending that every changed frame wins.
  int oldSum=0,newSum=0,empties=0,highest=0;
  for(int i=0;i<16;i++){
   oldSum+=cells[i];newSum+=next[i];if(next[i]==0)empties++;
   highest=Math.max(highest,next[i]);
  }
  // GameLab spawns one tile on a legal move. A net +2 alone is not a merge.
  int mergePoints=0;
  for(int v:new int[]{4,8,16,32,64,128,256,512,1024,2048,4096}){
   int beforeCount=0,afterCount=0;
   for(int a:cells)if(a==v)beforeCount++;
   for(int a:next)if(a==v)afterCount++;
   if(afterCount>beforeCount)mergePoints+=(afterCount-beforeCount)*v;
  }
  // The pure metric favors merges, space and stability of the max tile.
  int corner=Math.max(Math.max(next[0],next[3]),Math.max(next[12],next[15]));
  return mergePoints*8d+empties*3.5+corner*.03+
    (next[0]==highest?2:0)+Math.min(2,newSum-oldSum);
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
  double futureX=env.ballX;
  if(env.vy>0.0001f){
   double time=clamp((float)((.80-env.ballY)/env.vy),0,200);
   futureX=reflected(env.ballX+env.vx*time);
  }
  return -Math.abs(m[2]-futureX)*100;
 }
 private static double bubbleUtility(GameLabEngine env,float[] m){
  int column=Math.max(0,Math.min(env.n-1,(int)(m[2]*env.n)));
  int row=0;while(row<env.n&&env.cells[row*env.n+column]!=0)row++;
  if(row==env.n)return -1_000_000;
  int nextBall=1+env.launches%4;
  int neighbors=0;
  if(row>0&&env.cells[(row-1)*env.n+column]==nextBall)neighbors++;
  if(column>0&&env.cells[row*env.n+column-1]==nextBall)neighbors++;
  if(column+1<env.n&&env.cells[row*env.n+column+1]==nextBall)neighbors++;
  return neighbors*15-row*.2-Math.abs(column-env.n/2.0)*.01;
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
