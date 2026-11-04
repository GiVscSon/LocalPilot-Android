import javax.tools.ToolProvider;
import java.net.*;
import java.nio.file.*;

class ProcessExitVerify {
 public static void main(String[]args)throws Exception{
  Path out=Files.createTempDirectory("localpilot-process-exit-test-");
  int code=ToolProvider.getSystemJavaCompiler().run(null,System.out,System.err,"--release","17","-d",out.toString(),
   "app/src/main/java/org/localpilot/android/ProcessExitMetadata.java","tests/ProcessExitMetadataTest.java");
  if(code!=0)throw new AssertionError("process metadata test compilation failed");
  try(var loader=new URLClassLoader(new URL[]{out.toUri().toURL()})){
   loader.loadClass("org.localpilot.android.ProcessExitMetadataTest").getMethod("main",String[].class).invoke(null,(Object)args);
  }
 }
}
