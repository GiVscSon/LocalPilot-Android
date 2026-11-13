import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.tools.ToolProvider;

class DiagnosticFrameSizeVerify {
 public static void main(String[] args)throws Exception{
  Path out=Files.createTempDirectory("diagnostic-frame-size-");
  if(ToolProvider.getSystemJavaCompiler().run(null,System.out,System.err,
    "--release","17","-d",out.toString(),
    "app/src/main/java/org/localpilot/android/DiagnosticFrameSize.java",
    "app/src/main/java/org/localpilot/android/DiagnosticBundle.java",
    "tests/DiagnosticFrameSizeTest.java")!=0)throw new AssertionError("diagnostic size compile");
  try(URLClassLoader loader=new URLClassLoader(new URL[]{out.toUri().toURL()})){
   loader.loadClass("org.localpilot.android.DiagnosticFrameSizeTest")
     .getMethod("main",String[].class).invoke(null,(Object)args);
  }
 }
}
