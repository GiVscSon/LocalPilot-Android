import java.nio.file.*;
import java.net.*;
import javax.tools.ToolProvider;
class ControllerVerify {
 public static void main(String[] args)throws Exception{
  Path out=Files.createTempDirectory("pilot-controller-");
  String[] names={"ArcadeProfile","ArcadeObservation","ArcadePolicy","ArcadeController","ArcadeFeedback"};
  var cmd=new java.util.ArrayList<String>();cmd.add("-d");cmd.add(out.toString());
  for(String name:names)cmd.add("app/src/main/java/org/localpilot/android/"+name+".java");
  cmd.add("tests/ArcadeControllerTest.java");cmd.add("tests/FlightSearchTest.java");
  if(ToolProvider.getSystemJavaCompiler().run(null,System.out,System.err,cmd.toArray(String[]::new))!=0)throw new AssertionError("compile failed");
  try(var loader=new URLClassLoader(new URL[]{out.toUri().toURL()})){loader.loadClass("org.localpilot.android.FlightSearchTest").getMethod("main",String[].class).invoke(null,(Object)args);loader.loadClass("org.localpilot.android.ArcadeControllerTest").getMethod("main",String[].class).invoke(null,(Object)args);}
 }
}
