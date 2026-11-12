import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.tools.ToolProvider;

class HarnessCoordinatorVerify {
 public static void main(String[] args)throws Exception{
  Path out=Files.createTempDirectory("harness-gate-one");
  int compiled=ToolProvider.getSystemJavaCompiler().run(null,System.out,System.err,
    "--release","17","-d",out.toString(),
    "shared/src/main/java/org/localpilot/shared/ConsoleAgentContract.java",
    "shared/src/main/java/org/localpilot/shared/HarnessCoordinator.java",
    "tests/HarnessCoordinatorTest.java");
  if(compiled!=0)throw new AssertionError("Harness coordinator does not compile");
  try(URLClassLoader loader=new URLClassLoader(new URL[]{out.toUri().toURL()})){
   loader.loadClass("org.localpilot.shared.HarnessCoordinatorTest")
     .getMethod("main",String[].class).invoke(null,(Object)args);
  }
 }
}
