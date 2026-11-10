import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

/** Single-file JVM runner (`java tests/RetroConsoleVerify.java`) for NES, DOS, and SEGA Retro Console Subsystem. */
public final class RetroConsoleVerify {
 public static void main(String[] args) throws Exception {
  JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
  if (compiler == null) throw new IllegalStateException("JDK compiler required");
  File outDir = new File(System.getProperty("java.io.tmpdir"), "lp-retro-verify-" + System.nanoTime());
  if (!outDir.mkdirs() && !outDir.isDirectory()) throw new IllegalStateException("mkdirs failed");

  List<String> cmd = new ArrayList<>();
  cmd.add("--release");
  cmd.add("17");
  cmd.add("-sourcepath");
  cmd.add("app/src/main/java:tests");
  cmd.add("-d");
  cmd.add(outDir.getAbsolutePath());
  cmd.add("tests/RetroConsoleTest.java");

  int rc = compiler.run(null, null, null, cmd.toArray(new String[0]));
  if (rc != 0) throw new IllegalStateException("Compilation of RetroConsoleTest failed with exit code " + rc);

  Process p = new ProcessBuilder(
    new File(System.getProperty("java.home"), "bin/java").getAbsolutePath(),
    "-cp", outDir.getAbsolutePath(),
    "org.localpilot.android.RetroConsoleTest"
  ).inheritIO().start();
  int exit = p.waitFor();
  if (exit != 0) throw new IllegalStateException("RetroConsoleTest failed with exit code " + exit);
 }
}
