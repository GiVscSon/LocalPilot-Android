package org.localpilot.android;
import java.util.*;
public final class Upgrade726Test {
 private static int checks;
 private static void check(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
 private static void rejects(Runnable r,String why){try{r.run();throw new AssertionError(why);}catch(IllegalArgumentException|IllegalStateException expected){checks++;}}
 public static void main(String[] args){palette();pacing();scroll();grid();System.out.println("Upgrade 0.7.26: "+checks+" assertions PASS (synthetic; no physical-device claims)");}
 private static int distance(int a,int b){int r=(a>>16&255)-(b>>16&255),g=(a>>8&255)-(b>>8&255),v=(a&255)-(b&255);return r*r+g*g+v*v;}
 private static int[] colors(){
  int[] c=new int[12];c[0]=0xddd0d0;c[1]=0xf56400;c[2]=0xf928be;c[3]=0xc02a64;c[4]=0x35268c;int count=5;
  for(int r:new int[]{0,85,170,255})for(int g:new int[]{0,85,170,255})for(int b:new int[]{0,85,170,255}){
   if(count==12)break;
   if(Math.max(r,Math.max(g,b))-Math.min(r,Math.min(g,b))<85)continue;int color=r<<16|g<<8|b;boolean distinct=true;
   for(int i=1;i<count;i++)if(distance(color,c[i])<120*120)distinct=false;
   if(distinct)c[count++]=color;
  }
  check(count==12,"test palette contains all supported ranks");
  return c;
 }
 private static PixelBoardReader.Pixels image(Board2048 b,int[] colors){return (x,y)->{int v=b.cells[(y/80)*4+x/80];return colors[v==0?0:Integer.numberOfTrailingZeros(v)];};}
 private static Board2048 read(Color2048Reader.Session session,Board2048 displayed,int[] colors,Board2048 before,int action,long time){return session.read(image(displayed,colors),320,320,0,0,320,320,before,action,time);}
 private static void palette(){
  int[] c=colors();var session=new Color2048Reader.Session();
  long time=1000;
  for(int rank=5;rank<=11;rank++){
   int[] values=new int[16];values[0]=values[1]=1<<(rank-1);var before=new Board2048(values);
   check(read(session,before,c,null,0,time).same(before),"previous values readable");
   int[] result=before.move(Board2048.LEFT).board.cells.clone();result[15]=2;var after=new Board2048(result);
   int old=session.learnedCount();
   check(read(session,after,c,before,0,time+100).same(after),"inferred legal merge "+rank);
   check(session.learnedCount()==old,"first frame cannot commit");
   rejects(()->session.confirm(after),"single-frame learning rejected");
   read(session,after,c,before,0,time+100);rejects(()->session.confirm(after),"duplicate timestamp cannot confirm");
   read(session,after,c,before,0,time+200);session.confirm(after);
   check(session.learnedCount()==old+1,"rank committed after two frames "+rank);
   check(read(session,after,c,null,0,time+300).same(after),"learned tile readable next turn");
   var restored=new Color2048Reader.Session();restored.restoreLearned(session.encodeLearned());
   check(restored.learnedCount()==old+1&&read(restored,after,c,null,0,time+350).same(after),"verified palette survives a new session at rank "+rank);
   time+=500;
  }
  session.reset();check(session.learnedCount()==0,"session palette reset");
  var restored=new Color2048Reader.Session();restored.restoreLearned(session.encodeLearned());check(restored.learnedCount()==0,"empty saved palette round-trips");
  rejects(()->restored.restoreLearned("v1;f56400;-;-;-;-;-;-"),"saved rank cannot overlap a base tile color");
  rejects(()->restored.restoreLearned("v1;zzzzzz;-;-;-;-;-;-"),"malformed saved color rejected");
  int[] v=new int[16];v[0]=v[1]=16;var before=new Board2048(v);int[] a=before.move(0).board.cells.clone();a[15]=2;var after=new Board2048(a);
  rejects(()->read(session,after,c,null,0,1000),"higher tile without predecessor rejected");
  int[] wrong=a.clone();wrong[14]=4;rejects(()->read(session,new Board2048(wrong),c,before,0,1100),"two spawns cannot teach");
  int[] noSpawn=a.clone();noSpawn[15]=0;rejects(()->read(session,new Board2048(noSpawn),c,before,0,1200),"no spawn cannot teach");
  int[] conflicting=c.clone();conflicting[5]=c[1];rejects(()->read(session,after,conflicting,before,0,1300),"known color cannot be renamed 32");
  PixelBoardReader.Pixels normal=image(after,c);
  rejects(()->session.read((x,y)->x<80&&y<80&&x%2==0?0xffffff:normal.rgb(x,y),320,320,0,0,320,320,before,0,1400),"animated tile samples rejected");
  check(session.learnedCount()==0,"failed frames never poison palette");
  read(session,after,c,before,0,1500);int[] shifted=c.clone();shifted[5]^=0x303030;
  try{read(session,after,shifted,before,0,1600);}catch(IllegalArgumentException ignored){}
  try{session.confirm(after);}catch(IllegalStateException expected){}
  check(session.learnedCount()==0,"unstable colour cannot confirm across frames");
  int[] dark=c.clone();dark[5]=0x1a1a1a;
  PixelBoardReader.Pixels darkBase=image(after,dark);
  PixelBoardReader.Pixels numbered=(x,y)->x<80&&y<80&&x>=27&&x<60&&y>=27&&y<60&&((x/6+y/6)%3==0)?0xffffff:darkBase.rgb(x,y);
  var darkSession=new Color2048Reader.Session();
  rejects(()->darkSession.read(numbered,320,320,0,0,320,320,null,0,2000),"dark pre-existing 32 is not guessed");
  rejects(()->darkSession.read(darkBase,320,320,0,0,320,320,before,0,2100),"plain dark obstruction cannot teach a tile");
  int[] nearBlue=dark.clone();nearBlue[5]=0x404040;
  rejects(()->read(darkSession,after,nearBlue,null,0,2150),"gray tile is never silently read as blue 16");
  rejects(()->read(darkSession,new Board2048(wrong),dark,before,0,2170),"dark tile cannot teach from an invalid transition");
  PixelBoardReader.Pixels nearBlueNumbered=(x,y)->x<80&&y<80&&x>=27&&x<60&&y>=27&&y<60&&((x/6+y/6)%3==0)?0xffffff:image(after,nearBlue).rgb(x,y);
  rejects(()->darkSession.read(nearBlueNumbered,320,320,0,0,320,320,before,0,2180),"numbered gray near blue remains unknown");
  rejects(()->darkSession.read((x,y)->x<24&&y<24&&x%2==0?0xffffff:numbered.rgb(x,y),320,320,0,0,320,320,before,0,2190),"animated dark tile rejected");
  check(darkSession.read(numbered,320,320,0,0,320,320,before,0,2200).same(after),"dark numbered merge recognized");
  rejects(()->darkSession.confirm(after),"dark merge needs two stable frames");
  rejects(()->darkSession.read(darkBase,320,320,0,0,320,320,before,0,2250),"missing glyph invalidates pending merge");
  darkSession.confirm(after);check(darkSession.learnedCount()==0,"invalid frame discards pending color");
  darkSession.read(numbered,320,320,0,0,320,320,before,0,2260);
  darkSession.read(numbered,320,320,0,0,320,320,before,0,2360);darkSession.confirm(after);
  check(darkSession.learnedCount()==1&&darkSession.read(numbered,320,320,0,0,320,320,null,0,2400).same(after),"dark merged tile learned");
  rejects(()->darkSession.read(darkBase,320,320,0,0,320,320,null,0,2450),"flat dark obstruction rejected after learning");
  var darkRestored=new Color2048Reader.Session();darkRestored.restoreLearned(darkSession.encodeLearned());
  check(darkRestored.read(numbered,320,320,0,0,320,320,null,0,2500).same(after),"dark palette restores");
  rejects(()->darkRestored.read(darkBase,320,320,0,0,320,320,null,0,2550),"flat dark obstruction rejected after restore");
  rejects(()->darkRestored.restoreLearned("v1;dddddd;-;-;-;-;-;-"),"pale background cannot be restored as a tile");
  rejects(()->darkRestored.restoreLearned("v1;404040;-;-;-;-;-;-"),"gray too close to blue cannot be restored");
  check(darkRestored.read(numbered,320,320,0,0,320,320,null,0,2600).same(after),"invalid restore does not alter prior palette");
  rejects(()->Verified2048.beforeSpawn(new Board2048(new int[]{2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}),0),"no-op cannot infer new colors");
 }
 private static void pacing(){
  var p=new CapturePacer();check(p.accept(0,true),"first frame at zero accepted");
  check(!p.accept(32_999_999,true),"less than 33ms skipped");check(p.accept(33_000_000,true),"33ms accepted");
  check(!p.accept(66_000_000,false),"busy worker skips before decode");check(p.accept(66_000_000,true),"busy skip does not consume next slot");
  check(!p.accept(65_000_000,true),"backwards clock skipped");check(!p.accept(-1,true),"negative clock rejected");
  check(p.acceptedFrames()==3&&p.skippedFrames()==4,"capture counters exact");
  for(int hz:new int[]{60,90,120}){p=new CapturePacer();for(int i=0;i<hz;i++)p.accept(i*1_000_000_000L/hz,true);check(p.acceptedFrames()<=31&&p.skippedFrames()>0,"high refresh decode bounded "+hz);}
 }
 private static JumperState scene(long time,float x,float shift,boolean inconsistent){
  var player=new JumperState.ObjectBox(JumperState.Kind.PLAYER,x-10,490,x+10,530,.9f);var platforms=new ArrayList<JumperState.ObjectBox>();
  int i=0;for(int y:new int[]{120,300,450,700,1000}){float dy=inconsistent?(i%2==0?shift:0):shift;platforms.add(new JumperState.ObjectBox(JumperState.Kind.PLATFORM,40+i*60,y+dy,140+i*60,y+15+dy,.9f));i++;}
  return new JumperState(854,1280,0,0,854,1280,time,player,platforms,List.of(),.9f,0,0,0);
 }
 private static void scroll(){
  var f=new JumperFeedback();long id=f.dispatched(JumperPolicy.Action.TAP_LEFT,scene(1000,20,0,false),1000);
  check(f.observe(scene(1100,20,20,false),1100)==JumperFeedback.State.WAITING,"scroll without ACK never releases");f.acknowledge(id);
  check(f.observe(scene(1200,20,30,false),1200)==JumperFeedback.State.WAITING,"one scroll frame insufficient");
  check(f.observe(scene(1300,20,45,false),1300)==JumperFeedback.State.SCENE_PROGRESS,"coherent edge scroll releases wait");
  check(f.report().contains("verified=0")&&f.report().contains("scene_progress=1")&&!f.failedWithAck(),"world movement never rewards steering");
  check(!f.acknowledge(id),"scroll completion invalidates old token");
  for(boolean noise:new boolean[]{true,false}){
   f.reset();float x=noise?20:400;id=f.dispatched(JumperPolicy.Action.TAP_LEFT,scene(2000,x,0,false),2000);f.acknowledge(id);
   check(f.observe(scene(2100,x,20,noise),2100)==JumperFeedback.State.WAITING,"false scroll/away from edge waits");
   check(f.observe(scene(2200,x,40,noise),2200)==JumperFeedback.State.WAITING,"false scroll cannot release on second frame");
   check(f.observe(null,2650)==JumperFeedback.State.RETRY,"false scroll still times out");
  }
  f.reset();id=f.dispatched(JumperPolicy.Action.TAP_LEFT,scene(3000,20,0,false),3000);f.acknowledge(id);
  check(f.observe(scene(3100,20,20,false),3500)==JumperFeedback.State.WAITING,"stale scroll excluded");
  check(f.observe(scene(3500,20,40,false),3400)==JumperFeedback.State.WAITING,"future scroll excluded");
  check(f.observe(null,3650)==JumperFeedback.State.RETRY,"missing valid scroll cannot hang wait");
  // ArcadeFeedback road movement must not interpret generic vertical scene motion as steering.
  var player=new ArcadeObservation.Box(.45f,.7f,.55f,.8f);
  var before=new ArcadeObservation(ArcadeProfile.TRAFFIC,4000,player,player,List.of(),0,0,1,1,0,1,0,0,.9f,true,false);
  var after=new ArcadeObservation(ArcadeProfile.TRAFFIC,4100,player,player,List.of(new ArcadeObservation.Box(.1f,.2f,.2f,.3f)),0,0,1,1,0,1,0,.4f,.9f,true,false);
  var feedback=new ArcadeFeedback();long token=feedback.dispatched(new ArcadePolicy.Decision(ArcadePolicy.Action.LEFT,.2f,.7f,"test"),before,4000);feedback.acknowledge(token);
  check(feedback.observe(after,4100)==ArcadeFeedback.State.WAITING&&feedback.verified()==0,"traffic scroll is not left response");
 }
 private static GridPuzzleVision.Pixels gridImage(int size,int[] types,boolean special,boolean bad){
  int[] colors={0xe72832,0xfbc72b,0x4aa91f,0x266ac6};return (x,y)->{
   int col=x/40,row=y/40,index=row*size+col;float nx=x%40/40f,ny=y%40/40f;
   if(nx<.12f||nx>.88f||ny<.12f||ny>.88f)return 0xdddddd;
   if(special&&index==size*size-1){if(nx>=.34f&&nx<=.66f&&ny>=.34f&&ny<=.66f)return 0xffffff;if(bad&&x%3==0)return 0x252525;}
   return colors[types[index]];
  };
 }
 private static void grid(){
  for(int size:new int[]{6,7,10}){
   int[] types=new int[size*size];for(int i=0;i<types.length;i++)types[i]=(i+i/size)%4;
   var plain=GridPuzzleVision.detect(gridImage(size,types,false,false),0,0,size*40,size*40,size,size,0,0,0,0,1000,null);
   check(plain.usable()&&plain.specialCellsMask().isEmpty(),"ordinary grid has no special cells");
   var special=GridPuzzleVision.detect(gridImage(size,types,true,false),0,0,size*40,size*40,size,size,0,0,0,0,1100,plain.palette());
   check(special.usable()&&special.specialCellsMask().get(size*size-1),"contrasting centre recognized at last index "+size);
   check(Arrays.equals(plain.cells(),special.cells()),"special inherits known ring type");
   var exported=special.specialCellsMask();exported.clear();check(!special.specialCellsMask().isEmpty(),"mask immutable through accessor");
   var bad=GridPuzzleVision.detect(gridImage(size,types,true,true),0,0,size*40,size*40,size,size,0,0,0,0,1200,plain.palette());check(!bad.usable(),"invalid ring rejected");
   var session=new GridPuzzleSession();check(session.observe(size,size,special.cells(),special.specialCellsMask(),1000,1000)==GridPuzzleSession.Status.WAIT_STABLE,"first special sample waits");
   check(session.observe(size,size,special.cells(),new BitSet(),1100,1100)==GridPuzzleSession.Status.WAIT_STABLE,"animated special bit resets stability");
  }
  int[] c={1,1,2,3,4,2,3,4,3};var b=new GridPuzzle(3,3,c);var mask=new BitSet();mask.set(5);
  var chosen=GridPuzzlePolicy.choose(b,GridPuzzlePolicy.Mode.TAP_GROUP,2,0,0,mask);
  check(chosen!=null&&chosen.specialInvolved==0&&!contains(chosen.clearedCells(),5),"tap selection avoids marked skull/special cells");
  b=new GridPuzzle(3,3,new int[]{1,2,1,2,1,3,4,5,6});mask.clear();mask.set(0);
  chosen=GridPuzzlePolicy.choose(b,GridPuzzlePolicy.Mode.SWAP_MATCH3,2,0,0,mask);
  check(chosen==null,"match-3 abstains when its only legal line includes a marked skull/special cell");
 }
 private static boolean contains(int[] values,int target){for(int value:values)if(value==target)return true;return false;}
}
