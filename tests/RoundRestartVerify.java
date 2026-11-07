import javax.tools.ToolProvider;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;

class RoundRestartVerify {
 public static void main(String[] args)throws Exception{
  var output=Files.createTempDirectory("localpilot-round-restart-");
  var sources=new java.util.ArrayList<String>();
  sources.add("--release");sources.add("17");sources.add("-d");sources.add(output.toString());
  for(String name:new String[]{"RoundRestartController","GameOverScreen","SceneRecovery","PixelBoardReader","Board2048"})
   sources.add("app/src/main/java/org/localpilot/android/"+name+".java");
  sources.add("tests/RoundRestartTest.java");
  if(ToolProvider.getSystemJavaCompiler().run(null,System.out,System.err,sources.toArray(String[]::new))!=0)throw new AssertionError("compile failed");
  try(var loader=new URLClassLoader(new URL[]{output.toUri().toURL()})){
   loader.loadClass("org.localpilot.android.RoundRestartTest").getMethod("main",String[].class).invoke(null,(Object)args);
  }
 }
}
