import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.*;
import javax.tools.ToolProvider;

/** Always compiles the production shared VisionV2Core. */
class VisionV2Verify {
 public static void main(String[] args)throws Exception{
  Path output=Files.createTempDirectory("vision-v2-test-");
  if(ToolProvider.getSystemJavaCompiler()==null
    ||ToolProvider.getSystemJavaCompiler().run(null,System.out,System.err,
      "--release","17","-d",output.toString(),
      "app/src/main/java/org/localpilot/android/VisionV2Core.java",
      "tests/VisionV2CoreTest.java")!=0)
   throw new AssertionError("Vision V2 did not compile");
  try(URLClassLoader loader=new URLClassLoader(new URL[]{output.toUri().toURL()})){
   loader.loadClass("org.localpilot.android.VisionV2CoreTest")
     .getMethod("main",String[].class).invoke(null,(Object)args);
  }
 }
}
