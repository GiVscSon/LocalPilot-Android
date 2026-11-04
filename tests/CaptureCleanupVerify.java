import javax.tools.ToolProvider;
import java.net.*;
import java.nio.file.*;
class CaptureCleanupVerify {
 public static void main(String[]args)throws Exception{
  Path out=Files.createTempDirectory("capture-cleanup-");
  int code=ToolProvider.getSystemJavaCompiler().run(null,System.out,System.err,"--release","17","-d",out.toString(),
   "app/src/main/java/org/localpilot/android/CaptureCleanup.java","tests/CaptureCleanupTest.java");
  if(code!=0)throw new AssertionError("capture cleanup compilation failed");
  try(var loader=new URLClassLoader(new URL[]{out.toUri().toURL()})){
   loader.loadClass("org.localpilot.android.CaptureCleanupTest").getMethod("main",String[].class).invoke(null,(Object)args);
  }
 }
}
