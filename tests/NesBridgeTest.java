package org.localpilot.shared;

import java.nio.file.Files;
import java.nio.file.Path;

public final class NesBridgeTest {
 private static int checks;
 private static void yes(boolean condition,String message){
  checks++;if(!condition)throw new AssertionError(message);
 }
 private static void reject(String evidence,String name){
  boolean threw=false;
  try{NesTrial.parse(evidence);}catch(IllegalArgumentException expected){threw=true;}
  yes(threw,name);
 }
 public static void main(String[] args)throws Exception{
  NesTrial demo=NesTrial.create("HOMEBREW","A",150,182,12731371,-4312416);
  String line=demo.serialize();
  NesTrial decoded=NesTrial.parse(line);
  yes(decoded.button.equals("A"),"action survives Android Intent payload");
  yes(decoded.frameAfter==182&&decoded.frameBefore==150,"real frame order");
  yes(decoded.status.equals("PIXELS_DIFFER"),"honest pixel fingerprint difference");
  yes(decoded.advisoryPrompt().contains("not a proven win"),"no fake score");
  yes(decoded.advisoryPrompt().contains("Never issue button sequences"),"advice only");
  yes(NesTrial.ACTION.equals("org.localpilot.modellab.NES_TRIAL"),"cross-app contract stable");
  yes(NesTrial.create("HOMEBREW","B",150,165,21,21).status.equals("NO_VISUAL_CHANGE"),
      "identical frame fingerprints never count as changed");
  reject(line.replace("PIXELS_DIFFER","NO_VISUAL_CHANGE"),"forged game result rejected");
  reject(line.replace("|A|","|RIGHT|"),"changed action rejected by checksum");
  reject(line.replace("JSNES_2_1_0","JSNES_9_9_9"),"unknown emulator rejected");
  reject(line.substring(0,line.length()-2),"truncated evidence rejected");
  boolean bad=false;
  try{NesTrial.create("HOMEBREW","A",150,151,10,20);}
  catch(IllegalArgumentException expected){bad=true;}
  yes(bad,"too-short frame interval rejected");
  bad=false;
  try{NesTrial.create("USER_ROM","POWER",150,182,10,20);}
  catch(IllegalArgumentException expected){bad=true;}
  yes(bad,"unknown input rejected");
  if(args.length>0){
   String actual=Files.readString(Path.of(args[0]));
   NesTrial real=NesTrial.parse(actual);
   yes(real.serialize().equals(actual.trim()),"legacy evidence retains exact wire identity across parsing");
   yes(real.scenario.equals("HOMEBREW")&&real.button.equals("A"),
       "actual JSNES homebrew output");
   yes(real.status.equals("PIXELS_DIFFER"),"CPU/PPU actually changed pixel hash");
   yes(real.frameAfter-real.frameBefore>=15,"real emulated frames advanced");
  }
  System.out.println("NES_MODELLAB_BRIDGE_PASS checks="+checks);
 }
}
