import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import javax.tools.ToolProvider;

class NesAgentToolsVerify {
 public static void main(String[] args)throws Exception{
  var out=Files.createTempDirectory("nes-agent-tools");String b="shared/src/main/java/org/localpilot/shared/";
  int code=ToolProvider.getSystemJavaCompiler().run(null,System.out,System.err,"--release","17","-d",out.toString(),
    b+"ConsoleAgentContract.java",b+"VoidLancerGameState.java",b+"NesAgentTools.java","tests/NesAgentToolsTest.java");
  if(code!=0)throw new AssertionError("NES tool contract compilation failed");
  try(var loader=new URLClassLoader(new URL[]{out.toUri().toURL()})){
   loader.loadClass("org.localpilot.shared.NesAgentToolsTest").getMethod("main",String[].class).invoke(null,(Object)args);
  }
 }
}
