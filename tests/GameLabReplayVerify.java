import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.*;
import javax.tools.ToolProvider;

class GameLabReplayVerify {
 public static void main(String[] args)throws Exception{
  Path out=Files.createTempDirectory("gamelab-replay-test-");
  int code=ToolProvider.getSystemJavaCompiler().run(null,System.out,System.err,
    "--release","17","-d",out.toString(),
    "shared/src/main/java/org/localpilot/shared/GameLabReplay.java",
    "tests/GameLabReplayTest.java");
  if(code!=0)throw new AssertionError("replay bridge failed to compile");
  try(URLClassLoader loader=new URLClassLoader(new URL[]{out.toUri().toURL()})){
   loader.loadClass("GameLabReplayTest").getMethod("main",String[].class)
    .invoke(null,(Object)args);
  }
 }
}
