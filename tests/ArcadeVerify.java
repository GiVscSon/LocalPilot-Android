import javax.tools.ToolProvider;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;

/** Java source launcher; optional argument points to private extracted replay frames. */
class ArcadeVerify {
 public static void main(String[] args)throws Exception{
  var out=Files.createTempDirectory("localpilot-arcade-verify-");
  String[] sources={"Board2048","PixelBoardReader","SceneRecovery","ArcadeProfile","ArcadeObservation","ArcadePolicy","ArcadeVision"};
  var command=new java.util.ArrayList<String>();command.add("-d");command.add(out.toString());
  for(String source:sources)command.add("app/src/main/java/org/localpilot/android/"+source+".java");
  command.add("tests/ArcadeProfilesTest.java");
  if(ToolProvider.getSystemJavaCompiler().run(null,System.out,System.err,command.toArray(String[]::new))!=0)throw new IllegalStateException("compile failed");
  try(var loader=new URLClassLoader(new URL[]{out.toUri().toURL()})){
   loader.loadClass("org.localpilot.android.ArcadeProfilesTest").getMethod("main",String[].class).invoke(null,(Object)args);
  }
 }
}
