import java.net.*;
import java.nio.file.*;
import javax.tools.ToolProvider;
class HomebrewStudentNetworkVerify{
 public static void main(String[] args)throws Exception{
  Path out=Files.createTempDirectory("nes-student-weights");
  if(ToolProvider.getSystemJavaCompiler()==null||
    ToolProvider.getSystemJavaCompiler().run(null,System.out,System.err,
      "--release","17","-d",out.toString(),
      "shared/src/main/java/org/localpilot/shared/ConsoleAgentContract.java",
      "shared/src/main/java/org/localpilot/shared/NesTrial.java",
      "shared/src/main/java/org/localpilot/shared/HomebrewGoalReward.java",
      "shared/src/main/java/org/localpilot/shared/ConsoleExperienceMemory.java",
      "shared/src/main/java/org/localpilot/shared/HomebrewStudentNetwork.java",
      "tests/HomebrewStudentNetworkTest.java")!=0)
     throw new AssertionError("NES student learning compilation failed");
  try(URLClassLoader loader=new URLClassLoader(new URL[]{out.toUri().toURL()})){
   loader.loadClass("org.localpilot.shared.HomebrewStudentNetworkTest")
      .getMethod("main",String[].class).invoke(null,(Object)args);
  }
 }
}
