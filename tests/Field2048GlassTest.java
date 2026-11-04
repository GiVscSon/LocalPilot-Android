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
  // 31-Oct user's labelled real screen: r2c2=64, nearby r4c2=8.
  // Only colour summaries derived from the private screenshot are included.
  // A pastel border/glare at top-left and white numeral at the centre must
  // not cause the reader to invent a blank tile or turn a real 64 into 8.
  int[][] october={
    {rgb(233,96,208),rgb(240,220,219),rgb(236,132,49),rgb(240,220,219)},
    {rgb(236,132,49),rgb(197,126,124),rgb(240,220,219),rgb(240,220,219)},
    {rgb(65,53,137),rgb(240,220,219),rgb(240,220,219),rgb(240,220,219)},
    {rgb(66,57,156),rgb(187,68,108),rgb(237,137,53),rgb(240,220,219)}
  };
  final int[] octoberExpected={
    4,0,2,0, 2,64,0,0, 16,0,0,0, 16,8,2,0
  };
  PixelBoardReader.Pixels photoDerived=(x,y)->{
   int row=Math.min(3,y/160),col=Math.min(3,x/160),value=octoberExpected[row*4+col];
   double fx=(x%160)/160.0,fy=(y%160)/160.0;
   if(value>0&&fx>=.18&&fx<.36&&fy>=.18&&fy<.33)
    return rgb(236,204,214); // top-left translucent raised edge
   if(value>0&&fx>=.40&&fx<.62&&fy>=.36&&fy<.64)
    return rgb(255,253,253); // oversized white number; not its identity
   return october[row][col];
  };
  var photographed=Color2048Reader.read(photoDerived,640,640,0,0,640,640);
  for(int i=0;i<16;i++)
   check(photographed.cells[i]==octoberExpected[i],
     "31-Oct board r"+(i/4+1)+"c"+(i%4+1)+" must read "+octoberExpected[i]);
  var capturedSession=new Color2048Reader.Session();
  check(capturedSession.read(photoDerived,640,640,0,0,640,640,null,0,450).same(photographed),
    "session must recognize numbered 64 without a false 8 or a guess at 128");
  check(capturedSession.learnedCount()==0,"labelled 64 reference is not unsupervised neural learning");
  // Unknown vivid tile is still rejected, despite the wider inner sample.
  int[][] foreignColor=new int[4][4];
  for(int i=0;i<4;i++)foreignColor[i]=october[i].clone();
  foreignColor[1][1]=rgb(190,170,90);
  try{
   new Color2048Reader.Session().read(pixels(foreignColor),640,640,0,0,640,640,null,0,500);
   throw new AssertionError("foreign first-frame colour was guessed as 64");
  }catch(IllegalArgumentException expected){assertions++;}
  // A user may choose the generic "2048" profile while the Market renders
  // the glass-colored skin. A strict failed legacy read may try the bounded
  // color reader only at startup, and only an actual complete board switches.
  boolean rejectedLegacy=false;
  try{PixelBoardReader.read(frame,640,640,0,0,640,640);}
  catch(IllegalArgumentException unknown){rejectedLegacy=true;}
  check(rejectedLegacy,"legacy reader must not silently accept the Market glass skin");
  var autoSession=new Color2048Reader.Session();
  var selected=Game2048AutoReader.read(frame,640,640,0,0,640,640,
     autoSession,false,true,null,0,450);
  check(selected.switched()&&selected.colored()&&selected.board().same(board),
     "known Market board switches to the verified color reader");
  var nextRead=Game2048AutoReader.read(frame,640,640,0,0,640,640,
     autoSession,true,false,null,0,550);
  check(!nextRead.switched()&&nextRead.colored()&&nextRead.board().same(board),
     "second stable frame stays in the same reader");
  try{
   Game2048AutoReader.read(frame,640,640,0,0,640,640,
     new Color2048Reader.Session(),false,false,null,0,450);
   throw new AssertionError("fallback occurred without explicit startup permission");
  }catch(IllegalArgumentException expected){assertions++;}
  try{
   Game2048AutoReader.read(frame,640,640,0,0,640,640,
     new Color2048Reader.Session(),false,true,board,0,450);
   throw new AssertionError("mid-gesture palette fallback was permitted");
  }catch(IllegalArgumentException expected){assertions++;}
  var unknownPixels=(PixelBoardReader.Pixels)(x,y)->0x22cbe0;
  try{
   Game2048AutoReader.read(unknownPixels,640,640,0,0,640,640,
     new Color2048Reader.Session(),false,true,null,0,450);
   throw new AssertionError("foreign colors were promoted to a Market tile");
  }catch(IllegalArgumentException expected){assertions++;}

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
  // A slightly shaded pre-existing 32 may be read in Soft Vision without
  // inventing the value of an unrelated foreign tile.
  int[][] shaded32=new int[4][4];
  for(int i=0;i<4;i++)shaded32[i]=CAPTURED_RGB[i].clone();
  shaded32[0][0]=rgb(75,63,65);
  var relaxed=new Color2048Reader.Session();
  relaxed.setStrictMode(false);
  check(relaxed.read(pixels(shaded32),640,640,0,0,640,640,null,0,700).cells[0]==32,
    "soft visual tolerance reads a shaded known 32 on the first frame");
  // Persisted game-scoped 64 is a known value even before a merge in this
  // session; the very same colour with no such evidence stays UNKNOWN.
  int[][] persisted64=new int[4][4];
  for(int i=0;i<4;i++)persisted64[i]=CAPTURED_RGB[i].clone();
  persisted64[0][0]=rgb(34,153,187);
  var recovered=new Color2048Reader.Session();recovered.setStrictMode(false);
  recovered.restoreLearned("v1;-;2299bb;-;-;-;-;-");
  check(recovered.read(pixels(persisted64),640,640,0,0,640,640,null,0,750).cells[0]==64,
    "known persisted 64 reads immediately without a new merge");
  try{
   new Color2048Reader.Session().read(pixels(persisted64),640,640,0,0,640,640,null,0,750);
   throw new AssertionError("unmapped 64 paint cannot safely imply its value");
  }catch(IllegalArgumentException expected){assertions++;}

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
