import java.net.*;
import java.nio.file.*;
import javax.tools.ToolProvider;
class ConsoleAgentContractVerify {
 public static void main(String[] args)throws Exception {
  Path out=Files.createTempDirectory("localpilot-console-agent-contract");
  if(ToolProvider.getSystemJavaCompiler()==null||
      ToolProvider.getSystemJavaCompiler().run(null,System.out,System.err,
      "--release","17","-d",out.toString(),
      "shared/src/main/java/org/localpilot/shared/ConsoleAgentContract.java",
      "game-lab/src/main/java/org/localpilot/gamelab/ConsoleAgentGate.java",
      "tests/ConsoleAgentContractTest.java")!=0)
    throw new AssertionError("Console agent protocol test did not compile");
  try(URLClassLoader loader=new URLClassLoader(new URL[]{out.toUri().toURL()})){
    loader.loadClass("org.localpilot.gamelab.ConsoleAgentContractTest")
      .getMethod("main",String[].class).invoke(null,(Object)args);
  }
 }
}
