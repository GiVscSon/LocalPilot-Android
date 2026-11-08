import javax.tools.ToolProvider;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Standalone JVM runner for TenGamesTest (P1-P5 fixes, Hexon, Kotobashnya). */
class TenGamesVerify {
 public static void main(String[] args) throws Exception {
  Path out = Files.createTempDirectory("pilot-ten-games-");
  List<String> cmd = new ArrayList<>(List.of("--release", "17", "-sourcepath", "app/src/main/java", "-d", out.toString()));
  String[] core = {
    "DisplayGeometry", "StopContext", "WindowFocus", "PanelDocking", "VisualGameTarget",
    "ArcadeProfile", "ArcadeObservation", "ArcadePolicy", "ArcadeController", "ArcadeFeedback", "ArcadeVision",
    "BubbleBoard", "BubblePolicy", "BubbleSession", "BubbleVision",
    "GridPuzzle", "GridPuzzleConfig", "GridPuzzleGoal", "GridPuzzlePolicy", "GridPuzzleSession",
    "GridPuzzleVision", "GridOnlineLearning", "GridChainShadow",
    "JumperState", "JumperPolicy", "JumperFeedback", "JumperTracking", "JumperVision", "JumperFrameGeometry",
    "Board2048", "Verified2048", "PixelBoardReader", "SceneRecovery",
    "HexaBoard", "HexaVision", "HexaPolicy", "HexaSession",
    "KotobashnyaState", "KotobashnyaVision", "KotobashnyaPolicy", "KotobashnyaFeedback"
  };
  for (String c : core) cmd.add("app/src/main/java/org/localpilot/android/" + c + ".java");
  cmd.add("tests/TenGamesTest.java");
  if (ToolProvider.getSystemJavaCompiler().run(null, System.out, System.err, cmd.toArray(String[]::new)) != 0)
   throw new AssertionError("TenGamesVerify compilation failed");
  try (URLClassLoader loader = new URLClassLoader(new URL[]{out.toUri().toURL()})) {
   loader.loadClass("org.localpilot.android.TenGamesTest").getMethod("main", String[].class)
     .invoke(null, (Object) args);
  }
 }
}
