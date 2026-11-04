import java.nio.file.*;
import java.net.*;
import javax.tools.ToolProvider;
class TextRepetitionGuardVerify {
 public static void main(String[] args)throws Exception{
  Path out=Files.createTempDirectory("pilot-loop-guard-");
  if(ToolProvider.getSystemJavaCompiler()==null
    ||ToolProvider.getSystemJavaCompiler().run(null,System.out,System.err,
      "--release","17","-d",out.toString(),
      "app/src/main/java/org/localpilot/android/TextRepetitionGuard.java",
      "tests/TextRepetitionGuardTest.java")!=0)
   throw new AssertionError("loop guard compilation");
  try(var loader=new URLClassLoader(new URL[]{out.toUri().toURL()})){
   loader.loadClass("org.localpilot.android.TextRepetitionGuardTest")
    .getMethod("main",String[].class).invoke(null,(Object)args);
  }
 }
}
