package org.localpilot.android;

/**
 * Conservative anomaly detector for screenshots. Near-black is UNKNOWN, not
 * evidence of SurfaceFlinger muting, game over, or an objective game reward.
 * Sampling is read-only, allocation-free and confined to a verified screen ROI.
 */
public final class FrameBlackoutGate {
 private FrameBlackoutGate() {}
 public interface Pixels { int get(int x,int y); }
 private static final int COLUMNS=32, ROWS=16;
 /**
  * More than 99.5% sampled RGB-black pixels requires a fresh capture before
  * any game-state decoder. Intended as fail-closed protection from muted,
  * protected, loading or legitimately black windows; cause is not inferred.
  */
 public static boolean nearBlack(int width,int height,int left,int top,
                                 int right,int bottom,Pixels pixels){
  if(pixels==null||width<=0||height<=0||left<0||top<0
     ||right<=left||bottom<=top||right>width||bottom>height)
   throw new IllegalArgumentException("invalid screenshot ROI");
  int black=0, samples=COLUMNS*ROWS;
  int rw=right-left,rh=bottom-top;
  for(int row=0;row<ROWS;row++){
   int y=top+(int)((long)(row*2+1)*rh/(ROWS*2));
   for(int col=0;col<COLUMNS;col++){
    int x=left+(int)((long)(col*2+1)*rw/(COLUMNS*2));
    if((pixels.get(x,y)&0x00ffffff)==0)black++;
   }
  }
  return black*1000>=samples*995;
 }
}
