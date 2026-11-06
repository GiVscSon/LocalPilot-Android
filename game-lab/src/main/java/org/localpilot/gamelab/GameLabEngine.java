package org.localpilot.gamelab;

import java.util.Arrays;
import java.util.Random;

/**
 * Deterministic, deliberately simplified mini-games for live Android UI tests.
 * Input is the OS-delivered MotionEvent, NOT a fake Android accessibility ACK.
 * These simulated states NEVER count as real Market-game acceptance.
 */
public final class GameLabEngine {
 public enum Game {
  BUBBLE("Шипучка",9), ARI("Прыгун Ари",0),
  MATCH("Мэтч Ворлд",6), PADDLE("Карканоид",1),
  TRAFFIC("Маркет Раш",2), FLAPPY("Гошан",3),
  BOARD2048("2048",4), HEXON("Гексон",-1),
  PROTECTED_SWAP("Нащупай ход · обмен",11), CAT_TOWER("Котобашня",-1);
  public final String label; public final int localPilotProfile;
  Game(String label,int profile){this.label=label;localPilotProfile=profile;}
 }
 public enum Fault { NONE, INPUT_IGNORED, FROZEN_VIEW, PANEL_OVERLAP }
 public enum Result { CHANGED, BLOCKED, NO_CHANGE }
 public final Game game;
 public Fault fault=Fault.NONE;
 public int score,actions,simulatedChanges,rejected;
 public float playerX=.5f,playerY=.55f,ballX=.5f,ballY=.43f;
 public float vx=.010f,vy=.017f,paddleX=.5f;
 public int lane=1,ticks,launches;
 public int[] cells,slots=new int[3];
 public int n;
 /** Same coordinates as GameSurface.drawSquareBoard, shared in the engine. */
 public static final float GRID_LEFT=22f/360f,GRID_TOP=140f/640f;
 public static final float GRID_RIGHT=(22f+315f)/360f;
 public static final float GRID_BOTTOM=(140f+315f)/640f;
 private final Random random;
 public GameLabEngine(Game game,long seed){
  if(game==null)throw new IllegalArgumentException("game");
  this.game=game;random=new Random(seed);reset();
 }
 public void reset(){
  score=actions=simulatedChanges=rejected=ticks=launches=0;
  playerX=.5f;playerY=.55f;ballX=.5f;ballY=.43f;
  vx=.01f;vy=.017f;paddleX=.5f;lane=1;
  Arrays.fill(slots,0);
  n=switch(game){case MATCH->7;case PROTECTED_SWAP->6;
   case BUBBLE->8;case BOARD2048->4;case HEXON->3;default->0;};
  cells=new int[n*n];
  if(game==Game.BOARD2048){cells[0]=2;cells[1]=2;}
  else if(game==Game.HEXON){for(int i=0;i<cells.length;i++)cells[i]=(i%3)+1;}
  else if(game==Game.BUBBLE){
   for(int i=0;i<2*n;i++)cells[i]=1+random.nextInt(4);
  }else if(game==Game.MATCH||game==Game.PROTECTED_SWAP){
   for(int r=0;r<n;r++)for(int c=0;c<n;c++)cells[r*n+c]=1+(r*2+c*3)%5;
   int r=game==Game.MATCH?0:2;
   cells[r*n]=1;cells[r*n+1]=2;cells[r*n+2]=1;cells[(r+1)*n+1]=1;
   if(game==Game.PROTECTED_SWAP){cells[0]=8;cells[1]=9;cells[n-1]=8;}
  }
 }
 public boolean protectedCell(int id){return game==Game.PROTECTED_SWAP&&
   (cells[id]==8||cells[id]==9);}
 public int fingerprint(){
  int hash=Arrays.hashCode(cells);
  hash=31*hash+Arrays.hashCode(slots);
  hash=31*hash+Float.floatToIntBits(playerX);
  hash=31*hash+Float.floatToIntBits(playerY);
  hash=31*hash+Float.floatToIntBits(paddleX);
  hash=31*hash+Float.floatToIntBits(ballX);
  hash=31*hash+Float.floatToIntBits(ballY);
  hash=31*hash+lane;hash=31*hash+score;
  return hash;
 }
 public Result gesture(float x,float y,float toX,float toY,long durationMs){
  if(!finite(x,y,toX,toY)||x<0||x>1||toX<0||toX>1
      ||y<0||y>1||toY<0||toY>1||durationMs<0||durationMs>20000){
   rejected++;return Result.BLOCKED;
  }
  // A focus probe on the title (or an out-of-play touch) is a rejected
  // non-gameplay event, not an attempted or verified game action.
  if(y<.13f||y>.90f){rejected++;return Result.BLOCKED;}
  actions++;
  if(fault==Fault.INPUT_IGNORED){rejected++;return Result.NO_CHANGE;}
  int old=fingerprint();
  float dx=toX-x,dy=toY-y;
  switch(game){
   case PADDLE -> {
    paddleX=clamp(toX,.10f,.90f);
    if(Math.abs(dx)<.02f)launches++;
   }
   case TRAFFIC -> {if(Math.abs(dx)>.045f)
    lane=Math.max(0,Math.min(2,lane+(dx<0?-1:1)));}
   case FLAPPY -> {vy=-.035f;playerY=clamp(playerY-.030f,.16f,.79f);}
   case ARI -> {playerX=wrap(playerX+(dx<-.018f?-.14f:dx>.018f?.14f:
      x<.5f?-.09f:.09f));playerY=clamp(playerY-.025f,.26f,.76f);}
   case CAT_TOWER -> {playerX=wrap(playerX+clamp(dx*1.3f,-.20f,.20f));
    playerY=clamp(playerY-.055f,.20f,.78f);score+=2;}
   case BUBBLE -> {int col=(int)(clamp(toX,0,.999f)*n);
    int row=0;while(row<n&&cells[row*n+col]!=0)row++;
    if(row<n){cells[row*n+col]=1+launches%4;launches++;score+=3;}
    else rejected++;
   }
   case HEXON -> {int slot=Math.min(2,(int)(clamp(toX,0,.999f)*3));
    if(slots[slot]<10){slots[slot]++;score++;}else rejected++;}
   case BOARD2048 -> {swipe2048(dx,dy);}
   case MATCH,PROTECTED_SWAP -> {
    int a=cellFrom(x,y),b=cellFrom(toX,toY);
    if(a>=0&&b>=0&&(Math.abs(a/n-b/n)+Math.abs(a%n-b%n)==1)
      &&!protectedCell(a)&&!protectedCell(b)){
     int tmp=cells[a];cells[a]=cells[b];cells[b]=tmp;
     if(!clearMatches()){
      tmp=cells[a];cells[a]=cells[b];cells[b]=tmp;
     }
    }else rejected++;
   }
  }
  if(fingerprint()!=old){simulatedChanges++;return Result.CHANGED;}
  return Result.NO_CHANGE;
 }
 public void tick(){
  ticks++;
  switch(game){
   case PADDLE -> {
    ballX+=vx;ballY+=vy;
    if(ballX<.055f||ballX>.945f){vx=-vx;ballX=clamp(ballX,.055f,.945f);}
    if(ballY<.20f){vy=Math.abs(vy);score++;}
    if(ballY>.80f){
     if(Math.abs(ballX-paddleX)<.125f)vy=-Math.abs(vy);
     else {ballX=.5f;ballY=.35f;vy=.017f;}
    }
   }
   case FLAPPY -> {vy=clamp(vy+.0028f,-.05f,.03f);playerY=clamp(playerY+vy,.15f,.80f);}
   case ARI -> {if(ticks%6==0)playerY=clamp(playerY+.014f,.27f,.70f);}
   case TRAFFIC -> {if(ticks%15==0)score++;}
   default -> {}
  }
 }
 public int cellFrom(float x,float y){
  if(n==0||x<GRID_LEFT||x>=GRID_RIGHT||y<GRID_TOP||y>=GRID_BOTTOM)return -1;
  int col=(int)((x-GRID_LEFT)/(GRID_RIGHT-GRID_LEFT)*n);
  int row=(int)((y-GRID_TOP)/(GRID_BOTTOM-GRID_TOP)*n);
  if(col<0||col>=n||row<0||row>=n)return -1;
  return row*n+col;
 }
 private boolean clearMatches(){
  boolean[] matched=new boolean[cells.length];
  int count=0;
  for(int r=0;r<n;r++)for(int c=0;c<n;c++){
   int here=cells[r*n+c];if(here==0||here>=8)continue;
   if(c+2<n&&cells[r*n+c+1]==here&&cells[r*n+c+2]==here)
    for(int k=c;k<n&&cells[r*n+k]==here;k++)matched[r*n+k]=true;
   if(r+2<n&&cells[(r+1)*n+c]==here&&cells[(r+2)*n+c]==here)
    for(int k=r;k<n&&cells[k*n+c]==here;k++)matched[k*n+c]=true;
  }
  for(int i=0;i<matched.length;i++)if(matched[i]){
   cells[i]=1+random.nextInt(5);count++;
  }
  score+=count*10;return count>0;
 }
 private void swipe2048(float dx,float dy){
  if(Math.max(Math.abs(dx),Math.abs(dy))<.045f)return;
  int[] before=cells.clone();
  for(int line=0;line<4;line++){
   int[] values=new int[4],packed=new int[4];int size=0;
   boolean horizontal=Math.abs(dx)>=Math.abs(dy);
   boolean forward=horizontal?dx>0:dy>0;
   for(int i=0;i<4;i++){
    int row=horizontal?line:(forward?3-i:i);
    int col=horizontal?(forward?3-i:i):line;
    values[i]=cells[row*4+col];
    if(values[i]!=0)packed[size++]=values[i];
   }
   int[] merged=new int[4];int count=0;
   for(int i=0;i<size;i++){
    if(i+1<size&&packed[i]==packed[i+1]){merged[count++]=packed[i]*2;score+=packed[i]*2;i++;}
    else merged[count++]=packed[i];
   }
   for(int i=0;i<4;i++){
    int row=horizontal?line:(forward?3-i:i);
    int col=horizontal?(forward?3-i:i):line;
    cells[row*4+col]=merged[i];
   }
  }
  if(!Arrays.equals(before,cells)){
   // A legal swipe spawns exactly one new tile in a seeded random empty slot.
   // 90% of spawns are 2, 10% are 4. This remains fully deterministic by seed
   // for reproducible GameLab regression runs, without leaking spawn to Pilot.
   int[] empty=new int[16];int count=0;
   for(int i=0;i<16;i++)if(cells[i]==0)empty[count++]=i;
   if(count>0)cells[empty[random.nextInt(count)]]=random.nextInt(10)==0?4:2;
  }
 }
 private static boolean finite(float... v){for(float x:v)if(!Float.isFinite(x))return false;return true;}
 private static float clamp(float x,float lo,float hi){return Math.max(lo,Math.min(hi,x));}
 private static float wrap(float x){if(x<0)return x+1;if(x>1)return x-1;return x;}
}
