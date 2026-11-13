import java.net.*;
import java.nio.file.*;
import javax.tools.ToolProvider;
class NesExperimentPolicyVerify {
 public static void main(String[] args)throws Exception {
  Path out=Files.createTempDirectory("nes-stop-sim-policy");
  if(ToolProvider.getSystemJavaCompiler()==null||
      ToolProvider.getSystemJavaCompiler().run(null,System.out,System.err,
        "--release","17","-d",out.toString(),
        "game-lab/src/main/java/org/localpilot/gamelab/NesExperimentPolicy.java",
        "tests/NesExperimentPolicyTest.java")!=0)
   throw new AssertionError("NES lab STOP policy compilation failed");
  try(URLClassLoader loader=new URLClassLoader(new URL[]{out.toUri().toURL()})){
    loader.loadClass("org.localpilot.gamelab.NesExperimentPolicyTest")
      .getMethod("main",String[].class).invoke(null,(Object)args);
  }
 }
}
