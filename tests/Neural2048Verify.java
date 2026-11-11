import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.*;
import javax.tools.ToolProvider;

class Neural2048Verify {
 public static void main(String[] args)throws Exception{
  Path output=Files.createTempDirectory("neural-2048-verifier-");
  String[] compile={
    "--release","17","-d",output.toString(),
    "app/src/main/java/org/localpilot/android/Board2048.java",
    "app/src/main/java/org/localpilot/android/TextRepetitionGuard.java",
    "app/src/main/java/org/localpilot/android/Neural2048Verifier.java",
    "tests/Neural2048VerifierTest.java"
  };
  if(ToolProvider.getSystemJavaCompiler()==null||
     ToolProvider.getSystemJavaCompiler().run(null,System.out,System.err,compile)!=0)
    throw new AssertionError("MNN 2048 verifier JVM compilation");
  try(URLClassLoader loader=new URLClassLoader(new URL[]{output.toUri().toURL()})){
    loader.loadClass("org.localpilot.android.Neural2048VerifierTest")
      .getMethod("main",String[].class).invoke(null,(Object)args);
  }
 }
}
