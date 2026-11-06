import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import javax.tools.ToolProvider;

/** Compiles production decision + persistence with the exact synthetic model. */
class PaddleTransferVerify {
 public static void main(String[] args)throws Exception{
  Path out=Files.createTempDirectory("real-paddle-transfer-");
  var sources=new ArrayList<String>(List.of("--release","17","-d",out.toString()));
  for(String name:List.of("ArcadeProfile","ArcadeObservation","ArcadePolicy",
    "PaddleThreats","ArcadeController","RealGameNeuralCritic","VisualGameTarget","ArcadeLearningStore","PaddleTransfer"))
   sources.add("app/src/main/java/org/localpilot/android/"+name+".java");
  sources.add("app/src/main/java/org/localpilot/android/rl/PaddleWeightLab.java");
  sources.add("tests/PaddleTransferTest.java");
  if(ToolProvider.getSystemJavaCompiler()==null
    ||ToolProvider.getSystemJavaCompiler().run(null,System.out,System.err,
      sources.toArray(String[]::new))!=0)throw new AssertionError("compile real paddle bridge");
  try(URLClassLoader loader=new URLClassLoader(new URL[]{out.toUri().toURL()})){
   loader.loadClass("org.localpilot.android.PaddleTransferTest")
    .getMethod("main",String[].class).invoke(null,(Object)args);
  }
 }
}
