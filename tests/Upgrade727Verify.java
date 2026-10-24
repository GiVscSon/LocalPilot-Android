import javax.tools.ToolProvider;
import java.net.*;import java.nio.file.*;
class Upgrade727Verify {
 public static void main(String[] args)throws Exception{
  Path out=Files.createTempDirectory("pilot-727-");
  if(ToolProvider.getSystemJavaCompiler().run(null,System.out,System.err,"--release","17","-d",out.toString(),"app/src/main/java/org/localpilot/android/DisplayGeometry.java","tests/DisplayGeometryTest.java")!=0)throw new AssertionError("compile");
  try(var loader=new URLClassLoader(new URL[]{out.toUri().toURL()})){loader.loadClass("org.localpilot.android.DisplayGeometryTest").getMethod("main",String[].class).invoke(null,(Object)args);}
 }
}
