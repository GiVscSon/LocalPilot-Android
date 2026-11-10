import javax.tools.ToolProvider;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
/** Run script grammar and safety assertions independently of Android SDK. */
class GameScriptVerify {
 public static void main(String[] args)throws Exception{
  Path out=Files.createTempDirectory("localpilot-script-test-");
  if(ToolProvider.getSystemJavaCompiler().run(null,System.out,System.err,
    "--release","17","-d",out.toString(),
    "app/src/main/java/org/localpilot/android/GameScriptCatalog.java",
    "tests/GameScriptCatalogTest.java")!=0)
   throw new AssertionError("GameScript compilation failed");
  try(URLClassLoader cl=new URLClassLoader(new URL[]{out.toUri().toURL()})){
   cl.loadClass("org.localpilot.android.GameScriptCatalogTest").getMethod("main",String[].class)
     .invoke(null,(Object)args);
  }
 }
}
