import java.net.*;
import java.nio.file.*;
import javax.tools.ToolProvider;
class HomebrewGoalRewardVerify {
 public static void main(String[] args)throws Exception{
  Path out=Files.createTempDirectory("goal-nrom");
  if(ToolProvider.getSystemJavaCompiler()==null||
     ToolProvider.getSystemJavaCompiler().run(null,System.out,System.err,
       "--release","17","-d",out.toString(),
       "shared/src/main/java/org/localpilot/shared/ConsoleAgentContract.java",
       "shared/src/main/java/org/localpilot/shared/HomebrewGoalReward.java",
       "tests/HomebrewGoalRewardTest.java")!=0)
    throw new AssertionError("Homebrew goal test compilation failed");
  try(URLClassLoader loader=new URLClassLoader(new URL[]{out.toUri().toURL()})){
   loader.loadClass("org.localpilot.shared.HomebrewGoalRewardTest")
      .getMethod("main",String[].class).invoke(null,(Object)args);
  }
 }
}
