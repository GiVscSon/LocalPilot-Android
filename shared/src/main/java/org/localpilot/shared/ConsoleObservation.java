package org.localpilot.shared;

/** Actual pixel-derived observation for text-only NES agents.
 * Coarse luminance is NOT object recognition or a score signal.
 */
public final class ConsoleObservation {
 private ConsoleObservation(){}
 public static final int WIDTH=128,HEIGHT=120;
 private static final char[] LEVELS="0123456789ABCDEF".toCharArray();
 public static String luminance8x8(int[] argb,int width,int height){
  if(width!=WIDTH||height!=HEIGHT||argb==null||argb.length!=width*height)
   throw new IllegalArgumentException("Expected one real 128x120 NES frame");
  StringBuilder data=new StringBuilder(71);
  for(int row=0;row<8;row++){
   if(row>0)data.append('/');
   for(int col=0;col<8;col++){
    long total=0;int samples=0;
    for(int y=row*height/8;y<(row+1)*height/8;y++)
     for(int x=col*width/8;x<(col+1)*width/8;x++){
      int value=argb[y*width+x];
      long rgb=(value>>>16&255)*299L+(value>>>8&255)*587L+(value&255)*114L;
      total+=rgb/1000;samples++;
     }
    data.append(LEVELS[(int)Math.min(15,(total/samples)*16/256)]);
   }
  }
  return data.toString();
 }
}
