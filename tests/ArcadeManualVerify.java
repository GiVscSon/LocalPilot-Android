import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.tools.ToolProvider;
class ArcadeManualVerify {
 public static void main(String[] args)throws Exception{
  Path out=Files.createTempDirectory("localpilot-manual-");
  if(ToolProvider.getSystemJavaCompiler().run(null,System.out,System.err,
      "--release","17","-d",out.toString(),
      "app/src/main/java/org/localpilot/android/rl/ArcadeEnv.java",
      "app/src/main/java/org/localpilot/android/ArcadeManualController.java",
      "tests/ArcadeManualControllerTest.java")!=0)
   throw new AssertionError("Manual game control compile failure");
  try(URLClassLoader cl=new URLClassLoader(new URL[]{out.toUri().toURL()})){
   cl.loadClass("org.localpilot.android.ArcadeManualControllerTest")
     .getMethod("main",String[].class).invoke(null,(Object)args);
  }
 }
}
