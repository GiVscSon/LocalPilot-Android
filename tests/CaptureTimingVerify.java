import javax.tools.ToolProvider;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;

/** Strict host-JVM gate: java tests/CaptureTimingVerify.java */
class CaptureTimingVerify {
 public static void main(String[] args)throws Exception{
  if(ToolProvider.getSystemJavaCompiler()==null)throw new IllegalStateException("Full JDK required");
  Path out=Files.createTempDirectory("localpilot-capture-timing-");
  int rc=ToolProvider.getSystemJavaCompiler().run(null,System.out,System.err,
      "--release","17","-d",out.toString(),
      "app/src/main/java/org/localpilot/android/CaptureTimingTrace.java",
      "tests/CaptureTimingTest.java");
  if(rc!=0)throw new AssertionError("capture timing Java 17 compile failed");
  try(URLClassLoader loader=new URLClassLoader(new URL[]{out.toUri().toURL()})){
   loader.loadClass("org.localpilot.android.CaptureTimingTest")
       .getMethod("main",String[].class).invoke(null,(Object)args);
  }
 }
}
