import javax.tools.ToolProvider;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;

/** Android-free fail-closed browser transition test runner. */
class BrowserSceneVerify {
 public static void main(String[] args)throws Exception {
  if(ToolProvider.getSystemJavaCompiler()==null)throw new IllegalStateException("JDK required");
  Path out=Files.createTempDirectory("localpilot-browser-scene-");
  if(ToolProvider.getSystemJavaCompiler().run(null,System.out,System.err,
      "--release","17","-d",out.toString(),
      "app/src/main/java/org/localpilot/android/BrowserSceneGate.java",
      "tests/BrowserSceneGateTest.java")!=0)throw new AssertionError("compile browser scene gate");
  try(URLClassLoader cl=new URLClassLoader(new URL[]{out.toUri().toURL()})){
   cl.loadClass("org.localpilot.android.BrowserSceneGateTest")
      .getMethod("main",String[].class).invoke(null,(Object)args);
  }
 }
}
