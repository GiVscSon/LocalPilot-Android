import java.nio.file.*;
import java.net.*;
import javax.tools.ToolProvider;
class NesSaveStoreVerify {
 public static void main(String[] args)throws Exception{
  Path out=Files.createTempDirectory("nes-save-test");
  if(ToolProvider.getSystemJavaCompiler().run(null,System.out,System.err,"--release","17","-d",out.toString(),
    "shared/src/main/java/org/localpilot/shared/NesSaveStore.java","tests/NesSaveStoreTest.java")!=0)throw new AssertionError("Save store compile failed");
  try(var loader=new URLClassLoader(new URL[]{out.toUri().toURL()})){
   loader.loadClass("org.localpilot.shared.NesSaveStoreTest").getMethod("main",String[].class).invoke(null,(Object)args);
  }
 }
}
