import javax.tools.ToolProvider;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.*;
class CaptureMatrixVerify {
 public static void main(String[] args)throws Exception{
  Path out=Files.createTempDirectory("capture-matrix-classes-");
  int result=ToolProvider.getSystemJavaCompiler().run(null,System.out,System.err,"-d",out.toString(),
   "app/src/main/java/org/localpilot/android/CaptureStartup.java",
   "app/src/main/java/org/localpilot/android/StageMetrics.java","tests/CaptureMatrixTest.java");
  if(result!=0)throw new IllegalStateException("compile failed");
  try(var loader=new URLClassLoader(new URL[]{out.toUri().toURL()})){
   loader.loadClass("org.localpilot.android.CaptureMatrixTest").getMethod("main",String[].class).invoke(null,(Object)args);
  }
 }
}
