import javax.tools.ToolProvider;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Standalone JVM runner for Unified 0.8.1 + Adaptive Engine verification. */
public class Unified081IntegrationVerify {
 public static void main(String[] args) throws Exception {
  Path out = Files.createTempDirectory("pilot-unified-081-");
  List<String> cmd = new ArrayList<>(List.of(
    "--release", "17",
    "-sourcepath", "app/src/main/java",
    "-d", out.toString(),
    "app/src/main/java/org/localpilot/android/RealGameNeuralCritic.java",
    "app/src/main/java/org/localpilot/android/VisionV2Core.java",
    "app/src/main/java/org/localpilot/android/VisionNoveltyMemory.java",
    "app/src/main/java/org/localpilot/android/JumperWeightShadow.java",
    "app/src/main/java/org/localpilot/android/PaddleTransfer.java",
    "app/src/main/java/org/localpilot/android/TextRepetitionGuard.java",
    "app/src/main/java/org/localpilot/android/rl/PaddleWeightLab.java",
    "app/src/main/java/org/localpilot/android/rl/WeightSpaceSkills.java",
    "app/src/main/java/org/localpilot/android/WindowFocus.java",
    "app/src/main/java/org/localpilot/android/PanelDocking.java",
    "tests/Unified081IntegrationTest.java"
  ));
  if (ToolProvider.getSystemJavaCompiler().run(null, System.out, System.err, cmd.toArray(String[]::new)) != 0)
   throw new AssertionError("Unified081IntegrationVerify compilation failed");
  try (URLClassLoader loader = new URLClassLoader(new URL[]{out.toUri().toURL()})) {
   loader.loadClass("org.localpilot.android.Unified081IntegrationTest").getMethod("main", String[].class)
     .invoke(null, (Object) args);
  }
 }
}
