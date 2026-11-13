import java.net.*;
import java.nio.file.*;
import javax.tools.ToolProvider;

class NesBridgeVerify {
 public static void main(String[] args)throws Exception{
  Path output=Files.createTempDirectory("nes-modellab-contract-");
  if(ToolProvider.getSystemJavaCompiler()==null||
     ToolProvider.getSystemJavaCompiler().run(null,System.out,System.err,
      "--release","17","-d",output.toString(),
      "shared/src/main/java/org/localpilot/shared/NesTrial.java",
      "tests/NesBridgeTest.java")!=0)
   throw new AssertionError("NES bridge contract compilation");
  try(URLClassLoader loader=new URLClassLoader(new URL[]{output.toUri().toURL()})){
   loader.loadClass("org.localpilot.shared.NesBridgeTest")
     .getMethod("main",String[].class).invoke(null,(Object)args);
  }
 }
}
