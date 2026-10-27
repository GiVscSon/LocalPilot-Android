import javax.tools.ToolProvider;
import java.nio.file.*;import java.net.*;import java.util.*;
class Upgrade726Verify {
 public static void main(String[] args)throws Exception{
  Path out=Files.createTempDirectory("pilot-726-");var cmd=new ArrayList<String>(List.of("--release","17","-d",out.toString()));
  for(String name:new String[]{"Board2048","PixelBoardReader","Color2048Reader","Verified2048","CapturePacer","JumperState","JumperPolicy","JumperFeedback","GridPuzzle","GridPuzzlePolicy","GridOnlineLearning","GridPuzzleVision","GridPuzzleSession","ArcadeFeedback","ArcadePolicy","ArcadeObservation","ArcadeProfile"})cmd.add("app/src/main/java/org/localpilot/android/"+name+".java");
  cmd.add("tests/Upgrade726Test.java");
  if(ToolProvider.getSystemJavaCompiler().run(null,System.out,System.err,cmd.toArray(String[]::new))!=0)throw new AssertionError("compile");
  try(var loader=new URLClassLoader(new URL[]{out.toUri().toURL()})){loader.loadClass("org.localpilot.android.Upgrade726Test").getMethod("main",String[].class).invoke(null,(Object)args);}
 }
}
