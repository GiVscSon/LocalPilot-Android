import java.net.URL;import java.net.URLClassLoader;
import java.nio.file.*;import javax.tools.ToolProvider;
class GameLabPhysicsVerify {
 public static void main(String[] args)throws Exception {
  Path out=Files.createTempDirectory("gamelab-physics-");
  if(ToolProvider.getSystemJavaCompiler().run(null,System.out,System.err,
    "--release","17","-d",out.toString(),
    "game-lab/src/main/java/org/localpilot/gamelab/GameLab2048Rules.java",
    "game-lab/src/main/java/org/localpilot/gamelab/GameLabBubbleRules.java",
    "game-lab/src/main/java/org/localpilot/gamelab/GameLabEngine.java",
    "tests/GameLabPhysicsTest.java")!=0)
   throw new AssertionError("GameLab fidelity physics compile failure");
  try(URLClassLoader loader=new URLClassLoader(new URL[]{out.toUri().toURL()})){
   loader.loadClass("org.localpilot.gamelab.GameLabPhysicsTest")
     .getMethod("main",String[].class).invoke(null,(Object)args);
  }
 }
}
