import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.*;
import javax.tools.ToolProvider;

class JumperWeightShadowVerify {
 public static void main(String[] args)throws Exception{
  Path out=Files.createTempDirectory("ari-weight-shadow-");
  String[] files={
   "--release","17","-d",out.toString(),
   "app/src/main/java/org/localpilot/android/JumperState.java",
   "app/src/main/java/org/localpilot/android/JumperPolicy.java",
   "app/src/main/java/org/localpilot/android/JumperWeightShadow.java",
   "app/src/main/java/org/localpilot/android/rl/WeightSpaceSkills.java",
   "tests/JumperWeightShadowTest.java"
  };
  if(ToolProvider.getSystemJavaCompiler()==null
    ||ToolProvider.getSystemJavaCompiler().run(null,System.out,System.err,files)!=0)
   throw new AssertionError("Ari real-frame shadow compilation failed");
  try(var loader=new URLClassLoader(new URL[]{out.toUri().toURL()})){
   loader.loadClass("org.localpilot.android.JumperWeightShadowTest")
    .getMethod("main",String[].class).invoke(null,(Object)args);
  }
 }
}
