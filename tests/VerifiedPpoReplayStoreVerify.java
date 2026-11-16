import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.tools.ToolProvider;

class VerifiedPpoReplayStoreVerify {
 public static void main(String[] args)throws Exception{
  Path out=Files.createTempDirectory("verified-nes-replay-verify");
  int compiled=ToolProvider.getSystemJavaCompiler().run(null,System.out,System.err,
    "--release","17","-d",out.toString(),
    "shared/src/main/java/org/localpilot/shared/ConsoleAgentContract.java",
    "shared/src/main/java/org/localpilot/shared/NesTrial.java",
    "shared/src/main/java/org/localpilot/shared/HomebrewGoalReward.java",
    "shared/src/main/java/org/localpilot/shared/VoidLancerGameState.java",
    "shared/src/main/java/org/localpilot/shared/ConsoleExperienceMemory.java",
    "shared/src/main/java/org/localpilot/shared/VerifiedReplayFilter.java",
    "shared/src/main/java/org/localpilot/shared/VerifiedPpoReplayStore.java",
    "tests/VerifiedPpoReplayStoreTest.java");
  if(compiled!=0)throw new AssertionError("VerifiedPpoReplayStore did not compile");
  try(URLClassLoader loader=new URLClassLoader(new URL[]{out.toUri().toURL()})){
   loader.loadClass("org.localpilot.shared.VerifiedPpoReplayStoreTest")
     .getMethod("main",String[].class).invoke(null,(Object)args);
  }
 }
}
