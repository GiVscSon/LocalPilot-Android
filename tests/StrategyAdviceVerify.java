import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.*;
import javax.tools.ToolProvider;

class StrategyAdviceVerify {
 public static void main(String[] args)throws Exception{
  Path out=Files.createTempDirectory("pilot-strategy-contract-");
  if(ToolProvider.getSystemJavaCompiler()==null||
      ToolProvider.getSystemJavaCompiler().run(null,System.out,System.err,
        "--release","17","-d",out.toString(),
        "app/src/main/java/org/localpilot/android/TextRepetitionGuard.java",
        "app/src/main/java/org/localpilot/android/LocalCoreContract.java",
        "tests/StrategyAdviceTest.java")!=0)
   throw new AssertionError("strategy contract compilation");
  try(URLClassLoader loader=new URLClassLoader(new URL[]{out.toUri().toURL()})){
    loader.loadClass("org.localpilot.android.StrategyAdviceTest")
      .getMethod("main",String[].class).invoke(null,(Object)args);
  }
 }
}