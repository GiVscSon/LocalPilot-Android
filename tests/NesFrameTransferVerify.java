import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.zip.CRC32;
import java.util.zip.DeflaterOutputStream;
import org.localpilot.shared.NesFrameTransfer;

/** Security and corruption regressions for the GameLab -> ModelLab PNG handoff. */
class NesFrameTransferVerify {
 static int checks;
 static void good(boolean condition,String what){
  checks++;if(!condition)throw new AssertionError(what);
 }
 static void reject(byte[] input,String what){
  checks++;
  try{NesFrameTransfer.verify(input);throw new AssertionError("Accepted "+what);}
  catch(IllegalArgumentException expected){}
 }
 static void u32(ByteArrayOutputStream out,long value){
  out.write((int)(value>>>24)&255);out.write((int)(value>>>16)&255);
  out.write((int)(value>>>8)&255);out.write((int)value&255);
 }
 static void chunk(ByteArrayOutputStream out,String type,byte[] data){
  u32(out,data.length);
  byte[] label=type.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
  out.writeBytes(label);out.writeBytes(data);
  CRC32 checksum=new CRC32();checksum.update(label);checksum.update(data);
  u32(out,checksum.getValue());
 }
 static byte[] screenshot(int width,int height)throws Exception{
  ByteArrayOutputStream out=new ByteArrayOutputStream();
  out.writeBytes(new byte[]{(byte)137,80,78,71,13,10,26,10});
  ByteArrayOutputStream head=new ByteArrayOutputStream();
  u32(head,width);u32(head,height);
  head.writeBytes(new byte[]{8,6,0,0,0});
  chunk(out,"IHDR",head.toByteArray());
  byte[] raw=new byte[height*(1+width*4)];
  for(int y=0;y<height;y++){
   int at=y*(1+width*4);raw[at]=0;
   for(int x=0;x<width;x++){
    raw[at+1+x*4]=(byte)x;raw[at+2+x*4]=(byte)y;
    raw[at+3+x*4]=(byte)(x^y);raw[at+4+x*4]=(byte)255;
   }
  }
  ByteArrayOutputStream compressed=new ByteArrayOutputStream();
  try(DeflaterOutputStream z=new DeflaterOutputStream(compressed)){z.write(raw);}
  chunk(out,"IDAT",compressed.toByteArray());
  chunk(out,"IEND",new byte[0]);
  return out.toByteArray();
 }
 public static void main(String[] args)throws Exception{
  byte[] valid=screenshot(NesFrameTransfer.WIDTH,NesFrameTransfer.HEIGHT);
  NesFrameTransfer.verify(valid);checks++;
  good(valid.length<NesFrameTransfer.MAX_BYTES,"PNG exceeds explicit IPC budget");
  good(NesFrameTransfer.MAX_BASE64_CHARS>=4*((valid.length+2)/3),"Base64 budget");
  reject(null,"null frame");
  reject(new byte[16],"short frame");
  reject(new byte[NesFrameTransfer.MAX_BYTES+1],"oversized frame");
  reject(screenshot(256,240),"full-size screenshot");
  reject(screenshot(128,119),"wrong height");
  byte[] tampered=valid.clone();tampered[20]^=1;reject(tampered,"changed dimensions and CRC");
  byte[] invalid=valid.clone();invalid[invalid.length-5]^=1;reject(invalid,"bad IEND CRC");
  reject(Arrays.copyOf(valid,valid.length-3),"truncated PNG");
  byte[] trailing=Arrays.copyOf(valid,valid.length+4);
  reject(trailing,"trailing payload");
  good(NesFrameTransfer.EXTRA_PNG.equals("org.localpilot.shared.NES_FRAME_PNG"),
     "stable intent field");
  System.out.println("NES_FRAME_TRANSFER_PASS checks="+checks+" png_bytes="+valid.length);
 }
}
