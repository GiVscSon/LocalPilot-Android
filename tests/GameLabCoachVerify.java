import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.tools.ToolProvider;

/** Compiles the actual GameLabEngine controller with no Android dependency. */
class GameLabCoachVerify {
 public static void main(String[] args)throws Exception{
  Path out=Files.createTempDirectory("gamelab-coach-");
  int status=ToolProvider.getSystemJavaCompiler().run(null,System.out,System.err,
    "--release","17","-d",out.toString(),
    "game-lab/src/main/java/org/localpilot/gamelab/GameLabEngine.java",
    "game-lab/src/main/java/org/localpilot/gamelab/GameLab2048Rules.java",
    "game-lab/src/main/java/org/localpilot/gamelab/GameLabBubbleRules.java",
    "tools/GameLabCoach.java","tests/GameLabCoachTest.java");
  if(status!=0)throw new AssertionError("GameLab coach compiler failed");
  try(URLClassLoader loader=new URLClassLoader(new URL[]{out.toUri().toURL()})){
   loader.loadClass("GameLabCoachTest").getMethod("main",String[].class)
     .invoke(null,(Object)args);
  }
 }
}
