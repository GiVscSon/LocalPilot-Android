package org.localpilot.android;
import java.nio.ByteBuffer;
import java.util.Arrays;
public class RgbaFrameDecoderTest {
 private static int checks;
 private static void check(boolean ok){checks++;if(!ok)throw new AssertionError();}
 private static void rejects(Runnable r){checks++;try{r.run();}catch(IllegalArgumentException expected){return;}throw new AssertionError("accepted invalid plane");}
 public static void main(String[] args){
  for(boolean direct:new boolean[]{false,true}){
   // Two pixels per row, pixel padding, row padding, nonzero buffer position,
   // and no padding after the last pixel of the final row.
   ByteBuffer b=direct?ByteBuffer.allocateDirect(35):ByteBuffer.allocate(35);
   b.position(3);b.limit(29);
   for(int at:new int[]{3,9,19,25}){b.put(at,(byte)255);b.put(at+1,(byte)128);b.put(at+2,(byte)3);b.put(at+3,(byte)255);}
   // Select bottom-right pixel, preserve position and do not require padding after ROI.
   int[] roi={17,17};RgbaFrameDecoder.decodeRoi(b,16,6,2,2,1,1,2,2,roi);
   check(roi[0]==0xffff8003&&roi[1]==17&&b.position()==3);
   int[] fullRoi=new int[4];RgbaFrameDecoder.decodeRoi(b,16,6,2,2,0,0,2,2,fullRoi);
   check(Arrays.equals(fullRoi,new int[]{0xffff8003,0xffff8003,0xffff8003,0xffff8003}));
   b.limit(28);Arrays.fill(roi,17);
   rejects(()->RgbaFrameDecoder.decodeRoi(b,16,6,2,2,1,1,2,2,roi));
   check(Arrays.equals(roi,new int[]{17,17}));b.limit(29);
   rejects(()->RgbaFrameDecoder.decodeRoi(b,16,6,2,2,-1,0,1,1,roi));
   rejects(()->RgbaFrameDecoder.decodeRoi(b,16,6,2,2,0,0,3,1,roi));
   rejects(()->RgbaFrameDecoder.decodeRoi(b,16,6,2,2,1,1,1,2,roi));
   rejects(()->RgbaFrameDecoder.decodeRoi(b,9,6,2,2,0,0,1,1,roi));
   rejects(()->RgbaFrameDecoder.decodeRoi(b,Integer.MAX_VALUE,Integer.MAX_VALUE,Integer.MAX_VALUE,Integer.MAX_VALUE,0,0,1,2,roi));
   int[] out=new int[4];RgbaFrameDecoder.decode(b,2,2,6,16,out);
   check(Arrays.equals(out,new int[]{0xffff8003,0xffff8003,0xffff8003,0xffff8003}));check(b.position()==3);
   Arrays.fill(out,17);b.limit(28);
   rejects(()->RgbaFrameDecoder.decode(b,2,2,6,16,out));check(Arrays.equals(out,new int[]{17,17,17,17}));
   rejects(()->RgbaFrameDecoder.decode(b,2,2,6,9,out));
   rejects(()->RgbaFrameDecoder.decode(b,2,2,3,16,out));
   rejects(()->RgbaFrameDecoder.decode(b,Integer.MAX_VALUE,2,4,16,out));
   rejects(()->RgbaFrameDecoder.decode(b,2,2,4,16,new int[3]));
  }
  // Different pixels, nonzero position and a crop ending before the full frame.
  ByteBuffer varied=ByteBuffer.allocate(48);varied.position(4);
  for(int y=0;y<3;y++)for(int x=0;x<3;x++){
   int p=4+y*14+x*4;varied.put(p,(byte)(x+10*y));varied.put(p+1,(byte)3);varied.put(p+2,(byte)4);varied.put(p+3,(byte)255);
  }
  int[] full=new int[9];RgbaFrameDecoder.decode(varied,3,3,4,14,full);
  int[] crop=new int[4];RgbaFrameDecoder.decodeRoi(varied,14,4,3,3,1,1,3,3,crop);
  check(Arrays.equals(crop,new int[]{full[4],full[5],full[7],full[8]}));
  varied.limit(26);int[] early=new int[2];RgbaFrameDecoder.decodeRoi(varied,14,4,3,3,0,1,2,2,early);
  check(Arrays.equals(early,new int[]{full[3],full[4]})&&varied.position()==4);
  System.out.println("RgbaFrameDecoderTest: "+checks+" assertions PASS");
 }
}
