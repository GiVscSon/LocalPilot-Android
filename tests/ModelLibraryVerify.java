import javax.tools.ToolProvider;
import java.net.URLClassLoader;
import java.nio.file.*;
class ModelLibraryVerify {
 public static void main(String[] args)throws Exception{
  Path out=Files.createTempDirectory("lp-library-tests-");
  if(ToolProvider.getSystemJavaCompiler().run(null,System.out,System.err,"--release","17","-sourcepath","app/src/main/java:shared/src/main/java",
    "-d",out.toString(),"tests/ModelLibraryTest.java","tests/NesExperienceTest.java")!=0)throw new AssertionError("library compile failed");
  try(URLClassLoader loader=new URLClassLoader(new java.net.URL[]{out.toUri().toURL()})){
   loader.loadClass("org.localpilot.shared.NesExperienceTest").getMethod("main",String[].class).invoke(null,(Object)args);
   loader.loadClass("org.localpilot.android.learning.ModelLibraryTest").getMethod("main",String[].class).invoke(null,(Object)args);
  }
 }
}
