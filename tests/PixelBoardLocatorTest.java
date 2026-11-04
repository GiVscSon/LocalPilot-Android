package org.localpilot.android;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;

/** Synthetic rendered-pixel fixtures only; not Android screenshots or gameplay. */
public class PixelBoardLocatorTest {
 private static final int BG=0xbbada0;
 private static final int[] CELLS={2,4,0,8,0,2,4,0,8,0,2,4,0,8,0,2};
 private static int checks;

 public static void main(String[] args){
  searchStability();
  exactGridAndHeader();
  roundedGeometryAndLargeImage();
  separatedNearbyFrame();
  rejectImpostorAndJoinedHeader();
  rejectUnknownAndEmpty();
  overlayAndWindowBounds();
  ambiguousFields();
  malformedAndLimits();
  cancellationAndProviderErrors();
  System.out.println("PASS "+checks+" pixel-board-locator assertions");
 }

 private static void searchStability(){
  PixelBoardLocator.Stability s=new PixelBoardLocator.Stability();
  Scene scene=new Scene();scene.board(53,281,400,6,CELLS);
  // Same known marker geometry as a real locator result; pixel identity remains independent.
  BoardSelection.Candidate candidate=new BoardSelection.Candidate(BoardSelection.MARKER_2048,53,281,453,681);
  check(!s.observe(null,1000,1),"missing first");check(s.missingFrames()==1,"one missing frame");
  check(!s.observe(null,1000,2)&&s.missingFrames()==1,"duplicate missing frame not counted");
  check(!s.observe(null,1100,2)&&s.missingFrames()==2,"two frames do not report persistent missing");
  check(!s.observe(null,1200,3)&&s.missingFrames()==3,"three occluded/animated frames enter search");
  check(!s.observe(candidate,1300,4),"first recovered candidate never arms");
  check(!s.observe(candidate,1400,5),"changing candidate pixels never stable");
  check(s.observe(candidate,1500,5),"two fresh consistent candidates recover");
  check(s.missingFrames()==0,"successful recovery clears missing count");
  s.reset();check(!s.observe(candidate,1600,5),"old session cannot confirm new candidate");
 }

 private static void exactGridAndHeader(){
  Scene scene=new Scene();scene.board(53,281,400,6,CELLS);
  scene.rectangle(20,40,160,100,BG);
  BoardSelection.Candidate c=find(scene,800,1000,0,0,800,1000,0,0,0,0);
  sameBounds(c,53,281,453,681);
  check(c.profile==BoardSelection.Profile.GAME_2048);
  check(Arrays.equals(PixelBoardReader.read(scene,800,1000,c.left,c.top,c.right,c.bottom).cells,CELLS));
  BoardSelection.Candidate offset=find(scene,800,1000,20,180,780,950,20,180,40,230);
  sameBounds(offset,53,281,453,681);
 }

 private static void roundedGeometryAndLargeImage(){
  Scene odd=new Scene();odd.board(101,423,403,8,CELLS);
  sameBounds(find(odd,1080,1920,0,24,1080,1880,0,0,0,0),101,423,504,826);
  Scene large=new Scene();large.board(803,2301,1000,12,CELLS);
  AtomicInteger calls=new AtomicInteger();
  PixelBoardReader.Pixels measured=(x,y)->{calls.incrementAndGet();return large.rgb(x,y);};
  sameBounds(find(measured,3000,4000,0,0,3000,4000,0,0,0,0),803,2301,1803,3301);
  check(calls.get()<=4_000_000);
 }

 private static void rejectImpostorAndJoinedHeader(){
  Scene badge=new Scene();badge.rectangle(80,200,480,600,BG);
  badge.rectangle(86,206,474,594,Board2048.PALETTE[1]);
  // The old sparse parser alone accepts this badge as sixteen equal tiles.
  check(PixelBoardReader.read(badge,700,800,80,200,480,600).max()==2);
  reject("AUTO_BOARD_MISSING",()->find(badge,700,800,0,0,700,800,0,0,0,0));
  Scene joined=new Scene();joined.board(100,300,400,6,CELLS);
  joined.rectangle(100,220,500,280,BG);
  joined.rectangle(100,280,104,300,BG);
  reject("AUTO_BOARD_MISSING",()->find(joined,800,900,0,0,800,900,0,0,0,0));
 }

 private static void separatedNearbyFrame(){
  Scene scene=new Scene();scene.board(200,800,400,6,CELLS);
  scene.rectangle(197,800,199,1200,BG);
  sameBounds(find(scene,1080,2400,0,0,1080,2400,0,0,0,0),200,800,600,1200);
 }

 private static void rejectUnknownAndEmpty(){
  Scene unknown=new Scene();unknown.board(50,200,400,6,CELLS);
  unknown.rectangle(56,206,144,294,0xff00ff);
  reject("AUTO_BOARD_MISSING",()->find(unknown,700,800,0,0,700,800,0,0,0,0));
  Scene empty=new Scene();empty.board(50,200,400,6,new int[16]);
  reject("AUTO_BOARD_MISSING",()->find(empty,700,800,0,0,700,800,0,0,0,0));
  reject("AUTO_BOARD_MISSING",()->find((x,y)->0xfaf8ef,700,800,0,0,700,800,0,0,0,0));
 }

