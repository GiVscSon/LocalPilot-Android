import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.util.List;
import javax.tools.ToolProvider;

/** Standalone JVM-only offline acceptance; no Android SDK and no network. */
class PaddleWeightLabVerify {
 public static void main(String[] args)throws Exception{
  Path out=Files.createTempDirectory("paddle-weight-lab-");
  List<String> sources=List.of("--release","17","-d",out.toString(),
    "app/src/main/java/org/localpilot/android/rl/PaddleWeightLab.java",
    "tests/PaddleWeightLabTest.java");
  if(ToolProvider.getSystemJavaCompiler()==null
    ||ToolProvider.getSystemJavaCompiler().run(null,System.out,System.err,sources.toArray(String[]::new))!=0)
   throw new AssertionError("PaddleWeightLab compilation failed");
  try(URLClassLoader loader=new URLClassLoader(new URL[]{out.toUri().toURL()})){
   loader.loadClass("org.localpilot.android.rl.PaddleWeightLabTest")
    .getMethod("main",String[].class).invoke(null,(Object)args);
  }
 }
}
