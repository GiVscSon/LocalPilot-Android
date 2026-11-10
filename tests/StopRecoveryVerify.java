import javax.tools.ToolProvider;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
/** Pure JVM proof: STOP recovery never bypasses action, ACK or fresh-frame gates. */
class StopRecoveryVerify {
 public static void main(String[] args)throws Exception{
  Path out=Files.createTempDirectory("localpilot-stop-recovery-");
  if(ToolProvider.getSystemJavaCompiler().run(null,System.out,System.err,
    "--release","17","-d",out.toString(),
    "app/src/main/java/org/localpilot/android/WindowFocus.java",
    "app/src/main/java/org/localpilot/android/GridPuzzleSession.java",
    "app/src/main/java/org/localpilot/android/GridPuzzlePolicy.java",
    "app/src/main/java/org/localpilot/android/GridPuzzle.java",
    "app/src/main/java/org/localpilot/android/GridOnlineLearning.java",
    "tests/WindowRebindTest.java","tests/GridPuzzleSessionTest.java")!=0)
   throw new AssertionError("STOP recovery Java compilation failed");
  try(URLClassLoader cl=new URLClassLoader(new URL[]{out.toUri().toURL()})){
   for(String file:new String[]{"WindowRebindTest","GridPuzzleSessionTest"})
    cl.loadClass("org.localpilot.android."+file).getMethod("main",String[].class)
      .invoke(null,(Object)args);
  }
 }
}
