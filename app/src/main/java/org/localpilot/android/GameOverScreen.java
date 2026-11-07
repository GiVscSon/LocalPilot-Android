package org.localpilot.android;

/** The Market result card: a wide yellow replay button on a white dialog.
 * Geometry and colour evidence only; this does not OCR the button text. */
public final class GameOverScreen {
 public interface Pixels { int get(int x,int y); }
 private GameOverScreen(){}
 /**
  * Pixel support for an independently identified semantic replay button inside
  * an explicitly confirmed result-modal rectangle. This method does not grant
  * permission to tap and cannot distinguish an advertisement by colour: the
  * caller must also reject unsafe/unknown UI and require an exact replay label.
  * All probes stay inside the supplied modal and allocate no working buffers.
  */
 public static boolean supportsReplayButton(Pixels pixels,int modalLeft,int modalTop,int modalRight,int modalBottom,
                                            int buttonLeft,int buttonTop,int buttonRight,int buttonBottom,
                                            int excludedLeft,int excludedTop,int excludedRight,int excludedBottom){
  if(pixels==null||modalLeft<0||modalTop<0||modalRight<=modalLeft||modalBottom<=modalTop
    ||buttonLeft<modalLeft||buttonTop<modalTop||buttonRight>modalRight||buttonBottom>modalBottom
    ||buttonRight<=buttonLeft||buttonBottom<=buttonTop)return false;
  long mw=(long)modalRight-modalLeft,mh=(long)modalBottom-modalTop;
  long bw=(long)buttonRight-buttonLeft,bh=(long)buttonBottom-buttonTop;
  if(mw<100||mh<80||bw<60||bh<12||bw*100<mw*35||bw*100>mw*96
    ||bh*100>mh*30||bw<bh*2||bw>bh*20)return false;
  // A panel touching any part of the actual target makes that target unknown.
  if(buttonLeft<excludedRight&&excludedLeft<buttonRight&&buttonTop<excludedBottom&&excludedTop<buttonBottom)return false;
  int yellow=0,total=0;
  for(int row=1;row<=5;row++)for(int col=1;col<=15;col++){
   int x=(int)(buttonLeft+bw*col/16),y=(int)(buttonTop+bh*row/6);
   int c=pixels.get(x,y),r=c>>16&255,g=c>>8&255,b=c&255;total++;
   if(r>185&&g>155&&g>r*.68f&&b<110)yellow++;
  }
  if(yellow*100<total*62)return false;
  long bandBottom=(long)buttonTop-Math.max(3,bh/3);
  long bandTop=Math.max((long)modalTop,(long)buttonTop-bh*3);
  if(bandBottom-bandTop<12)return false;
  int light=0;total=0;
  for(int row=1;row<=4;row++)for(int col=1;col<=11;col++){
   int x=(int)(buttonLeft+bw*col/12),y=(int)(bandTop+(bandBottom-bandTop)*row/5);
   if(x>=excludedLeft&&x<excludedRight&&y>=excludedTop&&y<excludedBottom)return false;
   int c=pixels.get(x,y),r=c>>16&255,g=c>>8&255,b=c&255;total++;
   if(r>205&&g>205&&b>205&&Math.max(r,Math.max(g,b))-Math.min(r,Math.min(g,b))<35)light++;
  }
  return light*100>=total*65;
 }
 public static boolean detect(Pixels pixels,int left,int top,int right,int bottom,
                             int excludedLeft,int excludedTop,int excludedRight,int excludedBottom){
  int w=right-left,h=bottom-top;
  if(pixels==null||w<120||h<180)return false;
  final int cols=80,rows=120;
  int consecutive=0;
  for(int row=24;row<96;row++){
   int y=top+row*h/rows,run=0,longest=0,start=0,bestStart=0;
   for(int col=0;col<cols;col++){
    int x=left+col*w/cols;
    boolean excluded=x>=excludedLeft&&x<excludedRight&&y>=excludedTop&&y<excludedBottom;
    int c=excluded?0:pixels.get(x,y),r=c>>16&255,g=c>>8&255,b=c&255;
    if(!excluded&&r>185&&g>155&&g>r*.68f&&b<110){if(run==0)start=col;run++;if(run>longest){longest=run;bestStart=start;}}
    else run=0;
   }
   // The score medals and orange platforms are much narrower than this button.
   if(longest>=32&&bestStart<cols/2&&bestStart+longest>cols/2){
    consecutive++;
    if(consecutive<3)continue;
    int light=0,total=0;
    // White card supporting the button, above its top edge (skip button text).
    for(int yy=Math.max(4,row-consecutive-17);yy<row-consecutive-3;yy+=2)
     for(int xx=bestStart+2;xx<bestStart+longest-2;xx+=2){
      int x=left+xx*w/cols,sy=top+yy*h/rows;
      if(x>=excludedLeft&&x<excludedRight&&sy>=excludedTop&&sy<excludedBottom)continue;
      int c=pixels.get(x,sy),r=c>>16&255,g=c>>8&255,b=c&255;total++;
      if(r>205&&g>205&&b>205&&Math.max(r,Math.max(g,b))-Math.min(r,Math.min(g,b))<35)light++;
     }
    if(total>=50&&light>total*.60f)return true;
   }else consecutive=0;
  }
  return false;
 }
}
