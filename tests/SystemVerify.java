import java.nio.file.*;
import java.net.*;
import javax.tools.ToolProvider;
class SystemVerify {
 public static void main(String[]args)throws Exception{
  Path out=Files.createTempDirectory("pilot-system-");
  var cmd=new java.util.ArrayList<String>();cmd.add("-d");cmd.add(out.toString());
  for(String name:new String[]{"Board2048","PixelBoardReader","SceneRecovery","ArcadeProfile","ArcadeObservation","ArcadePolicy","ArcadeVision","ArcadeController","ArcadeFeedback"})cmd.add("app/src/main/java/org/localpilot/android/"+name+".java");
  cmd.add("tests/ArcadeSystemTest.java");
  if(ToolProvider.getSystemJavaCompiler().run(null,System.out,System.err,cmd.toArray(String[]::new))!=0)throw new AssertionError("compile failed");
  try(var loader=new URLClassLoader(new URL[]{out.toUri().toURL()})){loader.loadClass("org.localpilot.android.ArcadeSystemTest").getMethod("main",String[].class).invoke(null,(Object)args);}
 }
}
