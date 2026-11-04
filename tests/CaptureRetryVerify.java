import javax.tools.ToolProvider;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
/** Android-free compilation and safety checks for bounded screenshot retry. */
class CaptureRetryVerify {
 public static void main(String[] args) throws Exception {
  Path out=Files.createTempDirectory("capture-retry-classes-");
  if(ToolProvider.getSystemJavaCompiler().run(null,System.out,System.err,
    "--release","17","-d",out.toString(),
    "app/src/main/java/org/localpilot/android/CaptureRetry.java",
    "tests/CaptureRetryTest.java")!=0)throw new AssertionError("compile capture retry");
  try(URLClassLoader cl=new URLClassLoader(new URL[]{out.toUri().toURL()})) {
   cl.loadClass("org.localpilot.android.CaptureRetryTest")
      .getMethod("main",String[].class).invoke(null,(Object)args);
  }
 }
}
