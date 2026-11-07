package org.localpilot.android;

import java.util.Arrays;

/** Synthetic photometry, temporal authority and allocation tests, not real-game acceptance. */
public final class AdaptiveGridReaderTest {
 private static int checks;
 private static void check(boolean ok,String text){checks++;if(!ok)throw new AssertionError(text);}
 private static final class Image implements GridPuzzleVision.Pixels {
  final int rows=6,cols=6,cell=48,left=13,top=17;
  final int[] tokens=new int[36];
  int[] colors={0xe72832,0xfbc72b,0x4aa91f,0x266ac6};
  float gain=1,offset;boolean localShadows,modal;int absent=-1;
  Image(){for(int i=0;i<tokens.length;i++)tokens[i]=i%4;}
  @Override public int get(int x,int y){
   if(x<left||y<top||x>=left+cols*cell||y>=top+rows*cell)throw new AssertionError("sample outside board ROI");
   int col=(x-left)/cell,row=(y-top)/cell,index=row*cols+col;
   float nx=(x-left-col*cell)/(float)cell,ny=(y-top-row*cell)/(float)cell;
   int rgb=modal?0xe0e0e0:index==absent?0x505050:
       nx<.12f||nx>.88f||ny<.12f||ny>.88f?0xdddddd:colors[tokens[index]];
   float exposure=gain*(localShadows&&(row&1)==1?.82f:1);
   int out=0;
   for(int c=0;c<3;c++){
    int v=rgb>>(16-c*8)&255;
    if(!modal&&index!=absent&&nx>.57f&&ny>.57f)v=Math.max(0,v-12);
    out|=Math.max(0,Math.min(255,Math.round(v*exposure+offset*255)))<<(16-c*8);
   }
   return out;
  }
  GridPuzzleVision.Palette palette(){return GridPuzzleVision.detect(this,left,top,left+cols*cell,top+rows*cell,rows,cols,0,0,0,0,1,null).palette();}
 }
 private static AdaptiveGridReader.Result read(AdaptiveGridReader reader,Image pixels,GridPuzzleVision.Palette palette,long frame,boolean pending){
  return reader.read(pixels,pixels.left,pixels.top,pixels.left+pixels.cols*pixels.cell,pixels.top+pixels.rows*pixels.cell,
      pixels.rows,pixels.cols,0,0,0,0,frame,frame,7,palette,false,false,pending);
 }
 private static void photometryAndNamespace(){
  Image pixels=new Image();GridPuzzleVision.Palette palette=pixels.palette();String saved=palette.encode();
  AdaptiveGridReader reader=new AdaptiveGridReader();int[] ids=new int[36];
  var first=read(reader,pixels,palette,1000,false);check(first.usable()&&!first.stable(),"first calibrated frame cannot authorize a stable decision");first.copyCells(ids);
  check(read(reader,pixels,palette,1200,false).status()==AdaptiveGridReader.Status.READY,"two independently fresh baseline frames stabilize");
  pixels.gain=.72f;pixels.offset=.02f;
  var shifted=read(reader,pixels,palette,1400,false);int[] actual=new int[36];shifted.copyCells(actual);
  check(shifted.usable()&&shifted.stable()&&shifted.usedPhotometricMatch(),"bounded exposure shift stays identifiable without a manual reset");
  check(Arrays.equals(ids,actual),"exposure does not renumber calibrated classes");
  check(reader.adaptationCount()>0,"stable, confident appearance updates are learned inside the session");
  pixels.gain=.91f;pixels.offset=0;pixels.localShadows=true;
  var shadowed=read(reader,pixels,palette,1600,false);shadowed.copyCells(actual);
  check(shadowed.usable()&&Arrays.equals(ids,actual),"spatially varying shadow preserves known identities");
  check(saved.equals(shadowed.snapshot().palette().encode()),"session adaptation never rewrites or extends the saved palette namespace");
  pixels.localShadows=false;pixels.gain=1;int temporary=pixels.tokens[0];pixels.tokens[0]=pixels.tokens[1];pixels.tokens[1]=temporary;
  int changes=reader.adaptationCount();
  read(reader,pixels,palette,1800,true);var changed=read(reader,pixels,palette,2000,true);changed.copyCells(actual);
  check(changed.stable()&&actual[0]==ids[1]&&actual[1]==ids[0],"a real token transition remains visible under frozen semantic IDs");
  check(reader.adaptationCount()==changes,"pending gestures freeze appearance learning");
 }
 private static void memoryAndInvalidation(){
  Image pixels=new Image();GridPuzzleVision.Palette palette=pixels.palette();AdaptiveGridReader reader=new AdaptiveGridReader();
  read(reader,pixels,palette,1000,false);int known=read(reader,pixels,palette,1200,false).type(0,0);
  pixels.absent=0;read(reader,pixels,palette,1400,false);var missing=read(reader,pixels,palette,1600,false);
  check(missing.type(0,0)==-1&&missing.unknownCount()==1&&missing.status()==AdaptiveGridReader.Status.PARTIAL_READY,"temporarily missing current object remains UNKNOWN on a partial board");
  check(reader.rememberedType(0,0,1600)==known&&missing.snapshot().type(0,0)==-1,"diagnostic motion memory never fills the current symbolic board");
  check(reader.rememberedType(0,0,2201)==-1,"object memory has a hard one-second expiry");
  long now=1800;var covered=reader.read(pixels,pixels.left,pixels.top,pixels.left+288,pixels.top+288,6,6,
      pixels.left+48,pixels.top,pixels.left+96,pixels.top+48,now,now,7,palette,false,false,false);
  check(!covered.usable()&&!covered.partialUsable()&&!covered.stable()&&covered.reason(0,1)==GridPuzzleVision.CellReason.EXCLUDED_OVERLAY,"even a single overlay-covered cell refuses the whole actionable scene");
  pixels.absent=-1;check(!read(reader,pixels,palette,2000,false).stable(),"clearing an overlay requires stabilization again");
  check(read(reader,pixels,palette,2200,false).stable(),"fresh unobstructed observations resume without a manual profile restart");
  check(!read(reader,pixels,palette,2200,false).stable(),"replaying an old capture cannot retain stable authority");
  check(!read(reader,pixels,palette,2400,false).stable()&&read(reader,pixels,palette,2600,false).stable(),"replay rejection requires two replacement frames");
  var stale=reader.read(pixels,pixels.left,pixels.top,pixels.left+288,pixels.top+288,6,6,0,0,0,0,2700,3101,7,palette,false,false,false);
  check(!stale.usable()&&!stale.stable(),"late delivery supplies no readable or stable board authority");
  var rebound=reader.read(pixels,pixels.left,pixels.top,pixels.left+288,pixels.top+288,6,6,0,0,0,0,3200,3200,8,palette,false,false,false);
  check(!rebound.stable()&&reader.adaptationCount()==0,"context changes reset stabilization and appearance estimates");
  reader.reset();check(reader.rememberedType(0,0,3200)==-1,"explicit reset discards historical objects");
 }
 private static void negativeScenes(){
  Image pixels=new Image();GridPuzzleVision.Palette palette=pixels.palette();AdaptiveGridReader reader=new AdaptiveGridReader();
  pixels.modal=true;
  check(!read(reader,pixels,palette,1000,false).usable()&&!read(reader,pixels,palette,1200,false).stable(),"stable white modal is not a calibrated game board");
  pixels.modal=false;int original=pixels.colors[0];pixels.colors[0]=0xa040c0;
  var foreign=read(reader,pixels,palette,1400,false);
  check(foreign.type(0,0)==-1,"foreign chromatic appearance is not guessed from the closest known color");pixels.colors[0]=original;
  Image sameHue=new Image();sameHue.colors=new int[]{0x800000,0xff0000,0x00cc00};
  for(int i=0;i<sameHue.tokens.length;i++)sameHue.tokens[i]=i%3;
  GridPuzzleVision.Palette ambiguousPalette=sameHue.palette();check(ambiguousPalette.size()==3,"negative fixture contains two calibrated red brightness identities");
  sameHue.colors=new int[]{0xb90000,0xb90000,0x00cc00};
  var ambiguous=read(new AdaptiveGridReader(),sameHue,ambiguousPalette,2000,false);
  check(ambiguous.type(0,0)==-1&&ambiguous.reason(0,0)==GridPuzzleVision.CellReason.COLOR_AMBIGUOUS,"photometric tie between semantic classes abstains rather than inventing an identity");
  check(!ambiguous.usable()&&!ambiguous.partialUsable(),"a palette collision cannot authorize a scene");
  var uncalibrated=read(new AdaptiveGridReader(),pixels,null,2400,false);
  check(uncalibrated.status()==AdaptiveGridReader.Status.NEEDS_PALETTE&&!uncalibrated.usable(),"adaptation requires explicit semantic calibration");
 }
 private static void allocation(){
  var bean=java.lang.management.ManagementFactory.getThreadMXBean();
  if(!(bean instanceof com.sun.management.ThreadMXBean memory)||!memory.isThreadAllocatedMemorySupported())return;
  memory.setThreadAllocatedMemoryEnabled(true);Image pixels=new Image();var palette=pixels.palette();var reader=new AdaptiveGridReader();
  for(int i=0;i<400;i++)read(reader,pixels,palette,1000+i*120L,true);
  long thread=Thread.currentThread().getId(),bytes=Long.MAX_VALUE;int ready=0;
  // A VM can initialize its allocation counter or compiled entry point once.
  // A true per-read allocation would affect every window and fail all three.
  for(int window=0;window<3;window++){
   long before=memory.getThreadAllocatedBytes(thread);int accepted=0;
   for(int i=0;i<300;i++)if(read(reader,pixels,palette,100000+(window*300L+i)*120L,true).stable())accepted++;
   bytes=Math.min(bytes,memory.getThreadAllocatedBytes(thread)-before);ready+=accepted;
  }
  check(ready==900&&bytes==0,"calibrated read reuses primitive storage: allocated="+bytes+" ready="+ready);
 }
 public static void main(String[] args){photometryAndNamespace();memoryAndInvalidation();negativeScenes();allocation();System.out.println("Adaptive grid reader: "+checks+" assertions PASS; synthetic exposure/ambiguity/context/allocation");}
}
