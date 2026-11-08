import javax.tools.ToolProvider;
import java.net.URL;import java.net.URLClassLoader;
import java.nio.file.Files;import java.nio.file.Path;
class Board2048StallVerify {
 public static void main(String[] args)throws Exception{
  Path out=Files.createTempDirectory("localpilot-2048-stall-");
  if(ToolProvider.getSystemJavaCompiler().run(null,System.out,System.err,
     "--release","17","-d",out.toString(),
     "app/src/main/java/org/localpilot/android/Board2048.java",
     "app/src/main/java/org/localpilot/android/Board2048StallRecovery.java",
     "tests/Board2048StallRecoveryTest.java")!=0)
   throw new AssertionError("2048 stall verifier compile failed");
  try(URLClassLoader cl=new URLClassLoader(new URL[]{out.toUri().toURL()})){
   cl.loadClass("org.localpilot.android.Board2048StallRecoveryTest")
     .getMethod("main",String[].class).invoke(null,(Object)args);
  }
 }
}
