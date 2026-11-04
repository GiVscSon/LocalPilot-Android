import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.*;
import javax.tools.ToolProvider;

class GameLabRetroVerify {
 public static void main(String[] args)throws Exception{
  Path out=Files.createTempDirectory("gamelab-retro-");
  if(ToolProvider.getSystemJavaCompiler()==null
    ||ToolProvider.getSystemJavaCompiler().run(null,System.out,System.err,
      "--release","17","-d",out.toString(),
      "game-lab/src/main/java/org/localpilot/gamelab/GameLabEngine.java",
      "game-lab/src/main/java/org/localpilot/gamelab/RetroEmulatorCatalog.java",
      "tests/RetroEmulatorCatalogTest.java")!=0)
   throw new AssertionError("GameLab retro catalog compile");
  try(URLClassLoader loader=new URLClassLoader(new URL[]{out.toUri().toURL()})){
   loader.loadClass("org.localpilot.gamelab.RetroEmulatorCatalogTest")
    .getMethod("main",String[].class).invoke(null,(Object)args);
  }
 }
}
