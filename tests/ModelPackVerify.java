import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import javax.tools.ToolProvider;

/** Runs the same DQN serialization and WeightSpace classes as the Android app. */
class ModelPackVerify {
 public static void main(String[] args)throws Exception{
  Path output=Files.createTempDirectory("portable-models-");
  var src=new ArrayList<>(List.of("--release","17","-d",output.toString()));
  for(String file:List.of("ArcadeEnv","DqnTrainer","WeightSpaceSkills",
                           "PaddleWeightLab","ModelPackExporter"))
   src.add("app/src/main/java/org/localpilot/android/rl/"+file+".java");
  src.add("tests/ModelPackExporterTest.java");
  if(ToolProvider.getSystemJavaCompiler()==null||
    ToolProvider.getSystemJavaCompiler().run(null,System.out,System.err,
      src.toArray(String[]::new))!=0)
    throw new AssertionError("ModelPack compiler failed");
  try(URLClassLoader loader=new URLClassLoader(new URL[]{output.toUri().toURL()})){
   loader.loadClass("org.localpilot.android.rl.ModelPackExporterTest")
    .getMethod("main",String[].class).invoke(null,(Object)args);
  }
 }
}
