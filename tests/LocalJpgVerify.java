import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.tools.ToolProvider;

/** Offline replay: java -Dlocalpilot.jpgDir=/path/to/screens tests/LocalJpgVerify.java */
class LocalJpgVerify {
 public static void main(String[] args) throws Exception {
  String path=System.getProperty("localpilot.jpgDir");
  if(path==null||path.isBlank())path=System.getenv("LOCALPILOT_JPG_DIR");
  if(path==null||path.isBlank()){
   System.out.println("LOCAL_JPG_REPLAY SKIP: localpilot.jpgDir / LOCALPILOT_JPG_DIR not set; ordinary CI unaffected");
   return;
  }
  Path dir=Path.of(path).toAbsolutePath().normalize();
  if(!Files.isDirectory(dir))throw new IllegalArgumentException("LOCAL_JPG_DIR_NOT_DIRECTORY: "+dir);
  try(var images=Files.list(dir)){
   if(images.noneMatch(f->Files.isRegularFile(f)&&f.getFileName().toString().toLowerCase(java.util.Locale.ROOT).matches(".*\\.(jpg|jpeg|png)"))
       &&!Files.exists(dir.resolve("localpilot-frames.csv"))){
    System.out.println("LOCAL_JPG_REPLAY SKIP: no JPG/PNG images in "+dir);
    return;
   }
  }
  Path classes=Files.createTempDirectory("localpilot-jpg-replay-");
  List<String> cmd=new ArrayList<>(List.of("--release","17","-d",classes.toString()));
  for(String source:List.of("Board2048","PixelBoardReader","BoardSelection","PixelBoardLocator","Verified2048",
    "Color2048Reader","BubbleBoard","BubbleVision","GridPuzzleVision","ArcadeProfile","ArcadeObservation","ArcadeVision","SceneRecovery"))
   cmd.add("app/src/main/java/org/localpilot/android/"+source+".java");
  cmd.add("tests/LocalJpgReplay.java");
  if(ToolProvider.getSystemJavaCompiler()==null)throw new IllegalStateException("JDK 17+ required, not a JRE");
  if(ToolProvider.getSystemJavaCompiler().run(null,System.out,System.err,cmd.toArray(String[]::new))!=0)
   throw new IllegalStateException("LOCAL_JPG_REPLAY COMPILE_FAILED");
  try(URLClassLoader loader=new URLClassLoader(new URL[]{classes.toUri().toURL()})){
   loader.loadClass("org.localpilot.android.LocalJpgReplay").getMethod("main",String[].class).invoke(null,(Object)new String[]{dir.toString()});
  }catch(java.lang.reflect.InvocationTargetException failure){
   Throwable root=failure.getCause();if(root instanceof Exception exception)throw exception;
   if(root instanceof Error error)throw error;
   throw failure;
  }
 }
}