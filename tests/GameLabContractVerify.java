import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.*;
import javax.tools.ToolProvider;

/** Real production shared contract + ten-game touch model, never test doubles. */
class GameLabContractVerify {
 public static void main(String[] args)throws Exception{
  Path output=Files.createTempDirectory("gamelab-touch-");
  if(ToolProvider.getSystemJavaCompiler()==null||
    ToolProvider.getSystemJavaCompiler().run(null,System.out,System.err,
      "--release","17","-d",output.toString(),
      "shared/src/main/java/org/localpilot/shared/HubContract.java",
      "game-lab/src/main/java/org/localpilot/gamelab/GameLab2048Rules.java",
      "game-lab/src/main/java/org/localpilot/gamelab/GameLabBubbleRules.java",
      "game-lab/src/main/java/org/localpilot/gamelab/GameLabEngine.java",
      "tests/GameLabContractTest.java")!=0)
   throw new AssertionError("GameLab touch/evidence test compile");
  try(URLClassLoader loader=new URLClassLoader(new URL[]{output.toUri().toURL()})){
   loader.loadClass("org.localpilot.gamelab.GameLabContractTest")
    .getMethod("main",String[].class).invoke(null,(Object)args);
  }
 }
}
