import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import javax.tools.ToolProvider;

/** Pure synthetic offline checks. No private captures or Android gestures. */
class LocalJpgContractVerify {
 private static int checks;
 private static void require(boolean ok,String reason){checks++;if(!ok)throw new AssertionError(reason);}
 private static String replay(Class<?> cls,Path dir,Class<? extends Throwable> expected,String fragment)throws Exception{
  PrintStream original=System.out;ByteArrayOutputStream buffer=new ByteArrayOutputStream();
  Throwable failure=null;
  try(PrintStream stream=new PrintStream(buffer,true,StandardCharsets.UTF_8)){
   System.setOut(stream);
   try{cls.getMethod("main",String[].class).invoke(null,(Object)new String[]{dir.toString()});}
   catch(InvocationTargetException error){failure=error.getCause();}
  }finally{System.setOut(original);}
  String report=buffer.toString(StandardCharsets.UTF_8);
  require(expected==null?failure==null:failure!=null&&expected.isInstance(failure),
    "unexpected replay outcome: "+failure+" report="+report);
  require(report.contains(fragment),"missing diagnostic "+fragment+" report="+report);
  return report;
 }
 public static void main(String[] unused)throws Exception{
  Path classes=Files.createTempDirectory("jpg-contract-classes-");
  List<String> sources=new ArrayList<>(List.of("--release","17","-d",classes.toString()));
  for(String name:List.of("Board2048","PixelBoardReader","BoardSelection","PixelBoardLocator","Verified2048",
    "Color2048Reader","BubbleBoard","BubbleVision","GridPuzzleVision","ArcadeProfile",
    "ArcadeObservation","ArcadeVision","SceneRecovery"))
   sources.add("app/src/main/java/org/localpilot/android/"+name+".java");
  sources.add("tests/LocalJpgReplay.java");
  require(ToolProvider.getSystemJavaCompiler()!=null,"JDK required");
  require(ToolProvider.getSystemJavaCompiler().run(null,System.out,System.err,sources.toArray(String[]::new))==0,
    "Local JPG replay compilation failed");
  Path root=Files.createTempDirectory("jpg-contract-data-");
  try(URLClassLoader loader=new URLClassLoader(new URL[]{classes.toUri().toURL()})){
   Class<?> cls=loader.loadClass("org.localpilot.android.LocalJpgReplay");
   Path empty=Files.createDirectory(root.resolve("empty"));
   replay(cls,empty,null,"LOCAL_JPG_REPLAY SKIP");
   Path missing=Files.createDirectory(root.resolve("missing"));
   Files.writeString(missing.resolve("localpilot-frames.csv"),
     "filename,profile,left,top,right,bottom\nmissing.png,GRID6,0,0,8,8\n");
   require(replay(cls,missing,AssertionError.class,"MISSING_IMAGE").contains("missing=1"),
     "declared missing frame was not counted");
   Path unlabelled=Files.createDirectory(root.resolve("unlabelled"));
   ImageIO.write(new BufferedImage(8,8,BufferedImage.TYPE_INT_ARGB),"png",
     unlabelled.resolve("unknown.png").toFile());
   replay(cls,unlabelled,AssertionError.class,"UNVERIFIED");
   Path invalid=Files.createDirectory(root.resolve("invalid-exclusion"));
   ImageIO.write(new BufferedImage(8,8,BufferedImage.TYPE_INT_ARGB),"png",
     invalid.resolve("sample.png").toFile());
   Files.writeString(invalid.resolve("localpilot-frames.csv"),
     "filename,profile,left,top,right,bottom,excludedLeft,excludedTop,excludedRight,excludedBottom\n"
     +"sample.png,GRID6,0,0,8,8,-1,0,3,4\n");
   replay(cls,invalid,AssertionError.class,"INVALID_EXCLUDED_ROI");
   Path incomplete=Files.createDirectory(root.resolve("incomplete"));
   ImageIO.write(new BufferedImage(8,8,BufferedImage.TYPE_INT_ARGB),"png",
     incomplete.resolve("present.png").toFile());
   Files.writeString(incomplete.resolve("localpilot-frames.csv"),
     "filename,profile,left,top,right,bottom\npresent.png,GRID6,0,0,8,8\nmissing.png,GRID6,0,0,8,8\n");
   replay(cls,incomplete,AssertionError.class,"MISSING_IMAGE");
  }
  System.out.println("LOCAL_JPG_CONTRACT_PASS checks="+checks);
 }
}
