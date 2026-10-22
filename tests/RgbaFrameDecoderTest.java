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
   int[] out=new int[4];RgbaFrameDecoder.decode(b,2,2,6,16,out);
   check(Arrays.equals(out,new int[]{0xffff8003,0xffff8003,0xffff8003,0xffff8003}));check(b.position()==3);
   Arrays.fill(out,17);b.limit(28);
   rejects(()->RgbaFrameDecoder.decode(b,2,2,6,16,out));check(Arrays.equals(out,new int[]{17,17,17,17}));
   rejects(()->RgbaFrameDecoder.decode(b,2,2,6,9,out));
   rejects(()->RgbaFrameDecoder.decode(b,2,2,3,16,out));
   rejects(()->RgbaFrameDecoder.decode(b,Integer.MAX_VALUE,2,4,16,out));
   rejects(()->RgbaFrameDecoder.decode(b,2,2,4,16,new int[3]));
  }
  System.out.println("RgbaFrameDecoderTest: "+checks+" assertions PASS");
 }
}
