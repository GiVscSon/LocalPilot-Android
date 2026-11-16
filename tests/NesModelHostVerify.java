import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.tools.ToolProvider;

class NesModelHostVerify {
 public static void main(String[] args)throws Exception{
  Path out=Files.createTempDirectory("nes-model-host-verify");String b="shared/src/main/java/org/localpilot/shared/";
  int compiled=ToolProvider.getSystemJavaCompiler().run(null,System.out,System.err,"--release","17","-d",out.toString(),
    b+"ConsoleAgentContract.java",b+"HarnessCoordinator.java",b+"NesFrameTransfer.java",b+"NesModelHost.java",b+"HarnessDemoWorkflow.java","tests/NesModelHostTest.java");
  if(compiled!=0)throw new AssertionError("Typed NES model host failed compilation");
  try(URLClassLoader loader=new URLClassLoader(new URL[]{out.toUri().toURL()})){
   loader.loadClass("org.localpilot.shared.NesModelHostTest").getMethod("main",String[].class).invoke(null,(Object)args);
  }
 }
}
