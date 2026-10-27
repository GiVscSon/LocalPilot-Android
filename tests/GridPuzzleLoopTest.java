package org.localpilot.android;

/** Synthetic pixel-to-action-to-pixel loops. No Android dispatch or gameplay claim. */
public final class GridPuzzleLoopTest {
 private static int checks;
 private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
 private static int token(int x,int y,int left,int top,int size,int rows,int cols,int[] types,int[] colors){
  if(x<left||y<top||x>=left+size*cols||y>=top+size*rows)return 0x222222;
  int col=(x-left)/size,row=(y-top)/size,type=types[row*cols+col];if(type<0)return 0x505050;
  float nx=(x-left-col*size)/(float)size,ny=(y-top-row*size)/(float)size;
  if(nx<.12f||nx>.88f||ny<.12f||ny>.88f)return 0xdddddd;
  int rgb=colors[type],shade=nx>.57f&&ny>.57f?-12:0;
  return Math.max(0,(rgb>>16&255)+shade)<<16|Math.max(0,(rgb>>8&255)+shade)<<8|Math.max(0,(rgb&255)+shade);
 }
 private static GridPuzzleVision.Observation read(int[] cells,int[] colors,int left,int top,int size,int rows,int cols,long at,GridPuzzleVision.Palette palette){
  return GridPuzzleVision.detect((x,y)->token(x,y,left,top,size,rows,cols,cells,colors),left,top,left+size*cols,top+size*rows,rows,cols,0,0,0,0,at,palette);
 }
 public static void main(String[] args){
  int[][] palettes={{0xe72832,0xfbc72b,0x4aa91f,0x266ac6},{0x993599,0xe28026,0x29b5ae,0x293758}};int scenes=0;
  for(GridPuzzlePolicy.Mode mode:GridPuzzlePolicy.Mode.values())for(int dimension:new int[]{3,6,7})for(int size:new int[]{20,45,80})for(int[] colors:palettes){
   scenes++;int rows=dimension,cols=dimension,left=23,top=117;int[] physical=new int[rows*cols];
   for(int row=0;row<rows;row++)for(int col=0;col<cols;col++)physical[row*cols+col]=(row*3+col*2)%colors.length;
   if(mode==GridPuzzlePolicy.Mode.SWAP_MATCH3){physical[0]=physical[2]=physical[cols+1]=0;physical[1]=1;}
   else physical[0]=physical[1]=0;
   var first=read(physical,colors,left,top,size,rows,cols,1000,null);
   check(first.usable()&&first.complete(),"pixel field recognized "+mode+" dimension="+dimension);
   var restoredPalette=GridPuzzleVision.Palette.decode(first.palette().encode());
   var second=read(physical,colors,left,top,size,rows,cols,1100,restoredPalette);
   GridPuzzleSession session=new GridPuzzleSession();
   check(session.observe(rows,cols,first.cells(),1000,1000)==GridPuzzleSession.Status.WAIT_STABLE,"single pixel frame cannot dispatch");
   check(second.usable()&&session.observe(rows,cols,second.cells(),1100,1100)==GridPuzzleSession.Status.READY,"restored palette and second frame authorize solving");
   var decision=GridPuzzlePolicy.choose(new GridPuzzle(rows,cols,second.cells()),mode);
   check(decision!=null,"solver receives classified symbols");
   float fromX=left+(decision.fromCol+.5f)*size,fromY=top+(decision.fromRow+.5f)*size;
   float toX=left+(decision.toCol+.5f)*size,toY=top+(decision.toRow+.5f)*size;
   check(fromX>=left&&fromX<left+cols*size&&toX>=left&&toX<left+cols*size&&fromY>=top&&fromY<top+rows*size&&toY>=top&&toY<top+rows*size,"gesture cell centers stay inside selected ROI");
   if(mode==GridPuzzlePolicy.Mode.SWAP_MATCH3)check(Math.abs(fromX-toX)+Math.abs(fromY-toY)==size,"swap gesture crosses exactly one orthogonal cell");
   else if(mode==GridPuzzlePolicy.Mode.TAP_GROUP)check(fromX==toX&&fromY==toY,"group action maps to one stationary tap");
   else check(fromX!=toX||fromY!=toY,"trace action maps to a swipe along the recognized line");
   session.sent(second.cells(),decision,1150);session.acknowledge(1200);
   check(session.pending()&&session.verifiedChanges()==0,"dispatch and Android ACK are not game progress");
   var unchanged=read(physical,colors,left,top,size,rows,cols,1400,restoredPalette);
   session.observe(rows,cols,unchanged.cells(),1400,1400);
   check(session.observe(rows,cols,unchanged.cells(),1480,1480)==GridPuzzleSession.Status.WAIT_RESPONSE&&session.verifiedChanges()==0,"unchanged pixels never confirm response");
   int[] response=physical.clone();
   if(mode==GridPuzzlePolicy.Mode.SWAP_MATCH3){int a=decision.fromRow*cols+decision.fromCol,b=decision.toRow*cols+decision.toCol,tmp=response[a];response[a]=response[b];response[b]=tmp;}
   for(int index:decision.clearedCells())response[index]=(physical[index]+1)%colors.length;
   // This controlled response tests visual change confirmation. It does not
   // simulate full refill rules, score, cascades or a successful game round.
   var after=read(response,colors,left,top,size,rows,cols,1600,restoredPalette);
   var afterAgain=read(response,colors,left,top,size,rows,cols,1700,restoredPalette);
   check(after.usable()&&after.signature()!=second.signature(),"applied symbolic action changes recognized pixels");
   check(session.observe(rows,cols,after.cells(),1600,1600)==GridPuzzleSession.Status.WAIT_RESPONSE,"one changed frame is insufficient");
   check(session.observe(rows,cols,afterAgain.cells(),1700,1700)==GridPuzzleSession.Status.CHANGED&&session.verifiedChanges()==1&&!session.pending(),"second stable changed pixel field confirms one response");
   var staticAfter=read(response,colors,left,top,size,rows,cols,1800,restoredPalette);
   session.observe(rows,cols,staticAfter.cells(),1800,1800);check(session.verifiedChanges()==1,"static response never counted twice");
   physical[0]=-1;var unknown=read(physical,colors,left,top,size,rows,cols,1900,restoredPalette);
   check(!unknown.usable()&&!unknown.complete(),"blank token is unknown in pixels");
   check(GridPuzzlePolicy.choose(new GridPuzzle(rows,cols,unknown.cells()),mode)==null,"unknown token prevents every mode from issuing input");
   check(session.observe(rows,cols,unknown.cells(),1900,1900)==GridPuzzleSession.Status.WAIT_STABLE,"unknown field removes stable authorization");
  }
  System.out.println("Grid puzzle loop: "+checks+" assertions PASS; "+scenes+" synthetic pixel/action/response scenes, no phone test");
 }
}
