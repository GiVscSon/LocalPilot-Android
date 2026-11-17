import java.util.Arrays;
import org.localpilot.shared.NesStateTransfer;

public final class NesStateTransferVerify {
 static int checks;
 static void check(boolean ok){checks++;if(!ok)throw new AssertionError("check "+checks);}
 static void rejects(Runnable r){checks++;try{r.run();throw new AssertionError("accepted");}
  catch(IllegalArgumentException|IllegalStateException expected){}}
 public static void main(String[] args){
  NesStateTransfer t=new NesStateTransfer();
  rejects(()->t.beginTransfer("x",0));
  rejects(()->t.beginTransfer("x",NesStateTransfer.MAX_STATE_BYTES+1));
  byte[] data=new byte[70000];Arrays.fill(data,(byte)19);
  t.beginTransfer("one",data.length);
  check(!t.appendChunk("wrong",0,new byte[]{1}));
  rejects(()->t.appendChunk("one",1,new byte[]{1}));
  rejects(()->t.appendChunk("one",0,new byte[65537]));
  check(t.appendChunk("one",0,Arrays.copyOfRange(data,0,65536)));
  rejects(()->t.finishAndVerifySha256("one",NesStateTransfer.sha256Hex(data)));
  check(t.appendChunk("one",65536,Arrays.copyOfRange(data,65536,data.length)));
  check(t.isComplete());
  check(Arrays.equals(data,t.finishAndVerifySha256("one",NesStateTransfer.sha256Hex(data))));
  check(!t.isComplete());
  rejects(()->t.finishAndVerifySha256("one",null));
  t.beginTransfer("two",1);t.appendChunk("two",0,new byte[]{1});
  rejects(()->t.finishAndVerifySha256("two","0".repeat(64)));
  check(!t.isComplete());
  check(!t.appendChunk("two",0,new byte[]{1}));
  t.beginTransfer("three",1);t.cancelOnStop();
  check(!t.appendChunk("three",0,new byte[]{1}));
  rejects(()->t.finishAndVerifySha256("three",null));
  t.beginTransfer("four",1);
  check(!t.appendChunk("three",0,new byte[]{1}));
  check(t.appendChunk("four",0,new byte[]{7}));
  byte[] result=t.finishAndVerifySha256("four",null);check(result[0]==7);
  System.out.println("NesStateTransferVerify PASS "+checks);
 }
}
