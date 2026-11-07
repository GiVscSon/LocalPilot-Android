import javax.tools.ToolProvider;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
class DeferredJumperVerify {
 public static void main(String[] args)throws Exception{
  var out=Files.createTempDirectory("deferred-jumper-");var sources=new ArrayList<String>(List.of("--release","17","-d",out.toString()));
  for(String name:new String[]{"JumperState","JumperPolicy","JumperFeedback","JumperTracking","JumperVision","PixelBoardReader","Board2048","SceneRecovery"})sources.add("app/src/main/java/org/localpilot/android/"+name+".java");
  sources.add("tests/DeferredJumperTest.java");sources.add("tests/JumperPolicyTest.java");sources.add("tests/DeferredJumperVisionTest.java");sources.add("tests/JumperVisionTest.java");
  if(ToolProvider.getSystemJavaCompiler().run(null,System.out,System.err,sources.toArray(String[]::new))!=0)throw new AssertionError("deferred jumper compile");
  try(var loader=new URLClassLoader(new URL[]{out.toUri().toURL()})){
   for(String test:new String[]{"JumperPolicyTest","JumperVisionTest","DeferredJumperTest","DeferredJumperVisionTest"})loader.loadClass("org.localpilot.android."+test).getMethod("main",String[].class).invoke(null,(Object)args);
  }
 }
}
