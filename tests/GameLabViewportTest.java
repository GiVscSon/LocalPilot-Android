package org.localpilot.gamelab;

import org.localpilot.android.Board2048;

public final class GameLabViewportTest {
 private static int checks;
 private static void yes(boolean condition,String message){
  checks++;if(!condition)throw new AssertionError(message);
 }
 private static boolean near(float a,float b){return Math.abs(a-b)<.00001f;}
 public static void main(String[] unused){
  GameLabViewport wide=new GameLabViewport(1080,950);
  yes(near(wide.scale,950f/640f),"wide phone preserves vertical scale");
  yes(wide.offsetX>200&&near(wide.offsetY,0),"game is centered horizontally");
  yes(near(wide.x(wide.px(.5f)),.5f),"roundtrip x");
  yes(near(wide.y(wide.py(.5f)),.5f),"roundtrip y");
  yes(wide.inGame(wide.px(.64f),wide.py(.28f)),"bubble witness is inside viewport");
  yes(!wide.inGame(8,400),"letterbox taps cannot fire game actions");
  GameLabViewport tall=new GameLabViewport(360,1000);
  yes(near(tall.scale,1)&&near(tall.offsetY,180),"tall phone centers vertically");
  yes(!tall.inGame(180,140)&&tall.inGame(180,500),"top system bar excluded");
  GameLabViewport square=new GameLabViewport(800,800);
  float xSide=square.px((22f+315f)/360f)-square.px(22f/360f);
  float ySide=square.py((140f+315f)/640f)-square.py(140f/640f);
  yes(near(xSide,ySide),"2048 and match boards render as genuine squares");
  yes(!square.inGame(-100,100)&&!square.inGame(400,1000),"outside press rejected");
  try{new GameLabViewport(0,800);throw new AssertionError("invalid bounds accepted");}
  catch(IllegalArgumentException expected){checks++;}
  // The renderer and input map must agree on the virtual 7x7 cell location.
  int n=7;int col=3,row=4;
  float worldX=GameLabEngine.GRID_LEFT+(col+.5f)*(GameLabEngine.GRID_RIGHT-GameLabEngine.GRID_LEFT)/n;
  float worldY=GameLabEngine.GRID_TOP+(row+.5f)*(GameLabEngine.GRID_BOTTOM-GameLabEngine.GRID_TOP)/n;
  GameLabEngine grid=new GameLabEngine(GameLabEngine.Game.MATCH,101);
  yes(grid.cellFrom(wide.x(wide.px(worldX)),wide.y(wide.py(worldY)))==row*n+col,
    "screen touch lands in the same logical grid cell that is drawn");
  for(int i=0;i<Board2048.PALETTE.length;i++){
   int tile=i==0?0:1<<i;
   int argb=GameLab2048Palette.paint(tile);
   yes((argb&0xffffff)==Board2048.PALETTE[i],
      "GameLab tile paint matches the actual production vision palette "+tile);
   yes(Board2048.colorTile(argb)==tile,
      "strict production reader recognizes the test tile "+tile);
  }
  GameLabEngine exercise=new GameLabEngine(GameLabEngine.Game.BOARD2048,212);
  Board2048 initial=new Board2048(exercise.cells);
  yes(exercise.gesture(.40f,.45f,.70f,.45f,160)==GameLabEngine.Result.CHANGED,
      "an actual swipe modifies the 2048 test game");
  Board2048 result=new Board2048(exercise.cells);
  yes(initial.verifies(result,Board2048.RIGHT),
      "the external game makes exactly a legal merge and one verified spawn");
  yes(exercise.simulatedChanges==1,"only synthetic GameLab changed, not a Market win");
  System.out.println("GAMELAB_VIEWPORT_PASS checks="+checks);
 }
}
