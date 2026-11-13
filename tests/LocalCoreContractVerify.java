import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.*;
import javax.tools.ToolProvider;

class LocalCoreContractVerify {
 public static void main(String[] args)throws Exception{
  Path output=Files.createTempDirectory("pilot-local-core-");
  if(ToolProvider.getSystemJavaCompiler()==null ||
    ToolProvider.getSystemJavaCompiler().run(null,System.out,System.err,
     "--release","17","-d",output.toString(),
     "app/src/main/java/org/localpilot/android/TextRepetitionGuard.java",
     "app/src/main/java/org/localpilot/android/LocalCoreContract.java",
     "tests/LocalCoreContractTest.java")!=0)
    throw new AssertionError("Local core contract compilation");
  try(URLClassLoader loader=new URLClassLoader(new URL[]{output.toUri().toURL()})){
   loader.loadClass("org.localpilot.android.LocalCoreContractTest")
    .getMethod("main",String[].class).invoke(null,(Object)args);
  }
 }
}
