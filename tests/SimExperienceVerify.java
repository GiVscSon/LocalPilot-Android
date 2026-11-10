import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.tools.ToolProvider;

class SimExperienceVerify {
 public static void main(String[] args)throws Exception{
  Path classes=Files.createTempDirectory("sim-experience-");
  int status=ToolProvider.getSystemJavaCompiler().run(null,System.out,System.err,
   "--release","17","-d",classes.toString(),
   "shared/src/main/java/org/localpilot/shared/SimExperience.java",
   "shared/src/main/java/org/localpilot/shared/SimLabPolicy.java",
   "tests/SimExperienceContractTest.java");
  if(status!=0)throw new AssertionError("simulator experience source compile");
  try(URLClassLoader loader=new URLClassLoader(new URL[]{classes.toUri().toURL()})){
   loader.loadClass("SimExperienceContractTest")
     .getMethod("main",String[].class).invoke(null,(Object)args);
  }
 }
}
