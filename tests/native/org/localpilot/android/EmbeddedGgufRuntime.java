package org.localpilot.android;

/**
 * Host-JVM probe for the production JNI decoder. This class deliberately
 * shares the binary JNI name with the Android app's runtime, but is compiled
 * only by the isolated CI smoke-test job.
 */
public final class EmbeddedGgufRuntime {
 private static native String nativeInfer(
   String filename,String system,String question,int maxTokens,float temperature);
 private static native String nativeAnalyzeImage(
   String model,String projector,String question,byte[] rgb,int width,int height);
 private static native void nativeRelease();

 public static void main(String[] args) {
  if(args.length!=1&&args.length!=5)
   throw new IllegalArgumentException("Supply MNN or MNN Qwen LFM VL MMProj");
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
   if(args.length==5){
    String qwen=nativeInfer(args[1],"Respond in short English sentences.",
      "Explain one reason to preserve empty cells in 2048.",96,0f);
    System.out.println("NATIVE_QWEN_RAW="+quote(qwen));
    if(qwen==null||qwen.isBlank())throw new AssertionError("Qwen cannot produce local strategy text");
    String liquid=nativeInfer(args[2],"Answer in English.",
      "What is a useful principle for avoiding game hazards?",96,0f);
    System.out.println("NATIVE_LFM_RAW="+quote(liquid));
    if(liquid==null||liquid.isBlank())throw new AssertionError("LFM failed text generation");
    int w=224,h=224;byte[] rgb=new byte[w*h*3];
    for(int y=0;y<h;y++)for(int x=0;x<w;x++){
     int at=(y*w+x)*3;
     rgb[at]=(byte)(x<w/2?220:30);
     rgb[at+1]=(byte)(x<w/2?25:170);
     rgb[at+2]=(byte)40;
    }
    String vision=nativeAnalyzeImage(args[3],args[4],
      "Describe the two colored areas that you can see. Avoid guessing game objects.",rgb,w,h);
    System.out.println("NATIVE_VISION_RAW="+quote(vision));
    String lower=vision==null?"":vision.toLowerCase(java.util.Locale.ROOT);
    if(!lower.contains("red")||!lower.contains("green"))
     throw new AssertionError("LFM2.5-VL did not identify BOTH real image colors: "+quote(vision));
    System.out.println("NATIVE_MULTI_MODEL_AND_VISION_SMOKE_PASS");
   }
  } finally {
   nativeRelease();
  }
 }
 private static String quote(String text){
  return text==null?"null":("\""+text.replace("\n","\\n")
    .replace("\r","\\r").substring(0,Math.min(140,text.length()))+"\"");
 }
}
