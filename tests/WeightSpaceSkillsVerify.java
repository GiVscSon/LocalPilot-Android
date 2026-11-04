import java.nio.file.*;
import java.net.*;
import javax.tools.ToolProvider;

class WeightSpaceSkillsVerify {
 public static void main(String[] args)throws Exception{
  Path out=Files.createTempDirectory("pilot-weight-space-");
  if(ToolProvider.getSystemJavaCompiler()==null
    ||ToolProvider.getSystemJavaCompiler().run(null,System.out,System.err,
      "--release","17","-d",out.toString(),
      "app/src/main/java/org/localpilot/android/rl/WeightSpaceSkills.java",
      "tests/WeightSpaceSkillsTest.java")!=0)
   throw new AssertionError("weight-space lab must compile");
  try(var loader=new URLClassLoader(new URL[]{out.toUri().toURL()})){
   loader.loadClass("org.localpilot.android.rl.WeightSpaceSkillsTest")
    .getMethod("main",String[].class).invoke(null,(Object)args);
  }
 }
}
