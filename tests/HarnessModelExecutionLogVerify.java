import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.tools.ToolProvider;

class HarnessModelExecutionLogVerify {
 public static void main(String[] args)throws Exception{
  Path output=Files.createTempDirectory("model-inference-log-verify");
  int code=ToolProvider.getSystemJavaCompiler().run(null,System.out,System.err,
    "--release","17","-d",output.toString(),
    "shared/src/main/java/org/localpilot/shared/ConsoleAgentContract.java",
    "model-lab/src/main/java/org/localpilot/android/HarnessModelExecutionLog.java",
    "tests/HarnessModelExecutionLogTest.java");
  if(code!=0)throw new AssertionError("Inference journal failed to compile");
  try(URLClassLoader loader=new URLClassLoader(new URL[]{output.toUri().toURL()})){
   loader.loadClass("org.localpilot.android.HarnessModelExecutionLogTest")
     .getMethod("main",String[].class).invoke(null,(Object)args);
  }
 }
}
