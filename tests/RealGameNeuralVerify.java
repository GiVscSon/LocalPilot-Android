import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import javax.tools.ToolProvider;

/** Compile and exercise the real visual game learning code, not a test double. */
class RealGameNeuralVerify {
 public static void main(String[] args)throws Exception{
  Path out=Files.createTempDirectory("pilot-real-neural-");
  var inputs=new ArrayList<String>(List.of("--release","17","-d",out.toString()));
  for(String name:List.of("ArcadeProfile","ArcadeObservation","ArcadePolicy",
    "ArcadeController","ArcadeLearningStore","VisualGameTarget","PaddleTransfer",
    "RealGameNeuralCritic"))
   inputs.add("app/src/main/java/org/localpilot/android/"+name+".java");
  inputs.add("app/src/main/java/org/localpilot/android/rl/PaddleWeightLab.java");
  inputs.add("tests/RealGameNeuralCriticTest.java");
  if(ToolProvider.getSystemJavaCompiler()==null||
    ToolProvider.getSystemJavaCompiler().run(null,System.out,System.err,
      inputs.toArray(String[]::new))!=0)
    throw new AssertionError("Real-game neural compilation");
  try(URLClassLoader classLoader=new URLClassLoader(new URL[]{out.toUri().toURL()})){
   classLoader.loadClass("org.localpilot.android.RealGameNeuralCriticTest")
    .getMethod("main",String[].class).invoke(null,(Object)args);
  }
 }
}
