import javax.tools.ToolProvider;
import java.net.URLClassLoader;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayList;
/** Standalone strict-Java-17 HexaSort vision, policy, feedback and allocation regression. */
class HexaVerify {
 public static void main(String[] args)throws Exception{
  var out=Files.createTempDirectory("localpilot-hexa-");var sources=new ArrayList<String>();sources.add("--release");sources.add("17");sources.add("-d");sources.add(out.toString());
  for(String name:new String[]{"HexaConfig","HexaBoard","HexaVision","HexaPolicy","HexaSession"})sources.add("app/src/main/java/org/localpilot/android/"+name+".java");
  sources.add("tests/HexaTest.java");
  if(ToolProvider.getSystemJavaCompiler().run(null,System.out,System.err,sources.toArray(String[]::new))!=0)throw new AssertionError("hexa compile failed");
  try(var loader=new URLClassLoader(new URL[]{out.toUri().toURL()})){loader.loadClass("org.localpilot.android.HexaTest").getMethod("main",String[].class).invoke(null,(Object)args);}
 }
}
