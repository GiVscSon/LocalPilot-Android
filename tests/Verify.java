import javax.tools.ToolProvider;
import java.net.*;
import java.nio.file.*;
class Verify {
 public static void main(String[] args)throws Exception{
  Path out=Files.createTempDirectory("pilot-gate-");
  int result=ToolProvider.getSystemJavaCompiler().run(null,System.out,System.err,"-d",out.toString(),"app/src/main/java/org/localpilot/android/ActionGate.java","app/src/main/java/org/localpilot/android/Board2048.java","app/src/main/java/org/localpilot/android/PixelBoardReader.java","app/src/main/java/org/localpilot/android/Verified2048.java","app/src/main/java/org/localpilot/android/CaptureFreshness.java","app/src/main/java/org/localpilot/android/MiniPolicy.java","app/src/main/java/org/localpilot/android/MiniReader.java","app/src/main/java/org/localpilot/android/InputPermit.java","app/src/main/java/org/localpilot/android/SafeJournal.java","app/src/main/java/org/localpilot/android/StageMetrics.java","app/src/main/java/org/localpilot/android/ExperienceStore.java","tests/ExperienceTest.java","tests/MetricsTest.java","tests/HardeningTest.java","tests/BoardTest.java","tests/GateTest.java");
  if(result!=0)throw new RuntimeException("compile failed");
  try(URLClassLoader loader=new URLClassLoader(new URL[]{out.toUri().toURL()})){loader.loadClass("org.localpilot.android.ExperienceTest").getMethod("main",String[].class).invoke(null,(Object)new String[0]);loader.loadClass("org.localpilot.android.MetricsTest").getMethod("main",String[].class).invoke(null,(Object)new String[0]);loader.loadClass("org.localpilot.android.GateTest").getMethod("main",String[].class).invoke(null,(Object)new String[0]);loader.loadClass("org.localpilot.android.BoardTest").getMethod("main",String[].class).invoke(null,(Object)args);loader.loadClass("org.localpilot.android.HardeningTest").getMethod("main",String[].class).invoke(null,(Object)new String[0]);}
 }
}