 private static void overlayAndWindowBounds(){
  Scene scene=new Scene();scene.board(53,281,400,6,CELLS);
  reject("AUTO_BOARD_MISSING",()->find(scene,800,1000,0,0,800,1000,250,450,300,500));
  sameBounds(find(scene,800,1000,0,0,800,1000,0,281,53,681),53,281,453,681);
  reject("AUTO_BOARD_MISSING",()->find(scene,800,1000,0,0,400,1000,0,0,0,0));
  Scene edge=new Scene();edge.board(80,200,400,6,CELLS);
  reject("AUTO_BOARD_MISSING",()->find(edge,700,800,0,0,700,580,0,0,0,0));
  reject("AUTO_BOARD_MISSING",()->find(edge,700,800,0,0,460,800,0,0,0,0));
  reject("AUTO_BOARD_MISSING",()->find(edge,700,800,0,0,700,800,80,580,480,620));
  reject("AUTO_BOARD_MISSING",()->find(edge,700,800,0,0,700,800,460,200,500,600));
  Scene tooSmall=new Scene();tooSmall.board(40,200,150,6,CELLS);
  reject("AUTO_BOARD_MISSING",()->find(tooSmall,500,600,0,0,500,600,0,0,0,0));
 }

 private static void ambiguousFields(){
  Scene scene=new Scene();scene.board(50,200,300,6,CELLS);scene.board(500,200,300,6,CELLS);
  reject("AUTO_BOARD_AMBIGUOUS",()->find(scene,1000,700,0,0,1000,700,0,0,0,0));
 }

 private static void malformedAndLimits(){
  PixelBoardReader.Pixels pixels=(x,y)->0xfaf8ef;
  reject("AUTO_BOARD_GEOMETRY",()->find(pixels,0,500,0,0,500,500,0,0,0,0));
  reject("AUTO_BOARD_GEOMETRY",()->find(null,500,500,0,0,500,500,0,0,0,0));
  reject("AUTO_BOARD_GEOMETRY",()->find(pixels,500,500,-1,0,500,500,0,0,0,0));
  reject("AUTO_BOARD_GEOMETRY",()->find(pixels,500,500,0,0,501,500,0,0,0,0));
  reject("AUTO_BOARD_GEOMETRY",()->find(pixels,500,500,100,100,100,400,0,0,0,0));
  reject("AUTO_BOARD_LIMIT",()->find(pixels,Integer.MAX_VALUE,2,0,0,100,100,0,0,0,0));
  Scene crowded=new Scene();
  for(int i=0;i<65;i++)crowded.board(20+(i%9)*180,20+(i/9)*180,160,6,CELLS);
  // A first result may not be returned when the search cannot exhaust the candidate set.
  reject("AUTO_BOARD_LIMIT",()->find(crowded,1640,1480,0,0,1640,1480,0,0,0,0));
 }

 private static void cancellationAndProviderErrors(){
  Thread.currentThread().interrupt();
  try{
   find((x,y)->0xfaf8ef,500,600,0,0,500,600,0,0,0,0);
   throw new AssertionError("interrupted search returned a candidate");
  }catch(CancellationException expected){check(true);}
  finally{Thread.interrupted();}
  RuntimeException unexpected=new IllegalStateException("fixture provider failure");
  try{
   find((x,y)->{throw unexpected;},500,600,0,0,500,600,0,0,0,0);
   throw new AssertionError("provider failure was swallowed");
  }catch(RuntimeException error){check(error==unexpected);}
  Scene scene=new Scene();scene.board(200,800,400,6,CELLS);
  IllegalArgumentException parserPixel=new IllegalArgumentException("fixture pixel read failed");
  try{
   find((x,y)->{if(x==223&&y==824)throw parserPixel;return scene.rgb(x,y);},
     1080,2400,0,0,1080,2400,0,0,0,0);
   throw new AssertionError("parser-time provider failure was swallowed");
  }catch(RuntimeException error){check(error==parserPixel);}
 }

 private static BoardSelection.Candidate find(PixelBoardReader.Pixels pixels,int width,int height,
   int left,int top,int right,int bottom,int ol,int ot,int or,int ob){
  return PixelBoardLocator.find(pixels,width,height,left,top,right,bottom,ol,ot,or,ob);
 }

 private static void sameBounds(BoardSelection.Candidate c,int left,int top,int right,int bottom){
  check(c.left==left);check(c.top==top);check(c.right==right);check(c.bottom==bottom);
 }

 private static void reject(String code,Runnable operation){
  try{operation.run();throw new AssertionError("expected "+code);}
  catch(BoardSelection.SelectionFailure expected){check(code.equals(expected.code));}
 }

 private static void check(boolean condition,String why){checks++;if(!condition)throw new AssertionError(why);}
 private static void check(boolean condition){checks++;if(!condition)throw new AssertionError("check "+checks);}

 private interface Layer {Integer color(int x,int y);}
 private static final class Scene implements PixelBoardReader.Pixels {
  private final List<Layer> layers=new ArrayList<>();
  void rectangle(int l,int t,int r,int b,int color){
   layers.add((x,y)->x>=l&&x<r&&y>=t&&y<b?color:null);
  }
  void board(int left,int top,int side,int gap,int[] cells){
   int[] copy=cells.clone();
   layers.add((x,y)->{
    if(x<left||x>=left+side||y<top||y>=top+side)return null;
    int col=(x-left)*4/side,row=(y-top)*4/side;
    int l=left+col*side/4,t=top+row*side/4;
    int r=left+(col+1)*side/4,b=top+(row+1)*side/4;
    if(x<l+gap||x>=r-gap||y<t+gap||y>=b-gap)return BG;
    int value=copy[row*4+col];
    return Board2048.PALETTE[value==0?0:Integer.numberOfTrailingZeros(value)];
   });
  }
  @Override public int rgb(int x,int y){
   for(int i=layers.size()-1;i>=0;i--){Integer color=layers.get(i).color(x,y);if(color!=null)return color;}
   return 0xfaf8ef;
  }
 }
}
