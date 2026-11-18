import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.tools.ToolProvider;

class NesExperienceArchiveVerify {
 public static void main(String[] args)throws Exception{
  Path out=Files.createTempDirectory("nes-experience-archive-verify");
  String base="shared/src/main/java/org/localpilot/shared/";
  int result=ToolProvider.getSystemJavaCompiler().run(null,System.out,System.err,
    "--release","17","-d",out.toString(),base+"ConsoleAgentContract.java",base+"NesTrial.java",
    base+"HomebrewGoalReward.java",base+"VoidLancerGameState.java",base+"ConsoleExperienceMemory.java",base+"VerifiedReplayFilter.java",
    base+"VerifiedPpoReplayStore.java",base+"NesExperienceArchive.java","tests/NesExperienceArchiveTest.java");
  if(result!=0)throw new AssertionError("Archive sources failed compilation");
  try(URLClassLoader loader=new URLClassLoader(new URL[]{out.toUri().toURL()})){
   loader.loadClass("org.localpilot.shared.NesExperienceArchiveTest").getMethod("main",String[].class).invoke(null,(Object)args);
  }
 }
}
