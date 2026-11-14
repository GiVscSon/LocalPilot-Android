import java.nio.file.*;
import java.net.*;
import javax.tools.ToolProvider;
class HomebrewTaskMaskVerify {
 public static void main(String[] args)throws Exception{
  Path out=Files.createTempDirectory("nes-task-mask-test");
  if(ToolProvider.getSystemJavaCompiler().run(null,System.out,System.err,"--release","17","-d",out.toString(),
    "shared/src/main/java/org/localpilot/shared/StudentPolicyNet.java",
    "shared/src/main/java/org/localpilot/shared/HomebrewObs64.java","tests/HomebrewTaskMaskTest.java")!=0)throw new AssertionError("Task mask compile failed");
  try(var loader=new URLClassLoader(new URL[]{out.toUri().toURL()})){
   loader.loadClass("org.localpilot.shared.HomebrewTaskMaskTest").getMethod("main",String[].class).invoke(null,(Object)args);
  }
 }
}
