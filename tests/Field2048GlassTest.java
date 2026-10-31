package org.localpilot.android;

/**
 * Pure Java reproduction of the uploaded real-market colour measurements
 * (2026-10-30 14:32, 4x4). No screenshot is shipped in the APK or repository.
 */
public final class Field2048GlassTest {
 private static int assertions;
 private static void check(boolean value,String reason){assertions++;if(!value)throw new AssertionError(reason);}
 private static int rgb(int r,int g,int b){return r<<16|g<<8|b;}
 private static final int[][] CAPTURED_RGB={
   {rgb(189,174,193),rgb(217,185,190),rgb(252,134,1),rgb(232,194,192)},
   {rgb(224,208,231),rgb(253,90,225),rgb(253,92,227),rgb(253,222,210)},
   {rgb(235,215,242),rgb(241,218,240),rgb(244,219,233),rgb(245,219,221)},
   {rgb(236,212,245),rgb(237,215,243),rgb(241,216,240),rgb(241,217,239)}
 };
 private static final int[] EXPECTED={
   0,0,2,0,
   0,4,4,0,
   0,0,0,0,
   0,0,0,0
 };
 private static PixelBoardReader.Pixels pixels(int[][] rgb){
  return (x,y)->rgb[Math.min(3,Math.max(0,y/160))][Math.min(3,Math.max(0,x/160))];
 }
 public static void main(String[] args){
  var frame=pixels(CAPTURED_RGB);
  Board2048 board=Color2048Reader.read(frame,640,640,0,0,640,640);
  for(int i=0;i<16;i++)
   check(board.cells[i]==EXPECTED[i],"real screenshot 4x4 r"+(i/4+1)+" c"+(i%4+1)+
       " expected="+EXPECTED[i]+" observed="+board.cells[i]);
  Color2048Reader.Session session=new Color2048Reader.Session();
  Board2048 staged=session.read(frame,640,640,0,0,640,640,null,0,500);
  check(staged.same(board),"game-scoped session must classify the same glass cells");
  check(session.learnedCount()==0,"empty glass must never become a learned tile");

  // A dark tile 32 is present before we have witnessed a merge. Its narrow
  // recorded RGB fingerprint is recognized, but is never "learned" from a
  // guessed new palette entry.
  int[][] with32=new int[4][4];
  for(int i=0;i<4;i++)with32[i]=CAPTURED_RGB[i].clone();
  with32[0][0]=rgb(50,50,51);
  Board2048 dark=Color2048Reader.read(pixels(with32),640,640,0,0,640,640);
  check(dark.cells[0]==32&&dark.cells[1]==0&&dark.cells[2]==2,"32 and translucent background are distinct");
  var darkSession=new Color2048Reader.Session();
  check(darkSession.read(pixels(with32),640,640,0,0,640,640,null,0,650).same(dark)
    &&darkSession.learnedCount()==0,"preexisting 32 does not invent a learned palette");
  // A vivid, foreign pixel is not a safe empty cell or known numbered tile.
  int[][] altered=new int[4][4];
  for(int i=0;i<4;i++)altered[i]=CAPTURED_RGB[i].clone();
  altered[1][3]=rgb(35,200,225);
  try{
   Color2048Reader.read(pixels(altered),640,640,0,0,640,640);
   throw new AssertionError("foreign vivid object classified as valid tile");
  }catch(IllegalArgumentException expected){assertions++;}
  try{
   session.read(pixels(altered),640,640,0,0,640,640,null,0,600);
   throw new AssertionError("foreign animated cell classified in session mode");
  }catch(IllegalArgumentException expected){assertions++;}

  // Never accept an arbitrary tall full-screen crop as a 4x4 game board.
  try{
   Color2048Reader.read(frame,640,800,0,0,640,800);
   throw new AssertionError("nonsquare board admitted");
  }catch(IllegalArgumentException expected){assertions++;}

  System.out.println("Real Market 2048 glass-palette regression: "+assertions+" assertions PASS");
 }
}
