package org.localpilot.android;

public final class GridPuzzleVisionTest {
 private static int checks;
 private static void check(boolean condition,String message){checks++;if(!condition)throw new AssertionError(message);}
 private static int token(int x,int y,int left,int top,int size,int rows,int cols,int[] types,int[] colors,int light){
  if(x<left||y<top||x>=left+size*cols||y>=top+size*rows)return 0x222222;
  int col=(x-left)/size,row=(y-top)/size;float nx=(x-left-col*size)/(float)size,ny=(y-top-row*size)/(float)size;
  if(types[row*cols+col]<0)return 0x505050;
  int color=colors[types[row*cols+col]];
  if(nx<.12f||nx>.88f||ny<.12f||ny>.88f)return 0xdddddd;
  if(nx>.57f&&ny>.57f)light-=12;
  int r=Math.min(255,Math.max(0,(color>>16&255)+light)),g=Math.min(255,Math.max(0,(color>>8&255)+light)),b=Math.min(255,Math.max(0,(color&255)+light));return r<<16|g<<8|b;
 }
 private static int skullTile(int x,int y,boolean eyes,boolean redFrame){
  float nx=x%80/80f,ny=y%80/80f;
  if(nx<.12f||nx>.88f||ny<.12f||ny>.88f)return 0x707070;
  int rgb=redFrame?0x9a4045:0x8d8d8d;
  if(nx>=.25f&&nx<=.70f&&ny>=.20f&&ny<=.65f)rgb=0xbbbaad;
  if(eyes&&ny>=.33f&&ny<=.48f&&(nx>=.29f&&nx<=.38f||nx>=.49f&&nx<=.59f))rgb=0x323232;
  if(nx>=.15f&&nx<=.32f&&ny>=.52f&&ny<=.75f||nx>=.62f&&nx<=.79f&&ny>=.64f&&ny<=.84f)rgb=0xbbbaad;
  return rgb;
 }
 public static void main(String[] args){
  int[][] palettes={{0xe72832,0xfbc72b,0x4aa91f,0x266ac6},{0x993599,0xe28026,0x29b5ae,0x293758}};
  for(int rows:new int[]{3,6,7,10})for(int size:new int[]{16,37,80})for(int[] colors:palettes){
   int cols=rows==10?8:rows,left=23,top=17;int[] types=new int[rows*cols];for(int i=0;i<types.length;i++)types[i]=(i*7+i/cols)%4;
   var first=GridPuzzleVision.detect((x,y)->token(x,y,left,top,size,rows,cols,types,colors,0),left,top,left+size*cols,top+size*rows,rows,cols,0,0,0,0,1000,null);
   check(first.usable()&&first.complete(),"grid recognized "+rows+" at "+size);
   check(first.palette().size()==4,"four symbol colors");
   int[] mapping=new int[4];for(int i=0;i<types.length;i++){int id=first.cells()[i];if(mapping[types[i]]==0)mapping[types[i]]=id;check(mapping[types[i]]==id,"same token type clusters consistently");}
   var restored=GridPuzzleVision.Palette.decode(first.palette().encode());check(restored.encode().equals(first.palette().encode()),"palette persistence");
   int tmp=types[0];types[0]=types[1];types[1]=tmp;
   var changed=GridPuzzleVision.detect((x,y)->token(x,y,left,top,size,rows,cols,types,colors,8),left,top,left+size*cols,top+size*rows,rows,cols,0,0,0,0,1100,restored);
   check(changed.usable()&&changed.palette().size()==4,"small lighting change and rearrangement preserve palette");
   check(changed.type(0,0)==mapping[types[0]],"type identity survives movement");
   check(changed.signature()!=first.signature(),"symbolic change changes signature");
   var duplicate=GridPuzzleVision.detect((x,y)->token(x,y,left,top,size,rows,cols,types,colors,8),left,top,left+size*cols,top+size*rows,rows,cols,0,0,0,0,1500,restored);
   check(duplicate.signature()==changed.signature(),"timestamp does not affect signature");
   int[] copy=changed.cells();copy[0]=999;check(changed.type(0,0)!=999,"observation cells are immutable");
   var masked=GridPuzzleVision.detect((x,y)->token(x,y,left,top,size,rows,cols,types,colors,0),left,top,left+size*cols,top+size*rows,rows,cols,left+size,top+size,left+size*2,top+size*2,1600,restored);
   check(!masked.usable()&&!masked.complete()&&masked.type(1,1)==-1,"panel occlusion is unknown, never a token");
   check(masked.reason(1,1)==GridPuzzleVision.CellReason.EXCLUDED_OVERLAY,"excluded cell reason preserves panel overlap");
   check(java.util.Arrays.stream(masked.unknownIndices()).anyMatch(i->i==cols+1),"unknown indices identify the excluded cell");
   check(masked.unknownSummary(3).contains("R2C2:EXCLUDED_OVERLAY"),"bounded per-cell status is readable");
   types[0]=-1;var missing=GridPuzzleVision.detect((x,y)->token(x,y,left,top,size,rows,cols,types,colors,0),left,top,left+size*cols,top+size*rows,rows,cols,0,0,0,0,1700,restored);
   check(!missing.usable()&&missing.type(0,0)==-1,"blank cell is uncertain, no fabricated empty tile");
   check(missing.reason(0,0)!=GridPuzzleVision.CellReason.KNOWN,"missing symbol may not claim a known classification");
  }
  for(int color:new int[]{0,0xffffff,0x209030,0x808080}){var blank=GridPuzzleVision.detect((x,y)->color,0,0,420,420,7,7,0,0,0,0,1000,null);check(!blank.usable()&&blank.palette().size()==0,"uniform scene abstains");}
  var gradient=GridPuzzleVision.detect((x,y)->{int v=70+x/10+y/10;return v<<16|v<<8|v;},0,0,420,420,7,7,0,0,0,0,1000,null);check(!gradient.usable(),"smooth background abstains");
  int[] sixBySix=new int[36];for(int i=0;i<sixBySix.length;i++)sixBySix[i]=i%4;
  int[] specials={0xd02020,0x20a020,0x3060e0,0xd0d0d0};
  var neutral=GridPuzzleVision.detect((x,y)->token(x,y,0,0,40,6,6,sixBySix,specials,0),0,0,240,240,6,6,0,0,0,0,1000,null);
  check(!neutral.usable()&&neutral.type(0,3)==-1,"neutral/special icons need separate rules and abstain");
  int[] vivid={0xff0000,0x00ff00,0x0000ff,0xffff00};
  var fullScale=GridPuzzleVision.detect((x,y)->token(x,y,0,0,40,6,6,sixBySix,vivid,0),0,0,240,240,6,6,0,0,0,0,1000,null);
  check(fullScale.usable()&&GridPuzzleVision.Palette.decode(fullScale.palette().encode()).size()==4,"full-scale RGB palette remains serializable despite float rounding");
  var texturedMenu=GridPuzzleVision.detect((x,y)->(((x/5+y/5)&1)==0?0x00ff00:0x00bf00),0,0,240,240,6,6,0,0,0,0,1100,fullScale.palette());
  check(texturedMenu.complete(),"negative textured menu really matches a known token rather than failing only contrast");
  check(!texturedMenu.usable()&&texturedMenu.confidence==0,"saved palette never turns a monochrome textured menu into a usable board");
  int[] monochrome=new int[36];java.util.Arrays.fill(monochrome,1);
  var singleColor=GridPuzzleVision.detect((x,y)->token(x,y,0,0,40,6,6,monochrome,vivid,0),0,0,240,240,6,6,0,0,0,0,1200,fullScale.palette());
  check(singleColor.complete()&&!singleColor.usable(),"even a real-looking monochrome grid abstains with a saved palette");
  // Rounded foreground objects leave substantial neutral background inside the cell.
  for(int center:new int[]{0xe72832,0x266ac6,0x808080}){
   var rounded=GridPuzzleVision.detect((x,y)->{
    int col=x/80,row=y/80; double dx=x%80-40,dy=y%80-40;
    if(dx*dx+dy*dy>27*27)return 0xb0b0b0;
    return ((row+col)%2==0)?center:0x4aa91f;
   },0,0,480,480,6,6,0,0,0,0,1300,null);
   check(center==0x808080?!rounded.usable():rounded.usable(),
     "rounded chromatic foreground accepted; neutral foreground still rejected");
  }
  int[] ordinary=new int[36];for(int i=0;i<ordinary.length;i++)ordinary[i]=i%4;
  for(boolean eyes:new boolean[]{false,true})for(boolean redFrame:new boolean[]{false,true}){
   GridPuzzleVision.Pixels image=(x,y)->y/80*6+x/80==14?skullTile(x,y,eyes,redFrame):token(x,y,0,0,80,6,6,ordinary,vivid,0);
   var protectedBoard=GridPuzzleVision.detect(image,0,0,480,480,6,6,0,0,0,0,1400,null,true);
   check(eyes&&redFrame?protectedBoard.usable()&&protectedBoard.type(2,2)==0&&protectedBoard.specialCellsMask().get(14):!protectedBoard.specialCellsMask().get(14),"protected skull requires both face and red tile evidence");
   if(eyes&&redFrame)check(protectedBoard.reason(2,2)==GridPuzzleVision.CellReason.PROTECTED_SKULL,
     "protected skull carries a distinct diagnostic reason");
   var genericBoard=GridPuzzleVision.detect(image,0,0,480,480,6,6,0,0,0,0,1400,null);
   check(genericBoard.type(2,2)!=0,"generic mode cannot silently enable the skull preset");
  }
  try{GridPuzzleVision.Palette.decode("v1;NaN");throw new AssertionError("invalid palette accepted");}catch(IllegalArgumentException expected){checks++;}
  try{GridPuzzleVision.detect((x,y)->0,0,0,10,10,7,7,0,0,0,0,1000,null);throw new AssertionError("tiny geometry accepted");}catch(IllegalArgumentException expected){checks++;}
  System.out.println("Grid puzzle vision: "+checks+" assertions PASS; synthetic palettes, sizes, occlusion, persistence");
 }
}
