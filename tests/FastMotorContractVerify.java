import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.tools.ToolProvider;

/** Host gate, not emulator proof or Android latency measurements. */
class FastMotorContractVerify {
 public static void main(String[] args)throws Exception{
  Path out=Files.createTempDirectory("localpilot-motor-contract");
  int code=ToolProvider.getSystemJavaCompiler().run(null,System.out,System.err,
    "--release","17","-d",out.toString(),
    "shared/src/main/java/org/localpilot/shared/ConsoleAgentContract.java",
    "shared/src/main/java/org/localpilot/shared/VoidLancerGameState.java",
    "shared/src/main/java/org/localpilot/shared/FastMotorContract.java",
    "tests/FastMotorContractTest.java");
  if(code!=0)throw new AssertionError("FastMotorContract failed to compile");
  try(URLClassLoader loader=new URLClassLoader(new URL[]{out.toUri().toURL()})){
   loader.loadClass("org.localpilot.shared.FastMotorContractTest")
    .getMethod("main",String[].class).invoke(null,(Object)args);
  }
 }
}
