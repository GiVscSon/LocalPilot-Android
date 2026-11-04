import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import javax.tools.ToolProvider;

class WeightSpaceGameEngineVerify {
 public static void main(String[] args)throws Exception{
  Path out=Files.createTempDirectory("weightspace-primary-");
  var source=new ArrayList<String>(List.of("--release","17","-d",out.toString()));
  for(String cls:List.of("ArcadeProfile","ArcadeObservation","ArcadePolicy",
    "ArcadeController","WeightSpaceGameEngine","PaddleTransfer",
    "JumperState","JumperPolicy","JumperWeightShadow","WeightSpaceJumperEngine"))
   source.add("app/src/main/java/org/localpilot/android/"+cls+".java");
  source.add("app/src/main/java/org/localpilot/android/rl/PaddleWeightLab.java");
  source.add("app/src/main/java/org/localpilot/android/rl/WeightSpaceSkills.java");
  source.add("tests/WeightSpaceGameEngineTest.java");
  if(ToolProvider.getSystemJavaCompiler()==null||
    ToolProvider.getSystemJavaCompiler().run(null,System.out,System.err,
      source.toArray(String[]::new))!=0)throw new AssertionError("weightspace primary compile");
  try(var loader=new URLClassLoader(new URL[]{out.toUri().toURL()})){
   loader.loadClass("org.localpilot.android.WeightSpaceGameEngineTest")
    .getMethod("main",String[].class).invoke(null,(Object)args);
  }
 }
}
