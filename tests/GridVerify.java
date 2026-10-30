import javax.tools.ToolProvider;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

/** Standalone JVM verification: java tests/GridVerify.java [optional test arguments]. */
class GridVerify {
 public static void main(String[] args)throws Exception{
  Path out=Files.createTempDirectory("pilot-grid-");ArrayList<String> sources=new ArrayList<>();
  sources.add("--release");sources.add("17");sources.add("-d");sources.add(out.toString());
  for(String name:new String[]{"GridPuzzle","GridChainShadow","GridPuzzlePolicy","GridProfileRules","GridOnlineLearning","GridPuzzleVision","GridPuzzleSession","GridPuzzleConfig","GridPuzzleGoal"})sources.add("app/src/main/java/org/localpilot/android/"+name+".java");
  String[] tests={"GridChainShadowTest","GridPuzzlePolicyTest","GridProfileRulesTest","GridPuzzleVisionTest","GridPuzzleSessionTest","GridPuzzleConfigTest","GridPuzzleLoopTest","GridPuzzleGoalTest","GridRecordedTest","GridPartialVerifyTest","GridOnlineLearningTest"};
  for(String name:tests)sources.add("tests/"+name+".java");
  if(ToolProvider.getSystemJavaCompiler()==null)throw new IllegalStateException("JDK required for grid verification");
  if(ToolProvider.getSystemJavaCompiler().run(null,System.out,System.err,sources.toArray(String[]::new))!=0)throw new AssertionError("grid verification compile failed");
  try(URLClassLoader loader=new URLClassLoader(new URL[]{out.toUri().toURL()})){
   for(String name:tests)loader.loadClass("org.localpilot.android."+name).getMethod("main",String[].class).invoke(null,(Object)args);
  }
 }
}
