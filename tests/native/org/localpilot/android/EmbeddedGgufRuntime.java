package org.localpilot.android;

/**
 * Host-JVM probe for the production JNI decoder. This class deliberately
 * shares the binary JNI name with the Android app's runtime, but is compiled
 * only by the isolated CI smoke-test job.
 */
public final class EmbeddedGgufRuntime {
 private static native String nativeInfer(
   String filename,String system,String question,int maxTokens,float temperature);
 private static native void nativeRelease();

 public static void main(String[] args) {
  if(args.length!=1)throw new IllegalArgumentException("Supply the verified GGUF model");
  System.loadLibrary("localpilot_embedded");
  String system="Answer in English only. Follow the exact output format. No explanations.";
  try {
   String blue=nativeInfer(args[0],system,"Reply with exactly one word: BLUE",16,0f);
   String sum=nativeInfer(args[0],system,"What is 12 + 15? Reply with the number only.",16,0f);
   String cat=nativeInfer(args[0],system,
      "Write one short English sentence about a cat.",64,0f);
   System.out.println("NATIVE_MODEL_QUALITY blue="+quote(blue)+" sum="+quote(sum)+
      " cat="+quote(cat));
   if(!"BLUE".equals(blue.trim())||!LocalCoreContract.acceptArithmetic(sum)||
       !LocalCoreContract.acceptEnglish(cat)||
       !cat.toLowerCase(java.util.Locale.ROOT).contains("cat"))
    throw new AssertionError("A loaded model is not necessarily a reliable English core; keep the APK experimental");
   System.out.println("NATIVE_MODEL_ENGLISH_SMOKE_PASS");
   Board2048 board=new Board2048(new int[]{
     2,2,0,0, 4,4,0,0, 0,0,0,0, 0,0,0,0
   });
   String proposal=nativeInfer(args[0],
     "You are a 2048 puzzle solver. Reply with one legal direction word only. "+
     "No explanation, punctuation or extra tokens.",
     Neural2048Verifier.prompt(board,0),24,0f);
   int parsed=Neural2048Verifier.parse(proposal);
   int baseline=board.chooseAvoiding(0);
   var gate=Neural2048Verifier.verify(board,board,0,baseline,proposal);
   System.out.println("NATIVE_2048_PROPOSAL raw="+quote(proposal)+
     " parsed="+parsed+" accepted="+gate.accepted+" decision="+gate.direction+
     " reason="+gate.reason);
   if(parsed<0)throw new AssertionError("MNN failed strict one-token 2048 action grammar");

  } finally {
   nativeRelease();
  }
 }
 private static String quote(String text){
  return text==null?"null":("\""+text.replace("\n","\\n")
    .replace("\r","\\r").substring(0,Math.min(140,text.length()))+"\"");
 }
}
