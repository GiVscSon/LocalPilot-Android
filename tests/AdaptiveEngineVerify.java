import javax.tools.ToolProvider;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Standalone JVM runner for LocalPilot Adaptive Learning Engine verification. */
class AdaptiveEngineVerify {
 public static void main(String[] args) throws Exception {
  Path out = Files.createTempDirectory("pilot-adaptive-engine-");
  List<String> cmd = new ArrayList<>(List.of(
    "--release", "17",
    "-sourcepath", "app/src/main/java",
    "-d", out.toString(),
    "tests/AdaptiveEngineTest.java"
  ));
  if (ToolProvider.getSystemJavaCompiler().run(null, System.out, System.err, cmd.toArray(String[]::new)) != 0)
   throw new AssertionError("AdaptiveEngineVerify compilation failed");
  try (URLClassLoader loader = new URLClassLoader(new URL[]{out.toUri().toURL()})) {
   loader.loadClass("org.localpilot.android.AdaptiveEngineTest").getMethod("main", String[].class)
     .invoke(null, (Object) args);
  }
 }
}
