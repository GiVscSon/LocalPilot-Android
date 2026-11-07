package org.localpilot.android;

import java.util.Arrays;

public final class HexaTest {
 private static int checks;
 private static final int SKY=0xff202834,CENTER=0xffd0d4dc,RING=0xff46505d;
 private static final int[] COLORS={0xffe52c32,0xff4ab63b,0xfff0cf20,0xff385adf};
 private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
 private static void invalid(Runnable operation){try{operation.run();throw new AssertionError("invalid operation accepted");}catch(IllegalArgumentException expected){checks++;}}
 private static void illegal(Runnable operation){try{operation.run();throw new AssertionError("illegal transition accepted");}catch(IllegalStateException expected){checks++;}}
 private static HexaConfig config(){return new HexaConfig(4,6,60,60,660,580);}
 private static int[] empty(HexaConfig c){int[] values=new int[c.size()];for(int i=0;i<values.length;i++)values[i]=c.active(i)?0:HexaBoard.BLOCKED;return values;}
 private static final class Frame implements HexaVision.Pixels {
  final int width=720,height=1000;final int[] pixels=new int[width*height];
  Frame(HexaConfig config,int[] cells,int[] hands){Arrays.fill(pixels,SKY);float r=Math.min(config.cellWidth(),config.cellHeight())*.41f;
   for(int i=0;i<config.size();i++)if(config.active(i))draw(config.cellCenterX(i/config.cols,i%config.cols),config.cellCenterY(i/config.cols,i%config.cols),r,cells[i]);
   for(int i=0;i<3;i++)draw(config.slotX(i),config.slotY(i),r,hands[i]);
  }
  void draw(float cx,float cy,float radius,int value){
   for(int y=Math.max(0,(int)(cy-radius-3));y<=Math.min(height-1,(int)(cy+radius+3));y++)for(int x=Math.max(0,(int)(cx-radius-3));x<=Math.min(width-1,(int)(cx+radius+3));x++){
    float d=(float)Math.hypot(x-cx,y-cy);
    if(value==0&&d<=radius+3)pixels[y*width+x]=d>=radius-3?RING:CENTER;
    else if(value>0&&d<=radius*.95f)pixels[y*width+x]=COLORS[value-1];
   }
  }
  public int get(int x,int y){if(x<0||x>=width||y<0||y>=height)throw new AssertionError("vision read outside frame");return pixels[y*width+x];}
 }
 private static void geometry(){
  HexaConfig c=new HexaConfig(4,4,0,0,400,400);int a=1*4+1,count=0;
  for(int i=0;i<c.size();i++){if(c.neighbors(a,i))count++;check(c.neighbors(a,i)==c.neighbors(i,a),"six-way adjacency is symmetric");}
  check(count==6,"interior odd hex cell has six neighbors");
  check(c.cellCenterX(0,1)==150&&c.cellCenterX(1,1)==200,"odd row shifts by exactly half a cell");
  check(!c.active(7)&&!c.active(15)&&c.active(14),"short odd rows exclude their phantom last column");
  check(c.slotX(0)==80&&c.slotX(1)==200&&c.slotX(2)==320&&c.slotY(0)==448,"hand sampling positions follow exact ROI fractions");
  check(HexaConfig.decode(c.encode()).encode().equals(c.encode()),"geometry preserves mask and coordinates");
  invalid(()->new HexaConfig(9,8,0,0,800,900));invalid(()->new HexaConfig(4,4,0,0,400,400,1L<<7));invalid(()->HexaConfig.decode("hexa-v1,4,4,0,0,400,400,0"));
  HexaConfig high=new HexaConfig(8,8,0,0,800,800,(1L<<61)|(1L<<62));int[] values=empty(high);values[61]=1;
  HexaPolicy.Move move=HexaPolicy.choose(new HexaBoard(high,values,new int[]{1,0,0}),false);
  check(move!=null&&move.targetIndex(8)==62,"primitive masks preserve the highest playable cells");
 }
 private static void policy(){
  HexaConfig c=config();int[] values=empty(c);values[0]=1;HexaBoard b=new HexaBoard(c,values,new int[]{1,2,3});
  HexaPolicy.Move normal=HexaPolicy.choose(b,false);check(normal!=null&&normal.color==1&&normal.matchingNeighbors>0,"normal placement joins a same-colour neighbor");
  check(normal.durationMs==420&&normal.fromX==c.slotX(normal.handSlot)&&normal.toX==c.cellCenterX(normal.targetRow,normal.targetCol),"native drag uses hand and shifted target geometry");
  HexaPolicy.Move quest=HexaPolicy.choose(b,true);check(quest!=null&&quest.matchingNeighbors==0,"fill quest deliberately avoids merges");
  values[1]=HexaBoard.UNKNOWN;check(HexaPolicy.choose(new HexaBoard(c,values,new int[]{1,2,3}),false)==null,"unknown board is not promoted to an empty target");
  values=empty(c);check(HexaPolicy.choose(new HexaBoard(c,values,new int[]{1,2,3}),false)==null,"normal mode waits when no matching stack exists");
  check(HexaPolicy.choose(new HexaBoard(c,values,new int[]{0,HexaBoard.UNKNOWN,0}),true)==null,"empty and unknown hands cannot be dragged");
  Arrays.fill(values,1);for(int i=0;i<values.length;i++)if(!c.active(i))values[i]=HexaBoard.BLOCKED;
  check(HexaPolicy.choose(new HexaBoard(c,values,new int[]{1,2,3}),true)==null,"full board does not invent a destination");
 }
 private static void vision(){
  HexaConfig c=config();int[] values=empty(c);values[0]=1;values[2]=2;Frame frame=new Frame(c,values,new int[]{1,2,3});
  HexaVision.Calibration calibration=HexaVision.calibrate(frame,c,0,1);check(calibration.colors()==3,"setup learns visible chromatic prototypes");
  check(HexaVision.Calibration.decode(calibration.encode()).encode().equals(calibration.encode()),"calibration roundtrips independently of Android");
  HexaVision.Reader reader=new HexaVision.Reader(c,calibration);HexaVision.Observation read=reader.read(frame,frame.width,frame.height,1000);
  check(read.usable()&&read.confidence()==1f&&read.board().occupied()==2,"calibrated fixture recognises every board cell");
  check(read.board().colorAt(0)==read.board().handColor(0)&&read.board().colorAt(2)==read.board().handColor(1),"palette IDs remain consistent between board and hand");
  check(read.board().colorAt(1)==0&&read.board().colorAt(11)==HexaBoard.BLOCKED,"proven empty and absent slots remain distinct");
  check(HexaVision.calibrateFromScene(frame,c,frame.width,frame.height)!=null,"two agreeing outlined holes permit conservative initial calibration");
  check(!reader.read(frame,frame.width,frame.height,1100,100,600,400,700).usable(),"overlay over the hand blocks every drag");
  check(!reader.read(frame,frame.width,620,1200).usable(),"ROI without all hand pixels is rejected before sampling");
  // A board-coloured flat patch has no closed empty outline and remains unknown.
  for(int y=60;y<190;y++)for(int x=160;x<260;x++)frame.pixels[y*frame.width+x]=CENTER;
  check(!reader.read(frame,frame.width,frame.height,1300).usable()&&reader.read(frame,frame.width,frame.height,1300).board().colorAt(1)==HexaBoard.UNKNOWN,"flat scenery is not an empty cell");
  Frame shade=new Frame(c,values,new int[]{1,2,3});for(int i=0;i<shade.pixels.length;i++){int rgb=shade.pixels[i],r=(rgb>>16&255)*3/4,g=(rgb>>8&255)*3/4,b=(rgb&255)*3/4;shade.pixels[i]=0xff000000|r<<16|g<<8|b;}
  check(reader.read(shade,shade.width,shade.height,1400).usable(),"calibrated chromaticity tolerates a uniform brightness reduction");
  invalid(()->new HexaVision.Calibration(CENTER,RING,new int[]{COLORS[0],COLORS[0]}));invalid(()->new HexaVision.Calibration(CENTER,CENTER,COLORS));
 }
 private static void session(){
  HexaConfig c=config();int[] values=empty(c);values[0]=1;HexaBoard before=new HexaBoard(c,values,new int[]{1,2,3});HexaPolicy.Move move=HexaPolicy.choose(before,false);int target=move.targetIndex(c.cols);
  int[] afterValues=values.clone();afterValues[target]=1;HexaBoard after=new HexaBoard(c,afterValues,new int[]{2,2,3});
  HexaSession s=new HexaSession();check(s.observe(before,1000,1000)==HexaSession.Status.SEARCHING&&s.observe(before,1200,1200)==HexaSession.Status.READY,"two stable fresh source frames authorize a plan");
  s.sent(before,move,1200);s.observe(after,1800,1800);s.observe(after,2000,2000);check(s.verified()==0,"visual changes without Android ACK do not confirm a drag");
  s.acknowledge(2001);s.observe(after,2300,2300);check(s.observe(after,2500,2500)==HexaSession.Status.VERIFIED&&s.verified()==1,"two fresh post-ACK target/hand observations confirm placement");
  HexaPolicy.Move next=HexaPolicy.choose(after,true);s.sent(after,next,2500);s.cancelUnsent();check(s.verified()==1&&!s.pending(),"unsent next plan preserves verified history");
  check(s.observe(after,2500,2500)==HexaSession.Status.SEARCHING,"previously consumed timestamp cannot authorize another plan");s.observe(after,2700,2700);check(s.observe(after,2900,2900)==HexaSession.Status.READY,"cancelled preparation requires two new frames");
  next=HexaPolicy.choose(after,true);s.sent(after,next,2900);s.acknowledge(3100);illegal(s::cancelUnsent);s.observe(after,3500,3500);s.observe(after,3700,3700);check(s.verified()==1&&s.reason()==HexaSession.Reason.EXPECTED_CHANGE_MISSING,"ACK and unchanged pixels do not verify");
  check(s.observe(after,8900,8900)==HexaSession.Status.PAUSED&&s.pending(),"absolute dispatch deadline pauses without learning");
  s.reset();s.observe(before,1000,1000);s.observe(before,1200,1200);s.sent(before,move,1200);s.acknowledge(1400);s.invalidateCandidate();s.observe(after,1800,1800);s.observe(after,2000,2000);check(s.status()==HexaSession.Status.PAUSED&&s.verified()==0,"context loss revokes automatic resumption");
  // The vision's reused object must not overwrite a session's prior frame snapshot.
  s.reset();HexaBoard reused=new HexaBoard(c,values,new int[]{1,2,3});s.observe(reused,1000,1000);reused.cell(1,2);check(s.observe(reused,1200,1200)==HexaSession.Status.SEARCHING,"reused mutable observation does not fabricate two equal frames");
  s.reset();s.observe(before,1000,1000);s.observe(before,1200,1200);s.sent(before,move,1200);s.acknowledge(1400);
  HexaBoard sameRefill=new HexaBoard(c,afterValues,new int[]{1,2,3});s.observe(sameRefill,1800,1800);check(s.observe(sameRefill,2000,2000)==HexaSession.Status.VERIFIED,"a known matching target proves placement even when refill has the same colour");
  s.reset();s.observe(before,1000,1000);s.observe(before,1200,1200);s.sent(before,move,1200);s.acknowledge(1400);int[] unrelated=values.clone();unrelated[3]=2;HexaBoard wrong=new HexaBoard(c,unrelated,new int[]{2,2,3});s.observe(wrong,1800,1800);s.observe(wrong,2000,2000);check(s.verified()==0,"hand change and an unrelated board animation cannot confirm the chosen target");
  s.reset();s.observe(before,1000,1000);s.observe(before,1200,1200);s.sent(before,move,1200);s.acknowledge(1400);int[] merge=values.clone();merge[0]=2;HexaBoard merged=new HexaBoard(c,merge,new int[]{2,2,3});s.observe(merged,1800,1800);check(s.observe(merged,2000,2000)==HexaSession.Status.VERIFIED,"immediate same-colour merge needs local topology change and refreshed hand");
 }
 private static void allocations(){
  var plain=java.lang.management.ManagementFactory.getThreadMXBean();if(!(plain instanceof com.sun.management.ThreadMXBean memory)||!memory.isThreadAllocatedMemorySupported()){System.out.println("Hexa allocation counter unavailable");return;}
  memory.setThreadAllocatedMemoryEnabled(true);HexaConfig c=config();int[] values=empty(c);values[0]=1;Frame frame=new Frame(c,values,new int[]{1,2,3});HexaVision.Reader reader=new HexaVision.Reader(c,HexaVision.calibrate(frame,c,0,1));HexaSession session=new HexaSession();
  for(int i=0;i<3000;i++)session.observe(reader.read(frame,frame.width,frame.height,i).board(),i,i);
  long thread=Thread.currentThread().getId();memory.getThreadAllocatedBytes(thread);long start=memory.getThreadAllocatedBytes(thread);
  for(int i=3000;i<3500;i++)session.observe(reader.read(frame,frame.width,frame.height,i).board(),i,i);
  long bytes=memory.getThreadAllocatedBytes(thread)-start;check(bytes==0,"warm Hexa read/observe allocation must be zero, found "+bytes);System.out.println("Hexa hot path: "+bytes+" bytes across 500 frames");
 }
 public static void main(String[] args){geometry();policy();vision();session();allocations();System.out.println("Hexa: "+checks+" assertions PASS; synthetic calibrated scenes, physical game not claimed");}
}
